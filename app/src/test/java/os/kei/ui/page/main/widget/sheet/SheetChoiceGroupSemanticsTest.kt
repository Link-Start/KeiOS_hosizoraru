package os.kei.ui.page.main.widget.sheet

import android.app.Application
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = SheetChoiceGroupSemanticsTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class SheetChoiceGroupSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

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

        composeRule.onAllNodes(SELECTABLE_GROUP).assertCountEquals(1)
        composeRule.onAllNodes(SELECTABLE_GROUP, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(RADIO_BUTTON).assertCountEquals(2)
        composeRule.onAllNodes(RADIO_BUTTON, useUnmergedTree = true).assertCountEquals(2)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("First choice").assertIsSelected()
        composeRule.onNodeWithText("Second choice").assertIsNotSelected().performClick()
        composeRule.runOnIdle {
            assertEquals(0, firstClickCount)
            assertEquals(1, secondClickCount)
        }
    }

    private companion object {
        val SELECTABLE_GROUP =
            SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)
        val RADIO_BUTTON =
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    }
}

internal class SheetChoiceGroupSemanticsTestApp : Application()
