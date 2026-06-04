package os.kei.feature.github.mcp

import android.content.Context
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubShareImportResolver
import os.kei.feature.github.data.remote.GitHubShareIntentParser
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.domain.GitHubReleaseCheckService
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isGitHubRepositoryTrack
import java.util.Locale

private const val MCP_GITHUB_CHECK_PARALLELISM = 4

internal data class GitHubCheckRow(
    val item: GitHubTrackedApp,
    val localVersion: String,
    val stableVersion: String,
    val preReleaseVersion: String,
    val status: String,
    val hasUpdate: Boolean,
    val hasPreReleaseUpdate: Boolean,
    val isPreRelease: Boolean,
    val addedAtMillis: Long,
    val modifiedAtMillis: Long
)

internal data class GitHubSortSelection(
    val mode: GitHubSortMode,
    val direction: GitHubSortDirection
)

internal suspend fun checkTrackedGitHub(
    appContext: Context,
    snapshot: GitHubTrackSnapshot,
    filtered: List<GitHubTrackedApp>,
    filterMode: GitHubTrackedFilterMode,
    sortMode: GitHubSortMode,
    sortDirection: GitHubSortDirection
): List<GitHubCheckRow> {
    val rows = GitHubExecution.mapOrderedBounded(
        items = filtered,
        maxConcurrency = MCP_GITHUB_CHECK_PARALLELISM
    ) { item ->
        runCatching { evaluateTrackedApp(appContext, item, snapshot) }.getOrElse { err ->
            GitHubCheckRow(
                item = item,
                localVersion = runCatching {
                    GitHubVersionUtils.localVersionName(appContext, item.packageName)
                }.getOrDefault("unknown"),
                stableVersion = "unknown",
                preReleaseVersion = "",
                status = "Check failed: ${err.message ?: "unknown"}",
                hasUpdate = false,
                hasPreReleaseUpdate = false,
                isPreRelease = false,
                addedAtMillis = snapshot.trackedAddedAtById[item.id] ?: -1L,
                modifiedAtMillis = snapshot.trackedModifiedAtById[item.id]
                    ?: snapshot.trackedAddedAtById[item.id]
                    ?: -1L
            )
        }
    }
    return sortCheckRows(
        rows = filterCheckRows(rows, filterMode),
        sortMode = sortMode,
        sortDirection = sortDirection
    )
}

internal fun filterCheckRows(
    rows: List<GitHubCheckRow>,
    filterMode: GitHubTrackedFilterMode
): List<GitHubCheckRow> {
    return rows.filter { row ->
        when (filterMode) {
            GitHubTrackedFilterMode.All,
            GitHubTrackedFilterMode.GitHubRepository,
            GitHubTrackedFilterMode.GitRepository,
            GitHubTrackedFilterMode.DirectApk,
            GitHubTrackedFilterMode.Installed,
            GitHubTrackedFilterMode.ActionsCheckEnabled -> true

            GitHubTrackedFilterMode.PreReleaseTracked -> row.isPreRelease
            GitHubTrackedFilterMode.UpdateAvailable ->
                row.hasUpdate || row.hasPreReleaseUpdate

            GitHubTrackedFilterMode.FailedChecks ->
                row.status.isGitHubCheckFailureMessage()
        }
    }
}

internal fun parseTrackedSourceModeFilter(raw: String): GitHubTrackedSourceMode? {
    return when (raw.trim().lowercase(Locale.ROOT)) {
        "github", "repo", "repository", "github_repository" ->
            GitHubTrackedSourceMode.GitHubRepository

        "git", "git_repository", "gitee", "gitlab" -> GitHubTrackedSourceMode.GitRepository
        "direct", "apk", "direct_apk" -> GitHubTrackedSourceMode.DirectApk
        else -> null
    }
}

