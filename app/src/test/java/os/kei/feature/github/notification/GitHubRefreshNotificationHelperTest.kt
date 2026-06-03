package os.kei.feature.github.notification

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import os.kei.feature.github.domain.GitHubRefreshScope
import os.kei.feature.github.domain.GitHubRefreshSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertTrue(focusParam.contains("\"business\":\"keios\""))
        assertTrue(focusParam.contains("\"notifyId\":\"38990\""))
        assertTrue(focusParam.contains("\"orderId\":\"github_refresh\""))
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

        assertTrue(focusParam.contains("imageTextInfoLeft"))
        assertTrue(focusParam.contains("progressTextInfo"))
        assertTrue(focusParam.contains("combinePicInfo"))
        assertTrue(focusParam.contains("baseInfo"))
        assertTrue(focusParam.contains("multiProgressInfo"))
        assertTrue(focusParam.contains("picInfo"))
        assertFalse(focusParam.contains("textButton"))
        assertTrue(focusParam.contains("\"title\":\"50%\""))
        assertTrue(focusParam.contains("\"content\":\"2/4\""))
        assertTrue(
            focusParam.contains(
                context.getString(os.kei.R.string.github_refresh_content_compact, 2, 4, 1, 2)
            )
        )
        assertTrue(focusParam.contains(context.getString(os.kei.R.string.github_refresh_scope_all_compact, 4)))
        assertFalse(focusParam.contains("预发可更新"))
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
        assertTrue(focusParam.contains("imageTextInfoLeft"))
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("picInfo"))
        assertTrue(focusParam.contains("baseInfo"))
        assertTrue(focusParam.contains("textButton"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertTrue(focusParam.contains("\"content\":\"4/4\""))
        assertTrue(
            focusParam.contains(
                context.getString(os.kei.R.string.github_refresh_content_compact, 4, 4, 1, 2)
            )
        )
        assertTrue(focusParam.contains(context.getString(os.kei.R.string.github_refresh_scope_all_compact, 4)))
        assertFalse(focusParam.contains("稳定可更新"))
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
        assertTrue(
            focusParam.contains(
                context.getString(os.kei.R.string.github_refresh_content_compact_with_failed, 4, 4, 1, 2, 1)
            )
        )
        assertTrue(focusParam.contains("\"showHighlightColor\":true"))
    }

    @Test
    fun `mi island due refresh summary uses tracked denominator`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = createRefreshState(
            running = true,
            current = 1,
            total = 1,
            preReleaseUpdateCount = 0,
            updatableCount = 0,
            displayProgressPercent = 50,
            scope = GitHubRefreshScope.DueTracked,
            source = GitHubRefreshSource.BackgroundTick,
            totalTrackedCount = 75,
        )
        val notification = invokeMiIslandNotification(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertTrue(focusParam.contains(context.getString(os.kei.R.string.github_refresh_scope_due_compact, 1, 75)))
        assertTrue(
            focusParam.contains(
                context.getString(os.kei.R.string.github_refresh_content_partial_compact, 1, 0, 0),
            ),
        )
        assertTrue(focusParam.contains("\"content\":\"1/75\""))
    }

    @Test
    fun `legacy due refresh summary uses scoped target text`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = createRefreshState(
            running = true,
            current = 1,
            total = 1,
            preReleaseUpdateCount = 0,
            updatableCount = 0,
            displayProgressPercent = 50,
            scope = GitHubRefreshScope.DueTracked,
            source = GitHubRefreshSource.BackgroundTick,
            totalTrackedCount = 75,
        )
        val notification = invokeLegacyLiveUpdateNotification(context, state)

        assertEquals(
            context.getString(
                os.kei.R.string.github_refresh_content_scoped,
                context.getString(os.kei.R.string.github_refresh_scope_due, 1, 75),
                context.getString(os.kei.R.string.github_refresh_content_partial, 1, 0, 0),
            ),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals(
            context.getString(os.kei.R.string.common_progress_with_value, "1/75"),
            notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString(),
        )
    }

    @Test
    fun `stale session progress cannot overwrite active notification session`() {
        invokeResetNotificationRuntime()

        val firstProgress =
            invokeResolveDisplayProgressPercent(
                sessionId = 10L,
                current = 0,
                total = 1,
                running = true,
                cancelled = false,
            )
        val secondProgress =
            invokeResolveDisplayProgressPercent(
                sessionId = 11L,
                current = 0,
                total = 75,
                running = true,
                cancelled = false,
            )
        val staleProgress =
            invokeResolveDisplayProgressPercent(
                sessionId = 10L,
                current = 1,
                total = 1,
                running = false,
                cancelled = false,
            )

        assertNotNull(firstProgress)
        assertNotNull(secondProgress)
        assertEquals(null, staleProgress)
    }

    private fun createRefreshState(
        running: Boolean,
        current: Int = 4,
        total: Int = 4,
        preReleaseUpdateCount: Int = 1,
        updatableCount: Int = 2,
        failedCount: Int = 0,
        cancelled: Boolean = false,
        displayProgressPercent: Int = 100,
        sessionId: Long = 1L,
        scope: GitHubRefreshScope = GitHubRefreshScope.AllTracked,
        source: GitHubRefreshSource = GitHubRefreshSource.Page,
        totalTrackedCount: Int = total
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
            Int::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            GitHubRefreshScope::class.java,
            GitHubRefreshSource::class.java,
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
            displayProgressPercent,
            sessionId,
            scope,
            source,
            totalTrackedCount
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

    private fun invokeLegacyLiveUpdateNotification(
        context: Context,
        state: Any
    ): Notification {
        val method = GitHubRefreshNotificationHelper::class.java.getDeclaredMethod(
            "buildLegacyLiveUpdateNotification",
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

    private fun invokeResolveDisplayProgressPercent(
        sessionId: Long,
        current: Int,
        total: Int,
        running: Boolean,
        cancelled: Boolean,
    ): Int? {
        val method = GitHubRefreshNotificationHelper::class.java.getDeclaredMethod(
            "resolveDisplayProgressPercent",
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        ).apply {
            isAccessible = true
        }
        return method.invoke(
            GitHubRefreshNotificationHelper,
            sessionId,
            current,
            total,
            running,
            cancelled,
        ) as Int?
    }

    private fun invokeResetNotificationRuntime() {
        val method = GitHubRefreshNotificationHelper::class.java.getDeclaredMethod(
            "resetNotificationRuntime",
        ).apply {
            isAccessible = true
        }
        method.invoke(GitHubRefreshNotificationHelper)
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
