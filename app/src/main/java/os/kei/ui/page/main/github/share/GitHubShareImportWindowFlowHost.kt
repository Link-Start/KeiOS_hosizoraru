@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import os.kei.feature.github.model.GitHubShareImportFlowMode

@Composable
internal fun GitHubShareImportWindowFlowHost(
    incomingGitHubShareText: String?,
    incomingGitHubShareToken: Int,
    resumeRequestToken: Int = 0,
    onIncomingGitHubShareConsumed: () -> Unit,
    onNavigateToGitHubPage: () -> Unit,
    showPendingArmedSheet: Boolean = false,
    onNotificationOnlyResolveChanged: ((Boolean) -> Unit)? = null,
    onActivityBackInterceptionChanged: ((Boolean) -> Unit)? = null,
    onMinimizeActiveFlow: (() -> Unit)? = null,
    onClosePendingArmedSheet: (() -> Unit)? = null,
    onIdleWithNoPendingFlow: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val windowCoordinator = remember { GitHubShareImportWindowCoordinator() }
    val installFlowCoordinator = remember { GitHubShareImportInstallFlowCoordinator() }
    val flowState = rememberGitHubShareImportWindowFlowStateHolder()
    val latestBackInterceptionCallback = rememberUpdatedState(onActivityBackInterceptionChanged)
    val latestNavigateToGitHubPage = rememberUpdatedState(onNavigateToGitHubPage)
    val sheetActions =
        remember(context, scope, flowState, installFlowCoordinator) {
            GitHubShareImportWindowFlowSheetActions(
                context = context,
                scope = scope,
                flowState = flowState,
                installFlowCoordinator = installFlowCoordinator,
                onNavigateToGitHubPage = { latestNavigateToGitHubPage.value() },
            )
        }

    DisposableEffect(Unit) {
        onDispose {
            latestBackInterceptionCallback.value?.invoke(false)
        }
    }

    GitHubShareImportWindowFlowEffects(
        context = context,
        flowState = flowState,
        windowCoordinator = windowCoordinator,
        incomingGitHubShareText = incomingGitHubShareText,
        incomingGitHubShareToken = incomingGitHubShareToken,
        resumeRequestToken = resumeRequestToken,
        showPendingArmedSheet = showPendingArmedSheet,
        onIncomingGitHubShareConsumed = onIncomingGitHubShareConsumed,
        onNotificationOnlyResolveChanged = onNotificationOnlyResolveChanged,
        onActivityBackInterceptionChanged = latestBackInterceptionCallback.value,
        onMinimizeActiveFlow = onMinimizeActiveFlow,
        onIdleWithNoPendingFlow = onIdleWithNoPendingFlow,
    )

    val snapshot = flowState.snapshot
    GitHubShareImportWindowFlowSheetRoute(
        snapshot = snapshot,
        sheetActions = sheetActions,
        pendingArmedSheetVisible = snapshot.pendingArmedSheetVisible(showPendingArmedSheet),
        onMinimizeActiveFlow = onMinimizeActiveFlow,
        onClosePendingArmedSheet = onClosePendingArmedSheet,
    )
}

internal fun shouldUseNotificationFirstFlow(
    flowMode: GitHubShareImportFlowMode,
    assetCount: Int,
): Boolean = flowMode == GitHubShareImportFlowMode.NotificationFirst && assetCount > 0
