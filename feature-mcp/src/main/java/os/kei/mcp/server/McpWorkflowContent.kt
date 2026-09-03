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
    private val workflowSkillCache = McpBoundedTextCache(maxEntries = 8)
    private val workflowListCache = McpBoundedTextCache(maxEntries = 8)
    private val workflowDetailCache = McpBoundedTextCache(maxEntries = 32)
    private val workflowMarkdownCache = McpBoundedTextCache(maxEntries = 32)

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
                "KeiOS MCP ワークフローブループリント",
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
                "KeiOS MCP ワークフロー詳細",
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
                "Claw 向けの KeiOS MCP 定期ワークフローまたは複合スキル計画を生成します。",
                "Generate a KeiOS MCP scheduled workflow or composed skill plan for Claw."
            ),
            arguments = listOf(
                PromptArgument(
                    name = "goal",
                    description = localText(locale, "目标", "目標", "Goal"),
                    required = true,
                    title = localText(locale, "目标", "目標", "Goal")
                ),
                PromptArgument(
                    name = "cadence",
                    description = localText(
                        locale,
                        "执行频率，例如每天 09:00 或每 3 小时。",
                        "実行頻度。例：毎日 09:00、または 3 時間ごと。",
                        "Cadence, such as daily at 09:00 or every 3 hours."
                    ),
                    required = false,
                    title = localText(locale, "频率", "実行頻度", "Cadence")
                ),
                PromptArgument(
                    name = "workflow",
                    description = localText(locale, "蓝图 id", "ブループリント ID", "Blueprint id"),
                    required = false,
                    title = localText(locale, "蓝图", "ブループリント", "Blueprint")
                ),
                PromptArgument(
                    name = "delivery",
                    description = localText(locale, "输出方式", "配信方法", "Delivery"),
                    required = false,
                    title = localText(locale, "输出", "出力", "Delivery")
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
                    "KeiOS MCP ワークフロー計画",
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
        return workflowSkillCache.getOrPut("workflow-skill|${locale.mcpCacheTag()}") {
            buildWorkflowSkillTextUncached(locale)
        }
    }

    private fun buildWorkflowSkillTextUncached(locale: Locale): String {
        return buildString {
            appendLine(localText(locale, "# KeiOS MCP 工作流", "# KeiOS MCP ワークフロー", "# KeiOS MCP Workflows"))
            appendLine()
            appendLine(
                localText(
                    locale,
                    "这些蓝图用于 Claw 侧创建定时任务或组合技能。KeiOS MCP 提供工具、资源与 Prompt，任务调度由客户端保存和触发。",
                    "これらのブループリントは、Claw で定期タスクや複合スキルを作成するために使います。KeiOS MCP はツール、リソース、プロンプトを提供し、クライアントがスケジュールを保存して実行します。",
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
                appendLine(localText(locale, "步骤：", "手順：", "Steps:"))
                blueprint.steps(locale).forEachIndexed { index, step ->
                    appendLine("${index + 1}. $step")
                }
                appendLine()
                appendLine(localText(locale, "输出：", "出力：", "Output:"))
                blueprint.output(locale).forEach { item ->
                    appendLine("- $item")
                }
                appendLine()
            }
        }.trim()
    }

    private fun buildBlueprintListText(locale: Locale): String {
        return workflowListCache.getOrPut("workflow-list|${locale.mcpCacheTag()}") {
            buildBlueprintListTextUncached(locale)
        }
    }

    private fun buildBlueprintListTextUncached(locale: Locale): String {
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
        val normalized = workflow.trim().lowercase(Locale.ROOT)
        return workflowDetailCache.getOrPut("workflow-detail|${locale.mcpCacheTag()}|$normalized") {
            buildBlueprintDetailTextUncached(workflow, locale)
        }
    }

    private fun buildBlueprintDetailTextUncached(workflow: String, locale: Locale): String {
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
        val normalized = workflow.trim().lowercase(Locale.ROOT)
        return workflowMarkdownCache.getOrPut("workflow-markdown|${locale.mcpCacheTag()}|$normalized") {
            buildBlueprintMarkdownUncached(workflow, locale)
        }
    }

    private fun buildBlueprintMarkdownUncached(workflow: String, locale: Locale): String {
        val blueprint = findBlueprint(workflow) ?: return buildString {
            appendLine(localText(locale, "# 未知工作流", "# 不明なワークフロー", "# Unknown Workflow"))
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
            appendLine(localText(locale, "## 步骤", "## 手順", "## Steps"))
            blueprint.steps(locale).forEachIndexed { index, step ->
                appendLine("${index + 1}. $step")
            }
            appendLine()
            appendLine(localText(locale, "## 输出", "## 出力", "## Output"))
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
                    "KeiOS MCP 向けの Claw 定期タスクまたは複合スキルを作成します。",
                    "Create a Claw scheduled task or composed skill for KeiOS MCP."
                )
            )
            appendLine("goal=${goal.ifBlank { "unspecified" }}")
            appendLine("cadence=${cadence.ifBlank { "client_decides" }}")
            appendLine("workflow=${workflow.ifBlank { "auto" }}")
            appendLine("delivery=${delivery.ifBlank { "markdown_summary" }}")
            appendLine()
            appendLine(
                localText(
                    locale,
                    "先读取 $WORKFLOW_RESOURCE_URI。",
                    "最初に $WORKFLOW_RESOURCE_URI を読み取ってください。",
                    "Read $WORKFLOW_RESOURCE_URI first."
                )
            )
            if (workflow.isNotBlank()) {
                val detailUri = WORKFLOW_TEMPLATE_URI.replace("{workflow}", workflow)
                appendLine(
                    localText(
                        locale,
                        "然后读取 $detailUri。",
                        "次に $detailUri を読み取ってください。",
                        "Then read $detailUri."
                    )
                )
            }
            appendLine(
                localText(
                    locale,
                    "按列出的顺序调用工具；写入操作必须显式设置 apply=true；网络刷新需用 limit/filter 参数限制范围。",
                    "記載順にツールを呼び出し、書き込みには明示的な apply=true を必須とし、ネットワーク更新は limit/filter 引数で範囲を制限してください。",
                    "Call tools in the listed order, keep writes behind explicit apply=true, and keep network refreshes bounded by limit/filter arguments."
                )
            )
            appendLine(
                localText(
                    locale,
                    "定时任务的计划由客户端保存，仅在执行时调用 KeiOS MCP。",
                    "定期タスクのスケジュールはクライアントに保存し、実行時にのみ KeiOS MCP を呼び出してください。",
                    "For scheduled tasks, store the schedule in the client and call KeiOS MCP only at execution time."
                )
            )
            appendLine(
                localText(
                    locale,
                    "返回预演计划、所需工具、执行频率、失败处理方式和最终用户可见输出。",
                    "ドライラン計画、必要なツール、実行頻度、失敗時の処理、最終的なユーザー向け出力を返してください。",
                    "Return a dry-run plan, required tools, cadence, failure handling, and final user-facing output."
                )
            )
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
                titleJa = "GitHub 更新ウォッチ",
                cadenceHintEn = "Every 3 to 6 hours, or follow the app refresh interval.",
                cadenceHintZh = "每 3 到 6 小时，或跟随 App 刷新间隔。",
                cadenceHintJa = "3〜6 時間ごと、またはアプリの更新間隔に合わせます。",
                summaryEn = "Audit tracked GitHub and subscription projects, then report update, failure, and pre-release states.",
                summaryZh = "巡检 GitHub 与订阅项目追踪项，输出更新、失败与预发行状态。",
                summaryJa = "追跡中の GitHub と購読プロジェクトを確認し、更新、失敗、プレリリースの状態を報告します。",
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
                    "設定とキャッシュ概要を読み取ります。",
                    "新しいデータが必要な場合は、filterMode=update_available でキャッシュ概要を取得し、onlyUpdates=true でネットワーク確認を実行します。",
                    "変更されたアプリ、確認失敗、direct_apk のリモート状態に関する注意を報告します。"
                ),
                outputEn = listOf("updated apps", "failed checks", "next action suggestion"),
                outputZh = listOf("可更新 App", "检查失败项", "下一步处理建议"),
                outputJa = listOf("更新可能なアプリ", "確認失敗", "次の対応案")
            ),
            WorkflowBlueprint(
                id = "github-actions-watch",
                titleEn = "GitHub Actions watch",
                titleZh = "GitHub Actions 巡检",
                titleJa = "GitHub Actions 更新ウォッチ",
                cadenceHintEn = "15 minutes to 3 hours, matching each track actionsUpdateIntervalMode.",
                cadenceHintZh = "15 分钟到 3 小时，优先匹配每个追踪项的 actionsUpdateIntervalMode。",
                cadenceHintJa = "15 分〜3 時間ごと。各追跡項目の actionsUpdateIntervalMode に合わせます。",
                summaryEn = "Refresh recommended Actions runs for enabled tracks and report newer Android artifacts.",
                summaryZh = "刷新已开启 Actions 检查的追踪项，报告新的 Android artifact。",
                summaryJa = "Actions の確認が有効な追跡項目を更新し、新しい Android artifact を報告します。",
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
                    "filterMode=actions_check_enabled で追跡項目を一覧表示します。",
                    "予定した実行間隔の範囲内でのみ、refresh=true を指定して推奨 run を取得します。",
                    "actionsIntervalMode と actionsIntervalMinutes から、クライアントの次回実行時刻を決めます。"
                ),
                outputEn = listOf("newer runs", "artifact counts", "next schedule hint"),
                outputZh = listOf("新的 run", "artifact 数量", "下次执行建议"),
                outputJa = listOf("新しい run", "artifact 数", "次回実行の提案")
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
                titleJa = "OS カードのバックアップ",
                cadenceHintEn = "Weekly or before major app changes.",
                cadenceHintZh = "每周一次，或在大改 App 前执行。",
                cadenceHintJa = "毎週、またはアプリの大きな変更前に実行します。",
                summaryEn = "Export Activity and shell cards, then keep the JSON as a client-side backup artifact.",
                summaryZh = "导出 Activity 与 Shell 卡片 JSON，并由客户端保存为备份产物。",
                summaryJa = "Activity と Shell カードをエクスポートし、JSON をクライアント側のバックアップとして保存します。",
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
                    "スナップショットを読み取り、表示中と展開後の件数を記録します。",
                    "target=all でエクスポートします。",
                    "返された JSON をクライアントのワークフロー出力に保存します。"
                ),
                outputEn = listOf("backup JSON", "card counts", "restore note"),
                outputZh = listOf("备份 JSON", "卡片数量", "恢复说明"),
                outputJa = listOf("バックアップ JSON", "カード数", "復元メモ")
            ),
            WorkflowBlueprint(
                id = "webdav-sync-diagnostics",
                titleEn = "WebDAV sync diagnostics",
                titleZh = "WebDAV 同步诊断",
                titleJa = "WebDAV 同期診断",
                cadenceHintEn = "Daily, or after a failed or review-required sync.",
                cadenceHintZh = "每天一次，或在同步失败、需要处理时执行。",
                cadenceHintJa = "毎日、または同期の失敗や確認待ちが発生した後に実行します。",
                summaryEn = "Inspect credential-safe WebDAV state and recent sync history through a read-only diagnostic flow.",
                summaryZh = "通过只读诊断流程检查已脱敏的 WebDAV 状态与近期同步历史。",
                summaryJa = "読み取り専用の診断フローで、認証情報を除いた WebDAV 状態と最近の同期履歴を確認します。",
                tools = listOf(
                    "keios.webdav.status",
                    "keios.webdav.history"
                ),
                stepsEn = listOf(
                    "Read status and check configuration, pending review items, and the latest auto-sync summary.",
                    "Read history with mode=summary and issuesOnly=true.",
                    "Read mode=detail for the newest issue id, then report runtime restrictions and affected items."
                ),
                stepsZh = listOf(
                    "读取状态，检查配置、待处理同步项与最近一次自动同步摘要。",
                    "用 mode=summary、issuesOnly=true 读取历史概览。",
                    "对最新异常 id 使用 mode=detail，输出运行限制与受影响同步项。"
                ),
                stepsJa = listOf(
                    "状態を読み取り、設定、確認待ち項目、最新の自動同期概要を確認します。",
                    "mode=summary と issuesOnly=true を指定して履歴を読み取ります。",
                    "最新の問題 ID を mode=detail で読み取り、実行時の制約と影響を受ける項目を報告します。"
                ),
                outputEn = listOf("sync health", "affected items", "credential-safe next action"),
                outputZh = listOf("同步健康度", "受影响同步项", "不涉及凭据的下一步建议"),
                outputJa = listOf("同期状態", "影響を受ける項目", "認証情報を扱わない次の対応")
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
