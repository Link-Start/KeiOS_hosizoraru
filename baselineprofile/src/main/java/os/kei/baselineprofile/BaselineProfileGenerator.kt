package os.kei.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The default profile is intentionally a small set of user journeys.
 *
 * A Baseline Profile is an install-time compilation budget. It should make startup, the main page
 * switch, first scrolls and the app's common routes warm. Feature diagnostics, debug catalogues and
 * network-dependent edge cases belong to functional tests: collecting them here enlarged the profile,
 * made the run depend on remote state and stretched a capture to 23 journeys.
 *
 * The maximum replay budget is 16:
 *
 *  - startup: 5
 *  - main pages: 3
 *  - common routes: 2
 *  - GitHub core: 2
 *  - BA core: 2
 *  - adaptive layouts: 2
 *
 * Each non-startup journey groups adjacent interactions behind one cold start. This preserves distinct
 * failure names while avoiding a new process launch for every screen.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    /**
     * Startup plus the first user gesture. This is the only journey included in startup-prof.txt.
     */
    @Test
    fun startupAndFirstScroll() {
        rule.collect(
            packageName = targetAppId(),
            maxIterations = STARTUP_MAX_ITERATIONS,
            stableIterations = STARTUP_STABLE_ITERATIONS,
            includeInStartupProfile = true,
        ) {
            launchHomeFromColdStart()
            flingVisibleScrollable(times = 2)
        }
    }

    /**
     * Every primary destination, its enter transition and its first list movement.
     */
    @Test
    fun mainPagesAndNavigation() {
        rule.collect(
            packageName = targetAppId(),
            maxIterations = CORE_MAX_ITERATIONS,
            stableIterations = CORE_STABLE_ITERATIONS,
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()
            flingVisibleScrollable(times = 2)

            navigateAndScrollMainPage(
                tabTag = MAIN_BOTTOM_TAB_OS,
                pageTag = OS_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_OS,
            )
            navigateAndScrollMainPage(
                tabTag = MAIN_BOTTOM_TAB_MCP,
                pageTag = MCP_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_MCP,
            )
            navigateAndScrollMainPage(
                tabTag = MAIN_BOTTOM_TAB_GITHUB,
                pageTag = GITHUB_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_GITHUB,
            )
            navigateAndScrollMainPage(
                tabTag = MAIN_BOTTOM_TAB_BA,
                pageTag = BA_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_BA,
            )
            navigateToMainPage(
                tabTag = MAIN_BOTTOM_TAB_HOME,
                pageTag = HOME_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_HOME,
            )
        }
    }

    /**
     * Common route pushes and the shared presentation layer.
     */
    @Test
    fun commonRoutesAndChrome() {
        rule.collect(
            packageName = targetAppId(),
            maxIterations = FEATURE_MAX_ITERATIONS,
            stableIterations = FEATURE_STABLE_ITERATIONS,
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            pushRouteAndReturn(
                entryTag = HOME_SETTINGS_BUTTON,
                pageTag = SETTINGS_PAGE_ROOT,
                returnTag = HOME_PAGE_ROOT,
            )
            pushRouteAndReturn(
                entryTag = HOME_ABOUT_BUTTON,
                pageTag = ABOUT_PAGE_ROOT,
                returnTag = HOME_PAGE_ROOT,
            )
            pushRouteAndReturn(
                entryTag = HOME_WEBDAV_CARD,
                pageTag = WEBDAV_SYNC_PAGE_ROOT,
                returnTag = HOME_PAGE_ROOT,
                flings = 1,
            )

            navigateToMainPage(
                tabTag = MAIN_BOTTOM_TAB_OS,
                pageTag = OS_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_OS,
            )
            pushRouteAndReturn(
                entryTag = OS_SHELL_RUNNER_BUTTON,
                pageTag = OS_SHELL_RUNNER_PAGE_ROOT,
                returnTag = OS_PAGE_ROOT,
                flings = 1,
            )

            navigateToMainPage(
                tabTag = MAIN_BOTTOM_TAB_MCP,
                pageTag = MCP_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_MCP,
            )
            pushRouteAndReturn(
                entryTag = MCP_SKILL_BUTTON,
                pageTag = MCP_SKILL_PAGE_ROOT,
                returnTag = MCP_PAGE_ROOT,
            )

            navigateToMainPage(
                tabTag = MAIN_BOTTOM_TAB_GITHUB,
                pageTag = GITHUB_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_GITHUB,
            )
            openMenuAndDismiss(
                triggerTag = GITHUB_IMPORT_MENU_BUTTON,
                rowTag = GITHUB_IMPORT_TRACKS,
            )
        }
    }

    /**
     * The frequent GitHub work: inspect a tracked app and open the two primary editing sheets.
     *
     * A fresh install can legitimately have no tracked card. The page, navigation and edit surfaces
     * still collect deterministically; the card branch becomes available once user state exists.
     */
    @Test
    fun gitHubTrackingCore() {
        rule.collect(
            packageName = targetAppId(),
            maxIterations = FEATURE_MAX_ITERATIONS,
            stableIterations = FEATURE_STABLE_ITERATIONS,
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()
            navigateToMainPage(
                tabTag = MAIN_BOTTOM_TAB_GITHUB,
                pageTag = GITHUB_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_GITHUB,
            )

            if (waitForOptionalTestTag(GITHUB_TRACKED_ITEM_CARD_FIRST, timeoutMs = 8_000)) {
                scrollTestTagIntoReach(GITHUB_TRACKED_ITEM_CARD_FIRST)
                clickTaggedCardHeader(GITHUB_TRACKED_ITEM_CARD_FIRST)
                flingVisibleScrollable(times = 1)

                scrollTestTagIntoReach(GITHUB_TRACKED_ITEM_MORE_BUTTON)
                clickTestTag(GITHUB_TRACKED_ITEM_MORE_BUTTON)
                if (waitForOptionalTestTag(GITHUB_ACTIONS_MENU_ITEM, timeoutMs = 5_000)) {
                    clickTestTag(GITHUB_ACTIONS_MENU_ITEM)
                    waitForTestTag(LIQUID_SHEET_PANEL, timeoutMs = 12_000)
                    dismissTheOpenOverlay(LIQUID_SHEET_PANEL)
                } else {
                    device.pressBack()
                    device.waitForIdle()
                }
            }

            openAndDismissOverlay(
                triggerTag = GITHUB_ADD_TRACKED_BUTTON,
                panelTag = LIQUID_SHEET_PANEL,
            )
            openExerciseAndDismissLiquidSheet(GITHUB_STRATEGY_SHEET_BUTTON)
        }
    }

    /**
     * BA office cards, the merged calendar route, the daily sheet and the media catalogue.
     *
     * Two representative student-detail tabs replace the former six-tab sweep. The catalogue still
     * includes Students, Lobby, Music and Play because each is a distinct high-level experience and
     * playing one row is the only path that warms Media3.
     */
    @Test
    fun baOfficeAndCatalogCore() {
        rule.collect(
            packageName = targetAppId(),
            maxIterations = FEATURE_MAX_ITERATIONS,
            stableIterations = FEATURE_STABLE_ITERATIONS,
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()
            navigateToMainPage(
                tabTag = MAIN_BOTTOM_TAB_BA,
                pageTag = BA_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_BA,
            )
            flingVisibleScrollable(times = 2)
            dragVisibleScrollable(times = 1)

            if (waitForOptionalTestTag(BA_COOLDOWN_CARD_FIRST, timeoutMs = 5_000)) {
                scrollTestTagIntoReach(BA_COOLDOWN_CARD_FIRST)
                clickTaggedCardHeader(BA_COOLDOWN_CARD_FIRST)
                if (waitForOptionalTestTag(BA_COOLDOWN_ADJUST_BUTTON, timeoutMs = 5_000)) {
                    openAndDismissOverlay(
                        triggerTag = BA_COOLDOWN_ADJUST_BUTTON,
                        panelTag = LIQUID_SHEET_PANEL,
                        required = false,
                    )
                }
            }

            openBaCalendarPoolAndReturn()
            openAndDismissOverlay(
                triggerTag = BA_DOCK_DAILY_DONE,
                panelTag = LIQUID_SHEET_PANEL,
                required = false,
            )

            if (openWindowFrom(
                    triggerTag = BA_DOCK_OPEN_GUIDE_CATALOG,
                    arrivalTag = BA_GUIDE_CATALOG_PAGE_ROOT,
                    required = false,
                )
            ) {
                if (waitForOptionalTestTag(BA_GUIDE_CATALOG_ENTRY_FIRST, timeoutMs = 8_000)) {
                    scrollTestTagIntoReach(BA_GUIDE_CATALOG_ENTRY_FIRST)
                    clickTestTag(BA_GUIDE_CATALOG_ENTRY_FIRST)
                    if (waitForOptionalTestTag(BA_STUDENT_GUIDE_PAGE_ROOT, timeoutMs = 15_000)) {
                        flingVisibleScrollable(times = 1)
                        clickBottomBarTab(BA_STUDENT_GUIDE_TAB_PROFILE)
                        flingVisibleScrollable(times = 1)
                        clickBottomBarTab(BA_STUDENT_GUIDE_TAB_SKILLS)
                        device.pressBack()
                        waitForTestTag(BA_GUIDE_CATALOG_PAGE_ROOT, timeoutMs = 15_000)
                    }
                }

                clickBottomBarTab(BA_GUIDE_CATALOG_DOCK_MEMORY_LOBBY)
                flingVisibleScrollable(times = 1)
                clickBottomBarTab(BA_GUIDE_CATALOG_DOCK_STUDENT_BGM)
                if (waitForOptionalTestTag(BA_GUIDE_CATALOG_STUDENT_BGM_FIRST, timeoutMs = 8_000)) {
                    scrollTestTagIntoReach(BA_GUIDE_CATALOG_STUDENT_BGM_FIRST)
                    clickTestTag(BA_GUIDE_CATALOG_STUDENT_BGM_FIRST)
                    flingVisibleScrollable(times = 1)
                }
                clickBottomBarTab(BA_GUIDE_CATALOG_DOCK_FAVORITE_BGM)
                flingVisibleScrollable(times = 1)
                clickBottomBarTab(BA_GUIDE_CATALOG_DOCK_STUDENT)

                device.pressBack()
                waitForTestTag(BA_PAGE_ROOT, timeoutMs = 15_000)
            }
        }
    }

    /**
     * The wide branches added by the recent two-lane UI work, followed by a live fold transition.
     *
     * One forced 1000x800dp window exercises Settings, About, OS, Shell, MCP, Skill, BA, Calendar,
     * Catalogue and GitHub in their wide forms. Both lane coordinates are scrolled independently.
     * The final 775dp -> 500dp transition covers sidebar-to-bottom-bar reflow without another cold start.
     */
    @Test
    fun adaptiveLargeScreenCore() {
        rule.collect(
            packageName = targetAppId(),
            maxIterations = ADAPTIVE_MAX_ITERATIONS,
            stableIterations = ADAPTIVE_STABLE_ITERATIONS,
            includeInStartupProfile = false,
        ) {
            try {
                forceWindowSizeDp(widthDp = 1000, heightDp = 800)
                launchHomeFromColdStart()

                pushWideRouteAndReturn(
                    entryTag = HOME_SETTINGS_BUTTON,
                    pageTag = SETTINGS_PAGE_ROOT,
                    returnTag = HOME_PAGE_ROOT,
                )
                pushWideRouteAndReturn(
                    entryTag = HOME_ABOUT_BUTTON,
                    pageTag = ABOUT_PAGE_ROOT,
                    returnTag = HOME_PAGE_ROOT,
                )

                navigateToMainPage(
                    tabTag = MAIN_BOTTOM_TAB_OS,
                    pageTag = OS_PAGE_ROOT,
                    settledTag = MAIN_PAGER_SETTLED_OS,
                )
                exerciseWideLanes()
                pushWideRouteAndReturn(
                    entryTag = OS_SHELL_RUNNER_BUTTON,
                    pageTag = OS_SHELL_RUNNER_PAGE_ROOT,
                    returnTag = OS_PAGE_ROOT,
                )

                // Scrolling both wide lanes can briefly rebuild the top chrome while the shared
                // pager settles. Wait for the adaptable-navigation control to re-enter the
                // accessibility tree before converting the tab bar to a sidebar.
                waitForTestTag(MAIN_SIDEBAR_TOGGLE, timeoutMs = 15_000)
                clickTestTag(MAIN_SIDEBAR_TOGGLE)
                waitForTestTag(MAIN_SIDEBAR_ROW_MCP)
                clickSidebarPage(
                    rowTag = MAIN_SIDEBAR_ROW_MCP,
                    pageTag = MCP_PAGE_ROOT,
                    settledTag = MAIN_PAGER_SETTLED_MCP,
                )
                exerciseWideLanes()
                pushWideRouteAndReturn(
                    entryTag = MCP_SKILL_BUTTON,
                    pageTag = MCP_SKILL_PAGE_ROOT,
                    returnTag = MCP_PAGE_ROOT,
                )

                clickSidebarPage(
                    rowTag = MAIN_SIDEBAR_ROW_BA,
                    pageTag = BA_PAGE_ROOT,
                    settledTag = MAIN_PAGER_SETTLED_BA,
                )
                exerciseWideLanes()
                openBaCalendarPoolAndReturn(wide = true)

                if (openWindowFrom(
                        triggerTag = BA_DOCK_OPEN_GUIDE_CATALOG,
                        arrivalTag = BA_GUIDE_CATALOG_PAGE_ROOT,
                        required = false,
                    )
                ) {
                    // First, while the catalog is still on the tab it opens on and its list is where it
                    // was left: the guide page, and the rail this width unlocks. Only offered at a width
                    // that can hold a rail, so this journey is the only place in the profile that can
                    // reach it -- and it is off by default, so without this the rail's first frame on a
                    // tablet is JIT-warmed. Ordered like baOfficeAndCatalogCore's, which taps the entry
                    // on arrival; doing it after the lane flings and the tab tour found nothing.
                    if (waitForOptionalTestTag(BA_GUIDE_CATALOG_ENTRY_FIRST, timeoutMs = 8_000)) {
                        scrollTestTagIntoReach(BA_GUIDE_CATALOG_ENTRY_FIRST)
                        clickTestTag(BA_GUIDE_CATALOG_ENTRY_FIRST)
                        if (waitForOptionalTestTag(BA_STUDENT_GUIDE_PAGE_ROOT, timeoutMs = 15_000)) {
                            exerciseGuideSidebarAndRestore()
                            device.pressBack()
                            waitForTestTag(BA_GUIDE_CATALOG_PAGE_ROOT, timeoutMs = 15_000)
                        }
                    }
                    exerciseWideLanes()
                    clickBottomBarTab(BA_GUIDE_CATALOG_DOCK_MEMORY_LOBBY)
                    exerciseWideLanes()
                    clickBottomBarTab(BA_GUIDE_CATALOG_DOCK_STUDENT_BGM)
                    if (waitForOptionalTestTag(BA_GUIDE_CATALOG_STUDENT_BGM_FIRST, timeoutMs = 8_000)) {
                        scrollTestTagIntoReach(BA_GUIDE_CATALOG_STUDENT_BGM_FIRST)
                        clickTestTag(BA_GUIDE_CATALOG_STUDENT_BGM_FIRST)
                    }
                    clickBottomBarTab(BA_GUIDE_CATALOG_DOCK_FAVORITE_BGM)
                    exerciseWideLanes()
                    device.pressBack()
                    waitForTestTag(BA_PAGE_ROOT, timeoutMs = 15_000)
                }

                clickSidebarPage(
                    rowTag = MAIN_SIDEBAR_ROW_GITHUB,
                    pageTag = GITHUB_PAGE_ROOT,
                    settledTag = MAIN_PAGER_SETTLED_GITHUB,
                )
                exerciseWideLanes()
                if (openWindowFrom(
                        triggerTag = GITHUB_ACTIONS_HISTORY_BUTTON,
                        arrivalTag = GITHUB_ACTIONS_HISTORY_PAGE_ROOT,
                        required = false,
                    )
                ) {
                    clickBottomBarTab(GITHUB_HISTORY_TAB_ACTIONS)
                    exerciseWideLanes()
                    clickBottomBarTab(GITHUB_HISTORY_TAB_TRACKING)
                    exerciseWideLanes()
                    device.pressBack()
                    waitForTestTag(GITHUB_PAGE_ROOT, timeoutMs = 15_000)
                }

                // The sidebar row selection above can still be settling its chrome layer. Keep
                // this second shape conversion deterministic for the following window reflow.
                waitForTestTag(MAIN_SIDEBAR_TOGGLE, timeoutMs = 15_000)
                clickTestTag(MAIN_SIDEBAR_TOGGLE)
                forceWindowSizeDp(widthDp = 775, heightDp = 800)
                forceWindowSizeDp(widthDp = 500, heightDp = 800)
                navigateToMainPage(
                    tabTag = MAIN_BOTTOM_TAB_HOME,
                    pageTag = HOME_PAGE_ROOT,
                    settledTag = MAIN_PAGER_SETTLED_HOME,
                )
            } finally {
                resetWindowSize()
            }
        }
    }
}

