package os.kei.ui.page.main.mcp.section

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import os.kei.mcp.server.McpServerUiState
import os.kei.mcp.server.McpToolCatalog
import os.kei.ui.page.main.mcp.McpToolBucketLoader
import os.kei.ui.page.main.mcp.state.McpToolBucketInput
import os.kei.ui.page.main.mcp.state.McpToolBuckets
import os.kei.ui.page.main.mcp.state.deriveMcpToolBuckets
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class McpToolSectionsTest {
    @Test
    fun toolBucketsKeepUserFacingGroupsSeparate() {
        val state = McpServerUiState(tools = McpToolCatalog.forLocale(Locale.SIMPLIFIED_CHINESE))

        val buckets = deriveMcpToolBuckets(McpToolBucketInput(state.tools, searchQuery = ""))

        assertTrue(buckets.baTools.any { it.name == "keios.ba.snapshot" })
        assertTrue(buckets.githubTools.any { it.name == "keios.github.config.snapshot" })
        assertTrue(buckets.codexTools.any { it.name == "keios.dev.codex.config" })
        assertTrue(buckets.workflowTools.any { it.name == "keios.mcp.workflow.blueprints" })
        assertEquals(emptySet(), duplicateBucketNames(buckets))
    }

    @Test
    fun toolBucketsApplySearchAcrossDomains() {
        val state = McpServerUiState(tools = McpToolCatalog.forLocale(Locale.SIMPLIFIED_CHINESE))

        val buckets = deriveMcpToolBuckets(McpToolBucketInput(state.tools, searchQuery = "codex"))

        assertTrue(buckets.codexTools.isNotEmpty())
        assertEquals(emptyList(), buckets.baTools)
        assertEquals(emptySet(), duplicateBucketNames(buckets))
    }

    @Test
    fun toolBucketLoaderSkipsTrimEquivalentSearchRequests() =
        runTest {
            var deriveCount = 0
            val tools = McpToolCatalog.forLocale(Locale.SIMPLIFIED_CHINESE)
            val loader =
                McpToolBucketLoader(
                    scope = this,
                    deriveToolBuckets = { input ->
                        deriveCount += 1
                        deriveMcpToolBuckets(input)
                    },
                )

            loader.request(McpToolBucketInput(tools, searchQuery = "codex"))
            advanceUntilIdle()
            loader.request(McpToolBucketInput(tools, searchQuery = "  codex  "))
            advanceUntilIdle()

            assertEquals(1, deriveCount)
            assertTrue(loader.buckets.value.codexTools.isNotEmpty())
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
