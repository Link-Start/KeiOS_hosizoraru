package os.kei.ui.page.main.github.importer

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.domain.GitHubStarImportClassifier
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportQuality
import os.kei.feature.github.model.GitHubStarListSummary
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.feature.github.model.StarImportApplyResult

@Immutable
internal data class GitHubStarImportUiState(
    val source: StarImportUiSource = StarImportUiSource.MyStars,
    val apiTokenAvailable: Boolean = false,
    val usernameInput: String = "",
    val listUrlInput: String = "",
    val filterInput: String = "",
    val viewFilter: StarImportViewFilter = StarImportViewFilter.All,
    val qualityFilters: Set<GitHubStarImportQuality> = defaultVisibleStarImportQualities(),
    val conflictStrategy: StarImportConflictStrategy = StarImportConflictStrategy.NewOnly,
    val preview: GitHubStarredRepositoryImportPreview? = null,
    val selectedIds: Set<String> = emptySet(),
    val apkVerificationStates: Map<String, StarImportApkVerificationUiState> = emptyMap(),
    val pendingImportCandidates: List<GitHubRepositoryImportCandidate> = emptyList(),
    val showExitConfirm: Boolean = false,
    val starLists: List<GitHubStarListSummary> = emptyList(),
    val loading: Boolean = false,
    val loadingProgress: Float = 0f,
    val loadingPhaseRes: Int? = null,
    val readyStarListCount: Int = 0,
    val importing: Boolean = false,
    val error: String? = null,
    val listUiState: StarImportCandidateListUiState = buildStarImportCandidateListUiState(
        candidates = emptyList(),
        filterInput = "",
        viewFilter = StarImportViewFilter.All,
        qualityFilters = defaultVisibleStarImportQualities(),
        conflictStrategy = StarImportConflictStrategy.NewOnly,
        selectedIds = emptySet(),
        verificationStates = emptyMap()
    )
) {
    val lookupConfigReady: Boolean
        get() = source.isReady(
            token = if (apiTokenAvailable) "token" else "",
            username = usernameInput,
            listUrl = listUrlInput
        )

    val candidates: List<GitHubRepositoryImportCandidate>
        get() = preview?.candidates.orEmpty()

    val selectedImportableCount: Int
        get() = listUiState.selectedImportableCount

    val importEnabled: Boolean
        get() = selectedImportableCount > 0 && !loading && !importing

    val hasPendingImportWork: Boolean
        get() = selectedImportableCount > 0 || pendingImportCandidates.isNotEmpty()
}

internal sealed interface GitHubStarImportEvent {
    data class Imported(val result: StarImportApplyResult) : GitHubStarImportEvent
    data object Close : GitHubStarImportEvent
}

