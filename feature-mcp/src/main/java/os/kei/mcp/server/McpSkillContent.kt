package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import os.kei.core.io.readTextLimitedBlocking
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal class McpSkillContent(
    private val environment: McpToolEnvironment,
    private val runtimeConfigBuilder: (McpServerUiState?, String, String, String) -> String
) {
    private val skillTemplateCache = ConcurrentHashMap<String, String>()

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
        server.addResource(
            uri = SUBAGENT_RESOURCE_URI,
            name = "keios-mcp-subagent",
            description = localText(
                locale,
                "KeiOS MCP 子 Agent 指南",
                "KeiOS MCP sub agent guide",
                "KeiOS MCP sub-agent guide"
            ),
            mimeType = MIME_MARKDOWN
        ) { _ ->
            callResource(
                uri = SUBAGENT_RESOURCE_URI,
                mimeType = MIME_MARKDOWN,
                text = buildSubAgentMarkdown(currentLocale())
            )
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
        server.addResourceTemplate(
            uriTemplate = SKILL_DOMAIN_TEMPLATE_URI,
            name = "keios-mcp-domain-guide",
            description = localText(
                locale,
                "KeiOS MCP 领域指南",
                "KeiOS MCP domain guide",
                "KeiOS MCP domain guide"
            ),
            mimeType = MIME_MARKDOWN
        ) { _, params ->
            val domain = params["domain"].orEmpty()
            callResource(
                uri = SKILL_DOMAIN_TEMPLATE_URI.replace("{domain}", domain),
                mimeType = MIME_MARKDOWN,
                text = buildDomainGuide(domain)
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
        server.addPrompt(
            name = DIAGNOSTICS_PLAN_PROMPT,
            description = localText(
                locale,
                "生成 KeiOS MCP 连接、权限与工具失败排查流程。",
                "Generate a KeiOS MCP diagnostics flow for connection, permission, and tool failures.",
                "Generate a KeiOS MCP diagnostics flow for connection, permission, and tool failures."
            ),
            arguments = listOf(
                PromptArgument(
                    name = "symptom",
                    description = localText(locale, "现象", "Symptom", "Symptom"),
                    required = false,
                    title = localText(locale, "现象", "Symptom", "Symptom")
                )
            )
        ) { request ->
            val symptom = request.arguments?.get("symptom").orEmpty().trim()
            GetPromptResult(
                description = localText(
                    locale,
                    "KeiOS MCP 排障计划",
                    "KeiOS MCP diagnostics plan",
                    "KeiOS MCP diagnostics plan"
                ),
                messages = listOf(
                    PromptMessage(
                        role = Role.User,
                        content = TextContent(buildDiagnosticsPromptText(symptom, currentLocale()))
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
            appendLine("- subAgent=$SUBAGENT_RESOURCE_URI")
            appendLine("- toolHelp=$SKILL_TOOL_TEMPLATE_URI")
            appendLine("- domainHelp=$SKILL_DOMAIN_TEMPLATE_URI")
            appendLine("- workflows=$WORKFLOW_RESOURCE_URI")
            appendLine("- workflowPrompt=$WORKFLOW_PLAN_PROMPT")
            appendLine("- diagnosticsPrompt=$DIAGNOSTICS_PLAN_PROMPT")
            appendLine("- config=$CONFIG_RESOURCE_URI")
        }.trim()
    }

    fun loadSkillMarkdown(): String {
        val locale = currentLocale()
        val path = localizedSkillAssetPath(locale)
        return runCatching {
            skillTemplateCache.getOrPut(path) {
                environment.appContext.assets.open(path)
                    .use { input ->
                        input.readTextLimitedBlocking(MCP_SKILL_ASSET_MAX_BYTES).text
                    }
            }
        }.map { template ->
            renderSkillTemplate(template, locale)
        }.getOrElse {
            buildFallbackSkillMarkdown(locale)
        }
    }

    private fun renderSkillTemplate(template: String, locale: Locale): String {
        val state = environment.currentState()
        val tools = McpToolCatalog.forLocale(locale)
        val toolList = tools.joinToString("\n") { meta -> "- `${meta.name}`: ${meta.description}" }
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
            .replace("{{RESOURCE_SUBAGENT_URI}}", SUBAGENT_RESOURCE_URI)
            .replace("{{RESOURCE_OVERVIEW_URI}}", SKILL_OVERVIEW_URI)
            .replace("{{RESOURCE_TOOL_TEMPLATE_URI}}", SKILL_TOOL_TEMPLATE_URI)
            .replace("{{RESOURCE_DOMAIN_TEMPLATE_URI}}", SKILL_DOMAIN_TEMPLATE_URI)
            .replace("{{RESOURCE_WORKFLOWS_URI}}", WORKFLOW_RESOURCE_URI)
            .replace("{{RESOURCE_WORKFLOW_TEMPLATE_URI}}", WORKFLOW_TEMPLATE_URI)
            .replace("{{PROMPT_BOOTSTRAP}}", BOOTSTRAP_PROMPT)
            .replace("{{PROMPT_WORKFLOW_PLAN}}", WORKFLOW_PLAN_PROMPT)
            .replace("{{PROMPT_DIAGNOSTICS_PLAN}}", DIAGNOSTICS_PLAN_PROMPT)
            .replace("{{RESOURCE_CONFIG_URI}}", CONFIG_RESOURCE_URI)
            .replace("{{RESOURCE_CONFIG_TEMPLATE_URI}}", CONFIG_TEMPLATE_URI)
            .replace(
                "{{ENTRYPOINT_TOOLS}}",
                buildToolSectionList(tools, McpToolVisibility.Entrypoint)
            )
            .replace("{{WORKFLOW_TOOLS}}", buildToolSectionList(tools, McpToolVisibility.Workflow))
            .replace("{{ADVANCED_TOOLS}}", buildToolSectionList(tools, McpToolVisibility.Advanced))
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
            appendLine("subAgentResource=$SUBAGENT_RESOURCE_URI")
            appendLine("skillOverviewResource=$SKILL_OVERVIEW_URI")
            appendLine("skillToolTemplate=$SKILL_TOOL_TEMPLATE_URI")
            appendLine("skillDomainTemplate=$SKILL_DOMAIN_TEMPLATE_URI")
            appendLine("bootstrapPrompt=$BOOTSTRAP_PROMPT")
            appendLine("workflowResource=$WORKFLOW_RESOURCE_URI")
            appendLine("workflowTemplate=$WORKFLOW_TEMPLATE_URI")
            appendLine("workflowPrompt=$WORKFLOW_PLAN_PROMPT")
            appendLine("diagnosticsPrompt=$DIAGNOSTICS_PLAN_PROMPT")
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
            appendLine("devTools=${McpToolCatalog.devToolNames.joinToString(",")}")
            appendLine("entrypointTools=${McpToolCatalog.entrypointToolNames.joinToString(",")}")
            appendLine("domainGuides=${listOf("runtime", "github", "os", "ba", "dev").joinToString(",")}")
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
            appendLine("group=${hit.group}")
            appendLine("visibility=${hit.visibility.wireName}")
            appendLine("maturity=${hit.maturity.wireName}")
            appendLine("output=${hit.outputContract.wireName}")
            appendLine("writeRequiresApply=${hit.name.endsWith(".import")}")
            appendLine("readOnly=${hit.readOnly}")
            appendLine("openWorld=${hit.openWorld}")
            appendLine("executionProfile=${hit.executionProfile.name}")
            appendLine("entrypoint=${hit.name in MCP_ENTRYPOINT_TOOLS}")
            if (hit.recommendedFor.isNotEmpty()) {
                appendLine("recommendedFor=${hit.recommendedFor.joinToString(",")}")
            }
            if (hit.workflowTags.isNotEmpty()) {
                appendLine("workflowTags=${hit.workflowTags.joinToString(",")}")
            }
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
            appendLine("6) $SUBAGENT_RESOURCE_URI")
            appendLine("7) $WORKFLOW_RESOURCE_URI")
            if (task.isNotBlank()) {
                appendLine("task=$task")
            }
        }.trim()
    }

    private fun buildDiagnosticsPromptText(symptom: String, locale: Locale): String {
        return buildString {
            appendLine(
                localText(
                    locale,
                    "请为 KeiOS MCP 生成一套排障步骤。",
                    "Create a diagnostics flow for KeiOS MCP.",
                    "Create a diagnostics flow for KeiOS MCP."
                )
            )
            appendLine("1) keios.health.ping")
            appendLine("2) keios.mcp.runtime.status")
            appendLine("3) keios.mcp.runtime.logs(limit=80)")
            appendLine("4) keios.shizuku.status")
            appendLine("5) $SKILL_DOMAIN_TEMPLATE_URI")
            appendLine("6) $SKILL_TOOL_TEMPLATE_URI")
            if (symptom.isNotBlank()) {
                appendLine("symptom=$symptom")
            }
        }.trim()
    }

    private fun buildClawSkillGuideText(
        state: McpServerUiState?,
        mode: String,
        endpointOverride: String,
        serverNameOverride: String
    ): String {
        val locale = currentLocale()
        val fixedMode = normalizeMcpConfigMode(mode)
        val configJson = runtimeConfigBuilder(state, fixedMode, endpointOverride, serverNameOverride)
        return buildString {
            appendLine("target=claw")
            appendLine("mode=$fixedMode")
            appendLine("skillResource=$SKILL_RESOURCE_URI")
            appendLine("subAgentResource=$SUBAGENT_RESOURCE_URI")
            appendLine("skillOverviewResource=$SKILL_OVERVIEW_URI")
            appendLine("skillToolTemplate=$SKILL_TOOL_TEMPLATE_URI")
            appendLine("skillDomainTemplate=$SKILL_DOMAIN_TEMPLATE_URI")
            appendLine("bootstrapPrompt=$BOOTSTRAP_PROMPT")
            appendLine("workflowResource=$WORKFLOW_RESOURCE_URI")
            appendLine("workflowTemplate=$WORKFLOW_TEMPLATE_URI")
            appendLine("workflowPrompt=$WORKFLOW_PLAN_PROMPT")
            appendLine("diagnosticsPrompt=$DIAGNOSTICS_PLAN_PROMPT")
            appendLine()
            appendLine("## Claw Setup")
            appendLine(
                "1. " + localText(
                    locale,
                    "在 Claw App 的 MCP 服务器列表里添加下面的 MCP Config。",
                    "Claw App の MCP サーバー一覧に下の MCP Config を追加します。",
                    "Add the MCP Config below in the Claw App MCP server list."
                )
            )
            appendLine(
                "2. " + localText(
                    locale,
                    "读取 skillResource，并将 SKILL.md 注册为 KeiOS MCP Skill。",
                    "skillResource を読み取り、SKILL.md を KeiOS MCP Skill として登録します。",
                    "Read skillResource and register SKILL.md as the KeiOS MCP Skill."
                )
            )
            appendLine(
                "3. " + localText(
                    locale,
                    "Agent 模式下读取 subAgentResource，并让 Claw 主 Agent 将 KeiOS、GitHub、OS、BA 相关任务委派给 KeiOS MCP 子 Agent。",
                    "Agent mode では subAgentResource を読み取り、Claw main Agent が KeiOS、GitHub、OS、BA 関連タスクを KeiOS MCP sub agent へ委譲します。",
                    "In Agent mode, read subAgentResource and let the Claw main Agent delegate KeiOS, GitHub, OS, and BA tasks to the KeiOS MCP sub agent."
                )
            )
            appendLine(
                "4. " + localText(
                    locale,
                    "需要定时任务或组合技能时读取 workflowResource。",
                    "定期タスクや複合 skill では workflowResource を読み取ります。",
                    "Read workflowResource for scheduled tasks or composed skills."
                )
            )
            appendLine(
                "5. " + localText(
                    locale,
                    "每次 KeiOS 升级后，在 Claw 删除旧服务器并重新添加，刷新工具缓存。",
                    "KeiOS 更新後は Claw で古いサーバーを削除して追加し直し、tools キャッシュを更新します。",
                    "After each KeiOS upgrade, delete the old server in Claw and add it again to refresh cached tools."
                )
            )
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
            appendLine()
            appendLine("## SUBAGENT.md")
            appendLine("```markdown")
            appendLine(buildSubAgentMarkdown(locale))
            appendLine("```")
        }.trim()
    }

    fun buildSubAgentMarkdown(locale: Locale = currentLocale()): String {
        val state = environment.currentState()
        val entrypointTools = McpToolCatalog.forLocale(locale)
            .filter { it.visibility == McpToolVisibility.Entrypoint }
        return buildString {
            appendLine("# KeiOS MCP Sub Agent")
            appendLine()
            appendLine(
                localText(
                    locale,
                    "这是给 Claw 主 Agent 读取的 KeiOS 子 Agent 指南。主 Agent 可以把 KeiOS 本地服务、GitHub 追踪、Actions、OS 卡片、Shell、Blue Archive 与开发验证相关任务委派给此子 Agent。",
                    "これは Claw main Agent が読む KeiOS sub agent ガイドです。main Agent は KeiOS local service、GitHub tracking、Actions、OS cards、Shell、Blue Archive、development validation 関連タスクをこの sub agent に委譲できます。",
                    "This guide is for the Claw main Agent. The main Agent can delegate KeiOS local service, GitHub tracking, Actions, OS cards, shell, Blue Archive, and development validation tasks to this sub agent."
                )
            )
            appendLine()
            appendLine("subAgentResource=$SUBAGENT_RESOURCE_URI")
            appendLine("skillResource=$SKILL_RESOURCE_URI")
            appendLine("skillOverviewResource=$SKILL_OVERVIEW_URI")
            appendLine("workflowResource=$WORKFLOW_RESOURCE_URI")
            appendLine("workflowTemplate=$WORKFLOW_TEMPLATE_URI")
            appendLine("configResource=$CONFIG_RESOURCE_URI")
            appendLine("bootstrapPrompt=$BOOTSTRAP_PROMPT")
            appendLine("workflowPrompt=$WORKFLOW_PLAN_PROMPT")
            appendLine("diagnosticsPrompt=$DIAGNOSTICS_PLAN_PROMPT")
            appendLine("localEndpoint=${state?.localEndpoint ?: DEFAULT_ENDPOINT}")
            if (state?.lanEndpoints?.isNotEmpty() == true) {
                appendLine("lanEndpoints=${state.lanEndpoints.joinToString(",")}")
            }
            appendLine()
            appendLine("## Suggested Claw Agent Card")
            appendLine()
            appendLine("```yaml")
            appendLine("name: KeiOS MCP Agent")
            appendLine("uri: $SUBAGENT_RESOURCE_URI")
            appendLine("mcpServer: ${state?.serverName ?: "KeiOS MCP"}")
            appendLine("modelRole: tool-operator")
            appendLine("defaultResources:")
            appendLine("  - $SKILL_RESOURCE_URI")
            appendLine("  - $SKILL_OVERVIEW_URI")
            appendLine("  - $WORKFLOW_RESOURCE_URI")
            appendLine("handoffFrom: Claw main Agent")
            appendLine("```")
            appendLine()
            appendLine("## When To Delegate")
            appendLine()
            listOf(
                localText(
                    locale,
                    "用户询问 KeiOS、MCP 服务、Token、endpoint、日志、连接或升级后的工具刷新。",
                    "ユーザーが KeiOS、MCP service、Token、endpoint、logs、接続、更新後の tools refresh を求めたとき。",
                    "The user asks about KeiOS, MCP service state, token, endpoint, logs, connectivity, or post-upgrade tool refresh."
                ),
                localText(
                    locale,
                    "用户需要 GitHub / Git / 订阅项目追踪、版本检查、Actions、Star List 导入或 APK 验证。",
                    "ユーザーが GitHub / Git / subscription tracking、version checks、Actions、Star List import、APK verification を求めたとき。",
                    "The user needs GitHub, Git, or subscription tracking, version checks, Actions, Star List import, or APK verification."
                ),
                localText(
                    locale,
                    "用户需要 OS Activity、Shell 卡片、Shizuku 状态、系统 TopInfo 或卡片导入导出。",
                    "ユーザーが OS Activity、Shell cards、Shizuku status、system TopInfo、card import/export を求めたとき。",
                    "The user needs OS Activity, shell cards, Shizuku state, system TopInfo, or card import/export."
                ),
                localText(
                    locale,
                    "用户需要 Blue Archive AP、咖啡厅、活动日历、卡池、图鉴缓存或 BGM 收藏信息。",
                    "ユーザーが Blue Archive AP、Cafe、calendar、pool、guide cache、BGM favorites を求めたとき。",
                    "The user needs Blue Archive AP, Cafe, calendar, pool, guide cache, or BGM favorites."
                )
            ).forEach { item -> appendLine("- $item") }
            appendLine()
            appendLine("## Startup Flow")
            appendLine()
            appendLine("1. `keios.health.ping`")
            appendLine("2. `keios.mcp.runtime.status`")
            appendLine("3. Read `$SKILL_OVERVIEW_URI`")
            appendLine("4. Read `$SKILL_RESOURCE_URI` for the full tool contract")
            appendLine("5. Read `$SKILL_DOMAIN_TEMPLATE_URI` for domain-specific tasks")
            appendLine()
            appendLine("## Operating Contract")
            appendLine()
            listOf(
                localText(
                    locale,
                    "优先调用入口工具理解状态，再读取领域资源或单工具帮助，再调用底层工具。",
                    "入口ツールで状態を理解し、domain resource または single-tool help を読み、下位ツールを呼びます。",
                    "Call entrypoint tools first, then read domain resources or single-tool help, then call lower-level tools."
                ),
                localText(
                    locale,
                    "导入、清理、写入类工具先用预览模式；用户明确确认后再传 `apply=true`。",
                    "import、clear、write 系ツールは preview mode から始め、ユーザー確認後に `apply=true` を渡します。",
                    "Use preview mode for import, clear, and write tools; pass `apply=true` after explicit user confirmation."
                ),
                localText(
                    locale,
                    "联网和深扫工具带上 limit、repoFilter 或 scope，优先返回可读摘要。",
                    "network / deep scan tools には limit、repoFilter、scope を付け、読みやすい summary を優先します。",
                    "Use limit, repoFilter, or scope for network and deep-scan tools, and return a readable summary first."
                ),
                localText(
                    locale,
                    "输出给主 Agent 时包含已调用工具、关键结果、风险、建议下一步。",
                    "main Agent へ返すときは called tools、key results、risks、next steps を含めます。",
                    "Return called tools, key results, risks, and next steps to the main Agent."
                )
            ).forEach { item -> appendLine("- $item") }
            appendLine()
            appendLine("## Entrypoint Tools")
            appendLine()
            entrypointTools.forEach { meta ->
                appendLine("- `${meta.name}`: ${meta.description}")
            }
            appendLine()
            appendLine("## Escalate To Main Agent")
            appendLine()
            listOf(
                localText(
                    locale,
                    "需要用户授权 Shizuku、本地网络权限、Token 重配或 Claw 重新添加服务器。",
                    "Shizuku permission、local network permission、Token reconfiguration、Claw server re-add が必要なとき。",
                    "User action is required for Shizuku permission, local network permission, token reconfiguration, or Claw server re-add."
                ),
                localText(
                    locale,
                    "写入操作会覆盖、清理或导入用户数据。",
                    "write operation がユーザーデータの overwrite、clear、import を行うとき。",
                    "A write operation may overwrite, clear, or import user data."
                ),
                localText(
                    locale,
                    "任务需要用户选择账号、服务器、追踪条目、目标文件或通知策略。",
                    "task が account、server、tracking item、target file、notification policy の選択を必要とするとき。",
                    "The task requires the user to choose an account, server, tracked item, target file, or notification policy."
                )
            ).forEach { item -> appendLine("- $item") }
        }.trim()
    }

    private fun buildDomainGuide(domain: String): String {
        val locale = currentLocale()
        val normalized = domain.trim().lowercase(Locale.ROOT)
        val tools = McpToolCatalog.forLocale(locale).filter { it.group == normalized }
        if (tools.isEmpty()) {
            return buildString {
                appendLine(localText(locale, "# 未知领域", "# Unknown Domain", "# Unknown Domain"))
                appendLine("domain=$domain")
                appendLine("available=runtime,home,system,os,github,ba,dev")
            }.trim()
        }
        return buildString {
            appendLine("# KeiOS MCP ${normalized.replaceFirstChar { it.uppercase(Locale.ROOT) }}")
            appendLine()
            appendLine("domain=$normalized")
            appendLine("toolCount=${tools.size}")
            appendLine(
                "entrypoints=${
                    tools.filter { it.visibility == McpToolVisibility.Entrypoint }
                        .joinToString(",") { it.name }
                }"
            )
            appendLine()
            appendLine("## Recommended")
            tools.filter { it.visibility == McpToolVisibility.Entrypoint || it.recommendedFor.isNotEmpty() }
                .forEach { meta -> appendLine("- `${meta.name}`: ${meta.description}") }
            appendLine()
            appendLine("## Tools")
            tools.forEach { meta ->
                appendLine("- `${meta.name}` (${meta.visibility.wireName}, ${meta.executionProfile.name}): ${meta.description}")
            }
        }.trim()
    }

    private fun buildToolSectionList(
        tools: List<McpToolMeta>,
        visibility: McpToolVisibility
    ): String {
        return tools
            .filter { it.visibility == visibility }
            .joinToString("\n") { meta -> "- `${meta.name}`: ${meta.description}" }
            .ifBlank { "- N/A" }
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

    private companion object {
        private const val MCP_SKILL_ASSET_MAX_BYTES = 768L * 1024L
    }
}
