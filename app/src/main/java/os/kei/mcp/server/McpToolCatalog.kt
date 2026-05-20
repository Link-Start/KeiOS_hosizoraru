package os.kei.mcp.server

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class McpToolMeta(
    val name: String,
    val description: String,
    val group: String = "",
    val title: String? = null,
    val arguments: List<McpToolArgumentSpec> = emptyList(),
    val readOnly: Boolean = true,
    val destructive: Boolean = false,
    val idempotent: Boolean = true,
    val openWorld: Boolean = false,
    val executionProfile: McpToolExecutionProfile = McpToolExecutionProfile.CacheRead,
    val visibility: McpToolVisibility = McpToolVisibility.Advanced,
    val maturity: McpToolMaturity = McpToolMaturity.Stable,
    val outputContract: McpToolOutputContract = McpToolOutputContract.KeyValueText,
    val workflowTags: List<String> = emptyList(),
    val recommendedFor: List<String> = emptyList(),
    val outputSchema: io.modelcontextprotocol.kotlin.sdk.types.ToolSchema? = null
) {
    val requiredArguments: List<String>
        get() = arguments.filter { it.required }.map { it.name }
}

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

    private fun buildToolsForLocale(locale: Locale): List<McpToolMeta> {
        val descriptions = when {
            locale.language.equals("zh", ignoreCase = true) -> zhDescriptions
            locale.language.equals("ja", ignoreCase = true) -> jaDescriptions
            else -> enDescriptions
        }
        return orderedToolNames.map { name ->
            val definition = definitions.getValue(name)
            McpToolMeta(
                name = name,
                description = descriptions[name] ?: enDescriptions[name].orEmpty(),
                group = definition.group,
                title = titleForName(name),
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

    fun metaForName(name: String, locale: Locale): McpToolMeta? {
        return forLocale(locale).firstOrNull { it.name == name }
    }

    fun schemaFor(name: String): io.modelcontextprotocol.kotlin.sdk.types.ToolSchema {
        return McpSchema.toolSchema(argumentsFor(name))
    }

    fun descriptionFor(name: String, locale: Locale): String {
        return forLocale(locale).firstOrNull { it.name == name }?.description
            ?: enDescriptions[name].orEmpty()
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

    private fun argumentsFor(name: String): List<McpToolArgumentSpec> {
        return definitions[name]?.arguments.orEmpty().map { argument ->
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

    private data class ToolDefinition(
        val group: String,
        val arguments: List<McpToolArgumentSpec> = emptyList(),
        val readOnly: Boolean = true,
        val destructive: Boolean = false,
        val idempotent: Boolean = true,
        val openWorld: Boolean = false,
        val executionProfile: McpToolExecutionProfile = McpToolExecutionProfile.CacheRead,
        val visibility: McpToolVisibility = McpToolVisibility.Advanced,
        val maturity: McpToolMaturity = McpToolMaturity.Stable,
        val outputContract: McpToolOutputContract = McpToolOutputContract.KeyValueText,
        val workflowTags: List<String> = emptyList(),
        val recommendedFor: List<String> = emptyList(),
        val outputSchema: io.modelcontextprotocol.kotlin.sdk.types.ToolSchema? = null
    )

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

    private val toolArguments: Map<String, List<McpToolArgumentSpec>> = mapOf(
        "keios.mcp.runtime.logs" to listOf(McpSchema.integer("limit")),
        "keios.mcp.runtime.config" to listOf(
            McpSchema.string(
                name = "mode",
                description = "Connection template mode.",
                enumValues = listOf("auto", "local", "lan"),
                defaultValue = "auto"
            ),
            McpSchema.string("endpoint", description = "Optional endpoint override."),
            McpSchema.string("serverName", description = "Optional client display name override.")
        ),
        "keios.mcp.claw.skill.guide" to listOf(
            McpSchema.string(
                name = "mode",
                description = "Connection template mode for the generated Claw config.",
                enumValues = listOf("auto", "local", "lan"),
                defaultValue = "auto"
            ),
            McpSchema.string("endpoint", description = "Optional endpoint override."),
            McpSchema.string("serverName", description = "Optional client display name override.")
        ),
        "keios.mcp.workflow.blueprints" to listOf(
            McpSchema.string(
                name = "mode",
                description = "Blueprint output mode.",
                enumValues = listOf("list", "detail", "skill"),
                defaultValue = "list"
            ),
            McpSchema.string(
                name = "workflow",
                description = "Blueprint id.",
                enumValues = listOf(
                    "github-update-watch",
                    "github-actions-watch",
                    "ba-daily-brief",
                    "os-card-backup"
                )
            )
        ),
        "keios.system.topinfo.query" to listOf(McpSchema.string("query"), McpSchema.integer("limit")),
        "keios.os.activity.cards" to listOf(
            McpSchema.string("query"),
            McpSchema.boolean("onlyVisible"),
            McpSchema.integer("limit")
        ),
        "keios.os.shell.cards" to listOf(
            McpSchema.string("query"),
            McpSchema.boolean("onlyVisible"),
            McpSchema.boolean("includeOutput"),
            McpSchema.integer("limit")
        ),
        "keios.os.cards.export" to listOf(McpSchema.string("target")),
        "keios.os.cards.import" to listOf(
            McpSchema.string(
                name = "target",
                required = true,
                description = "OS card domain to import.",
                enumValues = listOf("activity", "shell", "all")
            ),
            McpSchema.string("json", required = true, description = "Exported KeiOS OS card JSON."),
            McpSchema.boolean(
                "apply",
                description = "Apply the import after preview.",
                defaultValue = "false"
            )
        ),
        "keios.github.tracks.list" to listOf(
            McpSchema.string(
                "repoFilter",
                description = "Owner/repo, package name, or app label filter."
            ),
            githubSourceModeArgument(),
            githubFilterModeArgument(),
            githubSortModeArgument(),
            githubSortDirectionArgument(),
            McpSchema.integer("limit")
        ),
        "keios.github.tracks.export" to listOf(
            McpSchema.string(
                "repoFilter",
                description = "Owner/repo, package name, or app label filter."
            ),
            githubSourceModeArgument(),
            githubFilterModeArgument(),
            githubSortModeArgument(),
            githubSortDirectionArgument()
        ),
        "keios.github.tracks.import" to listOf(
            McpSchema.string("json", required = true, description = "Tracked-item export JSON."),
            McpSchema.boolean(
                "apply",
                description = "Apply the import after preview.",
                defaultValue = "false"
            )
        ),
        "keios.github.tracks.check" to listOf(
            McpSchema.string(
                "repoFilter",
                description = "Owner/repo, package name, or app label filter."
            ),
            githubSourceModeArgument(),
            githubFilterModeArgument(),
            githubSortModeArgument(),
            githubSortDirectionArgument(),
            McpSchema.boolean(
                "onlyUpdates",
                description = "Return update rows only.",
                defaultValue = "false"
            ),
            McpSchema.integer("limit")
        ),
        "keios.github.tracks.summary" to listOf(
            McpSchema.string(
                name = "mode",
                description = "Summary source.",
                enumValues = listOf("cache", "network"),
                defaultValue = "cache"
            ),
            McpSchema.string(
                "repoFilter",
                description = "Owner/repo, package name, or app label filter."
            ),
            githubSourceModeArgument(),
            githubFilterModeArgument(),
            githubSortModeArgument(),
            githubSortDirectionArgument()
        ),
        "keios.github.actions.recommended" to listOf(
            McpSchema.string("repoFilter"),
            McpSchema.boolean(
                "refresh",
                description = "Refresh network data before returning.",
                defaultValue = "false"
            ),
            McpSchema.boolean(
                "onlyEnabled",
                description = "Only include tracks with Actions checks enabled.",
                defaultValue = "true"
            ),
            McpSchema.integer("limit")
        ),
        "keios.github.link.parse" to listOf(McpSchema.string("text", required = true)),
        "keios.github.link.resolve" to listOf(
            McpSchema.string("text", required = true),
            McpSchema.integer("limit")
        ),
        "keios.github.link.pending" to listOf(McpSchema.boolean("clear")),
        "keios.github.discovery.search" to listOf(
            McpSchema.string("query", required = true),
            McpSchema.integer("limit")
        ),
        "keios.github.repo.package.scan" to listOf(
            McpSchema.string("repoUrl", required = true),
            McpSchema.string("expectedPackageName")
        ),
        "keios.github.direct_apk.inspect" to listOf(
            McpSchema.string("url", required = true),
            McpSchema.string("expectedPackageName"),
            McpSchema.string("appLabel"),
            McpSchema.boolean(
                "forceRefresh",
                description = "Bypass cached manifest data.",
                defaultValue = "false"
            )
        ),
        "keios.github.package.repo.scan" to listOf(
            McpSchema.string("packageName", required = true),
            McpSchema.string("appLabel"),
            McpSchema.string("preferredRepoUrl"),
            McpSchema.integer("candidateLimit"),
            McpSchema.integer("verificationLimit")
        ),
        "keios.github.stars.lists" to listOf(McpSchema.string("url", required = true)),
        "keios.github.stars.preview" to starImportArguments(),
        "keios.github.stars.import" to starImportArguments(),
        "keios.github.stars.apk.verify" to listOf(
            McpSchema.string("repoUrls", required = true),
            McpSchema.integer("limit")
        ),
        "keios.ba.calendar.cache" to cacheEntryArguments(),
        "keios.ba.pool.cache" to cacheEntryArguments(),
        "keios.ba.guide.catalog.cache" to listOf(
            McpSchema.string("tab"),
            McpSchema.boolean("includeEntries"),
            McpSchema.integer("limit")
        ),
        "keios.ba.guide.cache.inspect" to listOf(
            McpSchema.string("url"),
            McpSchema.boolean("includeSections"),
            McpSchema.integer("refreshIntervalHours")
        ),
        "keios.ba.guide.media.list" to listOf(
            McpSchema.string("url"),
            McpSchema.string("kind"),
            McpSchema.integer("limit")
        ),
        "keios.ba.guide.bgm.favorites" to listOf(
            McpSchema.string("action"),
            McpSchema.string("query"),
            McpSchema.integer("limit"),
            McpSchema.string("json"),
            McpSchema.boolean("apply")
        ),
        "keios.ba.cache.clear" to listOf(McpSchema.string("scope"), McpSchema.string("url")),
        "keios.dev.codex.config" to listOf(
            McpSchema.string(
                name = "mode",
                description = "Connection mode for generated Codex config.",
                enumValues = listOf("auto", "local", "lan"),
                defaultValue = "local"
            ),
            McpSchema.string("endpoint", description = "Optional endpoint override."),
            McpSchema.string("serverName", description = "Optional Codex MCP server name override."),
            McpSchema.string(
                name = "tokenEnv",
                description = "Environment variable name used by Codex for the bearer token.",
                defaultValue = "KEIOS_MCP_TOKEN"
            )
        ),
        "keios.dev.validation.plan" to listOf(
            McpSchema.string(
                name = "scope",
                description = "Validation scope.",
                enumValues = listOf("quick", "mcp", "release", "ui", "baseline"),
                defaultValue = "quick"
            )
        )
    )

    private val definitions: Map<String, ToolDefinition> = orderedToolNames.associateWith { name ->
        val profile = when (name) {
            in deepScanTools -> McpToolExecutionProfile.DeepScan
            in networkTools -> McpToolExecutionProfile.Network
            in writeTools -> McpToolExecutionProfile.NormalWrite
            else -> McpToolExecutionProfile.CacheRead
        }
        ToolDefinition(
            group = groupForName(name),
            arguments = toolArguments[name].orEmpty(),
            readOnly = name !in writeTools,
            destructive = name.endsWith(".clear") || name == "keios.github.link.pending",
            idempotent = name !in networkTools && name !in writeTools,
            openWorld = name in networkTools,
            executionProfile = profile,
            visibility = visibilityForName(name),
            maturity = maturityForName(name),
            outputContract = outputContractForName(name),
            workflowTags = workflowTagsForName(name),
            recommendedFor = recommendedUseCasesForName(name),
            outputSchema = outputSchemaForName(name)
        )
    }

    private fun cacheEntryArguments(): List<McpToolArgumentSpec> {
        return listOf(
            McpSchema.integer("serverIndex"),
            McpSchema.boolean("includeEntries"),
            McpSchema.integer("limit")
        )
    }

    private fun starImportArguments(): List<McpToolArgumentSpec> {
        return listOf(
            McpSchema.string("source"),
            McpSchema.string("username"),
            McpSchema.string("listUrl"),
            McpSchema.integer("limit"),
            McpSchema.string("quality"),
            McpSchema.boolean("apply")
        )
    }

    private fun githubSourceModeArgument(): McpToolArgumentSpec {
        return McpSchema.string(
            name = "sourceMode",
            description = "Tracked source filter.",
            enumValues = listOf("github_repository", "direct_apk")
        )
    }

    private fun githubFilterModeArgument(): McpToolArgumentSpec {
        return McpSchema.string(
            name = "filterMode",
            description = "Temporary tracking filter.",
            enumValues = listOf(
                "all",
                "github_repository",
                "direct_apk",
                "pre_release_tracked",
                "update_available",
                "installed",
                "failed_checks",
                "actions_check_enabled"
            ),
            defaultValue = "all"
        )
    }

    private fun githubSortModeArgument(): McpToolArgumentSpec {
        return McpSchema.string(
            name = "sortMode",
            description = "Tracking sort rule.",
            enumValues = listOf("update", "name", "pre_release", "changed", "added"),
            defaultValue = "update"
        )
    }

    private fun githubSortDirectionArgument(): McpToolArgumentSpec {
        return McpSchema.string(
            name = "sortDirection",
            description = "Tracking sort direction.",
            enumValues = listOf("forward", "reverse"),
            defaultValue = "forward"
        )
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

    private fun visibilityForName(name: String): McpToolVisibility {
        return when {
            name == "keios.mcp.workflow.blueprints" -> McpToolVisibility.Workflow
            name in entrypointToolNames -> McpToolVisibility.Entrypoint
            name.startsWith("keios.dev.") -> McpToolVisibility.Advanced
            name.endsWith(".cache.clear") ||
                    name == "keios.github.cache.clear" ||
                    name == "keios.github.link.pending" ||
                    name == "keios.github.package.repo.scan" ||
                    name == "keios.system.topinfo.query" -> McpToolVisibility.Advanced

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

    private fun outputSchemaForName(name: String): io.modelcontextprotocol.kotlin.sdk.types.ToolSchema? {
        return if (name in entrypointToolNames || name == "keios.mcp.runtime.config") {
            McpSchema.textOutputSchema()
        } else {
            null
        }
    }

    private fun titleForName(name: String): String {
        return name.removePrefix("keios.").split('.').joinToString(" ") { segment ->
            segment.replaceFirstChar { char -> char.uppercase(Locale.ROOT) }
        }
    }

    private val enDescriptions = mapOf(
        "keios.health.ping" to "Connectivity probe; returns pong.",
        "keios.app.info" to "Read app label, package, version, and Shizuku API level.",
        "keios.app.version" to "Read versionName and versionCode.",
        "keios.shizuku.status" to "Read current Shizuku status.",
        "keios.mcp.runtime.status" to "Read MCP runtime endpoint, clients, token state, and last error.",
        "keios.mcp.runtime.logs" to "Read MCP runtime logs. Args: limit=1..200.",
        "keios.mcp.runtime.config" to "Generate streamable HTTP client config. Args: mode=auto|local|lan, endpoint, serverName.",
        "keios.mcp.claw.skill.guide" to "Generate Claw onboarding with config JSON, resource URIs, and SKILL.md.",
        "keios.mcp.workflow.blueprints" to "List or read Claw workflow blueprints for scheduled tasks and composed skills. Args: mode=list|detail|skill, workflow.",
        "keios.home.overview.snapshot" to "Read Home overview data for MCP, GitHub, and Blue Archive.",
        "keios.system.topinfo.query" to "Query cached system TopInfo values. Args: query, limit.",
        "keios.os.cards.snapshot" to "Read OS page visibility, expansion state, cache footprint, and card counts.",
        "keios.os.activity.cards" to "List OS Activity shortcut cards. Args: query, onlyVisible, limit.",
        "keios.os.shell.cards" to "List OS shell cards. Args: query, onlyVisible, includeOutput, limit.",
        "keios.os.cards.export" to "Export Activity, shell, or all OS cards. Args: target=activity|shell|all.",
        "keios.os.cards.import" to "Preview or import Activity or shell card JSON. Args: target, json, apply.",
        "keios.github.config.snapshot" to "Read GitHub strategy, token, refresh, share import, and cache settings.",
        "keios.github.tracks.snapshot" to "Read GitHub tracking counts, cache health, and update settings.",
        "keios.github.tracks.list" to "List tracked GitHub repository or subscription entries. Args: repoFilter, sourceMode=github_repository|direct_apk, filterMode=all|github_repository|direct_apk|pre_release_tracked|update_available|installed|failed_checks|actions_check_enabled, sortMode=update|name|pre_release|changed|added, sortDirection=forward|reverse, limit.",
        "keios.github.tracks.export" to "Export tracked GitHub repository or subscription entries as keios.github.tracked/v3 JSON. Args: repoFilter, sourceMode, filterMode, sortMode, sortDirection.",
        "keios.github.tracks.import" to "Preview or import tracked GitHub repository and subscription JSON. Args: json, apply.",
        "keios.github.tracks.check" to "Check tracked GitHub repository or subscription entries online. Args: repoFilter, sourceMode, filterMode, sortMode, sortDirection, onlyUpdates, limit.",
        "keios.github.tracks.summary" to "Summarize cached or online tracking state. Args: mode=cache|network, repoFilter, sourceMode, filterMode, sortMode, sortDirection.",
        "keios.github.actions.recommended" to "Read or refresh recommended GitHub Actions runs with per-track interval metadata. Args: repoFilter, refresh, onlyEnabled, limit.",
        "keios.github.link.parse" to "Parse GitHub repo, release, tag, and APK asset links from text.",
        "keios.github.link.resolve" to "Resolve a GitHub link into APK asset candidates.",
        "keios.github.link.pending" to "Read or clear pending pre-install share tracking state.",
        "keios.github.discovery.search" to "Search GitHub repositories. Args: query, limit.",
        "keios.github.repo.package.scan" to "Scan repository release APKs and read the package name, preferring expectedPackageName when provided.",
        "keios.github.direct_apk.inspect" to "Inspect a subscription/direct APK URL manifest for package, version, SDK, ABI, permissions, and signature summary.",
        "keios.github.package.repo.scan" to "Reverse scan repositories for a package name. Args: packageName, appLabel, preferredRepoUrl, candidateLimit, verificationLimit.",
        "keios.github.stars.lists" to "Discover public GitHub Star Lists from a profile stars URL. Args: url.",
        "keios.github.stars.preview" to "Preview Star List import candidates. Args: source, username, listUrl, limit, quality.",
        "keios.github.stars.import" to "Preview or apply Star List import. Args: source, username, listUrl, limit, quality, apply.",
        "keios.github.stars.apk.verify" to "Verify latest stable release APK presence for repository URLs. Args: repoUrls, limit.",
        "keios.github.cache.clear" to "Clear GitHub release, check, and package-scan related caches.",
        "keios.ba.snapshot" to "Read Blue Archive AP, Cafe, notification, server, and ID snapshot.",
        "keios.ba.calendar.cache" to "Read Blue Archive event calendar cache. Args: serverIndex, includeEntries, limit.",
        "keios.ba.pool.cache" to "Read Blue Archive recruitment banner cache. Args: serverIndex, includeEntries, limit.",
        "keios.ba.guide.catalog.cache" to "Read Student Guide catalog cache. Args: tab, includeEntries, limit.",
        "keios.ba.guide.cache.overview" to "Read Student Guide detail cache footprint and latest sync.",
        "keios.ba.guide.cache.inspect" to "Inspect Student Guide detail cache by URL. Args: url, includeSections, refreshIntervalHours.",
        "keios.ba.guide.media.list" to "List gallery and voice media from Student Guide cache. Args: url, kind, limit.",
        "keios.ba.guide.bgm.favorites" to "List, export, or import Memorial Lobby BGM favorites.",
        "keios.ba.cache.clear" to "Clear Blue Archive and GitHub cache data. Args: scope, url.",
        "keios.dev.codex.config" to "Generate Codex MCP onboarding with streamable HTTP endpoint, bearer-token environment variable, and first-call flow.",
        "keios.dev.project.snapshot" to "Read a compact developer snapshot for Codex, including source files, test files, architecture, and common commands.",
        "keios.dev.validation.plan" to "Generate a validation command plan for Codex. Args: scope=quick|mcp|release|ui|baseline."
    )

    private val zhDescriptions = mapOf(
        "keios.health.ping" to "连通性探测，返回 pong。",
        "keios.app.info" to "读取应用名称、包名、版本与 Shizuku API 等级。",
        "keios.app.version" to "读取 versionName 与 versionCode。",
        "keios.shizuku.status" to "读取当前 Shizuku 状态。",
        "keios.mcp.runtime.status" to "读取 MCP 运行端点、客户端、Token 状态与最近错误。",
        "keios.mcp.runtime.logs" to "读取 MCP 运行日志。参数：limit=1..200。",
        "keios.mcp.runtime.config" to "生成 streamable HTTP 客户端配置。参数：mode=auto|local|lan、endpoint、serverName。",
        "keios.mcp.claw.skill.guide" to "生成 Claw 接入指南，包含配置 JSON、资源 URI 与 SKILL.md。",
        "keios.mcp.workflow.blueprints" to "列出或读取 Claw 工作流蓝图，用于定时任务与组合技能。参数：mode=list|detail|skill、workflow。",
        "keios.home.overview.snapshot" to "读取主页概览数据，包含 MCP、GitHub 与 Blue Archive。",
        "keios.system.topinfo.query" to "查询已缓存的系统 TopInfo 值。参数：query、limit。",
        "keios.os.cards.snapshot" to "读取 OS 页可见性、展开状态、缓存占用与卡片数量。",
        "keios.os.activity.cards" to "列出 OS Activity 快捷方式卡片。参数：query、onlyVisible、limit。",
        "keios.os.shell.cards" to "列出 OS Shell 卡片。参数：query、onlyVisible、includeOutput、limit。",
        "keios.os.cards.export" to "导出 Activity、Shell 或全部 OS 卡片。参数：target=activity|shell|all。",
        "keios.os.cards.import" to "预览或导入 Activity / Shell 卡片 JSON。参数：target、json、apply。",
        "keios.github.config.snapshot" to "读取 GitHub 策略、Token、刷新、分享导入与缓存配置。",
        "keios.github.tracks.snapshot" to "读取 GitHub 追踪数量、缓存健康度与更新设置。",
        "keios.github.tracks.list" to "列出已追踪 GitHub 仓库或订阅项目。参数：repoFilter、sourceMode=github_repository|direct_apk、filterMode=all|github_repository|direct_apk|pre_release_tracked|update_available|installed|failed_checks|actions_check_enabled、sortMode=update|name|pre_release|changed|added、sortDirection=forward|reverse、limit。",
        "keios.github.tracks.export" to "将已追踪 GitHub 仓库或订阅项目导出为 keios.github.tracked/v3 JSON。参数：repoFilter、sourceMode、filterMode、sortMode、sortDirection。",
        "keios.github.tracks.import" to "预览或导入已追踪 GitHub 仓库与订阅项目 JSON。参数：json、apply。",
        "keios.github.tracks.check" to "联网检查已追踪 GitHub 仓库或订阅项目更新。参数：repoFilter、sourceMode、filterMode、sortMode、sortDirection、onlyUpdates、limit。",
        "keios.github.tracks.summary" to "汇总缓存或联网追踪状态。参数：mode=cache|network、repoFilter、sourceMode、filterMode、sortMode、sortDirection。",
        "keios.github.actions.recommended" to "读取或刷新已追踪 App 的推荐 GitHub Actions 构建，并输出单项间隔信息。参数：repoFilter、refresh、onlyEnabled、limit。",
        "keios.github.link.parse" to "从文本中解析 GitHub 仓库、release、tag 与 APK asset 链接。",
        "keios.github.link.resolve" to "将 GitHub 链接解析为 APK asset 候选。",
        "keios.github.link.pending" to "读取或清理安装前分享追踪状态。",
        "keios.github.discovery.search" to "搜索 GitHub 仓库。参数：query、limit。",
        "keios.github.repo.package.scan" to "扫描仓库最新稳定 release APK 并读取包名，传入 expectedPackageName 时优先匹配目标包名。",
        "keios.github.direct_apk.inspect" to "检查订阅项目或直链 APK manifest，输出包名、版本、SDK、ABI、权限与签名摘要。",
        "keios.github.package.repo.scan" to "按包名反扫仓库并验证 APK 包名。参数：packageName、appLabel、preferredRepoUrl。",
        "keios.github.stars.lists" to "从 GitHub stars 主页发现公开 Star List。参数：url。",
        "keios.github.stars.preview" to "预览 Star List 导入候选。参数：source、username、listUrl、limit、quality。",
        "keios.github.stars.import" to "预览或执行 Star List 导入。参数：source、username、listUrl、limit、quality、apply。",
        "keios.github.stars.apk.verify" to "批量验证仓库最新稳定 release 是否含 APK。参数：repoUrls、limit。",
        "keios.github.cache.clear" to "清理 GitHub 检查、release asset 与扫描相关缓存。",
        "keios.ba.snapshot" to "读取 Blue Archive AP、咖啡厅、通知、服务器与 ID 快照。",
        "keios.ba.calendar.cache" to "读取 Blue Archive 活动日历缓存。参数：serverIndex、includeEntries、limit。",
        "keios.ba.pool.cache" to "读取 Blue Archive 招募卡池缓存。参数：serverIndex、includeEntries、limit。",
        "keios.ba.guide.catalog.cache" to "读取学生图鉴目录缓存。参数：tab、includeEntries、limit。",
        "keios.ba.guide.cache.overview" to "读取学生图鉴详情缓存占用与最近同步时间。",
        "keios.ba.guide.cache.inspect" to "按 URL 检查学生图鉴详情缓存。参数：url、includeSections、refreshIntervalHours。",
        "keios.ba.guide.media.list" to "列出学生图鉴缓存里的鉴赏与语音媒体。参数：url、kind、limit。",
        "keios.ba.guide.bgm.favorites" to "列出、导出或导入纪念大厅 BGM 收藏。",
        "keios.ba.cache.clear" to "清理 Blue Archive 与 GitHub 缓存数据。参数：scope、url。",
        "keios.dev.codex.config" to "生成 Codex MCP 接入指南，包含 streamable HTTP endpoint、Bearer Token 环境变量与首次调用流程。",
        "keios.dev.project.snapshot" to "读取给 Codex 使用的开发快照，包含关键源码、测试文件、架构信息与常用命令。",
        "keios.dev.validation.plan" to "生成 Codex 可执行的验证命令计划。参数：scope=quick|mcp|release|ui|baseline。"
    )

    private val jaDescriptions = mapOf(
        "keios.health.ping" to "接続確認を行い、pong を返します。",
        "keios.app.info" to "アプリ名、package、version、Shizuku API レベルを読み取ります。",
        "keios.app.version" to "versionName と versionCode を読み取ります。",
        "keios.shizuku.status" to "現在の Shizuku 状態を読み取ります。",
        "keios.mcp.runtime.status" to "MCP 実行エンドポイント、クライアント、Token 状態、直近エラーを読み取ります。",
        "keios.mcp.runtime.logs" to "MCP 実行ログを読み取ります。引数: limit=1..200。",
        "keios.mcp.runtime.config" to "streamable HTTP クライアント設定を生成します。引数: mode=auto|local|lan、endpoint、serverName。",
        "keios.mcp.claw.skill.guide" to "Claw 導入ガイドを生成します。設定 JSON、resource URI、SKILL.md を含みます。",
        "keios.mcp.workflow.blueprints" to "Claw の定期タスクと複合 skill 向け workflow blueprint を一覧または取得します。引数: mode=list|detail|skill、workflow。",
        "keios.home.overview.snapshot" to "MCP、GitHub、Blue Archive を含む Home 概要データを読み取ります。",
        "keios.system.topinfo.query" to "キャッシュ済み system TopInfo 値を検索します。引数: query、limit。",
        "keios.os.cards.snapshot" to "OS ページの表示状態、展開状態、キャッシュ容量、カード数を読み取ります。",
        "keios.os.activity.cards" to "OS Activity ショートカットカードを一覧します。引数: query、onlyVisible、limit。",
        "keios.os.shell.cards" to "OS Shell カードを一覧します。引数: query、onlyVisible、includeOutput、limit。",
        "keios.os.cards.export" to "Activity、Shell、またはすべての OS カードをエクスポートします。引数: target=activity|shell|all。",
        "keios.os.cards.import" to "Activity / Shell カード JSON をプレビューまたはインポートします。引数: target、json、apply。",
        "keios.github.config.snapshot" to "GitHub の方式、Token、更新間隔、共有インポート、キャッシュ設定を読み取ります。",
        "keios.github.tracks.snapshot" to "GitHub 追跡数、キャッシュ状態、更新設定を読み取ります。",
        "keios.github.tracks.list" to "追跡中の GitHub リポジトリまたは購読プロジェクトを一覧します。引数: repoFilter、sourceMode=github_repository|direct_apk、filterMode=all|github_repository|direct_apk|pre_release_tracked|update_available|installed|failed_checks|actions_check_enabled、sortMode=update|name|pre_release|changed|added、sortDirection=forward|reverse、limit。",
        "keios.github.tracks.export" to "追跡中の GitHub リポジトリまたは購読プロジェクトを keios.github.tracked/v3 JSON としてエクスポートします。引数: repoFilter、sourceMode、filterMode、sortMode、sortDirection。",
        "keios.github.tracks.import" to "追跡中の GitHub リポジトリと購読プロジェクト JSON をプレビューまたはインポートします。引数: json、apply。",
        "keios.github.tracks.check" to "追跡中の GitHub リポジトリまたは購読プロジェクトの更新をオンライン確認します。引数: repoFilter、sourceMode、filterMode、sortMode、sortDirection、onlyUpdates、limit。",
        "keios.github.tracks.summary" to "キャッシュまたはオンラインの追跡状態を要約します。引数: mode=cache|network、repoFilter、sourceMode、filterMode、sortMode、sortDirection。",
        "keios.github.actions.recommended" to "追跡中アプリの推奨 GitHub Actions run を読み取りまたは更新し、項目ごとの間隔情報を返します。引数: repoFilter、refresh、onlyEnabled、limit。",
        "keios.github.link.parse" to "テキストから GitHub repo、release、tag、APK asset リンクを解析します。",
        "keios.github.link.resolve" to "GitHub リンクを APK asset 候補へ解決します。",
        "keios.github.link.pending" to "インストール前共有追跡状態を読み取りまたはクリアします。",
        "keios.github.discovery.search" to "GitHub リポジトリを検索します。引数: query、limit。",
        "keios.github.repo.package.scan" to "リポジトリの最新安定版 APK から package 名を読み取ります。expectedPackageName 指定時は対象 package を優先します。",
        "keios.github.direct_apk.inspect" to "購読プロジェクトまたは直接 APK URL の manifest を検査し、package、version、SDK、ABI、権限、署名概要を返します。",
        "keios.github.package.repo.scan" to "package 名から候補リポジトリを逆引きし、APK package を検証します。",
        "keios.github.stars.lists" to "GitHub stars ページから公開 Star List を検出します。引数: url。",
        "keios.github.stars.preview" to "Star List インポート候補をプレビューします。引数: source、username、listUrl、limit、quality。",
        "keios.github.stars.import" to "Star List インポートをプレビューまたは実行します。引数: source、username、listUrl、limit、quality、apply。",
        "keios.github.stars.apk.verify" to "リポジトリ一覧の最新安定版 release に APK があるか検証します。引数: repoUrls、limit。",
        "keios.github.cache.clear" to "GitHub 確認、release asset、スキャン関連キャッシュを削除します。",
        "keios.ba.snapshot" to "Blue Archive の AP、カフェ、通知、サーバー、ID スナップショットを読み取ります。",
        "keios.ba.calendar.cache" to "Blue Archive イベントカレンダーキャッシュを読み取ります。引数: serverIndex、includeEntries、limit。",
        "keios.ba.pool.cache" to "Blue Archive 募集バナーキャッシュを読み取ります。引数: serverIndex、includeEntries、limit。",
        "keios.ba.guide.catalog.cache" to "生徒図鑑カタログキャッシュを読み取ります。引数: tab、includeEntries、limit。",
        "keios.ba.guide.cache.overview" to "生徒図鑑詳細キャッシュ容量と最新同期時刻を読み取ります。",
        "keios.ba.guide.cache.inspect" to "URL から生徒図鑑詳細キャッシュを検査します。引数: url、includeSections、refreshIntervalHours。",
        "keios.ba.guide.media.list" to "生徒図鑑キャッシュ内の鑑賞・音声メディアを一覧します。引数: url、kind、limit。",
        "keios.ba.guide.bgm.favorites" to "メモリアルロビー BGM お気に入りを一覧、エクスポート、またはインポートします。",
        "keios.ba.cache.clear" to "Blue Archive と GitHub のキャッシュデータを削除します。引数: scope、url。",
        "keios.dev.codex.config" to "Codex MCP 導入ガイドを生成します。streamable HTTP endpoint、Bearer Token 環境変数、初回呼び出し手順を含みます。",
        "keios.dev.project.snapshot" to "Codex 向けの開発スナップショットを読み取ります。主要ソース、テスト、構成、よく使うコマンドを含みます。",
        "keios.dev.validation.plan" to "Codex 向けの検証コマンド計画を生成します。引数: scope=quick|mcp|release|ui|baseline。"
    )
}

internal val MCP_ENTRYPOINT_TOOLS: Set<String> = McpToolCatalog.entrypointToolNames.toSet()