internal class GitHubStarImportViewModel(
    private val repository: GitHubStarImportPageRepository = GitHubStarImportPageRepository(),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<GitHubStarImportUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GitHubStarImportEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<GitHubStarImportEvent> = _events.asSharedFlow()

    private var filterJob: Job? = null
    private var listDeriveJob: Job? = null
    private var previewJob: Job? = null
    private var verifyJob: Job? = null
    private var importJob: Job? = null

    init {
        viewModelScope.launch {
            GitHubTrackStoreSignals.version.collect {
                val snapshot = withContext(AppDispatchers.githubNetwork) { GitHubTrackStore.loadSnapshot() }
                _uiState.update { state ->
                    state.copy(apiTokenAvailable = snapshot.lookupConfig.apiToken.isNotBlank())
                }
            }
        }
    }

    fun updateSource(source: StarImportUiSource) {
        previewJob?.cancel()
        verifyJob?.cancel()
        _uiState.update { state -> state.copy(source = source) }
        resetSourceDependentState()
        saveDraft()
        rebuildListState()
    }

    fun updateUsernameInput(value: String) {
        _uiState.update { state -> state.copy(usernameInput = value) }
        saveDraft()
    }

    fun updateListUrlInput(value: String) {
        _uiState.update { state -> state.copy(listUrlInput = value) }
        saveDraft()
    }

    fun updateFilterInput(value: String) {
        _uiState.update { state -> state.copy(filterInput = value) }
        saveDraft()
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            delay(STAR_IMPORT_FILTER_DEBOUNCE_MS.milliseconds)
            rebuildListState()
        }
    }

    fun updateViewFilter(value: StarImportViewFilter) {
        _uiState.update { state -> state.copy(viewFilter = value) }
        saveDraft()
        rebuildListState()
    }

    fun toggleQualityFilter(quality: GitHubStarImportQuality) {
        _uiState.update { state ->
            val next = if (quality in state.qualityFilters) {
                state.qualityFilters - quality
            } else {
                state.qualityFilters + quality
            }
            state.copy(
                qualityFilters = next.ifEmpty { GitHubStarImportQuality.entries.toSet() }
            )
        }
        saveDraft()
        rebuildListState()
    }

    fun updateConflictStrategy(value: StarImportConflictStrategy) {
        _uiState.update { state ->
            val nextSelectedIds = if (value == StarImportConflictStrategy.NewOnly) {
                state.selectedIds - state.candidates
                    .asSequence()
                    .filter { it.alreadyTracked }
                    .map { it.trackedApp.id }
                    .toSet()
            } else {
                state.selectedIds
            }
            state.copy(
                conflictStrategy = value,
                selectedIds = nextSelectedIds
            )
        }
        saveDraft()
        rebuildListState()
    }

    fun selectRecommendedVisible() {
        _uiState.update { state ->
            state.copy(selectedIds = state.selectedIds + state.listUiState.visibleRecommendedIds)
        }
        saveDraft()
        rebuildListState()
    }

    fun selectVerifiedVisible() {
        _uiState.update { state ->
            state.copy(selectedIds = state.selectedIds + state.listUiState.visibleVerifiedApkIds)
        }
        saveDraft()
        rebuildListState()
    }

    fun selectVisible() {
        _uiState.update { state ->
            state.copy(selectedIds = state.selectedIds + state.listUiState.visibleImportableIds)
        }
        saveDraft()
        rebuildListState()
    }

    fun clearSelection() {
        _uiState.update { state -> state.copy(selectedIds = emptySet()) }
        saveDraft()
        rebuildListState()
    }

    fun toggleCandidate(candidate: GitHubRepositoryImportCandidate) {
        _uiState.update { state ->
            if (
                candidate.alreadyTracked &&
                state.conflictStrategy != StarImportConflictStrategy.IncludeTracked
            ) {
                return@update state
            }
            val id = candidate.trackedApp.id
            val nextSelectedIds = if (id in state.selectedIds) {
                state.selectedIds - id
            } else {
                state.selectedIds + id
            }
            state.copy(selectedIds = nextSelectedIds)
        }
        saveDraft()
        rebuildListState()
    }

    fun requestClose() {
        val state = _uiState.value
        if (state.importing) return
        if (state.hasPendingImportWork) {
            _uiState.update { it.copy(showExitConfirm = true) }
        } else {
            _events.tryEmit(GitHubStarImportEvent.Close)
        }
    }

    fun dismissExitConfirm() {
        _uiState.update { it.copy(showExitConfirm = false) }
    }

    fun confirmExit() {
        _uiState.update { it.copy(showExitConfirm = false) }
        _events.tryEmit(GitHubStarImportEvent.Close)
    }

    fun dismissPendingImport() {
        _uiState.update { it.copy(pendingImportCandidates = emptyList()) }
    }

    fun requestImport() {
        val selected = _uiState.value.listUiState.selectedCandidates
        if (selected.isEmpty() || _uiState.value.importing) return
        _uiState.update { it.copy(pendingImportCandidates = selected) }
    }

    fun loadPreview(requirementMessage: String, forcedStarListUrl: String? = null) {
        val state = _uiState.value
        if (state.loading || state.importing) return
        val targetSource =
            if (forcedStarListUrl != null) StarImportUiSource.ListUrl else state.source
        val targetListUrl = forcedStarListUrl ?: state.listUrlInput.trim()
        if (
            !targetSource.isReady(
                token = if (state.apiTokenAvailable) "token" else "",
                username = state.usernameInput,
                listUrl = targetListUrl
            )
        ) {
            _uiState.update { it.copy(error = requirementMessage) }
            return
        }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = true,
                    loadingProgress = 0.08f,
                    loadingPhaseRes = R.string.github_star_import_loading_source,
                    readyStarListCount = 0,
                    error = null,
                    starLists = if (forcedStarListUrl == null) emptyList() else it.starLists
                )
            }
            val result = runCatching {
                repository.loadPreview(
                    request = StarImportLoadRequest(
                        source = state.source,
                        usernameInput = state.usernameInput.toGitHubUsernameInput(),
                        listUrlInput = targetListUrl,
                        forcedStarListUrl = forcedStarListUrl
                    ),
                    onProgress = { progress ->
                        _uiState.update {
                            it.copy(
                                loadingProgress = progress.progress,
                                loadingPhaseRes = progress.phaseRes,
                                readyStarListCount = 0
                            )
                        }
                    }
                )
            }
            result.onSuccess { loadResult ->
                when (loadResult) {
                    is StarImportLoadResult.Lists -> {
                        _uiState.update {
                            it.copy(
                                preview = null,
                                selectedIds = emptySet(),
                                starLists = loadResult.items,
                                loading = false,
                                loadingProgress = 1f,
                                loadingPhaseRes = null,
                                readyStarListCount = loadResult.items.size
                            )
                        }
                    }

                    is StarImportLoadResult.Preview -> {
                        val nextPreview = loadResult.preview
                        val importableIds = nextPreview.candidates
                            .map { it.trackedApp.id }
                            .toSet()
                        val previousSelected = _uiState.value.selectedIds
                        val restoredSelection = previousSelected.intersect(importableIds)
                        val nextSelected = restoredSelection.ifEmpty {
                            nextPreview.candidates
                                .filter { GitHubStarImportClassifier.isDefaultSelected(it) }
                                .map { it.trackedApp.id }
                                .toSet()
                        }
                        _uiState.update {
                            it.copy(
                                preview = nextPreview,
                                starLists = emptyList(),
                                apkVerificationStates = emptyMap(),
                                selectedIds = nextSelected,
                                loading = false,
                                loadingProgress = 1f,
                                loadingPhaseRes = R.string.github_star_import_loading_preview,
                                readyStarListCount = 0
                            )
                        }
                        rebuildListState()
                    }
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = throwable.message.orEmpty()
                            .ifBlank { throwable.javaClass.simpleName },
                        loadingPhaseRes = null,
                        readyStarListCount = 0
                    )
                }
            }
            saveDraft()
            rebuildListState()
        }
    }

    fun verifyApkAssets(targets: List<GitHubRepositoryImportCandidate>) {
        val state = _uiState.value
        if (state.loading || state.importing || targets.isEmpty()) return
        val uniqueTargets = targets
            .distinctBy { it.trackedApp.id }
            .take(MAX_APK_VERIFICATION_BATCH)
        if (uniqueTargets.isEmpty()) return
        _uiState.update { current ->
            val updated = current.apkVerificationStates.toMutableMap()
            uniqueTargets.forEach { candidate ->
                val id = candidate.trackedApp.id
                updated[id] = StarImportApkVerificationUiState(
                    checking = true,
                    verification = updated[id]?.verification
                )
            }
            current.copy(apkVerificationStates = updated)
        }
        rebuildListState()
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch {
            val results = repository.verifyApkAssets(uniqueTargets)
            _uiState.update { current ->
                val updated = current.apkVerificationStates.toMutableMap()
                results.forEach { (id, verification) ->
                    updated[id] = StarImportApkVerificationUiState(
                        checking = false,
                        verification = verification
                    )
                }
                current.copy(apkVerificationStates = updated)
            }
            rebuildListState()
        }
    }

    fun applyImport(context: Context, selected: List<GitHubRepositoryImportCandidate>) {
        if (selected.isEmpty() || _uiState.value.importing) return
        importJob?.cancel()
        importJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    importing = true,
                    pendingImportCandidates = emptyList()
                )
            }
            val result = runCatching {
                repository.importCandidates(
                    context = context.applicationContext,
                    candidates = selected,
                    verificationStates = _uiState.value.apkVerificationStates
                )
            }
            _uiState.update { it.copy(importing = false) }
            result.onSuccess { applyResult ->
                GitHubStarImportDraftStore.clearSelection()
                _events.emit(GitHubStarImportEvent.Imported(applyResult))
                _events.emit(GitHubStarImportEvent.Close)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        error = throwable.message.orEmpty()
                            .ifBlank { throwable.javaClass.simpleName })
                }
            }
        }
    }

    private fun resetSourceDependentState() {
        _uiState.update {
            it.copy(
                error = null,
                preview = null,
                selectedIds = emptySet(),
                pendingImportCandidates = emptyList(),
                showExitConfirm = false,
                apkVerificationStates = emptyMap(),
                starLists = emptyList(),
                filterInput = "",
                viewFilter = StarImportViewFilter.All,
                qualityFilters = defaultVisibleStarImportQualities(),
                loadingProgress = 0f,
                loadingPhaseRes = null,
                readyStarListCount = 0
            )
        }
    }

    private fun saveDraft() {
        val state = _uiState.value
        GitHubStarImportDraftStore.save(
            GitHubStarImportDraft(
                source = state.source,
                usernameInput = state.usernameInput,
                listUrlInput = state.listUrlInput,
                filterInput = state.filterInput,
                viewFilter = state.viewFilter,
                qualityFilters = state.qualityFilters,
                conflictStrategy = state.conflictStrategy,
                selectedIds = state.selectedIds
            )
        )
    }

    private fun rebuildListState() {
        listDeriveJob?.cancel()
        val snapshot = _uiState.value
        listDeriveJob = viewModelScope.launch {
            val derived = withContext(defaultDispatcher) {
                buildStarImportCandidateListUiState(
                    candidates = snapshot.candidates,
                    filterInput = snapshot.filterInput,
                    viewFilter = snapshot.viewFilter,
                    qualityFilters = snapshot.qualityFilters,
                    conflictStrategy = snapshot.conflictStrategy,
                    selectedIds = snapshot.selectedIds,
                    verificationStates = snapshot.apkVerificationStates
                )
            }
            _uiState.update { state ->
                state.copy(listUiState = derived)
            }
        }
    }

    private fun loadInitialState(): GitHubStarImportUiState {
        val snapshot = GitHubTrackStore.loadSnapshot()
        val savedDraft = GitHubStarImportDraftStore.load()
        return GitHubStarImportUiState(
            source = savedDraft.source,
            apiTokenAvailable = snapshot.lookupConfig.apiToken.isNotBlank(),
            usernameInput = savedDraft.usernameInput,
            listUrlInput = savedDraft.listUrlInput,
            filterInput = savedDraft.filterInput,
            viewFilter = savedDraft.viewFilter,
            qualityFilters = savedDraft.qualityFilters,
            conflictStrategy = savedDraft.conflictStrategy,
            selectedIds = savedDraft.selectedIds
        )
    }

    private companion object {
        const val STAR_IMPORT_FILTER_DEBOUNCE_MS = 150L
        const val MAX_APK_VERIFICATION_BATCH = 30
    }
}
