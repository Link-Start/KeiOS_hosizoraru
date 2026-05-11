package os.kei.feature.github.install

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import os.kei.feature.github.data.remote.GitHubReleaseAssetRepository
import os.kei.feature.github.model.GitHubLookupStrategyOption
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

private const val APK_STREAM_BUFFER_SIZE = 1024 * 1024
private const val DOWNLOAD_PROGRESS_MIN_INTERVAL_MS = 200L
private const val UNKNOWN_TOTAL_PROGRESS_STEP_BYTES = 1024L * 1024L

interface GitHubManagedApkInstaller {
    suspend fun install(
        context: Context,
        request: GitHubApkInstallRequest,
        onProgress: suspend (GitHubApkInstallProgress) -> Unit = {}
    ): GitHubApkInstallResult
}

class GitHubShizukuPackageInstaller(
    private val bridge: ShizukuPackageInstallerBridge = ShizukuPackageInstallerBridge(),
    private val client: OkHttpClient = defaultClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : GitHubManagedApkInstaller {
    override suspend fun install(
        context: Context,
        request: GitHubApkInstallRequest,
        onProgress: suspend (GitHubApkInstallProgress) -> Unit
    ): GitHubApkInstallResult = withContext(ioDispatcher) {
        val appContext = context.applicationContext
        var sessionId = -1
        var packageInstaller: PackageInstaller? = null
        try {
            onProgress(GitHubApkInstallProgress(GitHubApkInstallStage.Preparing, 4))
            val capability = bridge.checkCapability()
            if (!capability.available) {
                return@withContext GitHubApkInstallResult.Failed(
                    reason = capability.failureReason ?: GitHubApkInstallFailureReason.Unknown,
                    message = capability.message.ifBlank { "Shizuku install capability unavailable" }
                )
            }
            val resolvedUrl = resolveDownloadUrl(request).trim()
            if (!resolvedUrl.startsWith("https://", ignoreCase = true)) {
                return@withContext GitHubApkInstallResult.Failed(
                    reason = GitHubApkInstallFailureReason.DownloadUrlInvalid,
                    message = "Invalid APK download URL"
                )
            }

            packageInstaller = bridge.packageInstaller(appContext)
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            bridge.applySessionParams(
                params = params,
                context = appContext,
                appPackageName = request.scannedPackageName.trim()
            )
            sessionId = runCatching { packageInstaller.createSession(params) }
                .getOrElse { error ->
                    return@withContext GitHubApkInstallResult.Failed(
                        reason = GitHubApkInstallFailureReason.SessionCreateFailed,
                        message = error.installMessage("Create install session failed")
                    )
                }

            val session = runCatching {
                bridge.wrapSession(packageInstaller.openSession(sessionId))
            }.getOrElse { error ->
                abandonSession(packageInstaller, sessionId)
                return@withContext GitHubApkInstallResult.Failed(
                    reason = GitHubApkInstallFailureReason.SessionOpenFailed,
                    message = error.installMessage("Open install session failed"),
                    sessionId = sessionId
                )
            }

            val writeResult = runCatching {
                streamApkIntoSession(
                    resolvedUrl = resolvedUrl,
                    assetName = request.asset.name,
                    declaredSizeBytes = request.asset.sizeBytes,
                    session = session,
                    sessionId = sessionId,
                    onProgress = onProgress
                )
            }.getOrElse { error ->
                session.closeQuietly()
                abandonSession(packageInstaller, sessionId)
                if (error is CancellationException) {
                    return@withContext GitHubApkInstallResult.Cancelled(
                        request.requestId,
                        sessionId
                    )
                }
                return@withContext GitHubApkInstallResult.Failed(
                    reason = if (error is IOException) {
                        GitHubApkInstallFailureReason.DownloadFailed
                    } else {
                        GitHubApkInstallFailureReason.SessionWriteFailed
                    },
                    message = error.installMessage("Stage APK failed"),
                    sessionId = sessionId
                )
            }

            onProgress(
                GitHubApkInstallProgress(
                    stage = GitHubApkInstallStage.Committing,
                    progressPercent = 92,
                    downloadedBytes = writeResult.bytesWritten,
                    totalBytes = writeResult.totalBytes,
                    sessionId = sessionId
                )
            )
            val deferred = GitHubShizukuInstallCommitRegistry.register(request.requestId)
            val sender = GitHubShizukuInstallCommitRegistry.buildIntentSender(
                context = appContext,
                requestId = request.requestId,
                sessionId = sessionId
            )
            runCatching {
                session.commit(sender)
            }.onFailure { error ->
                GitHubShizukuInstallCommitRegistry.unregister(request.requestId)
                session.closeQuietly()
                abandonSession(packageInstaller, sessionId)
                return@withContext GitHubApkInstallResult.Failed(
                    reason = GitHubApkInstallFailureReason.CommitFailed,
                    message = error.installMessage("Commit install session failed"),
                    sessionId = sessionId
                )
            }
            session.closeQuietly()

            val commitResult = runCatching {
                withTimeout(5.minutes) { deferred.await() }
            }.getOrElse { error ->
                GitHubShizukuInstallCommitRegistry.unregister(request.requestId)
                abandonSession(packageInstaller, sessionId)
                if (error is CancellationException) {
                    return@withContext GitHubApkInstallResult.Cancelled(
                        request.requestId,
                        sessionId
                    )
                }
                return@withContext GitHubApkInstallResult.Failed(
                    reason = GitHubApkInstallFailureReason.ResultTimeout,
                    message = error.installMessage("Install result timed out"),
                    sessionId = sessionId
                )
            }

            if (commitResult.statusCode != PackageInstaller.STATUS_SUCCESS) {
                return@withContext GitHubApkInstallResult.Failed(
                    reason = GitHubApkInstallFailureReason.CommitFailed,
                    message = commitResult.message.ifBlank { "PackageInstaller commit failed" },
                    sessionId = sessionId,
                    statusCode = commitResult.statusCode,
                    legacyStatus = commitResult.legacyStatus,
                    packageName = commitResult.packageName
                )
            }

            val packageName = request.scannedPackageName
                .trim()
                .ifBlank { commitResult.packageName.trim() }
            if (packageName.isBlank()) {
                return@withContext GitHubApkInstallResult.Failed(
                    reason = GitHubApkInstallFailureReason.PackageNameMissing,
                    message = "Install succeeded but package name was not reported",
                    sessionId = sessionId,
                    statusCode = commitResult.statusCode,
                    legacyStatus = commitResult.legacyStatus
                )
            }
            onProgress(
                GitHubApkInstallProgress(
                    stage = GitHubApkInstallStage.Succeeded,
                    progressPercent = 100,
                    downloadedBytes = writeResult.bytesWritten,
                    totalBytes = writeResult.totalBytes,
                    sessionId = sessionId
                )
            )
            val installed = loadPackageInfo(appContext, packageName)
            GitHubApkInstallResult.Succeeded(
                requestId = request.requestId,
                sessionId = sessionId,
                packageName = packageName,
                appLabel = installed?.appLabel.orEmpty(),
                firstInstallTimeMs = installed?.firstInstallTimeMs ?: -1L
            )
        } catch (error: CancellationException) {
            packageInstaller?.let { abandonSession(it, sessionId) }
            GitHubApkInstallResult.Cancelled(request.requestId, sessionId)
        } catch (error: Throwable) {
            packageInstaller?.let { abandonSession(it, sessionId) }
            GitHubApkInstallResult.Failed(
                reason = GitHubApkInstallFailureReason.Unknown,
                message = error.installMessage("Install failed"),
                sessionId = sessionId
            )
        }
    }

    private suspend fun resolveDownloadUrl(request: GitHubApkInstallRequest): String {
        request.resolvedDownloadUrl.trim().takeIf { it.isNotBlank() }?.let { return it }
        val token = request.lookupConfig.apiToken.trim()
        val preferApiAsset =
            request.lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken
        return GitHubReleaseAssetRepository.resolvePreferredDownloadUrl(
            asset = request.asset,
            useApiAssetUrl = preferApiAsset,
            apiToken = token
        ).getOrElse { request.asset.downloadUrl }
    }

    private suspend fun streamApkIntoSession(
        resolvedUrl: String,
        assetName: String,
        declaredSizeBytes: Long,
        session: PackageInstaller.Session,
        sessionId: Int,
        onProgress: suspend (GitHubApkInstallProgress) -> Unit
    ): SessionWriteResult {
        val request = Request.Builder()
            .url(resolvedUrl)
            .header("User-Agent", "KeiOS-App/1.0 (Android)")
            .header(
                "Accept",
                "application/vnd.android.package-archive, application/octet-stream;q=0.9, */*;q=0.1"
            )
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body
            val totalBytes = when {
                body.contentLength() > 0L -> body.contentLength()
                declaredSizeBytes > 0L -> declaredSizeBytes
                else -> -1L
            }
            val fileName = assetName.trim().ifBlank { "base.apk" }
            var totalRead = 0L
            var lastProgressPercent = -1
            var lastProgressBytes = -1L
            var lastProgressEmitAt = 0L
            suspend fun emitDownloadProgress(force: Boolean = false) {
                val progressPercent = downloadProgressPercent(totalRead, totalBytes)
                val now = SystemClock.uptimeMillis()
                val percentAdvanced = progressPercent > lastProgressPercent
                val unknownTotalBytesAdvanced = totalBytes <= 0L &&
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
                        sessionId = sessionId
                    )
                )
            }
            emitDownloadProgress(force = true)
            body.byteStream().use { input ->
                session.openWrite(fileName, 0, totalBytes).use { output ->
                    val buffer = ByteArray(APK_STREAM_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        totalRead += read.toLong()
                        emitDownloadProgress()
                    }
                    emitDownloadProgress(force = true)
                    onProgress(
                        GitHubApkInstallProgress(
                            stage = GitHubApkInstallStage.Staging,
                            progressPercent = 100,
                            downloadedBytes = totalRead,
                            totalBytes = totalBytes,
                            sessionId = sessionId
                        )
                    )
                    session.fsync(output)
                }
            }
            return SessionWriteResult(totalRead, totalBytes)
        }
    }

    private fun downloadProgressPercent(downloadedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L) return 0
        val fraction = downloadedBytes.toDouble() / totalBytes.toDouble()
        return (fraction.coerceIn(0.0, 1.0) * 100.0).roundToInt().coerceIn(0, 100)
    }

    private fun abandonSession(packageInstaller: PackageInstaller, sessionId: Int) {
        if (sessionId <= 0) return
        runCatching {
            packageInstaller.abandonSession(sessionId)
        }
    }

    private fun PackageInstaller.Session.closeQuietly() {
        runCatching { close() }
    }

    private fun loadPackageInfo(
        context: Context,
        packageName: String
    ): InstalledPackageInfo? {
        val pm = context.packageManager
        val info = runCatching {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        }.recoverCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }.getOrNull() ?: return null
        val label = runCatching {
            val appInfo = info.applicationInfo ?: pm.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0)
            )
            pm.getApplicationLabel(appInfo).toString().trim()
        }.recoverCatching {
            @Suppress("DEPRECATION")
            val appInfo = info.applicationInfo ?: pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString().trim()
        }.getOrDefault("")
        return InstalledPackageInfo(
            appLabel = label,
            firstInstallTimeMs = info.firstInstallTime
        )
    }

    private fun Throwable.installMessage(fallback: String): String {
        return message?.takeIf { it.isNotBlank() } ?: fallback
    }

    private data class SessionWriteResult(
        val bytesWritten: Long,
        val totalBytes: Long
    )

    private data class InstalledPackageInfo(
        val appLabel: String,
        val firstInstallTimeMs: Long
    )

    companion object {
        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
