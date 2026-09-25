package os.kei.ui.page.main.settings.section

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.settings.state.SettingsWebDavSyncUiState
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
class SettingsWebDavSyncSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchResultShowsOneActionWithoutRepeatedTitleOrCollapseControl() {
        var clicks = 0

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                SettingsWebDavSyncSection(
                    state = SettingsWebDavSyncUiState(configured = true, username = "kei"),
                    onClick = { clicks++ },
                    enabledCardColor = Color.White,
                    disabledCardColor = Color.LightGray,
                    expanded = false,
                    onExpandedChange = {},
                    isSearchResult = true,
                )
            }
        }

        composeRule.onAllNodesWithText("WebDAV Sync").assertCountEquals(1)
        composeRule.onAllNodesWithText("Open WebDAV sync settings").assertCountEquals(1)
        composeRule.onAllNodesWithText("Active").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Expand").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Collapse").assertCountEquals(0)

        val navigationAction = composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true)[0]
        navigationAction.performClick()

        composeRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }
}
