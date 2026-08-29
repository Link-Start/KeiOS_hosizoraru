package os.kei.ui.page.main.github.release

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubAtomReleaseStrategy
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseListEntry
import os.kei.feature.github.domain.GitHubReleaseAssetService
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.forTrackedItem

/** One release in the list, plus whatever its expanded card has managed to load. */
internal data class GitHubReleaseRow(
    val entry: GitHubReleaseListEntry,
    val detail: GitHubReleaseAssetBundle? = null,
    val detailLoading: Boolean = false,
    val detailError: String = "",
)

internal data class GitHubReleaseListUiState(
    val loading: Boolean = true,
    val errorMessage: String = "",
    val rows: List<GitHubReleaseRow> = emptyList(),
    val page: Int = 1,
    val hasNextPage: Boolean = false,
    val hasPreviousPage: Boolean = false,
    val repositoryLabel: String = "",
    val repositoryUrl: String = "",
    /** The tracked app's package, so an asset row can judge whether a file installs over it. */
    val packageName: String = "",
    val unsupported: Boolean = false,
    /** True when the list came from the Atom feed rather than the API — see [loadPage]. */
    val atomMode: Boolean = false,
    /** Whether tag-only entries are being hidden. Only ever does anything in [atomMode]. */
    val hideTagOnly: Boolean = true,
    /** True when the filter was asked for but the source that can apply it did not answer. */
    val tagFilterUnavailable: Boolean = false,
)

/**
 * A repository's release history, browsed rather than tracked.
 *
 * Holds nothing: no cache write, no check-cache entry, no persisted setting. The update check keeps two
 * releases because that is what an update decision needs, and browsing does not change that — it asks the
 * same sources for a page when the page opens.
 *
 * **Both lookup modes, as the user configured them.** The API path pages properly and reports each
 * release's asset count; the Atom feed does not page at all, so it is fetched deep and sliced, and runs
 * out where the feed does. Neither is silently substituted for the other: a token-less user asking for
 * Atom would otherwise be spending their 60 unauthenticated API calls an hour here.
 *
 * The heavy half of a release — files, notes body, commit — is one call to [GitHubReleaseAssetService
 * .fetchApkAssets], which already takes an arbitrary tag and already falls back to parsing release HTML
 * when there is no token. So an expanded card costs exactly one request, in either mode.
 */
