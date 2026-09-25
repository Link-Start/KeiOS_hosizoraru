package os.kei.ui.page.main.github.asset

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.ui.page.main.github.query.installerXRevivedPackageName
import os.kei.ui.page.main.github.query.systemDownloadManagerPackageName

@Config(application = Application::class, sdk = [35])
@RunWith(AndroidJUnit4::class)
class GitHubAssetHandoffTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun aChosenInstallerIsSentTheShareDirectly() {
        val context = RecordingContext(application)

        val result =
            GitHubAssetHandoff.share(
                context = context,
                lookupConfig = GitHubLookupConfig(onlineShareTargetPackage = installerXRevivedPackageName),
                url = APK_URL,
                subject = APK_NAME,
                chooserTitle = CHOOSER_TITLE,
            )

        assertEquals(GitHubAssetHandoffResult.Delivered(GitHubAssetHandoffRoute.ShareTarget), result)
        assertNull(result.messageRes, "The installer opening is the confirmation")
        val sent = context.started.single()
        // Not a chooser: issue #29 was this setting being skipped for a share sheet.
        assertEquals(Intent.ACTION_SEND, sent.action)
        assertEquals(installerXRevivedPackageName, sent.`package`)
        assertEquals(APK_URL, sent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(APK_NAME, sent.getStringExtra(Intent.EXTRA_SUBJECT))
        // What tells an online installer to install as it downloads rather than save the link as text.
        assertEquals("Online", sent.getStringExtra("channel"))
        assertEquals("Online", sent.getStringExtra("extra_channel"))
        assertTrue(sent.getBooleanExtra("online_channel", false))
    }

    @Test
    fun withNoInstallerChosenTheShareOpensTheShareSheet() {
        val context = RecordingContext(application)

        val result =
            GitHubAssetHandoff.share(
                context = context,
                lookupConfig = GitHubLookupConfig(),
                url = APK_URL,
                subject = APK_NAME,
                chooserTitle = CHOOSER_TITLE,
            )

        assertEquals(GitHubAssetHandoffResult.Delivered(GitHubAssetHandoffRoute.ShareChooser), result)
        val chooser = context.started.single()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = assertNotNull(chooser.sendIntent())
        assertEquals(Intent.ACTION_SEND, send.action)
        assertNull(send.`package`)
        assertEquals(APK_URL, send.getStringExtra(Intent.EXTRA_TEXT))
        assertFalse(send.hasExtra("online_channel"), "A share sheet target is not an online installer")
    }

    @Test
    fun anInstallerThatRefusesTheShareIsReportedAsAFailedShare() {
        val context = RecordingContext(application, refusedPackages = setOf(installerXRevivedPackageName))

        val result =
            GitHubAssetHandoff.share(
                context = context,
                lookupConfig = GitHubLookupConfig(onlineShareTargetPackage = installerXRevivedPackageName),
                url = APK_URL,
                subject = APK_NAME,
                chooserTitle = CHOOSER_TITLE,
            )

        assertEquals(GitHubAssetHandoffResult.Failed(R.string.github_toast_share_link_failed), result)
    }

    @Test
    fun onlyHttpsLinksLeaveTheApp() {
        val context = RecordingContext(application)
        val config = GitHubLookupConfig(onlineShareTargetPackage = installerXRevivedPackageName)

        val shared =
            GitHubAssetHandoff.share(
                context = context,
                lookupConfig = config,
                url = "http://example.org/app.apk",
                subject = APK_NAME,
                chooserTitle = CHOOSER_TITLE,
            )
        val downloaded =
            GitHubAssetHandoff.openInDownloader(
                context = context,
                lookupConfig = config,
                url = "http://example.org/app.apk",
                fileName = APK_NAME,
            )

        assertEquals(GitHubAssetHandoffResult.Failed(R.string.github_toast_share_link_failed), shared)
        assertEquals(GitHubAssetHandoffResult.Failed(R.string.github_toast_open_downloader_failed), downloaded)
        assertTrue(context.started.isEmpty())
    }

    @Test
    fun aDownloadGoesToTheChosenDownloader() {
        val context = RecordingContext(application)

        val result =
            GitHubAssetHandoff.openInDownloader(
                context = context,
                lookupConfig = GitHubLookupConfig(preferredDownloaderPackage = DOWNLOADER_PACKAGE),
                url = APK_URL,
                fileName = APK_NAME,
            )

        assertEquals(GitHubAssetHandoffResult.Delivered(GitHubAssetHandoffRoute.SelectedDownloader), result)
        val view = context.started.single()
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(DOWNLOADER_PACKAGE, view.`package`)
        assertEquals(APK_URL, view.dataString)
        assertTrue(view.hasCategory(Intent.CATEGORY_BROWSABLE))
    }

