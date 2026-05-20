package os.kei.feature.github.notification

import android.app.Application
import android.app.Notification
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import os.kei.MainActivity
import os.kei.R
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.notification.NotificationActionReceiver
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    application = GitHubActionsUpdateNotificationHelperTestApp::class,
    sdk = [35],
)
class GitHubActionsUpdateNotificationHelperTest {
    @Test
    @Suppress("DEPRECATION")
    fun `mi island summary keeps short run label and expanded action colors are semantic`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val trackedPackageName = "me.him188.ani"
        Shadows.shadowOf(context.packageManager).apply {
            addPackage(
                PackageInfo().apply {
                    packageName = trackedPackageName
                    applicationInfo =
                        ApplicationInfo().apply {
                            packageName = trackedPackageName
                        }
                },
            )
            setApplicationIcon(trackedPackageName, ColorDrawable(Color.MAGENTA))
        }
        val snapshot = createSnapshot(trackedPackageName)
        val notification = invokeMiIslandNotification(context, snapshot)
        val openAction = notification.focusAction("github_actions_update_open")
        val markReadAction = notification.focusAction("github_actions_update_read")
        val notificationId = GitHubActionsUpdateNotificationHelper.notificationId(snapshot)
        val displayIcon = notification.focusPicture("mi_focus_display")
        val expandedIcon = notification.focusPicture("mi_focus_expanded")
        val contentIntent = Shadows.shadowOf(notification.contentIntent).savedIntent
        val focusOpenIntent = Shadows.shadowOf(openAction.actionIntent).savedIntent
        val markReadIntent = Shadows.shadowOf(markReadAction.actionIntent).savedIntent
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertNotNull(notification.getLargeIcon())
        assertNotNull(Shadows.shadowOf(displayIcon).bitmap)
        assertNotNull(Shadows.shadowOf(expandedIcon).bitmap)
        assertEquals(context.getString(R.string.common_open), openAction.title.toString())
        assertEquals(context.getString(R.string.common_mark_read), markReadAction.title.toString())
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"title\":\"#44\""))
        assertFalse(focusParam.contains("\"content\":\"Actions\""))
        assertFalse(focusParam.contains("\"picFunction\""))
        assertTrue(focusParam.contains("\"baseInfo\""))
        assertTrue(focusParam.contains("\"picInfo\":{\"type\":1,\"pic\":\"mi_focus_expanded\""))
        assertTrue(focusParam.contains("\"actionTitle\":\"${context.getString(R.string.common_open)}\""))
        assertTrue(focusParam.contains("\"actionBgColor\":\"#3B82F6\""))
        assertTrue(focusParam.contains("\"actionTitleColor\":\"#FFFFFF\""))
        assertTrue(focusParam.contains("\"business\":\"keios\""))
        assertTrue(focusParam.contains("\"notifyId\":\"$notificationId\""))
        assertTrue(focusParam.contains("\"orderId\":\"${snapshot.trackId}\""))
        assertFalse(
            focusParam.contains(
                "\"actionTitle\":\"${context.getString(R.string.common_mark_read)}\",\"actionBgColor\"",
            ),
        )
        assertEquals(
            MainActivity.TARGET_BOTTOM_PAGE_GITHUB,
            contentIntent.getStringExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE),
        )
        assertEquals(
            snapshot.trackId,
            contentIntent.getStringExtra(MainActivity.EXTRA_GITHUB_ACTIONS_TRACK_ID),
        )
        assertEquals(
            MainActivity.TARGET_BOTTOM_PAGE_GITHUB,
            focusOpenIntent.getStringExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE),
        )
        assertEquals(
            snapshot.trackId,
            focusOpenIntent.getStringExtra(MainActivity.EXTRA_GITHUB_ACTIONS_TRACK_ID),
        )
        assertEquals(
            notificationId,
            markReadIntent.getIntExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, -1),
        )
    }

    @Test
    fun `notification id is scoped by tracked item`() {
        val first = createSnapshot("me.him188.ani")
        val sameItemNewRun =
            first.copy(
                runId = first.runId + 1,
                runNumber = first.runNumber + 1,
                runDisplayName = "Build #45",
            )
        val second =
            createSnapshot("os.kei").copy(
                trackId = "hosizoraru/KeiOS|os.kei",
                owner = "hosizoraru",
                repo = "KeiOS",
                appLabel = "KeiOS",
            )

        assertEquals(
            GitHubActionsUpdateNotificationHelper.notificationId(first),
            GitHubActionsUpdateNotificationHelper.notificationId(sameItemNewRun),
        )
        assertNotEquals(
            GitHubActionsUpdateNotificationHelper.notificationId(first),
            GitHubActionsUpdateNotificationHelper.notificationId(second),
        )
    }

    @Test
    fun `notification content keeps app label and run label compact`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = createSnapshot("me.him188.ani")
        val notification = invokeFrameworkNotification(context, snapshot)

        assertEquals(
            "Animeko #44 · CI / Benchmark APK",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    private fun createSnapshot(packageName: String): GitHubActionsRecommendedRunSnapshot =
        GitHubActionsRecommendedRunSnapshot(
            trackId = "open-ani/animeko|$packageName",
            owner = "open-ani",
            repo = "animeko",
            appLabel = "Animeko",
            workflowId = 42L,
            workflowName = "CI / Benchmark APK",
            workflowPath = ".github/workflows/android.yml",
            runId = 4444L,
            runNumber = 44L,
            runAttempt = 1,
            runDisplayName = "Build #44",
            headBranch = "main",
            headSha = "abcdef0",
            event = "workflow_dispatch",
            status = "completed",
            conclusion = "success",
            htmlUrl = "https://github.com/open-ani/animeko/actions/runs/4444",
            artifactCount = 2,
            androidArtifactCount = 1,
            createdAtMillis = 1778000000000L,
            updatedAtMillis = 1778000100000L,
            checkedAtMillis = 1778000200000L,
        )

    private fun invokeMiIslandNotification(
        context: Application,
        snapshot: GitHubActionsRecommendedRunSnapshot,
    ): Notification {
        val method =
            GitHubActionsUpdateNotificationHelper::class.java
                .getDeclaredMethod(
                    "buildMiIslandNotification",
                    android.content.Context::class.java,
                    GitHubActionsRecommendedRunSnapshot::class.java,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                ).apply {
                    isAccessible = true
                }
        val notificationId = GitHubActionsUpdateNotificationHelper.notificationId(snapshot)
        return method.invoke(
            GitHubActionsUpdateNotificationHelper,
            context,
            snapshot,
            true,
            notificationId,
        ) as Notification
    }

    private fun invokeFrameworkNotification(
        context: Application,
        snapshot: GitHubActionsRecommendedRunSnapshot,
    ): Notification {
        val method =
            GitHubActionsUpdateNotificationHelper::class.java
                .getDeclaredMethod(
                    "buildFrameworkNotification",
                    android.content.Context::class.java,
                    GitHubActionsRecommendedRunSnapshot::class.java,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                ).apply {
                    isAccessible = true
                }
        val notificationId = GitHubActionsUpdateNotificationHelper.notificationId(snapshot)
        return method.invoke(
            GitHubActionsUpdateNotificationHelper,
            context,
            snapshot,
            true,
            notificationId,
        ) as Notification
    }

    private fun Notification.focusAction(key: String): Notification.Action {
        val actions = extras.getBundle("miui.focus.actions")
        assertNotNull(actions, "Focus actions bundle should be present")
        return actions.getActionCompat(key)
    }

    private fun Notification.focusPicture(key: String): Icon {
        val pictures = extras.getBundle("miui.focus.pics")
        assertNotNull(pictures, "Focus pictures bundle should be present")
        return pictures.getIconCompat(key)
    }

    @Suppress("DEPRECATION")
    private fun Bundle.getActionCompat(key: String): Notification.Action =
        getParcelable<Notification.Action>(key)
            ?: error("Missing focus action: $key")

    @Suppress("DEPRECATION")
    private fun Bundle.getIconCompat(key: String): Icon =
        getParcelable<Icon>(key)
            ?: error("Missing focus picture: $key")
}

class GitHubActionsUpdateNotificationHelperTestApp : Application()
