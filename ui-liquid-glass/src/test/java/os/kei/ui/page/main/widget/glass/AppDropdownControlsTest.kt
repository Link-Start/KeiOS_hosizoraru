@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import os.kei.ui.page.main.widget.sheet.SceneBackdropHost
import os.kei.ui.page.main.widget.sheet.SnapshotMenuPanelTestTag
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
