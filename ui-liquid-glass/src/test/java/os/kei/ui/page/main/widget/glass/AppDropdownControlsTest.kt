@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import os.kei.ui.page.main.widget.sheet.SceneBackdropHost
import os.kei.ui.page.main.widget.sheet.LocalSceneBackdrop
import os.kei.ui.page.main.widget.chrome.LiquidToolbarTextButton
import os.kei.ui.page.main.widget.chrome.LiquidToolbarPopupAnchors
import os.kei.ui.page.main.widget.sheet.SnapshotMenuPanelTestTag
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppDropdownControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun internallyOwnedAnchorDoesNotRecomposeItsCardAndOpensAtItsMovedPosition() {
        val position = mutableIntStateOf(0)
        var expanded by mutableStateOf(false)
        var selected by mutableIntStateOf(0)
        var cardCompositions = 0
        composeRule.setContent {
            DropdownTestTheme {
                SideEffect { cardCompositions++ }
                AppDropdownSelector(
                    selectedText = "Owned level",
                    options = listOf("One", "Two"),
                    selectedIndex = selected,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onSelectedIndexChange = { selected = it },
                    modifier = Modifier.testTag("owned-anchor").offset { IntOffset(0, position.intValue) },
                )
            }
        }
        composeRule.waitForIdle()
        val before = cardCompositions
        repeat(6) { index ->
            composeRule.runOnIdle { position.intValue = (index + 1) * 20 }
            composeRule.waitForIdle()
        }
        assertEquals(before, cardCompositions)
        val anchor = composeRule.onNodeWithTag("owned-anchor").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithText("Owned level").performClick()
        composeRule.waitForIdle()
        val panel = composeRule.onNodeWithTag(SnapshotMenuPanelTestTag).fetchSemanticsNode().boundsInRoot
        assertTrue(panel.top >= anchor.bottom - 1f, "owned menu must open at the moved button")
        composeRule.onNode(hasText("Two") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        assertEquals(1, selected)
        assertEquals(false, expanded)
        assertEquals(0, composeRule.onAllNodesWithTag(SnapshotMenuPanelTestTag).fetchSemanticsNodes().size)
    }

    @Test
    fun rawPopupObservesItsProviderThroughExitThenReleasesItAndCallsDismissFinished() {
        var show by mutableStateOf(false)
        var bounds by mutableStateOf(IntRect(32, 120, 192, 168))
        var reads = 0
        var dismissals = 0
        composeRule.setContent {
            DropdownTestTheme {
                SnapshotWindowListPopup(
                    show = show,
                    alignment = PopupPositionProvider.Align.BottomEnd,
                    placement = SnapshotPopupPlacement.ButtonEnd,
                    anchorBoundsProvider = { reads++; bounds },
                    onDismissFinished = { dismissals++ },
                    minWidth = 80.dp,
                ) { Text("Deferred raw menu") }
            }
        }
        composeRule.runOnIdle { bounds = IntRect(32, 240, 192, 288) }
        composeRule.waitForIdle()
        assertEquals(0, reads)
        composeRule.runOnIdle { show = true }
        composeRule.waitForIdle()
        assertTrue(reads > 0)
        val panel = composeRule.onNodeWithTag(SnapshotMenuPanelTestTag).fetchSemanticsNode().boundsInRoot
        assertTrue(panel.top >= bounds.bottom - 1f)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { show = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        val exitReads = reads
        composeRule.runOnIdle { bounds = IntRect(32, 252, 192, 300) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertTrue(reads > exitReads, "raw menu must retain its live anchor during exit")
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()
        assertEquals(1, dismissals)
        val finishedReads = reads
        composeRule.runOnIdle { bounds = IntRect(32, 264, 192, 312) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertEquals(finishedReads, reads)
        assertEquals(0, composeRule.onAllNodesWithTag(SnapshotMenuPanelTestTag).fetchSemanticsNodes().size)
    }

    @Test
    fun movingToolbarSlotsDoNotRecomposeClosedMenuContent() {
        val position = mutableIntStateOf(0)
        var show by mutableStateOf(false)
        var slotCompositions = 0
        var reads = 0
        var presentedBounds: IntRect? = null
        composeRule.setContent {
            DropdownTestTheme {
                Box(Modifier.offset { IntOffset(0, position.intValue) }) {
                    LiquidToolbarPopupAnchors(itemCount = 3) { index, anchor ->
                        SideEffect { slotCompositions++ }
                        if (index == 1) {
                            SnapshotWindowListPopup(
                                show = show,
                                anchorBoundsProvider = { reads++; anchor().also { presentedBounds = it } },
                                alignment = PopupPositionProvider.Align.BottomEnd,
                                placement = SnapshotPopupPlacement.ButtonEnd,
                            ) { Text("Toolbar menu") }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val before = slotCompositions
        repeat(6) { index ->
            composeRule.runOnIdle { position.intValue = (index + 1) * 20 }
            composeRule.waitForIdle()
        }
        assertEquals(before, slotCompositions)
        assertEquals(0, reads)
        composeRule.runOnIdle { show = true }
        composeRule.waitForIdle()
        val panel = composeRule.onNodeWithTag(SnapshotMenuPanelTestTag).fetchSemanticsNode().boundsInRoot
        assertTrue(panel.top >= requireNotNull(presentedBounds).bottom - 1f)
    }

    @Test
    fun movingClosedSelectorsDoNotRecomposeTheirCardsWithDeferredBounds() {
        val position = mutableIntStateOf(0)
        val compositions = mutableMapOf<String, Int>()
        composeRule.setContent {
            DropdownTestTheme {
                Row {
                    MovingSelectorCard("eager", position, deferred = false) {
                        compositions["eager"] = (compositions["eager"] ?: 0) + 1
                    }
                    MovingSelectorCard("deferred", position, deferred = true) {
                        compositions["deferred"] = (compositions["deferred"] ?: 0) + 1
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val before = compositions.toMap()
        repeat(6) { index ->
            composeRule.runOnIdle { position.intValue = (index + 1) * 12 }
            composeRule.waitForIdle()
        }
        assertTrue(compositions.getValue("eager") > before.getValue("eager"))
        assertEquals(before.getValue("deferred"), compositions.getValue("deferred"))
    }

    @Test
    fun deferredBoundsAreCurrentWhenOpenedAndTrackedUntilExitFinishes() {
        val position = mutableIntStateOf(0)
        var expanded by mutableStateOf(false)
        var bounds by mutableStateOf<IntRect?>(null)
        var providerReads = 0
        composeRule.setContent {
            DropdownTestTheme {
                AppDropdownSelector(
                    selectedText = "Level",
                    options = listOf("One", "Two"),
                    selectedIndex = 0,
                    expanded = expanded,
                    anchorBounds = null,
                    anchorBoundsProvider = { providerReads++; bounds },
                    onExpandedChange = { expanded = it },
                    onSelectedIndexChange = {},
                    onAnchorBoundsChange = { bounds = it },
                    modifier = Modifier.offset { IntOffset(0, position.intValue) },
                )
            }
        }
        composeRule.waitForIdle()
        val initialBounds = requireNotNull(bounds)
        composeRule.runOnIdle { position.intValue = 120 }
        composeRule.waitForIdle()
        assertEquals(0, providerReads)
        assertEquals(initialBounds.top + 120, requireNotNull(bounds).top)
        composeRule.onNodeWithText("Level").performClick()
        composeRule.waitForIdle()
        assertTrue(providerReads > 0)
        val panelBounds = composeRule.onNodeWithTag(SnapshotMenuPanelTestTag)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(panelBounds.top >= requireNotNull(bounds).bottom - 1f,
            "menu must open below the current anchor, not its pre-scroll position")

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { expanded = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        val readsAtExitStart = providerReads
        composeRule.runOnIdle { position.intValue = 132 }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertTrue(providerReads > readsAtExitStart, "exit animation must still track its anchor")

        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag(SnapshotMenuPanelTestTag).fetchSemanticsNodes().size)
        val afterExit = providerReads
        composeRule.runOnIdle { position.intValue = 144 }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertEquals(afterExit, providerReads, "closed menu must release the coordinate-state observation")
    }

    @Test
    fun emptyOptionsCollapseExpandedStateAndDisableAnchor() {
        val expandedChanges = mutableListOf<Boolean>()

        composeRule.setContent {
            DropdownTestTheme {
                var expanded by remember { mutableStateOf(true) }
                AppDropdownSelector(
                    selectedText = "No choices",
                    options = emptyList(),
                    selectedIndex = 7,
                    expanded = expanded,
                    anchorBounds = null,
                    onExpandedChange = { nextExpanded ->
                        expandedChanges += nextExpanded
                        expanded = nextExpanded
                    },
                    onSelectedIndexChange = {},
                    onAnchorBoundsChange = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No choices").assertIsNotEnabled()
        // Used to assert a single Compose root, which proved no popup window had opened. The panel is
        // hosted in the activity window now, so there is always exactly one root and that assertion would
        // pass vacuously — check for the panel itself instead.
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(SnapshotMenuPanelTestTag).fetchSemanticsNodes().size,
        )
        assertEquals(listOf(false), expandedChanges)
    }

    @Test
    fun outOfRangeSelectionKeepsChoicesUsableAndClosesAfterSelection() {
        val selections = mutableListOf<Int>()
        var expandedState = true

        composeRule.setContent {
            DropdownTestTheme {
                var expanded by remember { mutableStateOf(true) }
                expandedState = expanded
                AppDropdownSelector(
                    selectedText = "Unknown",
                    options = listOf("First", "Second"),
                    selectedIndex = Int.MAX_VALUE,
                    expanded = expanded,
                    anchorBounds = null,
                    onExpandedChange = { expanded = it },
                    onSelectedIndexChange = { selections += it },
                    onAnchorBoundsChange = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("Second") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule
            .onNode(hasText("First") and hasClickAction())
            .assertIsNotSelected()
        composeRule
            .onNode(hasText("Second") and hasClickAction())
            .assertIsNotSelected()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("First") and hasClickAction()).fetchSemanticsNodes().isEmpty()
        }

        assertEquals(listOf(1), selections)
        assertEquals(false, expandedState)
    }

    @Test
    fun popupMaxHeightCapsTallChoiceListGeometry() {
        val popupMaxHeight = 112.dp

        composeRule.setContent {
            DropdownTestTheme {
                AppDropdownSelector(
                    selectedText = "Selected",
                    options = List(10) { index -> "Option ${index + 1}" },
                    selectedIndex = 0,
                    expanded = true,
                    anchorBounds = null,
                    onExpandedChange = {},
                    onSelectedIndexChange = {},
                    onAnchorBoundsChange = {},
                    popupMaxHeight = popupMaxHeight,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("Option 1") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        // Measured off the panel's own node. This used to find "the tallest Compose root", which worked
        // only because the panel had a window to itself; it is now hosted in the activity window, where
        // the single root is the whole screen.
        val panelHeight: Dp =
            with(composeRule.density) {
                composeRule
                    .onNodeWithTag(SnapshotMenuPanelTestTag)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .height
                    .toDp()
            }

        assertTrue(panelHeight > 0.dp, "Expected a measured panel, got $panelHeight")
        assertTrue(
            panelHeight <= popupMaxHeight,
            "Expected panel height <= $popupMaxHeight, was $panelHeight",
        )
    }

    @Test
    fun customPopupMinWidthKeepsCompactSelectorGeometry() {
        val popupMinWidth = 136.dp

        composeRule.setContent {
            DropdownTestTheme {
                AppDropdownSelector(
                    selectedText = "A",
                    options = listOf("A", "B"),
                    selectedIndex = 0,
                    expanded = true,
                    anchorBounds = null,
                    onExpandedChange = {},
                    onSelectedIndexChange = {},
                    onAnchorBoundsChange = {},
                    popupMinWidth = popupMinWidth,
                    popupMaxWidth = 196.dp,
                    dropdownItemVariant = GlassVariant.SheetAction,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("B") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        val panelWidth: Dp =
            with(composeRule.density) {
                composeRule
                    .onNodeWithTag(SnapshotMenuPanelTestTag)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .width
                    .toDp()
            }

        assertEquals(popupMinWidth, panelWidth)
    }

    @Test
    fun toolbarAnchorFollowsAvailabilityAndClosesAfterChoosingAnAction() {
        var options by mutableStateOf(emptyList<String>())
        var selected = -1
        var expandedState = false
        composeRule.setContent {
            DropdownTestTheme {
                var expanded by remember { mutableStateOf(false) }
                expandedState = expanded
                val backdrop = LocalSceneBackdrop.current
                AppDropdownSelector(
                    selectedText = "Actions",
                    options = options,
                    selectedIndex = selected,
                    expanded = expanded,
                    anchorBounds = null,
                    onExpandedChange = { expanded = it },
                    onSelectedIndexChange = { selected = it },
                    onAnchorBoundsChange = {},
                    anchorContent = { enabled, onClick ->
                        LiquidToolbarTextButton(backdrop, "Actions", onClick, enabled = enabled)
                    },
                )
            }
        }
        composeRule.onNodeWithText("Actions").assertIsNotEnabled()
        composeRule.runOnIdle { options = listOf("First", "Second") }
        composeRule.onNodeWithText("Actions").performClick()
        composeRule.onNodeWithText("Second").performClick()
        composeRule.waitForIdle()
        assertEquals(1, selected)
        assertEquals(false, expandedState)
        assertEquals(0, composeRule.onAllNodesWithTag(SnapshotMenuPanelTestTag).fetchSemanticsNodes().size)
    }
}

@Composable
private fun MovingSelectorCard(
    tag: String,
    position: MutableIntState,
    deferred: Boolean,
    onComposed: () -> Unit,
) {
    var bounds by remember { mutableStateOf<IntRect?>(null) }
    SideEffect(onComposed)
    AppDropdownSelector(
        selectedText = "Level",
        options = listOf("One", "Two"),
        selectedIndex = 0,
        expanded = false,
        anchorBounds = if (deferred) null else bounds,
        anchorBoundsProvider = if (deferred) ({ bounds }) else null,
        onExpandedChange = {},
        onSelectedIndexChange = {},
        onAnchorBoundsChange = { bounds = it },
        modifier = Modifier.testTag(tag).offset { IntOffset(0, position.intValue) },
    )
}

@Composable
private fun DropdownTestTheme(content: @Composable () -> Unit) {
    MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
        // The anchored panel portals into the overlay host, so the harness has to provide one. Without
        // it the portal falls back to composing in place, inside the anchor's own layout, and the panel
        // inherits the anchor's width instead of resolving its own.
        SceneBackdropHost(content = content)
    }
}
