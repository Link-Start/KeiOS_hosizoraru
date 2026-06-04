package os.kei.feature.os.mcp

import os.kei.mcp.server.McpToolEnvironment

interface McpSystemOsToolDelegate {
    fun buildTopInfoText(query: String, limit: Int): String

    fun buildOsCardsSnapshotText(): String

    fun buildOsActivityCardsText(
        query: String,
        onlyVisible: Boolean,
        limit: Int,
    ): String

    fun buildOsShellCardsText(
        query: String,
        onlyVisible: Boolean,
        includeOutput: Boolean,
        limit: Int,
    ): String

    fun buildOsCardsExportText(target: String): String

    fun buildOsCardsImportText(
        target: String,
        rawJson: String,
        apply: Boolean,
    ): String
}

fun interface McpSystemOsToolDelegateFactory {
    fun create(environment: McpToolEnvironment): McpSystemOsToolDelegate
}
