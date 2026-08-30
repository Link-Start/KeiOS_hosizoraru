package os.kei.baselineprofile

import android.content.Intent
import android.service.quicksettings.TileService
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

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = true,
        ) {
            launchHomeFromColdStart()
        }
    }

    @Test
    fun homeAndGitHubInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            flingVisibleScrollable(times = 2)
            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_GITHUB,
                pageTag = GITHUB_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_GITHUB,
            )
            flingVisibleScrollable(times = 2)
        }
    }

    @Test
    fun osPageInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_OS,
                pageTag = OS_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_OS,
            )
            flingVisibleScrollable(times = 3)
        }
    }

    @Test
    fun mcpPageInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_GITHUB,
                pageTag = GITHUB_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_GITHUB,
            )
            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_MCP,
                pageTag = MCP_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_MCP,
            )
            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_HOME,
                pageTag = HOME_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_HOME,
            )
            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_MCP,
                pageTag = MCP_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_MCP,
            )
            flingVisibleScrollable(times = 3)
        }
    }

    @Test
    fun baPageInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_BA,
                pageTag = BA_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_BA,
            )
            flingVisibleScrollable(times = 3)
        }
    }

    /**
     * The BA office's slot cards: the shape the page took when its rows became cards.
     *
     * Three things live here that no other journey reaches, and all three are new since the page was
     * rebuilt around one card per cooldown and per craft slot.
     *
     * **The accordion.** Nine `AppLiquidAccordionCard`s replaced two tall cards, and each one expands and
     * collapses through `appExpandIn`/`appExpandOut` on glass inside a lazy list. Both card kinds are
     * walked because they animate the same accordion but compose different bodies — a cooldown's is a
     * progress bar and two facts, a craft slot's is its node composition — and the first composition of
     * either happens mid-animation, which is where an interpreted class costs a dropped frame.
     *
     * **The pile.** The office list became an edge-stack host, so BA cards now recede, blur and dim into
     * the top edge as they leave. `cardPileInteractions` covers that transform on the OS page; this covers
     * it on a list of *cards with their own glass*, which is the combination the BA page has and the OS
     * page does not.
     *
     * **The craft sheet, reached the way it now is** — through a card's own configure button rather than a
     * row. The sheet itself was already profiled; the path into it was not.
     *
     * No fold to drive any more: the Craft Chamber card became an overview with no disclosure of its own,
     * so the slot cards are always in the list and the journey can go straight at them.
     */
    @Test
    fun baSlotCardInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_BA,
                pageTag = BA_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_BA,
            )

            // A cooldown card, both directions of its accordion, and the editor it can now reach —
            // `BaCafeCooldownEditSheet` had no journey at all, and it is only reachable from inside an
            // expanded card, so the accordion has to be walked to get there.
            scrollTestTagIntoReach(BA_COOLDOWN_CARD_FIRST)
            clickTaggedCardHeader(BA_COOLDOWN_CARD_FIRST)
            waitForTestTag(BA_COOLDOWN_ADJUST_BUTTON, timeoutMs = 15_000)
            // The body opens below the header, so the editor's button lands past the tappable band even
            // though the card itself was in reach when it was tapped — measured at cy 2298 of 2856.
            scrollTestTagIntoReach(BA_COOLDOWN_ADJUST_BUTTON)
            openAndDismissOverlay(
                triggerTag = BA_COOLDOWN_ADJUST_BUTTON,
                panelTag = LIQUID_SHEET_PANEL,
            )
            scrollTestTagIntoReach(BA_COOLDOWN_CARD_FIRST)
            collapseTaggedCard(cardTag = BA_COOLDOWN_CARD_FIRST, bodyTag = BA_COOLDOWN_ADJUST_BUTTON)

            // Then a craft slot card, and the sheet its configure button opens.
            scrollTestTagIntoReach(BA_CRAFT_SLOT_CARD_FIRST)
            clickTaggedCardHeader(BA_CRAFT_SLOT_CARD_FIRST)
            waitForTestTag(BA_CRAFT_SLOT_FIRST, timeoutMs = 15_000)
            scrollTestTagIntoReach(BA_CRAFT_SLOT_FIRST)
            openAndDismissOverlay(
                triggerTag = BA_CRAFT_SLOT_FIRST,
                panelTag = LIQUID_SHEET_PANEL,
            )

            // The list is long enough to scroll now, so the pile actually engages.
            flingVisibleScrollable(times = 3)
            dragSlowly(times = 2)
        }
    }

    /**
     * The release list, which is a page nothing had ever walked.
     *
     * It is reached the way a user reaches it — a tracked card's overflow menu — so the menu's own
     * popup is on the path too. Then the parts that only exist here: the accordion over a lazy list of
     * ten cards with the pile engaged on the *open* ones, the release notes' markdown blocks, and the
     * asset rows, which are the tracked card's own asset row composed on a different surface.
     *
     * Paging is walked because a second page is a fresh fetch into an already-composed list, which is
     * the one path here that is not first-composition.
     */
    @Test
    fun gitHubReleaseListInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_GITHUB,
                pageTag = GITHUB_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_GITHUB,
            )

            scrollTestTagIntoReach(GITHUB_TRACKED_ITEM_MORE_BUTTON)
            clickTestTag(GITHUB_TRACKED_ITEM_MORE_BUTTON)
            waitForTestTag(GITHUB_RELEASE_MENU_ITEM, timeoutMs = 15_000)
            clickTestTag(GITHUB_RELEASE_MENU_ITEM)
            waitForTestTag(GITHUB_RELEASE_PAGE_ROOT, timeoutMs = 20_000)
            device.waitForIdle()

            // Waited for before it is scrolled to, unlike the BA cards [scrollTestTagIntoReach] was
            // written for. Those exist only once a scroll composes them; these arrive over the network
            // and land at the top of the list, so scrolling immediately walks an empty list fourteen
            // times and gives up — which is how a capture died three quarters of the way through on a
            // slow fetch, taking every journey after it with it.
            waitForTestTag(GITHUB_RELEASE_CARD_FIRST, timeoutMs = 45_000)

            // The first card opens itself, so this walks the other direction first and back, which is
            // the accordion exit the page otherwise never plays.
            scrollTestTagIntoReach(GITHUB_RELEASE_CARD_FIRST)
            clickTaggedCardHeader(GITHUB_RELEASE_CARD_FIRST)
            device.waitForIdle()
            clickTaggedCardHeader(GITHUB_RELEASE_CARD_FIRST)
            device.waitForIdle()

            // The pile only does anything once cards move under the top edge.
            flingVisibleScrollable(times = 2)
            dragSlowly(times = 1)

            // A second page: same list, new content, no first composition.
            if (device.findObject(testTagSelector(GITHUB_RELEASE_NEXT_PAGE_BUTTON)) != null) {
                clickTestTag(GITHUB_RELEASE_NEXT_PAGE_BUTTON)
                device.waitForIdle()
            }

            device.pressBack()
            waitForTestTag(GITHUB_PAGE_ROOT, timeoutMs = 15_000)
            device.waitForIdle()
        }
    }

    /**
     * The daily-done template editor, which nothing else can reach.
     *
     * It is not a page and not a sheet on a page: a QS tile long-press starts
     * `BaDailyDoneTemplateActivity` into a translucent window, from a process that is usually dead. So
     * everything on that path — the activity, its window configuration, `SceneBackdropHost`, and the
     * sheet's whole first composition — was being interpreted on a user's first long-press.
     *
     * The switch is toggled deliberately rather than for its own sake: a clean sheet closes straight
     * out, and only a dirty one routes the exit through the unsaved-changes confirmation, which is a
     * second overlay stacked on the first. That pair of exit animations is the part no other journey has.
     *
     * The helpers for this landed without it. They sat here uncalled through two profile captures while
     * the KDoc above claimed the coverage, which is why `theGeneratorCallsEveryHelperItDeclares` exists.
     */
    @Test
    fun baDailyTemplateEditorInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchDailyTemplateFromTileLongPress()

            waitForTestTag(BA_DAILY_TEMPLATE_HEADPAT_SWITCH, timeoutMs = 15_000)
            clickTestTag(BA_DAILY_TEMPLATE_HEADPAT_SWITCH)
            discardTheOpenSheetsEdit()
        }
    }

    /**
     * The activity calendar and pool pages became nav routes, so their first composition now runs
     * inside the push transition instead of behind an activity launch. Nothing had ever profiled
     * them — the shipped profile carried 606 BaCalendarPool* rules and not one for either page
     * composable — which left the whole route path to be interpreted on first entry, mid-animation.
     */
    @Test
    fun baCalendarPoolRouteInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_BA,
                pageTag = BA_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_BA,
            )

            openDockRouteAndReturn(BA_DOCK_OPEN_CALENDAR)
            openDockRouteAndReturn(BA_DOCK_OPEN_POOL)
        }
    }

    /**
     * The Settings route: the most-reached push in the app, and still cold on first entry. Its
     * category pager shares MainLoadedPager, so this also covers the section-switch path there.
     */
    @Test
    fun settingsRouteInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            pushRouteAndReturn(
                entryTag = HOME_SETTINGS_BUTTON,
                pageTag = SETTINGS_PAGE_ROOT,
                returnTag = HOME_PAGE_ROOT,
            )
        }
    }

    /**
     * The GitHub Actions history route. Its tabbed section switch had no profile coverage at all —
     * TabbedPageContentMotion resolved to zero rules — so that path was interpreted on first use,
     * the same gap the calendar and pool pages had before they got a journey.
     */
    @Test
    fun gitHubActionsHistoryRouteInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_GITHUB,
                pageTag = GITHUB_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_GITHUB,
            )
            pushRouteAndReturn(
                entryTag = GITHUB_ACTIONS_HISTORY_BUTTON,
                pageTag = GITHUB_ACTIONS_HISTORY_PAGE_ROOT,
                returnTag = GITHUB_PAGE_ROOT,
            )
        }
    }

    /**
     * The two routes Home pushes besides Settings. About renders the changelog and the component
     * inventory; the WebDAV card opens the sync route. Both were reachable only through paths no
     * journey walked, so every class on them was interpreted on first entry.
     */
    @Test
    fun homeAboutAndWebDavRouteInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            pushRouteAndReturn(
                entryTag = HOME_ABOUT_BUTTON,
                pageTag = ABOUT_PAGE_ROOT,
                returnTag = HOME_PAGE_ROOT,
            )
            pushRouteAndReturn(
                entryTag = HOME_WEBDAV_CARD,
                pageTag = WEBDAV_SYNC_PAGE_ROOT,
                returnTag = HOME_PAGE_ROOT,
            )
        }
    }

    /**
     * The MCP skill route, pushed from the MCP page's action bar. It renders Markdown, which is the
     * most expensive first composition of any pushed route in the app.
     */
    @Test
    fun mcpSkillRouteInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_MCP,
                pageTag = MCP_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_MCP,
            )
            pushRouteAndReturn(
                entryTag = MCP_SKILL_BUTTON,
                pageTag = MCP_SKILL_PAGE_ROOT,
                returnTag = MCP_PAGE_ROOT,
            )
        }
    }

    /**
     * The menu — the whole presentation layer, which had no coverage at all.
     *
     * Every journey before this one walked pages and routes, so not one class in the overlay layer was
     * ever profiled: the menu presentation, the shared overlay host and the shared presentation
     * material were all interpreted the first time a user opened them. That is the worst case for it,
     * and for exactly the reason recorded on the calendar and pool journeys above — a menu composes
     * *inside* its present transition, so an interpreted class there costs a dropped frame rather than
     * a slower launch.
     *
     * The GitHub top-bar menu is the one to use: it routes through `SnapshotWindowListPopup` into the
     * menu presentation and renders a `LiquidGlassActionMenu` inside it, so one tap reaches the menu
     * surface, the action-menu layouts, the dropdown rows, the overlay host and the shared material.
     *
     * It waits on a menu *row*, not on the panel container. A bare `Modifier.testTag` on a container
     * with no other semantics never becomes its own accessibility node, so `SnapshotMenuPanelTestTag`
     * is invisible to UiAutomator — verified by dumping the hierarchy with the menu open, where the
     * rows appear and the panel does not.
     *
     * Sheets are no longer the gap this used to name: [baSlotCardInteractions] opens one and
     * [baDailyTemplateEditorInteractions] opens a second plus an action sheet. What is still uncovered is
     * the BA *account* sheet specifically, which does not open under a synthetic tap on its toolbar
     * action — and an unverified wait here costs a 25-minute run, so it is left alone deliberately.
     */
    @Test
    fun presentationChromeInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_GITHUB,
                pageTag = GITHUB_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_GITHUB,
            )
            openAndDismissOverlay(
                triggerTag = GITHUB_IMPORT_MENU_BUTTON,
                panelTag = GITHUB_IMPORT_TRACKS,
            )
        }
    }

    /**
     * The card pile at a standstill, which a fling never reaches.
     *
     * The other journeys fling, and a fling crosses the stack line so fast that the receding states in
     * between are barely sampled. The pile's transform, its progressive blur and its scrim only run
     * while a card is *part way* into the pile, so a slow drag that parks it there is what gets that
     * code compiled. OS is the page to do it on: it is the most card-dense one in the app.
     */
    @Test
    fun cardPileInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_OS,
                pageTag = OS_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_OS,
            )
            dragSlowly(times = 3)
        }
    }

    /**
     * The shell runner, which stopped being an activity and became a route. That move put its first
     * composition inside the push transition, where an interpreted class costs a dropped frame
     * rather than a slower activity launch — the same trap the calendar and pool pages fell into.
     */
    @Test
    fun osShellRunnerRouteInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_OS,
                pageTag = OS_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_OS,
            )
            pushRouteAndReturn(
                entryTag = OS_SHELL_RUNNER_BUTTON,
                pageTag = OS_SHELL_RUNNER_PAGE_ROOT,
                returnTag = OS_PAGE_ROOT,
            )
        }
    }

    /**
     * The guide catalog and the student guide it opens — together the largest hole this profile had.
     *
     * About a hundred composable files under `student.` resolved to not one rule in the shipped profile,
     * and the reason was mechanical rather than editorial: the only way in is the BA dock's third action,
     * and that action carried no test tag, so no journey could reach either route. Both are pushed routes,
     * which means every class on them was interpreted *inside* a push transition — the trap already
     * recorded on the calendar and pool journey — over a page that then composes a tab pager, a lazy list
     * of entry cards with the pile engaged, and a media-heavy detail with its own pager.
     *
     * The detail half is conditional on purpose. Catalog entries come from a synced dataset, so a device
     * that has not synced shows an empty state, which is a legitimate thing to profile rather than a
     * reason to fail a half-hour run. When entries are present the guide is opened; when they are not, the
     * catalog's own first composition is still collected.
     */
    @Test
    fun baGuideCatalogInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_BA,
                pageTag = BA_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_BA,
            )

            waitForTestTag(BA_DOCK_OPEN_GUIDE_CATALOG, timeoutMs = 15_000)
            clickTestTag(BA_DOCK_OPEN_GUIDE_CATALOG)
            waitForTestTag(BA_GUIDE_CATALOG_PAGE_ROOT, timeoutMs = 25_000)

            // The detail *first*, while the first entry is still where it composed. Scrolling before this
            // is what left `student.page` at 57 rules and `student.section` at zero in the first capture:
            // the entry is a lazy item, so a fling disposes it and takes the only tag on it with it.
            if (waitForOptionalTestTag(BA_GUIDE_CATALOG_ENTRY_FIRST, timeoutMs = 15_000)) {
                scrollTestTagIntoReach(BA_GUIDE_CATALOG_ENTRY_FIRST)
                clickTestTag(BA_GUIDE_CATALOG_ENTRY_FIRST)
                waitForTestTag(BA_STUDENT_GUIDE_PAGE_ROOT, timeoutMs = 30_000)
                flingVisibleScrollable(times = 3)
                // Every one of the guide's tabs, which is the ~290KB of `student.section` and
                // `student.tabcontent` that nothing has ever composed. Tapped rather than swiped: the
                // guide's tab bar is a bar, not a pager, and two horizontal swipes over its content left
                // the page exactly where it was.
                GUIDE_BOTTOM_TABS.forEach { tab ->
                    clickTestTag(tab)
                    flingVisibleScrollable(times = 1)
                }
                device.pressBack()
                waitForTestTag(BA_GUIDE_CATALOG_PAGE_ROOT, timeoutMs = 20_000)
            }

            flingVisibleScrollable(times = 2)
            dragSlowly(times = 1)

            // The catalog's own dock: memory lobby, student BGM, favourite BGM. Tapped rather than
            // swiped for the same reason as the guide's tabs — it is a bar, and a horizontal swipe over
            // the content leaves the page exactly where it was.
            clickDockTab(BA_GUIDE_CATALOG_DOCK_MEMORY_LOBBY)
            flingVisibleScrollable(times = 2)
            clickDockTab(BA_GUIDE_CATALOG_DOCK_STUDENT_BGM)
            flingVisibleScrollable(times = 1)

            // Playing a track, which is the only thing that loads androidx.media3 at all. The BGM
            // *interface* composes on every catalog tab because the mini player is always there, so a
            // capture can look like it covers playback while carrying no ExoPlayer, MediaSession or
            // extractor rules — which is exactly what the capture before this one did.
            if (waitForOptionalTestTag(BA_GUIDE_CATALOG_STUDENT_BGM_FIRST, timeoutMs = 15_000)) {
                scrollTestTagIntoReach(BA_GUIDE_CATALOG_STUDENT_BGM_FIRST)
                clickTestTag(BA_GUIDE_CATALOG_STUDENT_BGM_FIRST)
                // The player prepares off the main thread, so the scrolling below is both coverage and
                // the wall time it needs. Waiting on a tag would only prove the row redrew.
                flingVisibleScrollable(times = 2)
            }

            clickDockTab(BA_GUIDE_CATALOG_DOCK_FAVORITE_BGM)
            flingVisibleScrollable(times = 1)
            clickDockTab(BA_GUIDE_CATALOG_DOCK_STUDENT)

            device.pressBack()
            waitForTestTag(BA_PAGE_ROOT, timeoutMs = 20_000)
            device.waitForIdle()
        }
    }

    /**
     * A tracked card opened, which is where the GitHub page keeps almost all of its content.
     *
     * Every GitHub journey before this one scrolled a list of *collapsed* cards, and a collapsed card
     * composes its header and nothing else. The asset panel, the asset rows, the version sections and the
     * health cards under it — the densest thing on the page, and the part a user actually reads — had no
     * rules at all. Both directions of the accordion are walked for the reason [baSlotCardInteractions]
     * records: the exit animation composes as much as the entry does.
     *
     * The Actions sheet then comes off the same card's overflow, and is conditional because that row only
     * exists for GitHub repository tracks — an F-Droid track first in the sort order would otherwise fail
     * the run rather than skip a step.
     */
    @Test
    fun gitHubTrackedCardInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_GITHUB,
                pageTag = GITHUB_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_GITHUB,
            )

            scrollTestTagIntoReach(GITHUB_TRACKED_ITEM_CARD_FIRST)
            clickTaggedCardHeader(GITHUB_TRACKED_ITEM_CARD_FIRST)
            device.waitForIdle()
            flingVisibleScrollable(times = 1)
            dragSlowly(times = 1)
            scrollTestTagIntoReach(GITHUB_TRACKED_ITEM_CARD_FIRST)
            clickTaggedCardHeader(GITHUB_TRACKED_ITEM_CARD_FIRST)
            device.waitForIdle()

            scrollTestTagIntoReach(GITHUB_TRACKED_ITEM_MORE_BUTTON)
            clickTestTag(GITHUB_TRACKED_ITEM_MORE_BUTTON)
            if (waitForOptionalTestTag(GITHUB_ACTIONS_MENU_ITEM, timeoutMs = 10_000)) {
                clickTestTag(GITHUB_ACTIONS_MENU_ITEM)
                waitForTestTag(LIQUID_SHEET_PANEL, timeoutMs = 25_000)
                dismissTheOpenOverlay(LIQUID_SHEET_PANEL)
            } else {
                device.pressBack()
                device.waitForIdle()
            }
        }
    }

    /**
     * The three sheets the GitHub page's own chrome opens, and the star importer beside them.
     *
     * The track editor is the app's most-used form — adding or editing a tracked repository is the whole
     * point of the page — and it, the strategy sheet and the check-logic sheet were all uncovered, which
     * is roughly 200KB of source composed for the first time under a sheet's present transition. None of
     * the three had a test tag on its trigger before this journey, which is why they had none.
     *
     * The star importer is a separate activity rather than a sheet, so it is proven by the GitHub page
     * going *away* rather than by a tag of its own: it has no page root, and adding one would be a tag
     * that exists only to be waited on.
     */
    @Test
    fun gitHubChromeSheetInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickAndWaitForPage(
                tabTag = MAIN_BOTTOM_TAB_GITHUB,
                pageTag = GITHUB_PAGE_ROOT,
                settledTag = MAIN_PAGER_SETTLED_GITHUB,
            )

            openAndDismissOverlay(
                triggerTag = GITHUB_ADD_TRACKED_BUTTON,
                panelTag = LIQUID_SHEET_PANEL,
            )
            openAndDismissOverlay(
                triggerTag = GITHUB_STRATEGY_SHEET_BUTTON,
                panelTag = LIQUID_SHEET_PANEL,
            )
            openAndDismissOverlay(
                triggerTag = GITHUB_CHECK_LOGIC_SHEET_BUTTON,
                panelTag = LIQUID_SHEET_PANEL,
            )

            clickTestTag(GITHUB_IMPORT_MENU_BUTTON)
            waitForTestTag(GITHUB_IMPORT_STARS, timeoutMs = 15_000)
            clickTestTag(GITHUB_IMPORT_STARS)
            check(device.wait(Until.gone(testTagSelector(GITHUB_PAGE_ROOT)), 20_000)) {
                "Timed out waiting for the star importer to take the window in ${targetAppId()}"
            }
            device.waitForIdle()
            flingVisibleScrollable(times = 2)
            device.pressBack()
            waitForTestTag(GITHUB_PAGE_ROOT, timeoutMs = 20_000)
            device.waitForIdle()
        }
    }

    /**
     * The two windows another app hands KeiOS an intent to open.
     *
     * Both start from a dead process into their own activity, which is the worst case an ART profile can
     * fix: the Compose setup, the window chrome and the flow's first composition all run interpreted while
     * the user is looking at an empty window. `github.share` alone is 50-odd files and had no rules.
     *
     * The share window is driven with the intent a share sheet would send it, and everything after the
     * launch is conditional: resolving a repository is a network round trip, and a run must not fail
     * because GitHub was slow. The resolving state composes either way, which is the part worth having.
     *
     * The *feedback* window is the one deliberately not here. It is `exported="false"`, so
     * [launchActivityFromColdStart] cannot reach it — see that helper for the platform reason — and the
     * only other way in is a button inside an expandable card most of the way down the settings page.
     * That is a three-tag chain for 28KB of source, so it is left for whoever wants it.
     */
    @Test
    fun sharedIntentWindowInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchActivityFromColdStart(
                className = SHARE_IMPORT_ACTIVITY_CLASS,
                arguments =
                    "-a ${Intent.ACTION_SEND} -t text/plain " +
                        "--es ${Intent.EXTRA_TEXT} $SHARE_IMPORT_SAMPLE_URL",
            )
            // The resolving sheet is what always appears; the asset picker is one of several states the
            // flow can settle into, so it is waited for second and softly. Waiting only on the picker is
            // what made the first capture collect the flow's coordinators and none of its UI.
            if (waitForOptionalTestTag(LIQUID_SHEET_PANEL, timeoutMs = 20_000)) {
                waitForOptionalTestTag(GITHUB_SHARE_IMPORT_CANCEL, timeoutMs = 15_000)
                device.pressBack()
                device.waitForIdle()
            } else {
                device.pressBack()
            }
            device.waitForIdle()

            // The other window, which a file manager or a share sheet opens with a KeiOS export. The
            // payload is deliberately an empty document: what is being collected is the window and the
            // import screen's first composition, not a real merge into the user's tracked list.
            launchActivityFromColdStart(
                className = JSON_IMPORT_ACTIVITY_CLASS,
                arguments =
                    "-a ${Intent.ACTION_SEND} -t text/plain " +
                        "--es ${Intent.EXTRA_TEXT} '$JSON_IMPORT_SAMPLE_PAYLOAD'",
            )
            flingVisibleScrollable(times = 1)
            device.pressBack()
            device.waitForIdle()
        }
    }

    /**
     * The tablet and fold navigation shapes: the top tab bar, and the sidebar it converts into.
     *
     * ## Why this forces the geometry instead of requiring a tablet
     *
     * Every other journey here runs in whatever window the device happens to have. These two shapes only exist
     * at `>= 600dp` and `>= 660dp`, so on a phone they would never be compiled into the profile — and a profile
     * generated on a *tablet* has the opposite hole, because the floating bottom bar never renders there.
     * Neither device alone produces a complete profile, and merging two runs is a process step that gets
     * forgotten.
     *
     * So the journey resizes the window itself. `MainActivity` declares `screenSize|screenLayout|smallestScreenSize`
     * in `configChanges`, so this reflows rather than recreating the Activity — the reflow is exactly the code
     * path worth compiling anyway, since a fold does it every time it opens.
     *
     * ## Why the sizes are in dp and why the *short* side matters
     *
     * The first version of this passed physical pixels straight to `wm size`, which made it silently
     * density-dependent: `2560x1600` is 1280x800dp on the Pad AVD at density 320 and 853x533dp on the phone
     * AVD at 480. It failed on the phone AVD with `Unable to find testTag=main_sidebar_toggle`, and the
     * reason is a two-step trap worth stating.
     *
     * `MainActivity` still declares `screenOrientation="sensorPortrait"`, and from targetSdk 36 that request
     * is ignored *only* on a display whose **smallest** width is >= 600dp. At 853x533dp the short side is
     * 533dp, so the request was honoured, the window was flipped to portrait at 533dp wide, the placement
     * fell back to `Bottom`, and the toggle under test never composed. The failure looked like a missing test
     * tag; it was a forced rotation.
     *
     * So both tablet-shaped steps below keep their *short* side past 600dp as well as their long side past
     * 660dp. Getting only the long side right reproduces exactly the bug above.
     *
     * ## Why the geometry is restored in a `finally`
     *
     * `wm size` outlives the process. Leaving a 1280dp override behind would silently invalidate every later
     * journey in the same run and every macrobenchmark on that device afterwards, and the symptom — a phone
     * profile missing its bottom bar — looks like a code problem rather than a leaked shell command.
     */
    @Test
    fun tabletAndFoldNavigationShapes() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            try {
                // Tablet-shaped: long side past the 660dp sidebar floor, short side past the 600dp line that
                // decides whether the manifest's portrait request is ignored. Both, or the window rotates.
                forceWindowSizeDp(widthDp = 1000, heightDp = 800)
                launchHomeFromColdStart()

                // Tab bar shape: the same tab test tags, now in the top row.
                clickAndWaitForPage(
                    tabTag = MAIN_BOTTOM_TAB_OS,
                    pageTag = OS_PAGE_ROOT,
                    settledTag = MAIN_PAGER_SETTLED_OS,
                )
                flingVisibleScrollable(times = 2)

                // Convert to the sidebar, drive it, and convert back — both directions of the morph.
                clickTestTag(MAIN_SIDEBAR_TOGGLE)
                waitForTestTag(MAIN_SIDEBAR_ROW_HOME)
                clickTestTag(MAIN_SIDEBAR_ROW_MCP)
                waitForTestTag(MCP_PAGE_ROOT)
                flingVisibleScrollable(times = 2)
                clickTestTag(MAIN_SIDEBAR_ROW_BA)
                waitForTestTag(BA_PAGE_ROOT)
                clickTestTag(MAIN_SIDEBAR_TOGGLE)

                // A fold opening and closing: the width crosses both thresholds while the app is running.
                // 775dp is past 660dp so the sidebar is still offered; its 800dp short side keeps the window
                // from rotating. 500dp is under both, which is the compact shape and the point of the step —
                // and there the portrait request applies again, which is correct rather than incidental.
                forceWindowSizeDp(widthDp = 775, heightDp = 800) // fold inner
                device.waitForIdle()
                forceWindowSizeDp(widthDp = 500, heightDp = 800) // compact, back to the bottom bar
                device.waitForIdle()

                // Returning to Home here is a *navigation*, not a wait. The sidebar left the app on BA and
                // resizing does not move it, so the previous `waitForHome()` could only ever time out — it
                // was unreachable behind the rotation bug above, and surfaced the moment that was fixed.
                //
                // Going through the tab is also the assertion worth making: at 500dp the bottom bar is the
                // only way to move between sections, so a tap that lands proves the compact shape came back
                // rather than merely that the window resized.
                clickAndWaitForPage(
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

/**
 * Overrides the window size for the rest of the journey, in **dp**.
 *
 * Every threshold this journey exercises is expressed in dp, so the sizes have to be too. Passing pixels
 * instead is what made the first version pass on one AVD and fail on another purely because their densities
 * differ — see the journey's KDoc.
 */
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

/**
 * Overrides the window size for the rest of the journey, in physical pixels — what `wm size` actually takes.
 */
private fun MacrobenchmarkScope.forceWindowSize(
    widthPx: Int,
    heightPx: Int,
) {
    device.executeShellCommand("wm size ${widthPx}x$heightPx")
    device.waitForIdle()
}

/**
 * The device's effective density, so dp can be converted to the pixels `wm size` wants.
 *
 * Prefers an override when one is set: `wm density` reports both, and a device someone has changed the density
 * on would otherwise be measured against a figure it is not using. Fails loudly rather than guessing a default
 * — a silently wrong density is precisely the failure this helper exists to remove.
 */
private fun MacrobenchmarkScope.deviceDensityDpi(): Int {
    val output = device.executeShellCommand("wm density")
    val override = Regex("Override density: (\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull()
    val physical = Regex("Physical density: (\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull()
    return override ?: physical
        ?: error("Could not read the device density from `wm density`, got: $output")
}

/** Returns the window to the device's own size. Must run even when the journey fails. */
private fun MacrobenchmarkScope.resetWindowSize() {
    device.executeShellCommand("wm size reset")
    device.waitForIdle()
}

private fun MacrobenchmarkScope.clickTestTag(tag: String) {
    val node = device.findObject(testTagSelector(tag))
        ?: error("Unable to find testTag=$tag in ${targetAppId()}")
    node.click()
    device.waitForIdle()
}

/** A clipped header reports single-digit pixels; anything that thin is not worth tapping. */
private const val MIN_TAPPABLE_HEIGHT_PX = 40

/**
 * Taps a control that pushes a nav route, exercises the route, then pops back.
 *
 * Both directions matter: the pop replays the covered entry's restore path, which is what a user
 * feels on the way out.
 */
private fun MacrobenchmarkScope.pushRouteAndReturn(
    entryTag: String,
    pageTag: String,
    returnTag: String,
) {
    waitForTestTag(entryTag, timeoutMs = 15_000)
    val entry = device.findObject(testTagSelector(entryTag))
        ?: error("Unable to find testTag=$entryTag in ${targetAppId()}")
    entry.click()
    waitForTestTag(pageTag, timeoutMs = 15_000)
    flingVisibleScrollable(times = 2)

    device.pressBack()
    waitForTestTag(returnTag, timeoutMs = 15_000)
    device.waitForIdle()
}

/**
 * Pushes a route from the BA floating dock, exercises it, then pops back. Both directions matter:
 * the pop replays the covered entry's restore path, which is what a user feels on the way out.
 */
private fun MacrobenchmarkScope.openDockRouteAndReturn(dockTag: String) {
    waitForTestTag(dockTag, timeoutMs = 15_000)
    val action = device.findObject(testTagSelector(dockTag))
        ?: error("Unable to find dock action testTag=$dockTag in ${targetAppId()}")
    action.click()
    device.waitForIdle()
    // The route settles over the push transition; the dock belongs to the covered page and goes away.
    check(device.wait(Until.gone(testTagSelector(dockTag)), 15_000)) {
        "Timed out waiting for the route pushed by testTag=$dockTag in ${targetAppId()}"
    }
    device.waitForIdle()
    flingVisibleScrollable(times = 2)
    device.pressBack()
    waitForTestTag(BA_PAGE_ROOT, timeoutMs = 15_000)
    device.waitForIdle()
}

/**
 * Starts the daily-done template editor from a dead process, exactly as a tile long-press does.
 *
 * `TileService.ACTION_QS_TILE_PREFERENCES` and `Intent.EXTRA_COMPONENT_NAME` are read from the platform
 * rather than restated, so a renamed constant fails to compile instead of failing 20 minutes into a run.
 * The two app class names cannot be — the macrobenchmark module has no view of the app's source set — so
 * they are literals, and `BaselineProfileTestTagContractTest` checks them against the manifest.
 */
private fun MacrobenchmarkScope.launchDailyTemplateFromTileLongPress() {
    pressHome()
    grantRuntimePermissions()
    device.executeShellCommand("am force-stop ${targetAppId()}")
    device.executeShellCommand(
        "am start -W -a ${TileService.ACTION_QS_TILE_PREFERENCES} " +
            "-n ${targetAppId()}/$DAILY_TEMPLATE_ACTIVITY_CLASS " +
            "--ecn ${Intent.EXTRA_COMPONENT_NAME} ${targetAppId()}/$DAILY_TILE_ACCOUNT_SERVICE_CLASS",
    )
    waitForTestTag(LIQUID_SHEET_PANEL, timeoutMs = 15_000)
    device.waitForIdle()
}

/**
 * Leaves an edited sheet through its unsaved-changes confirmation, discarding the edit.
 *
 * Waits on the discard action rather than on the panel, because the sheet underneath still carries
 * [LIQUID_SHEET_PANEL] while the confirmation is up — the panel tag cannot tell the two apart. Both
 * surfaces are then waited *gone*, so the pair of exit animations is collected as well.
 */
private fun MacrobenchmarkScope.discardTheOpenSheetsEdit() {
    device.pressBack()
    waitForTestTag(UNSAVED_SHEET_DISMISS_DISCARD, timeoutMs = 15_000)
    clickTestTag(UNSAVED_SHEET_DISMISS_DISCARD)
    check(device.wait(Until.gone(testTagSelector(LIQUID_SHEET_PANEL)), 15_000)) {
        "Timed out waiting for the edited sheet to dismiss in ${targetAppId()}"
    }
    device.waitForIdle()
}

/**
 * Scrolls until a tagged node is a real target: composed at all, tall enough to hit, and clear of both
 * the pile band under the top bar and the band the floating dock draws over.
 *
 * "Composed at all" is the part that bites, and it is why this no longer waits for the tag first. Each
 * cooldown and craft slot is its own lazy item now, so a card below the fold has no semantics node to
 * wait for — scrolling is what brings it into existence, and waiting first only buys a timeout. Nor can
 * the walk be one-directional: the keep-alive headroom keeps a card composed after it recedes into the
 * pile, where it reports a clipped height beneath the top bar, so an overshoot has to be walked back.
 */
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

/**
 * Taps a card's header rather than the middle of the card.
 *
 * [clickTestTag] clicks a node's centre, which is the header only while the card is collapsed. On an
 * expanded card the centre lands in the body, and the tap is a silent no-op: nothing fails, the card
 * stays open, and the exit animation half of the accordion is never collected. Measured on the BA page,
 * an expanded cooldown card spans 508px against a 216px header.
 */
private fun MacrobenchmarkScope.clickTaggedCardHeader(tag: String) {
    val bounds = device.findObject(testTagSelector(tag))?.visibleBounds
        ?: error("Unable to find card testTag=$tag in ${targetAppId()}")
    // A quarter in from the top stays inside the header of a collapsed card, and the cap keeps a tall
    // expanded card's quarter from reaching past its header into the body.
    val inset = minOf(bounds.height() / 4, MAX_HEADER_TAP_INSET_PX)
    device.click(bounds.centerX(), bounds.top + inset)
    device.waitForIdle()
}

/**
 * Collapses a card and proves it collapsed, by waiting for a control only its body carries.
 *
 * Without the proof a missed header tap reads as success — which is exactly how the collapse half of
 * this journey went uncollected while the test still passed.
 */
private fun MacrobenchmarkScope.collapseTaggedCard(
    cardTag: String,
    bodyTag: String,
) {
    clickTaggedCardHeader(cardTag)
    check(device.wait(Until.gone(testTagSelector(bodyTag)), 15_000)) {
        "Card testTag=$cardTag did not collapse in ${targetAppId()}"
    }
    device.waitForIdle()
}

/** Keeps a header tap inside the header of even a fully expanded card. */
private const val MAX_HEADER_TAP_INSET_PX = 100

/** The pile band under the top bar and the band the floating dock draws over. */
private const val SCROLL_SAFE_TOP_FRACTION = 0.22f
private const val SCROLL_SAFE_BOTTOM_FRACTION = 0.80f

/** Enough nudges to walk the BA page end to end at roughly a quarter of a screen each. */
private const val SCROLL_INTO_REACH_ATTEMPTS = 14

/**
 * Scrolls roughly a quarter of a screen, either way.
 *
 * Deliberately shorter than [flingVisibleScrollable]: a fling exists to collect the scroll path, while
 * this exists to land one card in the tappable band, and a fling overshoots a one-line card.
 */
private fun MacrobenchmarkScope.nudgeVisibleScrollable(forward: Boolean) {
    val centerX = device.displayWidth / 2
    val near = (device.displayHeight * 0.62f).toInt()
    val far = (device.displayHeight * 0.38f).toInt()
    if (forward) {
        device.swipe(centerX, near, centerX, far, 24)
    } else {
        device.swipe(centerX, far, centerX, near, 24)
    }
    device.waitForIdle()
}

/**
 * Opens an overlay from a tagged trigger, lets it settle, and dismisses it with back.
 *
 * Waits for the panel to be *gone* rather than for a fixed delay, so the exit animation is collected
 * too — a dismissal recomposes and re-animates the same surface, and it is what the user feels last.
 */
private fun MacrobenchmarkScope.openAndDismissOverlay(
    triggerTag: String,
    panelTag: String,
) {
    waitForTestTag(triggerTag, timeoutMs = 15_000)
    val trigger = device.findObject(testTagSelector(triggerTag))
        ?: error("Unable to find overlay trigger testTag=$triggerTag in ${targetAppId()}")
    trigger.click()
    waitForTestTag(panelTag, timeoutMs = 15_000)

    device.pressBack()
    check(device.wait(Until.gone(testTagSelector(panelTag)), 15_000)) {
        "Timed out waiting for testTag=$panelTag to dismiss in ${targetAppId()}"
    }
    device.waitForIdle()
}

/**
 * Waits for a tag and reports whether it arrived, instead of failing the run.
 *
 * For steps whose *presence* is data-dependent — a catalog that has not synced, a menu row only GitHub
 * repository tracks carry, a share resolution waiting on the network. A hard wait there turns a
 * legitimate device state into a failure half an hour into a capture, and the alternative of dropping the
 * step loses the coverage on every device that does have the data.
 */
private fun MacrobenchmarkScope.waitForOptionalTestTag(
    tag: String,
    timeoutMs: Long,
): Boolean {
    val arrived = device.wait(Until.hasObject(testTagSelector(tag)), timeoutMs) == true
    device.waitForIdle()
    return arrived
}

/**
 * Taps a catalog dock tab, bringing the dock back if a scroll has put it away.
 *
 * The dock is an `AnimatedCompactBottomBar`: scrolling down collapses it to a pill and the expanded
 * tabs leave the composition entirely, tags and all. So a journey that flings one tab's list and then
 * reaches for the next tab finds nothing — measured on the AVD as the tag count going 1, 0, then 1
 * again on the scroll back. Scrolling back is what restores it, so that is what this does.
 */
private fun MacrobenchmarkScope.clickDockTab(tag: String) {
    repeat(DOCK_REEXPAND_ATTEMPTS) {
        if (device.findObject(testTagSelector(tag)) != null) {
            clickTestTag(tag)
            return
        }
        nudgeVisibleScrollable(forward = false)
    }
    error("Unable to bring dock tab testTag=$tag back into view in ${targetAppId()}")
}

/** Enough backward nudges to undo the flings a tab's own step makes, with room to spare. */
private const val DOCK_REEXPAND_ATTEMPTS = 8

/** Dismisses an open overlay with back, and waits out its exit animation rather than a fixed delay. */
private fun MacrobenchmarkScope.dismissTheOpenOverlay(panelTag: String) {
    device.pressBack()
    check(device.wait(Until.gone(testTagSelector(panelTag)), 15_000)) {
        "Timed out waiting for testTag=$panelTag to dismiss in ${targetAppId()}"
    }
    device.waitForIdle()
}

/**
 * Starts one of the app's other activities from a dead process, the way the system does.
 *
 * `force-stop` first is the whole point: these windows are only ever entered cold — a share sheet hands
 * one an intent and the process starts to receive it — so profiling them from a warm process would
 * collect the cheap path and miss the expensive one.
 *
 * **Exported activities only.** `am start` runs as the shell uid, which on this platform does not hold
 * `START_ANY_ACTIVITY`, so an `exported="false"` activity comes back as
 * `SecurityException: Permission Denial: … not exported from uid …` and this times out. Confirmed
 * against `FeedbackIssueActivity` and `GitHubStarImportActivity`; the star importer is reached through
 * its menu row instead, which is why [gitHubChromeSheetInteractions] taps rather than starts it.
 */
private fun MacrobenchmarkScope.launchActivityFromColdStart(
    className: String,
    arguments: String = "",
) {
    pressHome()
    grantRuntimePermissions()
    device.executeShellCommand("am force-stop ${targetAppId()}")
    device.executeShellCommand(
        "am start -W -n ${targetAppId()}/$className $arguments".trim(),
    )
    check(device.wait(Until.hasObject(By.pkg(targetAppId()).depth(0)), 20_000)) {
        "Timed out waiting for $className to take the window in ${targetAppId()}"
    }
    device.waitForIdle()
}

/**
 * Drags roughly one card at a time, slowly, so cards sit part way into the pile.
 *
 * 120 steps against [flingVisibleScrollable]'s 24: the step count is the whole point, because the
 * receding transform, blur and scrim only run for cards mid-pile and a fling skips straight past them.
 */
private fun MacrobenchmarkScope.dragSlowly(times: Int) {
    val centerX = device.displayWidth / 2
    val startY = (device.displayHeight * 0.68f).toInt()
    val endY = (device.displayHeight * 0.42f).toInt()
    repeat(times) {
        device.swipe(centerX, startY, centerX, endY, 120)
        device.waitForIdle()
    }
}

private fun MacrobenchmarkScope.waitForHome() {
    check(device.wait(Until.hasObject(By.pkg(targetAppId()).depth(0)), 10_000)) {
        "Timed out waiting for target package ${targetAppId()}"
    }
    waitForTestTag(HOME_PAGE_ROOT, timeoutMs = 10_000)
    device.waitForIdle()
}

private const val MAIN_BOTTOM_TAB_HOME = "main_bottom_tab_home"
private const val MAIN_SIDEBAR_TOGGLE = "main_sidebar_toggle"
private const val MAIN_SIDEBAR_ROW_HOME = "main_sidebar_row_home"
private const val MAIN_SIDEBAR_ROW_MCP = "main_sidebar_row_mcp"
private const val MAIN_SIDEBAR_ROW_BA = "main_sidebar_row_ba"
private const val MAIN_BOTTOM_TAB_OS = "main_bottom_tab_os"
private const val MAIN_BOTTOM_TAB_MCP = "main_bottom_tab_mcp"
private const val MAIN_BOTTOM_TAB_GITHUB = "main_bottom_tab_github"
private const val MAIN_BOTTOM_TAB_BA = "main_bottom_tab_ba"
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
private const val BA_PAGE_ROOT = "ba_page_root"
private const val GITHUB_IMPORT_MENU_BUTTON = "github_import_menu_button"

/** A menu row, used as the "menu is open" signal — see [presentationChromeInteractions]. */
private const val GITHUB_IMPORT_TRACKS = "github_import_tracks"
private const val BA_DOCK_OPEN_CALENDAR = "ba_dock_open_calendar"
private const val BA_DOCK_OPEN_POOL = "ba_dock_open_pool"
/** The cafe's headpat card — see [BaselineProfileGenerator.baSlotCardInteractions]. */
private const val BA_COOLDOWN_CARD_FIRST = "ba_cooldown_card_first"

/** The cooldown editor's entry point, inside that card. */
private const val BA_COOLDOWN_ADJUST_BUTTON = "ba_cooldown_adjust_button"

/** The first craft slot's own card, which the journey expands to reach its configure button. */
private const val BA_CRAFT_SLOT_CARD_FIRST = "ba_craft_slot_card_first"

/** The configure button inside that card, which opens the craft sheet. */
private const val BA_CRAFT_SLOT_FIRST = "ba_craft_slot_first"

/**
 * Any sheet's panel, from `LiquidSheetPanelTestTag`. Declared in ui-liquid-glass rather than
 * `KeiOsTestTags`, because the sheet component and not a page owns it.
 */
private const val LIQUID_SHEET_PANEL = "liquid_sheet_panel"

/** One switch in the daily-done template editor — see [BaselineProfileGenerator.baDailyTemplateEditorInteractions]. */
private const val BA_DAILY_TEMPLATE_HEADPAT_SWITCH = "ba_daily_template_headpat_switch"

/**
 * Any sheet's unsaved-changes discard action, from `UnsavedSheetDismissDiscardTestTag`. Component-owned
 * for the same reason [LIQUID_SHEET_PANEL] is: the journey cannot name the sheet it happens to be in.
 */
private const val UNSAVED_SHEET_DISMISS_DISCARD = "unsaved_sheet_dismiss_discard"

/**
 * The two app classes the tile long-press names. Not test tags — see
 * [launchDailyTemplateFromTileLongPress] for why they have to be literals, and
 * `BaselineProfileTestTagContractTest` for what keeps them honest.
 */
private const val DAILY_TEMPLATE_ACTIVITY_CLASS = "os.kei.ui.page.main.ba.BaDailyDoneTemplateActivity"
private const val DAILY_TILE_ACCOUNT_SERVICE_CLASS = "os.kei.core.tile.BaDailyDoneAccountTileService1"
/** The release list page and the handles a journey needs to reach and drive it. */
private const val GITHUB_TRACKED_ITEM_MORE_BUTTON = "github_tracked_item_more_button"
private const val GITHUB_RELEASE_MENU_ITEM = "github_release_menu_item"
private const val GITHUB_RELEASE_PAGE_ROOT = "github_release_page_root"
private const val GITHUB_RELEASE_CARD_FIRST = "github_release_card_first"
private const val GITHUB_RELEASE_NEXT_PAGE_BUTTON = "github_release_next_page_button"

/** The BA dock's guide-catalog action, and the two routes behind it. */
private const val BA_DOCK_OPEN_GUIDE_CATALOG = "ba_dock_open_guide_catalog"
private const val BA_GUIDE_CATALOG_PAGE_ROOT = "ba_guide_catalog_page_root"
private const val BA_GUIDE_CATALOG_ENTRY_FIRST = "ba_guide_catalog_entry_first"

/** The catalog's own dock, and the row on its BGM tab whose whole card is a play button. */
private const val BA_GUIDE_CATALOG_DOCK_STUDENT = "ba_guide_catalog_dock_student"
private const val BA_GUIDE_CATALOG_DOCK_MEMORY_LOBBY = "ba_guide_catalog_dock_memory_lobby"
private const val BA_GUIDE_CATALOG_DOCK_STUDENT_BGM = "ba_guide_catalog_dock_student_bgm"
private const val BA_GUIDE_CATALOG_DOCK_FAVORITE_BGM = "ba_guide_catalog_dock_favorite_bgm"
private const val BA_GUIDE_CATALOG_STUDENT_BGM_FIRST = "ba_guide_catalog_student_bgm_first"
private const val BA_STUDENT_GUIDE_PAGE_ROOT = "ba_student_guide_page_root"

/** The guide's six bottom tabs, in bar order. Walked whole: each one composes a different section set. */
private const val BA_STUDENT_GUIDE_TAB_ARCHIVE = "ba_student_guide_tab_archive"
private const val BA_STUDENT_GUIDE_TAB_SKILLS = "ba_student_guide_tab_skills"
private const val BA_STUDENT_GUIDE_TAB_PROFILE = "ba_student_guide_tab_profile"
private const val BA_STUDENT_GUIDE_TAB_VOICE = "ba_student_guide_tab_voice"
private const val BA_STUDENT_GUIDE_TAB_GALLERY = "ba_student_guide_tab_gallery"
private const val BA_STUDENT_GUIDE_TAB_SIMULATE = "ba_student_guide_tab_simulate"

private val GUIDE_BOTTOM_TABS =
    listOf(
        BA_STUDENT_GUIDE_TAB_ARCHIVE,
        BA_STUDENT_GUIDE_TAB_SKILLS,
        BA_STUDENT_GUIDE_TAB_VOICE,
        BA_STUDENT_GUIDE_TAB_GALLERY,
        BA_STUDENT_GUIDE_TAB_SIMULATE,
        BA_STUDENT_GUIDE_TAB_PROFILE,
    )

/** The GitHub page's own chrome: a card to open, three sheets, and the star importer. */
private const val GITHUB_TRACKED_ITEM_CARD_FIRST = "github_tracked_item_card_first"
private const val GITHUB_ACTIONS_MENU_ITEM = "github_actions_menu_item"
private const val GITHUB_ADD_TRACKED_BUTTON = "github_add_tracked_button"
private const val GITHUB_STRATEGY_SHEET_BUTTON = "github_strategy_sheet_button"
private const val GITHUB_CHECK_LOGIC_SHEET_BUTTON = "github_check_logic_sheet_button"
private const val GITHUB_IMPORT_STARS = "github_import_stars"

/** The share window's cancel, which is the first control its asset picker offers. */
private const val GITHUB_SHARE_IMPORT_CANCEL = "github_share_import_cancel"

/**
 * The other two activities a journey starts by name, checked against the manifest by
 * `BaselineProfileTestTagContractTest` for the reason [launchDailyTemplateFromTileLongPress] records.
 */
private const val SHARE_IMPORT_ACTIVITY_CLASS = "os.kei.ui.page.main.github.share.GitHubShareImportActivity"
private const val JSON_IMPORT_ACTIVITY_CLASS = "os.kei.ui.page.main.jsonimport.KeiOSJsonImportActivity"

/** An empty export, so the import screen composes without changing anything the next journey reads. */
private const val JSON_IMPORT_SAMPLE_PAYLOAD = "{}"

/**
 * A repository the share window can resolve, and that the app is not already tracking.
 *
 * KeiOS's own repository was the obvious choice and the wrong one: an already-tracked repository settles
 * the flow without a decision to make, so the window wrote its result and closed before composing
 * anything. That capture collected the coordinators and the result writer and not one sheet. This is a
 * library rather than an app, so nothing that ships APKs would have it tracked.
 */
private const val SHARE_IMPORT_SAMPLE_URL = "https://github.com/JetBrains/compose-multiplatform"

private const val GITHUB_ACTIONS_HISTORY_BUTTON = "github_actions_history_button"
private const val GITHUB_ACTIONS_HISTORY_PAGE_ROOT = "github_actions_history_page_root"

private fun targetAppId(): String {
    return InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: error("targetAppId not passed as instrumentation runner arg")
}

private fun MacrobenchmarkScope.launchHomeFromColdStart() {
    pressHome()
    grantRuntimePermissions()
    val launcherComponent = resolveLauncherComponent()
    device.executeShellCommand("am force-stop ${targetAppId()}")
    device.executeShellCommand(
        "am start -W -a android.intent.action.MAIN " +
            "-c android.intent.category.LAUNCHER " +
            "-n $launcherComponent",
    )
    waitForHome()
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

private fun MacrobenchmarkScope.testTagSelector(tag: String): BySelector = By.res(tag)

private fun MacrobenchmarkScope.waitForTestTag(
    tag: String,
    timeoutMs: Long = 5_000,
) {
    check(device.wait(Until.hasObject(testTagSelector(tag)), timeoutMs)) {
        "Timed out waiting for testTag=$tag in ${targetAppId()}"
    }
    device.waitForIdle()
}

private fun MacrobenchmarkScope.clickAndWaitForPage(
    tabTag: String,
    pageTag: String,
    settledTag: String,
    timeoutMs: Long = 15_000,
) {
    check(device.wait(Until.hasObject(testTagSelector(tabTag)), timeoutMs)) {
        "Timed out waiting for tab testTag=$tabTag in ${targetAppId()}"
    }
    val node = device.findObject(testTagSelector(tabTag))
        ?: error("Unable to find tab testTag=$tabTag in ${targetAppId()}")
    node.click()
    waitForTestTag(pageTag, timeoutMs)
    waitForTestTag(settledTag, timeoutMs)
    device.waitForIdle()
}

private fun MacrobenchmarkScope.flingVisibleScrollable(times: Int) {
    val centerX = device.displayWidth / 2
    val startY = (device.displayHeight * 0.74f).toInt()
    val endY = (device.displayHeight * 0.34f).toInt()
    repeat(times) {
        device.swipe(centerX, startY, centerX, endY, 24)
        device.waitForIdle()
    }
}