private const val STARTUP_MAX_ITERATIONS = 5
private const val STARTUP_STABLE_ITERATIONS = 2
private const val CORE_MAX_ITERATIONS = 3
private const val CORE_STABLE_ITERATIONS = 2
private const val FEATURE_MAX_ITERATIONS = 2
private const val FEATURE_STABLE_ITERATIONS = 2
private const val ADAPTIVE_MAX_ITERATIONS = 2
private const val ADAPTIVE_STABLE_ITERATIONS = 2

private fun MacrobenchmarkScope.navigateAndScrollMainPage(
    tabTag: String,
    pageTag: String,
    settledTag: String,
) {
    navigateToMainPage(tabTag, pageTag, settledTag)
    flingVisibleScrollable(times = 2)
}

private fun MacrobenchmarkScope.navigateToMainPage(
    tabTag: String,
    pageTag: String,
    settledTag: String,
) {
    clickBottomBarTab(tabTag)
    waitForTestTag(pageTag, timeoutMs = 15_000)
    waitForTestTag(settledTag, timeoutMs = 15_000)
}

private fun MacrobenchmarkScope.clickSidebarPage(
    rowTag: String,
    pageTag: String,
    settledTag: String,
) {
    clickTestTag(rowTag)
    waitForTestTag(pageTag, timeoutMs = 15_000)
    waitForTestTag(settledTag, timeoutMs = 15_000)
}

