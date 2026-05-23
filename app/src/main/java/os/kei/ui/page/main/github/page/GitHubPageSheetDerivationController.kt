package os.kei.ui.page.main.github.page

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.core.ui.snapshot.AppSnapshotFlowManager
import os.kei.ui.page.main.github.actions.GitHubActionsSheetInput
import os.kei.ui.page.main.github.actions.GitHubActionsSheetUiState
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheetInput
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheetUiState
import os.kei.ui.page.main.github.sheet.GitHubManagedInstallConfirmSheetInput
import os.kei.ui.page.main.github.sheet.GitHubManagedInstallConfirmSheetUiState
import os.kei.ui.page.main.github.sheet.GitHubReleaseNotesDetailInput
import os.kei.ui.page.main.github.sheet.GitHubReleaseNotesDetailUiState
import kotlin.time.Duration.Companion.milliseconds

private const val ACTIONS_SHEET_DERIVATION_DEBOUNCE_MS = 32L

internal class GitHubPageSheetDerivationController(
    private val scope: CoroutineScope,
    private val repository: GitHubPageRepository,
    private val snapshotFlowManager: AppSnapshotFlowManager,
) {
    private var apkInfoSheetStateJob: Job? = null
    private var apkInfoSheetInput: GitHubApkInfoSheetInput? = null
    private var releaseNotesDetailStateJob: Job? = null
    private var releaseNotesDetailInput: GitHubReleaseNotesDetailInput? = null
    private var managedInstallConfirmSheetStateJob: Job? = null
    private var managedInstallConfirmSheetInput: GitHubManagedInstallConfirmSheetInput? = null
    private var actionsSheetStateJob: Job? = null

    private val mutableApkInfoSheetState = MutableStateFlow(GitHubApkInfoSheetUiState())
    private val mutableReleaseNotesDetailState = MutableStateFlow(GitHubReleaseNotesDetailUiState())
    private val mutableManagedInstallConfirmSheetState = MutableStateFlow(GitHubManagedInstallConfirmSheetUiState())
    private val mutableActionsSheetState = MutableStateFlow(GitHubActionsSheetUiState())

    val apkInfoSheetState: StateFlow<GitHubApkInfoSheetUiState> =
        mutableApkInfoSheetState.asStateFlow()
    val releaseNotesDetailState: StateFlow<GitHubReleaseNotesDetailUiState> =
        mutableReleaseNotesDetailState.asStateFlow()
    val managedInstallConfirmSheetState: StateFlow<GitHubManagedInstallConfirmSheetUiState> =
        mutableManagedInstallConfirmSheetState.asStateFlow()
    val actionsSheetState: StateFlow<GitHubActionsSheetUiState> =
        mutableActionsSheetState.asStateFlow()

    fun requestApkInfoSheetState(input: GitHubApkInfoSheetInput) {
        val currentState = mutableApkInfoSheetState.value
        val normalizedInput =
            if (currentState.assetKey == input.assetKey) {
                input.copy(query = currentState.query)
            } else {
                input.copy(query = "")
            }
        requestApkInfoSheetDerivation(normalizedInput)
    }

    fun updateApkInfoSheetQuery(query: String) {
        val currentInput = apkInfoSheetInput ?: return
        requestApkInfoSheetDerivation(currentInput.copy(query = query))
    }

    fun clearApkInfoSheetState() {
        apkInfoSheetStateJob?.cancel()
        apkInfoSheetStateJob = null
        apkInfoSheetInput = null
        mutableApkInfoSheetState.value = GitHubApkInfoSheetUiState()
    }

    fun requestReleaseNotesDetailState(input: GitHubReleaseNotesDetailInput) {
        if (releaseNotesDetailInput == input && !mutableReleaseNotesDetailState.value.deriving) return
        releaseNotesDetailInput = input
        releaseNotesDetailStateJob?.cancel()
        mutableReleaseNotesDetailState.update { state ->
            state.copy(
                requestKey = input.requestKey,
                deriving = true,
            )
        }
        releaseNotesDetailStateJob =
            scope.launch {
                val derivedState = repository.buildReleaseNotesDetailState(input)
                if (releaseNotesDetailInput != input) return@launch
                mutableReleaseNotesDetailState.value = derivedState
            }
    }

    fun clearReleaseNotesDetailState() {
        releaseNotesDetailStateJob?.cancel()
        releaseNotesDetailStateJob = null
        releaseNotesDetailInput = null
        mutableReleaseNotesDetailState.value = GitHubReleaseNotesDetailUiState()
    }

    fun requestManagedInstallConfirmSheetState(input: GitHubManagedInstallConfirmSheetInput) {
        if (
            managedInstallConfirmSheetInput == input &&
            mutableManagedInstallConfirmSheetState.value.requestKey == input.requestKey
        ) {
            return
        }
        managedInstallConfirmSheetInput = input
        managedInstallConfirmSheetStateJob?.cancel()
        managedInstallConfirmSheetStateJob =
            scope.launch {
                val derivedState = repository.buildManagedInstallConfirmSheetState(input)
                if (managedInstallConfirmSheetInput != input) return@launch
                mutableManagedInstallConfirmSheetState.value = derivedState
            }
    }

    fun clearManagedInstallConfirmSheetState() {
        managedInstallConfirmSheetStateJob?.cancel()
        managedInstallConfirmSheetStateJob = null
        managedInstallConfirmSheetInput = null
        mutableManagedInstallConfirmSheetState.value = GitHubManagedInstallConfirmSheetUiState()
    }

    @OptIn(FlowPreview::class)
    fun bindActionsSheetState(state: GitHubPageState) {
        actionsSheetStateJob?.cancel()
        actionsSheetStateJob =
            scope.launch {
                snapshotFlowManager
                    .snapshotFlow {
                        GitHubActionsSheetInput(
                            visible = state.showActionsSheet,
                            loading = state.actionsLoading,
                            runsLoading = state.actionsRunsLoading,
                            workflows = state.actionsWorkflows,
                            runs = state.actionsRuns,
                            selectedWorkflowId = state.actionsSelectedWorkflowId,
                            selectedRunId = state.actionsSelectedRunId,
                            refreshingRunIds = state.actionsStatusRefreshingRunIds.toMap(),
                            artifactFilter = state.actionsArtifactFilter,
                            lookupConfig = state.lookupConfig,
                        )
                    }.conflate()
                    .debounce(ACTIONS_SHEET_DERIVATION_DEBOUNCE_MS.milliseconds)
                    .distinctUntilChanged()
                    .collectLatest { input ->
                        mutableActionsSheetState.value = repository.buildActionsSheetState(input)
                    }
            }
    }

    fun cancel() {
        apkInfoSheetStateJob?.cancel()
        releaseNotesDetailStateJob?.cancel()
        managedInstallConfirmSheetStateJob?.cancel()
        actionsSheetStateJob?.cancel()
    }

    private fun requestApkInfoSheetDerivation(input: GitHubApkInfoSheetInput) {
        if (apkInfoSheetInput == input && !mutableApkInfoSheetState.value.deriving) return
        apkInfoSheetInput = input
        apkInfoSheetStateJob?.cancel()
        mutableApkInfoSheetState.update { state ->
            state.copy(
                assetKey = input.assetKey,
                query = input.query,
                normalizedQuery = input.query.trim(),
                deriving = true,
            )
        }
        apkInfoSheetStateJob =
            scope.launch {
                val derivedState = repository.buildApkInfoSheetState(input)
                if (apkInfoSheetInput != input) return@launch
                mutableApkInfoSheetState.value = derivedState
            }
    }
}
