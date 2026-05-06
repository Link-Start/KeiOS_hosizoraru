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
        assertEquals("View status", notification.actions[0].title.toString())
        assertEquals("Cancel linkage", notification.actions[1].title.toString())
    }

    @Test
    fun `asset ready notification uses install selection action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.AssetReady,
            owner = "owner",
            repo = "repo",
            releaseTag = "v1.2.3",
            count = 2
        )

        val notification = buildModern(context, state)

        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertEquals(2, notification.actions.size)
        assertEquals("Choose APK", notification.actions[0].title.toString())
        assertEquals("Cancel linkage", notification.actions[1].title.toString())
    }

    @Test
    fun `added notification keeps live update and tracking actions`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.Added,
            owner = "owner",
            repo = "repo",
            appLabel = "Demo"
        )

        val notification = buildModern(context, state)

        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertEquals(McpNotificationHelper.LIVE_CHANNEL_ID, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(
            "GitHub tracking added",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            "Demo was added to owner/repo tracking",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertEquals(2, notification.actions.size)
        assertEquals("View tracking", notification.actions[0].title.toString())
        assertEquals("Mark read", notification.actions[1].title.toString())
    }

    @Test
    fun `already tracked notification keeps live update and tracking actions`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.AlreadyTracked,
            owner = "owner",
            repo = "repo",
            appLabel = "Demo"
        )

        val notification = buildModern(context, state)

        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertEquals(McpNotificationHelper.LIVE_CHANNEL_ID, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(
            "GitHub tracking already exists",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(2, notification.actions.size)
        assertEquals("View tracking", notification.actions[0].title.toString())
        assertEquals("Mark read", notification.actions[1].title.toString())
    }

    @Test
    fun `cancelled notification keeps live update and mark read actions`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.Cancelled
        )

        val notification = buildModern(context, state)

        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(
            "Share import cancelled",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(2, notification.actions.size)
        assertEquals("View GitHub", notification.actions[0].title.toString())
        assertEquals("Mark read", notification.actions[1].title.toString())
    }

    @Test
    fun `resolving notification keeps progress action without duplicate secondary action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.Resolving,
            primaryLabel = "https://github.com/owner/repo/releases"
        )

        val notification = buildModern(context, state)

        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertEquals(1, notification.actions.size)
        assertEquals("View progress", notification.actions[0].title.toString())
    }

    @Test
    fun `install detected notification offers confirmation and cancel actions`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.InstallDetected,
            owner = "owner",
            repo = "repo",
            appLabel = "Demo",
            packageName = "demo.app"
        )

        val notification = buildModern(context, state)

        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertEquals(2, notification.actions.size)
        assertEquals("Confirm tracking", notification.actions[0].title.toString())
        assertEquals("Cancel linkage", notification.actions[1].title.toString())
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
