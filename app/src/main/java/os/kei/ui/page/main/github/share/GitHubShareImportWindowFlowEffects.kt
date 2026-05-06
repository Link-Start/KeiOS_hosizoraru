package os.kei.ui.page.main.github.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord

@Composable
internal fun BindGitHubShareImportIdleCallback(
    restoringActiveFlow: Boolean,
    resolving: Boolean,
    incomingResolveRunning: Boolean,
    pendingPreview: GitHubShareImportPreview?,
    pendingTrack: GitHubPendingShareImportTrackRecord?,
    attachCandidate: GitHubPendingShareImportAttachCandidate?,
    incomingGitHubShareText: String?,
    onIdleWithNoPendingFlow: (() -> Unit)?
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
        onIdleWithNoPendingFlow
    ) {
        val onIdle = onIdleWithNoPendingFlow ?: return@LaunchedEffect
        val hasIncomingShareText = !incomingGitHubShareText.isNullOrBlank()
        val hasActiveFlow = restoringActiveFlow ||
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