internal fun parseGitHubSortSelection(
    modeRaw: String,
    directionRaw: String
): GitHubSortSelection {
    return GitHubSortSelection(
        mode = parseGitHubSortMode(modeRaw),
        direction = parseGitHubSortDirection(directionRaw)
    )
}

internal fun parseGitHubSortMode(raw: String): GitHubSortMode {
    return when (raw.trim().lowercase(Locale.ROOT)) {
        "", "update" -> GitHubSortMode.Update
        "name" -> GitHubSortMode.Name
        "prerelease", "pre_release", "pre-release" -> GitHubSortMode.PreRelease
        "changed" -> GitHubSortMode.Changed
        "added" -> GitHubSortMode.Added
        else -> GitHubSortMode.Update
    }
}

internal fun parseGitHubTrackedFilterMode(raw: String): GitHubTrackedFilterMode {
    val normalized = raw.trim().lowercase(Locale.ROOT)
    return GitHubTrackedFilterMode.entries.firstOrNull {
        it.storageId.equals(normalized, ignoreCase = true)
    } ?: when (normalized) {
        "", "all" -> GitHubTrackedFilterMode.All
        "github", "repo", "repository" -> GitHubTrackedFilterMode.GitHubRepository
        "git", "git_repository", "gitee", "gitlab" -> GitHubTrackedFilterMode.GitRepository
        "direct", "apk", "subscription", "subscription_project" ->
            GitHubTrackedFilterMode.DirectApk

        "pre_release", "pre-release", "prerelease" ->
            GitHubTrackedFilterMode.PreReleaseTracked

        "updates", "update" -> GitHubTrackedFilterMode.UpdateAvailable
        "failed", "failure" -> GitHubTrackedFilterMode.FailedChecks
        "actions", "actions_enabled" -> GitHubTrackedFilterMode.ActionsCheckEnabled
        else -> GitHubTrackedFilterMode.All
    }
}

internal fun actionsIntervalModesText(): String {
    return GitHubTrackedActionsUpdateIntervalMode.entries.joinToString(",") { it.storageId }
}

internal fun parseGitHubSortDirection(raw: String): GitHubSortDirection {
    return when (raw.trim().lowercase(Locale.ROOT)) {
        "reverse" -> GitHubSortDirection.Reverse
        else -> GitHubSortDirection.Forward
    }
}

