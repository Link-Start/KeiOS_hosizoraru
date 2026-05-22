@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import os.kei.feature.github.data.local.GitHubTrackStoreSignals

@Composable
internal fun GitHubShareImportWindowCallbackEffects(
    snapshot: GitHubShareImportWindowFlowSnapshot,
    showPendingArmedSheet: Boolean,
    onNotificationOnlyResolveChanged: ((Boolean) -> Unit)?,
    onActivityBackInterceptionChanged: ((Boolean) -> Unit)?,
) {
    LaunchedEffect(snapshot.notificationOnlyIncomingResolve) {
        onNotificationOnlyResolveChanged?.invoke(snapshot.notificationOnlyIncomingResolve)
    }

    val pendingArmedSheetVisible = snapshot.pendingArmedSheetVisible(showPendingArmedSheet)
    LaunchedEffect(pendingArmedSheetVisible) {
        onActivityBackInterceptionChanged?.invoke(pendingArmedSheetVisible)
    }
}

@Composable
internal fun GitHubShareImportWindowRestoreEffects(
    context: Context,
    flowState: GitHubShareImportWindowFlowStateHolder,
    windowCoordinator: GitHubShareImportWindowCoordinator,
    incomingGitHubShareText: String?,
    resumeRequestToken: Int,
) {
    LaunchedEffect(Unit) {
        GitHubTrackStoreSignals.version.collect {
            if (!flowState.resolving && !flowState.incomingResolveRunning && !flowState.attachSubmitting) {
                flowState.applyStoredSnapshot(windowCoordinator.loadStoredSnapshot())
            }
        }
    }

    LaunchedEffect(resumeRequestToken) {
        restoreGitHubShareImportWindowFlow(
            context = context,
            flowState = flowState,
            windowCoordinator = windowCoordinator,
            incomingGitHubShareText = incomingGitHubShareText,
        )
    }
}

private suspend fun restoreGitHubShareImportWindowFlow(
    context: Context,
    flowState: GitHubShareImportWindowFlowStateHolder,
    windowCoordinator: GitHubShareImportWindowCoordinator,
    incomingGitHubShareText: String?,
) {
    flowState.updateRestoringActiveFlow(true)
    try {
        if (!incomingGitHubShareText.isNullOrBlank()) return
        if (flowState.resolving || flowState.incomingResolveRunning || flowState.attachCandidate != null) return
        when (val restored = windowCoordinator.restoreActiveFlow(context)) {
            is GitHubShareImportRestoreResult.Snapshot -> {
                flowState.applyStoredSnapshot(restored.snapshot)
            }

            is GitHubShareImportRestoreResult.Coordinator -> {
                flowState.applyCoordinatorResult(restored.result)
            }
        }
    } finally {
        flowState.updateRestoringActiveFlow(false)
    }
}
