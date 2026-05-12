package os.kei.feature.github.install

import android.content.Context
import android.content.pm.ApplicationInfo
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
import os.kei.core.log.AppLogger
import os.kei.feature.github.data.remote.GitHubReleaseAssetRepository
import os.kei.feature.github.model.GitHubLookupStrategyOption
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

private const val GITHUB_SHIZUKU_INSTALLER_TAG = "GitHubShizukuInstaller"
private const val APK_STREAM_BUFFER_SIZE = 1024 * 1024
private const val DOWNLOAD_PROGRESS_MIN_INTERVAL_MS = 200L
private const val UNKNOWN_TOTAL_PROGRESS_STEP_BYTES = 1024L * 1024L

interface GitHubManagedApkInstaller {
    suspend fun stage(
        context: Context,
        request: GitHubApkInstallRequest,
        onProgress: suspend (GitHubApkInstallProgress) -> Unit = {}
    ): GitHubApkInstallResult

    suspend fun commit(
        context: Context,
        request: GitHubApkInstallRequest,
        sessionId: Int,
        downloadedBytes: Long = 0L,
        totalBytes: Long = -1L,
        onProgress: suspend (GitHubApkInstallProgress) -> Unit = {}
    ): GitHubApkInstallResult

    suspend fun cancel(context: Context, sessionId: Int) = Unit

    suspend fun install(
        context: Context,
        request: GitHubApkInstallRequest,
        onProgress: suspend (GitHubApkInstallProgress) -> Unit = {}
    ): GitHubApkInstallResult {
        val staged = stage(context, request, onProgress)
        if (staged !is GitHubApkInstallResult.Staged) return staged
        val commitRequest = request.copy(
            scannedAppLabel = staged.appLabel.ifBlank { request.scannedAppLabel },
            scannedPackageName = staged.packageName.ifBlank { request.scannedPackageName },
            scannedVersionName = staged.versionName.ifBlank { request.scannedVersionName },
            scannedVersionCode = staged.versionCode.ifBlank { request.scannedVersionCode },
            scannedMinSdk = staged.minSdk.ifBlank { request.scannedMinSdk },
            scannedTargetSdk = staged.targetSdk.ifBlank { request.scannedTargetSdk }
        )
        return commit(
            context = context,
            request = commitRequest,
            sessionId = staged.sessionId,
            downloadedBytes = staged.downloadedBytes,
            totalBytes = staged.totalBytes,
            onProgress = onProgress
        )
    }
}

