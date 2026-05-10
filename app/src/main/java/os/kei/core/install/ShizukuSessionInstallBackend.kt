package os.kei.core.install

import android.content.Context
import android.content.pm.PackageInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShizukuSessionInstallBackend(
    context: Context? = null,
    private val runtimeCapabilities: ShizukuRuntimeCapabilities = ShizukuRuntimeCapabilities(),
    private val sessionGateway: PackageInstallerSessionGateway? =
        context?.let { AndroidPackageInstallerSessionGateway(it.applicationContext) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ApkInstallBackend {
    override suspend fun install(
        request: ApkInstallRequest,
        onProgress: suspend (ApkInstallProgress) -> Unit
    ): ApkInstallResult = withContext(ioDispatcher) {
        val capability = runtimeCapabilities.current()
        if (!capability.sessionReady) {
            return@withContext ApkInstallResult.Failure(
                backendId = ApkInstallBackendId.ShizukuSession,
                reason = ApkInstallFailureReason.BackendUnavailable,
                message = capability.statusText
            )
        }
        val gateway = sessionGateway
            ?: return@withContext ApkInstallResult.Failure(
                backendId = ApkInstallBackendId.ShizukuSession,
                reason = ApkInstallFailureReason.BackendUnavailable,
                message = "PackageInstaller session gateway unavailable"
            )
        if (request.entries.isEmpty() || request.entries.any { it.sizeBytes <= 0L }) {
            return@withContext ApkInstallResult.Failure(
                backendId = ApkInstallBackendId.ShizukuSession,
                reason = ApkInstallFailureReason.InvalidRequest,
                message = "APK install request has no sized APK entries"
            )
        }

        var sessionId: Int? = null
        var handle: PackageInstallerSessionHandle? = null
        try {
            onProgress(ApkInstallProgress.Preparing(ApkInstallBackendId.ShizukuSession))
            sessionId = gateway.createSession(
                ApkInstallSessionParams(
                    packageName = request.packageName,
                    totalSizeBytes = request.totalSizeBytes,
                    replaceExisting = request.replaceExisting,
                    allowTestOnly = request.allowTestOnly
                )
            )
            handle = gateway.openSession(sessionId)
            request.entries.forEach { entry ->
                handle.write(entry) { written ->
                    onProgress(
                        ApkInstallProgress.Staging(
                            backendId = ApkInstallBackendId.ShizukuSession,
                            entryName = entry.name,
                            bytesWritten = written,
                            totalBytes = entry.sizeBytes
                        )
                    )
                }
            }
            onProgress(ApkInstallProgress.Committing(ApkInstallBackendId.ShizukuSession))
            val result = handle.commit()
            result.toInstallResult(request.packageName)
        } catch (error: CancellationException) {
            sessionId?.let { gateway.abandonSession(it) }
            throw error
        } catch (error: Throwable) {
            sessionId?.let { gateway.abandonSession(it) }
            ApkInstallResult.Failure(
                backendId = ApkInstallBackendId.ShizukuSession,
                reason = if (sessionId == null) {
                    ApkInstallFailureReason.SessionCreateFailed
                } else {
                    ApkInstallFailureReason.StagingFailed
                },
                message = error.message.orEmpty().ifBlank { error.javaClass.simpleName },
                cause = error
            )
        } finally {
            runCatching { handle?.close() }
        }
    }

    private fun PackageInstallCommitResult.toInstallResult(packageName: String): ApkInstallResult {
        return when {
            success -> ApkInstallResult.Success(
                backendId = ApkInstallBackendId.ShizukuSession,
                packageName = packageName,
                message = message
            )

            pendingUserAction -> ApkInstallResult.PendingUserAction(
                backendId = ApkInstallBackendId.ShizukuSession,
                intent = userAction,
                message = message
            )

            else -> ApkInstallResult.Failure(
                backendId = ApkInstallBackendId.ShizukuSession,
                reason = if (status == PackageInstaller.STATUS_FAILURE_TIMEOUT) {
                    ApkInstallFailureReason.TimedOut
                } else {
                    ApkInstallFailureReason.CommitFailed
                },
                message = message.ifBlank { "PackageInstaller commit failed: $status#$legacyStatus" }
            )
        }
    }
}

class ShizukuDualInstallBackend(
    private val sessionBackend: ApkInstallBackend,
    private val shellBackend: ApkInstallBackend
) : ApkInstallBackend {
    override suspend fun install(
        request: ApkInstallRequest,
        onProgress: suspend (ApkInstallProgress) -> Unit
    ): ApkInstallResult {
        val sessionResult = sessionBackend.install(request, onProgress)
        if (sessionResult is ApkInstallResult.Failure &&
            sessionResult.reason in fallbackReasons
        ) {
            return shellBackend.install(request, onProgress)
        }
        return sessionResult
    }

    private companion object {
        val fallbackReasons = setOf(
            ApkInstallFailureReason.BackendUnavailable,
            ApkInstallFailureReason.SessionCreateFailed
        )
    }
}
