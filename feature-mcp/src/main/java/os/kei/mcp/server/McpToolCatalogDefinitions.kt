package os.kei.mcp.server

internal object McpToolCatalogDefinitions {
    private val writeTools = setOf(
        "keios.os.cards.import",
        "keios.github.tracks.import",
        "keios.github.link.pending",
        "keios.github.stars.import",
        "keios.github.cache.clear",
        "keios.github.actions.recommended",
        "keios.ba.guide.bgm.favorites",
        "keios.ba.cache.clear"
    )

    private val networkTools = setOf(
        "keios.github.tracks.check",
        "keios.github.tracks.summary",
        "keios.github.link.resolve",
        "keios.github.discovery.search",
        "keios.github.repo.package.scan",
        "keios.github.direct_apk.inspect",
        "keios.github.actions.recommended",
        "keios.github.stars.lists",
        "keios.github.stars.preview",
        "keios.github.stars.import",
        "keios.github.stars.apk.verify"
    )

    private val deepScanTools = setOf(
        "keios.github.package.repo.scan"
    )

    fun definitionFor(
        name: String,
        entrypoint: Boolean
    ): McpToolDefinition {
        val profile = when (name) {
            in deepScanTools -> McpToolExecutionProfile.DeepScan
            in networkTools -> McpToolExecutionProfile.Network
            in writeTools -> McpToolExecutionProfile.NormalWrite
            else -> McpToolExecutionProfile.CacheRead
        }
        return McpToolDefinition(
            readOnly = name !in writeTools,
            destructive = name.endsWith(".clear") || name == "keios.github.link.pending",
            idempotent = name !in networkTools && name !in writeTools,
            openWorld = name in networkTools,
            executionProfile = profile,
            visibility = visibilityForName(name, entrypoint),
            maturity = maturityForName(name),
            outputContract = outputContractForName(name),
            workflowTags = workflowTagsForName(name),
            recommendedFor = recommendedUseCasesForName(name),
            outputSchema = outputSchemaForName(name, entrypoint)
        )
    }

    private fun visibilityForName(
        name: String,
        entrypoint: Boolean
    ): McpToolVisibility {
        return when {
            name == "keios.mcp.workflow.blueprints" -> McpToolVisibility.Workflow
            entrypoint -> McpToolVisibility.Entrypoint
            else -> McpToolVisibility.Advanced
        }
    }

    private fun maturityForName(name: String): McpToolMaturity {
        return when {
            name == "keios.github.package.repo.scan" ||
                name == "keios.github.stars.import" ||
                name == "keios.github.stars.apk.verify" -> McpToolMaturity.Preview

            name == "keios.github.direct_apk.inspect" -> McpToolMaturity.Preview
            name.startsWith("keios.dev.") -> McpToolMaturity.Preview
            else -> McpToolMaturity.Stable
        }
    }

    private fun outputContractForName(name: String): McpToolOutputContract {
        return when {
            name == "keios.mcp.runtime.config" ||
                name == "keios.os.cards.export" ||
                name == "keios.github.tracks.export" -> McpToolOutputContract.JsonText

            name == "keios.mcp.claw.skill.guide" ||
                name == "keios.mcp.workflow.blueprints" ||
                name == "keios.dev.codex.config" -> McpToolOutputContract.Markdown

            else -> McpToolOutputContract.KeyValueText
        }
    }

    private fun workflowTagsForName(name: String): List<String> {
        return when {
            name.startsWith("keios.github.actions") -> listOf("github-actions-watch")
            name.startsWith("keios.github") -> listOf("github-update-watch")
            name.startsWith("keios.ba") -> listOf("ba-daily-brief")
            name.startsWith("keios.os") -> listOf("os-card-backup")
            name.startsWith("keios.dev") -> listOf("codex-development")
            name.startsWith("keios.mcp") || name == "keios.health.ping" -> listOf("runtime-diagnostics")
            else -> emptyList()
        }
    }

    private fun recommendedUseCasesForName(name: String): List<String> {
        return when (name) {
            "keios.health.ping",
            "keios.mcp.runtime.status",
            "keios.mcp.runtime.logs" -> listOf("connectivity", "diagnostics")

            "keios.mcp.workflow.blueprints" -> listOf("scheduled_tasks", "composed_skills")
            "keios.dev.codex.config",
            "keios.dev.project.snapshot",
            "keios.dev.validation.plan" -> listOf("codex_development", "repository_validation")

            "keios.github.config.snapshot",
            "keios.github.tracks.list",
            "keios.github.tracks.summary",
            "keios.github.tracks.check" -> listOf("github_update_audit")

            "keios.github.actions.recommended" -> listOf("github_actions_audit")
            "keios.ba.snapshot",
            "keios.ba.calendar.cache",
            "keios.ba.pool.cache" -> listOf("ba_daily_brief")

            "keios.os.cards.export",
            "keios.os.cards.import" -> listOf("os_card_backup")

            else -> emptyList()
        }
    }

    private fun outputSchemaForName(
        name: String,
        entrypoint: Boolean
    ): io.modelcontextprotocol.kotlin.sdk.types.ToolSchema? {
        return if (entrypoint || name == "keios.mcp.runtime.config") {
            McpSchema.textOutputSchema()
        } else {
            null
        }
    }
}
