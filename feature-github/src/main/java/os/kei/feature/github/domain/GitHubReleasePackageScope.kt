package os.kei.feature.github.domain

import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.engine.release.GitHubReleaseCandidateRanker
import os.kei.feature.github.engine.release.GitHubReleaseSelector
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubRejectedRelease
import os.kei.feature.github.model.GitHubReleaseRejection
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.toReleaseVersionSignals
import java.util.Locale

/**
 * A shared repository can publish independent apps/plugins with independent version numbers.
 * Resolve product identity before ranking those numbers, without changing repository-wide caches.
 * Tag prefixes only group releases; an inspected APK is what establishes a group's package.
 */
internal object GitHubReleasePackageScope {
    private const val MAX_FAMILIES = 8
    private val versionStart = Regex("(?:^|[-_/])v?\\d+\\.\\d+(?:\\.\\d+)*", RegexOption.IGNORE_CASE)

    suspend fun resolve(
        item: GitHubTrackedApp,
        snapshot: GitHubRepositoryReleaseSnapshot,
        lookupConfig: GitHubLookupConfig,
        resolver: GitHubPreciseApkVersionResolver,
    ): GitHubRepositoryReleaseSnapshot {
        if (item.packageName.isBlank()) return snapshot
        val candidateEntries = snapshot.feed.entries.toMutableList()
        if (snapshot.hasStableRelease && candidateEntries.none { it.tag == snapshot.latestStable.rawTag }) {
            val stable = snapshot.latestStable
            candidateEntries += GitHubAtomReleaseEntry(
                tag = stable.rawTag, title = stable.rawName, link = stable.link,
                updatedAtMillis = stable.updatedAtMillis, versionCandidates = stable.versionCandidates,
                channel = stable.channel, isLikelyPreRelease = false,
            )
        }
        val families = candidateEntries.mapNotNull { entry ->
            familyOf(entry.tag)?.let { it to entry }
        }.groupBy({ it.first }, { it.second })
        // Ordinary single-product histories keep their existing lightweight lookup path.
        if (families.size < 2) return snapshot
        val entrySource = snapshot.latestStable.source.takeUnless {
            it == GitHubReleaseSignalSource.LatestRedirect
        } ?: GitHubReleaseSignalSource.AtomFallback
        val selectedFamilies = listOfNotNull(
            familyOf(snapshot.latestStable.rawTag),
            snapshot.latestPreRelease?.rawTag?.let(::familyOf),
        ).toSet()
        val targets = families.entries.sortedBy { if (it.key in selectedFamilies) 0 else 1 }
            .take(MAX_FAMILIES)
        val results = GitHubExecution.mapOrderedBounded(targets, maxConcurrency = 2) { (family, entries) ->
            val representative = entries.firstOrNull {
                snapshot.hasStableRelease && it.tag == snapshot.latestStable.rawTag
            } ?: requireNotNull(GitHubReleaseCandidateRanker.latest(entries))
            val result = resolver.resolveRelease(
                GitHubPreciseApkVersionRequest(
                    owner = item.owner,
                    repo = item.repo,
                    release = representative.toReleaseVersionSignals(entrySource),
                    packageName = item.packageName,
                    lookupConfig = lookupConfig,
                ),
            )
            Triple(family, representative, result)
        }
        val excludedFamilies = results.filter {
            it.third.exceptionOrNull() is GitHubApkPackageMismatchException
        }.map { it.first }.toSet()
        val confirmedPreviewTags = results.filter { it.third.getOrNull()?.isPreRelease == true }
            .map { it.second.tag }.toMutableSet()
        val checkedTags = results.map { it.second.tag }.toMutableSet()
        val (excludedEntries, retainedEntries) = snapshot.feed.entries.partition { entry ->
            familyOf(entry.tag) in excludedFamilies
        }
        val excludedTags = excludedEntries.map { it.tag }.toMutableSet()
        fun GitHubAtomReleaseEntry.withConfirmedLane(): GitHubAtomReleaseEntry =
            if (tag in confirmedPreviewTags && !isLikelyPreRelease) {
                copy(isLikelyPreRelease = true, channel = if (channel.isPreRelease) channel else GitHubReleaseChannel.PREVIEW)
            } else this
        var retained = retainedEntries.map { it.withConfirmedLane() }
        val retainedForgeStable = snapshot.latestStable.takeIf {
            snapshot.hasStableRelease &&
                snapshot.selection?.stableCameFromForgeLatest == true &&
                familyOf(it.rawTag) !in excludedFamilies
        }
        fun scopedSnapshot(): GitHubRepositoryReleaseSnapshot {
            if (retained == snapshot.feed.entries &&
                familyOf(snapshot.latestStable.rawTag) !in excludedFamilies &&
                snapshot.latestStable.rawTag !in excludedTags
            ) return snapshot
            val selection = GitHubReleaseSelector.plan(
                entries = retained,
                windowWasFull = snapshot.selection?.windowWasFull ?: false,
                source = entrySource,
            ).resolve(authoritativeStable = retainedForgeStable?.takeIf { it.rawTag !in excludedTags }).let { selection ->
                selection.copy(
                    consideredCount = snapshot.feed.entries.size,
                    rejected = selection.rejected + excludedTags.map {
                        GitHubRejectedRelease(it, GitHubReleaseRejection.OtherPackage)
                    },
                )
            }
            return snapshot.copy(
                feed = snapshot.feed.copy(entries = retained),
                latestStable = selection.stable ?: selection.preRelease ?: snapshot.latestStable,
                hasStableRelease = selection.hasStableRelease,
                latestPreRelease = selection.preRelease,
                selection = selection,
            )
        }
        // Atom carries no lane flag. A numbered plugin may still be marked Pre-release on its
        // page. After moving one such release, confirm the newly selected stable candidate too;
        // otherwise the next older preview would just be mislabelled as stable instead.
        if (entrySource.laneIsInferred) repeat(MAX_FAMILIES) {
            val scoped = scopedSnapshot()
            val candidate = retained.firstOrNull {
                scoped.hasStableRelease && it.tag == scoped.latestStable.rawTag &&
                    it.tag != retainedForgeStable?.rawTag && it.tag !in checkedTags && !it.isLikelyPreRelease
            } ?: return scoped
            checkedTags += candidate.tag
            val result = resolver.resolveRelease(GitHubPreciseApkVersionRequest(
                owner = item.owner, repo = item.repo,
                release = candidate.toReleaseVersionSignals(entrySource),
                packageName = item.packageName, lookupConfig = lookupConfig,
            ))
            when {
                result.getOrNull()?.isPreRelease == true -> {
                    confirmedPreviewTags += candidate.tag
                    retained = retained.map { it.withConfirmedLane() }
                }
                result.exceptionOrNull() is GitHubApkPackageMismatchException -> {
                    excludedTags += candidate.tag
                    retained = retained.filter { it.tag != candidate.tag }
                }
                else -> return scoped
            }
        }
        return scopedSnapshot()
    }

    private fun familyOf(tag: String): String? = versionStart.find(tag.trim())?.let { match ->
        tag.trim().take(match.range.first).trimEnd('-', '_', '/').lowercase(Locale.ROOT)
    }
}
