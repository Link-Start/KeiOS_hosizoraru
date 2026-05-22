@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord

@Composable
internal fun GitHubShareImportWindowFlowEffects(
    context: Context,
    flowState: GitHubShareImportWindowFlowStateHolder,
    windowCoordinator: GitHubShareImportWindowCoordinator,
    incomingGitHubShareText: String?,
    incomingGitHubShareToken: Int,
    resumeRequestToken: Int,
    showPendingArmedSheet: Boolean,
    onIncomingGitHubShareConsumed: () -> Unit,
    onNotificationOnlyResolveChanged: ((Boolean) -> Unit)?,
    onActivityBackInterceptionChanged: ((Boolean) -> Unit)?,
    onMinimizeActiveFlow: (() -> Unit)?,
    onIdleWithNoPendingFlow: (() -> Unit)?,
) {
    val snapshot = flowState.snapshot
    GitHubShareImportWindowCallbackEffects(
        snapshot = snapshot,
        showPendingArmedSheet = showPendingArmedSheet,
        onNotificationOnlyResolveChanged = onNotificationOnlyResolveChanged,
        onActivityBackInterceptionChanged = onActivityBackInterceptionChanged,
    )
    GitHubShareImportWindowRestoreEffects(
        context = context,
        flowState = flowState,
        windowCoordinator = windowCoordinator,
        incomingGitHubShareText = incomingGitHubShareText,
        resumeRequestToken = resumeRequestToken,
    )
    GitHubShareImportWindowResolveEffects(
        context = context,
        flowState = flowState,
        windowCoordinator = windowCoordinator,
        snapshot = snapshot,
        incomingGitHubShareText = incomingGitHubShareText,
        incomingGitHubShareToken = incomingGitHubShareToken,
        onIncomingGitHubShareConsumed = onIncomingGitHubShareConsumed,
        onMinimizeActiveFlow = onMinimizeActiveFlow,
    )
    BindGitHubShareImportIdleCallback(
        restoringActiveFlow = snapshot.restoringActiveFlow,
        resolving = snapshot.resolving,
        incomingResolveRunning = snapshot.incomingResolveRunning,
        pendingPreview = snapshot.pendingPreview,
        pendingTrack = snapshot.pendingTrack,
        attachCandidate = snapshot.attachCandidate,
        incomingGitHubShareText = incomingGitHubShareText,
        onIdleWithNoPendingFlow = onIdleWithNoPendingFlow,
    )
}

@Composable
internal fun BindGitHubShareImportIdleCallback(
    restoringActiveFlow: Boolean,
    resolving: Boolean,
    incomingResolveRunning: Boolean,
    pendingPreview: GitHubShareImportPreview?,
    pendingTrack: GitHubPendingShareImportTrackRecord?,
    attachCandidate: GitHubPendingShareImportAttachCandidate?,
    incomingGitHubShareText: String?,
    onIdleWithNoPendingFlow: (() -> Unit)?,
) {
    var idleCallbackDispatched by remember { mutableStateOf(false) }
    LaunchedEffect(
        restoringActiveFlow,
        resolving,
        incomingResolveRunning,
        pendingPreview,
        pendingTrack?.armedAtMillis,
        attachCandidate,
        incomingGitHubShareText,
        onIdleWithNoPendingFlow,
    ) {
        val onIdle = onIdleWithNoPendingFlow ?: return@LaunchedEffect
        val hasIncomingShareText = !incomingGitHubShareText.isNullOrBlank()
        val hasActiveFlow =
            restoringActiveFlow ||
                resolving ||
                incomingResolveRunning ||
                pendingPreview != null ||
                pendingTrack != null ||
                attachCandidate != null
        if (hasIncomingShareText || hasActiveFlow) {
            idleCallbackDispatched = false
            return@LaunchedEffect
        }
        if (idleCallbackDispatched) return@LaunchedEffect
        idleCallbackDispatched = true
        onIdle()
    }
}
