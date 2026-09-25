package os.kei.feature.github.notification

import android.app.Application
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import os.kei.MainActivity
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
        // The text stays the same whether or not the pending track has a scanned package name.
        listOf("", "demo.app").forEach { packageName ->
            val case = "packageName=\"$packageName\""
            val state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.WaitingInstall,
                owner = "owner",
                repo = "repo",
                assetName = "app-arm64.apk",
                packageName = packageName,
                count = 12
            )

            val notification = buildModern(context, state)

            assertEquals(Notification.CATEGORY_PROGRESS, notification.category, case)
            assertEquals(McpNotificationHelper.LIVE_CHANNEL_ID, notification.channelId, case)
            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0, case)
            assertEquals(
                "Waiting for install",
                notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
                case
            )
            assertEquals(
                "repo · 12 min",
                notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
                case
            )
            assertEquals(2, notification.actions.size, case)
            assertEquals("Open flow", notification.actions[0].title.toString(), case)
            assertEquals("Refresh", notification.actions[1].title.toString(), case)
        }
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
        assertSendInstallReceiverAction(context, notification.actions[1])
    }

    @Test
    fun `terminal notifications keep live update and mark read actions`() {
        data class Case(
            val name: String,
            val state: GitHubShareImportNotificationState,
            val title: String,
            val text: String?,
            val primaryAction: String,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        listOf(
            Case(
                name = "added",
                state = GitHubShareImportNotificationState(
                    phase = GitHubShareImportNotificationPhase.Added,
                    owner = "owner",
                    repo = "repo",
                    appLabel = "Demo"
                ),
                title = "GitHub tracking added",
                text = "Demo was added to owner/repo tracking",
                primaryAction = "View tracking",
            ),
            Case(
                name = "already tracked",
                state = GitHubShareImportNotificationState(
                    phase = GitHubShareImportNotificationPhase.AlreadyTracked,
                    owner = "owner",
                    repo = "repo",
                    appLabel = "Demo"
                ),
                title = "GitHub tracking already exists",
                text = null,
                primaryAction = "View tracking",
            ),
            Case(
                name = "cancelled",
                state = GitHubShareImportNotificationState(
                    phase = GitHubShareImportNotificationPhase.Cancelled
                ),
                title = "Share import cancelled",
                text = null,
                primaryAction = "View GitHub",
            ),
            Case(
                name = "failed",
                state = GitHubShareImportNotificationState(
                    phase = GitHubShareImportNotificationPhase.Failed,
                    primaryLabel = "Network timeout"
                ),
                title = "Share import failed",
                text = "Network timeout",
                primaryAction = "View GitHub",
            ),
        ).forEach { case ->
            val notification = buildModern(context, case.state)

            assertEquals(Notification.CATEGORY_PROGRESS, notification.category, case.name)
            assertEquals(McpNotificationHelper.LIVE_CHANNEL_ID, notification.channelId, case.name)
            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT == 0, case.name)
            assertNotNull(notification.deleteIntent, case.name)
            assertEquals(
                case.title,
                notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
                case.name
            )
            if (case.text != null) {
                assertEquals(
                    case.text,
                    notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
                    case.name
                )
            }
            assertEquals(2, notification.actions.size, case.name)
            assertEquals(case.primaryAction, notification.actions[0].title.toString(), case.name)
            assertEquals("Mark read", notification.actions[1].title.toString(), case.name)
        }
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
            packageName = "demo.app",
            versionName = "2.0.0"
        )

        val notification = buildModern(context, state)

        assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
        assertEquals(
            "Demo · repo · 2.0.0",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertEquals(2, notification.actions.size)
        assertEquals("Open flow", notification.actions[0].title.toString())
        assertEquals("Confirm tracking", notification.actions[1].title.toString())
    }

    @Test
    fun `waiting install mi island notification uses concise status and linkage actions`() {
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
        assertTrue(focusParam.contains("\"business\":\"keios\""))
        assertTrue(focusParam.contains("\"notifyId\":\"38991\""))
        assertTrue(focusParam.contains("\"orderId\":\"github_share_import\""))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertFalse(focusParam.contains("multiProgressInfo"))
        assertTrue(focusParam.contains("\"title\":\"Install\""))
        assertFalse(focusParam.contains("\"title\":\"repo\""))
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
        assertSendInstallReceiverAction(context, focusCancelAction)
        assertTrue(focusParam.contains("\"title\":\"Ready\""))
        assertTrue(focusParam.contains("v1.2.3"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
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
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertTrue(focusParam.contains("\"title\":\"Parse\""))
    }

    @Test
    fun `delivering mi island compact text uses phase label`() {
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
        assertTrue(focusParam.contains("Demo"))
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
            "Download APK",
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
        assertTrue(focusParam.contains("\"content\":\"Download\""))
        assertTrue(focusParam.contains("\"specialTitle\":\"v1.2.3\""))
        assertEquals(1, focusParam.split("v1.2.3").size - 1)
        assertTrue(focusParam.contains("progressInfo"))
        assertFalse(focusParam.contains("multiProgressInfo"))
        assertTrue(focusParam.contains("\"colorProgress\":\"#2563EB\""))
        assertTrue(focusParam.contains("\"colorContent\":\"#475569\""))
        assertTrue(focusParam.contains("\"progress\":48"))
    }

    @Test
    fun `page install downloading uses github actions and focus safe content`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            source = GitHubShareImportNotificationSource.PageInstall,
            phase = GitHubShareImportNotificationPhase.InstallDownloading,
            owner = "T8RIN",
            repo = "ImageToolbox",
            releaseTag = "4.2.0-alpha01",
            targetDisplayName = "Image Toolbox With A Very Long Display Name",
            progressPercentOverride = 99,
            downloadedBytes = 98_160_000L,
            totalBytes = 98_560_000L,
        )

        val modern = buildModern(context, state)
        val miIsland = buildMiIsland(context, state)
        val focusParam = miIsland.extras.getString("miui.focus.param").orEmpty()
        val focusJson = JSONObject(focusParam).getJSONObject("param_v2")
        val focusContent = focusJson.getJSONObject("baseInfo").getString("content")
        val openIntent = shadowOf(modern.actions[0].actionIntent).savedIntent

        assertEquals(2, modern.actions.size)
        assertEquals("View progress", modern.actions[0].title.toString())
        assertEquals("Cancel install", modern.actions[1].title.toString())
        assertEquals(MainActivity::class.java.name, openIntent.component?.className)
        assertEquals(
            MainActivity.TARGET_BOTTOM_PAGE_GITHUB,
            openIntent.getStringExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE),
        )
        assertCancelPageInstallReceiverAction(context, modern.actions[1])
        assertCancelPageInstallReceiverAction(
            context,
            miIsland.focusAction("mcp_action_stop"),
        )
        assertTrue(focusContent.length <= 28, focusContent)
        assertFalse(focusContent.contains("..."), focusContent)
        assertTrue(focusParam.contains("\"orderId\":\"github_page_install\""))
    }

    @Test
    fun `managed staging mi island uses concise phase status`() {
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
        assertFalse(focusParam.contains("progressInfo"))
        assertTrue(focusParam.contains("\"title\":\"Prepare\""))
        assertTrue(focusParam.contains("Demo"))
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
            "repo · v1.2.3",
            modern.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertTrue(focusParam.contains("\"title\":\"Prepare\""))
        assertTrue(focusParam.contains("\"specialTitle\":\"v1.2.3\""))
        assertTrue(focusParam.contains("\"content\":\"repo\""))
        assertEquals(1, focusParam.split("v1.2.3").size - 1)
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
        val focusParam = buildMiIsland(context, state)
            .extras
            .getString("miui.focus.param")
            .orEmpty()

        assertEquals(
            "Demo · 2.0.0",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertTrue(focusParam.contains("\"specialTitle\":\"2.0.0\""))
        assertFalse(focusParam.contains("v1.2.3"))
    }

    @Test
    fun `managed downloading without known total uses concise status template with byte text`() {
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

        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertFalse(focusParam.contains("progressInfo"))
        assertTrue(focusParam.contains("\"title\":\"Download\""))
        assertFalse(focusParam.contains("\"title\":\"repo\""))
        assertTrue(focusParam.contains("repo"))
        assertTrue(focusParam.contains("downloaded"))
    }

    @Test
    fun `managed install committing mi island uses concise phase status`() {
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
        assertFalse(focusParam.contains("progressInfo"))
        assertTrue(focusParam.contains("\"title\":\"Commit\""))
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
            "Confirm install",
            modern.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            "repo",
            modern.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertEquals(2, modern.actions.size)
        assertEquals("Open flow", modern.actions[0].title.toString())
        assertEquals("Continue install", modern.actions[1].title.toString())
        assertSendInstallReceiverAction(context, modern.actions[1])
        assertEquals("Open flow", focusOpenAction.title.toString())
        assertEquals("Continue install", focusContinueAction.title.toString())
        assertSendInstallReceiverAction(context, focusContinueAction)
        assertTrue(focusParam.contains("\"title\":\"Ready\""))
        assertFalse(focusParam.contains("\"title\":\"repo\""))
        assertTrue(focusParam.contains("\"islandFirstFloat\":true"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertFalse(focusParam.contains("progressInfo"))
    }

    @Test
    fun `install detected mi island compact title uses phase label`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.InstallDetected,
            owner = "owner",
            repo = "repo",
            appLabel = "Demo",
            packageName = "demo.app",
            versionName = "2.0.0"
        )

        val notification = buildMiIsland(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertTrue(focusParam.contains("\"title\":\"Confirm\""))
        assertTrue(focusParam.contains("Demo · repo · 2.0.0"))
        assertEquals(
            "Install detected",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            "Demo · repo · 2.0.0",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
    }

    @Test
    fun `page managed install completion uses final short state`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.PageInstallCompleted,
            owner = "owner",
            repo = "repo",
            appLabel = "Demo",
            packageName = "dev.demo.app",
            versionName = "2.0.0"
        )

        val notification = buildMiIsland(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()
        val focusJson = JSONObject(focusParam).getJSONObject("param_v2")

        assertEquals(
            "Install complete",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            "Demo · owner/repo",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertEquals("View GitHub", notification.actions[0].title.toString())
        assertEquals("Mark read", notification.actions[1].title.toString())
        assertNotNull(notification.deleteIntent)
        assertFalse(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(notification.extras.getBoolean("android.requestPromotedOngoing"))
        assertTrue(focusJson.getBoolean("enableFloat"))
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"title\":\"Done\""))
        assertEquals(
            "Demo",
            focusJson.getJSONObject("baseInfo").getString("content"),
        )
        assertEquals(
            "2.0.0",
            focusJson.getJSONObject("baseInfo").getString("specialTitle"),
        )
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
    }

    @Test
    fun `page managed install confirmation uses live update short state`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.PageInstallConfirm,
            owner = "owner",
            repo = "repo",
            appLabel = "Demo",
            packageName = "dev.demo.app",
            versionName = "2.0.0",
            pageInstallConfirmActionEnabled = true
        )

        val notification = buildMiIsland(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()
        val focusJson = JSONObject(focusParam).getJSONObject("param_v2")

        assertEquals(
            "Confirm install",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            "Demo · owner/repo · waiting",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertEquals(2, notification.actions.size)
        assertEquals("Review install", notification.actions[0].title.toString())
        assertEquals("Confirm install", notification.actions[1].title.toString())
        assertConfirmPageInstallReceiverAction(context, notification.actions[1])
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Review install", notification.focusAction("mcp_action_open").title.toString())
        val focusConfirmAction = notification.focusAction("mcp_action_stop")
        assertEquals("Confirm install", focusConfirmAction.title.toString())
        assertConfirmPageInstallReceiverAction(context, focusConfirmAction)
        assertTrue(focusParam.contains("\"title\":\"Confirm\""))
        assertEquals(
            "Demo",
            focusJson.getJSONObject("baseInfo").getString("content"),
        )
        assertEquals(
            "2.0.0",
            focusJson.getJSONObject("baseInfo").getString("specialTitle"),
        )
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"actionBgColor\":\"#DBEAFE\""))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
    }

    @Test
    fun `long install version keeps full value in content and semantic core in badge`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.PageInstallCompleted,
            owner = "owner",
            repo = "repo",
            appLabel = "PiliPlus",
            packageName = "com.example.piliplus",
            versionName = "2.1.0-c1a5e8d5b1d9f04",
        )

        val notification = buildMiIsland(context, state)
        val focusJson = JSONObject(
            notification.extras.getString("miui.focus.param").orEmpty(),
        ).getJSONObject("param_v2")
        val baseInfo = focusJson.getJSONObject("baseInfo")

        assertEquals(
            "Install complete",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertEquals("PiliPlus", baseInfo.getString("title"))
        assertEquals("2.1.0", baseInfo.getString("specialTitle"))
        assertEquals("2.1.0-c1a5e8d5b1d9f04", baseInfo.getString("content"))
    }

    @Test
    fun `page managed install confirmation waits for manifest before confirm action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = GitHubShareImportNotificationState(
            phase = GitHubShareImportNotificationPhase.PageInstallConfirm,
            owner = "owner",
            repo = "repo",
            appLabel = "Demo",
            packageName = "dev.demo.app",
            versionName = "2.0.0",
            pageInstallConfirmActionEnabled = false
        )

        val notification = buildMiIsland(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertEquals(1, notification.actions.size)
        assertEquals("Review install", notification.actions[0].title.toString())
        assertTrue(focusParam.contains("mcp_action_open"))
        assertFalse(focusParam.contains("mcp_action_stop"))
    }

    @Test
    fun `final mi island updates float once and offer mark read`() {
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
            ) to "View GitHub",
            GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.PageInstallCompleted,
                owner = "owner",
                repo = "repo",
                appLabel = "Demo"
            ) to "View GitHub",
            GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.PageInstallFailed,
                owner = "owner",
                repo = "repo",
                appLabel = "Demo"
            ) to "View GitHub",
            GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.PageInstallCancelled,
                owner = "owner",
                repo = "repo",
                appLabel = "Demo"
            ) to "View GitHub"
        )

        states.forEach { (state, primaryAction) ->
            val notification = buildMiIsland(context, state)
            val focusParam = notification.extras.getString("miui.focus.param").orEmpty()
            val focusJson = JSONObject(focusParam).getJSONObject("param_v2")

            assertEquals(Notification.CATEGORY_PROGRESS, notification.category)
            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT == 0)
            assertFalse(notification.extras.getBoolean("android.requestPromotedOngoing"))
            assertTrue(focusJson.getBoolean("enableFloat"))
            assertNotNull(notification.deleteIntent)
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
                val expectedTitle = if (state.phase == GitHubShareImportNotificationPhase.Added) {
                    "Tracked"
                } else {
                    "Exists"
                }
                assertTrue(focusParam.contains("\"title\":\"$expectedTitle\""))
                val expectedContent = if (state.phase == GitHubShareImportNotificationPhase.Added) {
                    "Demo was added"
                } else {
                    "Demo is already tracked"
                }
                assertTrue(focusParam.contains(expectedContent))
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

    private fun assertSendInstallReceiverAction(
        context: Context,
        action: Notification.Action,
    ) {
        val intent = shadowOf(action.actionIntent).savedIntent
        assertEquals(
            GitHubShareImportActionReceiver::class.java.name,
            intent.component?.className,
        )
        assertEquals(
            GitHubShareImportActionReceiver.actionSendInstallShareImport(context),
            intent.action,
        )
        assertTrue(intent.flags and Intent.FLAG_RECEIVER_FOREGROUND != 0)
    }

    private fun assertConfirmPageInstallReceiverAction(
        context: Context,
        action: Notification.Action,
    ) {
        val intent = shadowOf(action.actionIntent).savedIntent
        assertEquals(
            GitHubShareImportActionReceiver::class.java.name,
            intent.component?.className,
        )
        assertEquals(
            GitHubShareImportActionReceiver.actionConfirmPageInstall(context),
            intent.action,
        )
        assertTrue(intent.flags and Intent.FLAG_RECEIVER_FOREGROUND != 0)
    }

    private fun assertCancelPageInstallReceiverAction(
        context: Context,
        action: Notification.Action,
    ) {
        val intent = shadowOf(action.actionIntent).savedIntent
        assertEquals(
            GitHubShareImportActionReceiver::class.java.name,
            intent.component?.className,
        )
        assertEquals(
            GitHubShareImportActionReceiver.actionCancelPageInstall(context),
            intent.action,
        )
        assertTrue(intent.flags and Intent.FLAG_RECEIVER_FOREGROUND != 0)
    }

    @Suppress("DEPRECATION")
    private fun Bundle.getActionCompat(key: String): Notification.Action {
        return getParcelable<Notification.Action>(key)
            ?: error("Missing focus action: $key")
    }
}

class GitHubShareImportNotificationHelperTestApp : Application()
