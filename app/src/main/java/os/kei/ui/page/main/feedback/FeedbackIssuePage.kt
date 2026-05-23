@file:Suppress("FunctionName")

package os.kei.ui.page.main.feedback

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.back.KeiOSActivityRootBackHandler
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

@Composable
internal fun FeedbackIssuePage(
    state: FeedbackIssueUiState,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onExportZip: () -> Unit,
    onClearLogs: () -> Unit,
    onRequestSubmit: (FeedbackSubmitMode) -> Unit,
    onDismissSubmit: () -> Unit,
    onConfirmBrowserSubmit: () -> Unit,
    onConfirmApiSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)

    KeiOSActivityRootBackHandler(
        needsInterception = state.pendingSubmitMode != null || state.submittingIssue,
        onBack = {
            if (state.pendingSubmitMode != null) {
                onDismissSubmit()
            } else {
                onClose()
            }
        },
    )

    AppPageScaffold(
        title = stringResource(R.string.feedback_issue_title),
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
        titleBackdrop = pageBackdrop,
        reserveTopEndActionSpace = false,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onClose,
                backdrop = pageBackdrop,
            )
        },
    ) { innerPadding ->
        FeedbackIssueContent(
            state = state,
            innerPadding = innerPadding,
            listState = listState,
            nestedScrollConnection = scrollBehavior.nestedScrollConnection,
            pageBackdrop = pageBackdrop,
            onTitleChange = onTitleChange,
            onBodyChange = onBodyChange,
            onRefresh = onRefresh,
            onExportZip = onExportZip,
            onClearLogs = onClearLogs,
            onRequestSubmit = onRequestSubmit,
        )
    }

    FeedbackSubmitConfirmDialog(
        mode = state.pendingSubmitMode,
        apiTokenAvailable = state.apiTokenAvailable,
        submitting = state.submittingIssue,
        onDismiss = onDismissSubmit,
        onConfirmBrowser = onConfirmBrowserSubmit,
        onConfirmApi = onConfirmApiSubmit,
    )
}
