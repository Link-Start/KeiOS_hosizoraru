package os.kei.feature.github.notification

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import os.kei.MainActivity
import os.kei.R
import os.kei.mcp.notification.McpNotificationDispatchMode
import os.kei.ui.page.main.github.install.GitHubApkInstallFlowState
import os.kei.ui.page.main.github.install.GitHubApkInstallPhase
import os.kei.ui.page.main.github.install.GitHubApkInstallProgressKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    application = GitHubApkInstallNotificationHelperTestApp::class,
    sdk = [35]
)
class GitHubApkInstallNotificationHelperTest {
    @Test
    fun `open pending intent routes to github install sheet`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val pendingIntent = invokeOpenPendingIntent(context)
        val savedIntent = shadowOf(pendingIntent).savedIntent

        assertEquals(MainActivity::class.java.name, savedIntent.component?.className)
        assertEquals(
            MainActivity.TARGET_BOTTOM_PAGE_GITHUB,
            savedIntent.getStringExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE)
        )
        assertEquals(
            MainActivity.SHORTCUT_ACTION_GITHUB_OPEN_APK_INSTALL_SHEET,
            savedIntent.getStringExtra(MainActivity.EXTRA_SHORTCUT_ACTION)
        )
    }

    @Test
    fun `installing notification uses status category and cancel action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notification = GitHubApkInstallNotificationHelper.buildFrameworkLiveUpdateNotification(
            context = context,
            state = GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.Installing,
                overallProgress = 0.72f,
                selectedCandidateName = "demo.apk",
                message = "Installing"
            )
        )

        assertEquals(Notification.CATEGORY_STATUS, notification.category)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(2, notification.actions.size)
        assertEquals(
            context.getString(R.string.github_apk_install_notify_action_open_sheet),
            notification.actions[0].title.toString()
        )
        assertEquals(
            context.getString(R.string.common_stop),
            notification.actions[1].title.toString()
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_CANCEL_INSTALL,
            shadowOf(notification.actions[1].actionIntent).savedIntent.action
        )
    }

    @Test
    fun `ready notification replaces inspecting text with confirmation state`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val inspecting = GitHubApkInstallNotificationHelper.buildFrameworkLiveUpdateNotification(
            context = context,
            state = GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.Inspecting,
                progressKind = GitHubApkInstallProgressKind.Inspect,
                selectedCandidateName = "demo.apk"
            )
        )
        val ready = GitHubApkInstallNotificationHelper.buildFrameworkLiveUpdateNotification(
            context = context,
            state = GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.ReadyToInstall,
                progressKind = GitHubApkInstallProgressKind.Waiting,
                selectedCandidateName = "demo.apk",
                stageProgress = 1f,
                progress = 1f,
                overallProgress = 0.6f,
                message = "Ready"
            )
        )

        assertEquals(
            context.getString(R.string.github_apk_install_notify_title_inspecting),
            inspecting.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            context.getString(R.string.github_apk_install_notify_content_inspecting, "demo.apk"),
            inspecting.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertEquals(Notification.CATEGORY_STATUS, ready.category)
        assertEquals(
            context.getString(R.string.github_apk_install_notify_title_review),
            ready.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            context.getString(R.string.github_apk_install_notify_content_review, "demo.apk"),
            ready.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertEquals(
            context.getString(R.string.github_apk_install_action_install),
            ready.actions[0].title.toString()
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_CONFIRM_INSTALL,
            shadowOf(ready.actions[0].actionIntent).savedIntent.action
        )
        assertEquals(
            context.getString(R.string.common_cancel),
            ready.actions[1].title.toString()
        )
        assertFalse(ready.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(0, ready.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(0, ready.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_CANCEL_INSTALL,
            shadowOf(ready.actions[1].actionIntent).savedIntent.action
        )
    }

    @Test
    fun `failed notification uses retry action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notification = GitHubApkInstallNotificationHelper.buildFrameworkLiveUpdateNotification(
            context = context,
            state = GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.Failed,
                message = "Install failed"
            )
        )

        assertEquals(2, notification.actions.size)
        assertEquals(
            context.getString(R.string.github_apk_install_notify_action_open_sheet),
            notification.actions[0].title.toString()
        )
        assertEquals(
            context.getString(R.string.github_apk_install_action_retry),
            notification.actions[1].title.toString()
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_RETRY_INSTALL,
            shadowOf(notification.actions[1].actionIntent).savedIntent.action
        )
    }

    @Test
    fun `pending user action notification opens system confirmation from primary action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notification = GitHubApkInstallNotificationHelper.buildFrameworkLiveUpdateNotification(
            context = context,
            state = GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.PendingUserAction,
                selectedCandidateName = "demo.apk"
            )
        )

        assertEquals(2, notification.actions.size)
        assertEquals(
            context.getString(R.string.github_apk_install_action_open_system_confirm),
            notification.actions[0].title.toString()
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_LAUNCH_PENDING_USER_ACTION,
            shadowOf(notification.actions[0].actionIntent).savedIntent.action
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_CANCEL_INSTALL,
            shadowOf(notification.actions[1].actionIntent).savedIntent.action
        )
    }

    @Test
    fun `download progress update uses smooth dispatch after first pulse`() {
        GitHubApkInstallNotificationHelper.resetDispatchStateForTest()
        val first = GitHubApkInstallFlowState(
            sessionId = 9L,
            phase = GitHubApkInstallPhase.Downloading,
            progressKind = GitHubApkInstallProgressKind.Download,
            stageProgress = 0.12f
        )
        val second = first.copy(stageProgress = 0.48f, progress = 0.48f)

        assertEquals(
            McpNotificationDispatchMode.Pulse,
            GitHubApkInstallNotificationHelper.resolveDispatchMode(
                state = first,
                useXiaomiMagic = true
            )
        )
        assertEquals(
            McpNotificationDispatchMode.Update,
            GitHubApkInstallNotificationHelper.resolveDispatchMode(
                state = second,
                useXiaomiMagic = true
            )
        )
        assertEquals(
            McpNotificationDispatchMode.Plain,
            GitHubApkInstallNotificationHelper.resolveDispatchMode(
                state = second.copy(sessionId = 10L),
                useXiaomiMagic = false
            )
        )
    }

    private fun invokeOpenPendingIntent(context: Context): PendingIntent {
        val method = GitHubApkInstallNotificationHelper::class.java.getDeclaredMethod(
            "buildOpenPendingIntent",
            Context::class.java
        ).apply {
            isAccessible = true
        }
        return method.invoke(GitHubApkInstallNotificationHelper, context) as PendingIntent
    }
}

class GitHubApkInstallNotificationHelperTestApp : Application()
