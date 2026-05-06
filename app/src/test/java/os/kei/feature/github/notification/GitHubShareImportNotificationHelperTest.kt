package os.kei.feature.github.notification

import android.app.Application
import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.mcp.notification.McpNotificationHelper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    application = GitHubShareImportNotificationHelperTestApp::class,
    sdk = [35]
)
class GitHubShareImportNotificationHelperTest {
    @Test
    fun `waiting install notification keeps live update semantics`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.WaitingInstall,
            owner = "owner",
            repo = "repo",
            assetName = "app-arm64.apk",
            count = 12
        )

        val notification = buildModern(context, state)

        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertEquals(McpNotificationHelper.LIVE_CHANNEL_ID, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(
            "Waiting for install",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            "owner/repo · app-arm64.apk · 12 min left",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertEquals(2, notification.actions.size)
        assertEquals("Open", notification.actions[0].title.toString())
        assertEquals("Cancel linkage", notification.actions[1].title.toString())
    }

    @Test
    fun `completed notification is readable and dismissible`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.Added,
            owner = "owner",
            repo = "repo",
            appLabel = "Demo"
        )

        val notification = buildModern(context, state)

        assertEquals(Notification.CATEGORY_STATUS, notification.category)
        assertEquals(McpNotificationHelper.LIVE_CHANNEL_ID, notification.channelId)
        assertFalse(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(
            "GitHub tracking added",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            "Demo was added to owner/repo tracking",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertEquals(1, notification.actions.size)
        assertEquals("Open", notification.actions[0].title.toString())
    }

    @Test
    fun `resolving notification avoids duplicate open actions`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.Resolving,
            primaryLabel = "https://github.com/owner/repo/releases"
        )

        val notification = buildModern(context, state)

        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertEquals(1, notification.actions.size)
        assertEquals("Open", notification.actions[0].title.toString())
    }

    private fun buildModern(
        context: Context,
        state: GitHubShareImportNotificationState
    ): Notification {
        return GitHubShareImportNotificationHelper.buildFrameworkLiveUpdateNotification(
            context,
            state
        )
    }
}

class GitHubShareImportNotificationHelperTestApp : Application()
