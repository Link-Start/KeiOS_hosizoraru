package os.kei.feature.github.notification

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import os.kei.feature.github.domain.GitHubRefreshScope
import os.kei.feature.github.domain.GitHubRefreshSource
import os.kei.core.prefs.SuperIslandFloatBehavior
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    application = Application::class,
    sdk = [35]
)
class GitHubRefreshNotificationHelperTest {
    @After
    fun tearDown() {
        GitHubNotificationPreferences.overrideSuperIslandFirstFloatForTests(null)
    }

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
        val focusJson = focusParam.focusParamV2()

        assertTrue(focusParam.contains("imageTextInfoLeft"))
        assertTrue(focusParam.contains("progressTextInfo"))
        assertTrue(focusParam.contains("combinePicInfo"))
        assertTrue(focusParam.contains("baseInfo"))
        assertTrue(focusParam.contains("progressInfo"))
        assertFalse(focusParam.contains("multiProgressInfo"))
        assertTrue(focusParam.contains("picInfo"))
        assertFalse(focusParam.contains("textButton"))
        assertTrue(focusParam.contains("\"title\":\"50%\""))
        assertEquals(
            "2/4",
            focusJson.focusBigIslandArea()
                .getJSONObject("progressTextInfo")
                .getJSONObject("textInfo")
                .getString("content"),
        )
        assertEquals(
            context.getString(
                os.kei.R.string.github_refresh_mi_content,
                context.getString(os.kei.R.string.github_refresh_scope_all_compact, 4),
                "2/4",
                3,
            ),
            focusJson.getJSONObject("baseInfo").getString("content"),
        )
        assertTrue(focusParam.contains(context.getString(os.kei.R.string.github_refresh_scope_all_compact, 4)))
        assertFalse(focusParam.contains("预发可更新"))
        assertTrue(focusParam.contains("\"progress\":50"))
    }

    @Test
    fun `mi island running notification does not request platform live update`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = createRefreshState(
            running = true,
            current = 2,
            total = 4,
            displayProgressPercent = 50
        )
        val notification = invokeMiIslandNotification(context, state)

        assertEquals(Notification.CATEGORY_STATUS, notification.category)
        assertEquals(NotificationCompat.PRIORITY_MAX, notification.priority)
        assertFalse(
            notification.extras.getBoolean(
                NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING,
                false
            )
        )
        assertEquals(0, notification.extras.getInt(NotificationCompat.EXTRA_PROGRESS, 0))
        assertEquals(0, notification.extras.getInt(NotificationCompat.EXTRA_PROGRESS_MAX, 0))
        assertFalse(notification.extras.getBoolean(NotificationCompat.EXTRA_PROGRESS_INDETERMINATE, false))
        assertTrue(notification.extras.getString("miui.focus.param").orEmpty().contains("progressTextInfo"))
    }

    @Test
    fun `mi island refresh float flags follow first float preference and float behavior`() {
        data class Case(
            val name: String,
            val applyPreference: () -> Unit,
            val running: Boolean,
            val expectedIslandFirstFloat: Boolean,
            val expectedEnableFloat: Boolean,
            val expectUpdatable: Boolean = false,
            val expectNotOngoing: Boolean = false,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        listOf(
            Case(
                name = "running summary allows first float preference without repeated force float",
                applyPreference = { GitHubNotificationPreferences.overrideSuperIslandFirstFloatForTests(true) },
                running = true,
                expectedIslandFirstFloat = true,
                expectedEnableFloat = false,
                expectUpdatable = true,
            ),
            Case(
                name = "running summary can disable first float preference",
                applyPreference = { GitHubNotificationPreferences.overrideSuperIslandFirstFloatForTests(false) },
                running = true,
                expectedIslandFirstFloat = false,
                expectedEnableFloat = false,
            ),
            Case(
                name = "completed summary floats when start and finish behavior is enabled",
                applyPreference = {
                    GitHubNotificationPreferences.overrideSuperIslandFloatBehaviorForTests(
                        SuperIslandFloatBehavior.StartAndFinish
                    )
                },
                running = false,
                expectedIslandFirstFloat = true,
                expectedEnableFloat = true,
                expectNotOngoing = true,
            ),
            Case(
                name = "completed summary stays quiet for start only behavior",
                applyPreference = {
                    GitHubNotificationPreferences.overrideSuperIslandFloatBehaviorForTests(
                        SuperIslandFloatBehavior.StartOnly
                    )
                },
                running = false,
                expectedIslandFirstFloat = true,
                expectedEnableFloat = false,
            ),
            Case(
                name = "completed summary stays quiet for summary only behavior",
                applyPreference = {
                    GitHubNotificationPreferences.overrideSuperIslandFloatBehaviorForTests(
                        SuperIslandFloatBehavior.SummaryOnly
                    )
                },
                running = false,
                expectedIslandFirstFloat = false,
                expectedEnableFloat = false,
            ),
        ).forEach { case ->
            case.applyPreference()
            val state = if (case.running) {
                createRefreshState(
                    running = true,
                    current = 2,
                    total = 4,
                    displayProgressPercent = 50
                )
            } else {
                createRefreshState(running = false)
            }
            val notification = invokeMiIslandNotification(context, state)
            val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

            assertTrue(
                focusParam.contains("\"islandFirstFloat\":${case.expectedIslandFirstFloat}"),
                "${case.name}: $focusParam"
            )
            assertTrue(
                focusParam.contains("\"enableFloat\":${case.expectedEnableFloat}"),
                "${case.name}: $focusParam"
            )
            if (case.expectUpdatable) {
                assertTrue(focusParam.contains("\"updatable\":true"), "${case.name}: $focusParam")
            }
            if (case.expectNotOngoing) {
                assertFalse(notification.flags and Notification.FLAG_ONGOING_EVENT != 0, case.name)
                assertFalse(
                    notification.extras.getBoolean(
                        NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING,
                        false
                    ),
                    case.name
                )
            }
        }
    }

    @Test
    fun `mi island completed summary uses compact state text`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = createRefreshState(running = false)
        val notification = invokeMiIslandNotification(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()
        val focusJson = focusParam.focusParamV2()

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
        assertEquals(
            "4/4",
            focusJson.focusBigIslandArea()
                .getJSONObject("imageTextInfoRight")
                .getJSONObject("textInfo")
                .getString("content"),
        )
        assertEquals(
            context.getString(
                os.kei.R.string.github_refresh_mi_content,
                context.getString(os.kei.R.string.github_refresh_scope_all_compact, 4),
                "4/4",
                3,
            ),
            focusJson.getJSONObject("baseInfo").getString("content"),
        )
        assertTrue(focusParam.contains(context.getString(os.kei.R.string.github_refresh_scope_all_compact, 4)))
        assertTrue(focusParam.contains(context.getString(os.kei.R.string.github_refresh_mi_label_updates, 3)))
        assertTrue(focusParam.contains("\"colorSpecialBg\":\"#22C55E\""))
        assertTrue(focusParam.contains("\"colorContent\":\"#64748B\""))
        assertTrue(focusParam.contains("\"highlightColor\":\"#22C55E\""))
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
        val focusJson = focusParam.focusParamV2()

        assertTrue(
            focusParam.contains("\"title\":\"${context.getString(os.kei.R.string.github_refresh_island_cancelled)}\"")
        )
        assertEquals(
            "2/4",
            focusJson.focusBigIslandArea()
                .getJSONObject("imageTextInfoRight")
                .getJSONObject("textInfo")
                .getString("content"),
        )
        assertTrue(focusParam.contains("\"colorSpecialBg\":\"#64748B\""))
        assertTrue(focusParam.contains("\"highlightColor\":\"#64748B\""))
        assertFalse(focusParam.contains("\"showHighlightColor\":true"))
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
        val focusJson = focusParam.focusParamV2()

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
        assertEquals(
            "4/4",
            focusJson.focusBigIslandArea()
                .getJSONObject("imageTextInfoRight")
                .getJSONObject("textInfo")
                .getString("content"),
        )
        assertEquals(
            context.getString(
                os.kei.R.string.github_refresh_mi_content_failed,
                context.getString(os.kei.R.string.github_refresh_scope_all_compact, 4),
                "4/4",
                3,
                1,
            ),
            focusJson.getJSONObject("baseInfo").getString("content"),
        )
        assertTrue(focusParam.contains("\"colorSpecialBg\":\"#E25B6A\""))
        assertTrue(focusParam.contains("\"showHighlightColor\":true"))
    }

    @Test
    fun `mi island due refresh summary uses target denominator without duplicate total context`() {
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
        val focusJson = focusParam.focusParamV2()

        assertTrue(focusParam.contains(context.getString(os.kei.R.string.github_refresh_scope_due_compact, 1)))
        assertEquals(
            context.getString(
                os.kei.R.string.github_refresh_mi_content_base,
                context.getString(os.kei.R.string.github_refresh_scope_due_compact, 1),
                "1/1",
            ),
            focusJson.getJSONObject("baseInfo").getString("content"),
        )
        assertEquals(
            "1/1",
            focusJson.focusBigIslandArea()
                .getJSONObject("progressTextInfo")
                .getJSONObject("textInfo")
                .getString("content"),
        )
        assertTrue(focusParam.contains(context.getString(os.kei.R.string.github_refresh_mi_label_running)))
        assertTrue(focusParam.contains("\"colorSpecialBg\":\"#3B82F6\""))
        assertFalse(focusParam.contains(context.getString(os.kei.R.string.github_refresh_total_context, 75)))
    }

    @Test
    fun `legacy due refresh summary uses target progress text`() {
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
                context.getString(os.kei.R.string.github_refresh_scope_due, 1),
                context.getString(os.kei.R.string.github_refresh_content, 1, 1, 0, 0),
            ),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals(
            context.getString(os.kei.R.string.common_progress_with_value, "1/1"),
            notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString(),
        )
    }

    @Test
    fun `mi island failed terminal keeps partial progress and failure title`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val state = createRefreshState(
            running = false,
            current = 2,
            total = 5,
            failedCount = 1,
            displayProgressPercent = 40,
            scope = GitHubRefreshScope.DueTracked,
            source = GitHubRefreshSource.BackgroundTick,
            totalTrackedCount = 75,
        )
        val notification = invokeMiIslandNotification(context, state)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()
        val focusJson = focusParam.focusParamV2()

        assertEquals(
            context.getString(os.kei.R.string.github_refresh_mi_title_failed),
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertEquals(
            "2/5",
            focusJson.focusBigIslandArea()
                .getJSONObject("imageTextInfoRight")
                .getJSONObject("textInfo")
                .getString("content"),
        )
        assertEquals(
            context.getString(
                os.kei.R.string.github_refresh_mi_content_failed,
                context.getString(os.kei.R.string.github_refresh_scope_due_compact, 5),
                "2/5",
                3,
                1,
            ),
            focusJson.getJSONObject("baseInfo").getString("content"),
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

    @Test
    fun `stale cleanup cannot cancel the active notification session`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        invokeResetNotificationRuntime()
        assertNotNull(
            invokeResolveDisplayProgressPercent(
                sessionId = 11L,
                current = 1,
                total = 75,
                running = true,
                cancelled = false,
            ),
        )

        val staleCancelled = GitHubRefreshNotificationHelper.cancel(context, sessionId = 10L)
        val activeProgress =
            invokeResolveDisplayProgressPercent(
                sessionId = 11L,
                current = 2,
                total = 75,
                running = true,
                cancelled = false,
            )

        assertFalse(staleCancelled)
        assertNotNull(activeProgress)
        assertTrue(GitHubRefreshNotificationHelper.cancel(context, sessionId = 11L))
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

    private fun String.focusParamV2(): JSONObject =
        JSONObject(this).getJSONObject("param_v2")

    private fun JSONObject.focusBigIslandArea(): JSONObject =
        getJSONObject("param_island").getJSONObject("bigIslandArea")

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
