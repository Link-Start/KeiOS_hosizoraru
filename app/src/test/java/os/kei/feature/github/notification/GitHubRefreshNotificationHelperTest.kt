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
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    application = GitHubRefreshNotificationHelperTestApp::class,
    sdk = [35]
)
class GitHubRefreshNotificationHelperTest {
    @Test
    fun `mi island open action uses focus pending intent`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = createRefreshState(running = false)
        val notification = invokeMiIslandNotification(context, state)
        val notificationOpenPendingIntent = invokePendingIntentMethod("buildOpenPendingIntent", context)
        val focusOpenPendingIntent = invokePendingIntentMethod("buildFocusOpenPendingIntent", context)
        val focusOpenAction = notification.focusAction("github_action_open")
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertEquals(notificationOpenPendingIntent, notification.contentIntent)
        assertEquals(focusOpenPendingIntent, focusOpenAction.actionIntent)
        assertTrue(focusParam.contains("github_action_open"))
    }

    @Test
    fun `mi island running summary uses progress text and small combine progress`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = createRefreshState(
            running = true,
            current = 2,
            total = 4,
            displayProgressPercent = 50
        )
        val notification = invokeMiIslandNotification(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertTrue(focusParam.contains("progressTextInfo"))
        assertTrue(focusParam.contains("combinePicInfo"))
        assertTrue(focusParam.contains("\"title\":\"50%\""))
        assertTrue(focusParam.contains("\"content\":\"2/4\""))
        assertTrue(focusParam.contains("\"progress\":50"))
    }

    @Test
    fun `mi island completed summary uses compact state text`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = createRefreshState(running = false)
        val notification = invokeMiIslandNotification(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertTrue(
            focusParam.contains("\"title\":\"${context.getString(os.kei.R.string.github_refresh_island_completed)}\"")
        )
        assertTrue(focusParam.contains("\"content\":\"4/4\""))
        assertTrue(focusParam.contains("github_action_open"))
        assertTrue(focusParam.contains("github_action_read"))
    }

    @Test
    fun `mi island cancelled summary uses compact state text`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = createRefreshState(
            running = false,
            cancelled = true,
            current = 2,
            total = 4,
            displayProgressPercent = 50
        )
        val notification = invokeMiIslandNotification(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertTrue(
            focusParam.contains("\"title\":\"${context.getString(os.kei.R.string.github_refresh_island_cancelled)}\"")
        )
        assertTrue(focusParam.contains("\"content\":\"2/4\""))
    }

    @Test
    fun `mi island failed summary uses compact failure text`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = createRefreshState(
            running = false,
            failedCount = 1
        )
        val notification = invokeMiIslandNotification(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertTrue(
            focusParam.contains(
                "\"title\":\"${
                    context.getString(
                        os.kei.R.string.github_refresh_failed_short_with_count,
                        1
                    )
                }\""
            )
        )
        assertTrue(focusParam.contains("\"content\":\"4/4\""))
        assertTrue(focusParam.contains("\"showHighlightColor\":true"))
    }

    private fun createRefreshState(
        running: Boolean,
        current: Int = 4,
        total: Int = 4,
        preReleaseUpdateCount: Int = 1,
        updatableCount: Int = 2,
        failedCount: Int = 0,
        cancelled: Boolean = false,
        displayProgressPercent: Int = 100
    ): Any {
        val stateClass = refreshStateClass()
        return stateClass.getDeclaredConstructor(
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }.newInstance(
            current,
            total,
            preReleaseUpdateCount,
            updatableCount,
            failedCount,
            running,
            cancelled,
            displayProgressPercent
        )
    }

    private fun invokeMiIslandNotification(
        context: Context,
        state: Any
    ): Notification {
        val method = GitHubRefreshNotificationHelper::class.java.getDeclaredMethod(
            "buildMiIslandNotification",
            Context::class.java,
            refreshStateClass(),
            Boolean::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }
        return method.invoke(
            GitHubRefreshNotificationHelper,
            context,
            state,
            true
        ) as Notification
    }

    private fun invokePendingIntentMethod(
        methodName: String,
        context: Context
    ): PendingIntent {
        val method = GitHubRefreshNotificationHelper::class.java.getDeclaredMethod(
            methodName,
            Context::class.java
        ).apply {
            isAccessible = true
        }
        return method.invoke(GitHubRefreshNotificationHelper, context) as PendingIntent
    }

    private fun refreshStateClass(): Class<*> {
        return Class.forName(
            "os.kei.feature.github.notification.GitHubRefreshNotificationHelper\$RefreshState"
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

class GitHubRefreshNotificationHelperTestApp : Application()
