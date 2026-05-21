package os.kei.ui.page.main.mcp.state

import os.kei.mcp.server.McpToolCatalog
import os.kei.mcp.server.McpToolDomains
import os.kei.mcp.server.McpToolMeta
import os.kei.mcp.server.McpToolVisibility
import java.util.Locale

internal data class McpToolBucketInput(
    val tools: List<McpToolMeta>,
    val searchQuery: String,
)

internal data class McpToolBuckets(
    val entrypointTools: List<McpToolMeta>,
    val runtimeTools: List<McpToolMeta>,
    val systemTools: List<McpToolMeta>,
    val githubTools: List<McpToolMeta>,
    val baTools: List<McpToolMeta>,
    val codexTools: List<McpToolMeta>,
    val workflowTools: List<McpToolMeta>,
    val advancedTools: List<McpToolMeta>,
) {
    companion object {
        val Empty =
            McpToolBuckets(
                entrypointTools = emptyList(),
                runtimeTools = emptyList(),
                systemTools = emptyList(),
                githubTools = emptyList(),
                baTools = emptyList(),
                codexTools = emptyList(),
                workflowTools = emptyList(),
                advancedTools = emptyList(),
            )
    }
}

private val McpToolEntrypointGroups =
    setOf(
        McpToolDomains.RUNTIME,
        McpToolDomains.HOME,
    )

private val McpToolSystemGroups =
    setOf(
        McpToolDomains.SYSTEM,
        McpToolDomains.OS,
    )

private val McpToolCategorizedGroups =
    setOf(
        McpToolDomains.RUNTIME,
        McpToolDomains.HOME,
        McpToolDomains.SYSTEM,
        McpToolDomains.OS,
        McpToolDomains.GITHUB,
        McpToolDomains.BA,
        McpToolDomains.DEV,
    )

private val McpCodexToolNames = McpToolCatalog.devToolNames.toSet()

internal fun deriveMcpToolBuckets(input: McpToolBucketInput): McpToolBuckets {
    val query = input.searchQuery.trim().lowercase(Locale.ROOT)
    val entrypointTools = mutableListOf<McpToolMeta>()
    val runtimeTools = mutableListOf<McpToolMeta>()
    val systemTools = mutableListOf<McpToolMeta>()
    val githubTools = mutableListOf<McpToolMeta>()
    val baTools = mutableListOf<McpToolMeta>()
    val codexTools = mutableListOf<McpToolMeta>()
    val workflowTools = mutableListOf<McpToolMeta>()
    val advancedCandidates = mutableListOf<McpToolMeta>()
    val categorizedToolNames = mutableSetOf<String>()

    input.tools.forEach { tool ->
        if (query.isNotBlank() && !tool.matchesMcpToolQuery(query)) {
            return@forEach
        }
        if (tool.group in McpToolCategorizedGroups || tool.visibility == McpToolVisibility.Workflow) {
            categorizedToolNames += tool.name
        }
        when {
            tool.visibility == McpToolVisibility.Entrypoint &&
                tool.group in McpToolEntrypointGroups -> entrypointTools += tool

            tool.group in McpToolEntrypointGroups &&
                tool.visibility != McpToolVisibility.Workflow -> runtimeTools += tool

            tool.group in McpToolSystemGroups -> systemTools += tool

            tool.group == McpToolDomains.GITHUB -> githubTools += tool

            tool.group == McpToolDomains.BA -> baTools += tool
        }
        if (tool.name in McpCodexToolNames) {
            codexTools += tool
        }
        if (tool.visibility == McpToolVisibility.Workflow) {
            workflowTools += tool
        }
        if (tool.visibility == McpToolVisibility.Advanced) {
            advancedCandidates += tool
        }
    }

    return McpToolBuckets(
        entrypointTools = entrypointTools,
        runtimeTools = runtimeTools,
        systemTools = systemTools,
        githubTools = githubTools,
        baTools = baTools,
        codexTools = codexTools,
        workflowTools = workflowTools,
        advancedTools = advancedCandidates.filter { tool -> tool.name !in categorizedToolNames },
    )
}

private fun McpToolMeta.matchesMcpToolQuery(query: String): Boolean =
    name.lowercase(Locale.ROOT).contains(query) ||
        description.lowercase(Locale.ROOT).contains(query) ||
        group.lowercase(Locale.ROOT).contains(query)
