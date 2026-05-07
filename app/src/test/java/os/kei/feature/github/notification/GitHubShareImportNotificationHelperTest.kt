package os.kei.feature.github.notification

import android.app.Application
import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.mcp.notification.McpNotificationHelper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun `waiting install notification shows exact package linkage when package is scanned`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.WaitingInstall,
            owner = "owner",
            repo = "repo",
            assetName = "app-arm64.apk",
            packageName = "demo.app",
            count = 12
        )

        val notification = buildModern(context, state)

        assertEquals(
            "owner/repo · demo.app · exact match · 12 min left",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
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
    fun `failed notification keeps live update and mark read actions`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.Failed,
            primaryLabel = "Network timeout"
        )

        val notification = buildModern(context, state)

        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertEquals(McpNotificationHelper.LIVE_CHANNEL_ID, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(
            "Share import failed",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            "Network timeout",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
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

    @Test
    fun `waiting install mi island notification exposes progress and linkage actions`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.WaitingInstall,
            owner = "owner",
            repo = "repo",
            assetName = "app-arm64.apk",
            packageName = "demo.app",
            count = 12
        )

        val notification = buildMiIsland(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()
        val focusOpenAction = notification.focusAction("mcp_action_open")
        val focusCancelAction = notification.focusAction("mcp_action_stop")

        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertEquals(McpNotificationHelper.CHANNEL_ID, notification.channelId)
        assertEquals("View status", notification.actions[0].title.toString())
        assertEquals("Cancel linkage", notification.actions[1].title.toString())
        assertEquals("View status", focusOpenAction.title.toString())
        assertEquals("Cancel linkage", focusCancelAction.title.toString())
        assertTrue(focusParam.contains("\"progress\":72"))
        assertTrue(focusParam.contains("\"title\":\"demo.app\""))
        assertTrue(focusParam.contains("demo.app"))
    }

    @Test
    fun `resolving mi island notification keeps only progress action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.Resolving,
            primaryLabel = "https://github.com/owner/repo/releases"
        )

        val notification = buildMiIsland(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertEquals(1, notification.actions.size)
        assertEquals("View progress", notification.actions[0].title.toString())
        assertTrue(focusParam.contains("mcp_action_open"))
        assertFalse(focusParam.contains("mcp_action_stop"))
        assertTrue(focusParam.contains("\"progress\":12"))
    }

    @Test
    fun `delivering mi island compact text says sending`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.Delivering,
            owner = "owner",
            repo = "repo",
            assetName = "demo.apk",
            targetDisplayName = "Demo"
        )

        val notification = buildMiIsland(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertEquals(
            "Sending",
            context.getString(GitHubShareImportNotificationPhase.Delivering.shortTextRes)
        )
        assertTrue(focusParam.contains("\"title\":\"Sending\""))
        assertFalse(focusParam.contains("\"content\":\"Demo\""))
    }

    @Test
    fun `install detected mi island compact title uses app label`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.InstallDetected,
            owner = "owner",
            repo = "repo",
            appLabel = "Demo",
            packageName = "demo.app"
        )

        val notification = buildMiIsland(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertTrue(focusParam.contains("\"title\":\"Demo\""))
        assertFalse(focusParam.contains("\"title\":\"Detect\""))
        assertFalse(focusParam.contains("\"content\":\"Demo\""))
        assertEquals(
            "Install detected",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            "Demo · demo.app",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
    }

    @Test
    fun `final mi island notifications stay promoted and offer mark read`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val states = listOf(
            GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.Added,
                owner = "owner",
                repo = "repo",
                appLabel = "Demo"
            ) to "View tracking",
            GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.AlreadyTracked,
                owner = "owner",
                repo = "repo",
                appLabel = "Demo"
            ) to "View tracking",
            GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.Failed,
                primaryLabel = "Network timeout"
            ) to "View GitHub",
            GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.Cancelled
            ) to "View GitHub"
        )

        states.forEach { (state, primaryAction) ->
            val notification = buildMiIsland(context, state)
            val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

            assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertEquals(primaryAction, notification.actions[0].title.toString())
            assertEquals("Mark read", notification.actions[1].title.toString())
            assertTrue(focusParam.contains("\"progress\":100"))
            assertTrue(focusParam.contains("mcp_action_open"))
            assertTrue(focusParam.contains("mcp_action_stop"))
            if (state.phase == GitHubShareImportNotificationPhase.Added ||
                state.phase == GitHubShareImportNotificationPhase.AlreadyTracked
            ) {
                assertTrue(focusParam.contains("\"title\":\"Demo\""))
                assertTrue(focusParam.contains("\"colorReach\":\"#22C55E\""))
            }
        }
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

    private fun buildMiIsland(
        context: Context,
        state: GitHubShareImportNotificationState
    ): Notification {
        return GitHubShareImportNotificationHelper.buildFrameworkMiIslandNotification(
            context,
            state
        )
    }

    private fun Notification.focusAction(key: String): Notification.Action {
        val actions = extras.getBundle("miui.focus.actions")
        assertNotNull(actions, "Focus actions bundle should be present")
        return actions.getActionCompat(key)
    }

    @Suppress("DEPRECATION")
    private fun Bundle.getActionCompat(key: String): Notification.Action {
        return getParcelable<Notification.Action>(key)
            ?: error("Missing focus action: $key")
    }
}

class GitHubShareImportNotificationHelperTestApp : Application()
