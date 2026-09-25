package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppFloatingSearchDockAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collapsedDockRemovesZeroWidthTextInputAndKeepsButtonWidth() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppFloatingSearchDock(
                    backdrop = null,
                    expanded = false,
                    query = "hidden query",
                    onQueryChange = {},
                    onExpandedChange = {},
                    searchIcon = MiuixIcons.Basic.Check,
                    contentDescription = "Search",
                    placeholder = "Search field",
                    modifier = Modifier.testTag("search-dock"),
                )
            }
        }

        composeRule
            .onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onNodeWithTag("search-dock").assertWidthIsEqualTo(62.dp)
    }

    @Test
    fun expandedDockUsesItsParentWidth() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Box(modifier = Modifier.width(300.dp)) {
                    AppFloatingSearchDock(
                        backdrop = null,
                        expanded = true,
                        query = "",
                        onQueryChange = {},
                        onExpandedChange = {},
                        searchIcon = MiuixIcons.Basic.Check,
                        contentDescription = "Search",
                        placeholder = "Search field",
                        showActionWhenExpanded = false,
                        modifier = Modifier.testTag("search-dock"),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search-dock").assertWidthIsEqualTo(300.dp)
    }

    @Test
    fun expandedFieldOnlyDockRemovesTheSearchActionFromAccessibility() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Box(modifier = Modifier.width(300.dp)) {
                    AppFloatingSearchDock(
                        backdrop = null,
                        expanded = true,
                        query = "",
                        onQueryChange = {},
                        onExpandedChange = {},
                        searchIcon = MiuixIcons.Basic.Check,
                        contentDescription = "Search",
                        placeholder = "Search field",
                        showActionWhenExpanded = false,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule
            .onAllNodesWithContentDescription("Search", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun fieldRestoresFocusAndInputAfterExpansionThenLeavesSemanticsDuringExit() {
        lateinit var expandedState: MutableState<Boolean>
        lateinit var queryState: MutableState<String>
        var queryChanges = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                expandedState = remember { mutableStateOf(value = false) }
                queryState = remember { mutableStateOf("") }
                AppFloatingSearchDock(
                    backdrop = null,
                    expanded = expandedState.value,
                    query = queryState.value,
                    onQueryChange = { value ->
                        queryChanges++
                        queryState.value = value
                    },
                    onExpandedChange = { expandedState.value = it },
                    searchIcon = MiuixIcons.Basic.Check,
                    contentDescription = "Search",
                    placeholder = "Search field",
                    modifier = Modifier.testTag("search-dock"),
                )
            }
        }

        composeRule.runOnIdle { expandedState.value = true }
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).assertIsFocused().performTextInput("liquid")
        composeRule.waitForIdle()
        assertEquals("liquid", queryState.value)
        assertEquals(1, queryChanges)

        composeRule.runOnIdle { expandedState.value = false }
        composeRule.waitForIdle()
        composeRule
            .onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .assertCountEquals(0)
        assertEquals(1, queryChanges)

        composeRule.onNodeWithTag("search-dock").assertWidthIsEqualTo(62.dp)
    }

    @Test
    fun compactVerticalDockRestoresFocusedFieldAndHidesItWhenCollapsed() {
        lateinit var expandedState: MutableState<Boolean>
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                expandedState = remember { mutableStateOf(value = true) }
                AppFloatingVerticalSearchActionDock(
                    backdrop = null,
                    expanded = expandedState.value,
                    query = "",
                    onQueryChange = {},
                    onExpandedChange = { expandedState.value = it },
                    searchIcon = MiuixIcons.Basic.Check,
                    searchContentDescription = "Search",
                    placeholder = "Vertical search field",
                    addIcon = MiuixIcons.Basic.Check,
                    addContentDescription = "Add",
                    onAddClick = {},
                    refreshIcon = MiuixIcons.Basic.Check,
                    refreshContentDescription = "Refresh",
                    onRefreshClick = {},
                    compact = true,
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).assertIsFocused()

        composeRule.runOnIdle { expandedState.value = false }
        composeRule.waitForIdle()
        composeRule
            .onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .assertCountEquals(0)
    }
}
