package os.kei.core.install

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.core.system.AppCommandResult

class ShizukuShellInstallBackend(
    private val runtimeCapabilities: ShizukuRuntimeCapabilities = ShizukuRuntimeCapabilities(),
    private val processGateway: ShizukuProcessGateway = DefaultShizukuProcessGateway(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ApkInstallBackend {
    override suspend fun install(
        request: ApkInstallRequest,
        onProgress: suspend (ApkInstallProgress) -> Unit
    ): ApkInstallResult = withContext(ioDispatcher) {
        val capability = runtimeCapabilities.current()
        if (!capability.shellReady) {
            return@withContext ApkInstallResult.Failure(
                backendId = ApkInstallBackendId.ShizukuShell,
                reason = ApkInstallFailureReason.BackendUnavailable,
                message = capability.statusText
            )
        }
        if (request.entries.isEmpty() || request.entries.any { it.sizeBytes <= 0L }) {
            return@withContext ApkInstallResult.Failure(
                backendId = ApkInstallBackendId.ShizukuShell,
                reason = ApkInstallFailureReason.InvalidRequest,
                message = "APK install request has no sized APK entries"
            )
        }

        var sessionId: Int? = null
        try {
            onProgress(ApkInstallProgress.Preparing(ApkInstallBackendId.ShizukuShell))
            val createResult = processGateway.execute(
                buildInstallCreateCommand(request),
                timeoutMs = ShizukuProcessGateway.DEFAULT_TIMEOUT_MS
            )
            if (!createResult.succeeded) {
                return@withContext createResult.toFailure(ApkInstallFailureReason.SessionCreateFailed)
            }
            sessionId = parseSessionId(createResult.combinedOutput())
                ?: return@withContext createResult.toFailure(
                    reason = ApkInstallFailureReason.SessionCreateFailed,
                    fallbackMessage = "Package manager did not return an install session id"
                )

            request.entries.forEach { entry ->
                val writeCommand = buildInstallWriteCommand(sessionId, entry)
                val writeResult = processGateway.streamInput(
                    command = writeCommand,
                    input = entry.openInputStream(),
                    sizeBytes = entry.sizeBytes,
                    timeoutMs = ShizukuProcessGateway.DEFAULT_STREAM_TIMEOUT_MS
                ) { written ->
                    onProgress(
                        ApkInstallProgress.Staging(
                            backendId = ApkInstallBackendId.ShizukuShell,
                            entryName = entry.name,
                            bytesWritten = written,
                            totalBytes = entry.sizeBytes
                        )
                    )
                }
                if (!writeResult.succeeded) {
                    abandonSession(sessionId)
                    return@withContext writeResult.toFailure(ApkInstallFailureReason.StagingFailed)
                }
            }

            onProgress(ApkInstallProgress.Committing(ApkInstallBackendId.ShizukuShell))
            val commitResult = processGateway.execute(
                command = "cmd package install-commit $sessionId",
                timeoutMs = ShizukuProcessGateway.DEFAULT_STREAM_TIMEOUT_MS
            )
            if (commitResult.succeeded && commitResult.combinedOutput()
                    .contains("success", ignoreCase = true)
            ) {
                ApkInstallResult.Success(
                    backendId = ApkInstallBackendId.ShizukuShell,
                    packageName = request.packageName,
                    message = commitResult.combinedOutput()
                )
            } else {
                commitResult.toFailure(ApkInstallFailureReason.CommitFailed)
            }
        } catch (error: CancellationException) {
            sessionId?.let { abandonSession(it) }
            throw error
        } catch (error: Throwable) {
            sessionId?.let { abandonSession(it) }
            ApkInstallResult.Failure(
                backendId = ApkInstallBackendId.ShizukuShell,
                reason = ApkInstallFailureReason.Unknown,
                message = error.message.orEmpty().ifBlank { error.javaClass.simpleName },
                cause = error
            )
        }
    }

    private suspend fun abandonSession(sessionId: Int) {
        processGateway.execute(
            command = "cmd package install-abandon $sessionId",
            timeoutMs = ShizukuProcessGateway.DEFAULT_TIMEOUT_MS
        )
    }

    private fun buildInstallCreateCommand(request: ApkInstallRequest): String {
        return buildString {
            append("cmd package install-create")
            if (request.replaceExisting) append(" -r")
            if (request.allowTestOnly) append(" -t")
            if (request.packageName.isNotBlank()) {
                append(" --pkg ")
                append(request.packageName.shellQuote())
            }
            if (request.totalSizeBytes > 0L) {
                append(" -S ")
                append(request.totalSizeBytes)
            }
        }
    }

    private fun buildInstallWriteCommand(sessionId: Int, entry: ApkInstallEntry): String {
        return buildString {
            append("cmd package install-write -S ")
            append(entry.sizeBytes)
            append(' ')
            append(sessionId)
            append(' ')
            append(entry.name.shellQuote())
            append(" -")
        }
    }

    private fun parseSessionId(output: String): Int? {
        return Regex("""\[(\d+)]""")
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun AppCommandResult.toFailure(
        reason: ApkInstallFailureReason,
        fallbackMessage: String = ""
    ): ApkInstallResult.Failure {
        return ApkInstallResult.Failure(
            backendId = ApkInstallBackendId.ShizukuShell,
            reason = when {
                cancelled -> ApkInstallFailureReason.Cancelled
                timedOut -> ApkInstallFailureReason.TimedOut
                else -> reason
            },
            message = combinedOutput().ifBlank { fallbackMessage },
        )
    }
}

internal fun String.shellQuote(): String {
    return "'" + replace("'", "'\"'\"'") + "'"
}
