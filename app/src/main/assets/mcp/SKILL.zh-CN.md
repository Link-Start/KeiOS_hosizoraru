# KeiOS MCP Skill

## 身份
- App：{{APP_LABEL}}（{{APP_PACKAGE}}）
- 版本：{{APP_VERSION}}
- MCP 服务：{{SERVER_NAME}}
- 本机 endpoint：{{LOCAL_ENDPOINT}}
- 局域网 endpoint：{{LAN_ENDPOINTS}}

## 起步

1. 调用 `keios.health.ping`。
2. 调用 `keios.mcp.runtime.status`。
3. 定时任务或组合技能先读取 `{{RESOURCE_WORKFLOWS_URI}}`。
4. 需要从工具选择工作流时调用 `keios.mcp.workflow.blueprints(mode=list)`。
5. 需要底层细节时再读取 `{{RESOURCE_OVERVIEW_URI}}` 或 `keios://skill/tool/{tool}`。

## 客户端配置

- 默认配置资源：`{{RESOURCE_CONFIG_URI}}`
- mode 配置模板：`{{RESOURCE_CONFIG_TEMPLATE_URI}}`
- 初始化 Prompt：`{{PROMPT_BOOTSTRAP}}`
- 工作流 Prompt：`{{PROMPT_WORKFLOW_PLAN}}`
- 工作流蓝图：`{{RESOURCE_WORKFLOWS_URI}}`
- Claw 接入：`keios.mcp.claw.skill.guide(mode=auto)`
- 同设备客户端用 `mode=local`，跨设备联调用 `mode=lan`。

## 入口工具

- `keios.health.ping`：连通性探测。
- `keios.mcp.runtime.status`：服务、Token、endpoint 与客户端状态。
- `keios.mcp.workflow.blueprints`：定时任务与组合技能的工作流选择器。
- `keios.github.config.snapshot`：GitHub 设置与追踪上下文。
- `keios.ba.snapshot`：Blue Archive AP、咖啡厅、通知与服务器上下文。

优先使用这些入口工具。调用更底层工具前先读取 `keios://skill/tool/{tool}`。

## 运行与 Home

- `keios.health.ping`、`keios.app.info`、`keios.app.version`、`keios.shizuku.status`
- `keios.mcp.runtime.status`、`keios.mcp.runtime.logs`、`keios.mcp.runtime.config`
- `keios.mcp.workflow.blueprints`
- `keios.home.overview.snapshot`

## OS 与系统

- `keios.system.topinfo.query`
- `keios.os.cards.snapshot`
- `keios.os.activity.cards`
- `keios.os.shell.cards`
- `keios.os.cards.export`
- `keios.os.cards.import`

## GitHub

- 配置：`keios.github.config.snapshot`
- 跟踪：`keios.github.tracks.snapshot`、`keios.github.tracks.list`、`keios.github.tracks.check`、
  `keios.github.tracks.summary`、`keios.github.tracks.export`、`keios.github.tracks.import`
- Actions：`keios.github.actions.recommended`
- 链接：`keios.github.link.parse`、`keios.github.link.resolve`、`keios.github.link.pending`
- 发现：`keios.github.discovery.search`、`keios.github.repo.package.scan`、
  `keios.github.direct_apk.inspect`、`keios.github.package.repo.scan`
- Star 导入：`keios.github.stars.lists`、`keios.github.stars.preview`、`keios.github.stars.import`、
  `keios.github.stars.apk.verify`
- 缓存：`keios.github.cache.clear`

## Blue Archive

- `keios.ba.snapshot`
- `keios.ba.calendar.cache`
- `keios.ba.pool.cache`
- `keios.ba.guide.catalog.cache`
- `keios.ba.guide.cache.overview`
- `keios.ba.guide.cache.inspect`
- `keios.ba.guide.media.list`
- `keios.ba.guide.bgm.favorites`
- `keios.ba.cache.clear`

## 推荐任务流

1. 运行排障：`keios.health.ping` -> `keios.mcp.runtime.status` -> `keios.mcp.runtime.logs`
2. GitHub 更新巡检：`keios.github.config.snapshot` -> `keios.github.tracks.summary(mode=cache)` ->
   `keios.github.tracks.check(onlyUpdates=true)`
3. GitHub 来源拆分：在列表、导出、检查和汇总工具里使用 `sourceMode=github_repository`
   或 `sourceMode=direct_apk`。`direct_apk` 是订阅项目的参数与存储 id。
4. 仓库扫包名：`keios.github.repo.package.scan(repoUrl=..., expectedPackageName=...)`
5. 直链 APK 检查：`keios.github.direct_apk.inspect(url=..., expectedPackageName=...)`
6. Actions 巡检：先用 `keios.github.actions.recommended(refresh=false)` 读缓存；
   需要联网刷新时用 `keios.github.actions.recommended(refresh=true, onlyEnabled=true)`。
7. 包名反扫仓库：`keios.github.package.repo.scan(packageName=..., appLabel=...)`
8. Star 导入：`keios.github.stars.lists` -> `keios.github.stars.preview` ->
   `keios.github.stars.apk.verify` -> `keios.github.stars.import(apply=true)`
9. 分享链接接入：`keios.github.link.parse` -> `keios.github.link.resolve`
10. BA 缓存巡检：`keios.ba.snapshot` -> `keios.ba.calendar.cache` 或 `keios.ba.pool.cache` ->
   `keios.ba.guide.cache.inspect`

## Claw 工作流

- 读取 `{{RESOURCE_WORKFLOWS_URI}}` 获取定时任务与组合技能蓝图。
- 读取 `{{RESOURCE_WORKFLOW_TEMPLATE_URI}}` 获取单个蓝图详情。
- 创建 Claw 任务或复用技能时，使用 `{{PROMPT_WORKFLOW_PLAN}}`，参数为 `goal`、`cadence`、
  `workflow`、`delivery`。
- 客户端更适合调用工具时，使用 `keios.mcp.workflow.blueprints(mode=list|detail|skill, workflow=...)`。
- 定时规则由客户端保存。KeiOS MCP tools 在任务触发时执行。

## 完整工具参考

{{TOOL_LIST}}

## 输出约定

- 工具输出使用紧凑 `key=value` 与固定列表行。
- 导入类工具默认预览；写入必须显式传 `apply=true`。
- 工作流工具是只读计划助手；写入类工具依旧需要显式 `apply=true`。
- 审计任务的 `limit` 建议从 20 到 80 开始。
- `repoFilter` 支持 owner/repo、包名或应用名。
- `sourceMode` 支持 `github_repository`、`direct_apk`，留空表示全部追踪来源。
- `filterMode` 支持 `all`、`github_repository`、`direct_apk`、`pre_release_tracked`、
  `update_available`、`installed`、`failed_checks`、`actions_check_enabled`。
- GitHub 追踪工具的 `sortMode` 支持 `update`、`name`、`pre_release`、`changed`、`added`；
  `sortDirection` 支持 `forward`、`reverse`。
- GitHub 追踪导出使用 `format=keios.github.tracked/v3`。
- `actionsUpdateIntervalMode` 支持 `follow_global`、`15m`、`30m`、`1h`、`2h`、`3h`。
- 仓库 APK 扫描支持 `expectedPackageName`，用于同 release 多包名 APK。
- Actions 刷新会写入推荐 run 缓存，使用有界并发联网检查，并在每行输出
  `actionsIntervalMode` 与 `actionsIntervalMinutes`。
- `serverIndex` 范围 0 到 2，留空使用当前 BA 服务器。
