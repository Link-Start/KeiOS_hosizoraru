# KeiOS MCP Skill

## Identity
- App: {{APP_LABEL}} ({{APP_PACKAGE}})
- Version: {{APP_VERSION}}
- MCP server: {{SERVER_NAME}}
- Local endpoint: {{LOCAL_ENDPOINT}}
- LAN endpoints: {{LAN_ENDPOINTS}}

## Start

1. Call `keios.health.ping`.
2. Call `keios.mcp.runtime.status`.
3. For GitHub tasks, call `keios.github.config.snapshot`.
4. Read `{{RESOURCE_OVERVIEW_URI}}` for grouped tools.
5. Read `{{RESOURCE_SKILL_URI}}` or `keios://skill/tool/{tool}` for task help.

## Client Config

- Default config resource: `{{RESOURCE_CONFIG_URI}}`
- Mode config template: `{{RESOURCE_CONFIG_TEMPLATE_URI}}`
- Bootstrap prompt: `{{PROMPT_BOOTSTRAP}}`
- Claw onboarding: `keios.mcp.claw.skill.guide(mode=auto)`
- Use `mode=local` for same-device clients and `mode=lan` for cross-device debugging.

## Tool Catalog
{{TOOL_LIST}}

## Runtime And Home

- `keios.health.ping`, `keios.app.info`, `keios.app.version`, `keios.shizuku.status`
- `keios.mcp.runtime.status`, `keios.mcp.runtime.logs`, `keios.mcp.runtime.config`
- `keios.home.overview.snapshot`

## OS And System

- `keios.system.topinfo.query`
- `keios.os.cards.snapshot`
- `keios.os.activity.cards`
- `keios.os.shell.cards`
- `keios.os.cards.export`
- `keios.os.cards.import`

## GitHub

- Config: `keios.github.config.snapshot`
- Tracking: `keios.github.tracks.snapshot`, `keios.github.tracks.list`, `keios.github.tracks.check`,
  `keios.github.tracks.summary`, `keios.github.tracks.export`, `keios.github.tracks.import`
- Actions: `keios.github.actions.recommended`
- Links: `keios.github.link.parse`, `keios.github.link.resolve`, `keios.github.link.pending`
- Discovery: `keios.github.discovery.search`, `keios.github.repo.package.scan`,
  `keios.github.direct_apk.inspect`, `keios.github.package.repo.scan`
- Star import: `keios.github.stars.lists`, `keios.github.stars.preview`,
  `keios.github.stars.import`, `keios.github.stars.apk.verify`
- Cache: `keios.github.cache.clear`

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

## Recommended Flows

1. Runtime diagnostics: `keios.health.ping` -> `keios.mcp.runtime.status` ->
   `keios.mcp.runtime.logs`
2. GitHub update audit: `keios.github.config.snapshot` ->
   `keios.github.tracks.summary(mode=cache)` -> `keios.github.tracks.check(onlyUpdates=true)`
3. GitHub source split: use `sourceMode=github_repository` or `sourceMode=direct_apk`
   on track list, export, check, and summary tools.
4. Repo to package: `keios.github.repo.package.scan(repoUrl=..., expectedPackageName=...)`
5. Direct APK check: `keios.github.direct_apk.inspect(url=..., expectedPackageName=...)`
6. Actions audit: `keios.github.actions.recommended(refresh=false)`, then
   `keios.github.actions.recommended(refresh=true, onlyEnabled=true)` for network refresh.
7. Package to repo: `keios.github.package.repo.scan(packageName=..., appLabel=...)`
8. Star import: `keios.github.stars.lists` -> `keios.github.stars.preview` ->
   `keios.github.stars.apk.verify` -> `keios.github.stars.import(apply=true)`
9. Shared link intake: `keios.github.link.parse` -> `keios.github.link.resolve`
10. BA cache audit: `keios.ba.snapshot` -> `keios.ba.calendar.cache` or `keios.ba.pool.cache` ->
   `keios.ba.guide.cache.inspect`

## Output Contract

- Tools return compact `key=value` lines and fixed list rows.
- Import tools preview by default; writes require `apply=true`.
- Start `limit` at 20 to 80 for audits.
- `repoFilter` accepts owner/repo, package name, or app label.
- `sourceMode` accepts `github_repository`, `direct_apk`, or blank for all tracked sources.
- GitHub tracking `sortMode` accepts `update_first`, `name_asc`, `prerelease_first`,
  `changed_newest`, `changed_oldest`, `added_newest`, or `added_oldest`.
- Repository APK scans accept `expectedPackageName` for multi-package releases.
- Actions refresh writes the recommended-run cache and uses bounded parallel network checks.
- `serverIndex` accepts 0 to 2 and defaults to the current BA server.
