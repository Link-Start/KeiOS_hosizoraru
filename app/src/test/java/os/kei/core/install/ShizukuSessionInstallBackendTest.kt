package os.kei.core.install

import android.content.pm.PackageInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.core.system.ShizukuCommandIdentity
import os.kei.core.system.ShizukuRuntimeState
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShizukuSessionInstallBackendTest {
    @Test
    fun `session backend creates writes commits and closes session`() = runBlocking {
        val handle = FakeSessionHandle(
            commitResult = PackageInstallCommitResult(
                status = PackageInstaller.STATUS_SUCCESS,
                legacyStatus = 1,
                message = "ok",
                userAction = null
            )
        )
        val gateway = FakeSessionGateway(handle = handle)
        val backend = backend(gateway)
        val progress = mutableListOf<ApkInstallProgress>()

        val result = backend.install(request()) { progress += it }

        assertIs<ApkInstallResult.Success>(result)
        assertEquals("os.kei.demo", gateway.params.single().packageName)
        assertEquals(listOf("base.apk"), handle.writtenEntries)
        assertTrue(handle.closed)
        assertTrue(progress.any { it is ApkInstallProgress.Staging })
    }

    @Test
    fun `session backend maps pending user action`() = runBlocking {
        val handle = FakeSessionHandle(
            commitResult = PackageInstallCommitResult(
                status = PackageInstaller.STATUS_PENDING_USER_ACTION,
                legacyStatus = 0,
                message = "needs approval",
                userAction = android.content.Intent("demo")
            )
        )
        val backend = backend(FakeSessionGateway(handle = handle))

        val result = backend.install(request())

        val pending = assertIs<ApkInstallResult.PendingUserAction>(result)
        assertTrue(pending.message.contains("approval"))
    }

    @Test
    fun `session backend abandons session when write fails`() = runBlocking {
        val gateway = FakeSessionGateway(
            handle = FakeSessionHandle(writeError = IllegalStateException("write failed"))
        )
        val backend = backend(gateway)

        val result = backend.install(request())

        val failure = assertIs<ApkInstallResult.Failure>(result)
        assertEquals(ApkInstallFailureReason.StagingFailed, failure.reason)
        assertEquals(listOf(42), gateway.abandonedSessionIds)
    }

    @Test
    fun `dual backend falls back to shell when session is unavailable`() = runBlocking {
        val sessionBackend = ApkInstallBackend { _, _ ->
            ApkInstallResult.Failure(
                backendId = ApkInstallBackendId.ShizukuSession,
                reason = ApkInstallFailureReason.BackendUnavailable,
                message = "hidden constructor unavailable"
            )
        }
        val shellBackend = ApkInstallBackend { _, _ ->
            ApkInstallResult.Success(ApkInstallBackendId.ShizukuShell, "os.kei.demo")
        }
        val dual = ShizukuDualInstallBackend(sessionBackend, shellBackend)

        val result = dual.install(request())

        assertIs<ApkInstallResult.Success>(result)
        assertEquals(ApkInstallBackendId.ShizukuShell, result.backendId)
    }

    private fun backend(gateway: FakeSessionGateway): ShizukuSessionInstallBackend {
        return ShizukuSessionInstallBackend(
            runtimeCapabilities = sessionRuntimeCapabilities(),
            sessionGateway = gateway,
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

    private class FakeSessionGateway(
        private val handle: FakeSessionHandle = FakeSessionHandle(),
        private val sessionId: Int = 42
    ) : PackageInstallerSessionGateway {
        val params = mutableListOf<ApkInstallSessionParams>()
        val abandonedSessionIds = mutableListOf<Int>()

        override suspend fun createSession(params: ApkInstallSessionParams): Int {
            this.params += params
            return sessionId
        }

        override suspend fun openSession(sessionId: Int): PackageInstallerSessionHandle {
            return handle
        }

        override suspend fun abandonSession(sessionId: Int) {
            abandonedSessionIds += sessionId
        }
    }

    private class FakeSessionHandle(
        private val commitResult: PackageInstallCommitResult = PackageInstallCommitResult(
            status = PackageInstaller.STATUS_SUCCESS,
            legacyStatus = 1,
            message = "",
            userAction = null
        ),
        private val writeError: Throwable? = null
    ) : PackageInstallerSessionHandle {
        val writtenEntries = mutableListOf<String>()
        var closed = false

        override suspend fun write(
            entry: ApkInstallEntry,
            onBytesWritten: suspend (Long) -> Unit
        ) {
            writeError?.let { throw it }
            entry.openInputStream().use { input -> input.readBytes() }
            writtenEntries += entry.name
            onBytesWritten(entry.sizeBytes)
        }

        override suspend fun commit(): PackageInstallCommitResult = commitResult

        override fun close() {
            closed = true
        }
    }

    private fun sessionRuntimeCapabilities(): ShizukuRuntimeCapabilities {
        return ShizukuRuntimeCapabilities(
            object : ShizukuRuntimeProbe {
                override fun runtimeState(): ShizukuRuntimeState {
                    return ShizukuRuntimeState(
                        binderAlive = true,
                        preV11 = false,
                        permissionGranted = true,
                        serviceUid = 10_000,
                        commandIdentity = ShizukuCommandIdentity.UNSUPPORTED
                    )
                }

                override fun remotePermissionGranted(permission: String): Boolean = true
            }
        )
    }
}
