package os.kei.mcp.server

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.core.privilege.PrivilegedShell
import java.util.Locale
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
@Config(application = McpServerTestApp::class, sdk = [35])
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
    fun nonBaWorkflowBlueprintsProvideJapaneseGuidance() {
        val content = McpWorkflowContent(testEnvironment()).buildWorkflowSkillText(Locale.JAPANESE)

        listOf(
            "# KeiOS MCP ワークフロー",
            "GitHub 更新ウォッチ",
            "GitHub Actions 更新ウォッチ",
            "OS カードのバックアップ",
            "WebDAV 同期診断",
            "手順：",
            "出力：",
        ).forEach { expected -> assertContains(content, expected) }
    }

    @Test
    fun catalogDescriptorsExposeProductInformationArchitecture() {
        val tools = McpToolCatalog.forLocale(Locale.ENGLISH)

        assertEquals(
            true,
            tools.any { it.visibility == McpToolVisibility.Entrypoint }
        )
        assertEquals(
            true,
            tools.any { it.visibility == McpToolVisibility.Workflow }
        )
        tools.forEach { tool ->
            assertEquals(true, tool.title?.isNotBlank() == true, "${tool.name} needs a title")
            assertEquals(true, tool.group.isNotBlank(), "${tool.name} needs a group")
            assertEquals(true, tool.description.isNotBlank(), "${tool.name} needs a description")
            tool.arguments.forEach { argument ->
                assertEquals(
                    true,
                    argument.description.isNotBlank() || !argument.required,
                    "${tool.name}.${argument.name} required arguments need descriptions"
                )
            }
        }
    }

    @Test
    fun githubTrackingDescriptionsDocumentCurrentOptions() {
        val english = McpToolCatalog.descriptionFor(
            "keios.github.tracks.list",
            Locale.ENGLISH
        )
        assertEquals(
            true,
            english.contains("filterMode=all|github_repository|git_repository|direct_apk")
        )
        assertEquals(true, english.contains("sortMode=update|name|pre_release|changed|added"))

        val chinese = McpToolCatalog.descriptionFor(
            "keios.github.tracks.export",
            Locale.SIMPLIFIED_CHINESE
        )
        assertEquals(true, chinese.contains("keios.github.tracked/v4"))
        assertEquals(true, chinese.contains("订阅项目"))
    }

    private fun testEnvironment(): McpToolEnvironment = McpToolEnvironment(
        appContext = ApplicationProvider.getApplicationContext(),
        privilegedShell = PrivilegedShell(),
        appVersionName = "test",
        appVersionCode = 1L,
        appPackageName = "os.kei.test",
        appLabel = "KeiOS",
        stateProvider = { null },
        toolCallLogger = { _, _, _, _, _ -> },
    )
}
