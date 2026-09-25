package os.kei.ui.page.main.ba

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.rememberTabbedPageChromeScrollState
import os.kei.ui.page.main.widget.chrome.tabbedPageContentNestedScrollConnection
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * The calendar/pool page's bottom bar hides while the list scrolls down and comes back on the way up,
 * like every other tabbed page's.
 *
 * It was once the one bar in the app pinned open: no scroll connection of its own, so it sat over the
 * list the whole way down. This drives the real calendar list, inside the real scaffold, through the
 * connection the page composes (bottom chrome first, then the top bar's scroll behaviour) and watches
 * the visibility the page hands to its bar.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class BaCalendarPoolBottomChromeScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scrollingTheCalendarDownHidesTheBarAndScrollingBackShowsIt() {
        var bottomBarVisible by mutableStateOf(true)
        lateinit var calendarListState: LazyListState

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val listState = rememberLazyListState()
                calendarListState = listState
                val scrollBehavior = MiuixScrollBehavior()
                val bottomChromeScrollState =
                    rememberTabbedPageChromeScrollState(
                        visible = bottomBarVisible,
                        activeListStateProvider = { listState },
                        onVisibleChange = { visible -> bottomBarVisible = visible },
                    )
                val connection =
                    remember(listState, bottomChromeScrollState, scrollBehavior.nestedScrollConnection) {
                        tabbedPageContentNestedScrollConnection(
                            listState = listState,
                            chrome = bottomChromeScrollState.chromeNestedScrollConnection,
                            delegate = scrollBehavior.nestedScrollConnection,
                        )
                    }
                // The real scaffold, so the top bar's scroll behaviour has a measured bar to collapse
                // rather than an unbounded one that would take the whole drag.
                AppPageScaffold(title = "Calendar", scrollBehavior = scrollBehavior) { innerPadding ->
                    BaActivityCalendarListContent(
                        innerPadding = innerPadding,
                        listState = listState,
                        nestedScrollConnection = connection,
                        backdrop = rememberLayerBackdrop(),
                        serverOptions = listOf("CN", "Global", "JP"),
                        serverIndex = 0,
                        showServerPopup = false,
                        serverPopupAnchorBounds = null,
                        showEndedActivities = true,
                        showCalendarPoolImages = false,
                        entries = runningEntries(),
                        loading = false,
                        refreshing = false,
                        error = null,
                        syncText = "synced",
                        syncTextColor = Color.Blue,
                        onServerPopupChange = {},
                        onServerPopupAnchorBoundsChange = {},
                        onServerSelected = {},
                        onOpenCalendarLink = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue(bottomBarVisible, "the bar starts shown") }

        // Both drags start and end inside the list, below the top bar: a drag that lands on the bar is
        // the bar's, not the list's.
        composeRule.onRoot().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.3f) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(calendarListState.firstVisibleItemIndex > 0, "the drag must have scrolled the list")
            assertFalse(bottomBarVisible, "scrolling down the list must hide the bar")
        }

        // Let the first swipe's fling settle, so the second one scrolls rather than only catching it.
        composeRule.mainClock.advanceTimeBy(FLING_SETTLE_MS)
        val scrolledTo = calendarListState.firstVisibleItemIndex
        composeRule.onRoot().performTouchInput { swipeDown(startY = height * 0.3f, endY = height * 0.8f) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(calendarListState.firstVisibleItemIndex < scrolledTo, "the drag must have scrolled back")
            assertTrue(bottomBarVisible, "scrolling back up must bring the bar back")
        }
    }

    private fun runningEntries(): List<BaCalendarEntry> {
        val now = System.currentTimeMillis()
        return List(30) { index ->
            BaCalendarEntry(
                id = index,
                title = "Event $index",
                kindId = 1,
                kindName = "Event",
                beginAtMs = now - HOUR_MS,
                endAtMs = now + (index + 1) * DAY_MS,
                linkUrl = "",
                imageUrl = "",
                isRunning = true,
            )
        }
    }
}

private const val FLING_SETTLE_MS = 3_000L
private const val HOUR_MS = 60L * 60L * 1000L
private const val DAY_MS = 24L * HOUR_MS
