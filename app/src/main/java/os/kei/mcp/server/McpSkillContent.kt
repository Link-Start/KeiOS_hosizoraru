package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.util.Locale

internal class McpSkillContent(
    private val environment: McpToolEnvironment,
    private val runtimeConfigBuilder: (McpServerUiState?, String, String, String) -> String
) {
    fun registerClawGuideTool(server: Server) {
        server.addMcpTextTool(environment, name = "keios.mcp.claw.skill.guide") { request ->
            val state = environment.currentState()
            buildClawSkillGuideText(
                state = state,
                mode = argString(request.arguments?.get("mode")),
                endpointOverride = argString(request.arguments?.get("endpoint")).trim(),
                serverNameOverride = argString(request.arguments?.get("serverName")).trim()
            )
        }
    }

    fun registerResources(server: Server) {
        val locale = currentLocale()
        server.addResource(
            uri = SKILL_RESOURCE_URI,
            name = "keios-mcp-skill",
            description = localText(
                locale,
                "KeiOS MCP Skill 指南",
                "KeiOS MCP Skill guide",
                "KeiOS MCP skill guide"
            ),
            mimeType = MIME_MARKDOWN
        ) { _ ->
            callResource(uri = SKILL_RESOURCE_URI, mimeType = MIME_MARKDOWN, text = loadSkillMarkdown())
        }
        server.addResource(
            uri = SKILL_OVERVIEW_URI,
            name = "keios-mcp-skill-overview",
            description = localText(
                locale,
                "KeiOS MCP 快速总览",
                "KeiOS MCP quick overview",
                "KeiOS MCP quick overview"
            ),
            mimeType = MIME_TEXT
        ) { _ ->
            callResource(uri = SKILL_OVERVIEW_URI, mimeType = MIME_TEXT, text = buildSkillOverview())
        }
        server.addResourceTemplate(
            uriTemplate = SKILL_TOOL_TEMPLATE_URI,
            name = "keios-mcp-tool-help",
            description = localText(
                locale,
                "KeiOS MCP 单工具帮助",
                "KeiOS MCP tool help",
                "KeiOS MCP tool help"
            ),
            mimeType = MIME_MARKDOWN
        ) { _, params ->
            val tool = params["tool"].orEmpty()
            callResource(
                uri = SKILL_TOOL_TEMPLATE_URI.replace("{tool}", tool),
                mimeType = MIME_MARKDOWN,
                text = buildToolHelp(tool)
            )
        }
        server.addResource(
            uri = CONFIG_RESOURCE_URI,
            name = "keios-mcp-config-default",
            description = "MCP config JSON (auto mode)",
            mimeType = MIME_JSON
        ) { _ ->
            callResource(
                uri = CONFIG_RESOURCE_URI,
                mimeType = MIME_JSON,
                text = runtimeConfigBuilder(environment.currentState(), "auto", "", "")
            )
        }
        server.addResourceTemplate(
            uriTemplate = CONFIG_TEMPLATE_URI,
            name = "keios-mcp-config-template",
            description = "MCP config JSON by mode",
            mimeType = MIME_JSON
        ) { _, params ->
            val mode = normalizeMcpConfigMode(params["mode"].orEmpty())
            callResource(
                uri = CONFIG_TEMPLATE_URI.replace("{mode}", mode),
                mimeType = MIME_JSON,
                text = runtimeConfigBuilder(environment.currentState(), mode, "", "")
            )
        }
    }

    fun registerPrompt(server: Server) {
        val locale = currentLocale()
        server.addPrompt(
            name = BOOTSTRAP_PROMPT,
            description = localText(
                locale,
                "初始化 KeiOS MCP 工具使用方式。",
                "Initialize KeiOS MCP tool usage.",
                "Initialize KeiOS MCP tool usage."
            ),
            arguments = listOf(
                PromptArgument(
                    name = "task",
                    description = localText(locale, "当前任务", "Current task", "Current task"),
                    required = false,
                    title = localText(locale, "任务", "Task", "Task")
                )
            )
        ) { request ->
            val task = request.arguments?.get("task").orEmpty().trim()
            GetPromptResult(
                description = localText(
                    locale,
                    "KeiOS MCP 启动 Prompt",
                    "KeiOS MCP bootstrap prompt",
                    "KeiOS MCP bootstrap prompt"
                ),
                messages = listOf(
                    PromptMessage(
                        role = Role.User,
                        content = TextContent(buildBootstrapPromptText(task, currentLocale()))
                    )
                )
            )
        }
    }

    fun buildServerInstructions(): String {
        val locale = currentLocale()
        return buildString {
            appendLine(
                localText(
                    locale,
                    "KeiOS 本地 MCP 服务",
                    "KeiOS local MCP server",
                    "KeiOS local MCP server"
                )
            )
            appendLine("- keios.health.ping -> keios.mcp.runtime.status -> keios.github.config.snapshot")
            appendLine("- overview=$SKILL_OVERVIEW_URI")
            appendLine("- skill=$SKILL_RESOURCE_URI")
            appendLine("- toolHelp=$SKILL_TOOL_TEMPLATE_URI")
            appendLine("- workflows=$WORKFLOW_RESOURCE_URI")
            appendLine("- workflowPrompt=$WORKFLOW_PLAN_PROMPT")
            appendLine("- config=$CONFIG_RESOURCE_URI")
        }.trim()
    }

    fun loadSkillMarkdown(): String {
        val locale = currentLocale()
        return runCatching {
            environment.appContext.assets.open(localizedSkillAssetPath(locale))
                .bufferedReader()
                .use { it.readText() }
        }.map { template ->
            renderSkillTemplate(template, locale)
        }.getOrElse {
            buildFallbackSkillMarkdown(locale)
        }
    }

    private fun renderSkillTemplate(template: String, locale: Locale): String {
        val state = environment.currentState()
        val toolList = McpToolCatalog.forLocale(locale).joinToString("\n") { meta ->
            "- `${meta.name}`: ${meta.description}"
        }
        return template
            .replace("{{APP_LABEL}}", environment.appLabel)
            .replace("{{APP_PACKAGE}}", environment.appPackageName)
            .replace(
                "{{APP_VERSION}}",
                "${environment.appVersionName} (${environment.appVersionCode})"
            )
            .replace("{{SERVER_NAME}}", state?.serverName ?: "KeiOS MCP")
            .replace("{{LOCAL_ENDPOINT}}", state?.localEndpoint ?: DEFAULT_ENDPOINT)
            .replace(
                "{{LAN_ENDPOINTS}}",
                state?.lanEndpoints?.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "N/A"
            )
            .replace("{{RESOURCE_SKILL_URI}}", SKILL_RESOURCE_URI)
            .replace("{{RESOURCE_OVERVIEW_URI}}", SKILL_OVERVIEW_URI)
            .replace("{{RESOURCE_WORKFLOWS_URI}}", WORKFLOW_RESOURCE_URI)
            .replace("{{RESOURCE_WORKFLOW_TEMPLATE_URI}}", WORKFLOW_TEMPLATE_URI)
            .replace("{{PROMPT_BOOTSTRAP}}", BOOTSTRAP_PROMPT)
            .replace("{{PROMPT_WORKFLOW_PLAN}}", WORKFLOW_PLAN_PROMPT)
            .replace("{{RESOURCE_CONFIG_URI}}", CONFIG_RESOURCE_URI)
            .replace("{{RESOURCE_CONFIG_TEMPLATE_URI}}", CONFIG_TEMPLATE_URI)
            .replace("{{TOOL_LIST}}", toolList)
    }

    private fun buildFallbackSkillMarkdown(locale: Locale): String {
        return buildString {
            appendLine("# KeiOS MCP Skill")
            appendLine()
            appendLine("## Tools")
            McpToolCatalog.forLocale(locale).forEach { meta ->
                appendLine("- `${meta.name}`: ${meta.description}")
            }
        }.trim()
    }

    private fun buildSkillOverview(): String {
        val state = environment.currentState()
        val locale = currentLocale()
        return buildString {
            appendLine("skillResource=$SKILL_RESOURCE_URI")
            appendLine("skillOverviewResource=$SKILL_OVERVIEW_URI")
            appendLine("skillToolTemplate=$SKILL_TOOL_TEMPLATE_URI")
            appendLine("bootstrapPrompt=$BOOTSTRAP_PROMPT")
            appendLine("workflowResource=$WORKFLOW_RESOURCE_URI")
            appendLine("workflowTemplate=$WORKFLOW_TEMPLATE_URI")
            appendLine("workflowPrompt=$WORKFLOW_PLAN_PROMPT")
            appendLine("language=${locale.toLanguageTag()}")
            appendLine("configResource=$CONFIG_RESOURCE_URI")
            appendLine("configTemplate=$CONFIG_TEMPLATE_URI")
            appendLine("localEndpoint=${state?.localEndpoint ?: DEFAULT_ENDPOINT}")
            if (state?.lanEndpoints?.isNotEmpty() == true) {
                appendLine("lanEndpoints=${state.lanEndpoints.joinToString(",")}")
            }
            appendLine("runtimeTools=${McpToolCatalog.runtimeToolNames.joinToString(",")}")
            appendLine("homeTools=${McpToolCatalog.homeToolNames.joinToString(",")}")
            appendLine("systemTools=${McpToolCatalog.systemToolNames.joinToString(",")}")
            appendLine("osTools=${McpToolCatalog.osToolNames.joinToString(",")}")
            appendLine("githubTools=${McpToolCatalog.githubToolNames.joinToString(",")}")
            appendLine("baTools=${McpToolCatalog.baToolNames.joinToString(",")}")
            appendLine("entrypointTools=${McpToolCatalog.entrypointToolNames.joinToString(",")}")
            appendLine("toolCount=${McpToolCatalog.all.size}")
            appendLine("tools=${McpToolCatalog.all.joinToString(",") { it.name }}")
        }.trim()
    }

    private fun buildToolHelp(tool: String): String {
        val locale = currentLocale()
        val normalized = tool.trim().lowercase(Locale.ROOT)
        val hit = McpToolCatalog.forLocale(locale)
            .firstOrNull { it.name.lowercase(Locale.ROOT) == normalized }
            ?: return buildUnknownToolHelp(tool, locale)
        return buildString {
            appendLine("# ${hit.name}")
            appendLine()
            appendLine(hit.description)
            appendLine()
            appendLine("group=${toolGroup(hit.name)}")
            appendLine("output=key_value_text")
            appendLine("writeRequiresApply=${hit.name.endsWith(".import")}")
            appendLine("readOnly=${hit.readOnly}")
            appendLine("openWorld=${hit.openWorld}")
            appendLine("executionProfile=${hit.executionProfile.name}")
            appendLine("entrypoint=${hit.name in MCP_ENTRYPOINT_TOOLS}")
            if (hit.arguments.isNotEmpty()) {
                appendLine("arguments=${hit.arguments.joinToString(",") { argument -> argument.name }}")
                appendLine("required=${hit.requiredArguments.joinToString(",")}")
                hit.arguments.forEach { argument ->
                    appendLine(
                        buildString {
                            append("argument.${argument.name}=type:${argument.type.wireName}")
                            append(" | required:${argument.required}")
                            if (argument.defaultValue.isNotBlank()) {
                                append(" | default:${argument.defaultValue}")
                            }
                            if (argument.enumValues.isNotEmpty()) {
                                append(" | enum:${argument.enumValues.joinToString("|")}")
                            }
                            if (argument.description.isNotBlank()) {
                                append(" | description:${argument.description}")
                            }
                        }
                    )
                }
            }
            appendLine()
            appendLine("See $SKILL_RESOURCE_URI for grouped flows.")
        }.trim()
    }

    private fun buildUnknownToolHelp(tool: String, locale: Locale): String {
        return buildString {
            appendLine(localText(locale, "# 未知工具", "# Unknown Tool", "# Unknown Tool"))
            appendLine("tool=$tool")
            appendLine("available=${McpToolCatalog.all.joinToString(",") { it.name }}")
        }.trim()
    }

    private fun buildBootstrapPromptText(task: String, locale: Locale): String {
        return buildString {
            appendLine(
                localText(
                    locale,
                    "你正在连接 KeiOS 本地 MCP 服务。",
                    "You are connected to the local KeiOS MCP server.",
                    "You are connected to the local KeiOS MCP server."
                )
            )
            appendLine("1) keios.health.ping")
            appendLine("2) keios.mcp.runtime.status")
            appendLine("3) keios.github.config.snapshot")
            appendLine("4) $SKILL_OVERVIEW_URI")
            appendLine("5) $SKILL_RESOURCE_URI")
            if (task.isNotBlank()) {
                appendLine("task=$task")
            }
        }.trim()
    }

    private fun buildClawSkillGuideText(
        state: McpServerUiState?,
        mode: String,
        endpointOverride: String,
        serverNameOverride: String
    ): String {
        val fixedMode = normalizeMcpConfigMode(mode)
        val configJson = runtimeConfigBuilder(state, fixedMode, endpointOverride, serverNameOverride)
        return buildString {
            appendLine("target=claw")
            appendLine("mode=$fixedMode")
            appendLine("skillResource=$SKILL_RESOURCE_URI")
            appendLine("skillOverviewResource=$SKILL_OVERVIEW_URI")
            appendLine("skillToolTemplate=$SKILL_TOOL_TEMPLATE_URI")
            appendLine("bootstrapPrompt=$BOOTSTRAP_PROMPT")
            appendLine("workflowResource=$WORKFLOW_RESOURCE_URI")
            appendLine("workflowTemplate=$WORKFLOW_TEMPLATE_URI")
            appendLine("workflowPrompt=$WORKFLOW_PLAN_PROMPT")
            appendLine()
            appendLine("## MCP Config")
            appendLine("```json")
            appendLine(configJson)
            appendLine("```")
            appendLine()
            appendLine("## SKILL.md")
            appendLine("```markdown")
            appendLine(loadSkillMarkdown())
            appendLine("```")
        }.trim()
    }

    private fun toolGroup(name: String): String {
        return McpToolCatalog.metaForName(name, currentLocale())?.group ?: "unknown"
    }

    private fun localizedSkillAssetPath(locale: Locale): String {
        return when {
            isSimplifiedChinese(locale) -> "mcp/SKILL.zh-CN.md"
            isJapanese(locale) -> "mcp/SKILL.ja.md"
            else -> "mcp/SKILL.md"
        }
    }

    private fun currentLocale(): Locale {
        return environment.currentLocale()
    }

    private fun isSimplifiedChinese(locale: Locale): Boolean {
        return locale.language.equals("zh", ignoreCase = true)
    }

    private fun isJapanese(locale: Locale): Boolean {
        return locale.language.equals("ja", ignoreCase = true)
    }

    private fun localText(locale: Locale, zh: String, ja: String, en: String): String {
        return when {
            isSimplifiedChinese(locale) -> zh
            isJapanese(locale) -> ja
            else -> en
        }
    }
}
