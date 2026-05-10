package os.kei.feature.github.notification

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import os.kei.MainActivity
import kotlin.test.assertEquals

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
