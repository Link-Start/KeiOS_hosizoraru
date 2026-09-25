package os.kei.ui.page.main.github.actions

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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
    application = GitHubActionsSelectableSemanticsTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class GitHubActionsSelectableSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedCardExposesOneSelectedRadioActionAndClicksOnce() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                GitHubActionsSelectableCard(
                    selected = true,
                    isDark = false,
                    onClick = { clickCount++ },
                ) {
                    Text("Workflow")
                }
            }
        }

        composeRule.onAllNodes(radioRole).assertCountEquals(1)
        composeRule.onAllNodes(radioRole, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNode(radioRole).assertIsSelected().performClick()
        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun unselectedCardReportsItsSelectionState() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                GitHubActionsSelectableCard(
                    selected = false,
                    isDark = false,
                    onClick = {},
                ) {
                    Text("Branch")
                }
            }
        }

        composeRule.onNode(radioRole).assertIsNotSelected()
    }

    @Test
    fun collapsibleHeaderExposesOneExpandAction() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                GitHubActionsCollapsibleSection(
                    title = "Workflows",
                    summary = "Selected workflow",
                    countLabel = "3",
                    expanded = false,
                    isDark = false,
                    onExpandedChange = {},
                ) {}
            }
        }

        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    private companion object {
        val radioRole =
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.RadioButton,
            )
    }
}

internal class GitHubActionsSelectableSemanticsTestApp : Application()
