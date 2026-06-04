package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.util.Locale

internal class McpWorkflowContent(
    private val environment: McpToolEnvironment
) {
    private data class WorkflowBlueprint(
        val id: String,
        val titleEn: String,
        val titleZh: String,
        val titleJa: String,
        val cadenceHintEn: String,
        val cadenceHintZh: String,
        val cadenceHintJa: String,
        val summaryEn: String,
        val summaryZh: String,
        val summaryJa: String,
        val tools: List<String>,
        val stepsEn: List<String>,
        val stepsZh: List<String>,
        val stepsJa: List<String>,
        val outputEn: List<String>,
        val outputZh: List<String>,
        val outputJa: List<String>
    ) {
        fun title(locale: Locale): String = localText(locale, titleZh, titleJa, titleEn)
        fun cadenceHint(locale: Locale): String =
            localText(locale, cadenceHintZh, cadenceHintJa, cadenceHintEn)

        fun summary(locale: Locale): String = localText(locale, summaryZh, summaryJa, summaryEn)
        fun steps(locale: Locale): List<String> = when {
            isSimplifiedChinese(locale) -> stepsZh
            isJapanese(locale) -> stepsJa
            else -> stepsEn
        }

        fun output(locale: Locale): List<String> = when {
            isSimplifiedChinese(locale) -> outputZh
            isJapanese(locale) -> outputJa
            else -> outputEn
        }
    }

    fun registerTools(server: Server) {
        server.addMcpTextTool(environment, name = "keios.mcp.workflow.blueprints") { request ->
            val mode = argString(request.arguments?.get("mode")).trim()
            val workflow = argString(request.arguments?.get("workflow")).trim()
            val locale = currentLocale()
            when (mode.lowercase(Locale.ROOT)) {
                "detail", "markdown" -> buildBlueprintDetailText(workflow, locale)
                "skill" -> buildWorkflowSkillText(locale)
                else -> buildBlueprintListText(locale)
            }
        }
    }

    fun registerResources(server: Server) {
        val locale = currentLocale()
        server.addResource(
            uri = WORKFLOW_RESOURCE_URI,
            name = "keios-mcp-workflows",
            description = localText(
                locale,
                "KeiOS MCP 工作流蓝图",
                "KeiOS MCP workflow blueprints",
                "KeiOS MCP workflow blueprints"
            ),
            mimeType = MIME_MARKDOWN
        ) { _ ->
            callResource(
                uri = WORKFLOW_RESOURCE_URI,
                mimeType = MIME_MARKDOWN,
                text = buildWorkflowSkillText(currentLocale())
            )
        }
        server.addResourceTemplate(
            uriTemplate = WORKFLOW_TEMPLATE_URI,
            name = "keios-mcp-workflow-detail",
            description = localText(
                locale,
                "KeiOS MCP 单个工作流蓝图",
                "KeiOS MCP workflow detail",
                "KeiOS MCP workflow detail"
            ),
            mimeType = MIME_MARKDOWN
        ) { _, params ->
            val workflow = params["workflow"].orEmpty()
            callResource(
                uri = WORKFLOW_TEMPLATE_URI.replace("{workflow}", workflow),
                mimeType = MIME_MARKDOWN,
                text = buildBlueprintMarkdown(workflow, currentLocale())
            )
        }
    }

    fun registerPrompts(server: Server) {
        val locale = currentLocale()
        server.addPrompt(
            name = WORKFLOW_PLAN_PROMPT,
            description = localText(
                locale,
                "为 Claw 生成 KeiOS MCP 定时任务或组合技能计划。",
                "Generate a KeiOS MCP scheduled workflow or composed skill plan for Claw.",
                "Generate a KeiOS MCP scheduled workflow or composed skill plan for Claw."
            ),
            arguments = listOf(
                PromptArgument(
                    name = "goal",
                    description = localText(locale, "目标", "Goal", "Goal"),
                    required = true,
                    title = localText(locale, "目标", "Goal", "Goal")
                ),
                PromptArgument(
                    name = "cadence",
                    description = localText(
                        locale,
                        "执行频率，例如每天 09:00 或每 3 小时。",
                        "Cadence, such as daily at 09:00 or every 3 hours.",
                        "Cadence, such as daily at 09:00 or every 3 hours."
                    ),
                    required = false,
                    title = localText(locale, "频率", "Cadence", "Cadence")
                ),
                PromptArgument(
                    name = "workflow",
                    description = localText(locale, "蓝图 id", "Blueprint id", "Blueprint id"),
                    required = false,
                    title = localText(locale, "蓝图", "Blueprint", "Blueprint")
                ),
                PromptArgument(
                    name = "delivery",
                    description = localText(locale, "输出方式", "Delivery", "Delivery"),
                    required = false,
                    title = localText(locale, "输出", "Delivery", "Delivery")
                )
            )
        ) { request ->
            val goal = request.arguments?.get("goal").orEmpty().trim()
            val cadence = request.arguments?.get("cadence").orEmpty().trim()
            val workflow = request.arguments?.get("workflow").orEmpty().trim()
            val delivery = request.arguments?.get("delivery").orEmpty().trim()
            GetPromptResult(
                description = localText(
                    locale,
                    "KeiOS MCP 工作流计划",
                    "KeiOS MCP workflow plan",
                    "KeiOS MCP workflow plan"
                ),
                messages = listOf(
                    PromptMessage(
                        role = Role.User,
                        content = TextContent(
                            buildWorkflowPlanPromptText(
                                goal = goal,
                                cadence = cadence,
                                workflow = workflow,
                                delivery = delivery,
                                locale = currentLocale()
                            )
                        )
                    )
                )
            )
        }
    }

    fun buildWorkflowSkillText(locale: Locale = currentLocale()): String {
        return buildString {
            appendLine("# KeiOS MCP Workflows")
            appendLine()
            appendLine(
                localText(
                    locale,
                    "这些蓝图用于 Claw 侧创建定时任务或组合技能。KeiOS MCP 提供工具、资源与 Prompt，任务调度由客户端保存和触发。",
                    "These blueprints help Claw create scheduled tasks or composed skills. KeiOS MCP provides tools, resources, and prompts; the client stores and triggers schedules.",
                    "These blueprints help Claw create scheduled tasks or composed skills. KeiOS MCP provides tools, resources, and prompts; the client stores and triggers schedules."
                )
            )
            appendLine()
            appendLine("workflowResource=$WORKFLOW_RESOURCE_URI")
            appendLine("workflowTemplate=$WORKFLOW_TEMPLATE_URI")
            appendLine("workflowPrompt=$WORKFLOW_PLAN_PROMPT")
            appendLine("workflowTool=keios.mcp.workflow.blueprints")
            appendLine()
            blueprints.forEach { blueprint ->
                appendLine("## ${blueprint.title(locale)}")
                appendLine()
                appendLine("id=${blueprint.id}")
                appendLine("cadenceHint=${blueprint.cadenceHint(locale)}")
                appendLine("tools=${blueprint.tools.joinToString(",")}")
                appendLine()
                appendLine(blueprint.summary(locale))
                appendLine()
                appendLine(localText(locale, "步骤：", "Steps:", "Steps:"))
                blueprint.steps(locale).forEachIndexed { index, step ->
                    appendLine("${index + 1}. $step")
                }
                appendLine()
                appendLine(localText(locale, "输出：", "Output:", "Output:"))
                blueprint.output(locale).forEach { item ->
                    appendLine("- $item")
                }
                appendLine()
            }
        }.trim()
    }

    private fun buildBlueprintListText(locale: Locale): String {
        return buildString {
            appendLine("ok=true")
            appendLine("count=${blueprints.size}")
            appendLine("workflowResource=$WORKFLOW_RESOURCE_URI")
            appendLine("workflowTemplate=$WORKFLOW_TEMPLATE_URI")
            appendLine("workflowPrompt=$WORKFLOW_PLAN_PROMPT")
            blueprints.forEachIndexed { index, blueprint ->
                appendLine(
                    "workflow[$index]=id:${blueprint.id} | title:${blueprint.title(locale)} | cadence:${
                        blueprint.cadenceHint(
                            locale
                        )
                    } | tools:${blueprint.tools.joinToString(",")}"
                )
            }
        }.trim()
    }

    private fun buildBlueprintDetailText(workflow: String, locale: Locale): String {
        val blueprint = findBlueprint(workflow)
            ?: return "ok=false\nmessage=workflow_not_found\navailable=${blueprints.joinToString(",") { it.id }}"
        return buildString {
            appendLine("ok=true")
            appendLine("id=${blueprint.id}")
            appendLine("title=${blueprint.title(locale)}")
            appendLine("cadenceHint=${blueprint.cadenceHint(locale)}")
            appendLine("tools=${blueprint.tools.joinToString(",")}")
            appendLine("resource=${WORKFLOW_TEMPLATE_URI.replace("{workflow}", blueprint.id)}")
            blueprint.steps(locale).forEachIndexed { index, step ->
                appendLine("step[$index]=$step")
            }
            blueprint.output(locale).forEachIndexed { index, output ->
                appendLine("output[$index]=$output")
            }
        }.trim()
    }

    private fun buildBlueprintMarkdown(workflow: String, locale: Locale): String {
        val blueprint = findBlueprint(workflow) ?: return buildString {
            appendLine("# Unknown Workflow")
            appendLine()
            appendLine("workflow=$workflow")
            appendLine("available=${blueprints.joinToString(",") { it.id }}")
        }.trim()
        return buildString {
            appendLine("# ${blueprint.title(locale)}")
            appendLine()
            appendLine("id=${blueprint.id}")
            appendLine("cadenceHint=${blueprint.cadenceHint(locale)}")
            appendLine("tools=${blueprint.tools.joinToString(",")}")
            appendLine()
            appendLine(blueprint.summary(locale))
            appendLine()
            appendLine("## Steps")
            blueprint.steps(locale).forEachIndexed { index, step ->
                appendLine("${index + 1}. $step")
            }
            appendLine()
            appendLine("## Output")
            blueprint.output(locale).forEach { output ->
                appendLine("- $output")
            }
        }.trim()
    }

    private fun buildWorkflowPlanPromptText(
        goal: String,
        cadence: String,
        workflow: String,
        delivery: String,
        locale: Locale
    ): String {
        return buildString {
            appendLine(
                localText(
                    locale,
                    "你正在为 KeiOS MCP 创建 Claw 定时任务或组合技能。",
                    "Create a Claw scheduled task or composed skill for KeiOS MCP.",
                    "Create a Claw scheduled task or composed skill for KeiOS MCP."
                )
            )
            appendLine("goal=${goal.ifBlank { "unspecified" }}")
            appendLine("cadence=${cadence.ifBlank { "client_decides" }}")
            appendLine("workflow=${workflow.ifBlank { "auto" }}")
            appendLine("delivery=${delivery.ifBlank { "markdown_summary" }}")
            appendLine()
            appendLine("Read $WORKFLOW_RESOURCE_URI first.")
            if (workflow.isNotBlank()) {
                appendLine("Then read ${WORKFLOW_TEMPLATE_URI.replace("{workflow}", workflow)}.")
            }
            appendLine("Call tools in the listed order, keep writes behind explicit apply=true, and keep network refreshes bounded by limit/filter arguments.")
            appendLine("For scheduled tasks, store the schedule in the client and call KeiOS MCP only at execution time.")
            appendLine("Return a dry-run plan, required tools, cadence, failure handling, and final user-facing output.")
        }.trim()
    }

    private fun findBlueprint(id: String): WorkflowBlueprint? {
        val normalized = id.trim().lowercase(Locale.ROOT)
        return blueprints.firstOrNull { it.id.equals(normalized, ignoreCase = true) }
    }

    private fun currentLocale(): Locale {
        return environment.currentLocale()
    }

    private companion object {
        val blueprints = listOf(
            WorkflowBlueprint(
                id = "github-update-watch",
                titleEn = "GitHub update watch",
                titleZh = "GitHub 更新巡检",
                titleJa = "GitHub update watch",
                cadenceHintEn = "Every 3 to 6 hours, or follow the app refresh interval.",
                cadenceHintZh = "每 3 到 6 小时，或跟随 App 刷新间隔。",
                cadenceHintJa = "Every 3 to 6 hours, or follow the app refresh interval.",
                summaryEn = "Audit tracked GitHub and subscription projects, then report update, failure, and pre-release states.",
                summaryZh = "巡检 GitHub 与订阅项目追踪项，输出更新、失败与预发行状态。",
                summaryJa = "Audit tracked GitHub and subscription projects, then report update, failure, and pre-release states.",
                tools = listOf(
                    "keios.github.config.snapshot",
                    "keios.github.tracks.summary",
                    "keios.github.tracks.check"
                ),
                stepsEn = listOf(
                    "Read config and cache summary.",
                    "Run cache summary with filterMode=update_available, then network check with onlyUpdates=true when fresh data is needed.",
                    "Report changed apps, failed checks, and direct_apk remote health hints."
                ),
                stepsZh = listOf(
                    "读取配置与缓存摘要。",
                    "先用 filterMode=update_available 读取缓存摘要，需要新数据时再联网 onlyUpdates=true 检查。",
                    "输出有变化的 App、检查失败项与 direct_apk 远端健康提示。"
                ),
                stepsJa = listOf(
                    "Read config and cache summary.",
                    "Run cache summary with filterMode=update_available, then network check with onlyUpdates=true when fresh data is needed.",
                    "Report changed apps, failed checks, and direct_apk remote health hints."
                ),
                outputEn = listOf("updated apps", "failed checks", "next action suggestion"),
                outputZh = listOf("可更新 App", "检查失败项", "下一步处理建议"),
                outputJa = listOf("updated apps", "failed checks", "next action suggestion")
            ),
            WorkflowBlueprint(
                id = "github-actions-watch",
                titleEn = "GitHub Actions watch",
                titleZh = "GitHub Actions 巡检",
                titleJa = "GitHub Actions watch",
                cadenceHintEn = "15 minutes to 3 hours, matching each track actionsUpdateIntervalMode.",
                cadenceHintZh = "15 分钟到 3 小时，优先匹配每个追踪项的 actionsUpdateIntervalMode。",
                cadenceHintJa = "15 minutes to 3 hours, matching each track actionsUpdateIntervalMode.",
                summaryEn = "Refresh recommended Actions runs for enabled tracks and report newer Android artifacts.",
                summaryZh = "刷新已开启 Actions 检查的追踪项，报告新的 Android artifact。",
                summaryJa = "Refresh recommended Actions runs for enabled tracks and report newer Android artifacts.",
                tools = listOf(
                    "keios.github.tracks.list",
                    "keios.github.actions.recommended"
                ),
                stepsEn = listOf(
                    "List tracks with filterMode=actions_check_enabled.",
                    "Call recommended runs with refresh=true only for the intended cadence window.",
                    "Use actionsIntervalMode and actionsIntervalMinutes from each row to decide the next client schedule."
                ),
                stepsZh = listOf(
                    "用 filterMode=actions_check_enabled 列出目标追踪项。",
                    "只在计划窗口内用 refresh=true 刷新推荐 run。",
                    "根据每行 actionsIntervalMode 与 actionsIntervalMinutes 规划客户端下次执行时间。"
                ),
                stepsJa = listOf(
                    "List tracks with filterMode=actions_check_enabled.",
                    "Call recommended runs with refresh=true only for the intended cadence window.",
                    "Use actionsIntervalMode and actionsIntervalMinutes from each row to decide the next client schedule."
                ),
                outputEn = listOf("newer runs", "artifact counts", "next schedule hint"),
                outputZh = listOf("新的 run", "artifact 数量", "下次执行建议"),
                outputJa = listOf("newer runs", "artifact counts", "next schedule hint")
            ),
            WorkflowBlueprint(
                id = "ba-daily-brief",
                titleEn = "Blue Archive daily brief",
                titleZh = "Blue Archive 每日简报",
                titleJa = "Blue Archive daily brief",
                cadenceHintEn = "Daily near login time.",
                cadenceHintZh = "每天接近登录游戏的时间。",
                cadenceHintJa = "Daily near login time.",
                summaryEn = "Summarize AP, Cafe, calendar, pool, and guide cache states.",
                summaryZh = "汇总 AP、咖啡厅、活动日历、卡池与学生图鉴缓存状态。",
                summaryJa = "Summarize AP, Cafe, calendar, pool, and guide cache states.",
                tools = listOf(
                    "keios.ba.snapshot",
                    "keios.ba.calendar.cache",
                    "keios.ba.pool.cache",
                    "keios.ba.guide.catalog.cache"
                ),
                stepsEn = listOf(
                    "Read BA snapshot.",
                    "Read calendar and pool cache with the current server index.",
                    "Read guide catalog cache only when the brief needs student-guide context."
                ),
                stepsZh = listOf(
                    "读取 BA 快照。",
                    "按当前服务器读取活动日历与卡池缓存。",
                    "需要学生图鉴上下文时再读取图鉴目录缓存。"
                ),
                stepsJa = listOf(
                    "Read BA snapshot.",
                    "Read calendar and pool cache with the current server index.",
                    "Read guide catalog cache only when the brief needs student-guide context."
                ),
                outputEn = listOf("AP and Cafe status", "current events", "cache freshness"),
                outputZh = listOf("AP 与咖啡厅状态", "当前活动", "缓存新鲜度"),
                outputJa = listOf("AP and Cafe status", "current events", "cache freshness")
            ),
            WorkflowBlueprint(
                id = "os-card-backup",
                titleEn = "OS card backup",
                titleZh = "OS 卡片备份",
                titleJa = "OS card backup",
                cadenceHintEn = "Weekly or before major app changes.",
                cadenceHintZh = "每周一次，或在大改 App 前执行。",
                cadenceHintJa = "Weekly or before major app changes.",
                summaryEn = "Export Activity and shell cards, then keep the JSON as a client-side backup artifact.",
                summaryZh = "导出 Activity 与 Shell 卡片 JSON，并由客户端保存为备份产物。",
                summaryJa = "Export Activity and shell cards, then keep the JSON as a client-side backup artifact.",
                tools = listOf(
                    "keios.os.cards.snapshot",
                    "keios.os.cards.export"
                ),
                stepsEn = listOf(
                    "Read snapshot to capture visible and expanded counts.",
                    "Export target=all.",
                    "Store the returned JSON in the client workflow output."
                ),
                stepsZh = listOf(
                    "读取快照，记录可见与展开数量。",
                    "使用 target=all 导出。",
                    "把返回 JSON 保存在客户端工作流输出里。"
                ),
                stepsJa = listOf(
                    "Read snapshot to capture visible and expanded counts.",
                    "Export target=all.",
                    "Store the returned JSON in the client workflow output."
                ),
                outputEn = listOf("backup JSON", "card counts", "restore note"),
                outputZh = listOf("备份 JSON", "卡片数量", "恢复说明"),
                outputJa = listOf("backup JSON", "card counts", "restore note")
            )
        )

        fun isSimplifiedChinese(locale: Locale): Boolean {
            return locale.language.equals("zh", ignoreCase = true)
        }

        fun isJapanese(locale: Locale): Boolean {
            return locale.language.equals("ja", ignoreCase = true)
        }

        fun localText(locale: Locale, zh: String, ja: String, en: String): String {
            return when {
                isSimplifiedChinese(locale) -> zh
                isJapanese(locale) -> ja
                else -> en
            }
        }
    }
}