private fun MacrobenchmarkScope.pushRouteAndReturn(
    entryTag: String,
    pageTag: String,
    returnTag: String,
    flings: Int = 2,
) {
    openWindowFrom(triggerTag = entryTag, arrivalTag = pageTag)
    flingVisibleScrollable(times = flings)
    returnFromPushedRoute(pageTag = pageTag, returnTag = returnTag)
}

private fun MacrobenchmarkScope.pushWideRouteAndReturn(
    entryTag: String,
    pageTag: String,
    returnTag: String,
) {
    openWindowFrom(triggerTag = entryTag, arrivalTag = pageTag)
    exerciseWideLanes()
    returnFromPushedRoute(pageTag = pageTag, returnTag = returnTag)
}

/**
 * Leaves a pushed route and proves the pushed page actually disappeared.
 *
 * Several route roots stay composed underneath the pushed page, so seeing [returnTag] alone is not
 * proof of a pop. Shell Runner also focuses its editor and opens the IME: its first Back closes the
 * keyboard and its second Back pops the route. Other routes complete on the first attempt.
 */
private fun MacrobenchmarkScope.returnFromPushedRoute(
    pageTag: String,
    returnTag: String,
) {
    repeat(PUSH_ROUTE_MAX_BACK_ATTEMPTS) {
        device.pressBack()
        if (device.wait(Until.gone(testTagSelector(pageTag)), PUSH_ROUTE_GONE_TIMEOUT_MS)) {
            waitForTestTag(returnTag, timeoutMs = 15_000)
            return
        }
    }
    error("Unable to leave pushed route testTag=$pageTag in ${targetAppId()}")
}

