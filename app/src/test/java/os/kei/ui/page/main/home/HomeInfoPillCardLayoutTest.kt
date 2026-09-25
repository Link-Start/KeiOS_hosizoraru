package os.kei.ui.page.main.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.testing.distinctRowCount
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "zh-rCN-w375dp-h817dp-520dpi",
)
class HomeInfoPillCardLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mcpOverviewStaysWithinTwoRows() {
        assertRowLimit(
            labels = listOf("Kei MCP", "运行中", "1h 20m", "局域网", "2", "38888/mcp", "Token 1092...b6a1"),
            maxRows = 2,
        )
    }

    @Test
    fun webDavOverviewStaysWithinTwoRows() {
        assertRowLimit(
            labels = listOf("WebDAV", "自动同步", "数据项 8/9", "自动 07-11 10:42", "全量 07-10 22:10"),
            maxRows = 2,
        )
    }

    @Test
    fun baOverviewStaysWithinTwoRows() {
        assertRowLimit(
            labels = listOf("BA Hoshino", "账号 2/3", "日服", "AP 120/240", "咖啡厅 Lv.10 80/90", "提醒 180+"),
            maxRows = 2,
        )
    }

    @Test
    fun githubOverviewStaysWithinThreeRows() {
        assertRowLimit(
            labels =
                listOf(
                    "版本追踪",
                    "API",
                    "追踪 75",
                    "稳定 3",
                    "预发 1",
                    "Actions 8",
                    "缓存 75",
                    "刷新 3h 12m",
                    "精准版本 4",
                ),
            maxRows = 3,
        )
    }

    private fun assertRowLimit(labels: List<String>, maxRows: Int) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    HomeInfoPillCard(
                        pills = labels.map { HomeCardPillItem(value = it, color = Color(0xFF2563EB)) },
                        naText = "N/A",
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val rowCount = composeRule.distinctRowCount(labels)
        assertTrue(
            actual = rowCount <= maxRows,
            message = "Expected at most $maxRows rows, actual=$rowCount labels=$labels",
        )
    }
}
