package os.kei.ui.page.main.github.page

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.BuildConfig
import os.kei.core.ui.snapshot.AppSnapshotFlowManager
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.GitHubTrackedFilterMode
import os.kei.ui.page.main.github.actions.GitHubActionsUiStateStore
import os.kei.ui.page.main.github.query.DownloaderOption
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.github.section.GitHubOverviewUiStateStore
import os.kei.ui.page.main.github.section.GitHubTrackedReleaseUiStateStore
import kotlin.time.Duration.Companion.milliseconds

private const val pendingShareImportCardTickMs = 15_000L
private const val CONTENT_DERIVATION_DEBOUNCE_MS = 48L

internal data class GitHubTrackedExportRequest(
    val content: String,
    val fileName: String
)

internal data class GitHubTrackTransferUiState(
    val tracksExporting: Boolean = false,
    val tracksImporting: Boolean = false,
    val pendingExport: GitHubTrackedExportRequest? = null
)

internal sealed interface GitHubTrackedExportStartResult {
    data object Ready : GitHubTrackedExportStartResult
    data object Busy : GitHubTrackedExportStartResult
    data object Empty : GitHubTrackedExportStartResult
    data class Failed(val reason: String?) : GitHubTrackedExportStartResult
}

internal sealed interface GitHubTrackedImportStartResult {
    data object Ready : GitHubTrackedImportStartResult
    data object Busy : GitHubTrackedImportStartResult
}

internal class GitHubPageViewModel : ViewModel() {
    val repository = GitHubPageRepository()
    private var pageState: GitHubPageState? = null
    private var contentStateJob: Job? = null
    private var pendingShareImportClockJob: Job? = null
    private var onlineShareTargetsJob: Job? = null
    private var downloaderOptionsJob: Job? = null
    private val snapshotFlowManager = AppSnapshotFlowManager()
    private val pendingShareImportPageActive = MutableStateFlow(false)
    private val pendingShareImportNowMillis = MutableStateFlow(System.currentTimeMillis())

    private val _contentDerivedState = MutableStateFlow(GitHubPageContentDerivedState())
    val contentDerivedState: StateFlow<GitHubPageContentDerivedState> = _contentDerivedState.asStateFlow()

    private val _transferState = MutableStateFlow(GitHubTrackTransferUiState())
    val transferState: StateFlow<GitHubTrackTransferUiState> = _transferState.asStateFlow()

    private val _installedOnlineShareTargets = MutableStateFlow<List<OnlineShareTargetOption>>(emptyList())
    val installedOnlineShareTargets: StateFlow<List<OnlineShareTargetOption>> =
        _installedOnlineShareTargets.asStateFlow()

    private val _checkLogicDownloaderOptions = MutableStateFlow<List<DownloaderOption>>(emptyList())
    val checkLogicDownloaderOptions: StateFlow<List<DownloaderOption>> =
        _checkLogicDownloaderOptions.asStateFlow()

    fun pageState(searchBarHideThresholdPx: Float): GitHubPageState {
        val current = pageState
        if (current != null) return current
        return GitHubPageState(
            searchBarHideThresholdPx = searchBarHideThresholdPx,
            pageUiState = GitHubPageUiStateStore.load(),
            actionsSectionExpansionState = GitHubActionsUiStateStore.loadSectionExpansionState(),
            overviewUiState = GitHubOverviewUiStateStore.load(),
            trackedReleaseExpansionState = GitHubTrackedReleaseUiStateStore.load()
        ).also {
            pageState = it
            bindContentState(it)
        }
    }

    fun bindContextObservers(
        context: Context,
        state: GitHubPageState
    ) {
        val appContext = context.applicationContext
        if (onlineShareTargetsJob?.isActive != true) {
            onlineShareTargetsJob = viewModelScope.launch {
                snapshotFlowManager.snapshotFlow {
                    GitHubOnlineShareTargetInput(
                        shouldResolve = state.showCheckLogicSheet ||
                            state.lookupConfig.onlineShareTargetPackage.isNotBlank() ||
                            state.onlineShareTargetPackageInput.isNotBlank(),
                        appList = state.appList.toList()
                    )
                }.distinctUntilChanged().collectLatest { input ->
                    _installedOnlineShareTargets.value = repository.queryOnlineShareTargets(
                        context = appContext,
                        input = input
                    )
                }
            }
        }
        if (downloaderOptionsJob?.isActive != true) {
            downloaderOptionsJob = viewModelScope.launch {
                snapshotFlowManager.snapshotFlow { state.showCheckLogicSheet }
                    .distinctUntilChanged()
                    .collectLatest { showCheckLogicSheet ->
                        _checkLogicDownloaderOptions.value = if (showCheckLogicSheet) {
                            repository.queryDownloaders(appContext)
                        } else {
                            emptyList()
                        }
                    }
            }
        }
    }

    fun setPageDataActive(active: Boolean) {
        if (pendingShareImportPageActive.value == active) return
        pendingShareImportPageActive.value = active
    }

