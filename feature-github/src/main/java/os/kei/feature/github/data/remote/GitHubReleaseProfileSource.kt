package os.kei.feature.github.data.remote

import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubRepositoryReleasesProfile

object GitHubReleaseProfileSource {
    fun build(
        snapshot: GitHubRepositoryReleaseSnapshot,
        fetchedAtMillis: Long
    ): GitHubRepositoryReleasesProfile {
        val stable = snapshot.latestStable.takeIf { snapshot.hasStableRelease }
        val pre = snapshot.latestPreRelease
        return GitHubRepositoryReleasesProfile(
            releaseCount = intField(
                snapshot.feed.entries.size,
                snapshot.releaseProfileSource(),
                fetchedAtMillis
            ),
            hasStableRelease = booleanField(
                snapshot.hasStableRelease,
                snapshot.releaseProfileSource(),
                fetchedAtMillis
            ),
            latestStableTag = stringField(
                stable?.rawTag.orEmpty(),
                stable.releaseProfileSource(snapshot),
                fetchedAtMillis
            ),
            latestStableName = stringField(
                stable?.rawName.orEmpty(),
                stable.releaseProfileSource(snapshot),
                fetchedAtMillis
            ),
            latestStablePublishedAtMillis = longField(
                stable?.updatedAtMillis ?: -1L,
                stable.releaseProfileSource(snapshot),
                fetchedAtMillis
            ),
            latestStableAuthor = stringField(
                stable?.authorName.orEmpty(),
                stable.releaseProfileSource(snapshot),
                fetchedAtMillis
            ),
            latestPreReleaseTag = stringField(
                pre?.rawTag.orEmpty(),
                pre.releaseProfileSource(snapshot),
                fetchedAtMillis
            ),
            latestPreReleaseName = stringField(
                pre?.rawName.orEmpty(),
                pre.releaseProfileSource(snapshot),
                fetchedAtMillis
            ),
            latestPreReleasePublishedAtMillis = longField(
                pre?.updatedAtMillis ?: -1L,
                pre.releaseProfileSource(snapshot),
                fetchedAtMillis
            ),
            latestPreReleaseAuthor = stringField(
                pre?.authorName.orEmpty(),
                pre.releaseProfileSource(snapshot),
                fetchedAtMillis
            )
        )
    }
}

fun GitHubRepositoryReleaseSnapshot.releaseProfileSource(): GitHubRepositoryProfileSource {
    return when (strategyId) {
        GitHubLookupStrategyOption.GitHubApiToken.storageId -> GitHubRepositoryProfileSource.GitHubApiReleases
        else -> GitHubRepositoryProfileSource.AtomReleaseFeed
    }
}

fun GitHubRepositoryReleaseSnapshot.releaseProfileSources(): Set<GitHubRepositoryProfileSource> {
    return buildSet {
        add(releaseProfileSource())
        latestStable.releaseProfileSource(this@releaseProfileSources).let(::add)
        latestPreRelease.releaseProfileSource(this@releaseProfileSources).let(::add)
    }
}

fun GitHubReleaseVersionSignals?.releaseProfileSource(
    snapshot: GitHubRepositoryReleaseSnapshot
): GitHubRepositoryProfileSource {
    return when (this?.source) {
        GitHubReleaseSignalSource.GitHubApi -> GitHubRepositoryProfileSource.GitHubApiReleases
        GitHubReleaseSignalSource.LatestRedirect -> GitHubRepositoryProfileSource.HtmlLatestReleaseRedirect
        GitHubReleaseSignalSource.AtomEntry,
        GitHubReleaseSignalSource.AtomFallback -> GitHubRepositoryProfileSource.AtomReleaseFeed

        null -> snapshot.releaseProfileSource()
    }
}
