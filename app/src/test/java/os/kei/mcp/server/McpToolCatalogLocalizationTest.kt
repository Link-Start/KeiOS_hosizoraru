package os.kei.mcp.server

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class McpToolCatalogLocalizationTest {
    @Test
    fun chineseToolDescriptionsCoverEveryRegisteredTool() {
        val englishByName = McpToolCatalog.forLocale(Locale.ENGLISH).associateBy { it.name }
        val chineseTools = McpToolCatalog.forLocale(Locale.SIMPLIFIED_CHINESE)

        assertEquals(McpToolCatalog.all.size, chineseTools.size)
        chineseTools.forEach { tool ->
            assertFalse(
                actual = tool.description == englishByName.getValue(tool.name).description,
                message = "${tool.name} should have a Chinese description"
            )
        }
    }

    @Test
    fun japaneseToolDescriptionsCoverEveryRegisteredTool() {
        val englishByName = McpToolCatalog.forLocale(Locale.ENGLISH).associateBy { it.name }
        val japaneseTools = McpToolCatalog.forLocale(Locale.JAPANESE)

        assertEquals(McpToolCatalog.all.size, japaneseTools.size)
        japaneseTools.forEach { tool ->
            assertFalse(
                actual = tool.description == englishByName.getValue(tool.name).description,
                message = "${tool.name} should have a Japanese description"
            )
        }
    }

    @Test
    fun descriptionLookupUsesRequestedLocale() {
        assertEquals(
            "读取 MCP 运行端点、客户端、Token 状态与最近错误。",
            McpToolCatalog.descriptionFor("keios.mcp.runtime.status", Locale.SIMPLIFIED_CHINESE)
        )
        assertEquals(
            "MCP 実行エンドポイント、クライアント、Token 状態、直近エラーを読み取ります。",
            McpToolCatalog.descriptionFor("keios.mcp.runtime.status", Locale.JAPANESE)
        )
    }

    @Test
    fun githubTrackingDescriptionsDocumentCurrentOptions() {
        val english = McpToolCatalog.descriptionFor(
            "keios.github.tracks.list",
            Locale.ENGLISH
        )
        assertEquals(
            true,
            english.contains("filterMode=all|github_repository|direct_apk")
        )
        assertEquals(true, english.contains("sortMode=update|name|pre_release|changed|added"))

        val chinese = McpToolCatalog.descriptionFor(
            "keios.github.tracks.export",
            Locale.SIMPLIFIED_CHINESE
        )
        assertEquals(true, chinese.contains("keios.github.tracked/v3"))
        assertEquals(true, chinese.contains("订阅项目"))
    }
}
