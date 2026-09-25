package os.kei.ui.page.main.ba

import os.kei.MainActivity
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import os.kei.R
import os.kei.ui.page.main.ba.support.baServerLabelRes
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class BaCalendarPoolNotificationDispatcherTest {
    @Test
    fun `calendar pool grouped notification id keeps each server inside base bucket`() {
        val notifyAtMs = 1_777_392_000_000L
        val ids =
            (0..2).map { serverIndex ->
                baCalendarPoolGroupedNotificationId(
                    baseId = BASE_ID,
                    serverIndex = serverIndex,
                    notifyAtMs = notifyAtMs,
                )
            }

        assertEquals(ids.distinct(), ids)
        ids.forEach { id ->
            assertTrue(id in BASE_ID until BASE_ID + 900_000)
        }
    }

    @Test
    fun `calendar pool focus order id is stable for notification id`() {
        assertEquals(
            "ba-calendar-pool-391123456",
            baCalendarPoolMiFocusOrderId(391_123_456),
        )
    }

    @Test
    fun `notification target opens its page for the requested server`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val cases =
            listOf(
                Triple(BaCalendarPoolNotificationDestination.Calendar, 2, MainActivity.TARGET_ROUTE_BA_ACTIVITY_CALENDAR),
                Triple(BaCalendarPoolNotificationDestination.Pool, 1, MainActivity.TARGET_ROUTE_BA_POOL),
            )
        for ((destination, serverIndex, route) in cases) {
            val intent =
                baCalendarPoolOpenIntent(
                    context = context,
                    destination = destination,
                    serverIndex = serverIndex,
                )
            val label = "$destination"

            assertEquals(MainActivity::class.java.name, intent.component?.className, label)
            assertEquals(route, intent.getStringExtra(MainActivity.EXTRA_TARGET_ROUTE), label)
            assertEquals(MainActivity.TARGET_BOTTOM_PAGE_BA, intent.getStringExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE), label)
            assertEquals(serverIndex, intent.baCalendarPoolServerIndexOrNull(), label)
            assertFlag(intent, Intent.FLAG_ACTIVITY_NEW_TASK, label)
            assertFlag(intent, Intent.FLAG_ACTIVITY_SINGLE_TOP, label)
            assertFlag(intent, Intent.FLAG_ACTIVITY_CLEAR_TOP, label)
        }
    }

    @Test
    fun `notification target selection keeps token for repeated server routes`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val intent =
            baCalendarPoolOpenIntent(
                context = context,
                destination = BaCalendarPoolNotificationDestination.Calendar,
                serverIndex = 2,
            )

        val first = intent.toBaCalendarPoolInitialServerSelection(token = 1L)
        val second = intent.toBaCalendarPoolInitialServerSelection(token = 2L)

        assertEquals(2, first.serverIndex)
        assertEquals(1L, first.token)
        assertEquals(2, second.serverIndex)
        assertEquals(2L, second.token)
        assertNotEquals(first, second)
    }

    @Test
    fun `notification target selection preserves null server routes`() {
        val selection = Intent().toBaCalendarPoolInitialServerSelection(token = 3L)

        assertNull(selection.serverIndex)
        assertEquals(3L, selection.token)
    }

    @Test
    fun `mixed data changed copy uses compact calendar summary`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val serverLabel = context.getString(baServerLabelRes(2))

        val copy =
            BaCalendarPoolNotificationDispatcher.buildDataChangedCopy(
                context = context,
                serverIndex = 2,
                calendarChangeCount = 1,
                poolChangeCount = 1,
                detail = "测试活动 / 测试卡池",
            )

        assertEquals(BaCalendarPoolNotificationDestination.Calendar, copy.destination)
        assertEquals(
            context.getString(R.string.ba_calendar_pool_notify_change_title_with_server, serverLabel),
            copy.title,
        )
        assertEquals(
            context.getString(R.string.ba_calendar_pool_notify_change_content, 1, 1),
            copy.content,
        )
        assertEquals(context.getString(R.string.ba_calendar_pool_notify_short_change), copy.shortText)
        assertEquals(context.getString(R.string.ba_calendar_pool_notify_short_both), copy.onlineText)
    }

    @Test
    fun `pool data changed copy opens pool and trims single detail`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val serverLabel = context.getString(baServerLabelRes(2))

        val copy =
            BaCalendarPoolNotificationDispatcher.buildDataChangedCopy(
                context = context,
                serverIndex = 2,
                calendarChangeCount = 0,
                poolChangeCount = 1,
                detail = "ABCDEFGHIJKLMNO",
            )

        assertEquals(BaCalendarPoolNotificationDestination.Pool, copy.destination)
        assertEquals(
            context.getString(R.string.ba_pool_notify_change_title_with_server, serverLabel),
            copy.title,
        )
        assertEquals(
            context.getString(
                R.string.ba_calendar_pool_notify_change_content_with_detail,
                context.getString(R.string.ba_pool_notify_change_content, 1),
                "ABCDEFGHIJKLMN…",
            ),
            copy.content,
        )
        assertEquals(context.getString(R.string.ba_calendar_pool_notify_short_change), copy.shortText)
        assertEquals(context.getString(R.string.ba_calendar_pool_notify_short_pool), copy.onlineText)
    }

    private fun assertFlag(intent: Intent, flag: Int, label: String) {
        assertTrue(intent.flags and flag != 0, "$label: missing flag $flag")
    }

    private companion object {
        private const val BASE_ID = 389_100_000
    }
}
