package os.kei.feature.github.install

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
@Config(application = GitHubShizukuPackageInstallerTestApp::class, sdk = [35])
class GitHubShizukuPackageInstallerTest {
    @Test
    fun `installer fails before session when shizuku is unavailable`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val installer = GitHubShizukuPackageInstaller(
            bridge = FakeBridge(
                ShizukuPackageInstallerCapability(
                    available = false,
                    failureReason = GitHubApkInstallFailureReason.ShizukuUnavailable,
                    message = "No Shizuku"
                )
            ),
            client = OkHttpClient()
        )

        val result = installer.install(context, request())

        val failed = assertIs<GitHubApkInstallResult.Failed>(result)
        assertEquals(GitHubApkInstallFailureReason.ShizukuUnavailable, failed.reason)
        assertEquals("No Shizuku", failed.message)
    }

    @Test
    fun `installer rejects non https resolved url before creating session`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val installer = GitHubShizukuPackageInstaller(
            bridge = FakeBridge(ShizukuPackageInstallerCapability(available = true)),
            client = OkHttpClient()
        )

        val result = installer.install(
            context = context,
            request = request(resolvedDownloadUrl = "http://example.com/app.apk")
        )

        val failed = assertIs<GitHubApkInstallResult.Failed>(result)
        assertEquals(GitHubApkInstallFailureReason.DownloadUrlInvalid, failed.reason)
    }

    @Test
    fun `progress percent is bounded`() {
        assertEquals(
            100,
            GitHubApkInstallProgress(
                stage = GitHubApkInstallStage.Staging,
                progressPercent = 140
            ).boundedProgressPercent
        )
        assertEquals(
            0,
            GitHubApkInstallProgress(
                stage = GitHubApkInstallStage.Preparing,
                progressPercent = -5
            ).boundedProgressPercent
        )
    }

    @Test
    fun `request id can be scoped by runtime package name`() {
        val requestId = GitHubApkInstallRequestIds.newId("os.kei.debug")

        assertEquals(true, requestId.startsWith("os.kei.debug-github-apk-"))
    }

    @Test
    fun `install result action is scoped by runtime package name`() {
        assertEquals(
            "os.kei.debug.github.install.action.SHIZUKU_INSTALL_RESULT",
            GitHubShizukuInstallCommitRegistry.installResultAction("os.kei.debug")
        )
        assertEquals(
            "os.kei.benchmark.github.install.action.SHIZUKU_INSTALL_RESULT",
            GitHubShizukuInstallCommitRegistry.installResultAction("os.kei.benchmark")
        )
    }

    private fun request(
        resolvedDownloadUrl: String = "https://example.com/app.apk"
    ): GitHubApkInstallRequest {
        return GitHubApkInstallRequest(
            owner = "owner",
            repo = "repo",
            releaseTag = "v1.0.0",
            projectUrl = "https://github.com/owner/repo",
            asset = GitHubReleaseAssetFile(
                name = "app.apk",
                downloadUrl = "https://example.com/app.apk",
                sizeBytes = 128L,
                downloadCount = 0
            ),
            lookupConfig = GitHubLookupConfig(appManagedShareInstallEnabled = true),
            scannedPackageName = "demo.app",
            resolvedDownloadUrl = resolvedDownloadUrl
        )
    }

    private class FakeBridge(
        private val capability: ShizukuPackageInstallerCapability
    ) : ShizukuPackageInstallerBridge() {
        override fun checkCapability(): ShizukuPackageInstallerCapability = capability

        override fun packageInstaller(context: Context): android.content.pm.PackageInstaller {
            error("PackageInstaller should not be requested")
        }
    }
}

class GitHubShizukuPackageInstallerTestApp : Application()
