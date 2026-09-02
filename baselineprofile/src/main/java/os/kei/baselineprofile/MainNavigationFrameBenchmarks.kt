package os.kei.baselineprofile

import android.os.Trace
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainNavigationFrameBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private val targetAppId: String
        get() =
            InstrumentationRegistry
                .getArguments()
                .getString("targetAppId")
                ?: error("targetAppId not passed as instrumentation runner arg")

    @Test
    fun homeRestingDynamicBackground() {
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                grantRuntimePermissions(targetAppId)
                startActivityAndWait()
                waitForTag(HOME_PAGE_ROOT)
                waitForTag(MAIN_PAGER_SETTLED_HOME)
            },
            measureBlock = {
                traceSection("benchmark:home_resting_dynamic_background") {
                    Thread.sleep(HOME_RESTING_MEASURE_MS)
                }
            },
        )
    }

    @Test
    fun homeScrollWithFullEffects() {
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                grantRuntimePermissions(targetAppId)
                startActivityAndWait()
                waitForTag(HOME_PAGE_ROOT)
                waitForTag(MAIN_PAGER_SETTLED_HOME)
            },
            measureBlock = {
                traceSection("benchmark:home_scroll_full_effects") {
                    repeat(HOME_SCROLL_FLING_COUNT) {
                        swipePage(up = true, waitForIdle = false)
                    }
                    repeat(HOME_SCROLL_FLING_COUNT) {
                        swipePage(up = false, waitForIdle = false)
                    }
                    Thread.sleep(HOME_SCROLL_SETTLE_MS)
                }
            },
        )
    }

    /**
     * Shared Liquid Sheet motion with the generated profile applied.
     *
     * The generator uses the same strategy sheet and gesture order, so this measures the exact code
     * path the profile is expected to precompile rather than treating "sheet opened" as motion
     * coverage.
     */
    @Test
    fun liquidSheetMotionBaselineProfile() =
        measureLiquidSheetMotion(CompilationMode.Partial(BaselineProfileMode.Require))

    /** A/B sibling that establishes how much Sheet motion ART compilation can actually recover. */
    @Test
    fun liquidSheetMotionCompilationNone() = measureLiquidSheetMotion(CompilationMode.None())

    @OptIn(ExperimentalMetricApi::class)
    private fun measureLiquidSheetMotion(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = traceBreakdownMetrics(),
            compilationMode = compilationMode,
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                grantRuntimePermissions(targetAppId)
                startActivityAndWait()
                waitForTag(HOME_PAGE_ROOT)
                waitForTag(MAIN_PAGER_SETTLED_HOME)
                clickAndWait(
                    tabTag = MAIN_BOTTOM_TAB_GITHUB,
                    pageTag = GITHUB_PAGE_ROOT,
                    settledTag = MAIN_PAGER_SETTLED_GITHUB,
                )
                waitForTag(GITHUB_STRATEGY_SHEET_BUTTON)
                clickTag(GITHUB_STRATEGY_SHEET_BUTTON)
                waitForTag(LIQUID_SHEET_PANEL)
                waitForTag(LIQUID_SHEET_DRAG_REGION)
            },
            measureBlock = {
                traceSection("benchmark:liquid_sheet_expand_drag") {
                    dragLiquidSheetRegion(up = true)
                }
                traceSection("benchmark:liquid_sheet_content_scroll") {
                    swipeLiquidSheetContent(up = true)
                    swipeLiquidSheetContent(up = true)
                    swipeLiquidSheetContent(up = false)
                    swipeLiquidSheetContent(up = false)
                }
                traceSection("benchmark:liquid_sheet_collapse_drag") {
                    dragLiquidSheetRegion(up = false)
                }
            },
        )
    }

    @Test
    fun homeGitHubMcpHome() {
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                grantRuntimePermissions(targetAppId)
                startActivityAndWait()
                waitForTag(HOME_PAGE_ROOT)
                waitForTag(MAIN_PAGER_SETTLED_HOME)
            },
            measureBlock = {
                traceSection("benchmark:home_to_github") {
                    clickAndWait(
                        tabTag = MAIN_BOTTOM_TAB_GITHUB,
                        pageTag = GITHUB_PAGE_ROOT,
                        settledTag = MAIN_PAGER_SETTLED_GITHUB,
                    )
                }
                traceSection("benchmark:github_to_mcp") {
                    clickAndWait(
                        tabTag = MAIN_BOTTOM_TAB_MCP,
                        pageTag = MCP_PAGE_ROOT,
                        settledTag = MAIN_PAGER_SETTLED_MCP,
                    )
                }
                traceSection("benchmark:mcp_to_home") {
                    clickAndWait(
                        tabTag = MAIN_BOTTOM_TAB_HOME,
                        pageTag = HOME_PAGE_ROOT,
                        settledTag = MAIN_PAGER_SETTLED_HOME,
                    )
                }
            },
        )
    }

    /**
     * First entry into BA after a warm start: the RenderNode/Backdrop first-record window.
     */
    @Test
    fun homeBaFirstEntry() {
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                grantRuntimePermissions(targetAppId)
                startActivityAndWait()
                waitForTag(HOME_PAGE_ROOT)
                waitForTag(MAIN_PAGER_SETTLED_HOME)
                dwellOnHome()
            },
            measureBlock = {
                traceSection("benchmark:home_to_ba_first") {
                    clickAndWait(
                        tabTag = MAIN_BOTTOM_TAB_BA,
                        pageTag = BA_PAGE_ROOT,
                        settledTag = MAIN_PAGER_SETTLED_BA,
                    )
                }
            },
        )
    }

    /**
     * Second entry into BA within the same process, for the first-vs-second tail gap.
     */
    @Test
    fun homeBaSecondEntry() {
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                grantRuntimePermissions(targetAppId)
                startActivityAndWait()
                waitForTag(HOME_PAGE_ROOT)
                waitForTag(MAIN_PAGER_SETTLED_HOME)
                clickAndWait(
                    tabTag = MAIN_BOTTOM_TAB_BA,
                    pageTag = BA_PAGE_ROOT,
                    settledTag = MAIN_PAGER_SETTLED_BA,
                )
                clickAndWait(
                    tabTag = MAIN_BOTTOM_TAB_HOME,
                    pageTag = HOME_PAGE_ROOT,
                    settledTag = MAIN_PAGER_SETTLED_HOME,
                )
                dwellOnHome()
            },
            measureBlock = {
                traceSection("benchmark:home_to_ba_second") {
                    clickAndWait(
                        tabTag = MAIN_BOTTOM_TAB_BA,
                        pageTag = BA_PAGE_ROOT,
                        settledTag = MAIN_PAGER_SETTLED_BA,
                    )
                }
            },
        )
    }

    /**
     * Same journey as [homeBaFirstEntry], but reports a RenderThread/UI slice breakdown instead of
     * only frame percentiles. Four blind single-variable experiments have failed to move the
     * first-entry Max, so this answers "which stage owns the long bars" directly.
     *
     * [TraceSectionMetric.Mode.Sum] reports 0 for a section that never appears, so unmatched names
     * are harmless — the metric list can stay broad while we learn the real slice names.
     */
    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun homeBaFirstEntryTraceBreakdown() {
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = traceBreakdownMetrics(),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                grantRuntimePermissions(targetAppId)
                startActivityAndWait()
                waitForTag(HOME_PAGE_ROOT)
                waitForTag(MAIN_PAGER_SETTLED_HOME)
                dwellOnHome()
            },
            measureBlock = {
                traceSection("benchmark:home_to_ba_first") {
                    clickAndWait(
                        tabTag = MAIN_BOTTOM_TAB_BA,
                        pageTag = BA_PAGE_ROOT,
                        settledTag = MAIN_PAGER_SETTLED_BA,
                    )
                }
            },
        )
    }

    /**
     * Slice breakdown for the *second* entry in the same process, to diff against
     * [homeBaFirstEntryTraceBreakdown]. Second entry keeps the material fully intact, so whatever
     * stage carries the first-vs-second delta is the only cost a visually-neutral pre-warm can
     * recover.
     */
    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun homeBaSecondEntryTraceBreakdown() {
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = traceBreakdownMetrics(),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                grantRuntimePermissions(targetAppId)
                startActivityAndWait()
                waitForTag(HOME_PAGE_ROOT)
                waitForTag(MAIN_PAGER_SETTLED_HOME)
                clickAndWait(
                    tabTag = MAIN_BOTTOM_TAB_BA,
                    pageTag = BA_PAGE_ROOT,
                    settledTag = MAIN_PAGER_SETTLED_BA,
                )
                clickAndWait(
                    tabTag = MAIN_BOTTOM_TAB_HOME,
                    pageTag = HOME_PAGE_ROOT,
                    settledTag = MAIN_PAGER_SETTLED_HOME,
                )
                dwellOnHome()
            },
            measureBlock = {
                traceSection("benchmark:home_to_ba_second") {
                    clickAndWait(
                        tabTag = MAIN_BOTTOM_TAB_BA,
                        pageTag = BA_PAGE_ROOT,
                        settledTag = MAIN_PAGER_SETTLED_BA,
                    )
                }
            },
        )
    }

    /**
     * A route push and the pop back out, measured separately.
     *
     * `dumpsys gfxinfo` cannot answer this: its 120-frame ring holds far more than a 560ms
     * transition, so three attempts each caught a different mix of slide frames and whatever the
     * page did afterwards. A trace section around each half bounds the measurement to the
     * transition itself.
     *
     * Settings is the push to measure first because it is the one users take most, and both sides
     * of it are full-screen Liquid Glass — the covered page parallaxes under the scrim while the
     * entering page slides, so the display is compositing two glass surfaces for the whole slide.
     */
    @Test
    fun settingsRoutePushAndPop() {
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                grantRuntimePermissions(targetAppId)
                startActivityAndWait()
                waitForTag(HOME_PAGE_ROOT)
                waitForTag(MAIN_PAGER_SETTLED_HOME)
                dwellOnHome()
            },
            measureBlock = {
                traceSection("benchmark:settings_route_push") {
                    clickTag(HOME_SETTINGS_BUTTON)
                    waitForTag(SETTINGS_PAGE_ROOT)
                    settleRouteTransition()
                }
                traceSection("benchmark:settings_route_pop") {
                    device.pressBack()
                    waitForTag(HOME_PAGE_ROOT)
                    settleRouteTransition()
                }
            },
        )
    }

    /**
     * The same push measured on its *second* traversal in one process, so the numbers carry no
     * first-composition cost. The gap against [settingsRoutePushAndPop] separates "the route is
     * cold" from "the slide itself is expensive" — only the second is worth a rendering change.
     */
    @Test
    fun settingsRouteSecondPushAndPop() {
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                grantRuntimePermissions(targetAppId)
                startActivityAndWait()
                waitForTag(HOME_PAGE_ROOT)
                waitForTag(MAIN_PAGER_SETTLED_HOME)
                clickTag(HOME_SETTINGS_BUTTON)
                waitForTag(SETTINGS_PAGE_ROOT)
                settleRouteTransition()
                device.pressBack()
                waitForTag(HOME_PAGE_ROOT)
                dwellOnHome()
            },
            measureBlock = {
                traceSection("benchmark:settings_route_push_second") {
                    clickTag(HOME_SETTINGS_BUTTON)
                    waitForTag(SETTINGS_PAGE_ROOT)
                    settleRouteTransition()
                }
                traceSection("benchmark:settings_route_pop_second") {
                    device.pressBack()
                    waitForTag(HOME_PAGE_ROOT)
                    settleRouteTransition()
                }
            },
        )
    }

    /**
     * The merged Calendar/Pool route: one dock entry, one page, and a phone-only category switch.
     *
     * A wide window renders both lists side by side and removes the category bar, so the switch section
     * is conditional. The route-root wait is the stable arrival signal for both layouts.
     */
    @Test
    fun baCalendarPoolRouteInteractions() {
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                grantRuntimePermissions(targetAppId)
                startActivityAndWait()
                waitForTag(HOME_PAGE_ROOT)
                waitForTag(MAIN_PAGER_SETTLED_HOME)
                clickAndWait(
                    tabTag = MAIN_BOTTOM_TAB_BA,
                    pageTag = BA_PAGE_ROOT,
                    settledTag = MAIN_PAGER_SETTLED_BA,
                )
                waitForTag(BA_DOCK_OPEN_CALENDAR_POOL)
            },
            measureBlock = {
                traceSection("benchmark:ba_calendar_pool_route_push") {
                    clickTag(BA_DOCK_OPEN_CALENDAR_POOL)
                    check(device.wait(Until.hasObject(By.res(BA_CALENDAR_POOL_PAGE_ROOT)), PAGE_TIMEOUT_MS)) {
                        "Timed out waiting for the merged Calendar/Pool page"
                    }
                    settleRouteTransition()
                }
                if (device.findObject(By.res(BA_CALENDAR_POOL_TAB_POOL)) != null) {
                    traceSection("benchmark:ba_calendar_pool_tab_switch") {
                        clickTag(BA_CALENDAR_POOL_TAB_POOL)
                        settleRouteTransition()
                    }
                }
                traceSection("benchmark:ba_calendar_pool_route_pop") {
                    device.pressBack()
                    waitForTag(BA_PAGE_ROOT)
                    settleRouteTransition()
                }
            },
        )
    }

    /**
     * Slice breakdown for the Settings push, to name the stage that owns the slide. Reported
     * alongside [settingsRoutePushAndPop] so the percentiles and the breakdown describe the same
     * journey.
     */
    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun settingsRoutePushTraceBreakdown() {
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = traceBreakdownMetrics(),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                grantRuntimePermissions(targetAppId)
                startActivityAndWait()
                waitForTag(HOME_PAGE_ROOT)
                waitForTag(MAIN_PAGER_SETTLED_HOME)
                dwellOnHome()
            },
            measureBlock = {
                traceSection("benchmark:settings_route_push") {
                    clickTag(HOME_SETTINGS_BUTTON)
                    waitForTag(SETTINGS_PAGE_ROOT)
                    settleRouteTransition()
                }
            },
        )
    }

    @Test
    fun mcpStackedCardsScroll() {
        rule.measureRepeated(
            packageName = targetAppId,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                grantRuntimePermissions(targetAppId)
                startActivityAndWait()
                waitForTag(HOME_PAGE_ROOT)
                waitForTag(MAIN_PAGER_SETTLED_HOME)
                clickAndWait(
                    tabTag = MAIN_BOTTOM_TAB_MCP,
                    pageTag = MCP_PAGE_ROOT,
                    settledTag = MAIN_PAGER_SETTLED_MCP,
                )
            },
            measureBlock = {
                repeat(MCP_SCROLL_FLING_COUNT) {
                    swipeMcpPage(up = true)
                }
                repeat(MCP_SCROLL_FLING_COUNT) {
                    swipeMcpPage(up = false)
                }
            },
        )
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.clickAndWait(
        tabTag: String,
        pageTag: String,
        settledTag: String,
    ) {
        val tab = device.findObject(By.res(tabTag))
            ?: error("Unable to find tab testTag=$tabTag")
        tab.click()
        waitForTag(pageTag)
        waitForTag(settledTag)
    }

    /**
     * The plan's fixed journey is "温启动 App，停留 Home，Home → BA" — it dwells on Home before the
     * jump. Tapping the instant Home settles was measuring something a user never does, and it also
     * gave any idle-triggered work no window to run in.
     */
    private fun dwellOnHome() {
        Thread.sleep(HOME_DWELL_MS)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.clickTag(tag: String) {
        val node = device.findObject(By.res(tag))
            ?: error("Unable to find testTag=$tag")
        node.click()
    }

    /**
     * Holds the measurement open past the end of the route transition.
     *
     * The page-root tag appears as soon as the entering entry composes, which is the *start* of the
     * slide, not the end. Without this the measured window would cover only the first few frames —
     * the cheap ones, before both layers are on screen together.
     */
    private fun settleRouteTransition() {
        Thread.sleep(ROUTE_TRANSITION_SETTLE_MS)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.waitForTag(tag: String) {
        check(device.wait(Until.hasObject(By.res(tag)), PAGE_TIMEOUT_MS)) {
            "Timed out waiting for testTag=$tag in $targetAppId"
        }
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.swipeMcpPage(up: Boolean) {
        swipePage(up = up, waitForIdle = true)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.swipePage(
        up: Boolean,
        waitForIdle: Boolean,
    ) {
        val centerX = device.displayWidth / 2
        val upperY = (device.displayHeight * MCP_SCROLL_UPPER_FRACTION).toInt()
        val lowerY = (device.displayHeight * MCP_SCROLL_LOWER_FRACTION).toInt()
        val startY = if (up) lowerY else upperY
        val endY = if (up) upperY else lowerY
        check(device.swipe(centerX, startY, centerX, endY, MCP_SCROLL_STEPS)) {
            "Unable to swipe page ${if (up) "up" else "down"}"
        }
        if (waitForIdle) {
            device.waitForIdle()
        } else {
            Thread.sleep(HOME_SCROLL_BETWEEN_SWIPES_MS)
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.dragLiquidSheetRegion(up: Boolean) {
        val bounds = device.findObject(By.res(LIQUID_SHEET_DRAG_REGION))?.visibleBounds
            ?: error("Unable to find Liquid Sheet drag region")
        val distance = (device.displayHeight * LIQUID_SHEET_DRAG_DISTANCE_FRACTION).toInt()
        val startY = bounds.centerY()
        val endY =
            (if (up) startY - distance else startY + distance)
                .coerceIn(1, device.displayHeight - 2)
        check(
            device.swipe(
                bounds.centerX(),
                startY,
                bounds.centerX(),
                endY,
                LIQUID_SHEET_DRAG_STEPS,
            ),
        ) {
            "Unable to drag Liquid Sheet ${if (up) "up" else "down"}"
        }
        Thread.sleep(LIQUID_SHEET_GESTURE_SETTLE_MS)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.swipeLiquidSheetContent(up: Boolean) {
        val bounds = device.findObject(By.res(LIQUID_SHEET_PANEL))?.visibleBounds
            ?: error("Unable to find Liquid Sheet panel")
        val upperY = bounds.top + (bounds.height() * LIQUID_SHEET_CONTENT_UPPER_FRACTION).toInt()
        val lowerY = bounds.top + (bounds.height() * LIQUID_SHEET_CONTENT_LOWER_FRACTION).toInt()
        val startY = if (up) lowerY else upperY
        val endY = if (up) upperY else lowerY
        check(
            device.swipe(
                bounds.centerX(),
                startY,
                bounds.centerX(),
                endY,
                LIQUID_SHEET_CONTENT_SCROLL_STEPS,
            ),
        ) {
            "Unable to scroll Liquid Sheet content ${if (up) "up" else "down"}"
        }
        Thread.sleep(LIQUID_SHEET_BETWEEN_SWIPES_MS)
    }
}

@OptIn(ExperimentalMetricApi::class)
private fun traceBreakdownMetrics(): List<androidx.benchmark.macro.Metric> =
    listOf(FrameTimingMetric()) +
        TRACE_BREAKDOWN_SECTIONS.flatMap { section ->
            listOf(
                TraceSectionMetric(
                    sectionName = section,
                    mode = TraceSectionMetric.Mode.Sum,
                    label = "${section}_sum",
                ),
                TraceSectionMetric(
                    sectionName = section,
                    mode = TraceSectionMetric.Mode.Max,
                    label = "${section}_max",
                ),
            )
        }

private inline fun <T> traceSection(
    name: String,
    block: () -> T,
): T {
    Trace.beginSection(name)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}

private const val MAIN_BOTTOM_TAB_HOME = "main_bottom_tab_home"
private const val MAIN_BOTTOM_TAB_MCP = "main_bottom_tab_mcp"
private const val MAIN_BOTTOM_TAB_GITHUB = "main_bottom_tab_github"
private const val MAIN_BOTTOM_TAB_BA = "main_bottom_tab_ba"
private const val MAIN_PAGER_SETTLED_HOME = "main_pager_settled_home"
private const val MAIN_PAGER_SETTLED_MCP = "main_pager_settled_mcp"
private const val MAIN_PAGER_SETTLED_GITHUB = "main_pager_settled_github"
private const val MAIN_PAGER_SETTLED_BA = "main_pager_settled_ba"
private const val HOME_PAGE_ROOT = "home_page_root"
private const val MCP_PAGE_ROOT = "mcp_page_root"
private const val GITHUB_PAGE_ROOT = "github_page_root"
private const val GITHUB_STRATEGY_SHEET_BUTTON = "github_strategy_sheet_button"
private const val BA_PAGE_ROOT = "ba_page_root"
private const val BA_DOCK_OPEN_CALENDAR_POOL = "ba_dock_open_calendar_pool"
private const val BA_CALENDAR_POOL_PAGE_ROOT = "ba_calendar_pool_page_root"
private const val BA_CALENDAR_POOL_TAB_POOL = "ba_calendar_pool_tab_1"
private const val HOME_SETTINGS_BUTTON = "home_settings_button"
private const val SETTINGS_PAGE_ROOT = "settings_page_root"
private const val LIQUID_SHEET_PANEL = "liquid_sheet_panel"
private const val LIQUID_SHEET_DRAG_REGION = "liquid_sheet_drag_region"

/**
 * RenderThread/UI slice names for the plan's P0 breakdown. "%" is a TraceProcessor wildcard, used
 * where the exact HWUI slice name carries a per-frame suffix.
 */
private val TRACE_BREAKDOWN_SECTIONS =
    listOf(
        // RenderThread
        "DrawFrame%",
        "flush layers",
        "flush commands",
        "drawLayer",
        "eglSwapBuffers%",
        "dequeueBuffer%",
        "queueBuffer%",
        "syncFrameState",
        "prepareTree",
        "Drawing%",
        // UI thread / Compose
        "Choreographer#doFrame%",
        "Compose:recompose",
        "Record View#draw()",
        "measure",
        "layout",
        "draw",
    )

private const val PAGE_TIMEOUT_MS = 15_000L

/** Dwell on Home before jumping to BA, per the plan's "停留 Home" fixed journey. */
private const val HOME_DWELL_MS = 1_500L

/**
 * Comfortably past `RouteSwitchDurationMillis` (560), so a measured window always contains the
 * whole slide plus the frames right after it settles.
 */
private const val ROUTE_TRANSITION_SETTLE_MS = 900L
private const val HOME_RESTING_MEASURE_MS = 3_000L
private const val HOME_SCROLL_BETWEEN_SWIPES_MS = 180L
private const val HOME_SCROLL_SETTLE_MS = 600L
private const val HOME_SCROLL_FLING_COUNT = 2
private const val MCP_SCROLL_FLING_COUNT = 3
private const val MCP_SCROLL_UPPER_FRACTION = 0.30f
private const val MCP_SCROLL_LOWER_FRACTION = 0.78f
private const val MCP_SCROLL_STEPS = 18
private const val LIQUID_SHEET_DRAG_STEPS = 72
private const val LIQUID_SHEET_CONTENT_SCROLL_STEPS = 18
private const val LIQUID_SHEET_DRAG_DISTANCE_FRACTION = 0.14f
private const val LIQUID_SHEET_CONTENT_UPPER_FRACTION = 0.34f
private const val LIQUID_SHEET_CONTENT_LOWER_FRACTION = 0.82f
private const val LIQUID_SHEET_GESTURE_SETTLE_MS = 480L
private const val LIQUID_SHEET_BETWEEN_SWIPES_MS = 160L