class GitHubShizukuPackageInstaller(
    private val bridge: ShizukuPackageInstallerBridge = ShizukuPackageInstallerBridge(),
    private val client: OkHttpClient = defaultClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : GitHubManagedApkInstaller {
    override suspend fun cancel(context: Context, sessionId: Int) = withContext(ioDispatcher) {
        if (sessionId <= 0) return@withContext
        runCatching {
            bridge.packageInstaller(context.applicationContext).abandonSession(sessionId)
        }.onFailure { error ->
            AppLogger.w(
                GITHUB_SHIZUKU_INSTALLER_TAG,
                "Cancel install session failed: session=$sessionId, " +
                        "error=${error.installMessage("cancel failed")}",
                error
            )
        }
    }

    override suspend fun stage(
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

            packageInstaller = runCatching {
                bridge.packageInstaller(appContext)
            }.getOrElse { error ->
                AppLogger.e(
                    GITHUB_SHIZUKU_INSTALLER_TAG,
                    "Create PackageInstaller bridge failed: request=${request.requestId}, " +
                            "package=${appContext.packageName}, error=${error.installMessage("bridge failed")}",
                    error
                )
                return@withContext GitHubApkInstallResult.Failed(
                    reason = GitHubApkInstallFailureReason.SessionCreateFailed,
                    message = error.installMessage("Create PackageInstaller bridge failed")
                )
            }
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
                    AppLogger.e(
                        GITHUB_SHIZUKU_INSTALLER_TAG,
                        "Create install session failed: request=${request.requestId}, " +
                                "package=${appContext.packageName}, error=${error.installMessage("session failed")}",
                        error
                    )
                    return@withContext GitHubApkInstallResult.Failed(
                        reason = GitHubApkInstallFailureReason.SessionCreateFailed,
                        message = error.installMessage("Create install session failed")
                    )
                }

            val session = runCatching {
                bridge.wrapSession(packageInstaller.openSession(sessionId))
            }.getOrElse { error ->
                AppLogger.e(
                    GITHUB_SHIZUKU_INSTALLER_TAG,
                    "Open install session failed: request=${request.requestId}, session=$sessionId, " +
                            "error=${error.installMessage("open failed")}",
                    error
                )
                abandonSession(packageInstaller, sessionId)
                return@withContext GitHubApkInstallResult.Failed(
                    reason = GitHubApkInstallFailureReason.SessionOpenFailed,
                    message = error.installMessage("Open install session failed"),
                    sessionId = sessionId
                )
            }

            val writeResult = runCatching {
                streamApkIntoSession(
                    context = appContext,
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
                AppLogger.e(
                    GITHUB_SHIZUKU_INSTALLER_TAG,
                    "Stage APK failed: request=${request.requestId}, session=$sessionId, " +
                            "error=${error.installMessage("stage failed")}",
                    error
                )
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

            session.closeQuietly()
            val archiveInfo = writeResult.archiveInfo
            val stagedPackageName = archiveInfo.packageName.ifBlank {
                request.scannedPackageName.trim()
            }
            onProgress(
                GitHubApkInstallProgress(
                    stage = GitHubApkInstallStage.ReadyToCommit,
                    progressPercent = 100,
                    downloadedBytes = writeResult.bytesWritten,
                    totalBytes = writeResult.totalBytes,
                    sessionId = sessionId,
                    appLabel = archiveInfo.appLabel,
                    packageName = stagedPackageName,
                    versionName = archiveInfo.versionName,
                    versionCode = archiveInfo.versionCode,
                    minSdk = archiveInfo.minSdk,
                    targetSdk = archiveInfo.targetSdk
                )
            )
            GitHubApkInstallResult.Staged(
                requestId = request.requestId,
                sessionId = sessionId,
                packageName = stagedPackageName,
                appLabel = archiveInfo.appLabel,
                versionName = archiveInfo.versionName,
                versionCode = archiveInfo.versionCode,
                minSdk = archiveInfo.minSdk,
                targetSdk = archiveInfo.targetSdk,
                downloadedBytes = writeResult.bytesWritten,
                totalBytes = writeResult.totalBytes
            )
        } catch (error: CancellationException) {
            packageInstaller?.let { abandonSession(it, sessionId) }
            GitHubApkInstallResult.Cancelled(request.requestId, sessionId)
        } catch (error: Throwable) {
            AppLogger.e(
                GITHUB_SHIZUKU_INSTALLER_TAG,
                "Managed install staging failed: request=${request.requestId}, session=$sessionId, " +
                        "error=${error.installMessage("stage failed")}",
                error
            )
            packageInstaller?.let { abandonSession(it, sessionId) }
            GitHubApkInstallResult.Failed(
                reason = GitHubApkInstallFailureReason.Unknown,
                message = error.installMessage("Stage APK failed"),
                sessionId = sessionId
            )
        }
    }

    override suspend fun commit(
        context: Context,
        request: GitHubApkInstallRequest,
        sessionId: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        onProgress: suspend (GitHubApkInstallProgress) -> Unit
    ): GitHubApkInstallResult = withContext(ioDispatcher) {
        val appContext = context.applicationContext
        var packageInstaller: PackageInstaller? = null
        var session: PackageInstaller.Session? = null
        try {
            onProgress(
                GitHubApkInstallProgress(
                    stage = GitHubApkInstallStage.Committing,
                    progressPercent = 92,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    sessionId = sessionId,
                    appLabel = request.scannedAppLabel,
                    packageName = request.scannedPackageName,
                    versionName = request.scannedVersionName,
                    versionCode = request.scannedVersionCode,
                    minSdk = request.scannedMinSdk,
                    targetSdk = request.scannedTargetSdk
                )
            )
            val capability = bridge.checkCapability()
            if (!capability.available) {
                return@withContext GitHubApkInstallResult.Failed(
                    reason = capability.failureReason ?: GitHubApkInstallFailureReason.Unknown,
                    message = capability.message.ifBlank { "Shizuku install capability unavailable" },
                    sessionId = sessionId
                )
            }
            packageInstaller = runCatching {
                bridge.packageInstaller(appContext)
            }.getOrElse { error ->
                AppLogger.e(
                    GITHUB_SHIZUKU_INSTALLER_TAG,
                    "Create PackageInstaller bridge for commit failed: request=${request.requestId}, " +
                            "package=${appContext.packageName}, error=${error.installMessage("bridge failed")}",
                    error
                )
                return@withContext GitHubApkInstallResult.Failed(
                    reason = GitHubApkInstallFailureReason.SessionCreateFailed,
                    message = error.installMessage("Create PackageInstaller bridge failed"),
                    sessionId = sessionId
                )
            }
            session = runCatching {
                bridge.wrapSession(packageInstaller.openSession(sessionId))
            }.getOrElse { error ->
                AppLogger.e(
                    GITHUB_SHIZUKU_INSTALLER_TAG,
                    "Open staged install session failed: request=${request.requestId}, session=$sessionId, " +
                            "error=${error.installMessage("open failed")}",
                    error
                )
                return@withContext GitHubApkInstallResult.Failed(
                    reason = GitHubApkInstallFailureReason.SessionOpenFailed,
                    message = error.installMessage("Open staged install session failed"),
                    sessionId = sessionId
                )
            }

            val deferred = GitHubShizukuInstallCommitRegistry.register(request.requestId)
            val sender = GitHubShizukuInstallCommitRegistry.buildIntentSender(
                context = appContext,
                requestId = request.requestId,
                sessionId = sessionId
            )
            runCatching {
                session.commit(sender)
            }.onFailure { error ->
                AppLogger.e(
                    GITHUB_SHIZUKU_INSTALLER_TAG,
                    "Commit install session failed: request=${request.requestId}, session=$sessionId, " +
                            "error=${error.installMessage("commit failed")}",
                    error
                )
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
            session = null

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
                AppLogger.e(
                    GITHUB_SHIZUKU_INSTALLER_TAG,
                    "PackageInstaller commit result failed: request=${request.requestId}, " +
                            "session=$sessionId, status=${commitResult.statusCode}, " +
                            "legacy=${commitResult.legacyStatus}, message=${commitResult.message}"
                )
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
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    sessionId = sessionId,
                    appLabel = request.scannedAppLabel,
                    packageName = packageName,
                    versionName = request.scannedVersionName,
                    versionCode = request.scannedVersionCode,
                    minSdk = request.scannedMinSdk,
                    targetSdk = request.scannedTargetSdk
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
            AppLogger.e(
                GITHUB_SHIZUKU_INSTALLER_TAG,
                "Managed install commit failed: request=${request.requestId}, session=$sessionId, " +
                        "error=${error.installMessage("install failed")}",
                error
            )
            packageInstaller?.let { abandonSession(it, sessionId) }
            GitHubApkInstallResult.Failed(
                reason = GitHubApkInstallFailureReason.Unknown,
                message = error.installMessage("Install failed"),
                sessionId = sessionId
            )
        } finally {
            session?.closeQuietly()
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
        context: Context,
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
            val tempApkFile = createTempApkFile(context, assetName)
            try {
                body.byteStream().use { input ->
                    session.openWrite(fileName, 0, totalBytes).use { output ->
                        FileOutputStream(tempApkFile).use { archiveOutput ->
                            val buffer = ByteArray(APK_STREAM_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                archiveOutput.write(buffer, 0, read)
                                totalRead += read.toLong()
                                emitDownloadProgress()
                            }
                            archiveOutput.flush()
                        }
                        emitDownloadProgress(force = true)
                        val archiveInfo = readArchiveInfo(context, tempApkFile)
                        onProgress(
                            GitHubApkInstallProgress(
                                stage = GitHubApkInstallStage.Staging,
                                progressPercent = 100,
                                downloadedBytes = totalRead,
                                totalBytes = totalBytes,
                                sessionId = sessionId,
                                appLabel = archiveInfo.appLabel,
                                packageName = archiveInfo.packageName,
                                versionName = archiveInfo.versionName,
                                versionCode = archiveInfo.versionCode,
                                minSdk = archiveInfo.minSdk,
                                targetSdk = archiveInfo.targetSdk
                            )
                        )
                        session.fsync(output)
                        return SessionWriteResult(totalRead, totalBytes, archiveInfo)
                    }
                }
            } finally {
                runCatching { tempApkFile.delete() }
            }
        }
    }

    private fun createTempApkFile(context: Context, assetName: String): File {
        val directory = File(context.cacheDir, "github-managed-install").apply { mkdirs() }
        val safeName = assetName
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
            .ifBlank { "download.apk" }
        return File.createTempFile("kei-", "-$safeName", directory)
    }

    private fun readArchiveInfo(context: Context, apkFile: File): ApkArchiveInfo {
        val pm = context.packageManager
        val packageInfo = runCatching {
            pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(0)
            )
        }.recoverCatching {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
        }.getOrNull() ?: return ApkArchiveInfo()
        val appInfo = packageInfo.applicationInfo?.applyArchiveSource(apkFile)
        val label = appInfo?.let { info ->
            runCatching { info.loadLabel(pm).toString().trim() }.getOrDefault("")
        }.orEmpty()
        return ApkArchiveInfo(
            packageName = packageInfo.packageName.orEmpty(),
            appLabel = label,
            versionName = packageInfo.versionName?.trim().orEmpty(),
            versionCode = packageInfo.longVersionCode.takeIf { it >= 0L }?.toString().orEmpty(),
            minSdk = appInfo?.minSdkVersion?.takeIf { it >= 0 }?.toString().orEmpty(),
            targetSdk = appInfo?.targetSdkVersion?.takeIf { it >= 0 }?.toString().orEmpty()
        )
    }

    private fun ApplicationInfo.applyArchiveSource(apkFile: File): ApplicationInfo {
        sourceDir = apkFile.absolutePath
        publicSourceDir = apkFile.absolutePath
        return this
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
        val totalBytes: Long,
        val archiveInfo: ApkArchiveInfo = ApkArchiveInfo()
    )

    private data class ApkArchiveInfo(
        val packageName: String = "",
        val appLabel: String = "",
        val versionName: String = "",
        val versionCode: String = "",
        val minSdk: String = "",
        val targetSdk: String = ""
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
