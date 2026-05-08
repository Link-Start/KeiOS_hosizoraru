package os.kei.ui.page.main.github

import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.domain.GitHubRepositoryHealthEvaluator
import os.kei.feature.github.model.GitHubRepositoryHealthInput
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.asset.assetDisplayName
import os.kei.ui.page.main.github.asset.assetFileExtensionLabel
import os.kei.ui.page.main.github.asset.assetIsPreferredForDevice
import os.kei.ui.page.main.github.asset.assetLikelyCompatibleWithDevice
import os.kei.ui.page.main.widget.markdown.AppMarkdownBlock
import os.kei.ui.page.main.widget.markdown.parseAppMarkdownBlocks
import os.kei.feature.github.model.GitHubDecisionLevel as ModelGitHubDecisionLevel
import os.kei.feature.github.model.GitHubRepositoryHealth as ModelGitHubRepositoryHealth
import os.kei.feature.github.model.GitHubRepositoryHealthReason as ModelGitHubRepositoryHealthReason

internal typealias GitHubDecisionLevel = ModelGitHubDecisionLevel
internal typealias GitHubRepositoryHealth = ModelGitHubRepositoryHealth
internal typealias GitHubRepositoryHealthReason = ModelGitHubRepositoryHealthReason

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
    return GitHubRepositoryHealthEvaluator.evaluate(
        input = GitHubRepositoryHealthInput(
            packageName = item.packageName,
            localVersion = state.localVersion,
            localVersionCode = state.localVersionCode,
            checkFailed = state.failed || state.message.isNotBlank() && state.hasUpdate == null,
            hasStableRelease = state.hasStableRelease,
            hasUpdate = state.hasUpdate,
            recommendsPreRelease = state.recommendsPreRelease,
            latestStableRawTag = state.latestStableRawTag,
            latestStableUpdatedAtMillis = state.latestStableUpdatedAtMillis,
            latestPreUpdatedAtMillis = state.latestPreUpdatedAtMillis,
            repositoryArchived = item.repositoryArchived || state.repositoryArchived,
            repositoryFork = item.repositoryFork || state.repositoryFork,
            repositoryPushedAtMillis = state.repositoryPushedAtMillis,
            upstreamFullName = state.upstreamFullName,
            upstreamArchived = state.upstreamArchived,
            upstreamPushedAtMillis = state.upstreamPushedAtMillis,
            profile = state.repositoryProfile
        ),
        nowMillis = nowMillis
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
        reason to GitHubRepositoryHealthEvaluator.impactFor(reason)
    }
}
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
