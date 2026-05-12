package os.kei.ui.page.main.feedback

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class FeedbackIssueViewModel : ViewModel() {
    private val repository = FeedbackIssueRepository()
    private val _uiState = MutableStateFlow(FeedbackIssueUiState())
    val uiState: StateFlow<FeedbackIssueUiState> = _uiState.asStateFlow()

    fun refresh(context: Context) {
        viewModelScope.launch {
            val appContext = context.applicationContext
            _uiState.update { state -> state.copy(loading = true, errorMessage = "") }
            val deviceInfo = repository.loadDeviceInfo(appContext)
            val logStats = repository.loadLogStats(appContext)
            val logPreview = repository.loadLogPreview(appContext)
            val sanitizedLogPreview = FeedbackIssueMarkdown.redactSensitiveText(logPreview.text)
            val title = _uiState.value.title.ifBlank {
                FeedbackIssueMarkdown.defaultTitle(deviceInfo)
            }
            val body = _uiState.value.body.ifBlank {
                FeedbackIssueMarkdown.buildBody(
                    deviceInfo = deviceInfo,
                    logPreview = sanitizedLogPreview,
                    logPreviewTruncated = logPreview.truncated
                )
            }
            _uiState.update { state ->
                state.copy(
                    loading = false,
                    deviceInfo = deviceInfo,
                    logStats = logStats,
                    logPreview = sanitizedLogPreview,
                    logPreviewTruncated = logPreview.truncated,
                    apiTokenAvailable = repository.hasGitHubApiToken(),
                    title = title,
                    body = body
                )
            }
        }
    }

    fun updateTitle(value: String) {
        _uiState.update { state -> state.copy(title = value) }
    }

    fun updateBody(value: String) {
        _uiState.update { state -> state.copy(body = value) }
    }

    fun requestSubmit(mode: FeedbackSubmitMode) {
        _uiState.update { state ->
            state.copy(
                pendingSubmitMode = mode,
                errorMessage = "",
                statusMessage = ""
            )
        }
    }

    fun dismissSubmitConfirmation() {
        _uiState.update { state -> state.copy(pendingSubmitMode = null) }
    }

    fun buildBrowserIssueUrl(): String {
        val state = _uiState.value
        return FeedbackIssueMarkdown.buildBrowserIssueUrl(
            title = state.title,
            body = state.body,
            deviceInfo = state.deviceInfo
        )
    }

    fun submitViaApi(
        context: Context,
        onSuccessOpenUrl: (String) -> Unit
    ) {
        val title = _uiState.value.title.trim()
        val body = _uiState.value.body.trim()
        if (title.isBlank() || body.isBlank()) {
            _uiState.update { state ->
                state.copy(
                    pendingSubmitMode = null,
                    errorMessage = context.getString(os.kei.R.string.feedback_issue_error_empty_draft)
                )
            }
            return
        }
        if (_uiState.value.submittingIssue) return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    pendingSubmitMode = null,
                    submittingIssue = true,
                    errorMessage = "",
                    statusMessage = ""
                )
            }
            when (val result = repository.submitIssueViaApi(title = title, body = body)) {
                is FeedbackIssueSubmitResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            submittingIssue = false,
                            statusMessage = context.getString(os.kei.R.string.feedback_issue_status_created)
                        )
                    }
                    onSuccessOpenUrl(result.issueUrl)
                }

                FeedbackIssueSubmitResult.MissingToken -> {
                    _uiState.update { state ->
                        state.copy(
                            submittingIssue = false,
                            errorMessage = context.getString(os.kei.R.string.feedback_issue_error_missing_token),
                            apiTokenAvailable = false
                        )
                    }
                }

                is FeedbackIssueSubmitResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            submittingIssue = false,
                            errorMessage = context.getString(
                                os.kei.R.string.feedback_issue_error_api_failed,
                                result.statusCode?.toString() ?: "-",
                                result.message
                            )
                        )
                    }
                }
            }
        }
    }

    fun exportZip(
        context: Context,
        uri: Uri
    ) {
        if (_uiState.value.exportingZip) return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(exportingZip = true, errorMessage = "", statusMessage = "")
            }
            val result = repository.exportZip(context.applicationContext, uri)
            val fileName = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
            _uiState.update { state ->
                if (result.isSuccess) {
                    state.copy(
                        exportingZip = false,
                        lastExportedFileName = fileName,
                        statusMessage = context.getString(os.kei.R.string.feedback_issue_status_exported)
                    )
                } else {
                    state.copy(
                        exportingZip = false,
                        errorMessage = context.getString(
                            os.kei.R.string.settings_log_toast_export_failed,
                            result.exceptionOrNull()?.message
                                ?: result.exceptionOrNull()?.javaClass?.simpleName.orEmpty()
                        )
                    )
                }
            }
            refresh(context)
        }
    }

    fun clearLogs(context: Context) {
        if (_uiState.value.clearingLogs) return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(clearingLogs = true, errorMessage = "", statusMessage = "")
            }
            val result = repository.clearLogs(context.applicationContext)
            _uiState.update { state ->
                if (result.isSuccess) {
                    state.copy(
                        clearingLogs = false,
                        statusMessage = context.getString(os.kei.R.string.settings_log_toast_cleared)
                    )
                } else {
                    state.copy(
                        clearingLogs = false,
                        errorMessage = context.getString(
                            os.kei.R.string.settings_log_toast_clear_failed,
                            result.exceptionOrNull()?.message
                                ?: result.exceptionOrNull()?.javaClass?.simpleName.orEmpty()
                        )
                    )
                }
            }
            refresh(context)
        }
    }

    fun suggestLogExportFileName(
        onReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            onReady(repository.buildLogExportFileName())
        }
    }
}
