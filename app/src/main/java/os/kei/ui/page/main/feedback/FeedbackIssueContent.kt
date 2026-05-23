@file:Suppress("FunctionName")

package os.kei.ui.page.main.feedback

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn

@Composable
internal fun FeedbackIssueContent(
    state: FeedbackIssueUiState,
    innerPadding: PaddingValues,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    pageBackdrop: LayerBackdrop,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onExportZip: () -> Unit,
    onClearLogs: () -> Unit,
    onRequestSubmit: (FeedbackSubmitMode) -> Unit,
) {
    AppPageLazyColumn(
        innerPadding = innerPadding,
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .layerBackdrop(pageBackdrop),
        sectionSpacing = 10.dp,
    ) {
        item(key = "feedback-status", contentType = "feedback_status") {
            FeedbackStatusCard(
                state = state,
                onRefresh = onRefresh,
            )
        }
        item(key = "feedback-draft", contentType = "feedback_draft") {
            FeedbackDraftCard(
                title = state.title,
                body = state.body,
                loading = state.loading,
                submitting = state.submittingIssue,
                onTitleChange = onTitleChange,
                onBodyChange = onBodyChange,
            )
        }
        item(key = "feedback-log", contentType = "feedback_log") {
            FeedbackLogCard(
                state = state,
                onExportZip = onExportZip,
                onClearLogs = onClearLogs,
            )
        }
        item(key = "feedback-device", contentType = "feedback_device") {
            FeedbackDeviceInfoCard(deviceInfo = state.deviceInfo)
        }
        item(key = "feedback-submit", contentType = "feedback_submit") {
            FeedbackSubmitCard(
                state = state,
                onRequestSubmit = onRequestSubmit,
            )
        }
    }
}
