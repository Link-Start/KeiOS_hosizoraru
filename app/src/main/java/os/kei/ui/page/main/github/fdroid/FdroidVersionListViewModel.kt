package os.kei.ui.page.main.github.fdroid

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.fdroid.FdroidMetadataSidecar
import os.kei.feature.github.data.local.fdroid.FdroidMetadataSidecarStore
import os.kei.feature.github.data.remote.fdroid.FdroidPackageSnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidVersionSnapshot
import os.kei.feature.github.domain.fdroid.FdroidPackageApiSnapshotProvider
import os.kei.feature.github.domain.fdroid.FdroidPackageSnapshotProvider
import os.kei.feature.github.model.FdroidTrackedAppConfig
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.buildFdroidRepositoryTrackIdentity
import os.kei.feature.github.model.fdroidRepositoryCheckSourceSignature
import os.kei.feature.github.model.forTrackedItem
import os.kei.feature.github.model.isFdroidRepositoryTrack
import os.kei.ui.page.main.github.asset.fdroidVersionAssetFile

internal data class FdroidVersionListUiState(
    val loading: Boolean = true,
    /** True while a refresh runs over a list that is already on screen. */
    val refreshing: Boolean = false,
    val errorMessage: String = "",
    /** True when this track is not an F-Droid source, so there is no index history to read. */
    val unsupported: Boolean = false,
    /** The builds the filters leave visible, newest first. */
    val rows: List<FdroidVersionRow> = emptyList(),
    /** How many builds the history holds before filtering, so the page can say what it is hiding. */
    val totalCount: Int = 0,
    val compatibleOnly: Boolean = false,
    val query: String = "",
    val appLabel: String = "",
    val packageName: String = "",
    /** What the repository is called — "IzzyOnDroid", "F-Droid" — for the reader, not for a request. */
    val repositoryLabel: String = "",
    /** The normalized repository base, which is what an APK path resolves against. */
    val repositoryUrl: String = "",
    val packagePageUrl: String = "",
    /** When the refresh sidecar was written, or 0 when this track has never been refreshed. */
    val cachedAtMillis: Long = 0L,
    /**
     * True once the package API has answered.
     *
     * While false the list is whatever the last refresh cached — the eight newest builds — and saying so
     * matters, because "this repository has eight versions" and "we only kept eight" look identical.
     */
    val liveHistoryLoaded: Boolean = false,
    /** The reader's lookup settings, so an APK row judges trust the way the tracked card does. */
    val lookupConfig: GitHubLookupConfig = GitHubLookupConfig(),
)

/**
 * An F-Droid package's version history, browsed rather than tracked.
 *
 * The release list's counterpart for the other source that really has a history behind it. It is not the
 * same shape underneath, though, and the difference decides the page: GitHub answers a paged endpoint
 * that returns ten fully-formed releases at a time, while F-Droid answers `/api/v1/packages` once with
 * every build there has ever been and almost nothing about any of them.
 *
 * So this holds two sources instead of paging one. The refresh sidecar on disk is opened first and shown
 * immediately — it has full records for the eight newest builds and needs no network, which is what lets
 * the page open at all on a repository that is unreachable. The package API then supplies the rest of the
 * history and [mergeFdroidVersions] lays the detail back over it. A failure at that second step is
 * reported without taking the cached list down, because a repository that publishes only an index has no
 * package API to answer and the eight builds already on disk are still the truth about it.
 *
 * Writes no F-Droid state. The sidecar is read-only here — a refresh from the tracked card is what
 * maintains it, and browsing a history is not a reason to move which build the track holds as selected.
 * (`GitHubTrackStore.load()` may still lazily migrate its own store on first read; that is the track
 * list's business, not this page's.)
 */
