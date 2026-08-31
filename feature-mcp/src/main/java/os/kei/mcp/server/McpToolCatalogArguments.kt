package os.kei.mcp.server

internal object McpToolCatalogArguments {
    fun argumentsFor(name: String): List<McpToolArgumentSpec> {
        return toolArguments[name].orEmpty()
    }

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
                    "os-card-backup",
                    "webdav-sync-diagnostics"
                )
            )
        ),
        "keios.webdav.history" to listOf(
            McpSchema.string(
                name = "mode",
                description = "History output mode.",
                enumValues = listOf("summary", "list", "detail"),
                defaultValue = "summary"
            ),
            McpSchema.integer("limit"),
            McpSchema.boolean(
                name = "issuesOnly",
                description = "Only include entries that need review or failed.",
                defaultValue = "false"
            ),
            McpSchema.string("id", description = "History entry id for detail mode.")
        ),
        "keios.system.topinfo.query" to listOf(McpSchema.string("query"), McpSchema.integer("limit")),
        "keios.os.cards.list" to listOf(
            McpSchema.string(
                name = "target",
                description = "OS card domain to list; defaults to all.",
                enumValues = listOf("activity", "shell", "all")
            ),
            McpSchema.string("query"),
            McpSchema.boolean("onlyVisible"),
            McpSchema.boolean("includeOutput", description = "Shell cards only: include last command output."),
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
        "keios.github.refresh.history" to githubRefreshHistoryArguments(includeMode = true),
        "keios.github.refresh.history.export" to githubRefreshHistoryArguments(includeMode = false),
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
        "keios.github.package.repo.search" to listOf(
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
        "keios.ba.snapshot" to listOf(
            McpSchema.string(
                name = "accountId",
                description = "Blue Archive account id. Empty targets the active account."
            )
        ),
        "keios.ba.daily.done" to listOf(
            McpSchema.string(
                name = "accountId",
                description = "Blue Archive account id. Empty targets every enabled account."
            ),
            McpSchema.boolean(
                name = "apply",
                description = "Leave false to preview. True writes game state and cannot be undone."
            )
        ),
        "keios.ba.calendar.cache" to cacheEntryArguments(),
        "keios.ba.pool.cache" to cacheEntryArguments(),
        "keios.ba.guide.catalog.cache" to listOf(
            McpSchema.string("tab"),
            McpSchema.boolean("includeEntries"),
            McpSchema.integer("limit")
        ),
        "keios.ba.guide.cache.inspect" to listOf(
            McpSchema.string(
                name = "url",
                description = "Student Guide detail URL; omit for the whole-cache footprint."
            ),
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

    private fun githubRefreshHistoryArguments(includeMode: Boolean): List<McpToolArgumentSpec> {
        val base =
            listOf(
                McpSchema.string(
                    name = "outcome",
                    description = "Refresh outcome filter.",
                    enumValues = listOf("all", "completed", "updatable", "failed", "cancelled", "partial_failed"),
                    defaultValue = "all"
                ),
                McpSchema.string(
                    name = "source",
                    description = "Refresh source filter.",
                    enumValues = listOf("all", "page", "background", "shortcut", "debug"),
                    defaultValue = "all"
                ),
                McpSchema.string(
                    name = "scope",
                    description = "Refresh scope filter.",
                    enumValues = listOf(
                        "all",
                        "all_tracked",
                        "due_tracked",
                        "visible_tracked",
                        "requested_tracked",
                        "missing_cache",
                        "single_tracked",
                        "shortcut_all_tracked"
                    ),
                    defaultValue = "all"
                ),
                McpSchema.string(
                    name = "sinceMillis",
                    description = "Only include records finished at or after this epoch millisecond."
                ),
                McpSchema.integer("limit")
            )
        return if (includeMode) {
            listOf(
                McpSchema.string(
                    name = "mode",
                    description = "History output mode.",
                    enumValues = listOf("summary", "list", "detail"),
                    defaultValue = "summary"
                ),
                McpSchema.boolean(
                    name = "includeFailures",
                    description = "Include compact per-track failure rows.",
                    defaultValue = "false"
                )
            ) + base
        } else {
            base
        }
    }

    private fun githubSourceModeArgument(): McpToolArgumentSpec {
        return McpSchema.string(
            name = "sourceMode",
            description = "Tracked source filter.",
            enumValues = listOf("github_repository", "git_repository", "direct_apk", "fdroid_repository")
        )
    }

    private fun githubFilterModeArgument(): McpToolArgumentSpec {
        return McpSchema.string(
            name = "filterMode",
            description = "Temporary tracking filter.",
            enumValues = listOf(
                "all",
                "github_repository",
                "git_repository",
                "direct_apk",
                "fdroid_repository",
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
}