internal class GitHubReleaseListViewModel(
    private val trackId: String,
    private val service: GitHubReleaseAssetService = GitHubReleaseAssetService(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(GitHubReleaseListUiState())
    val uiState: StateFlow<GitHubReleaseListUiState> = _uiState.asStateFlow()

    private var owner: String = ""
    private var repo: String = ""
    private var lookupConfig: GitHubLookupConfig = GitHubLookupConfig()
    private var defaultsApplied = false
    private var hideTagOnly: Boolean = GitHubTrackStore.loadHideTagOnlyReleases()
    private var tagFilterUnavailable: Boolean = false

    init {
        val track = GitHubTrackStore.load().firstOrNull { item -> item.id == trackId }
        if (track == null) {
            _uiState.update { state -> state.copy(loading = false, unsupported = true) }
        } else {
            bind(track)
            loadPage(1)
        }
    }

    fun retry() = loadPage(_uiState.value.page)

    /**
     * Hides entries the feed carries that are tags rather than releases.
     *
     * Persisted, because it is a property of how a reader wants the list to read rather than of this
     * visit. Off in API mode by construction: that endpoint returns releases only.
     */
    fun toggleHideTagOnly() {
        hideTagOnly = !hideTagOnly
        GitHubTrackStore.saveHideTagOnlyReleases(hideTagOnly)
        _uiState.update { state -> state.copy(hideTagOnly = hideTagOnly) }
        loadPage(_uiState.value.page)
    }

    fun openFirstPage() = loadPage(1)

    fun openNextPage() {
        if (_uiState.value.hasNextPage) loadPage(_uiState.value.page + 1)
    }

    fun openPreviousPage() {
        if (_uiState.value.hasPreviousPage) loadPage(_uiState.value.page - 1)
    }

    fun jumpToPage(page: Int) {
        val target = page.coerceAtLeast(1)
        if (target != _uiState.value.page) loadPage(target)
    }

    /**
     * Loads a release's files, notes and commit, once.
     *
     * Called when a card opens rather than with the list, so a page costs one request until the reader
     * actually asks for a release. A failure is kept on the row so the card can say so without taking the
     * whole page down with it.
     */
    fun ensureDetail(entryId: String) {
        val row = _uiState.value.rows.firstOrNull { candidate -> candidate.entry.id == entryId } ?: return
        if (row.detail != null || row.detailLoading) return
        updateRow(entryId) { current -> current.copy(detailLoading = true, detailError = "") }
        viewModelScope.launch {
            val result =
                runCatching {
                    service.fetchApkAssets(
                        owner = owner,
                        repo = repo,
                        rawTag = row.entry.tagName,
                        releaseUrl = row.entry.htmlUrl,
                        // No token means the API cannot be trusted to answer, and the repository already
                        // knows how to read a release out of its HTML page instead.
                        preferHtml = _uiState.value.atomMode,
                        aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
                        // The app already knows which files are worth offering — source archives, the
                        // attestation json and the rest of a release's bookkeeping are not what anyone
                        // opens this page to download. Same selector the tracked card's asset panel uses,
                        // so the two surfaces offer the same files.
                        includeAllAssets = false,
                        apiToken = lookupConfig.apiToken,
                    ).getOrThrow()
                }
            result
                .onSuccess { bundle ->
                    updateRow(entryId) { current ->
                        current.copy(detail = bundle, detailLoading = false, detailError = "")
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    updateRow(entryId) { current ->
                        current.copy(
                            detailLoading = false,
                            detailError = error.message ?: error::class.java.simpleName,
                        )
                    }
                }
        }
    }

    private fun bind(track: GitHubTrackedApp) {
        owner = track.owner
        repo = track.repo
        lookupConfig = GitHubTrackStore.loadLookupConfig().forTrackedItem(track)
        _uiState.update { state ->
            state.copy(
                repositoryLabel = track.appLabel.ifBlank { "${track.owner}/${track.repo}" },
                repositoryUrl = track.repoUrl,
                packageName = track.packageName,
                atomMode = lookupConfig.selectedStrategy != GitHubLookupStrategyOption.GitHubApiToken,
                hideTagOnly = hideTagOnly,
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
            val result = runCatching { fetchPage(page) }
            result
                .onSuccess { loaded ->
                    _uiState.update { state ->
                        state.copy(
                            loading = false,
                            rows = loaded.entries.map { entry -> GitHubReleaseRow(entry = entry) },
                            page = loaded.page,
                            hasNextPage = loaded.hasNextPage,
                            hasPreviousPage = loaded.page > 1,
                            errorMessage = "",
                            tagFilterUnavailable = tagFilterUnavailable,
                        )
                    }
                    applyDefaultExpansionOnce(loaded.entries)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { state ->
                        state.copy(
                            loading = false,
                            // Not `message.orEmpty()`: NetworkOnMainThreadException carries no message,
                            // and a blank one made a hard failure render as "no releases yet".
                            errorMessage = error.message ?: error::class.java.simpleName,
                        )
                    }
                }
        }
    }

    private suspend fun fetchPage(page: Int): LoadedReleasePage {
        if (!_uiState.value.atomMode) {
            val apiPage =
                service.fetchReleasePage(
                    owner = owner,
                    repo = repo,
                    apiToken = lookupConfig.apiToken,
                    page = page,
                ).getOrThrow()
            return LoadedReleasePage(
                entries = apiPage.entries,
                page = apiPage.page,
                hasNextPage = apiPage.hasNextPage,
            )
        }
        // Hiding tag-only entries means asking something that knows the difference, and only the API
        // does. GitHub's `releases.atom` carries a repository's tags alongside its releases and gives
        // the two *structurally identical* entries — same element set, same `tag:github.com,2008:
        // Repository/<id>/<tag>` id shape. No title rule separates them either: KeiOS's bare tags are
        // titled `v1.11.8`, and InstallerX's real releases are titled `26.05.01`, both equal to their
        // tag. So when the filter is on, the list comes from the releases endpoint *unauthenticated* —
        // one request, no token, and definitive. If that fails, which on a shared address usually means
        // the 60-per-hour limit, the feed answers instead and the filter reports itself unavailable
        // rather than silently dropping rows it cannot identify.
        if (hideTagOnly) {
            val filtered =
                runCatching {
                    service.fetchReleasePage(
                        owner = owner,
                        repo = repo,
                        apiToken = lookupConfig.apiToken,
                        page = page,
                    ).getOrThrow()
                }.getOrNull()
            if (filtered != null) {
                tagFilterUnavailable = false
                return LoadedReleasePage(
                    entries = filtered.entries,
                    page = filtered.page,
                    hasNextPage = filtered.hasNextPage,
                )
            }
            tagFilterUnavailable = true
        }
        val wanted = page * RELEASE_PAGE_SIZE
        // The strategy is an object with no dispatcher of its own — unlike the service path, which wraps
        // every call in `withContext(networkDispatcher)`. Calling it straight from `viewModelScope` put an
        // OkHttp call on the main thread.
        val entries =
            withContext(AppDispatchers.githubNetwork) {
                GitHubAtomReleaseStrategy.fetchReleaseEntries(
                    owner = owner,
                    repo = repo,
                    limit = wanted + 1,
                ).getOrThrow()
            }
        val window = entries.drop((page - 1) * RELEASE_PAGE_SIZE).take(RELEASE_PAGE_SIZE)
        val mapped =
            window.map { entry ->
                GitHubReleaseListEntry(
                    tagName = entry.tag,
                    releaseName = entry.title,
                    htmlUrl = entry.link,
                    prerelease = entry.isLikelyPreRelease,
                    publishedAtMillis = entry.updatedAtMillis,
                    authorName = entry.authorName,
                    authorAvatarUrl = entry.authorAvatarUrl,
                    // The feed carries the notes as HTML; the body an expanded card renders comes from
                    // the release itself, so this stays the text fallback.
                    bodyMarkdown = entry.contentText,
                    assetCount = 0,
                )
            }
        // No "Latest" badge on this path. GitHub's own "latest" means the newest *non-pre-release*, and
        // the feed has no pre-release field — `isLikelyPreRelease` is inferred from the title and channel,
        // which reads a CI build called "… Preview (11f15e4)" as stable. Marking latest off an unreliable
        // flag put the badge on a pre-release, which is worse than not showing it.
        return LoadedReleasePage(
            entries = mapped,
            page = page,
            hasNextPage = entries.size > wanted,
        )
    }

    /**
     * Opens the two releases nearly everyone came for: the latest, and the newest pre-release.
     *
     * Once per visit, and only on the first page. Re-applying it on every page would fight the reader on
     * the way back, and a repository whose CI publishes a pre-release per push would have one open on
     * every page of history.
     */
    private fun applyDefaultExpansionOnce(entries: List<GitHubReleaseListEntry>) {
        if (defaultsApplied) return
        defaultsApplied = true
        val latest = entries.firstOrNull { entry -> entry.latest }
        val newestPreRelease = entries.firstOrNull { entry -> entry.prerelease }
        val anchors = listOfNotNull(latest?.id, newestPreRelease?.id)
        // Atom mode marks neither, because the feed cannot tell them apart. The newest release is the
        // closest honest anchor there — opening nothing at all would be worse than opening the top one.
        defaultExpandedIds =
            anchors.ifEmpty { listOfNotNull(entries.firstOrNull()?.id) }.toSet()
    }

    var defaultExpandedIds: Set<String> = emptySet()
        private set

    private fun updateRow(
        entryId: String,
        transform: (GitHubReleaseRow) -> GitHubReleaseRow,
    ) {
        _uiState.update { state ->
            state.copy(
                rows =
                    state.rows.map { row ->
                        if (row.entry.id == entryId) transform(row) else row
                    },
            )
        }
    }

    private data class LoadedReleasePage(
        val entries: List<GitHubReleaseListEntry>,
        val page: Int,
        val hasNextPage: Boolean,
    )

    private companion object {
        /** What GitHub's own release list shows. Mirrors the engine's page size. */
        const val RELEASE_PAGE_SIZE = 10
    }
}
