package os.kei.mcp.server

import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The GitHub tracking options a client is told about must be the ones the tools advertise.
 *
 * The option values come from the tools' own argument schemas (the `enum` every MCP client receives), not from
 * a copied list, so adding or dropping a source or filter mode fails here until the tool descriptions in every
 * shipped locale and every shipped SKILL asset say so. The F-Droid source mode is the drift this was written
 * for: the schema and the descriptions offered `fdroid_repository` while the SKILL assets never mentioned it.
 */
class McpGitHubTrackingOptionsDocumentationTest {
    @Test
    fun trackingOptionsAreDocumentedInEveryDescriptionAndSkillAsset() {
        val options = trackingOptionValues()
        assertEquals(TRACKING_OPTIONS.toSet(), options.keys, "tracking options advertised by the catalog")

        val problems = mutableListOf<String>()
        DESCRIPTION_LOCALES.forEach { locale ->
            options.forEach { (option, values) ->
                val listings =
                    McpToolCatalog.forLocale(locale).mapNotNull { tool ->
                        descriptionValues(tool.description, option)?.let { tool.name to it }
                    }
                if (listings.isEmpty()) {
                    problems += "$locale: no tool description lists $option=…"
                }
                listings.forEach { (tool, documented) ->
                    if (documented != values) {
                        problems +=
                            "$locale $tool $option: missing ${values - documented}, stale ${documented - values}"
                    }
                }
            }
        }
        SKILL_ASSETS.forEach { asset ->
            val bullets = skillBullets(File(SKILL_ASSET_DIR, asset).readTextOrFail())
            options.forEach { (option, values) ->
                val bullet = bullets.singleOrNull { "`$option`" in it }
                if (bullet == null) {
                    problems += "$asset: expected exactly one bullet documenting `$option`"
                    return@forEach
                }
                val tokens = backtickedTokens(bullet)
                val optionsInBullet = options.filterKeys { it in tokens }
                val stale = tokens - optionsInBullet.keys - optionsInBullet.values.flatten().toSet()
                if (!tokens.containsAll(values) || stale.isNotEmpty()) {
                    problems += "$asset $option: missing ${values - tokens}, stale $stale"
                }
            }
        }

        assertTrue(problems.isEmpty(), problems.joinToString(separator = "\n"))
    }

    /** Every enum the tracking tools advertise for each option, and a check that the tools agree on it. */
    private fun trackingOptionValues(): Map<String, Set<String>> {
        val byOption = mutableMapOf<String, MutableSet<Set<String>>>()
        McpToolCatalog.all
            .filter { it.name.startsWith(TRACKING_TOOL_PREFIX) }
            .flatMap { it.arguments }
            .filter { it.name in TRACKING_OPTIONS && it.enumValues.isNotEmpty() }
            .forEach { argument -> byOption.getOrPut(argument.name) { mutableSetOf() } += argument.enumValues.toSet() }
        return byOption.mapValues { (option, variants) ->
            variants.singleOrNull() ?: fail("tracking tools disagree on $option: $variants")
        }
    }

    /** The values after `option=` in a description, up to the next argument separator. */
    private fun descriptionValues(
        description: String,
        option: String,
    ): Set<String>? {
        val match = Regex("""\b$option=([a-z_]+(?:\|[a-z_]+)*)""").find(description) ?: return null
        return match.groupValues[1].split('|').toSet()
    }

    /** Top-level `- ` bullets with their indented continuation lines folded in. */
    private fun skillBullets(markdown: String): List<String> {
        val bullets = mutableListOf<StringBuilder>()
        markdown.lineSequence().forEach { line ->
            when {
                line.startsWith("- ") -> bullets += StringBuilder(line)
                line.startsWith("  ") && bullets.isNotEmpty() -> bullets.last().append(' ').append(line.trim())
                else -> bullets += StringBuilder()
            }
        }
        return bullets.map { it.toString() }.filter { it.startsWith("- ") }
    }

    private fun backtickedTokens(text: String): Set<String> =
        Regex("`([^`]+)`").findAll(text).map { it.groupValues[1] }.toSet()

    private fun File.readTextOrFail(): String {
        if (!isFile) fail("Unable to locate $path from ${File("").absolutePath}")
        return readText()
    }

    private companion object {
        const val TRACKING_TOOL_PREFIX = "keios.github.tracks."
        val TRACKING_OPTIONS = listOf("sourceMode", "filterMode", "sortMode", "sortDirection")
        val DESCRIPTION_LOCALES = listOf(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE, Locale.JAPANESE)
        const val SKILL_ASSET_DIR = "src/main/assets/mcp"
        val SKILL_ASSETS = listOf("SKILL.md", "SKILL.zh-CN.md", "SKILL.ja.md")
    }
}