internal class FdroidVersionListViewModel(
    private val trackId: String,
    private val snapshotProvider: FdroidPackageSnapshotProvider = FdroidPackageApiSnapshotProvider(),
    private val sidecarLoader: (String) -> FdroidMetadataSidecar? = { id ->
        FdroidMetadataSidecarStore.load(id)
    },
    private val deviceSdkProvider: () -> Int = { Build.VERSION.SDK_INT },
) : ViewModel() {
    private val _uiState = MutableStateFlow(FdroidVersionListUiState())
    val uiState: StateFlow<FdroidVersionListUiState> = _uiState.asStateFlow()

    private var track: GitHubTrackedApp? = null
    private var fdroidConfig: FdroidTrackedAppConfig = FdroidTrackedAppConfig()
    private var repoUrl: String = ""
    private var installedVersion: String = ""
    private var installedVersionCode: Long = -1L

    /**
     * Every build, already badged. The filters narrow this rather than refetching or re-deriving —
     * whether a build is recommended, installed or runnable here does not depend on what was typed
     * into the filter field.
     */
    private var allRows: List<FdroidVersionRow> = emptyList()
    private var suggestedVersionCode: Long? = null

    /**
     * The row the last refresh recorded as selected, while that is the best answer available.
     *
     * Until the package API answers there is no `suggestedVersionCode`, so re-deriving a candidate would
     * fall back to "highest compatible" and could name a different build than the one the tracked card
     * and the detail sheet both show as selected. The sidecar already knows the answer; use it.
     */
    private var cachedSelectedRowId: String? = null

    /** The in-flight load, so a second refresh replaces the first rather than racing it. */
    private var loadJob: Job? = null

    /** The ids the page should open by itself, resolved once the history is known. */
    var defaultExpandedIds: Set<String> = emptySet()
        private set

    init {
        val resolved = GitHubTrackStore.load().firstOrNull { item -> item.id == trackId }
        if (resolved == null || !resolved.isFdroidRepositoryTrack()) {
            _uiState.update { state -> state.copy(loading = false, unsupported = true) }
        } else {
            bind(resolved)
            load(forceRefresh = false)
        }
    }

    /** Re-reads the package API. The cached rows stay on screen while it runs. */
    fun refresh() = load(forceRefresh = true)

    fun setQuery(text: String) {
        if (text == _uiState.value.query) return
        _uiState.update { state -> state.copy(query = text) }
        publishRows()
    }

    /**
     * Hides builds this device could not install.
     *
     * Not persisted, unlike the release list's tag filter: an F-Droid history is read to go *back*
     * through it, and a reader who came looking for the last build that still ran on their device would
     * be shown nothing with no way to tell why. It defaults off for the same reason.
     */
    fun toggleCompatibleOnly() {
        _uiState.update { state -> state.copy(compatibleOnly = !state.compatibleOnly) }
        publishRows()
    }

    private fun bind(item: GitHubTrackedApp) {
        track = item
        fdroidConfig = item.fdroidConfig
        val identity = buildFdroidRepositoryTrackIdentity(item.repoUrl, item.packageName)
        repoUrl = identity?.normalizedRepoUrl.orEmpty().ifBlank { item.repoUrl.trim() }
        val cacheEntry = GitHubTrackStore.loadCheckCache().first[item.id]
        // What is running right now. The update check already resolved it, so the history can mark the
        // build you are on — which is the point of a history you open in order to go back through it.
        installedVersion = cacheEntry?.localVersion.orEmpty()
        installedVersionCode = cacheEntry?.localVersionCode ?: -1L
        _uiState.update { state ->
            state.copy(
                appLabel = item.appLabel,
                packageName = item.packageName,
                repositoryLabel = identity?.repoDisplayName.orEmpty().ifBlank { item.repoUrl },
                repositoryUrl = repoUrl,
                packagePageUrl = item.fdroidConfig.packagePageUrl
                    .ifBlank { identity?.packagePageUrl.orEmpty() }
                    .ifBlank { item.repoUrl },
                lookupConfig = GitHubTrackStore.loadLookupConfig().forTrackedItem(item),
            )
        }
    }

    private fun load(forceRefresh: Boolean) {
        val item = track ?: return
        // Whether there is a history *at all*, not whether the filters left any of it visible. A refresh
        // run while the filter field excludes everything would otherwise report itself as a cold load and
        // replace "no match" with a spinner.
        val hasHistory = allRows.isNotEmpty()
        _uiState.update { state ->
            state.copy(loading = !hasHistory, refreshing = hasHistory, errorMessage = "")
        }
        // A second tap replaces the first rather than racing it: two launches both write `allRows` and
        // both clear the busy flags, so the loser could blank the winner's list or unstick the spinner
        // early.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // MMKV plus a JSON parse, so it belongs on the local dispatcher rather than the network one
            // it would otherwise be queued behind during a refresh batch.
            val cached = withContext(AppDispatchers.githubLocal) { loadCachedVersions(item) }
            cachedSelectedRowId = cached.selectedRowId
            if (cached.versions.isNotEmpty()) {
                _uiState.update { state -> state.copy(cachedAtMillis = cached.fetchedAtMillis) }
                // Only while there is nothing on screen. On a refresh the full history is already here,
                // and dropping back to the eight builds the sidecar keeps would visibly truncate it --
                // permanently, if the refresh then failed.
                if (!hasHistory) rebuildRows(cached.versions)
            }
            val remote = runCatching { snapshotProvider.loadPackageSnapshot(item, forceRefresh).getOrThrow() }
            remote
                .onSuccess { snapshot ->
                    suggestedVersionCode = snapshot.suggestedVersionCode
                    _uiState.update { state ->
                        state.copy(loading = false, refreshing = false, liveHistoryLoaded = true)
                    }
                    rebuildRows(mergeFdroidVersions(cached = cached.versions, remote = snapshot.versions))
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    // The cached rows stay: a repository that publishes only an index has no package API
                    // to answer, and the builds already on disk are still the truth about it.
                    _uiState.update { state ->
                        state.copy(
                            loading = false,
                            refreshing = false,
                            errorMessage = error.message ?: error::class.java.simpleName,
                        )
                    }
                }
        }
    }

    private data class CachedVersions(
        val versions: List<FdroidVersionSnapshot>,
        val fetchedAtMillis: Long,
        val selectedRowId: String?,
    )

    /**
     * The builds the last refresh kept, selected one included.
     *
     * The signature check is the same one the tracked card's asset panel makes: a sidecar written under a
     * different source config describes a different question — another repository, another selection mode
     * — and its records must not be laid over this one's history.
     */
    private fun loadCachedVersions(item: GitHubTrackedApp): CachedVersions {
        val sidecar = sidecarLoader(trackId) ?: return CachedVersions(emptyList(), 0L, null)
        if (sidecar.sourceConfigSignature != item.fdroidRepositoryCheckSourceSignature()) {
            return CachedVersions(emptyList(), 0L, null)
        }
        val selected = sidecar.selectedVersion?.toVersionSnapshot()
        val versions = (listOfNotNull(selected) + sidecar.candidateVersions.map { it.toVersionSnapshot() })
        return CachedVersions(
            // The selected build is normally also in the candidate list, so this list arrives with a
            // duplicate in it and has to be reduced before anything keys a lazy list off it.
            versions = fdroidVersionHistoryOf(versions),
            fetchedAtMillis = sidecar.fetchedAtMillis,
            selectedRowId = selected?.let { version -> fdroidVersionRowId(version) },
        )
    }

    /** Re-badges the whole history. Called when the history changes, never when a filter does. */
    private fun rebuildRows(history: List<FdroidVersionSnapshot>) {
        val deviceSdk = deviceSdkProvider()
        val snapshot =
            FdroidPackageSnapshot(
                repoUrl = repoUrl,
                packageName = _uiState.value.packageName,
                // Null until the package API answers, which drops the default selection mode back to
                // "highest compatible" — the same fallback FdroidCandidateSelector already takes when a
                // repository publishes no suggestion at all.
                suggestedVersionCode = suggestedVersionCode,
                versions = history,
            )
        val recommended = fdroidRecommendedVersionsFor(snapshot, fdroidConfig, deviceSdk)
        // Stable first, else the pre-release: exactly what FdroidReleaseCheckSource records as the
        // selected version, so the badge names the build a refresh would actually offer.
        val derivedId = (recommended.stable ?: recommended.preRelease)?.let { fdroidVersionRowId(it) }
        // With no repository suggestion yet, a derived candidate can name a different build than the one
        // the tracked card and the detail sheet both show. The sidecar recorded the real answer.
        val recommendedId =
            if (suggestedVersionCode == null && cachedSelectedRowId != null) cachedSelectedRowId else derivedId
        allRows =
            history.map { version ->
                FdroidVersionRow(
                    version = version,
                    channel = version.channel(),
                    // Compared by row identity, not by version code and name: two rebuilds under one
                    // code and one APK name are distinct rows, and both would take the badge otherwise.
                    recommended = recommendedId != null && fdroidVersionRowId(version) == recommendedId,
                    installed = version.matchesInstalled(installedVersionCode, installedVersion),
                    compatible = version.isCompatibleWith(deviceSdk),
                    downloadUrl =
                        fdroidVersionAssetFile(
                            repoUrl = repoUrl,
                            apkName = version.apkName,
                            apkPath = version.apkPath,
                            apkSha256 = version.apkSha256,
                            apkSizeBytes = version.apkSizeBytes,
                            addedAtMillis = version.addedAtMillis,
                            signerSha256 = version.signerSha256,
                        )?.downloadUrl.orEmpty(),
                )
            }
        publishRows()
    }

    private fun publishRows() {
        val state = _uiState.value
        val visible =
            allRows.filter { row ->
                (!state.compatibleOnly || row.compatible) && row.version.matchesQuery(state.query)
            }
        defaultExpandedIds = fdroidVersionAnchorIds(visible)
        _uiState.update { current -> current.copy(rows = visible, totalCount = allRows.size) }
    }
}