    @Test
    fun aDownloaderThatRefusesTheLinkFallsBackToTheSystemDefault() {
        val context = RecordingContext(application, refusedPackages = setOf(DOWNLOADER_PACKAGE))

        val result =
            GitHubAssetHandoff.openInDownloader(
                context = context,
                lookupConfig = GitHubLookupConfig(preferredDownloaderPackage = DOWNLOADER_PACKAGE),
                url = APK_URL,
                fileName = APK_NAME,
            )

        assertEquals(
            GitHubAssetHandoffResult.Delivered(GitHubAssetHandoffRoute.SelectedDownloaderFallback),
            result,
        )
        assertEquals(R.string.github_toast_downloader_fallback_system, result.messageRes)
        val view = context.started.single()
        assertNull(view.`package`)
        assertEquals(APK_URL, view.dataString)
    }

    @Test
    fun withNoDownloaderChosenTheSystemDefaultOpensTheLink() {
        val context = RecordingContext(application)

        val result =
            GitHubAssetHandoff.openInDownloader(
                context = context,
                lookupConfig = GitHubLookupConfig(),
                url = APK_URL,
                fileName = APK_NAME,
            )

        assertEquals(GitHubAssetHandoffResult.Delivered(GitHubAssetHandoffRoute.SystemDefault), result)
        assertNull(context.started.single().`package`)
    }

    @Test
    fun theBuiltInDownloaderIsEnqueuedRatherThanOpened() {
        val context = RecordingContext(application)

        val result =
            GitHubAssetHandoff.openInDownloader(
                context = context,
                lookupConfig = GitHubLookupConfig(preferredDownloaderPackage = systemDownloadManagerPackageName),
                url = APK_URL,
                fileName = APK_NAME,
            )

        assertEquals(
            GitHubAssetHandoffResult.Delivered(GitHubAssetHandoffRoute.SystemDownloadManager),
            result,
        )
        assertTrue(context.started.isEmpty())
    }

    @Test
    fun theConfiguredChannelPrefersAChosenInstallerOverTheDownloader() {
        val toInstaller = RecordingContext(application)
        val toDownloader = RecordingContext(application)

        val installerResult =
            runBlocking {
                GitHubAssetHandoff.sendAssetToConfiguredChannel(
                    context = toInstaller,
                    lookupConfig =
                        GitHubLookupConfig(
                            onlineShareTargetPackage = installerXRevivedPackageName,
                            preferredDownloaderPackage = DOWNLOADER_PACKAGE,
                        ),
                    asset = apkAsset(),
                    newTask = true,
                )
            }
        val downloaderResult =
            runBlocking {
                GitHubAssetHandoff.sendAssetToConfiguredChannel(
                    context = toDownloader,
                    lookupConfig = GitHubLookupConfig(preferredDownloaderPackage = DOWNLOADER_PACKAGE),
                    asset = apkAsset(),
                    newTask = true,
                )
            }

        assertEquals(GitHubAssetHandoffResult.Delivered(GitHubAssetHandoffRoute.ShareTarget), installerResult)
        assertEquals(installerXRevivedPackageName, toInstaller.started.single().`package`)
        assertEquals(
            GitHubAssetHandoffResult.Delivered(GitHubAssetHandoffRoute.SelectedDownloader),
            downloaderResult,
        )
        assertEquals(DOWNLOADER_PACKAGE, toDownloader.started.single().`package`)
        // Share-import sends from a notification action as well as from its activity.
        (toInstaller.started + toDownloader.started).forEach { intent ->
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }
    }

    @Test
    fun aResultSaysOnlyWhatTheScreenDoesNotAlreadySay() {
        val said = mutableListOf<Int>()

        val shared = GitHubAssetHandoffResult.Delivered(GitHubAssetHandoffRoute.ShareTarget).report(said::add)
        val fellBack =
            GitHubAssetHandoffResult.Delivered(GitHubAssetHandoffRoute.SelectedDownloaderFallback).report(said::add)
        val failed = GitHubAssetHandoffResult.Failed(R.string.github_toast_share_link_failed).report(said::add)

        assertTrue(shared)
        assertTrue(fellBack)
        assertFalse(failed)
        assertEquals(
            listOf(R.string.github_toast_downloader_fallback_system, R.string.github_toast_share_link_failed),
            said,
        )
    }

    private fun apkAsset(): GitHubReleaseAssetFile =
        GitHubReleaseAssetFile(
            name = APK_NAME,
            downloadUrl = APK_URL,
            sizeBytes = 1_600_000L,
            downloadCount = 0,
        )

    @Suppress("DEPRECATION")
    private fun Intent.sendIntent(): Intent? = getParcelableExtra(Intent.EXTRA_INTENT)

    /** Records what would have been started, and refuses the packages it is told are not installed. */
    private class RecordingContext(
        base: Context,
        private val refusedPackages: Set<String> = emptySet(),
    ) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            if (intent.`package` in refusedPackages) throw ActivityNotFoundException(intent.`package`)
            started += intent
        }
    }

    private companion object {
        const val APK_NAME = "Accounts_v1.5.apk"
        const val APK_URL = "https://github.com/iamr0s/Accounts/releases/download/v1.5/Accounts_v1.5.apk"
        const val CHOOSER_TITLE = "Share"
        const val DOWNLOADER_PACKAGE = "com.dv.adm"
    }
}
