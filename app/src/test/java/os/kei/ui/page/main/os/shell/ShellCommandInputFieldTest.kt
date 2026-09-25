package os.kei.ui.page.main.os.shell

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class ShellCommandInputFieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeFontKeepsLegacy136DpPanelAndEightDpPromptGap() {
        setShellContent(
            value = "",
            fontScale = 1.5f,
        )

        composeRule
            .onNodeWithTag(PANEL_TAG)
            .assertWidthIsEqualTo(360.dp)
            .assertHeightIsEqualTo(136.dp)
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("$", useUnmergedTree = true).assertCountEquals(1)

        val panelBounds = composeRule.onNodeWithTag(PANEL_TAG).fetchSemanticsNode().boundsInRoot
        val promptBounds =
            composeRule
                .onNodeWithText("$", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val field = composeRule.onNode(hasSetTextAction(), useUnmergedTree = true)
        val fieldBounds = field.fetchSemanticsNode().boundsInRoot

        field.assertHeightIsEqualTo(112.dp)
        assertDpDistance(promptBounds.left - panelBounds.left, 14.dp)
        assertDpDistance(fieldBounds.left - promptBounds.right, 8.dp)
        assertDpDistance(fieldBounds.top - panelBounds.top, 12.dp)
        assertDpDistance(panelBounds.right - fieldBounds.right, 14.dp)
        assertEquals(
            AnnotatedString(""),
            field.fetchSemanticsNode().config[SemanticsProperties.EditableText],
        )
    }

    @Test
    fun multilineCommandStaysInsideThe112DpFieldAndExcludesThePromptFromItsValue() {
        val command = (1..24).joinToString(separator = "\n") { index -> "echo line-$index" }
        setShellContent(value = command)

        val panelBounds = composeRule.onNodeWithTag(PANEL_TAG).fetchSemanticsNode().boundsInRoot
        val field = composeRule.onNode(hasSetTextAction(), useUnmergedTree = true)
        val fieldBounds = field.fetchSemanticsNode().boundsInRoot
        val semantics = field.fetchSemanticsNode().config

        composeRule.onNodeWithTag(PANEL_TAG).assertHeightIsEqualTo(136.dp)
        field.assertHeightIsEqualTo(112.dp)
        assertTrue(fieldBounds.top >= panelBounds.top)
        assertTrue(fieldBounds.bottom <= panelBounds.bottom)
        assertEquals(AnnotatedString(command), semantics[SemanticsProperties.EditableText])
        assertTrue(semantics.contains(SemanticsProperties.TextSelectionRange))
        assertTrue(semantics.contains(SemanticsActions.SetText))
        assertTrue('$' !in semantics[SemanticsProperties.EditableText].text)
    }

    @Test
    fun positiveFocusTokenStillFocusesTheSharedTextInput() {
        setShellContent(
            value = "getprop",
            focusRequestToken = 1,
        )

        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction(), useUnmergedTree = true).assertIsFocused()
    }

    private fun setShellContent(
        value: String,
        fontScale: Float = 1f,
        focusRequestToken: Int = 0,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale),
            ) {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    ShellCommandInputField(
                        value = value,
                        onValueChange = {},
                        label = "Enter a command",
                        focusRequestToken = focusRequestToken,
                        modifier =
                            Modifier
                                .width(360.dp)
                                .testTag(PANEL_TAG),
                    )
                }
            }
        }
    }

    private fun assertDpDistance(
        actualPx: Float,
        expected: Dp,
    ) {
        val actual = with(composeRule.density) { actualPx.toDp() }
        assertTrue(
            abs(actual.value - expected.value) <= 0.75f,
            "Expected $expected, got $actual",
        )
    }
}

private const val PANEL_TAG = "shell-command-input-panel"