internal fun sortTrackedItems(
    items: List<GitHubTrackedApp>,
    snapshot: GitHubTrackSnapshot,
    sortMode: GitHubSortMode,
    sortDirection: GitHubSortDirection
): List<GitHubTrackedApp> {
    val titleForSort: (GitHubTrackedApp) -> String = { item ->
        item.appLabel.ifBlank { item.repo }
            .ifBlank { item.packageName }
            .lowercase(Locale.ROOT)
    }
    val addedNewest: (GitHubTrackedApp) -> Long = { item ->
        snapshot.trackedAddedAtById[item.id]?.takeIf { it > 0L } ?: Long.MIN_VALUE
    }
    val addedOldest: (GitHubTrackedApp) -> Long = { item ->
        snapshot.trackedAddedAtById[item.id]?.takeIf { it > 0L } ?: Long.MAX_VALUE
    }
    val modifiedNewest: (GitHubTrackedApp) -> Long = { item ->
        snapshot.trackedModifiedAtById[item.id]?.takeIf { it > 0L }
            ?: snapshot.trackedAddedAtById[item.id]?.takeIf { it > 0L }
            ?: Long.MIN_VALUE
    }
    val modifiedOldest: (GitHubTrackedApp) -> Long = { item ->
        snapshot.trackedModifiedAtById[item.id]?.takeIf { it > 0L }
            ?: snapshot.trackedAddedAtById[item.id]?.takeIf { it > 0L }
            ?: Long.MAX_VALUE
    }
    return when (sortMode) {
        GitHubSortMode.Update -> when (sortDirection) {
            GitHubSortDirection.Forward -> items.sortedWith(
                compareByDescending<GitHubTrackedApp> {
                    snapshot.checkCache[it.id]?.hasUpdate == true
                }
                    .thenByDescending {
                        snapshot.checkCache[it.id]?.hasPreReleaseUpdate == true
                    }
                    .thenBy { titleForSort(it) }
            )

            GitHubSortDirection.Reverse -> items.sortedWith(
                compareBy<GitHubTrackedApp> {
                    snapshot.checkCache[it.id]?.hasUpdate == true
                }
                    .thenBy { snapshot.checkCache[it.id]?.hasPreReleaseUpdate == true }
                    .thenByDescending { titleForSort(it) }
            )
        }

        GitHubSortMode.Name -> when (sortDirection) {
            GitHubSortDirection.Forward -> items.sortedBy { titleForSort(it) }
            GitHubSortDirection.Reverse -> items.sortedByDescending { titleForSort(it) }
        }

        GitHubSortMode.PreRelease -> when (sortDirection) {
            GitHubSortDirection.Forward -> items.sortedWith(
                compareByDescending<GitHubTrackedApp> {
                    snapshot.checkCache[it.id]?.isPreRelease == true
                }
                    .thenByDescending { snapshot.checkCache[it.id]?.hasUpdate == true }
                    .thenBy { titleForSort(it) }
            )

            GitHubSortDirection.Reverse -> items.sortedWith(
                compareBy<GitHubTrackedApp> {
                    snapshot.checkCache[it.id]?.isPreRelease == true
                }
                    .thenBy { snapshot.checkCache[it.id]?.hasUpdate == true }
                    .thenByDescending { titleForSort(it) }
            )
        }

        GitHubSortMode.Changed -> when (sortDirection) {
            GitHubSortDirection.Forward -> items.sortedWith(
                compareByDescending<GitHubTrackedApp> { modifiedNewest(it) }
                    .thenBy { titleForSort(it) }
            )

            GitHubSortDirection.Reverse -> items.sortedWith(
                compareBy<GitHubTrackedApp> { modifiedOldest(it) }
                    .thenBy { titleForSort(it) }
            )
        }

        GitHubSortMode.Added -> when (sortDirection) {
            GitHubSortDirection.Forward -> items.sortedWith(
                compareByDescending<GitHubTrackedApp> { addedNewest(it) }
                    .thenBy { titleForSort(it) }
            )

            GitHubSortDirection.Reverse -> items.sortedWith(
                compareBy<GitHubTrackedApp> { addedOldest(it) }
                    .thenBy { titleForSort(it) }
            )
        }
    }
}