private fun MacrobenchmarkScope.openBaCalendarPoolAndReturn(wide: Boolean = false) {
    if (!openWindowFrom(
            triggerTag = BA_DOCK_OPEN_CALENDAR_POOL,
            arrivalTag = BA_CALENDAR_POOL_PAGE_ROOT,
            required = false,
        )
    ) {
        return
    }

    if (wide) {
        // The merged page shows Calendar and Pool side by side at this width and removes the category
        // bar. Driving both lane coordinates covers the two lists; looking for the phone-only Pool tab
        // here would time out on exactly the new UI this journey exists to profile.
        exerciseWideLanes()
    } else {
        flingVisibleScrollable(times = 1)
        clickBottomBarTab(BA_CALENDAR_POOL_TAB_POOL)
        flingVisibleScrollable(times = 1)
    }
    device.pressBack()
    waitForTestTag(BA_PAGE_ROOT, timeoutMs = 15_000)
}

private fun MacrobenchmarkScope.openAndDismissOverlay(
    triggerTag: String,
    panelTag: String,
    required: Boolean = true,
): Boolean {
    val opened = openWindowFrom(triggerTag, panelTag, required)
    if (opened) dismissTheOpenOverlay(panelTag)
    return opened
}

/**
 * Compiles the shared Sheet paths that opening and pressing Back never reaches.
 *
 * The strategy sheet is deterministic, long and lazy. Expanding through the grabber first lets the
 * following swipes belong to its content; the final grabber drag warms resize, nested-scroll
 * arbitration, layout-height updates and the settle spring without adding another cold start.
 */
