package os.kei.ui.page.main.mcp.section

import org.junit.Test
import os.kei.mcp.server.McpServerUiState
import os.kei.mcp.server.McpToolCatalog
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpToolSectionsTest {
    @Test
    fun toolBucketsKeepUserFacingGroupsSeparate() {
        val state = McpServerUiState(tools = McpToolCatalog.forLocale(Locale.SIMPLIFIED_CHINESE))

        val buckets = mcpToolBuckets(state, searchQuery = "")

        assertTrue(buckets.baTools.any { it.name == "keios.ba.snapshot" })
        assertTrue(buckets.githubTools.any { it.name == "keios.github.config.snapshot" })
        assertTrue(buckets.codexTools.any { it.name == "keios.dev.codex.config" })
        assertTrue(buckets.workflowTools.any { it.name == "keios.mcp.workflow.blueprints" })
        assertEquals(emptySet(), duplicateBucketNames(buckets))
    }

    @Test
    fun toolBucketsApplySearchAcrossDomains() {
        val state = McpServerUiState(tools = McpToolCatalog.forLocale(Locale.SIMPLIFIED_CHINESE))

        val buckets = mcpToolBuckets(state, searchQuery = "codex")

        assertTrue(buckets.codexTools.isNotEmpty())
        assertEquals(emptyList(), buckets.baTools)
        assertEquals(emptySet(), duplicateBucketNames(buckets))
    }

    private fun duplicateBucketNames(buckets: McpToolBuckets): Set<String> {
        val groupedNames =
            listOf(
                buckets.entrypointTools,
                buckets.runtimeTools,
                buckets.systemTools,
                buckets.githubTools,
                buckets.baTools,
                buckets.codexTools,
                buckets.workflowTools,
                buckets.advancedTools,
            ).flatMap { tools -> tools.map { it.name } }
        return groupedNames
            .groupingBy { it }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
    }
}
