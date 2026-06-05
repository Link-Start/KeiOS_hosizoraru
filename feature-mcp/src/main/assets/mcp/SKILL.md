# KeiOS MCP Skill

## Identity
- App: {{APP_LABEL}} ({{APP_PACKAGE}})
- Version: {{APP_VERSION}}
- MCP server: {{SERVER_NAME}}
- Local endpoint: {{LOCAL_ENDPOINT}}
- LAN endpoints: {{LAN_ENDPOINTS}}

## Quick Start

1. Add the current MCP config to the Claw App MCP server list.
2. Ask Claw to read `{{RESOURCE_SKILL_URI}}`, or call `keios.mcp.claw.skill.guide(mode=auto)` for the complete Skill content.
3. In Agent mode, ask the Claw main Agent to read `{{RESOURCE_SUBAGENT_URI}}` and create a KeiOS MCP sub agent.
4. Call `keios.health.ping` to verify connectivity.
5. Call `keios.mcp.runtime.status` for server, token, and endpoint state.
6. For scheduled tasks or composed skills, read `{{RESOURCE_WORKFLOWS_URI}}` and choose a blueprint.
7. After each KeiOS upgrade, delete the old KeiOS MCP server in Claw and add it again so the client refreshes cached tools.

## Client Config

- Default config resource: `{{RESOURCE_CONFIG_URI}}`
- Mode config template: `{{RESOURCE_CONFIG_TEMPLATE_URI}}`
- Sub-agent guide: `{{RESOURCE_SUBAGENT_URI}}`
- Bootstrap prompt: `{{PROMPT_BOOTSTRAP}}`
- Workflow prompt: `{{PROMPT_WORKFLOW_PLAN}}`
- Diagnostics prompt: `{{PROMPT_DIAGNOSTICS_PLAN}}`
- Claw onboarding tool: `keios.mcp.claw.skill.guide(mode=auto)`
- Codex onboarding tool: `keios.dev.codex.config(mode=local)`

## Recommended Entry Points

{{ENTRYPOINT_TOOLS}}

Entry points explain the current state. Read `{{RESOURCE_DOMAIN_TEMPLATE_URI}}` or
`{{RESOURCE_TOOL_TEMPLATE_URI}}` before lower-level tools.

## Workflows

- Workflow overview: `{{RESOURCE_WORKFLOWS_URI}}`
- Single workflow: `{{RESOURCE_WORKFLOW_TEMPLATE_URI}}`
- Tool entry: `keios.mcp.workflow.blueprints(mode=list|detail|skill, workflow=...)`
- Planning prompt: `{{PROMPT_WORKFLOW_PLAN}}`

{{WORKFLOW_TOOLS}}

The client stores schedules. KeiOS MCP supplies query, check, export, and preview capabilities when
a task fires.

## Domain Resources

- Runtime: `keios://skill/domain/runtime`
- GitHub: `keios://skill/domain/github`
- OS: `keios://skill/domain/os`
- BA: `keios://skill/domain/ba`
- Dev/Codex: `keios://skill/domain/dev`
- Single tool help: `{{RESOURCE_TOOL_TEMPLATE_URI}}`

## Common Flows

1. Runtime diagnostics: `keios.health.ping` -> `keios.mcp.runtime.status` ->
   `keios.mcp.runtime.logs(limit=80)`
2. GitHub update audit: `keios.github.config.snapshot` ->
   `keios.github.tracks.summary(mode=cache)` -> `keios.github.tracks.check(onlyUpdates=true)`
3. Actions audit: `keios.github.tracks.list(filterMode=actions_check_enabled)` ->
   `keios.github.actions.recommended(refresh=true, onlyEnabled=true)`
4. StarList import: `keios.github.stars.lists` -> `keios.github.stars.preview` ->
   `keios.github.stars.apk.verify` -> `keios.github.stars.import(apply=true)`
5. OS card backup: `keios.os.cards.snapshot` -> `keios.os.cards.export(target=all)`
6. BA daily brief: `keios.ba.snapshot` -> `keios.ba.calendar.cache` -> `keios.ba.pool.cache`
7. Codex development: `keios.dev.codex.config(mode=local)` ->
   `keios.dev.project.snapshot` -> `keios.dev.validation.plan(scope=quick)`

## Full Tool Index

{{TOOL_LIST}}

## Output And Safety Contract

- Tools keep compact `key=value` lines and fixed list rows.
- `structuredContent.text` mirrors text output on entrypoint tools for clients that read structured
  fields.
- Import tools preview by default; writes require explicit `apply=true`.
- Network and deep-scan tools have timeouts and limit arguments; scheduled audits should start with
  20 to 80 rows.
- Dev/Codex tools are read-only and return onboarding or validation text for local development.
- `repoFilter` accepts owner/repo, package name, or app label.
- `sourceMode` accepts `github_repository`, `git_repository`, `direct_apk`, or blank for all tracked sources.
- `filterMode` accepts `all`, `github_repository`, `git_repository`, `direct_apk`, `pre_release_tracked`,
  `update_available`, `installed`, `failed_checks`, or `actions_check_enabled`.
- GitHub tracking `sortMode` accepts `update`, `name`, `pre_release`, `changed`, or `added`;
  `sortDirection` accepts `forward` or `reverse`.
- GitHub tracked export uses `keios.github.tracked/v3`.
- `actionsUpdateIntervalMode` accepts `follow_global`, `15m`, `30m`, `1h`, `2h`, or `3h`.
