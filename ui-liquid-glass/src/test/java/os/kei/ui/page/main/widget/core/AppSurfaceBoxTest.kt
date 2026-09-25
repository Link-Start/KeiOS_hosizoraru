package os.kei.ui.page.main.widget.core

import android.app.Application
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppSurfaceBoxTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun boxSurfacePreservesClickLongClickAndStateSemantics() {
        var clickCount = 0
        var longClickCount = 0

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppSurfaceBox(
                    pressSafePadding = 0.dp,
                    onClick = { clickCount++ },
                    onLongClick = { longClickCount++ },
                    stateDescription = "Available",
                ) {
                    BasicText("Surface action")
                }
            }
        }

        composeRule
            .onNodeWithText("Surface action")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Available"))
            .performClick()
            .performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals(1, clickCount)
            assertEquals(1, longClickCount)
        }
    }

    @Test
    fun boxSurfaceForwardsRadioButtonRoleAndSelectionState() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppSurfaceBox(
                    pressSafePadding = 0.dp,
                    onClick = {},
                    role = Role.RadioButton,
                    selected = true,
                ) {
                    BasicText("Selected surface")
                }
            }
        }

        composeRule
            .onNodeWithText("Selected surface")
            .assertHasClickAction()
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }
}
