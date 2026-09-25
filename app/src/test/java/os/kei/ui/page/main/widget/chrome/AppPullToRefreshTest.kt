package os.kei.ui.page.main.widget.chrome

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.ui.testing.repoSource

class AppPullToRefreshTest {
    @Test
    fun sameTravelSurvivesDifferentWindowHeights() {
        // A 1080p phone, the reference device, and a tablet: one dp figure, three window heights.
        val windows = listOf(2340f, REFERENCE_WINDOW_PX, 2560f)
        val densities = listOf(2.75f, 3.25f, 2.0f)

        windows.zip(densities).forEach { (windowHeightPx, density) ->
            val travelPx = 128f * density
            val threshold = appPullToRefreshThreshold(travelPx = travelPx, windowHeightPx = windowHeightPx)
            val armedAtDp = miuixTravelPxFor(threshold, windowHeightPx) / density

            assertEquals(
                128.0,
                armedAtDp.toDouble(),
                0.5,
                "A ${windowHeightPx.toInt()}px window at ${density}x must still arm at 128dp",
            )
        }
    }

    @Test
    fun theReferenceDeviceLandsWellPastTheMiuixDefault() {
        val threshold = appPullToRefreshThreshold(travelPx = 128f * 3.25f, windowHeightPx = REFERENCE_WINDOW_PX)
        val stockTravelPx = miuixTravelPxFor(MIUIX_DEFAULT, REFERENCE_WINDOW_PX)
        val armedTravelPx = miuixTravelPxFor(threshold, REFERENCE_WINDOW_PX)

        // Held on 5eea1f50 (1220x2656) and stepped down 40px at a time: the stock threshold flipped
        // to "release to refresh" between 240px and 280px of finger travel, touch slop included.
        // Short enough that an ordinary downward flick at the top of a list reaches it.
        val touchSlopPx = 8f * 3.25f
        assertTrue(
            stockTravelPx + touchSlopPx in 240f..280f,
            "The model must land inside the measured stock window, was ${stockTravelPx + touchSlopPx}px",
        )
        assertTrue(
            armedTravelPx > stockTravelPx * 1.5f,
            "128dp must be a clearly deeper pull than the Miuix default, was ${armedTravelPx / stockTravelPx}x",
        )
    }

    @Test
    fun aTallWindowNeverMakesThePullEasierThanStock() {
        // 128dp on a low-density 4K window would invert to a fraction below the library default.
        val threshold = appPullToRefreshThreshold(travelPx = 128f * 1.5f, windowHeightPx = 4000f)

        assertEquals(MIUIX_DEFAULT, threshold, 1e-4f, "The library default is the floor")
    }

    @Test
    fun aShortWindowCapsTravelAtAQuarterOfItsHeight() {
        val shortWindow = 900f
        val threshold = appPullToRefreshThreshold(travelPx = 800f, windowHeightPx = shortWindow)

        assertEquals(
            (shortWindow / 4f).toDouble(),
            miuixTravelPxFor(threshold, shortWindow).toDouble(),
            1.0,
            "A pull deeper than a quarter of the window stops being reachable in one sweep",
        )
    }

    @Test
    fun degenerateMeasurementsFallBackToTheLibraryDefault() {
        assertEquals(MIUIX_DEFAULT, appPullToRefreshThreshold(travelPx = 416f, windowHeightPx = 0f))
        assertEquals(MIUIX_DEFAULT, appPullToRefreshThreshold(travelPx = 0f, windowHeightPx = 2656f))
    }

    @Test
    fun everyPullToRefreshCallSiteUsesTheSharedTriggerDistance() {
        PULL_TO_REFRESH_SOURCES.forEach { relativePath ->
            val source = repoSource(relativePath)
            val callSites = source.occurrencesOf("PullToRefresh(")
            val sharedStates = source.occurrencesOf("pullToRefreshState = rememberAppPullToRefreshState()")

            assertTrue(callSites > 0, "$relativePath must keep pull-to-refresh")
            assertEquals(
                callSites,
                sharedStates,
                "$relativePath must arm every pull-to-refresh at the shared trigger distance",
            )
        }
    }
}

/**
 * Finger travel that arms a refresh at [threshold], derived from Miuix's own model rather than the
 * inversion under test: bisect the damping curve for the drag offset the library compares against.
 */
private fun miuixTravelPxFor(
    threshold: Float,
    windowHeightPx: Float,
): Float {
    val fullDragRangePx = damped(1f) * windowHeightPx
    val triggerOffsetPx = threshold * fullDragRangePx
    var low = 0f
    var high = 1f
    repeat(200) {
        val mid = (low + high) / 2f
        if (damped(mid) * windowHeightPx < triggerOffsetPx) low = mid else high = mid
    }
    return ((low + high) / 2f) * windowHeightPx
}

/** `SpringMath.obtainDampingDistance` normalised to the 0..1 input, spelled out independently. */
private fun damped(x: Float): Float = x - x * x + x * x * x / 3f

private fun String.occurrencesOf(needle: String): Int = windowed(needle.length).count { it == needle }

private const val MIUIX_DEFAULT = 0.25f

/** 1220x2656 at 3.25x — the device the trigger distance was measured on. */
private const val REFERENCE_WINDOW_PX = 2656f

private val PULL_TO_REFRESH_SOURCES =
    listOf(
        "app/src/main/java/os/kei/ui/page/main/os/components/OsPageMainList.kt",
        "app/src/main/java/os/kei/ui/page/main/mcp/McpPageContent.kt",
        "app/src/main/java/os/kei/ui/page/main/github/section/GitHubMainContentSection.kt",
        "app/src/main/java/os/kei/ui/page/main/github/history/GitHubActionsNotificationHistoryPage.kt",
    )