    suspend fun beginTrackedExport(
        items: List<GitHubTrackedApp>,
        exportedAtMillis: Long,
        fileName: String
    ): GitHubTrackedExportStartResult {
        if (_transferState.value.tracksExporting || _transferState.value.tracksImporting) {
            return GitHubTrackedExportStartResult.Busy
        }
        if (items.isEmpty()) return GitHubTrackedExportStartResult.Empty
        _transferState.update { state -> state.copy(tracksExporting = true) }
        val content = runCatching {
            repository.buildTrackedItemsExportJson(
                items = items,
                exportedAtMillis = exportedAtMillis
            )
        }.getOrElse { error ->
            finishTrackedExport()
            return GitHubTrackedExportStartResult.Failed(error.message ?: error.javaClass.simpleName)
        }
        _transferState.update { state ->
            state.copy(
                tracksExporting = true,
                pendingExport = GitHubTrackedExportRequest(
                    content = content,
                    fileName = fileName
                )
            )
        }
        return GitHubTrackedExportStartResult.Ready
    }

    fun beginTrackedImport(): GitHubTrackedImportStartResult {
        if (_transferState.value.tracksExporting || _transferState.value.tracksImporting) {
            return GitHubTrackedImportStartResult.Busy
        }
        _transferState.update { state -> state.copy(tracksImporting = true) }
        return GitHubTrackedImportStartResult.Ready
    }

    fun consumePendingExport(): GitHubTrackedExportRequest? {
        val request = _transferState.value.pendingExport
        _transferState.update { state -> state.copy(pendingExport = null) }
        return request
    }

    fun finishTrackedExport() {
        _transferState.update { state ->
            state.copy(
                tracksExporting = false,
                pendingExport = null
            )
        }
    }

    fun finishTrackedImport() {
        _transferState.update { state -> state.copy(tracksImporting = false) }
    }

    suspend fun writeExport(
        contentResolver: ContentResolver,
        uri: Uri,
        request: GitHubTrackedExportRequest
    ) {
        repository.writeText(
            contentResolver = contentResolver,
            uri = uri,
            content = request.content
        )
    }

    suspend fun readImport(
        contentResolver: ContentResolver,
        uri: Uri
    ): String {
        return repository.readText(
            contentResolver = contentResolver,
            uri = uri
        )
    }

    @OptIn(FlowPreview::class)
    private fun bindContentState(state: GitHubPageState) {
        contentStateJob?.cancel()
        pendingShareImportClockJob?.cancel()
        pendingShareImportClockJob = viewModelScope.launch {
            combine(
                snapshotFlowManager.snapshotFlow { state.pendingShareImportTrack?.armedAtMillis }
                    .distinctUntilChanged(),
                pendingShareImportPageActive
            ) { armedAtMillis, pageActive ->
                armedAtMillis.takeIf { pageActive }
            }
                .distinctUntilChanged()
                .collectLatest { armedAtMillis ->
                    pendingShareImportNowMillis.value = System.currentTimeMillis()
                    if (armedAtMillis == null) return@collectLatest
                    while (true) {
                        kotlinx.coroutines.delay(pendingShareImportCardTickMs.milliseconds)
                        pendingShareImportNowMillis.value = System.currentTimeMillis()
                    }
                }
        }
        contentStateJob = viewModelScope.launch {
            combine(
                snapshotFlowManager.snapshotFlow {
                    GitHubPageContentInput(
                        trackedItems = state.trackedItems.toList(),
                        trackedSearch = state.trackedSearch,
                        trackedFilterMode = state.trackedFilterMode,
                        sortMode = state.sortMode,
                        sortDirection = state.sortDirection,
                        checkStates = state.checkStates.toMap(),
                        appList = state.appList.toList(),
                        trackedFirstInstallAtByPackage = state.trackedFirstInstallAtByPackage.toMap(),
                        trackedAddedAtById = state.trackedAddedAtById.toMap(),
                        trackedModifiedAtById = state.trackedModifiedAtById.toMap(),
                        pendingShareImportTrack = state.pendingShareImportTrack,
                        selfPackageName = BuildConfig.APPLICATION_ID,
                        nowMillis = 0L
                    )
                }
                    .conflate()
                    .debounce(CONTENT_DERIVATION_DEBOUNCE_MS.milliseconds)
                    .distinctUntilChanged(),
                pendingShareImportNowMillis
            ) { input, nowMillis ->
                input.copy(nowMillis = nowMillis)
            }.collectLatest { input ->
                val derived = repository.buildContentState(input)
                if (shouldResetFailedTrackedFilter(input, derived)) {
                    state.trackedFilterMode = GitHubTrackedFilterMode.All
                    GitHubPageUiStateStore.setTrackedFilterMode(GitHubTrackedFilterMode.All)
                    _contentDerivedState.value = repository.buildContentState(
                        input.copy(trackedFilterMode = GitHubTrackedFilterMode.All)
                    )
                } else {
                    _contentDerivedState.value = derived
                }
            }
        }
    }

    override fun onCleared() {
        contentStateJob?.cancel()
        pendingShareImportClockJob?.cancel()
        onlineShareTargetsJob?.cancel()
        downloaderOptionsJob?.cancel()
        snapshotFlowManager.dispose()
        super.onCleared()
    }
}

internal fun shouldResetFailedTrackedFilter(
    input: GitHubPageContentInput,
    derived: GitHubPageContentDerivedState
): Boolean {
    return input.trackedFilterMode == GitHubTrackedFilterMode.FailedChecks &&
        input.trackedItems.isNotEmpty() &&
        derived.trackedUi.overviewMetrics.failedCount == 0
}
