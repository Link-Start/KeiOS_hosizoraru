package os.kei.ui.page.main.github

import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.asset.assetDisplayName
import os.kei.ui.page.main.github.asset.assetFileExtensionLabel
import os.kei.ui.page.main.github.asset.assetIsPreferredForDevice
import os.kei.ui.page.main.github.asset.assetLikelyCompatibleWithDevice
import os.kei.ui.page.main.widget.markdown.AppMarkdownBlock
import os.kei.ui.page.main.widget.markdown.parseAppMarkdownBlocks

internal enum class GitHubDecisionLevel {
    Good,
    Review,
    Risk
}

internal enum class GitHubRepositoryHealthReason {
    UpdateAvailable,
    PreReleaseRecommended,
    CheckFailed,
    MissingPackageName,
    MissingStableRelease,
    LocalMissing,
    StableDetected,
    FreshRelease
}

internal data class GitHubRepositoryHealth(
    val score: Int,
    val level: GitHubDecisionLevel,
    val reasons: List<GitHubRepositoryHealthReason>
)

internal enum class GitHubApkTrustReason {
    PreferredAbi,
    UniversalAsset,
    IncompatibleAbi,
    DebugBuild,
    UnsignedBuild,
    SourceArchive,
    ApkLike,
    UnknownFormat
}

internal data class GitHubApkTrustSignal(
    val level: GitHubDecisionLevel,
    val reasons: List<GitHubApkTrustReason>
)

internal fun buildGitHubRepositoryHealth(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    nowMillis: Long = System.currentTimeMillis()
): GitHubRepositoryHealth {
    var score = 82
    val reasons = mutableListOf<GitHubRepositoryHealthReason>()

    if (state.failed || state.message.isNotBlank() && state.hasUpdate == null) {
        score -= 28
        reasons += GitHubRepositoryHealthReason.CheckFailed
    }
    if (item.packageName.isBlank()) {
        score -= 12
        reasons += GitHubRepositoryHealthReason.MissingPackageName
    }
    if (!state.hasStableRelease) {
        score -= 18
        reasons += GitHubRepositoryHealthReason.MissingStableRelease
    }
    if (state.localVersion.isBlank() || state.localVersionCode < 0L) {
        score -= 10
        reasons += GitHubRepositoryHealthReason.LocalMissing
    }
    if (state.hasUpdate == true) {
        score += 7
        reasons += GitHubRepositoryHealthReason.UpdateAvailable
    }
    if (state.recommendsPreRelease) {
        score -= 6
        reasons += GitHubRepositoryHealthReason.PreReleaseRecommended
    }
    if (state.hasStableRelease && state.latestStableRawTag.isNotBlank()) {
        score += 6
        reasons += GitHubRepositoryHealthReason.StableDetected
    }
    val latestUpdateMillis =
        maxOf(state.latestStableUpdatedAtMillis, state.latestPreUpdatedAtMillis)
    if (latestUpdateMillis > 0L && nowMillis - latestUpdateMillis <= FRESH_RELEASE_WINDOW_MS) {
        score += 5
        reasons += GitHubRepositoryHealthReason.FreshRelease
    }

    val normalizedScore = score.coerceIn(0, 100)
    val level = when {
        normalizedScore >= 78 -> GitHubDecisionLevel.Good
        normalizedScore >= 55 -> GitHubDecisionLevel.Review
        else -> GitHubDecisionLevel.Risk
    }
    return GitHubRepositoryHealth(
        score = normalizedScore,
        level = level,
        reasons = reasons.distinct().take(4)
    )
}

internal fun buildGitHubApkTrustSignal(
    asset: GitHubReleaseAssetFile,
    supportedAbis: List<String>
): GitHubApkTrustSignal {
    val lowerName = asset.name.lowercase()
    val extension = assetFileExtensionLabel(asset.name).orEmpty()
    val reasons = mutableListOf<GitHubApkTrustReason>()
    var level = GitHubDecisionLevel.Good

    when {
        extension in setOf("zip", "tar", "gz", "tgz") || "source" in lowerName -> {
            level = GitHubDecisionLevel.Risk
            reasons += GitHubApkTrustReason.SourceArchive
        }

        extension != "apk" && extension != "apks" && extension != "xapk" -> {
            level = GitHubDecisionLevel.Review
            reasons += GitHubApkTrustReason.UnknownFormat
        }

        else -> reasons += GitHubApkTrustReason.ApkLike
    }

    if (!assetLikelyCompatibleWithDevice(asset.name, supportedAbis)) {
        level = GitHubDecisionLevel.Risk
        reasons += GitHubApkTrustReason.IncompatibleAbi
    } else if (assetIsPreferredForDevice(asset.name, supportedAbis)) {
        reasons += GitHubApkTrustReason.PreferredAbi
    } else if ("universal" in lowerName || "fat" in lowerName) {
        reasons += GitHubApkTrustReason.UniversalAsset
    }

    if ("unsigned" in lowerName) {
        level = GitHubDecisionLevel.Risk
        reasons += GitHubApkTrustReason.UnsignedBuild
    }
    if (listOf("debug", "snapshot", "dev", "canary").any { it in lowerName }) {
        if (level == GitHubDecisionLevel.Good) {
            level = GitHubDecisionLevel.Review
        }
        reasons += GitHubApkTrustReason.DebugBuild
    }

    return GitHubApkTrustSignal(
        level = level,
        reasons = reasons.distinct().sortedBy { it.priority }.take(3)
    )
}