private fun MacrobenchmarkScope.openExerciseAndDismissLiquidSheet(triggerTag: String) {
    openWindowFrom(triggerTag = triggerTag, arrivalTag = LIQUID_SHEET_PANEL)
    waitForTestTag(LIQUID_SHEET_DRAG_REGION, timeoutMs = 12_000)

    dragLiquidSheetRegion(up = true)
    swipeWithinTestTag(LIQUID_SHEET_PANEL, up = true)
    swipeWithinTestTag(LIQUID_SHEET_PANEL, up = false)
    dragLiquidSheetRegion(up = false)

    dismissTheOpenOverlay(LIQUID_SHEET_PANEL)
}

private fun MacrobenchmarkScope.dragLiquidSheetRegion(up: Boolean) {
    val bounds = device.findObject(testTagSelector(LIQUID_SHEET_DRAG_REGION))?.visibleBounds
        ?: error("Unable to find Liquid Sheet drag region in ${targetAppId()}")
    val distance = (device.displayHeight * LIQUID_SHEET_DRAG_DISTANCE_FRACTION).toInt()
    val startY = bounds.centerY()
    val endY =
        (if (up) startY - distance else startY + distance)
            .coerceIn(1, device.displayHeight - 2)
    swipeWithInjectionRetry(
        startX = bounds.centerX(),
        startY = startY,
        endX = bounds.centerX(),
        endY = endY,
        steps = DRAG_STEPS,
        failureMessage = "Unable to drag Liquid Sheet ${if (up) "up" else "down"}",
    )
}

private fun MacrobenchmarkScope.swipeWithinTestTag(tag: String, up: Boolean) {
    val bounds = device.findObject(testTagSelector(tag))?.visibleBounds
        ?: error("Unable to find testTag=$tag in ${targetAppId()}")
    val upperY = bounds.top + (bounds.height() * LIQUID_SHEET_CONTENT_UPPER_FRACTION).toInt()
    val lowerY = bounds.top + (bounds.height() * LIQUID_SHEET_CONTENT_LOWER_FRACTION).toInt()
    val startY = if (up) lowerY else upperY
    val endY = if (up) upperY else lowerY
    swipeWithInjectionRetry(
        startX = bounds.centerX(),
        startY = startY,
        endX = bounds.centerX(),
        endY = endY,
        steps = FLING_STEPS,
        failureMessage = "Unable to scroll Liquid Sheet content ${if (up) "up" else "down"}",
    )
}

private fun MacrobenchmarkScope.swipeWithInjectionRetry(
    startX: Int,
    startY: Int,
    endX: Int,
    endY: Int,
    steps: Int,
    failureMessage: String,
) {
    repeat(GESTURE_INJECTION_ATTEMPTS) {
        if (device.swipe(startX, startY, endX, endY, steps)) {
            device.waitForIdle()
            return
        }
        device.waitForIdle()
    }
    error(failureMessage)
}

private fun MacrobenchmarkScope.openMenuAndDismiss(
    triggerTag: String,
    rowTag: String,
) {
    clickVisibleTag(triggerTag)
    waitForTestTag(rowTag, timeoutMs = 12_000)
    device.pressBack()
    check(device.wait(Until.gone(testTagSelector(rowTag)), 12_000)) {
        "Timed out waiting for menu row testTag=$rowTag to dismiss in ${targetAppId()}"
    }
    device.waitForIdle()
}

private fun MacrobenchmarkScope.dismissTheOpenOverlay(panelTag: String) {
    device.pressBack()
    check(device.wait(Until.gone(testTagSelector(panelTag)), 15_000)) {
        "Timed out waiting for testTag=$panelTag to dismiss in ${targetAppId()}"
    }
    device.waitForIdle()
}

private fun MacrobenchmarkScope.openWindowFrom(
    triggerTag: String,
    arrivalTag: String,
    required: Boolean = true,
): Boolean {
    repeat(OPEN_WINDOW_ATTEMPTS) {
        if (clickVisibleTag(triggerTag, timeoutMs = 6_000) &&
            device.wait(Until.hasObject(testTagSelector(arrivalTag)), 12_000)
        ) {
            device.waitForIdle()
            return true
        }
        nudgeVisibleScrollable(forward = true)
    }

    check(!required) {
        "testTag=$triggerTag never opened testTag=$arrivalTag in ${targetAppId()}"
    }
    return false
}

private fun MacrobenchmarkScope.clickVisibleTag(
    tag: String,
    timeoutMs: Long = 8_000,
): Boolean {
    if (!waitForOptionalTestTag(tag, timeoutMs)) {
        // A page fling turns its action bar into a compact dock. Reveal it once before treating a
        // route action as absent; this is the common path into BA calendar/catalog after scrolling.
        val compactDock = device.findObject(testTagSelector(COMPACT_BOTTOM_BAR_DOCK)) ?: return false
        runCatching { compactDock.click() }
        device.waitForIdle()
        if (!waitForOptionalTestTag(tag, timeoutMs = 2_000)) return false
    }
    val bounds = device.findObject(testTagSelector(tag))?.visibleBounds ?: return false
    if (bounds.centerY() > (device.displayHeight * 0.88f).toInt()) {
        nudgeVisibleScrollable(forward = true)
    }
    val settled = device.findObject(testTagSelector(tag))?.visibleBounds ?: return false
    device.click(settled.centerX(), settled.centerY())
    device.waitForIdle()
    return true
}

