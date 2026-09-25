package os.kei.mcp.server

import android.app.Application
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
@Config(application = Application::class, sdk = [35])
class McpToolCatalogLocalizationTest {
    @Test
    fun translatedToolDescriptionsCoverEveryRegisteredTool() {
        val englishByName = McpToolCatalog.forLocale(Locale.ENGLISH).associateBy { it.name }

        listOf(Locale.SIMPLIFIED_CHINESE, Locale.JAPANESE).forEach { locale ->
            val translatedTools = McpToolCatalog.forLocale(locale)

            assertEquals(McpToolCatalog.all.size, translatedTools.size, "$locale tool count")
            translatedTools.forEach { tool ->
                assertFalse(
                    actual = tool.description == englishByName.getValue(tool.name).description,
                    message = "${tool.name} should have a $locale description"
                )
            }
        }
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
