package os.kei.ui.page.main.github.release

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubReleaseListEntry
import os.kei.feature.github.domain.GitHubReleaseAssetService
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.forTrackedItem

internal data class GitHubReleaseListUiState(
    val loading: Boolean = true,
    val errorMessage: String = "",
    val entries: List<GitHubReleaseListEntry> = emptyList(),
    val page: Int = 1,
    val hasNextPage: Boolean = false,
    val hasPreviousPage: Boolean = false,
    val repositoryLabel: String = "",
    val repositoryUrl: String = "",
    /** A track with no release feed at all — a direct APK link, or an F-Droid repository. */
    val unsupported: Boolean = false,
)

/**
 * One page of a repository's releases, browsed rather than tracked.
 *
 * Deliberately holds nothing: no cache write, no check-cache entry, no persisted setting. The update
 * check keeps two releases because that is what an update decision needs, and browsing history does not
 * need to change that — it just asks the same endpoint for a page when the page opens. Which is also why
 * this reaches the store directly for the track and its token instead of threading through the GitHub
 * page's own state: it shares the data source, not the state.
 */
internal class GitHubReleaseListViewModel(
    private val trackId: String,
    private val service: GitHubReleaseAssetService = GitHubReleaseAssetService(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(GitHubReleaseListUiState())
    val uiState: StateFlow<GitHubReleaseListUiState> = _uiState.asStateFlow()

    private var owner: String = ""
    private var repo: String = ""
    private var apiToken: String = ""

    init {
        val track = GitHubTrackStore.load().firstOrNull { item -> item.id == trackId }
        if (track == null) {
            _uiState.update { state -> state.copy(loading = false, unsupported = true) }
        } else {
            bind(track)
            loadPage(1)
        }
    }

    fun retry() {
        loadPage(_uiState.value.page)
    }

    fun openNextPage() {
        if (!_uiState.value.hasNextPage) return
        loadPage(_uiState.value.page + 1)
    }

    fun openPreviousPage() {
        if (!_uiState.value.hasPreviousPage) return
        loadPage(_uiState.value.page - 1)
    }

    private fun bind(track: GitHubTrackedApp) {
        owner = track.owner
        repo = track.repo
        apiToken = GitHubTrackStore.loadLookupConfig().forTrackedItem(track).apiToken
        _uiState.update { state ->
            state.copy(
                repositoryLabel = track.appLabel.ifBlank { "${track.owner}/${track.repo}" },
                repositoryUrl = track.repoUrl,
                // Only a repository has a release feed. The other modes reach a file or an index, and
                // there is no history behind them to page through.
                unsupported = track.sourceMode != GitHubTrackedSourceMode.GitHubRepository,
            )
        }
    }

    private fun loadPage(page: Int) {
        if (_uiState.value.unsupported) {
            _uiState.update { state -> state.copy(loading = false) }
            return
        }
        _uiState.update { state -> state.copy(loading = true, errorMessage = "") }
        viewModelScope.launch {
            val result =
                runCatching {
                    service.fetchReleasePage(
                        owner = owner,
                        repo = repo,
                        apiToken = apiToken,
                        page = page,
                    ).getOrThrow()
                }
            result
                .onSuccess { releasePage ->
                    _uiState.update { state ->
                        state.copy(
                            loading = false,
                            errorMessage = "",
                            entries = releasePage.entries,
                            page = releasePage.page,
                            hasNextPage = releasePage.hasNextPage,
                            hasPreviousPage = releasePage.hasPreviousPage,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { state ->
                        state.copy(
                            loading = false,
                            errorMessage = error.message.orEmpty(),
                        )
                    }
                }
        }
    }
}
