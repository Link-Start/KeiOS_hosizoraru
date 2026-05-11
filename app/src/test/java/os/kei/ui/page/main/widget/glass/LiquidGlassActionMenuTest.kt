package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
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
    application = LiquidGlassActionMenuTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi"
)
class LiquidGlassActionMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectingSubmenuChoiceCallsChoiceAndDismissesMenu() {
        var selectedSort = "update"
        var selectedInterval = "3h"
        var dismissCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Box(
                    modifier = Modifier
                        .size(width = 360.dp, height = 420.dp)
                        .background(Color(0xFFF3F4F6))
                        .padding(24.dp)
                ) {
                    LiquidGlassActionMenu(
                        items = listOf(
                            LiquidGlassActionMenuSubmenuRow(
                                id = "sort",
                                text = "排序",
                                subtitle = "更新优先",
                                submenuItems = listOf(
                                    LiquidGlassActionMenuSingleChoiceRow(
                                        id = "update",
                                        text = "更新优先",
                                        selected = selectedSort == "update",
                                        onClick = { selectedSort = "update" }
                                    ),
                                    LiquidGlassActionMenuSingleChoiceRow(
                                        id = "name",
                                        text = "名称 A-Z",
                                        selected = selectedSort == "name",
                                        onClick = { selectedSort = "name" }
                                    )
                                )
                            ),
                            LiquidGlassActionMenuSubmenuRow(
                                id = "interval",
                                text = "更新间隔",
                                subtitle = "3 小时",
                                submenuItems = listOf(
                                    LiquidGlassActionMenuSingleChoiceRow(
                                        id = "3h",
                                        text = "3 小时",
                                        selected = selectedInterval == "3h",
                                        onClick = { selectedInterval = "3h" }
                                    ),
                                    LiquidGlassActionMenuSingleChoiceRow(
                                        id = "6h",
                                        text = "6 小时",
                                        selected = selectedInterval == "6h",
                                        onClick = { selectedInterval = "6h" }
                                    )
                                )
                            )
                        ),
                        onDismissRequest = { dismissCount += 1 }
                    )
                }
            }
        }

        composeRule.onNode(hasText("排序") and hasClickAction()).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("名称 A-Z").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("名称 A-Z") and hasClickAction()).assertIsDisplayed()
            .performClick()

        assertEquals("name", selectedSort)
        assertEquals(1, dismissCount)

        composeRule.onNode(hasText("更新间隔") and hasClickAction()).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("6 小时").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("6 小时") and hasClickAction()).assertIsDisplayed()
            .performClick()

        assertEquals("6h", selectedInterval)
        assertEquals(2, dismissCount)
    }
}

class LiquidGlassActionMenuTestApp : Application()
