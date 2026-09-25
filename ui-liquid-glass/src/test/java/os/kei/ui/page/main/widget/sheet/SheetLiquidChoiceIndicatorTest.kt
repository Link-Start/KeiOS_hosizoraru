package os.kei.ui.page.main.widget.sheet

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class SheetLiquidChoiceIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun indicatorKeepsCompactVisualInsideMinimumTouchTarget() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                SheetLiquidChoiceIndicator(
                    selected = true,
                    onSelect = { clickCount++ },
                    modifier = Modifier.testTag("choice-indicator"),
                )
            }
        }

        composeRule
            .onNodeWithTag("choice-indicator")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performClick()
        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun disabledIndicatorExposesDisabledSelectionSemantics() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                SheetLiquidChoiceIndicator(
                    selected = true,
                    enabled = false,
                    onSelect = { clickCount++ },
                    modifier = Modifier.testTag("disabled-choice-indicator"),
                )
            }
        }

        composeRule
            .onNodeWithTag("disabled-choice-indicator")
            .assertIsSelected()
            .assertIsNotEnabled()
            .performClick()
        composeRule.runOnIdle { assertEquals(0, clickCount) }
    }

    @Test
    fun choiceCardUsesOneRadioButtonAndInvokesSelectionOnce() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                SheetChoiceCard(
                    title = "Local network",
                    summary = "Allow devices on the same network",
                    selected = true,
                    onSelect = { clickCount++ },
                )
            }
        }

        val radioButton =
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.RadioButton,
            )
        composeRule.onAllNodes(radioButton).assertCountEquals(1)
        composeRule.onAllNodes(radioButton, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule
            .onNode(radioButton)
            .assertIsSelected()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun disabledChoiceCardKeepsOneSelectedDisabledRadioAndDoesNotInvokeSelection() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                SheetChoiceCard(
                    title = "Unavailable source",
                    summary = "Selection is temporarily locked",
                    selected = true,
                    enabled = false,
                    onSelect = { clickCount++ },
                    selectedLabel = null,
                )
            }
        }

        val radioButton =
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.RadioButton,
            )
        composeRule.onAllNodes(radioButton).assertCountEquals(1)
        composeRule.onAllNodes(radioButton, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule
            .onNode(radioButton)
            .assertIsSelected()
            .assertIsNotEnabled()
            .performClick()
        composeRule.runOnIdle { assertEquals(0, clickCount) }
    }

    @Test
    fun actionGroupExposesOneGroupAndOneRadioActionPerCard() {
        var firstClickCount = 0
        var secondClickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                SheetActionGroup(modifier = Modifier.selectableGroup()) {
                    SheetChoiceCard(
                        title = "First choice",
                        summary = "Selected option",
                        selected = true,
                        onSelect = { firstClickCount++ },
                    )
                    SheetChoiceCard(
                        title = "Second choice",
                        summary = "Available option",
                        selected = false,
                        onSelect = { secondClickCount++ },
                    )
                }
            }
        }

        val selectableGroup = SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)
        val radioButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        composeRule.onAllNodes(selectableGroup).assertCountEquals(1)
        composeRule.onAllNodes(selectableGroup, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(radioButton).assertCountEquals(2)
        composeRule.onAllNodes(radioButton, useUnmergedTree = true).assertCountEquals(2)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("First choice").assertIsSelected()
        composeRule.onNodeWithText("Second choice").assertIsNotSelected().performClick()
        composeRule.runOnIdle {
            assertEquals(0, firstClickCount)
            assertEquals(1, secondClickCount)
        }
    }

    @Test
    fun standardChoiceCardKeepsDefaultDensity() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                SheetChoiceCard(
                    title = "Local network",
                    summary = "Allow devices on the same network",
                    selected = false,
                    onSelect = {},
                    modifier = Modifier.testTag("standard-choice-card"),
                    selectedLabel = null,
                )
            }
        }

        val cardBounds =
            composeRule
                .onNodeWithTag("standard-choice-card")
                .fetchSemanticsNode()
                .boundsInRoot
        val titleBounds =
            composeRule
                .onNodeWithText("Local network", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val summaryBounds =
            composeRule
                .onNodeWithText("Allow devices on the same network", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        with(composeRule.density) {
            val titleSummaryGap = (summaryBounds.top - titleBounds.bottom).toDp()
            val contentHeight = (titleBounds.height + summaryBounds.height).toDp() + titleSummaryGap
            val expectedCardHeight = contentHeight + 24.dp + 12.dp
            val actualCardHeight = cardBounds.height.toDp()

            assertEquals(8f, titleSummaryGap.value, absoluteTolerance = 0.5f)
            assertEquals(expectedCardHeight.value, actualCardHeight.value, absoluteTolerance = 0.5f)
        }
    }

    @Test
    fun compactChoiceCardKeepsDenseGeometrySlotsAndSingleSelectionAction() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                SheetChoiceCard(
                    title = "A deliberately long application title",
                    summary = "os.kei.example.application",
                    selected = true,
                    onSelect = { clickCount++ },
                    modifier = Modifier.testTag("compact-choice-card"),
                    pressSafePadding = 4.dp,
                    selectedLabel = null,
                    density = SheetChoiceCardDensity.Compact,
                    leading = {
                        Box(Modifier.size(32.dp).testTag("compact-leading"))
                    },
                    trailing = {
                        Box(Modifier.size(width = 50.dp, height = 18.dp).testTag("compact-trailing"))
                    },
                    showIndicator = false,
                )
            }
        }

        composeRule
            .onNodeWithTag("compact-choice-card")
            .assertHeightIsEqualTo(72.dp)
        composeRule
            .onNodeWithTag("compact-leading", useUnmergedTree = true)
            .assertWidthIsEqualTo(32.dp)
            .assertHeightIsEqualTo(32.dp)
        composeRule
            .onNodeWithTag("compact-trailing", useUnmergedTree = true)
            .assertWidthIsEqualTo(50.dp)
            .assertHeightIsEqualTo(18.dp)

        val titleBounds =
            composeRule
                .onNodeWithText("A deliberately long application title", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val summaryBounds =
            composeRule
                .onNodeWithText("os.kei.example.application", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val titleSummaryGap =
            with(composeRule.density) {
                (summaryBounds.top - titleBounds.bottom).toDp()
            }
        assertEquals(2.dp, titleSummaryGap)

        val radioButton =
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.RadioButton,
            )
        composeRule.onAllNodes(radioButton).assertCountEquals(1)
        composeRule.onAllNodes(radioButton, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNode(radioButton).performClick()
        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun indicatorUsesOnlyAnEnabledParentBackdrop() {
        var expectedBackdrop: Backdrop? = null
        var resolvedBackdrop: Backdrop? = null
        var disabledBackdrop: Backdrop? = null
        var standaloneBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val parentBackdrop = rememberLayerBackdrop()
                expectedBackdrop = parentBackdrop
                CompositionLocalProvider(LocalLiquidParentBackdrop provides parentBackdrop) {
                    resolvedBackdrop = resolvedSheetChoiceIndicatorBackdrop()
                    CompositionLocalProvider(LocalLiquidControlsEnabled provides false) {
                        disabledBackdrop = resolvedSheetChoiceIndicatorBackdrop()
                    }
                }
                standaloneBackdrop = resolvedSheetChoiceIndicatorBackdrop()
            }
        }

        composeRule.runOnIdle {
            assertSame(expectedBackdrop, resolvedBackdrop)
            assertNull(disabledBackdrop)
            assertNull(standaloneBackdrop)
        }
    }
}
