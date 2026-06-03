package os.kei.mcp.server

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal object McpToolCatalog {
    val entrypointToolNames = listOf(
        "keios.health.ping",
        "keios.mcp.runtime.status",
        "keios.mcp.workflow.blueprints",
        "keios.github.config.snapshot",
        "keios.ba.snapshot",
        "keios.dev.codex.config"
    )

    val runtimeToolNames = listOf(
        "keios.health.ping",
        "keios.app.info",
        "keios.app.version",
        "keios.shizuku.status",
        "keios.mcp.runtime.status",
        "keios.mcp.runtime.logs",
        "keios.mcp.runtime.config",
        "keios.mcp.claw.skill.guide",
        "keios.mcp.workflow.blueprints"
    )

    val homeToolNames = listOf(
        "keios.home.overview.snapshot"
    )

    val osToolNames = listOf(
        "keios.os.cards.snapshot",
        "keios.os.activity.cards",
        "keios.os.shell.cards",
        "keios.os.cards.export",
        "keios.os.cards.import"
    )

    val systemToolNames = listOf(
        "keios.system.topinfo.query"
    )

    val githubToolNames = listOf(
        "keios.github.config.snapshot",
        "keios.github.tracks.snapshot",
        "keios.github.tracks.list",
        "keios.github.tracks.export",
        "keios.github.tracks.import",
        "keios.github.tracks.check",
        "keios.github.tracks.summary",
        "keios.github.actions.recommended",
        "keios.github.link.parse",
        "keios.github.link.resolve",
        "keios.github.link.pending",
        "keios.github.discovery.search",
        "keios.github.repo.package.scan",
        "keios.github.direct_apk.inspect",
        "keios.github.package.repo.scan",
        "keios.github.stars.lists",
        "keios.github.stars.preview",
        "keios.github.stars.import",
        "keios.github.stars.apk.verify",
        "keios.github.cache.clear"
    )

    val baToolNames = listOf(
        "keios.ba.snapshot",
        "keios.ba.calendar.cache",
        "keios.ba.pool.cache",
        "keios.ba.guide.catalog.cache",
        "keios.ba.guide.cache.overview",
        "keios.ba.guide.cache.inspect",
        "keios.ba.guide.media.list",
        "keios.ba.guide.bgm.favorites",
        "keios.ba.cache.clear"
    )

    val devToolNames = listOf(
        "keios.dev.codex.config",
        "keios.dev.project.snapshot",
        "keios.dev.validation.plan"
    )

    val all: List<McpToolMeta>
        get() = englishTools

    fun forLocale(locale: Locale): List<McpToolMeta> {
        val cacheKey = when {
            locale.language.equals("zh", ignoreCase = true) -> "zh"
            locale.language.equals("ja", ignoreCase = true) -> "ja"
            else -> "en"
        }
        return localizedTools.getOrPut(cacheKey) {
            buildToolsForLocale(locale)
        }
    }

    fun metaForName(
        name: String,
        locale: Locale
    ): McpToolMeta? {
        return forLocale(locale).firstOrNull { it.name == name }
    }

    fun schemaFor(name: String): io.modelcontextprotocol.kotlin.sdk.types.ToolSchema {
        return McpSchema.toolSchema(argumentsFor(name))
    }

    fun descriptionFor(
        name: String,
        locale: Locale
    ): String {
        return forLocale(locale).firstOrNull { it.name == name }?.description
            ?: McpToolCatalogDescriptions.english[name].orEmpty()
    }

    private val orderedToolNames: List<String> =
        runtimeToolNames +
            homeToolNames +
            systemToolNames +
            osToolNames +
            githubToolNames +
            baToolNames +
            devToolNames

    private val englishTools: List<McpToolMeta>
        get() = forLocale(Locale.ENGLISH)

    private val localizedTools = ConcurrentHashMap<String, List<McpToolMeta>>()

    private val definitions: Map<String, McpToolDefinition> =
        orderedToolNames.associateWith { name ->
            McpToolCatalogDefinitions.definitionFor(
                name = name,
                entrypoint = name in entrypointToolNames
            )
        }

    private fun buildToolsForLocale(locale: Locale): List<McpToolMeta> {
        val descriptions = McpToolCatalogDescriptions.forLocale(locale)
        return orderedToolNames.map { name ->
            val definition = definitions.getValue(name)
            McpToolMeta(
                name = name,
                description = descriptions[name] ?: McpToolCatalogDescriptions.english[name].orEmpty(),
                group = groupForName(name),
                title = McpToolCatalogDescriptions.titleForName(name),
                arguments = argumentsFor(name),
                readOnly = definition.readOnly,
                destructive = definition.destructive,
                idempotent = definition.idempotent,
                openWorld = definition.openWorld,
                executionProfile = definition.executionProfile,
                visibility = definition.visibility,
                maturity = definition.maturity,
                outputContract = definition.outputContract,
                workflowTags = definition.workflowTags,
                recommendedFor = definition.recommendedFor,
                outputSchema = definition.outputSchema
            )
        }
    }

    private fun argumentsFor(name: String): List<McpToolArgumentSpec> {
        return McpToolCatalogArguments.argumentsFor(name).map { argument ->
            if (argument.description.isNotBlank()) {
                argument
            } else {
                argument.copy(
                    description = if (argument.required) {
                        "Required argument for $name."
                    } else {
                        "Optional argument for $name."
                    }
                )
            }
        }
    }

    private fun groupForName(name: String): String {
        return when (name) {
            in runtimeToolNames -> McpToolDomains.RUNTIME
            in homeToolNames -> McpToolDomains.HOME
            in systemToolNames -> McpToolDomains.SYSTEM
            in osToolNames -> McpToolDomains.OS
            in githubToolNames -> McpToolDomains.GITHUB
            in baToolNames -> McpToolDomains.BA
            in devToolNames -> McpToolDomains.DEV
            else -> "unknown"
        }
    }
}

internal val MCP_ENTRYPOINT_TOOLS: Set<String> = McpToolCatalog.entrypointToolNames.toSet()
