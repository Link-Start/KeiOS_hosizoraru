# KeiOS MCP Skill

## 身份
- App：{{APP_LABEL}}（{{APP_PACKAGE}}）
- 版本：{{APP_VERSION}}
- MCP 服务：{{SERVER_NAME}}
- 本机 endpoint：{{LOCAL_ENDPOINT}}
- 局域网 endpoint：{{LAN_ENDPOINTS}}

## 快速开始

1. 先把当前 MCP Config 添加到 Claw App 的 MCP 服务器列表。
2. 让 Claw 读取 `{{RESOURCE_SKILL_URI}}`，或调用 `keios.mcp.claw.skill.guide(mode=auto)` 获取完整 Skill 内容。
3. Agent 模式下让 Claw 主 Agent 读取 `{{RESOURCE_SUBAGENT_URI}}`，并创建 KeiOS MCP 子 Agent。
4. 调用 `keios.health.ping` 验证连通性。
5. 调用 `keios.mcp.runtime.status` 获取服务、Token 与 endpoint 状态。
6. 需要定时任务或组合技能时，读取 `{{RESOURCE_WORKFLOWS_URI}}` 选择蓝图。
7. 每次升级 KeiOS 后，在 Claw 删除旧的 KeiOS MCP 服务器并重新添加，让客户端刷新工具缓存。

## 客户端配置

- 默认配置资源：`{{RESOURCE_CONFIG_URI}}`
- mode 配置模板：`{{RESOURCE_CONFIG_TEMPLATE_URI}}`
- 子 Agent 指南：`{{RESOURCE_SUBAGENT_URI}}`
- 初始化 Prompt：`{{PROMPT_BOOTSTRAP}}`
- 工作流 Prompt：`{{PROMPT_WORKFLOW_PLAN}}`
- 排障 Prompt：`{{PROMPT_DIAGNOSTICS_PLAN}}`
- Claw 接入工具：`keios.mcp.claw.skill.guide(mode=auto)`
- Codex 接入工具：`keios.dev.codex.config(mode=local)`

## 推荐入口

{{ENTRYPOINT_TOOLS}}

入口工具用于理解当前状态。底层工具调用前建议读取 `{{RESOURCE_DOMAIN_TEMPLATE_URI}}` 或
`{{RESOURCE_TOOL_TEMPLATE_URI}}`。

## 工作流

- 工作流总览：`{{RESOURCE_WORKFLOWS_URI}}`
- 单个工作流：`{{RESOURCE_WORKFLOW_TEMPLATE_URI}}`
- 工具入口：`keios.mcp.workflow.blueprints(mode=list|detail|skill, workflow=...)`
- 计划 Prompt：`{{PROMPT_WORKFLOW_PLAN}}`

{{WORKFLOW_TOOLS}}

客户端负责保存定时规则。KeiOS MCP 在任务触发时提供查询、检查、导出和预览能力。

## 领域资源

- Runtime：`keios://skill/domain/runtime`
- GitHub：`keios://skill/domain/github`
- OS：`keios://skill/domain/os`
- BA：`keios://skill/domain/ba`
- WebDAV：`keios://skill/domain/webdav`
- Dev/Codex：`keios://skill/domain/dev`
- 单工具帮助：`{{RESOURCE_TOOL_TEMPLATE_URI}}`

## 常用任务流

1. 运行排障：`keios.health.ping` -> `keios.mcp.runtime.status` -> `keios.mcp.runtime.logs(limit=80)`
2. GitHub 更新巡检：`keios.github.config.snapshot` -> `keios.github.tracks.summary(mode=cache)` ->
   `keios.github.tracks.check(onlyUpdates=true)`
3. Actions 巡检：`keios.github.tracks.list(filterMode=actions_check_enabled)` ->
   `keios.github.actions.recommended(refresh=true, onlyEnabled=true)`
4. StarList 导入：`keios.github.stars.lists` -> `keios.github.stars.preview` ->
   `keios.github.stars.apk.verify` -> `keios.github.stars.import(apply=true)`
5. OS 卡片备份：`keios.os.cards.snapshot` -> `keios.os.cards.export(target=all)`
6. BA 每日简报：`keios.ba.accounts` -> `keios.ba.snapshot(accountId=...)` ->
   `keios.ba.calendar.cache` -> `keios.ba.pool.cache`。先调 `keios.ba.accounts`：每个账号各自拥有
   AP、咖啡厅、冷却与制造工坊槽位，不带参数的 `keios.ba.snapshot` 只能描述当前账号。
7. BA 日常：`keios.ba.daily.done`（预览）-> `keios.ba.daily.done(apply=true)`。会写入游戏状态且
   不可撤销，先把预览结果确认给用户。可以安全重试：只重启已冷却完毕的计时，只装填空闲的制造槽。
8. Codex 开发辅助：`keios.dev.codex.config(mode=local)` ->
   `keios.dev.project.snapshot` -> `keios.dev.validation.plan(scope=quick)`
9. WebDAV 同步诊断：`keios.webdav.status` ->
   `keios.webdav.history(mode=summary, issuesOnly=true)` -> `keios.webdav.history(mode=detail, id=...)`

## 完整工具索引

{{TOOL_LIST}}

## 输出与安全约定

- 工具输出保持 `key=value` 与固定列表行。
- `structuredContent.text` 在入口工具中镜像文本输出，兼容需要结构化字段的客户端。
- 导入类工具默认预览，写入必须显式传 `apply=true`。
- 网络和深扫工具有超时与 limit，定时任务建议从 20 到 80 条开始。
- Dev/Codex 工具保持只读，只返回本地开发接入、工程快照与验证计划文本。
- WebDAV 工具保持只读，输出会排除用户名、应用密码与认证头。
- `keios.privilege.status` 的 mode 支持关闭特权、Shizuku 与 Root 三种状态。
- `repoFilter` 支持 owner/repo、包名或应用名。
- `sourceMode` 支持 `github_repository`、`git_repository`、`direct_apk`、`fdroid_repository`，留空表示全部追踪来源。
- `filterMode` 支持 `all`、`github_repository`、`git_repository`、`direct_apk`、`fdroid_repository`、`pre_release_tracked`、
  `update_available`、`installed`、`failed_checks`、`actions_check_enabled`。
- GitHub 追踪工具的 `sortMode` 支持 `update`、`name`、`pre_release`、`changed`、`added`；`sortDirection`
  支持 `forward`、`reverse`。
- GitHub 追踪导出格式为 `keios.github.tracked/v4`。
- `actionsUpdateIntervalMode` 支持 `follow_global`、`15m`、`30m`、`1h`、`2h`、`3h`。