internal fun buildGitHubReleaseNotesLines(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    assetBundle: GitHubReleaseAssetBundle?,
    expanded: Boolean
): List<String> {
    val releaseNotesBodyLines = assetBundle
        ?.releaseNotesBody
        .orEmpty()
        .toReleaseNotesPreviewLines()
    if (releaseNotesBodyLines.isNotEmpty()) {
        return releaseNotesBodyLines.take(
            if (expanded) RELEASE_NOTES_EXPANDED_LINE_LIMIT else RELEASE_NOTES_COMPACT_LINE_LIMIT
        )
    }
    val lines = buildList {
        assetBundle?.releaseName?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        state.releaseHint.trim().takeIf { it.isNotBlank() }?.let(::add)
        state.preReleaseInfo.trim().takeIf { state.showPreReleaseInfo && it.isNotBlank() }
            ?.let(::add)
        state.latestStableRawTag.trim().takeIf { it.isNotBlank() }
            ?.let { add("${item.owner}/${item.repo} stable $it") }
        state.latestPreRawTag.trim().takeIf { it.isNotBlank() }
            ?.let { add("${item.owner}/${item.repo} pre $it") }
        assetBundle?.assets
            ?.take(3)
            ?.map { assetDisplayName(it.name) }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(" / ")
            ?.let(::add)
    }.distinct()
    return lines.take(
        if (expanded) RELEASE_NOTES_EXPANDED_LINE_LIMIT else RELEASE_NOTES_COMPACT_LINE_LIMIT
    )
}

internal fun buildGitHubReleaseNotesDetailLines(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    assetBundle: GitHubReleaseAssetBundle?
): List<String> {
    val releaseNotesBodyLines = assetBundle
        ?.releaseNotesBody
        .orEmpty()
        .toReleaseNotesPreviewLines()
    if (releaseNotesBodyLines.isNotEmpty()) {
        return releaseNotesBodyLines.take(RELEASE_NOTES_DETAIL_LINE_LIMIT)
    }
    return buildGitHubReleaseNotesLines(
        item = item,
        state = state,
        assetBundle = assetBundle,
        expanded = true
    )
}

internal fun buildGitHubRepositoryHealthImpactLines(
    health: GitHubRepositoryHealth
): List<Pair<GitHubRepositoryHealthReason, Int>> {
    return health.reasons.map { reason ->
        reason to when (reason) {
            GitHubRepositoryHealthReason.UpdateAvailable -> 7
            GitHubRepositoryHealthReason.PreReleaseRecommended -> -6
            GitHubRepositoryHealthReason.CheckFailed -> -28
            GitHubRepositoryHealthReason.MissingPackageName -> -12
            GitHubRepositoryHealthReason.MissingStableRelease -> -18
            GitHubRepositoryHealthReason.LocalMissing -> -10
            GitHubRepositoryHealthReason.StableDetected -> 6
            GitHubRepositoryHealthReason.FreshRelease -> 5
        }
    }
}

private const val FRESH_RELEASE_WINDOW_MS = 1000L * 60L * 60L * 24L * 14L
private const val RELEASE_NOTES_COMPACT_LINE_LIMIT = 2
private const val RELEASE_NOTES_EXPANDED_LINE_LIMIT = 7
private const val RELEASE_NOTES_DETAIL_LINE_LIMIT = 30
private const val RELEASE_NOTES_LINE_MAX_CHARS = 124

private fun String.toReleaseNotesPreviewLines(): List<String> {
    val markdownLines = parseAppMarkdownBlocks(
        markdown = this,
        preserveLineBreaks = true
    ).asSequence()
        .flatMap { block ->
            when (block) {
                is AppMarkdownBlock.Heading -> sequenceOf(block.text)
                is AppMarkdownBlock.Paragraph -> block.text.lines().asSequence()
                is AppMarkdownBlock.Bullet -> sequenceOf(block.text)
                is AppMarkdownBlock.Ordered -> sequenceOf(block.text)
                is AppMarkdownBlock.Code -> emptySequence()
            }
        }
        .toList()

    val sourceLines = markdownLines.ifEmpty { lines() }
    return sourceLines
        .asSequence()
        .map { line ->
            line.trim()
                .removePrefix("#")
                .removePrefix("-")
                .removePrefix("*")
                .removePrefix("•")
                .trim()
        }
        .map { line ->
            line.replace(Regex("""\[(.+?)]\((.+?)\)"""), "$1")
                .replace(Regex("""[`*_>]+"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }
        .filter { it.length >= 3 }
        .filterNot { line ->
            line.equals("changelog", ignoreCase = true) ||
                    line.equals("release notes", ignoreCase = true) ||
                    line.equals("what's changed", ignoreCase = true) ||
                    line.equals("whats changed", ignoreCase = true)
        }
        .map { line ->
            if (line.length > RELEASE_NOTES_LINE_MAX_CHARS) {
                line.take(RELEASE_NOTES_LINE_MAX_CHARS).trimEnd() + "..."
            } else {
                line
            }
        }
        .distinct()
        .toList()
}

private val GitHubApkTrustReason.priority: Int
    get() = when (this) {
        GitHubApkTrustReason.IncompatibleAbi -> 0
        GitHubApkTrustReason.UnsignedBuild -> 1
        GitHubApkTrustReason.DebugBuild -> 2
        GitHubApkTrustReason.SourceArchive -> 3
        GitHubApkTrustReason.UnknownFormat -> 4
        GitHubApkTrustReason.PreferredAbi -> 5
        GitHubApkTrustReason.UniversalAsset -> 6
        GitHubApkTrustReason.ApkLike -> 7
    }
