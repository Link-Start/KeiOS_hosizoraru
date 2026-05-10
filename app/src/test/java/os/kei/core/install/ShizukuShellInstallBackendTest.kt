package os.kei.core.install

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.core.system.AppCommandResult
import os.kei.core.system.ShizukuCommandIdentity
import os.kei.core.system.ShizukuRuntimeState
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShizukuShellInstallBackendTest {
    @Test
    fun `shell backend creates writes and commits session`() = runBlocking {
        val processGateway = RecordingProcessGateway(
            executeResults = ArrayDeque(
                listOf(
                    success("Success: created install session [42]"),
                    success("Success")
                )
            ),
            streamResults = ArrayDeque(listOf(success("Success")))
        )
        val backend = backend(processGateway)
        val progress = mutableListOf<ApkInstallProgress>()

        val result = backend.install(request()) { progress += it }

        assertIs<ApkInstallResult.Success>(result)
        assertEquals(
            listOf(
                "cmd package install-create -r -t --pkg 'os.kei.demo' -S 4",
                "cmd package install-write -S 4 42 'base.apk' -",
                "cmd package install-commit 42"
            ),
            processGateway.commands
        )
        assertEquals(listOf(4), processGateway.streamedSizes)
        assertTrue(progress.any { it is ApkInstallProgress.Staging })
    }

    @Test
    fun `shell backend abandons session when write fails`() = runBlocking {
        val processGateway = RecordingProcessGateway(
            executeResults = ArrayDeque(
                listOf(
                    success("Success: created install session [7]"),
                    success("Success")
                )
            ),
            streamResults = ArrayDeque(
                listOf(failure("write failed"))
            )
        )
        val backend = backend(processGateway)

        val result = backend.install(request())

        val failure = assertIs<ApkInstallResult.Failure>(result)
        assertEquals(ApkInstallFailureReason.StagingFailed, failure.reason)
        assertEquals(
            listOf(
                "cmd package install-create -r -t --pkg 'os.kei.demo' -S 4",
                "cmd package install-write -S 4 7 'base.apk' -",
                "cmd package install-abandon 7"
            ),
            processGateway.commands
        )
    }

    @Test
    fun `shell backend maps unavailable runtime`() = runBlocking {
        val backend = ShizukuShellInstallBackend(
            runtimeCapabilities = runtimeCapabilities(shellReady = false),
            processGateway = RecordingProcessGateway(),
            ioDispatcher = Dispatchers.Unconfined
        )

        val result = backend.install(request())

        val failure = assertIs<ApkInstallResult.Failure>(result)
        assertEquals(ApkInstallFailureReason.BackendUnavailable, failure.reason)
    }

    private fun backend(processGateway: RecordingProcessGateway): ShizukuShellInstallBackend {
        return ShizukuShellInstallBackend(
            runtimeCapabilities = runtimeCapabilities(shellReady = true),
            processGateway = processGateway,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    private fun request(): ApkInstallRequest {
        return ApkInstallRequest(
            packageName = "os.kei.demo",
            entries = listOf(
                ApkInstallEntry(
                    name = "base.apk",
                    sizeBytes = 4,
                    openInputStream = { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }
                )
            )
        )
    }

    private class RecordingProcessGateway(
        private val executeResults: ArrayDeque<AppCommandResult> = ArrayDeque(),
        private val streamResults: ArrayDeque<AppCommandResult> = ArrayDeque()
    ) : ShizukuProcessGateway {
        val commands = mutableListOf<String>()
        val streamedSizes = mutableListOf<Int>()

        override suspend fun execute(command: String, timeoutMs: Long): AppCommandResult {
            commands += command
            return executeResults.removeFirstOrNull() ?: success("Success")
        }

        override suspend fun streamInput(
            command: String,
            input: InputStream,
            sizeBytes: Long,
            timeoutMs: Long,
            onBytesWritten: suspend (Long) -> Unit
        ): AppCommandResult {
            commands += command
            val bytes = input.readBytes()
            streamedSizes += bytes.size
            onBytesWritten(bytes.size.toLong())
            return streamResults.removeFirstOrNull() ?: success("Success")
        }
    }
}

private fun runtimeCapabilities(
    shellReady: Boolean = true,
    sessionReady: Boolean = true
): ShizukuRuntimeCapabilities {
    val identity = if (shellReady) {
        ShizukuCommandIdentity.SHELL
    } else {
        ShizukuCommandIdentity.UNSUPPORTED
    }
    return ShizukuRuntimeCapabilities(
        object : ShizukuRuntimeProbe {
            override fun runtimeState(): ShizukuRuntimeState {
                return ShizukuRuntimeState(
                    binderAlive = true,
                    preV11 = false,
                    permissionGranted = true,
                    serviceUid = if (shellReady) 2000 else 10_000,
                    commandIdentity = identity
                )
            }

            override fun remotePermissionGranted(permission: String): Boolean = sessionReady
        }
    )
}

private fun success(stdout: String): AppCommandResult {
    return AppCommandResult(
        stdout = stdout,
        stderr = "",
        exitCode = 0,
        timedOut = false
    )
}

private fun failure(stderr: String): AppCommandResult {
    return AppCommandResult(
        stdout = "",
        stderr = stderr,
        exitCode = 1,
        timedOut = false
    )
}
