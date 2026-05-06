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
3. GitHub 相关任务先调用 `keios.github.config.snapshot`。
4. 读取 `{{RESOURCE_OVERVIEW_URI}}` 获取工具分组。
5. 读取 `{{RESOURCE_SKILL_URI}}` 或 `keios://skill/tool/{tool}` 获取任务帮助。

## 客户端配置

- 默认配置资源：`{{RESOURCE_CONFIG_URI}}`
- mode 配置模板：`{{RESOURCE_CONFIG_TEMPLATE_URI}}`
- 初始化 Prompt：`{{PROMPT_BOOTSTRAP}}`
- Claw 接入：`keios.mcp.claw.skill.guide(mode=auto)`
- 同设备客户端用 `mode=local`，跨设备联调用 `mode=lan`。

## 工具目录
{{TOOL_LIST}}

## 运行与 Home

- `keios.health.ping`、`keios.app.info`、`keios.app.version`、`keios.shizuku.status`
- `keios.mcp.runtime.status`、`keios.mcp.runtime.logs`、`keios.mcp.runtime.config`
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
- 链接：`keios.github.link.parse`、`keios.github.link.resolve`、`keios.github.link.pending`
- 发现：`keios.github.discovery.search`、`keios.github.repo.package.scan`、
  `keios.github.package.repo.scan`
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
3. 仓库扫包名：`keios.github.repo.package.scan(repoUrl=...)`
4. 包名反扫仓库：`keios.github.package.repo.scan(packageName=..., appLabel=...)`
5. Star 导入：`keios.github.stars.lists` -> `keios.github.stars.preview` ->
   `keios.github.stars.apk.verify` -> `keios.github.stars.import(apply=true)`
6. 分享链接接入：`keios.github.link.parse` -> `keios.github.link.resolve`
7. BA 缓存巡检：`keios.ba.snapshot` -> `keios.ba.calendar.cache` 或 `keios.ba.pool.cache` ->
   `keios.ba.guide.cache.inspect`

## 输出约定

- 工具输出使用紧凑 `key=value` 与固定列表行。
- 导入类工具默认预览；写入必须显式传 `apply=true`。
- 审计任务的 `limit` 建议从 20 到 80 开始。
- `repoFilter` 支持 owner/repo、包名或应用名。
- `serverIndex` 范围 0 到 2，留空使用当前 BA 服务器。
