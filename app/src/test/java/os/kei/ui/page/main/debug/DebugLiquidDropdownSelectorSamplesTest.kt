package os.kei.ui.page.main.debug

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.emptyBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.sheet.SceneBackdropHost
import os.kei.ui.page.main.widget.sheet.SnapshotMenuPanelTestTag
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = DebugLiquidDropdownSelectorSamplesTestApp::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class DebugLiquidDropdownSelectorSamplesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun menuPanelWidth(): Dp =
        with(composeRule.density) {
            composeRule
                .onNodeWithTag(SnapshotMenuPanelTestTag)
                .fetchSemanticsNode()
                .boundsInRoot
                .width
                .toDp()
        }

    @Test
    fun largeFontKeepsFullAndSplitAnchorsInsideThe324DpMatrix() {
        setProductionMatrix()
        val context = ApplicationProvider.getApplicationContext<Application>()
        listOf(
            DEBUG_LIQUID_STANDARD_SELECTOR_TAG,
            DEBUG_LIQUID_MULTILINGUAL_SELECTOR_TAG,
            DEBUG_LIQUID_SCROLL_SELECTOR_TAG,
        ).forEach { tag ->
            composeRule
                .onNodeWithTag(tag)
                .assertWidthIsEqualTo(324.dp)
        }
        listOf(
            context.getString(R.string.debug_component_lab_liquid_selector_option_balanced),
            context.getString(R.string.debug_component_lab_liquid_selector_long_zh),
            context.getString(
                R.string.debug_component_lab_liquid_selector_scroll_option,
                DebugLiquidDropdownStressSelectedIndex + 1,
            ),
        ).forEach { label ->
            composeRule
                .onNode(hasText(label) and buttonRoleMatcher)
                .assertHeightIsAtLeast(48.dp)
        }

        listOf(
            DEBUG_LIQUID_DISABLED_SELECTOR_TAG,
            DEBUG_LIQUID_EMPTY_SELECTOR_TAG,
        ).forEach { tag ->
            composeRule
                .onNodeWithTag(tag)
                .assertWidthIsEqualTo(157.dp)
        }
        listOf(
            context.getString(R.string.debug_component_lab_liquid_selector_disabled),
            context.getString(R.string.debug_component_lab_liquid_selector_empty),
        ).forEach { label ->
            composeRule
                .onNode(hasText(label) and buttonRoleMatcher)
                .assertHeightIsAtLeast(48.dp)
                .assertIsNotEnabled()
        }
        composeRule.onAllNodesWithTag(SnapshotMenuPanelTestTag).assertCountEquals(0)
    }

    @Test
    fun multilingualPopupMatchesItsAnchorAndKeepsEveryLongLabelInsideTheWindow() {
        setProductionMatrix()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val longLabels =
            listOf(
                context.getString(R.string.debug_component_lab_liquid_selector_long_zh),
                context.getString(R.string.debug_component_lab_liquid_selector_long_en),
                context.getString(R.string.debug_component_lab_liquid_selector_long_ja),
            )
        val selectorAnchor = composeRule.onNodeWithTag(DEBUG_LIQUID_MULTILINGUAL_SELECTOR_TAG)
        val anchorBounds =
            selectorAnchor
                .assertWidthIsEqualTo(324.dp)
                .fetchSemanticsNode()
                .boundsInRoot
        composeRule
            .onAllNodes(hasText(longLabels.first()), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .forEach { textNode ->
                assertTrue(textNode.boundsInRoot.left >= anchorBounds.left)
                assertTrue(textNode.boundsInRoot.right <= anchorBounds.right)
            }

        composeRule
            .onNode(hasText(longLabels.first()) and buttonRoleMatcher)
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(radioRoleMatcher).fetchSemanticsNodes().size == longLabels.size
        }

        longLabels.forEach { label ->
            composeRule
                .onNode(hasText(label) and radioRoleMatcher)
                .assertIsDisplayed()
        }
        // Measured off the panel's own node. This used to look for a second Compose root, which existed
        // only while the panel had a window to itself; it is hosted in the activity window now.
        val panelWidth = menuPanelWidth()
        assertEquals(324.dp, panelWidth, "Expected panel width to match the 324dp anchor")
        assertTrue(panelWidth <= 360.dp, "Panel escaped the window: $panelWidth")

        composeRule
            .onNode(hasText(longLabels[1]) and radioRoleMatcher)
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(radioRoleMatcher).fetchSemanticsNodes().isEmpty()
        }
        composeRule
            .onNode(hasText(longLabels[1]) and buttonRoleMatcher)
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNodeWithTag(DEBUG_LIQUID_MULTILINGUAL_SELECTOR_TAG)
            .assertWidthIsEqualTo(324.dp)
        composeRule.onAllNodesWithTag(SnapshotMenuPanelTestTag).assertCountEquals(0)
    }

    @Test
    fun sixteenItemPopupScrollsToTheLateSelectionAndClosesAfterChoosingItsNeighbor() {
        setProductionMatrix()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val selectedLabel =
            context.getString(
                R.string.debug_component_lab_liquid_selector_scroll_option,
                DebugLiquidDropdownStressSelectedIndex + 1,
            )
        val neighborLabel =
            context.getString(
                R.string.debug_component_lab_liquid_selector_scroll_option,
                DebugLiquidDropdownStressSelectedIndex,
            )

        composeRule
            .onNode(hasText(selectedLabel) and buttonRoleMatcher)
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(radioRoleMatcher).fetchSemanticsNodes().size ==
                DebugLiquidDropdownStressOptionCount
        }

        composeRule.onAllNodes(selectedRadioMatcher).assertCountEquals(1)
        composeRule
            .onNode(hasText(selectedLabel) and radioRoleMatcher)
            .assertIsSelected()
            .assertIsDisplayed()
        composeRule
            .onNode(hasText(neighborLabel) and radioRoleMatcher)
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(radioRoleMatcher).fetchSemanticsNodes().isEmpty()
        }
        composeRule
            .onNode(hasText(neighborLabel) and buttonRoleMatcher)
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNodeWithTag(DEBUG_LIQUID_SCROLL_SELECTOR_TAG)
            .assertWidthIsEqualTo(324.dp)
        composeRule.onAllNodesWithTag(SnapshotMenuPanelTestTag).assertCountEquals(0)
    }

    private fun setProductionMatrix() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                LocalTransitionAnimationsEnabled provides false,
            ) {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    // The anchored panel portals into the overlay host now, so the harness has to provide
                    // one. Without it the portal composes the panel in place, inside the 324dp sample
                    // column, and the panel inherits that width instead of resolving its own.
                    SceneBackdropHost {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            DebugLiquidProductionDropdownSelectorSamples(
                                backdrop = emptyBackdrop(),
                                modifier = Modifier.width(324.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val buttonRoleMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        val radioRoleMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val selectedRadioMatcher =
            radioRoleMatcher and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
    }
}

class DebugLiquidDropdownSelectorSamplesTestApp : Application()
