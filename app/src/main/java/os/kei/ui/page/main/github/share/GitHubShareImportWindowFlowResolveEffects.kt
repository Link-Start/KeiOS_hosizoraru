@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.system.AppPackageChangedEvents
import os.kei.feature.github.data.local.GitHubShareImportFlowStore

@Composable
internal fun GitHubShareImportWindowResolveEffects(
    context: Context,
    flowState: GitHubShareImportWindowFlowStateHolder,
    windowCoordinator: GitHubShareImportWindowCoordinator,
    snapshot: GitHubShareImportWindowFlowSnapshot,
    incomingGitHubShareText: String?,
    incomingGitHubShareToken: Int,
    onIncomingGitHubShareConsumed: () -> Unit,
    onMinimizeActiveFlow: (() -> Unit)?,
) {
    LaunchedEffect(snapshot.pendingTrack?.armedAtMillis, snapshot.attachCandidate?.packageName) {
        awaitGitHubShareImportPendingResolution(
            context = context,
            flowState = flowState,
            windowCoordinator = windowCoordinator,
        )
    }

    LaunchedEffect(incomingGitHubShareToken) {
        startGitHubShareImportIncomingResolve(
            context = context,
            flowState = flowState,
            incomingGitHubShareText = incomingGitHubShareText,
            onIncomingGitHubShareConsumed = onIncomingGitHubShareConsumed,
            onMinimizeActiveFlow = onMinimizeActiveFlow,
        )
    }

    LaunchedEffect(snapshot.pendingTrack?.armedAtMillis) {
        collectGitHubShareImportPackageEvents(
            context = context,
            flowState = flowState,
        )
    }

    LaunchedEffect(snapshot.attachCandidate) {
        val candidate = snapshot.attachCandidate
        if (candidate == null) {
            flowState.updateAttachDuplicateExists(false)
            flowState.updateAttachSubmitting(submitting = false, confirmAndOpen = false)
            return@LaunchedEffect
        }
        flowState.updatePhase(GitHubShareImportPhase.InstallDetected)
        flowState.updateAttachDuplicateExists(windowCoordinator.hasAttachDuplicate(candidate))
    }
}

private suspend fun awaitGitHubShareImportPendingResolution(
    context: Context,
    flowState: GitHubShareImportWindowFlowStateHolder,
    windowCoordinator: GitHubShareImportWindowCoordinator,
) {
    val armedAtMillis = flowState.pendingTrack?.armedAtMillis ?: return
    if (flowState.attachCandidate != null) return
    when (val result = windowCoordinator.awaitPendingResolution(context, armedAtMillis)) {
        is ShareImportCoordinatorResult.Pending,
        ShareImportCoordinatorResult.None,
        -> {
            Unit
        }

        else -> {
            flowState.applyCoordinatorResult(result)
            if (result is ShareImportCoordinatorResult.AlreadyTracked) {
                toast(context, R.string.github_toast_share_import_track_exists)
            }
        }
    }
}

private suspend fun startGitHubShareImportIncomingResolve(
    context: Context,
    flowState: GitHubShareImportWindowFlowStateHolder,
    incomingGitHubShareText: String?,
    onIncomingGitHubShareConsumed: () -> Unit,
    onMinimizeActiveFlow: (() -> Unit)?,
) {
    val sharedText = incomingGitHubShareText?.trim().orEmpty()
    if (sharedText.isBlank()) return
    if (flowState.resolving || flowState.incomingResolveRunning) return
    flowState.updateIncomingResolveRunning(true)
    onIncomingGitHubShareConsumed()
    flowState.clearActiveFlowForIncomingResolve()
    withContext(AppDispatchers.githubNetwork) {
        GitHubShareImportFlowStore.clearActiveFlow()
    }
    try {
        flowState.updateResolving(true)
        flowState.updatePhase(GitHubShareImportPhase.Resolving)
        val incomingResult =
            GitHubShareImportFlowCoordinator.startIncomingShare(
                context = context,
                sharedText = sharedText,
            )
        if (incomingResult.notificationFirst) {
            flowState.updateNotificationOnlyIncomingResolve(true)
            onMinimizeActiveFlow?.invoke()
            return
        }
        flowState.updateNotificationOnlyIncomingResolve(false)
        flowState.applyCoordinatorResult(incomingResult.coordinatorResult)
        val toastResId = incomingResult.toastResId
        if (toastResId != null) {
            if (incomingResult.toastMessage.isBlank()) {
                toast(context, toastResId)
            } else {
                toast(context, toastResId, incomingResult.toastMessage)
            }
        }
    } finally {
        flowState.updateResolving(false)
        flowState.updateIncomingResolveRunning(false)
        flowState.updateNotificationOnlyIncomingResolve(false)
    }
}

private suspend fun collectGitHubShareImportPackageEvents(
    context: Context,
    flowState: GitHubShareImportWindowFlowStateHolder,
) {
    val armedAtMillis = flowState.pendingTrack?.armedAtMillis ?: return
    AppPackageChangedEvents.events.collect { event ->
        val packageName = event.packageName.trim()
        if (packageName.isBlank()) return@collect
        val currentPending = flowState.pendingTrack ?: return@collect
        if (currentPending.armedAtMillis != armedAtMillis) return@collect
        if (event.action !in shareImportAttachActions) return@collect
        val expectedPackageName = currentPending.packageName.trim()
        if (expectedPackageName.isNotBlank() && packageName != expectedPackageName) return@collect

        when (val result = GitHubShareImportFlowCoordinator.refreshPendingInstall(context, event)) {
            is ShareImportCoordinatorResult.Pending,
            ShareImportCoordinatorResult.None,
            -> {
                Unit
            }

            else -> {
                flowState.applyCoordinatorResult(result)
                if (result is ShareImportCoordinatorResult.AlreadyTracked) {
                    toast(context, R.string.github_toast_share_import_track_exists)
                }
            }
        }
    }
}
