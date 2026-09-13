package os.kei.ui.page.main.widget.core

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = AppSurfaceCardToggleSemanticsTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppSurfaceCardToggleSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun checkedCardExposesOneEnabledCheckboxActionAndClicksOnce() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppSurfaceCard(
                    role = Role.Checkbox,
                    toggleableState = ToggleableState.On,
                    onClick = { clickCount++ },
                ) {
                    Text("Selected candidate")
                }
            }
        }

        composeRule.onAllNodes(checkboxRole).assertCountEquals(1)
        composeRule.onAllNodes(checkboxRole, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNode(checkboxRole).assertIsOn().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun uncheckedDisabledCardKeepsOneBlockedCheckboxAction() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppSurfaceCard(
                    enabled = false,
                    role = Role.Checkbox,
                    toggleableState = ToggleableState.Off,
                    onClick = { clickCount++ },
                ) {
                    Text("Tracked candidate")
                }
            }
        }

        composeRule.onAllNodes(checkboxRole).assertCountEquals(1)
        composeRule.onAllNodes(checkboxRole, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNode(checkboxRole).assertIsOff().assertIsNotEnabled().performClick()
        composeRule.runOnIdle { assertEquals(0, clickCount) }
    }

    private companion object {
        val checkboxRole =
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.Checkbox,
            )
    }
}

internal class AppSurfaceCardToggleSemanticsTestApp : Application()
