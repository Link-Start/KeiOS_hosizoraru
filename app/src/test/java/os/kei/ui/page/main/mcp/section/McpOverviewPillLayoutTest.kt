package os.kei.ui.page.main.mcp.section

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.mcp.state.McpOverviewPills
import os.kei.ui.page.main.widget.core.AppOverviewPill
import os.kei.ui.testing.boundsOf
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = McpOverviewPillLayoutTestApp::class,
    sdk = [35],
    qualifiers = "zh-rCN-w375dp-h817dp-520dpi",
)
class McpOverviewPillLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun networkAndClientsStayOnFirstRowWithLongServiceName() {
        val service = "一个明显比默认值更长的自定义 MCP 服务名称"
        val endpoint = "127.0.0.1:38888/mcp"
        val network = "仅本机"
        val clients = "0"
        val token = "Token 1092...b6a1"

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    McpOverviewCardSection(
                        backdrop = null,
                        titleColor = Color.Black,
                        overviewCardColor = Color(0x1AC62828),
                        overviewBorderColor = Color(0x42C62828),
                        overviewAccentColor = Color(0xFFC62828),
                        runtimeText = "0m",
                        isDark = false,
                        running = false,
                        overviewPills =
                            McpOverviewPills(
                                service = pill(service),
                                endpoint = pill(endpoint),
                                network = pill(network),
                                clients = pill(clients),
                                token = pill(token),
                            ),
                        onToggleServer = {},
                        onOpenEditSheet = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val serviceBounds = composeRule.boundsOf(service)
        val networkBounds = composeRule.boundsOf(network)
        val clientBounds = composeRule.boundsOf(clients)
        val endpointBounds = composeRule.boundsOf(endpoint)
        val tokenBounds = composeRule.boundsOf(token)
        val statusBounds = composeRule.boundsOf("未运行")
        val tolerancePx = with(composeRule.density) { 2.dp.toPx() }

        assertSameRow(serviceBounds, networkBounds, tolerancePx)
        assertSameRow(networkBounds, clientBounds, tolerancePx)
        assertSameRow(clientBounds, statusBounds, tolerancePx)
        assertSameRow(endpointBounds, tokenBounds, tolerancePx)
        assertTrue(serviceBounds.right <= networkBounds.left)
        assertTrue(networkBounds.right <= clientBounds.left)
        assertTrue(clientBounds.right <= statusBounds.left)
        assertTrue(endpointBounds.right <= tokenBounds.left)
        assertTrue(endpointBounds.top > serviceBounds.bottom)
        assertTrue(
            composeRule
                .onAllNodesWithText("MCP Server", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    private fun pill(label: String) = AppOverviewPill(label = label, color = Color(0xFF2563EB))

    private fun assertSameRow(first: Rect, second: Rect, tolerancePx: Float) {
        assertTrue(
            actual = abs(first.center.y - second.center.y) <= tolerancePx,
            message = "Expected pills on the same row, first=$first second=$second",
        )
    }
}

class McpOverviewPillLayoutTestApp : Application()
