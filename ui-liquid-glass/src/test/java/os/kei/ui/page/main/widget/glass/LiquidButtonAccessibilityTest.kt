package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
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
class LiquidButtonAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactIconAndTextButtonsKeepFortyEightDpInteractionBounds() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Column {
                    AppLiquidIconButton(
                        backdrop = null,
                        icon = MiuixIcons.Basic.Check,
                        contentDescription = "Confirm",
                        onClick = {},
                        variant = GlassVariant.Compact,
                        modifier = Modifier.testTag("compact-icon-button"),
                    )
                    AppLiquidTextButton(
                        backdrop = null,
                        text = "Confirm",
                        onClick = {},
                        variant = GlassVariant.Compact,
                        modifier = Modifier.testTag("compact-text-button"),
                    )
                }
            }
        }

        listOf("compact-icon-button", "compact-text-button").forEach { tag ->
            composeRule
                .onNodeWithTag(tag)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertHasClickAction()
        }
    }

    @Test
    fun iconButtonClickAndDisabledTextButtonUseStandardButtonSemantics() {
        var iconClicks = 0
        var disabledClicks = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Column {
                    AppLiquidIconButton(
                        backdrop = null,
                        icon = MiuixIcons.Basic.Check,
                        contentDescription = "Confirm",
                        onClick = { iconClicks++ },
                        modifier = Modifier.testTag("enabled-icon-button"),
                    )
                    AppLiquidTextButton(
                        backdrop = null,
                        text = "Unavailable",
                        onClick = { disabledClicks++ },
                        enabled = false,
                        modifier = Modifier.testTag("disabled-text-button"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("enabled-icon-button").performClick()
        composeRule
            .onNodeWithTag("disabled-text-button")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertIsNotEnabled()

        assertEquals(1, iconClicks)
        assertEquals(0, disabledClicks)
    }

    @Test
    fun textButtonCanExposeRadioSelectionWithoutChangingItsInteractionBounds() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppLiquidTextButton(
                    backdrop = null,
                    text = "Selected choice",
                    onClick = { clickCount++ },
                    role = Role.RadioButton,
                    selected = true,
                    variant = GlassVariant.Compact,
                    modifier = Modifier.testTag("radio-text-button"),
                )
            }
        }

        val radioRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        composeRule.onAllNodes(radioRole).assertCountEquals(1)
        composeRule
            .onNodeWithTag("radio-text-button")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(radioRole)
            .assertIsSelected()
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun standaloneTextButtonForwardsCheckboxToggleState() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppStandaloneLiquidTextButton(
                    text = "Enabled filter",
                    onClick = { clickCount++ },
                    role = Role.Checkbox,
                    toggleableState = ToggleableState.On,
                    buttonModifier = Modifier.testTag("checkbox-standalone-button"),
                    variant = GlassVariant.Compact,
                    pressSafePadding = 0.dp,
                )
            }
        }

        val checkboxRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        composeRule.onAllNodes(checkboxRole).assertCountEquals(1)
        composeRule
            .onNodeWithTag("checkbox-standalone-button")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(checkboxRole)
            .assertIsOn()
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun standaloneGroupKeepsCompactControlsAccessibleAndIndependent() {
        var iconClicks = 0
        var textClicks = 0
        var textSurfaceWidth = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppStandaloneLiquidBackdropGroup(
                    modifier = Modifier.width(160.dp),
                ) {
                    Column {
                        AppStandaloneLiquidIconButton(
                            icon = MiuixIcons.Basic.Check,
                            contentDescription = "Confirm",
                            onClick = { iconClicks++ },
                            buttonModifier = Modifier.testTag("standalone-icon-button"),
                            width = 36.dp,
                            height = 36.dp,
                            variant = GlassVariant.Compact,
                            pressSafePadding = 0.dp,
                        )
                        AppStandaloneLiquidTextButton(
                            text = "Confirm",
                            onClick = { textClicks++ },
                            buttonModifier = Modifier.testTag("standalone-text-button"),
                            surfaceModifier =
                                Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { textSurfaceWidth = it.width },
                            variant = GlassVariant.Compact,
                            pressSafePadding = 0.dp,
                        )
                        AppStandaloneLiquidTextButton(
                            text = "Unavailable",
                            onClick = {},
                            buttonModifier = Modifier.testTag("standalone-disabled-button"),
                            enabled = false,
                            variant = GlassVariant.Compact,
                            pressSafePadding = 0.dp,
                        )
                    }
                }
            }
        }

        listOf("standalone-icon-button", "standalone-text-button").forEach { tag ->
            composeRule
                .onNodeWithTag(tag)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertHasClickAction()
                .performClick()
        }
        composeRule
            .onNodeWithTag("standalone-disabled-button")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertIsNotEnabled()

        assertEquals(1, iconClicks)
        assertEquals(1, textClicks)
        assertTrue(textSurfaceWidth >= with(composeRule.density) { 160.dp.roundToPx() })
    }
}
