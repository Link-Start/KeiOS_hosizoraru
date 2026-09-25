package os.kei.feature.github.install

import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Test
import os.kei.core.privilege.PrivilegeMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GitHubRootPackageInstallerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `session id is read from pm install-create output`() {
        val rows = listOf(
            "the pm create line" to ("Success: created install session [1935741244]" to 1935741244),
            "vendor output without the success prefix" to ("created session [42]\n" to 42),
            "an error instead of a session" to ("Error: java.lang.SecurityException" to null),
            "empty brackets" to ("Success: created install session []" to null),
            "session zero is unusable" to ("Success: created install session [0]" to null),
            "no output" to ("" to null),
        )

        rows.forEach { (label, row) ->
            assertEquals(row.second, parseRootInstallSessionId(row.first), label)
        }
    }

    @Test
    fun `install-commit output succeeds only on a terminal success line`() {
        val downgrade = """
            Success: streamed 4096 bytes
            Failure [INSTALL_FAILED_VERSION_DOWNGRADE]
        """.trimIndent()
        val noTerminalLine = "Exception occurred while executing 'install-commit'"
        // output to (succeeded, message); a null message is not checked
        val rows = listOf(
            "a success line" to ("Success\n" to (true to "Success")),
            "a failure line outranks a success line in the same output" to
                (downgrade to (false to "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]")),
            "output without a terminal line" to (noTerminalLine to (false to noTerminalLine)),
            "empty output" to ("" to (false to null)),
        )

        rows.forEach { (label, row) ->
            val outcome = parseRootInstallOutcome(row.first)
            assertEquals(row.second.first, outcome.succeeded, label)
            row.second.second?.let { assertEquals(it, outcome.message, label) }
        }
    }

    @Test
    fun `commit follows the backend that created the session`() = runTest {
        val shizuku = RecordingInstaller(sessionId = 11)
        val root = RecordingInstaller(sessionId = 22)
        var mode = PrivilegeMode.Root
        val router =
            GitHubModeRoutedApkInstaller(
                shizukuInstaller = shizuku,
                rootInstaller = root,
                activeMode = { mode },
            )

        val staged = router.stage(context, REQUEST) {}
        mode = PrivilegeMode.Shizuku
        router.commit(
            context = context,
            request = REQUEST,
            sessionId = (staged as GitHubApkInstallResult.Staged).sessionId,
        )

        assertEquals(listOf("stage", "commit:22"), root.calls)
        assertTrue(shizuku.calls.isEmpty())
    }

    @Test
    fun `an unknown session falls back to the active backend`() = runTest {
        val shizuku = RecordingInstaller(sessionId = 11)
        val root = RecordingInstaller(sessionId = 22)
        val router =
            GitHubModeRoutedApkInstaller(
                shizukuInstaller = shizuku,
                rootInstaller = root,
                activeMode = { PrivilegeMode.Shizuku },
            )

        router.cancel(context, sessionId = 999)

        assertEquals(listOf("cancel:999"), shizuku.calls)
        assertTrue(root.calls.isEmpty())
    }

    @Test
    fun `disabled mode rejects managed install without touching a privileged backend`() = runTest {
        val shizuku = RecordingInstaller(sessionId = 11)
        val root = RecordingInstaller(sessionId = 22)
        val router =
            GitHubModeRoutedApkInstaller(
                shizukuInstaller = shizuku,
                rootInstaller = root,
                activeMode = { PrivilegeMode.Disabled },
            )

        val result = router.stage(context, REQUEST) {}

        assertEquals(
            GitHubApkInstallFailureReason.PrivilegeModeDisabled,
            (result as GitHubApkInstallResult.Failed).reason,
        )
        assertTrue(shizuku.calls.isEmpty())
        assertTrue(root.calls.isEmpty())
    }

    private class RecordingInstaller(
        private val sessionId: Int,
    ) : GitHubManagedApkInstaller {
        val calls = mutableListOf<String>()

        override suspend fun stage(
            context: Context,
            request: GitHubApkInstallRequest,
            onProgress: suspend (GitHubApkInstallProgress) -> Unit,
        ): GitHubApkInstallResult {
            calls += "stage"
            return GitHubApkInstallResult.Staged(requestId = request.requestId, sessionId = sessionId)
        }

        override suspend fun commit(
            context: Context,
            request: GitHubApkInstallRequest,
            sessionId: Int,
            downloadedBytes: Long,
            totalBytes: Long,
            onProgress: suspend (GitHubApkInstallProgress) -> Unit,
        ): GitHubApkInstallResult {
            calls += "commit:$sessionId"
            return GitHubApkInstallResult.Succeeded(
                requestId = request.requestId,
                sessionId = sessionId,
                packageName = "",
            )
        }

        override suspend fun cancel(context: Context, sessionId: Int) {
            calls += "cancel:$sessionId"
        }
    }

    private companion object {
        val REQUEST =
            GitHubApkInstallRequest(
                owner = "octocat",
                repo = "hello",
                releaseTag = "v1",
                projectUrl = "https://github.com/octocat/hello",
                asset =
                    GitHubReleaseAssetFile(
                        name = "app.apk",
                        downloadUrl = "https://example.com/app.apk",
                        sizeBytes = 128L,
                        downloadCount = 0,
                    ),
                lookupConfig = GitHubLookupConfig(),
            )
    }
}