private fun MacrobenchmarkScope.clickTestTag(tag: String) {
    val node = device.findObject(testTagSelector(tag))
        ?: error("Unable to find testTag=$tag in ${targetAppId()}")
    node.click()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.clickTaggedCardHeader(tag: String) {
    val bounds = device.findObject(testTagSelector(tag))?.visibleBounds
        ?: error("Unable to find card testTag=$tag in ${targetAppId()}")
    val inset = minOf(bounds.height() / 4, MAX_HEADER_TAP_INSET_PX)
    device.click(bounds.centerX(), bounds.top + inset)
    device.waitForIdle()
}

private fun MacrobenchmarkScope.scrollTestTagIntoReach(tag: String) {
    val safeTop = (device.displayHeight * SCROLL_SAFE_TOP_FRACTION).toInt()
    val safeBottom = (device.displayHeight * SCROLL_SAFE_BOTTOM_FRACTION).toInt()
    repeat(SCROLL_INTO_REACH_ATTEMPTS) {
        val bounds = device.findObject(testTagSelector(tag))?.visibleBounds
        if (bounds != null &&
            bounds.height() >= MIN_TAPPABLE_HEIGHT_PX &&
            bounds.centerY() in safeTop..safeBottom
        ) {
            device.waitForIdle()
            return
        }
        nudgeVisibleScrollable(forward = bounds == null || bounds.centerY() > safeBottom)
    }
    error("Unable to bring testTag=$tag into reach in ${targetAppId()}")
}

private fun MacrobenchmarkScope.clickBottomBarTab(tag: String) {
    repeat(BOTTOM_BAR_REEXPAND_ATTEMPTS) {
        val tab = device.findObject(testTagSelector(tag))
        if (tab != null && runCatching { tab.click() }.isSuccess) {
            device.waitForIdle()
            return
        }

        val compactDock = device.findObject(testTagSelector(COMPACT_BOTTOM_BAR_DOCK))
        if (compactDock != null) {
            // The shared tag sits on the visual Liquid surface while its clickable semantics can
            // belong to a descendant. UiObject2.click() therefore warns that the tagged node is
            // non-clickable even though tapping its bounds is the correct user interaction. A
            // compact-to-expanded transition also keeps stale semantics around for a few frames;
            // wait for the requested tab instead of burning through every retry during animation.
            val bounds = compactDock.visibleBounds
            device.click(bounds.centerX(), bounds.centerY())
            device.wait(Until.hasObject(testTagSelector(tag)), BOTTOM_BAR_EXPAND_TIMEOUT_MS)
            device.waitForIdle()
        } else {
            nudgeVisibleScrollable(forward = false)
            // A reverse scroll asks the shared chrome controller to expand the bar. Compose can be
            // idle before the 240ms visual transition publishes fresh tab semantics, so wait for
            // this exact destination before the next lookup.
            device.wait(Until.hasObject(testTagSelector(tag)), BOTTOM_BAR_EXPAND_TIMEOUT_MS)
        }
    }
    error("Unable to bring navigation tab testTag=$tag into view in ${targetAppId()}")
}

/**
 * Converts the guide to its rail, exercises it, and converts it back.
 *
 * The conversion writes a *persisted* preference, which makes this the one journey that changes how the
 * app looks after the capture ends. So it restores what it found, in a `finally`: a failure between the
 * two taps would otherwise hand every later journey -- and whoever picks the device up next -- a shape
 * nobody asked for. See the plan's note on journeys leaving device state behind.
 *
 * The toggle keeps one tag in both shapes, so the *row* tag is what confirms the conversion actually
 * happened; waiting on the toggle again would pass either way.
 */
private fun MacrobenchmarkScope.exerciseGuideSidebarAndRestore() {
    if (!waitForOptionalTestTag(BA_STUDENT_GUIDE_SIDEBAR_TOGGLE, timeoutMs = 8_000)) return
    var converted = false
    try {
        clickTestTag(BA_STUDENT_GUIDE_SIDEBAR_TOGGLE)
        converted = waitForOptionalTestTag(BA_STUDENT_GUIDE_SIDEBAR_ROW_SKILLS, timeoutMs = 8_000)
        if (!converted) return
        // A row tap, so the rail's selection change and the content beside it both compose, and a fling
        // so the inset content scrolls under the rail rather than only appearing beside it.
        clickTestTag(BA_STUDENT_GUIDE_SIDEBAR_ROW_SKILLS)
        device.waitForIdle()
        flingVisibleScrollable(times = 1, horizontalFraction = WIDE_SECONDARY_LANE_X)
    } finally {
        if (converted && waitForOptionalTestTag(BA_STUDENT_GUIDE_SIDEBAR_TOGGLE, timeoutMs = 6_000)) {
            clickTestTag(BA_STUDENT_GUIDE_SIDEBAR_TOGGLE)
            // Confirmed by the row going away, for the same reason the conversion is: the toggle is
            // present either way.
            device.wait(Until.gone(testTagSelector(BA_STUDENT_GUIDE_SIDEBAR_ROW_SKILLS)), 6_000)
            device.waitForIdle()
        }
    }
}

private fun MacrobenchmarkScope.exerciseWideLanes() {
    flingVisibleScrollable(times = 1, horizontalFraction = WIDE_PRIMARY_LANE_X)
    flingVisibleScrollable(times = 1, horizontalFraction = WIDE_SECONDARY_LANE_X)
}

private fun MacrobenchmarkScope.flingVisibleScrollable(
    times: Int,
    horizontalFraction: Float = DEFAULT_SCROLL_X,
) {
    val centerX = (device.displayWidth * horizontalFraction).toInt()
    val startY = (device.displayHeight * 0.74f).toInt()
    val endY = (device.displayHeight * 0.34f).toInt()
    repeat(times) {
        device.swipe(centerX, startY, centerX, endY, FLING_STEPS)
        device.waitForIdle()
    }
}

private fun MacrobenchmarkScope.dragVisibleScrollable(times: Int) {
    val centerX = (device.displayWidth * DEFAULT_SCROLL_X).toInt()
    val startY = (device.displayHeight * 0.68f).toInt()
    val endY = (device.displayHeight * 0.47f).toInt()
    repeat(times) {
        device.swipe(centerX, startY, centerX, endY, DRAG_STEPS)
        device.waitForIdle()
    }
}

private fun MacrobenchmarkScope.nudgeVisibleScrollable(forward: Boolean) {
    val centerX = (device.displayWidth * DEFAULT_SCROLL_X).toInt()
    val upperY = (device.displayHeight * 0.40f).toInt()
    val lowerY = (device.displayHeight * 0.66f).toInt()
    val startY = if (forward) lowerY else upperY
    val endY = if (forward) upperY else lowerY
    device.swipe(centerX, startY, centerX, endY, NUDGE_STEPS)
    device.waitForIdle()
}

private fun MacrobenchmarkScope.forceWindowSizeDp(
    widthDp: Int,
    heightDp: Int,
) {
    val densityDpi = deviceDensityDpi()
    forceWindowSize(
        widthPx = widthDp * densityDpi / 160,
        heightPx = heightDp * densityDpi / 160,
    )
}

private fun MacrobenchmarkScope.forceWindowSize(
    widthPx: Int,
    heightPx: Int,
) {
    device.executeShellCommand("wm size ${widthPx}x$heightPx")
    device.waitForIdle()
}

private fun MacrobenchmarkScope.deviceDensityDpi(): Int {
    val output = device.executeShellCommand("wm density")
    val override = Regex("Override density: (\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull()
    val physical = Regex("Physical density: (\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull()
    return override ?: physical
        ?: error("Could not read the device density from wm density: $output")
}

private fun MacrobenchmarkScope.resetWindowSize() {
    device.executeShellCommand("wm size reset")
    device.waitForIdle()
}

private fun MacrobenchmarkScope.launchHomeFromColdStart() {
    pressHome()
    grantRuntimePermissions()
    val launcherComponent = resolveLauncherComponent()
    device.executeShellCommand("am force-stop ${targetAppId()}")
    device.executeShellCommand(
        "am start -W -a android.intent.action.MAIN " +
            "-c android.intent.category.LAUNCHER -n $launcherComponent",
    )
    waitForTestTag(HOME_PAGE_ROOT, timeoutMs = 15_000)
}

private fun MacrobenchmarkScope.resolveLauncherComponent(): String {
    val output = device.executeShellCommand("cmd package resolve-activity --brief ${targetAppId()}")
    return output
        .lineSequence()
        .map(String::trim)
        .lastOrNull { line -> "/" in line }
        ?: error("Unable to resolve launcher activity for ${targetAppId()}: $output")
}

internal fun MacrobenchmarkScope.grantRuntimePermissions(packageName: String = targetAppId()) {
    listOf(
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.POST_PROMOTED_NOTIFICATIONS",
        "android.permission.ACCESS_LOCAL_NETWORK",
        "android.permission.USE_LOOPBACK_INTERFACE",
    ).forEach { permission ->
        device.executeShellCommand("pm grant $packageName $permission >/dev/null 2>&1 || true")
    }
}

private fun MacrobenchmarkScope.waitForTestTag(
    tag: String,
    timeoutMs: Long = 5_000,
) {
    check(device.wait(Until.hasObject(testTagSelector(tag)), timeoutMs)) {
        "Timed out waiting for testTag=$tag in ${targetAppId()}"
    }
    device.waitForIdle()
}

private fun MacrobenchmarkScope.waitForOptionalTestTag(
    tag: String,
    timeoutMs: Long,
): Boolean {
    val found = device.wait(Until.hasObject(testTagSelector(tag)), timeoutMs)
    if (found) device.waitForIdle()
    return found
}

private fun MacrobenchmarkScope.testTagSelector(tag: String): BySelector = By.res(tag)

private fun targetAppId(): String =
    InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: error("targetAppId not passed as instrumentation runner arg")

private const val OPEN_WINDOW_ATTEMPTS = 3
private const val BOTTOM_BAR_REEXPAND_ATTEMPTS = 8
private const val BOTTOM_BAR_EXPAND_TIMEOUT_MS = 3_000L
private const val GESTURE_INJECTION_ATTEMPTS = 2
private const val SCROLL_INTO_REACH_ATTEMPTS = 12
private const val MIN_TAPPABLE_HEIGHT_PX = 40
private const val MAX_HEADER_TAP_INSET_PX = 100
private const val FLING_STEPS = 24
private const val PUSH_ROUTE_MAX_BACK_ATTEMPTS = 2
private const val PUSH_ROUTE_GONE_TIMEOUT_MS = 3_000L
private const val DRAG_STEPS = 72
private const val NUDGE_STEPS = 36
private const val SCROLL_SAFE_TOP_FRACTION = 0.18f
private const val SCROLL_SAFE_BOTTOM_FRACTION = 0.82f
private const val DEFAULT_SCROLL_X = 0.50f
private const val WIDE_PRIMARY_LANE_X = 0.32f
private const val WIDE_SECONDARY_LANE_X = 0.74f
private const val LIQUID_SHEET_DRAG_DISTANCE_FRACTION = 0.14f
private const val LIQUID_SHEET_CONTENT_UPPER_FRACTION = 0.34f
private const val LIQUID_SHEET_CONTENT_LOWER_FRACTION = 0.82f

private const val MAIN_BOTTOM_TAB_HOME = "main_bottom_tab_home"
private const val MAIN_BOTTOM_TAB_OS = "main_bottom_tab_os"
private const val MAIN_BOTTOM_TAB_MCP = "main_bottom_tab_mcp"
private const val MAIN_BOTTOM_TAB_GITHUB = "main_bottom_tab_github"
private const val MAIN_BOTTOM_TAB_BA = "main_bottom_tab_ba"
private const val MAIN_SIDEBAR_TOGGLE = "main_sidebar_toggle"
private const val MAIN_SIDEBAR_ROW_MCP = "main_sidebar_row_mcp"
private const val MAIN_SIDEBAR_ROW_GITHUB = "main_sidebar_row_github"
private const val MAIN_SIDEBAR_ROW_BA = "main_sidebar_row_ba"
private const val MAIN_PAGER_SETTLED_HOME = "main_pager_settled_home"
private const val MAIN_PAGER_SETTLED_OS = "main_pager_settled_os"
private const val MAIN_PAGER_SETTLED_MCP = "main_pager_settled_mcp"
private const val MAIN_PAGER_SETTLED_GITHUB = "main_pager_settled_github"
private const val MAIN_PAGER_SETTLED_BA = "main_pager_settled_ba"
private const val HOME_PAGE_ROOT = "home_page_root"
private const val HOME_SETTINGS_BUTTON = "home_settings_button"
private const val HOME_ABOUT_BUTTON = "home_about_button"
private const val HOME_WEBDAV_CARD = "home_webdav_card"
private const val SETTINGS_PAGE_ROOT = "settings_page_root"
private const val ABOUT_PAGE_ROOT = "about_page_root"
private const val WEBDAV_SYNC_PAGE_ROOT = "webdav_sync_page_root"
private const val OS_PAGE_ROOT = "os_page_root"
private const val OS_SHELL_RUNNER_BUTTON = "os_shell_runner_button"
private const val OS_SHELL_RUNNER_PAGE_ROOT = "os_shell_runner_page_root"
private const val MCP_PAGE_ROOT = "mcp_page_root"
private const val MCP_SKILL_BUTTON = "mcp_skill_button"
private const val MCP_SKILL_PAGE_ROOT = "mcp_skill_page_root"
private const val GITHUB_PAGE_ROOT = "github_page_root"
private const val GITHUB_TRACKED_ITEM_CARD_FIRST = "github_tracked_item_card_first"
private const val GITHUB_TRACKED_ITEM_MORE_BUTTON = "github_tracked_item_more_button"
private const val GITHUB_ACTIONS_MENU_ITEM = "github_actions_menu_item"
private const val GITHUB_ADD_TRACKED_BUTTON = "github_add_tracked_button"
private const val GITHUB_STRATEGY_SHEET_BUTTON = "github_strategy_sheet_button"
private const val GITHUB_IMPORT_MENU_BUTTON = "github_import_menu_button"
private const val GITHUB_IMPORT_TRACKS = "github_import_tracks"
private const val GITHUB_ACTIONS_HISTORY_BUTTON = "github_actions_history_button"
private const val GITHUB_ACTIONS_HISTORY_PAGE_ROOT = "github_actions_history_page_root"
private const val GITHUB_HISTORY_TAB_ACTIONS = "github_history_tab_1"
private const val GITHUB_HISTORY_TAB_TRACKING = "github_history_tab_2"
private const val BA_PAGE_ROOT = "ba_page_root"
private const val BA_COOLDOWN_CARD_FIRST = "ba_cooldown_card_first"
private const val BA_COOLDOWN_ADJUST_BUTTON = "ba_cooldown_adjust_button"
private const val BA_DOCK_OPEN_CALENDAR_POOL = "ba_dock_open_calendar_pool"
private const val BA_CALENDAR_POOL_PAGE_ROOT = "ba_calendar_pool_page_root"
private const val BA_CALENDAR_POOL_TAB_POOL = "ba_calendar_pool_tab_1"
private const val BA_DOCK_OPEN_GUIDE_CATALOG = "ba_dock_open_guide_catalog"
private const val BA_DOCK_DAILY_DONE = "ba_dock_daily_done"
private const val BA_GUIDE_CATALOG_PAGE_ROOT = "ba_guide_catalog_page_root"
private const val BA_GUIDE_CATALOG_ENTRY_FIRST = "ba_guide_catalog_entry_first"
private const val BA_STUDENT_GUIDE_PAGE_ROOT = "ba_student_guide_page_root"
private const val BA_STUDENT_GUIDE_TAB_SKILLS = "ba_student_guide_tab_skills"
private const val BA_STUDENT_GUIDE_SIDEBAR_TOGGLE = "ba_student_guide_sidebar_toggle"
private const val BA_STUDENT_GUIDE_SIDEBAR_ROW_SKILLS = "ba_student_guide_tab_skills_sidebar_row"
private const val BA_STUDENT_GUIDE_TAB_PROFILE = "ba_student_guide_tab_profile"
private const val BA_GUIDE_CATALOG_DOCK_STUDENT = "ba_guide_catalog_dock_student"
private const val BA_GUIDE_CATALOG_DOCK_MEMORY_LOBBY = "ba_guide_catalog_dock_memory_lobby"
private const val BA_GUIDE_CATALOG_DOCK_STUDENT_BGM = "ba_guide_catalog_dock_student_bgm"
private const val BA_GUIDE_CATALOG_DOCK_FAVORITE_BGM = "ba_guide_catalog_dock_favorite_bgm"
private const val BA_GUIDE_CATALOG_STUDENT_BGM_FIRST = "ba_guide_catalog_student_bgm_first"
private const val COMPACT_BOTTOM_BAR_DOCK = "compact_bottom_bar_dock"
private const val LIQUID_SHEET_PANEL = "liquid_sheet_panel"
private const val LIQUID_SHEET_DRAG_REGION = "liquid_sheet_drag_region"
