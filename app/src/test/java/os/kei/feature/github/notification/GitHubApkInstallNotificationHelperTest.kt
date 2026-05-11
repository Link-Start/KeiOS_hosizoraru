package os.kei.feature.github.notification

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import os.kei.MainActivity
import os.kei.R
import os.kei.mcp.notification.McpNotificationDispatchMode
import os.kei.ui.page.main.github.install.GitHubApkInstallCandidate
import os.kei.ui.page.main.github.install.GitHubApkInstallFlowState
import os.kei.ui.page.main.github.install.GitHubApkInstallPhase
import os.kei.ui.page.main.github.install.GitHubApkInstallProgressKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun `remote ready notification prepares download and keeps content click on sheet`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notification = GitHubApkInstallNotificationHelper.buildFrameworkLiveUpdateNotification(
            context = context,
            state = GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.RemoteReady,
                selectedCandidateName = "demo.apk"
            )
        )

        assertEquals(Notification.CATEGORY_STATUS, notification.category)
        assertEquals(2, notification.actions.size)
        assertEquals(
            context.getString(R.string.github_apk_install_action_prepare_install),
            notification.actions[0].title.toString()
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_PREPARE_INSTALL,
            shadowOf(notification.actions[0].actionIntent).savedIntent.action
        )
        assertEquals(
            MainActivity::class.java.name,
            shadowOf(notification.contentIntent).savedIntent.component?.className
        )
        assertEquals(
            context.getString(R.string.common_cancel),
            notification.actions[1].title.toString()
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_CANCEL_INSTALL,
            shadowOf(notification.actions[1].actionIntent).savedIntent.action
        )
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(0, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(0, notification.extras.getInt(Notification.EXTRA_PROGRESS))
    }

    @Test
    fun `remote resolving notification uses status category for checking`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notification = GitHubApkInstallNotificationHelper.buildFrameworkLiveUpdateNotification(
            context = context,
            state = GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.RemoteResolving,
                selectedCandidateName = "demo.apk"
            )
        )

        assertEquals(Notification.CATEGORY_STATUS, notification.category)
        assertEquals(0, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(0, notification.extras.getInt(Notification.EXTRA_PROGRESS))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(
            context.getString(R.string.github_apk_install_notify_action_open_sheet),
            notification.actions[0].title.toString()
        )
        assertEquals(
            context.getString(R.string.common_cancel),
            notification.actions[1].title.toString()
        )
    }

    @Test
    fun `ready notification replaces inspecting text with confirmation state`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val inspecting = GitHubApkInstallNotificationHelper.buildFrameworkLiveUpdateNotification(
            context = context,
            state = GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.InspectingLocal,
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
    fun `remote ready mi island offers download action and status template`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notification = GitHubApkInstallNotificationHelper.buildFrameworkMiIslandNotification(
            context = context,
            state = GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.RemoteReady,
                selectedCandidateName = "demo.apk"
            )
        )
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()
        val focusOpenAction = notification.focusAction("mcp_action_open")
        val focusStopAction = notification.focusAction("mcp_action_stop")

        assertEquals(Notification.CATEGORY_STATUS, notification.category)
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"enableFloat\":true"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertFalse(focusParam.contains("\"progress\":"))
        assertEquals(
            context.getString(R.string.github_apk_install_action_prepare_install),
            focusOpenAction.title.toString()
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_PREPARE_INSTALL,
            shadowOf(focusOpenAction.actionIntent).savedIntent.action
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_CANCEL_INSTALL,
            shadowOf(focusStopAction.actionIntent).savedIntent.action
        )
    }

    @Test
    fun `remote resolving mi island uses status template for checking state`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notification = GitHubApkInstallNotificationHelper.buildFrameworkMiIslandNotification(
            context = context,
            state = GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.RemoteResolving,
                selectedCandidateName = "demo.apk"
            )
        )
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()
        val focusOpenAction = notification.focusAction("mcp_action_open")
        val focusStopAction = notification.focusAction("mcp_action_stop")

        assertEquals(Notification.CATEGORY_STATUS, notification.category)
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"islandFirstFloat\":false"))
        assertTrue(focusParam.contains("\"enableFloat\":false"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertFalse(focusParam.contains("\"progress\":"))
        assertEquals(
            context.getString(R.string.github_apk_install_notify_action_open_sheet),
            focusOpenAction.title.toString()
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_CANCEL_INSTALL,
            shadowOf(focusStopAction.actionIntent).savedIntent.action
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
    fun `success mi island floats completion with mark read action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notification = GitHubApkInstallNotificationHelper.buildFrameworkMiIslandNotification(
            context = context,
            state = GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.Success,
                selectedCandidateName = "demo.apk"
            )
        )
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()
        val focusOpenAction = notification.focusAction("mcp_action_open")
        val focusStopAction = notification.focusAction("mcp_action_stop")

        assertEquals(Notification.CATEGORY_STATUS, notification.category)
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"enableFloat\":true"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertEquals(
            context.getString(R.string.github_apk_install_notify_action_open_sheet),
            focusOpenAction.title.toString()
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_MARK_READ_INSTALL,
            shadowOf(focusStopAction.actionIntent).savedIntent.action
        )
    }

    @Test
    fun `checking mi island stays status template and keeps stop action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notification = GitHubApkInstallNotificationHelper.buildFrameworkMiIslandNotification(
            context = context,
            state = GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.InspectingLocal,
                progressKind = GitHubApkInstallProgressKind.Inspect,
                selectedCandidateName = "demo.apk"
            )
        )
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()
        val focusOpenAction = notification.focusAction("mcp_action_open")
        val focusStopAction = notification.focusAction("mcp_action_stop")

        assertEquals(Notification.CATEGORY_STATUS, notification.category)
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"enableFloat\":false"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertFalse(focusParam.contains("\"progress\":"))
        assertEquals(
            context.getString(R.string.github_apk_install_notify_action_open_sheet),
            focusOpenAction.title.toString()
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_CANCEL_INSTALL,
            shadowOf(focusStopAction.actionIntent).savedIntent.action
        )
    }

    @Test
    fun `ready mi island floats install confirmation action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notification = GitHubApkInstallNotificationHelper.buildFrameworkMiIslandNotification(
            context = context,
            state = GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.ReadyToInstall,
                progressKind = GitHubApkInstallProgressKind.Waiting,
                selectedCandidateName = "demo.apk"
            )
        )
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()
        val focusOpenAction = notification.focusAction("mcp_action_open")
        val focusStopAction = notification.focusAction("mcp_action_stop")

        assertEquals(Notification.CATEGORY_STATUS, notification.category)
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"enableFloat\":true"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertFalse(focusParam.contains("\"progress\":"))
        assertEquals(
            context.getString(R.string.github_apk_install_action_install),
            focusOpenAction.title.toString()
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_CONFIRM_INSTALL,
            shadowOf(focusOpenAction.actionIntent).savedIntent.action
        )
        assertEquals(
            GitHubApkInstallActionReceiver.ACTION_CANCEL_INSTALL,
            shadowOf(focusStopAction.actionIntent).savedIntent.action
        )
    }

    @Test
    fun `mi island install phases always carry focus payload and actions`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val states = listOf(
            GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.RemoteResolving,
                selectedCandidateName = "demo.apk"
            ),
            GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.RemoteReady,
                selectedCandidateName = "demo.apk"
            ),
            GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.Downloading,
                progressKind = GitHubApkInstallProgressKind.Download,
                selectedCandidateName = "demo.apk",
                stageProgress = 0.42f
            ),
            GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.SelectingApk,
                selectedCandidateName = "demo.apk",
                candidates = listOf(GitHubApkInstallCandidate(0, "demo.apk", 128L))
            ),
            GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.InspectingLocal,
                progressKind = GitHubApkInstallProgressKind.Inspect,
                selectedCandidateName = "demo.apk"
            ),
            GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.ReadyToInstall,
                progressKind = GitHubApkInstallProgressKind.Waiting,
                selectedCandidateName = "demo.apk"
            ),
            GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.Installing,
                progressKind = GitHubApkInstallProgressKind.Staging,
                selectedCandidateName = "demo.apk"
            ),
            GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.PendingUserAction,
                selectedCandidateName = "demo.apk"
            ),
            GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.Success,
                selectedCandidateName = "demo.apk"
            ),
            GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.Failed,
                selectedCandidateName = "demo.apk",
                message = "failed"
            ),
            GitHubApkInstallFlowState(
                phase = GitHubApkInstallPhase.Cancelled,
                selectedCandidateName = "demo.apk"
            )
        )

        states.forEach { state ->
            val notification =
                GitHubApkInstallNotificationHelper.buildFrameworkMiIslandNotification(
                    context = context,
                    state = state
                )
            val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

            assertTrue(focusParam.isNotBlank(), "Missing Focus payload for ${state.phase}")
            assertNotNull(
                notification.extras.getBundle("miui.focus.actions"),
                "Missing Focus actions for ${state.phase}"
            )
        }
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
            GitHubApkInstallNotificationBridge.resolveDispatchMode(
                state = first,
                useXiaomiMagic = true
            )
        )
        assertEquals(
            McpNotificationDispatchMode.Update,
            GitHubApkInstallNotificationBridge.resolveDispatchMode(
                state = second,
                useXiaomiMagic = true
            )
        )
        assertEquals(
            McpNotificationDispatchMode.Plain,
            GitHubApkInstallNotificationBridge.resolveDispatchMode(
                state = second.copy(sessionId = 10L),
                useXiaomiMagic = false
            )
        )
    }

    @Test
    fun `download percentage changes use smooth update after phase pulse`() {
        GitHubApkInstallNotificationHelper.resetDispatchStateForTest()
        val zero = GitHubApkInstallFlowState(
            sessionId = 12L,
            phase = GitHubApkInstallPhase.Downloading,
            progressKind = GitHubApkInstallProgressKind.Download,
            stageProgress = 0f
        )
        val firstProgress = zero.copy(stageProgress = 0.04f, progress = 0.04f)
        val laterProgress = zero.copy(stageProgress = 0.18f, progress = 0.18f)

        assertEquals(
            McpNotificationDispatchMode.Pulse,
            GitHubApkInstallNotificationBridge.resolveDispatchMode(
                state = zero,
                useXiaomiMagic = true
            )
        )
        assertEquals(
            McpNotificationDispatchMode.Update,
            GitHubApkInstallNotificationBridge.resolveDispatchMode(
                state = firstProgress,
                useXiaomiMagic = true
            )
        )
        assertEquals(
            McpNotificationDispatchMode.Update,
            GitHubApkInstallNotificationBridge.resolveDispatchMode(
                state = laterProgress,
                useXiaomiMagic = true
            )
        )
    }

    @Test
    fun `same phase status refresh uses smooth dispatch`() {
        GitHubApkInstallNotificationHelper.resetDispatchStateForTest()
        val first = GitHubApkInstallFlowState(
            sessionId = 11L,
            phase = GitHubApkInstallPhase.RemoteReady,
            selectedCandidateName = "demo.apk"
        )
        val second = first.copy(message = "manifest ready")

        assertEquals(
            McpNotificationDispatchMode.Pulse,
            GitHubApkInstallNotificationBridge.resolveDispatchMode(
                state = first,
                useXiaomiMagic = true
            )
        )
        assertEquals(
            McpNotificationDispatchMode.Update,
            GitHubApkInstallNotificationBridge.resolveDispatchMode(
                state = second,
                useXiaomiMagic = true
            )
        )
    }

    @Test
    fun `checking and ready phase changes use pulse dispatch`() {
        GitHubApkInstallNotificationHelper.resetDispatchStateForTest()
        val downloading = GitHubApkInstallFlowState(
            sessionId = 13L,
            phase = GitHubApkInstallPhase.Downloading,
            progressKind = GitHubApkInstallProgressKind.Download,
            stageProgress = 0.98f
        )
        val checking = downloading.copy(
            phase = GitHubApkInstallPhase.InspectingLocal,
            progressKind = GitHubApkInstallProgressKind.Inspect
        )
        val ready = checking.copy(
            phase = GitHubApkInstallPhase.ReadyToInstall,
            progressKind = GitHubApkInstallProgressKind.Waiting
        )

        assertEquals(
            McpNotificationDispatchMode.Pulse,
            GitHubApkInstallNotificationBridge.resolveDispatchMode(
                state = downloading,
                useXiaomiMagic = true
            )
        )
        assertEquals(
            McpNotificationDispatchMode.Pulse,
            GitHubApkInstallNotificationBridge.resolveDispatchMode(
                state = checking,
                useXiaomiMagic = true
            )
        )
        assertEquals(
            McpNotificationDispatchMode.Pulse,
            GitHubApkInstallNotificationBridge.resolveDispatchMode(
                state = ready,
                useXiaomiMagic = true
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

class GitHubApkInstallNotificationHelperTestApp : Application()
