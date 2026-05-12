package os.kei.ui.page.main.feedback

import androidx.compose.runtime.Immutable
import os.kei.core.log.AppLogStore

internal enum class FeedbackSubmitMode {
    Browser,
    GitHubApi
}

@Immutable
internal data class FeedbackDeviceInfo(
    val appVersionName: String = "",
    val appVersionCode: Long = 0L,
    val packageName: String = "",
    val buildType: String = "",
    val androidRelease: String = "",
    val sdkInt: Int = 0,
    val manufacturer: String = "",
    val model: String = "",
    val abis: String = "",
    val installSource: String = ""
) {
    val appVersionLine: String
        get() = "$appVersionName ($appVersionCode) · $packageName · $buildType"

    val androidLine: String
        get() = "Android $androidRelease · API $sdkInt"

    val deviceLine: String
        get() = "$manufacturer $model"
}

@Immutable
internal data class FeedbackIssueUiState(
    val loading: Boolean = true,
    val deviceInfo: FeedbackDeviceInfo = FeedbackDeviceInfo(),
    val logStats: AppLogStore.Stats = AppLogStore.Stats.Empty,
    val logPreview: String = "",
    val logPreviewTruncated: Boolean = false,
    val apiTokenAvailable: Boolean = false,
    val title: String = "",
    val body: String = "",
    val pendingSubmitMode: FeedbackSubmitMode? = null,
    val exportingZip: Boolean = false,
    val clearingLogs: Boolean = false,
    val submittingIssue: Boolean = false,
    val lastExportedFileName: String = "",
    val statusMessage: String = "",
    val errorMessage: String = ""
)

internal sealed interface FeedbackIssueSubmitResult {
    data class Success(val issueUrl: String) : FeedbackIssueSubmitResult
    data object MissingToken : FeedbackIssueSubmitResult
    data class Failure(val statusCode: Int?, val message: String) : FeedbackIssueSubmitResult
}
