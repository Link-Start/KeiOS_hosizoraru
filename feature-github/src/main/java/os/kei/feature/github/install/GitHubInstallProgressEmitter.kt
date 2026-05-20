package os.kei.feature.github.install

import android.os.SystemClock
import kotlin.math.roundToInt

private const val DOWNLOAD_PROGRESS_MIN_INTERVAL_MS = 200L
private const val UNKNOWN_TOTAL_PROGRESS_STEP_BYTES = 1024L * 1024L

class GitHubInstallProgressEmitter(
    private val sessionId: Int,
    private val totalBytes: Long,
    private val onProgress: suspend (GitHubApkInstallProgress) -> Unit,
) {
    var totalRead: Long = 0L
        private set

    private var lastProgressPercent = -1
    private var lastProgressBytes = -1L
    private var lastProgressEmitAt = 0L

    fun add(bytes: Long) {
        totalRead += bytes
    }

    suspend fun emit(force: Boolean = false) {
        val progressPercent = downloadProgressPercent(totalRead, totalBytes)
        val now = SystemClock.uptimeMillis()
        val percentAdvanced = progressPercent > lastProgressPercent
        val unknownTotalBytesAdvanced =
            totalBytes <= 0L &&
                totalRead - lastProgressBytes >= UNKNOWN_TOTAL_PROGRESS_STEP_BYTES
        val timeReady = now - lastProgressEmitAt >= DOWNLOAD_PROGRESS_MIN_INTERVAL_MS
        if (!force && !unknownTotalBytesAdvanced && (!percentAdvanced || !timeReady)) {
            return
        }
        lastProgressPercent = progressPercent
        lastProgressBytes = totalRead
        lastProgressEmitAt = now
        onProgress(
            GitHubApkInstallProgress(
                stage = GitHubApkInstallStage.Downloading,
                progressPercent = progressPercent,
                downloadedBytes = totalRead,
                totalBytes = totalBytes,
                sessionId = sessionId,
            ),
        )
    }
}

private fun downloadProgressPercent(
    downloadedBytes: Long,
    totalBytes: Long,
): Int {
    if (totalBytes <= 0L) return 0
    val fraction = downloadedBytes.toDouble() / totalBytes.toDouble()
    return (fraction.coerceIn(0.0, 1.0) * 100.0).roundToInt().coerceIn(0, 100)
}
