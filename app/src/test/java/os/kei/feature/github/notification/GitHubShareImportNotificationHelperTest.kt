package os.kei.feature.github.notification

import android.app.Application
import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.ui.page.main.github.share.GitHubShareImportActivity
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
            "repo · 12 min",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertEquals(2, notification.actions.size)
        assertEquals("Open flow", notification.actions[0].title.toString())
        assertEquals("Refresh", notification.actions[1].title.toString())
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
            "repo · 12 min",
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
        assertEquals("Open flow", notification.actions[0].title.toString())
        assertEquals("Cancel linkage", notification.actions[1].title.toString())
    }

    @Test
    fun `single asset ready notification uses send install action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.AssetReady,
            owner = "owner",
            repo = "repo",
            releaseTag = "v1.2.3",
            count = 1,
            sendInstallActionEnabled = true
        )

        val notification = buildModern(context, state)

        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertEquals(2, notification.actions.size)
        assertEquals("Open flow", notification.actions[0].title.toString())
        assertEquals("Send install", notification.actions[1].title.toString())
        assertEquals(
            GitHubShareImportActivity::class.java.name,
            shadowOf(notification.actions[0].actionIntent).savedIntent.component?.className
        )
        assertEquals(
            GitHubShareImportActivity.ACTION_RESUME_SHARE_IMPORT,
            shadowOf(notification.actions[0].actionIntent).savedIntent.action
        )
        assertTrue(
            shadowOf(notification.actions[0].actionIntent).savedIntent.getBooleanExtra(
                GitHubShareImportActivity.EXTRA_FORCE_SHEET,
                false
            )
        )
        assertEquals(
            GitHubShareImportActivity.ACTION_SEND_INSTALL_SHARE_IMPORT,
            shadowOf(notification.actions[1].actionIntent).savedIntent.action
        )
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
        assertEquals("Open flow", notification.actions[0].title.toString())
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
        assertEquals("Open flow", notification.actions[0].title.toString())
        assertEquals("Confirm tracking", notification.actions[1].title.toString())
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
        assertEquals("Open flow", notification.actions[0].title.toString())
        assertEquals("Refresh", notification.actions[1].title.toString())
        assertEquals("Open flow", focusOpenAction.title.toString())
        assertEquals("Refresh", focusCancelAction.title.toString())
        assertTrue(focusParam.contains("\"progress\":72"))
        assertTrue(focusParam.contains("\"title\":\"repo\""))
        assertTrue(focusParam.contains("\"content\":\"Install\""))
        assertTrue(focusParam.contains("\"actionBgColor\":\"#E5E7EB\""))
        assertTrue(focusParam.contains("\"actionBgColorDark\":\"#334155\""))
        assertTrue(focusParam.contains("\"actionTitleColor\":\"#475569\""))
        assertFalse(focusParam.contains("demo.app"))
    }

    @Test
    fun `single asset ready mi island notification exposes send install action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.AssetReady,
            owner = "owner",
            repo = "repo",
            releaseTag = "v1.2.3",
            count = 1,
            sendInstallActionEnabled = true
        )

        val notification = buildMiIsland(context, state)
        val focusOpenAction = notification.focusAction("mcp_action_open")
        val focusCancelAction = notification.focusAction("mcp_action_stop")
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertEquals(2, notification.actions.size)
        assertEquals("Open flow", notification.actions[0].title.toString())
        assertEquals("Send install", notification.actions[1].title.toString())
        assertEquals("Open flow", focusOpenAction.title.toString())
        assertEquals("Send install", focusCancelAction.title.toString())
        assertTrue(focusParam.contains("\"title\":\"repo\""))
        assertTrue(focusParam.contains("\"content\":\"Ready\""))
        assertTrue(focusParam.contains("v1.2.3"))
        assertTrue(focusParam.contains("\"progress\":32"))
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
        assertEquals("Open flow", notification.actions[0].title.toString())
        assertTrue(focusParam.contains("mcp_action_open"))
        assertFalse(focusParam.contains("mcp_action_stop"))
        assertTrue(focusParam.contains("\"progress\":12"))
        assertTrue(focusParam.contains("\"title\":\"repo\""))
    }

    @Test
    fun `delivering mi island compact text uses readable target name`() {
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
        assertTrue(focusParam.contains("\"title\":\"Demo\""))
        assertTrue(focusParam.contains("\"content\":\"Sending\""))
    }

    @Test
    fun `managed downloading notification exposes bytes and live progress`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.InstallDownloading,
            owner = "owner",
            repo = "repo",
            releaseTag = "v1.2.3",
            assetName = "demo.apk",
            targetDisplayName = "Demo",
            progressPercentOverride = 48,
            downloadedBytes = 5_120L,
            totalBytes = 10_240L
        )

        val notification = buildModern(context, state)
        val focusParam = buildMiIsland(context, state)
            .extras
            .getString("miui.focus.param")
            .orEmpty()

        assertEquals(
            "Downloading APK",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertTrue(
            notification.extras
                .getCharSequence(Notification.EXTRA_TEXT)
                .toString()
                .contains("Demo · v1.2.3")
        )
        assertTrue(focusParam.contains("progressTextInfo"))
        assertTrue(focusParam.contains("\"title\":\"48%\""))
        assertTrue(focusParam.contains("\"content\":\"Demo"))
        assertTrue(focusParam.contains("v1.2.3"))
        assertTrue(focusParam.contains("\"colorTitle\":\"#2563EB\""))
        assertTrue(focusParam.contains("\"colorContent\":\"#475569\""))
        assertTrue(focusParam.contains("5"))
        assertTrue(focusParam.contains("10"))
        assertTrue(focusParam.contains("\"progress\":48"))
    }

    @Test
    fun `managed staging mi island uses stable status template`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.Installing,
            owner = "owner",
            repo = "repo",
            assetName = "demo.apk",
            targetDisplayName = "Demo",
            progressPercentOverride = 48
        )

        val notification = buildMiIsland(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertEquals("Open flow", notification.actions[0].title.toString())
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertTrue(focusParam.contains("\"title\":\"Demo\""))
        assertTrue(focusParam.contains("\"content\":\"Prepare\""))
    }

    @Test
    fun `managed install content falls back to release tag before manifest version arrives`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.Installing,
            owner = "owner",
            repo = "repo",
            releaseTag = "v1.2.3",
            assetName = "demo.apk",
            progressPercentOverride = 36
        )

        val modern = buildModern(context, state)
        val focusParam = buildMiIsland(context, state)
            .extras
            .getString("miui.focus.param")
            .orEmpty()

        assertEquals(
            "repo · v1.2.3 · preparing",
            modern.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertTrue(focusParam.contains("repo · v1.2.3"))
    }

    @Test
    fun `managed install content prefers manifest version name over release tag`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.Installing,
            owner = "owner",
            repo = "repo",
            releaseTag = "v1.2.3",
            assetName = "demo.apk",
            targetDisplayName = "Demo",
            versionName = "2.0.0",
            progressPercentOverride = 36
        )

        val notification = buildModern(context, state)

        assertEquals(
            "Demo · 2.0.0 · preparing",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
    }

    @Test
    fun `managed downloading without known total uses status template with byte text`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.InstallDownloading,
            owner = "owner",
            repo = "repo",
            assetName = "demo.apk",
            progressPercentOverride = 0,
            downloadedBytes = 5_120L,
            totalBytes = -1L
        )

        val notification = buildMiIsland(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertTrue(focusParam.contains("progressTextInfo"))
        assertTrue(focusParam.contains("combinePicInfo"))
        assertTrue(focusParam.contains("\"progress\":0"))
        assertTrue(focusParam.contains("\"title\":\"repo\""))
        assertTrue(focusParam.contains("\"content\":\"repo"))
        assertTrue(focusParam.contains("downloaded"))
    }

    @Test
    fun `managed install committing mi island uses stable status template`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.InstallCommitting,
            owner = "owner",
            repo = "repo",
            assetName = "demo.apk",
            packageName = "demo.app"
        )

        val notification = buildMiIsland(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertEquals("Open flow", notification.actions[0].title.toString())
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertTrue(focusParam.contains("\"title\":\"repo\""))
        assertTrue(focusParam.contains("\"content\":\"Commit\""))
        assertFalse(focusParam.contains("demo.app"))
    }

    @Test
    fun `managed install ready waits for explicit continue action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.InstallReady,
            owner = "owner",
            repo = "repo",
            assetName = "demo.apk",
            packageName = "demo.app"
        )

        val modern = buildModern(context, state)
        val miIsland = buildMiIsland(context, state)
        val focusParam = miIsland.extras.getString("miui.focus.param").orEmpty()
        val focusOpenAction = miIsland.focusAction("mcp_action_open")
        val focusContinueAction = miIsland.focusAction("mcp_action_stop")

        assertEquals(
            "Waiting to install",
            modern.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            "repo · ready to install",
            modern.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertEquals(2, modern.actions.size)
        assertEquals("Open flow", modern.actions[0].title.toString())
        assertEquals("Continue install", modern.actions[1].title.toString())
        assertEquals("Open flow", focusOpenAction.title.toString())
        assertEquals("Continue install", focusContinueAction.title.toString())
        assertTrue(focusParam.contains("\"title\":\"repo\""))
        assertTrue(focusParam.contains("\"content\":\"Ready\""))
        assertTrue(focusParam.contains("\"islandFirstFloat\":true"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
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
        assertTrue(focusParam.contains("\"content\":\"Confirm\""))
        assertEquals(
            "Install detected",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            "Demo · repo",
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
            assertTrue(focusParam.contains("imageTextInfoRight"))
            assertFalse(focusParam.contains("progressTextInfo"))
            assertFalse(focusParam.contains("combinePicInfo"))
            assertTrue(focusParam.contains("mcp_action_open"))
            assertTrue(focusParam.contains("mcp_action_stop"))
            assertFalse(focusParam.contains("\"actionTitle\":\"Mark read\",\"actionBgColor\""))
            assertFalse(focusParam.contains("\"actionBgColor\":\"#E25B6A\""))
            if (state.phase == GitHubShareImportNotificationPhase.Added ||
                state.phase == GitHubShareImportNotificationPhase.AlreadyTracked
            ) {
                assertTrue(focusParam.contains("\"title\":\"Demo\""))
                val expectedContent = if (state.phase == GitHubShareImportNotificationPhase.Added) {
                    "Tracked"
                } else {
                    "Exists"
                }
                assertTrue(focusParam.contains("\"content\":\"$expectedContent\""))
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
