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
import os.kei.ui.page.main.github.install.GitHubApkInstallFlowState
import os.kei.ui.page.main.github.install.GitHubApkInstallPhase
import kotlin.test.assertEquals
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
            context.getString(R.string.common_cancel),
            notification.actions[1].title.toString()
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_CANCEL_INSTALL,
            shadowOf(notification.actions[1].actionIntent).savedIntent.action
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
