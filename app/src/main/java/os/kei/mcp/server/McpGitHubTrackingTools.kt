package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.core.background.AppBackgroundScheduler
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.domain.GitHubCacheService
import os.kei.feature.github.domain.GitHubTrackService
import os.kei.feature.github.domain.GitHubTrackedItemsTransferService
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isGitHubRepositoryTrack
import os.kei.ui.page.main.github.GitHubSortDirection
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.GitHubTrackedFilterMode
import java.util.Locale

internal class McpGitHubTrackingTools(
    private val environment: McpToolEnvironment
) {
    private val appContext get() = environment.appContext
    private val githubTrackService = GitHubTrackService()

    fun register(server: Server) {
        server.addMcpTextTool(environment, name = "keios.github.tracks.snapshot") { _ ->
            buildGitHubTrackedSnapshotText()
        }

        server.addMcpTextTool(environment, name = "keios.github.tracks.list") { request ->
            val repoFilter = argString(request.arguments?.get("repoFilter")).trim()
            val sourceMode = argString(request.arguments?.get("sourceMode")).trim()
            val filterMode = parseGitHubTrackedFilterMode(
                argString(request.arguments?.get("filterMode"))
            )
            val sortSelection = parseGitHubSortSelection(
                modeRaw = argString(request.arguments?.get("sortMode")),
                directionRaw = argString(request.arguments?.get("sortDirection"))
            )
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_TRACK_LIMIT).coerceIn(
                1,
                MAX_TRACK_LIMIT
            )
            buildGitHubTrackedListText(
                repoFilter = repoFilter,
                sourceMode = sourceMode,
                filterMode = filterMode,
                sortMode = sortSelection.mode,
                sortDirection = sortSelection.direction,
                limit = limit
            )
        }

        server.addMcpTextTool(environment, name = "keios.github.tracks.export") { request ->
            val repoFilter = argString(request.arguments?.get("repoFilter")).trim()
            val sourceMode = argString(request.arguments?.get("sourceMode")).trim()
            val filterMode = parseGitHubTrackedFilterMode(
                argString(request.arguments?.get("filterMode"))
            )
            val sortSelection = parseGitHubSortSelection(
                modeRaw = argString(request.arguments?.get("sortMode")),
                directionRaw = argString(request.arguments?.get("sortDirection"))
            )
            buildGitHubTrackedExportText(
                repoFilter = repoFilter,
                sourceMode = sourceMode,
                filterMode = filterMode,
                sortMode = sortSelection.mode,
                sortDirection = sortSelection.direction
            )
        }

        server.addMcpTextTool(environment, name = "keios.github.tracks.import") { request ->
            val rawJson = argString(request.arguments?.get("json"))
            val apply = argBoolean(request.arguments?.get("apply"), false)
            buildGitHubTrackedImportText(rawJson = rawJson, apply = apply)
        }

        server.addMcpTextTool(environment, name = "keios.github.tracks.check") { request ->
            val repoFilter = argString(request.arguments?.get("repoFilter")).trim()
            val sourceMode = argString(request.arguments?.get("sourceMode")).trim()
            val filterMode = parseGitHubTrackedFilterMode(
                argString(request.arguments?.get("filterMode"))
            )
            val sortSelection = parseGitHubSortSelection(
                modeRaw = argString(request.arguments?.get("sortMode")),
                directionRaw = argString(request.arguments?.get("sortDirection"))
            )
            val onlyUpdates = argBoolean(request.arguments?.get("onlyUpdates"), false)
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_TRACK_LIMIT).coerceIn(
                1,
                MAX_TRACK_LIMIT
            )
            val rows = checkTrackedGitHub(
                repoFilter = repoFilter,
                sourceMode = sourceMode,
                filterMode = filterMode,
                sortMode = sortSelection.mode,
                sortDirection = sortSelection.direction
            )
                .let { data -> if (onlyUpdates) data.filter { it.hasUpdate } else data }
                .take(limit)

            val text = if (rows.isEmpty()) {
                if (onlyUpdates) "No tracked repos with updates." else "No tracked repos matched."
            } else {
                rows.joinToString("\n") { row ->
                    val repo = "${row.item.owner}/${row.item.repo}"
                    val pre =
                        if (row.preReleaseVersion.isNotBlank()) " | pre=${row.preReleaseVersion}" else ""
                    "$repo | local=${row.localVersion} | stable=${row.stableVersion}$pre | status=${row.status} | update=${row.hasUpdate}"
                }
            }
            text
        }

        server.addMcpTextTool(environment, name = "keios.github.tracks.summary") { request ->
            val mode = argString(request.arguments?.get("mode")).trim().lowercase(Locale.ROOT)
            val repoFilter = argString(request.arguments?.get("repoFilter")).trim()
            val sourceMode = argString(request.arguments?.get("sourceMode")).trim()
            val filterMode = parseGitHubTrackedFilterMode(
                argString(request.arguments?.get("filterMode"))
            )
            val sortSelection = parseGitHubSortSelection(
                modeRaw = argString(request.arguments?.get("sortMode")),
                directionRaw = argString(request.arguments?.get("sortDirection"))
            )
            if (mode == "network") {
                buildGitHubTrackedSummaryFromNetwork(
                    repoFilter = repoFilter,
                    sourceMode = sourceMode,
                    filterMode = filterMode,
                    sortMode = sortSelection.mode,
                    sortDirection = sortSelection.direction
                )
            } else {
                buildGitHubTrackedSummaryFromCache(
                    repoFilter = repoFilter,
                    sourceMode = sourceMode,
                    filterMode = filterMode,
                    sortMode = sortSelection.mode,
                    sortDirection = sortSelection.direction
                )
            }
        }

        server.addMcpTextTool(environment, name = "keios.github.cache.clear") { _ ->
            GitHubCacheService.clearGitHubMcpCaches()
            "cleared=github_check_cache,github_release_asset_cache,github_star_import_apk_verification_cache"
        }

        server.addMcpTextTool(environment, name = "keios.github.link.parse") { request ->
            val text = argString(request.arguments?.get("text"))
            buildGitHubShareParseText(text)
        }

        server.addMcpTextTool(environment, name = "keios.github.link.resolve") { request ->
            val text = argString(request.arguments?.get("text"))
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_ENTRY_LIMIT).coerceIn(
                1,
                MAX_ENTRY_LIMIT
            )
            buildGitHubShareResolveText(text = text, limit = limit)
        }

        server.addMcpTextTool(environment, name = "keios.github.link.pending") { request ->
            val clear = argBoolean(request.arguments?.get("clear"), false)
            buildGitHubSharePendingText(clear = clear)
        }
    }

    private fun buildGitHubTrackedSnapshotText(): String {
        val snapshot = githubTrackService.loadTrackSnapshotBlocking()
        val sourceCounts = GitHubTrackedItemsTransferService.calculateSourceCounts(snapshot.items)
        val optionCounts = GitHubTrackedItemsTransferService.calculateOptionCounts(snapshot.items)
        val actionsIntervalOverrideCount = snapshot.items.count {
            it.checkActionsUpdates &&
                    it.actionsUpdateIntervalMode != GitHubTrackedActionsUpdateIntervalMode.FollowGlobal
        }
        val cachedUpdateCount = snapshot.checkCache.values.count { it.hasUpdate == true }
        val cachedFailedCount = snapshot.checkCache.values.count {
            it.message.isGitHubCheckFailureMessage()
        }
        return buildString {
            appendLine("trackedCount=${snapshot.items.size}")
            appendLine("githubRepositoryCount=${sourceCounts.githubRepositoryCount}")
            appendLine("directApkCount=${sourceCounts.directApkCount}")
            appendLine("cachedCheckCount=${snapshot.checkCache.size}")
            appendLine("cachedHasUpdateCount=$cachedUpdateCount")
            appendLine("cachedFailedCount=$cachedFailedCount")
            appendLine("lastRefreshMs=${snapshot.lastRefreshMs}")
            appendLine("refreshIntervalHours=${snapshot.refreshIntervalHours}")
            appendLine("lookupStrategy=${snapshot.lookupConfig.selectedStrategy.storageId}")
            appendLine("actionsStrategy=${snapshot.lookupConfig.actionsStrategy.storageId}")
            appendLine("apiTokenConfigured=${snapshot.lookupConfig.apiToken.isNotBlank()}")
            appendLine("checkAllTrackedPreReleases=${snapshot.lookupConfig.checkAllTrackedPreReleases}")
            appendLine("aggressiveApkFiltering=${snapshot.lookupConfig.aggressiveApkFiltering}")
            appendLine("preciseApkVersionEnabled=${snapshot.lookupConfig.preciseApkVersionEnabled}")
            appendLine("checkAllDirectApkPreReleases=${snapshot.lookupConfig.checkAllDirectApkPreReleases}")
            appendLine("preciseApkVersionOverrideCount=${optionCounts.preciseApkVersionOverrideCount}")
            appendLine("actionsUpdateCount=${optionCounts.actionsUpdateCount}")
            appendLine("actionsIntervalOverrideCount=$actionsIntervalOverrideCount")
            appendLine("actionsIntervalModes=${actionsIntervalModesText()}")
        }.trim()
    }

    private fun filterTrackedItems(
        snapshot: GitHubTrackSnapshot,
        items: List<GitHubTrackedApp>,
        repoFilter: String,
        sourceMode: String = "",
        filterMode: GitHubTrackedFilterMode = GitHubTrackedFilterMode.All,
        cacheDependentFilters: Boolean = true
    ): List<GitHubTrackedApp> {
        val sourceModeFilter = parseTrackedSourceModeFilter(sourceMode)
        val installedPackageNames by lazy {
            items.asSequence()
                .map { item -> item.packageName.trim() }
                .filter { packageName -> packageName.isNotBlank() }
                .distinct()
                .filter { packageName ->
                    runCatching {
                        GitHubVersionUtils.localVersionInfoOrNull(appContext, packageName) != null
                    }.getOrDefault(false)
                }
                .toSet()
        }
        return items.filter { item ->
            val sourceMatches = when (sourceModeFilter) {
                null -> true
                GitHubTrackedSourceMode.GitHubRepository -> item.isGitHubRepositoryTrack()
                GitHubTrackedSourceMode.DirectApk -> item.isDirectApkTrack()
            }
            if (!sourceMatches) return@filter false
            val filterMatches = when (filterMode) {
                GitHubTrackedFilterMode.All -> true
                GitHubTrackedFilterMode.GitHubRepository -> item.isGitHubRepositoryTrack()
                GitHubTrackedFilterMode.DirectApk -> item.isDirectApkTrack()
                GitHubTrackedFilterMode.Installed -> item.packageName.trim() in installedPackageNames
                GitHubTrackedFilterMode.ActionsCheckEnabled -> item.checkActionsUpdates
                GitHubTrackedFilterMode.PreReleaseTracked -> {
                    !cacheDependentFilters || snapshot.checkCache[item.id]?.isPreRelease == true
                }

                GitHubTrackedFilterMode.UpdateAvailable -> {
                    !cacheDependentFilters ||
                            snapshot.checkCache[item.id]?.let { entry ->
                                entry.hasUpdate == true || entry.hasPreReleaseUpdate
                            } == true
                }

                GitHubTrackedFilterMode.FailedChecks -> {
                    !cacheDependentFilters ||
                            snapshot.checkCache[item.id]?.message?.isGitHubCheckFailureMessage() == true
                }
            }
            if (!filterMatches) return@filter false
            if (repoFilter.isBlank()) return@filter true
            "${item.owner}/${item.repo}".contains(repoFilter, ignoreCase = true) ||
                    item.repoUrl.contains(repoFilter, ignoreCase = true) ||
                    item.packageName.contains(repoFilter, ignoreCase = true) ||
                    item.appLabel.contains(repoFilter, ignoreCase = true)
        }
    }

    private fun buildGitHubTrackedListText(
        repoFilter: String,
        sourceMode: String,
        filterMode: GitHubTrackedFilterMode,
        sortMode: GitHubSortMode,
        sortDirection: GitHubSortDirection,
        limit: Int
    ): String {
        val snapshot = githubTrackService.loadTrackSnapshotBlocking()
        val items = sortTrackedItems(
            items = filterTrackedItems(
                snapshot = snapshot,
                items = snapshot.items,
                repoFilter = repoFilter,
                sourceMode = sourceMode,
                filterMode = filterMode
            ),
            snapshot = snapshot,
            sortMode = sortMode,
            sortDirection = sortDirection
        ).take(limit)
        if (items.isEmpty()) return "No tracked GitHub apps."
        return items.joinToString("\n") { item ->
            "${item.owner}/${item.repo} | label=${item.appLabel} | package=${item.packageName} | " +
                    "sourceMode=${item.sourceMode.storageId} | url=${item.repoUrl} | " +
                    "preferPreRelease=${item.preferPreRelease} | " +
                    "checkActionsUpdates=${item.checkActionsUpdates} | " +
                    "actionsUpdateIntervalMode=${item.actionsUpdateIntervalMode.storageId} | " +
                    "preciseApkVersionMode=${item.preciseApkVersionMode.storageId} | " +
                    "latestDownload=${item.alwaysShowLatestReleaseDownloadButton}"
        }
    }

    private fun buildGitHubTrackedExportText(
        repoFilter: String,
        sourceMode: String,
        filterMode: GitHubTrackedFilterMode,
        sortMode: GitHubSortMode,
        sortDirection: GitHubSortDirection
    ): String {
        val snapshot = githubTrackService.loadTrackSnapshotBlocking()
        val items = sortTrackedItems(
            items = filterTrackedItems(
                snapshot = snapshot,
                items = snapshot.items,
                repoFilter = repoFilter,
                sourceMode = sourceMode,
                filterMode = filterMode
            ),
            snapshot = snapshot,
            sortMode = sortMode,
            sortDirection = sortDirection
        )
        return GitHubTrackedItemsTransferService.buildExportJson(items)
    }

    private fun buildGitHubTrackedImportText(rawJson: String, apply: Boolean): String {
        val payload = GitHubTrackedItemsTransferService.parseImport(rawJson)
        val existing = GitHubTrackedItemsTransferService.loadItems()
        val preview = GitHubTrackedItemsTransferService.buildImportPreview(
            payload = payload,
            existingItems = existing,
        )
        val optionCounts = GitHubTrackedItemsTransferService.calculateOptionCounts(payload.items)
        val hasChanges = preview.newCount > 0 || preview.updatedCount > 0
        val applyResult =
            if (apply && hasChanges) {
                GitHubTrackedItemsTransferService.applyImport(
                    payload = payload,
                    onRefreshNeeded = { AppBackgroundScheduler.scheduleGitHubRefresh(appContext) },
                    existingItems = existing,
                )
            } else {
                null
            }
        val addedCount = applyResult?.addedCount ?: preview.newCount
        val updatedCount = applyResult?.updatedCount ?: preview.updatedCount
        val unchangedCount = applyResult?.unchangedCount ?: preview.unchangedCount
        val applied = apply && hasChanges
        if (applied) {
            githubTrackService.clearCheckCacheBlocking()
        }
        return buildString {
            appendLine("apply=$apply")
            appendLine("format=${payload.format.ifBlank { "(legacy)" }}")
            appendLine("schemaVersion=${payload.schemaVersion}")
            appendLine("sourceCount=${payload.sourceCount}")
            appendLine("validCount=${payload.items.size}")
            appendLine("invalidCount=${payload.invalidCount}")
            appendLine("duplicateCount=${payload.duplicateCount}")
            appendLine("preferPreReleaseCount=${optionCounts.preferPreReleaseCount}")
            appendLine("actionsUpdateCount=${optionCounts.actionsUpdateCount}")
            appendLine(
                "actionsIntervalOverrideCount=${
                    payload.items.count {
                        it.checkActionsUpdates &&
                                it.actionsUpdateIntervalMode !=
                                GitHubTrackedActionsUpdateIntervalMode.FollowGlobal
                    }
                }"
            )
            appendLine("preciseApkVersionOverrideCount=${optionCounts.preciseApkVersionOverrideCount}")
            appendLine("latestReleaseDownloadCount=${optionCounts.latestReleaseDownloadCount}")
            appendLine("newCount=$addedCount")
            appendLine("updatedCount=$updatedCount")
            appendLine("unchangedCount=$unchangedCount")
            appendLine("mergedCount=${preview.mergedCount}")
            appendLine("applied=$applied")
            appendLine("cacheCleared=$applied")
        }.trim()
    }

    private fun buildGitHubTrackedSummaryFromCache(
        repoFilter: String,
        sourceMode: String,
        filterMode: GitHubTrackedFilterMode,
        sortMode: GitHubSortMode,
        sortDirection: GitHubSortDirection
    ): String {
        val snapshot = githubTrackService.loadTrackSnapshotBlocking()
        val tracked = sortTrackedItems(
            items = filterTrackedItems(
                snapshot = snapshot,
                items = snapshot.items,
                repoFilter = repoFilter,
                sourceMode = sourceMode,
                filterMode = filterMode
            ),
            snapshot = snapshot,
            sortMode = sortMode,
            sortDirection = sortDirection
        )
        if (tracked.isEmpty()) {
            return "mode=cache\ntracked=0\nmatched=0"
        }

        val ids = tracked.map { it.id }.toSet()
        val cacheHit = snapshot.checkCache.filterKeys { key -> key in ids }
        val hasUpdate = cacheHit.count { it.value.hasUpdate == true }
        val unknown = cacheHit.count { it.value.hasUpdate == null }
        val preRelease =
            cacheHit.count { it.value.showPreReleaseInfo || it.value.hasPreReleaseUpdate || it.value.recommendsPreRelease }

        return buildString {
            appendLine("mode=cache")
            appendLine("tracked=${snapshot.items.size}")
            appendLine("matched=${tracked.size}")
            appendLine("githubRepository=${tracked.count { it.isGitHubRepositoryTrack() }}")
            appendLine("directApk=${tracked.count { it.isDirectApkTrack() }}")
            appendLine("cacheHit=${cacheHit.size}")
            appendLine("hasUpdate=$hasUpdate")
            appendLine("unknown=$unknown")
            appendLine("preReleaseState=$preRelease")
            appendLine("lastRefreshMs=${snapshot.lastRefreshMs}")
            tracked.asSequence()
                .mapNotNull { item -> cacheHit[item.id]?.let { entry -> item.id to entry } }
                .take(20)
                .forEach { (id, entry) ->
                    appendLine("$id=${entry.message.toMcpGitHubMessage()}")
                }
        }.trim()
    }

    private suspend fun buildGitHubTrackedSummaryFromNetwork(
        repoFilter: String,
        sourceMode: String,
        filterMode: GitHubTrackedFilterMode,
        sortMode: GitHubSortMode,
        sortDirection: GitHubSortDirection
    ): String {
        val rows = checkTrackedGitHub(
            repoFilter = repoFilter,
            sourceMode = sourceMode,
            filterMode = filterMode,
            sortMode = sortMode,
            sortDirection = sortDirection
        )
        val hasUpdate = rows.count { it.hasUpdate }
        val preRelease = rows.count {
            it.preReleaseVersion.isNotBlank() || it.status.contains("pre", ignoreCase = true)
        }
        return buildString {
            appendLine("mode=network")
            appendLine("matched=${rows.size}")
            appendLine("githubRepository=${rows.count { it.item.isGitHubRepositoryTrack() }}")
            appendLine("directApk=${rows.count { it.item.isDirectApkTrack() }}")
            appendLine("hasUpdate=$hasUpdate")
            appendLine("preReleaseState=$preRelease")
            rows.take(20)
                .forEach { row ->
                    appendLine("${row.item.owner}/${row.item.repo}=${row.status}")
                }
        }.trim()
    }

    private suspend fun checkTrackedGitHub(
        repoFilter: String,
        sourceMode: String,
        filterMode: GitHubTrackedFilterMode,
        sortMode: GitHubSortMode,
        sortDirection: GitHubSortDirection
    ): List<GitHubCheckRow> {
        val snapshot = githubTrackService.loadTrackSnapshotBlocking()
        val filtered = filterTrackedItems(
            snapshot = snapshot,
            items = snapshot.items,
            repoFilter = repoFilter,
            sourceMode = sourceMode,
            filterMode = filterMode,
            cacheDependentFilters = false
        )
        return checkTrackedGitHub(
            appContext = appContext,
            snapshot = snapshot,
            filtered = filtered,
            filterMode = filterMode,
            sortMode = sortMode,
            sortDirection = sortDirection
        )
    }
}
