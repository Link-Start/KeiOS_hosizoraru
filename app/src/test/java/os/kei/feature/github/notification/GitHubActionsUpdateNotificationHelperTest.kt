package os.kei.feature.github.notification

import android.app.Application
import android.app.Notification
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import os.kei.MainActivity
import os.kei.R
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.notification.NotificationActionReceiver
import org.junit.After
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    application = Application::class,
    sdk = [35],
)
class GitHubActionsUpdateNotificationHelperTest {
    @After
    fun tearDown() {
        GitHubNotificationPreferences.overrideSuperIslandFirstFloatForTests(null)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `mi island summary keeps short run label and routes to the tracked item`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        GitHubNotificationPreferences.overrideSuperIslandFirstFloatForTests(true)
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
        val contentIntent = Shadows.shadowOf(notification.contentIntent).savedIntent
        val focusOpenIntent = Shadows.shadowOf(openAction.actionIntent).savedIntent
        val markReadIntent = Shadows.shadowOf(markReadAction.actionIntent).savedIntent
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()
        val focusJson = JSONObject(focusParam).getJSONObject("param_v2")
        val summaryText =
            focusJson
                .getJSONObject("param_island")
                .getJSONObject("bigIslandArea")
                .getJSONObject("imageTextInfoRight")
                .getJSONObject("textInfo")

        // The tracked app's own icon, not the KeiOS one.
        assertNotNull(Shadows.shadowOf(notification.focusPicture("mi_focus_display")).bitmap)
        assertEquals(context.getString(R.string.common_open), openAction.title.toString())
        assertEquals(context.getString(R.string.common_mark_read), markReadAction.title.toString())
        assertEquals("#44", summaryText.getString("title"))
        assertFalse(summaryText.has("content"))
        assertTrue(focusParam.contains("\"specialTitle\":\"#44\""))
        assertEquals("Animeko", focusJson.getJSONObject("baseInfo").getString("content"))
        assertFalse(focusParam.contains("Animeko · #44"))
        assertEquals(snapshot.trackId, focusJson.getString("orderId"))
        assertFalse(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(
            notification.extras.getBoolean(
                NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING,
                false,
            ),
        )
        assertTrue(focusParam.contains("\"islandFirstFloat\":true"))
        assertTrue(focusParam.contains("\"enableFloat\":false"))
        listOf(contentIntent, focusOpenIntent).forEach { intent ->
            assertEquals(
                MainActivity.TARGET_BOTTOM_PAGE_GITHUB,
                intent.getStringExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE),
            )
            assertEquals(snapshot.trackId, intent.getStringExtra(MainActivity.EXTRA_GITHUB_ACTIONS_TRACK_ID))
        }
        assertEquals(
            notificationId,
            markReadIntent.getIntExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, -1),
        )
    }

    @Test
    fun `mi island actions update can disable first float preference`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        GitHubNotificationPreferences.overrideSuperIslandFirstFloatForTests(false)
        val snapshot = createSnapshot(packageName = "me.him188.ani")
        val notification = invokeMiIslandNotification(context, snapshot)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertTrue(focusParam.contains("\"islandFirstFloat\":false"))
        assertTrue(focusParam.contains("\"enableFloat\":false"))
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
    fun `notification content keeps the full app label without repeating run number`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = createSnapshot("me.him188.ani")
        val notification = invokeFrameworkNotification(context, snapshot)

        assertEquals(
            "Animeko",
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
