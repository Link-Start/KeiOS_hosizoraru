package os.kei.ui.page.main.github

import os.kei.core.install.LocalApkArchiveInfo
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.domain.ApkTrustEvaluationInput
import os.kei.feature.github.domain.ApkTrustEvaluator
import os.kei.feature.github.domain.GitHubRepositoryHealthEvaluator
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.GitHubRepositoryHealthInput
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.asset.assetDisplayName
import os.kei.ui.page.main.widget.markdown.AppMarkdownBlock
import os.kei.ui.page.main.widget.markdown.parseAppMarkdownBlocks
import os.kei.feature.github.model.GitHubApkTrustReason as ModelGitHubApkTrustReason
import os.kei.feature.github.model.GitHubApkTrustSignal as ModelGitHubApkTrustSignal
import os.kei.feature.github.model.GitHubDecisionLevel as ModelGitHubDecisionLevel
import os.kei.feature.github.model.GitHubRepositoryHealth as ModelGitHubRepositoryHealth
import os.kei.feature.github.model.GitHubRepositoryHealthReason as ModelGitHubRepositoryHealthReason

internal typealias GitHubDecisionLevel = ModelGitHubDecisionLevel
internal typealias GitHubApkTrustReason = ModelGitHubApkTrustReason
internal typealias GitHubApkTrustSignal = ModelGitHubApkTrustSignal
internal typealias GitHubRepositoryHealth = ModelGitHubRepositoryHealth
internal typealias GitHubRepositoryHealthReason = ModelGitHubRepositoryHealthReason

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
    supportedAbis: List<String>,
    manifestInfo: GitHubApkManifestInfo? = null,
    installedInfo: GitHubInstalledPackageInfo? = null,
    expectedPackageName: String = "",
    localArchiveInfo: LocalApkArchiveInfo? = null
): GitHubApkTrustSignal {
    return ApkTrustEvaluator.evaluate(
        ApkTrustEvaluationInput(
            asset = asset,
            supportedAbis = supportedAbis,
            manifestInfo = manifestInfo,
            installedInfo = installedInfo,
            expectedPackageName = expectedPackageName,
            localArchiveInfo = localArchiveInfo
        )
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
                is AppMarkdownBlock.Task -> sequenceOf(block.text)
                is AppMarkdownBlock.Ordered -> sequenceOf(block.text)
                is AppMarkdownBlock.Quote -> sequenceOf(block.text)
                is AppMarkdownBlock.TableRow -> block.cells.asSequence()
                AppMarkdownBlock.HorizontalRule -> emptySequence()
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
            line.replace(markdownLinkRegex, "$1")
                .replace(markdownMarkerRegex, "")
                .replace(whitespaceRegex, " ")
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

private val markdownLinkRegex = Regex("""\[(.+?)]\((.+?)\)""")
private val markdownMarkerRegex = Regex("""[`*_>]+""")
private val whitespaceRegex = Regex("""\s+""")
