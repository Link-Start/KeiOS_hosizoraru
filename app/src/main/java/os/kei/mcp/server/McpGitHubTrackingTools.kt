package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.feature.github.data.local.GitHubReleaseAssetCacheStore
import os.kei.feature.github.data.local.GitHubStarImportApkVerificationCacheStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackedItemsImportPayload
import os.kei.feature.github.data.remote.GitHubShareImportResolver
import os.kei.feature.github.data.remote.GitHubShareIntentParser
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.domain.GitHubReleaseCheckService
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import java.util.Locale

internal class McpGitHubTrackingTools(
    private val environment: McpToolEnvironment
) {
    private data class GitHubCheckRow(
        val item: GitHubTrackedApp,
        val localVersion: String,
        val stableVersion: String,
        val preReleaseVersion: String,
        val status: String,
        val hasUpdate: Boolean
    )

    private val appContext get() = environment.appContext

    fun register(server: Server) {
        server.addMcpTextTool(environment, name = "keios.github.tracks.snapshot") { _ ->
            buildGitHubTrackedSnapshotText()
        }

        server.addMcpTextTool(environment, name = "keios.github.tracks.list") { request ->
            val repoFilter = argString(request.arguments?.get("repoFilter")).trim()
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_TRACK_LIMIT).coerceIn(
                1,
                MAX_TRACK_LIMIT
            )
            buildGitHubTrackedListText(repoFilter = repoFilter, limit = limit)
        }

        server.addMcpTextTool(environment, name = "keios.github.tracks.export") { request ->
            val repoFilter = argString(request.arguments?.get("repoFilter")).trim()
            buildGitHubTrackedExportText(repoFilter = repoFilter)
        }

        server.addMcpTextTool(environment, name = "keios.github.tracks.import") { request ->
            val rawJson = argString(request.arguments?.get("json"))
            val apply = argBoolean(request.arguments?.get("apply"), false)
            buildGitHubTrackedImportText(rawJson = rawJson, apply = apply)
        }

        server.addMcpTextTool(environment, name = "keios.github.tracks.check") { request ->
            val repoFilter = argString(request.arguments?.get("repoFilter")).trim()
            val onlyUpdates = argBoolean(request.arguments?.get("onlyUpdates"), false)
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_TRACK_LIMIT).coerceIn(
                1,
                MAX_TRACK_LIMIT
            )
            val rows = checkTrackedGitHub(repoFilter)
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
            if (mode == "network") {
                buildGitHubTrackedSummaryFromNetwork(repoFilter)
            } else {
                buildGitHubTrackedSummaryFromCache(repoFilter)
            }
        }

        server.addMcpTextTool(environment, name = "keios.github.cache.clear") { _ ->
            GitHubTrackStore.clearCheckCache()
            GitHubReleaseAssetCacheStore.clearAll()
            GitHubStarImportApkVerificationCacheStore.clearAll()
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
        val snapshot = GitHubTrackStore.loadSnapshot()
        val cachedUpdateCount = snapshot.checkCache.values.count { it.hasUpdate == true }
        val cachedFailedCount = snapshot.checkCache.values.count {
            it.message.isGitHubCheckFailureMessage()
        }
        return buildString {
            appendLine("trackedCount=${snapshot.items.size}")
            appendLine("cachedCheckCount=${snapshot.checkCache.size}")
            appendLine("cachedHasUpdateCount=$cachedUpdateCount")
            appendLine("cachedFailedCount=$cachedFailedCount")
            appendLine("lastRefreshMs=${snapshot.lastRefreshMs}")
            appendLine("refreshIntervalHours=${snapshot.refreshIntervalHours}")
            appendLine("lookupStrategy=${snapshot.lookupConfig.selectedStrategy.storageId}")
            appendLine("apiTokenConfigured=${snapshot.lookupConfig.apiToken.isNotBlank()}")
            appendLine("checkAllTrackedPreReleases=${snapshot.lookupConfig.checkAllTrackedPreReleases}")
            appendLine("aggressiveApkFiltering=${snapshot.lookupConfig.aggressiveApkFiltering}")
        }.trim()
    }

    private fun filterTrackedItems(
        items: List<GitHubTrackedApp>,
        repoFilter: String
    ): List<GitHubTrackedApp> {
        if (repoFilter.isBlank()) return items
        return items.filter {
            "${it.owner}/${it.repo}".contains(repoFilter, ignoreCase = true) ||
                    it.packageName.contains(repoFilter, ignoreCase = true) ||
                    it.appLabel.contains(repoFilter, ignoreCase = true)
        }
    }

    private fun buildGitHubTrackedListText(repoFilter: String, limit: Int): String {
        val items = filterTrackedItems(GitHubTrackStore.load(), repoFilter).take(limit)
        if (items.isEmpty()) return "No tracked GitHub apps."
        return items.joinToString("\n") { item ->
            "${item.owner}/${item.repo} | label=${item.appLabel} | package=${item.packageName} | preferPreRelease=${item.preferPreRelease}"
        }
    }

    private fun buildGitHubTrackedExportText(repoFilter: String): String {
        val items = filterTrackedItems(GitHubTrackStore.load(), repoFilter)
        return GitHubTrackStore.buildTrackedItemsExportJson(items)
    }

    private fun buildGitHubTrackedImportText(rawJson: String, apply: Boolean): String {
        val payload = GitHubTrackStore.parseTrackedItemsImport(rawJson)
        val existing = GitHubTrackStore.load()
        val preview = buildTrackedImportPreview(payload, existing)
        if (apply && (preview.addedCount > 0 || preview.updatedCount > 0)) {
            val merged = mergeTrackedItems(payload, existing)
            GitHubTrackStore.save(merged)
            GitHubTrackStore.clearCheckCache()
        }
        return buildString {
            appendLine("apply=$apply")
            appendLine("sourceCount=${payload.sourceCount}")
            appendLine("validCount=${payload.items.size}")
            appendLine("invalidCount=${payload.invalidCount}")
            appendLine("duplicateCount=${payload.duplicateCount}")
            appendLine("newCount=${preview.addedCount}")
            appendLine("updatedCount=${preview.updatedCount}")
            appendLine("unchangedCount=${preview.unchangedCount}")
            appendLine("mergedCount=${existing.size + preview.addedCount}")
            appendLine("applied=${apply && (preview.addedCount > 0 || preview.updatedCount > 0)}")
            appendLine("cacheCleared=${apply && (preview.addedCount > 0 || preview.updatedCount > 0)}")
        }.trim()
    }

    private data class TrackedImportPreviewCounts(
        val addedCount: Int,
        val updatedCount: Int,
        val unchangedCount: Int
    )

    private fun buildTrackedImportPreview(
        payload: GitHubTrackedItemsImportPayload,
        existingItems: List<GitHubTrackedApp>
    ): TrackedImportPreviewCounts {
        val existingItemsById = existingItems.associateBy { it.id }
        var addedCount = 0
        var updatedCount = 0
        var unchangedCount = 0
        payload.items.forEach { item ->
            when (val existingItem = existingItemsById[item.id]) {
                null -> addedCount += 1
                item -> unchangedCount += 1
                else -> updatedCount += 1
            }
        }
        return TrackedImportPreviewCounts(
            addedCount = addedCount,
            updatedCount = updatedCount,
            unchangedCount = unchangedCount
        )
    }

    private fun mergeTrackedItems(
        payload: GitHubTrackedItemsImportPayload,
        existingItems: List<GitHubTrackedApp>
    ): List<GitHubTrackedApp> {
        val mergedItems = existingItems.toMutableList()
        val indexById = mergedItems.withIndex()
            .associate { it.value.id to it.index }
            .toMutableMap()
        payload.items.forEach { item ->
            val existingIndex = indexById[item.id]
            if (existingIndex == null) {
                mergedItems += item
                indexById[item.id] = mergedItems.lastIndex
            } else {
                mergedItems[existingIndex] = item
            }
        }
        return mergedItems
    }

    private fun buildGitHubTrackedSummaryFromCache(repoFilter: String): String {
        val snapshot = GitHubTrackStore.loadSnapshot()
        val tracked = filterTrackedItems(snapshot.items, repoFilter)
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
            appendLine("cacheHit=${cacheHit.size}")
            appendLine("hasUpdate=$hasUpdate")
            appendLine("unknown=$unknown")
            appendLine("preReleaseState=$preRelease")
            appendLine("lastRefreshMs=${snapshot.lastRefreshMs}")
            cacheHit.entries
                .sortedByDescending { it.value.hasUpdate == true }
                .take(20)
                .forEach { (id, entry) ->
                    appendLine("$id=${entry.message.toMcpGitHubMessage()}")
                }
        }.trim()
    }

    private fun buildGitHubTrackedSummaryFromNetwork(repoFilter: String): String {
        val rows = checkTrackedGitHub(repoFilter)
        val hasUpdate = rows.count { it.hasUpdate }
        val preRelease = rows.count {
            it.preReleaseVersion.isNotBlank() || it.status.contains("pre", ignoreCase = true)
        }
        return buildString {
            appendLine("mode=network")
            appendLine("matched=${rows.size}")
            appendLine("hasUpdate=$hasUpdate")
            appendLine("preReleaseState=$preRelease")
            rows.sortedByDescending { it.hasUpdate }
                .take(20)
                .forEach { row ->
                    appendLine("${row.item.owner}/${row.item.repo}=${row.status}")
                }
        }.trim()
    }

    private fun checkTrackedGitHub(repoFilter: String): List<GitHubCheckRow> {
        val items = GitHubTrackStore.load()
        val filtered = filterTrackedItems(items, repoFilter)
        return filtered.map { item ->
            runCatching { evaluateTrackedApp(item) }.getOrElse { err ->
                GitHubCheckRow(
                    item = item,
                    localVersion = runCatching {
                        GitHubVersionUtils.localVersionName(appContext, item.packageName)
                    }.getOrDefault("unknown"),
                    stableVersion = "unknown",
                    preReleaseVersion = "",
                    status = "Check failed: ${err.message ?: "unknown"}",
                    hasUpdate = false
                )
            }
        }
    }

    private fun evaluateTrackedApp(item: GitHubTrackedApp): GitHubCheckRow {
        val check = GitHubReleaseCheckService.evaluateTrackedAppBlocking(appContext, item)
        return GitHubCheckRow(
            item = item,
            localVersion = check.localVersion,
            stableVersion = check.stableRelease?.displayVersion.orEmpty().ifBlank { "unknown" },
            preReleaseVersion = check.preReleaseInfo,
            status = check.message.toMcpGitHubMessage(),
            hasUpdate = check.hasUpdate == true
        )
    }

    private fun buildGitHubShareParseText(text: String): String {
        val urls = GitHubShareIntentParser.extractGitHubUrls(text)
        val parsed = GitHubShareIntentParser.parseSharedReleaseLink(text)
        return buildString {
            appendLine("matched=${parsed != null}")
            appendLine("urlCount=${urls.size}")
            urls.forEachIndexed { index, url ->
                appendLine("url[$index]=$url")
            }
            if (parsed != null) {
                appendLine("sourceUrl=${parsed.sourceUrl}")
                appendLine("projectUrl=${parsed.projectUrl}")
                appendLine("owner=${parsed.owner}")
                appendLine("repo=${parsed.repo}")
                appendLine("type=${parsed.type.name}")
                appendLine("releaseTag=${parsed.releaseTag}")
                appendLine("assetName=${parsed.assetName}")
            }
        }.trim()
    }

    private fun buildGitHubShareResolveText(text: String, limit: Int): String {
        val lookupConfig = GitHubTrackStore.loadLookupConfig()
        return GitHubShareImportResolver.resolve(
            sharedText = text,
            lookupConfig = lookupConfig
        ).fold(
            onSuccess = { plan ->
                buildString {
                    appendLine("resolved=true")
                    appendLine("sourceUrl=${plan.parsedLink.sourceUrl}")
                    appendLine("projectUrl=${plan.parsedLink.projectUrl}")
                    appendLine("owner=${plan.parsedLink.owner}")
                    appendLine("repo=${plan.parsedLink.repo}")
                    appendLine("type=${plan.parsedLink.type.name}")
                    appendLine("releaseTag=${plan.resolvedReleaseTag}")
                    appendLine("releaseUrl=${plan.resolvedReleaseUrl}")
                    appendLine("preferredAssetName=${plan.preferredAssetName}")
                    appendLine("assetCount=${plan.assets.size}")
                    plan.assets.take(limit).forEachIndexed { index, asset ->
                        appendLine(
                            "asset[$index]=name:${asset.name} | sizeBytes:${asset.sizeBytes} | downloads:${asset.downloadCount} | type:${asset.contentType} | url:${asset.downloadUrl}"
                        )
                    }
                }.trim()
            },
            onFailure = { error ->
                buildString {
                    appendLine("resolved=false")
                    appendLine("message=${error.message ?: error.javaClass.simpleName}")
                }.trim()
            }
        )
    }

    private fun buildGitHubSharePendingText(clear: Boolean): String {
        val before = GitHubTrackStore.loadPendingShareImportTrack()
        if (clear) {
            GitHubTrackStore.savePendingShareImportTrack(null)
        }
        val after = GitHubTrackStore.loadPendingShareImportTrack()
        return buildString {
            appendLine("clear=$clear")
            appendLine("hadPending=${before != null}")
            appendLine("pending=${after != null}")
            val pending = after ?: before
            if (pending != null) {
                appendLine("projectUrl=${pending.projectUrl}")
                appendLine("owner=${pending.owner}")
                appendLine("repo=${pending.repo}")
                appendLine("releaseTag=${pending.releaseTag}")
                appendLine("assetName=${pending.assetName}")
                appendLine("armedAtMillis=${pending.armedAtMillis}")
            }
        }.trim()
    }

    private fun String.isGitHubCheckFailureMessage(): Boolean {
        val raw = trim()
        return GitHubTrackedReleaseStatus.isFailureMessage(raw) ||
                raw.contains("failed", ignoreCase = true) ||
                raw.contains("\u5931\u8d25", ignoreCase = true)
    }

    private fun String.toMcpGitHubMessage(): String {
        val raw = trim()
        val status = GitHubTrackedReleaseStatus.fromMessage(raw)
        return when {
            status == GitHubTrackedReleaseStatus.UpdateAvailable -> "Update available"
            status == GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable -> "Pre-release update available"
            status == GitHubTrackedReleaseStatus.PreReleaseOptional -> "Pre-release available"
            status == GitHubTrackedReleaseStatus.PreReleaseTracked -> "Pre-release tracked"
            status == GitHubTrackedReleaseStatus.UpToDate -> "Up to date"
            status == GitHubTrackedReleaseStatus.MatchedRelease -> "Matched release"
            status == GitHubTrackedReleaseStatus.ComparisonUncertain -> "Version comparison uncertain"
            status == GitHubTrackedReleaseStatus.Failed ->
                GitHubTrackedReleaseStatus.localizedFailureDetail(raw, "Check failed")

            GitHubTrackedReleaseStatus.isOnlyPreReleasesHint(raw) ->
                "This project may currently publish only pre-releases"

            else -> {
                raw
            }
        }
    }
}