internal fun sortCheckRows(
    rows: List<GitHubCheckRow>,
    sortMode: GitHubSortMode,
    sortDirection: GitHubSortDirection
): List<GitHubCheckRow> {
    val titleForSort: (GitHubCheckRow) -> String = { row ->
        row.item.appLabel.ifBlank { row.item.repo }
            .ifBlank { row.item.packageName }
            .lowercase(Locale.ROOT)
    }
    val addedNewest: (GitHubCheckRow) -> Long = { row ->
        row.addedAtMillis.takeIf { it > 0L } ?: Long.MIN_VALUE
    }
    val addedOldest: (GitHubCheckRow) -> Long = { row ->
        row.addedAtMillis.takeIf { it > 0L } ?: Long.MAX_VALUE
    }
    val modifiedNewest: (GitHubCheckRow) -> Long = { row ->
        row.modifiedAtMillis.takeIf { it > 0L }
            ?: row.addedAtMillis.takeIf { it > 0L }
            ?: Long.MIN_VALUE
    }
    val modifiedOldest: (GitHubCheckRow) -> Long = { row ->
        row.modifiedAtMillis.takeIf { it > 0L }
            ?: row.addedAtMillis.takeIf { it > 0L }
            ?: Long.MAX_VALUE
    }
    return when (sortMode) {
        GitHubSortMode.Update -> when (sortDirection) {
            GitHubSortDirection.Forward -> rows.sortedWith(
                compareByDescending<GitHubCheckRow> { it.hasUpdate }
                    .thenByDescending { it.hasPreReleaseUpdate }
                    .thenBy { titleForSort(it) }
            )

            GitHubSortDirection.Reverse -> rows.sortedWith(
                compareBy<GitHubCheckRow> { it.hasUpdate }
                    .thenBy { it.hasPreReleaseUpdate }
                    .thenByDescending { titleForSort(it) }
            )
        }

        GitHubSortMode.Name -> when (sortDirection) {
            GitHubSortDirection.Forward -> rows.sortedBy { titleForSort(it) }
            GitHubSortDirection.Reverse -> rows.sortedByDescending { titleForSort(it) }
        }

        GitHubSortMode.PreRelease -> when (sortDirection) {
            GitHubSortDirection.Forward -> rows.sortedWith(
                compareByDescending<GitHubCheckRow> { it.isPreRelease }
                    .thenByDescending { it.hasUpdate }
                    .thenBy { titleForSort(it) }
            )

            GitHubSortDirection.Reverse -> rows.sortedWith(
                compareBy<GitHubCheckRow> { it.isPreRelease }
                    .thenBy { it.hasUpdate }
                    .thenByDescending { titleForSort(it) }
            )
        }

        GitHubSortMode.Changed -> when (sortDirection) {
            GitHubSortDirection.Forward -> rows.sortedWith(
                compareByDescending<GitHubCheckRow> { modifiedNewest(it) }
                    .thenBy { titleForSort(it) }
            )

            GitHubSortDirection.Reverse -> rows.sortedWith(
                compareBy<GitHubCheckRow> { modifiedOldest(it) }
                    .thenBy { titleForSort(it) }
            )
        }

        GitHubSortMode.Added -> when (sortDirection) {
            GitHubSortDirection.Forward -> rows.sortedWith(
                compareByDescending<GitHubCheckRow> { addedNewest(it) }
                    .thenBy { titleForSort(it) }
            )

            GitHubSortDirection.Reverse -> rows.sortedWith(
                compareBy<GitHubCheckRow> { addedOldest(it) }
                    .thenBy { titleForSort(it) }
            )
        }
    }
}

internal suspend fun evaluateTrackedApp(
    appContext: Context,
    item: GitHubTrackedApp,
    snapshot: GitHubTrackSnapshot
): GitHubCheckRow {
    val check = GitHubReleaseCheckService.evaluateTrackedApp(appContext, item)
    return GitHubCheckRow(
        item = item,
        localVersion = check.localVersion,
        stableVersion = check.stableRelease?.displayVersion.orEmpty().ifBlank { "unknown" },
        preReleaseVersion = check.preReleaseInfo,
        status = check.message.toMcpGitHubMessage(),
        hasUpdate = check.hasUpdate == true,
        hasPreReleaseUpdate = check.hasPreReleaseUpdate,
        isPreRelease = check.isPreReleaseInstalled,
        addedAtMillis = snapshot.trackedAddedAtById[item.id] ?: -1L,
        modifiedAtMillis = snapshot.trackedModifiedAtById[item.id]
            ?: snapshot.trackedAddedAtById[item.id]
            ?: -1L
    )
}

internal fun buildGitHubShareParseText(text: String): String {
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

internal suspend fun buildGitHubShareResolveText(text: String, limit: Int): String {
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

internal fun buildGitHubSharePendingText(clear: Boolean): String {
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

internal fun String.isGitHubCheckFailureMessage(): Boolean {
    val raw = trim()
    return GitHubTrackedReleaseStatus.isFailureMessage(raw) ||
            raw.contains("failed", ignoreCase = true) ||
            raw.contains("失败", ignoreCase = true)
}

internal fun String.toMcpGitHubMessage(): String {
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
