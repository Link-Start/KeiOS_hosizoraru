package os.kei.core.install

import android.content.Intent
import java.io.InputStream

enum class ApkInstallBackendId {
    ShizukuSession
}

class ApkInstallEntry(
    val name: String,
    val sizeBytes: Long,
    val openInputStream: suspend () -> InputStream
)

data class ApkInstallRequest(
    val packageName: String = "",
    val entries: List<ApkInstallEntry>,
    val sourceLabel: String = "",
    val replaceExisting: Boolean = true,
    val allowTestOnly: Boolean = true
) {
    val totalSizeBytes: Long
        get() = entries.sumOf { it.sizeBytes.coerceAtLeast(0L) }
}

sealed interface ApkInstallProgress {
    data class Preparing(val backendId: ApkInstallBackendId) : ApkInstallProgress

    data class Staging(
        val backendId: ApkInstallBackendId,
        val entryName: String,
        val bytesWritten: Long,
        val totalBytes: Long
    ) : ApkInstallProgress {
        val fraction: Float
            get() = if (totalBytes > 0L) {
                bytesWritten.toFloat() / totalBytes.toFloat()
            } else {
                0f
            }.coerceIn(0f, 1f)
    }

    data class Committing(val backendId: ApkInstallBackendId) : ApkInstallProgress
}

enum class ApkInstallFailureReason {
    BackendUnavailable,
    InvalidRequest,
    SessionCreateFailed,
    StagingFailed,
    CommitFailed,
    PendingUserAction,
    Cancelled,
    TimedOut,
    PermissionDenied,
    Unknown
}

sealed interface ApkInstallResult {
    val backendId: ApkInstallBackendId

    data class Success(
        override val backendId: ApkInstallBackendId,
        val packageName: String = "",
        val message: String = ""
    ) : ApkInstallResult

    data class PendingUserAction(
        override val backendId: ApkInstallBackendId,
        val intent: Intent?,
        val message: String = ""
    ) : ApkInstallResult

    data class Failure(
        override val backendId: ApkInstallBackendId,
        val reason: ApkInstallFailureReason,
        val message: String = "",
        val cause: Throwable? = null
    ) : ApkInstallResult
}

fun interface ApkInstallBackend {
    suspend fun install(
        request: ApkInstallRequest,
        onProgress: suspend (ApkInstallProgress) -> Unit
    ): ApkInstallResult
}

suspend fun ApkInstallBackend.install(request: ApkInstallRequest): ApkInstallResult {
    return install(request) {}
}
