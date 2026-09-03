package os.kei.feature.github.domain.fdroid

import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.buildFdroidRepositoryTrackIdentity
import os.kei.feature.github.model.isFdroidRepositoryTrack
import java.util.Locale

/**
 * Every package this app tracks in one F-Droid repository.
 *
 * This exists to make two readers of the same index share one download, and the reason is the cache key.
 * `FdroidRepoCacheRequestKey.packages` is built from the repository *and the set of packages asked for*,
 * so a refresh batch asking for four packages and a version-history page asking for one of them produce
 * different keys — two entries, two downloads of the same file. On IzzyOnDroid that is fourteen megabytes
 * twice; on f-droid.org it would be fifty-eight.
 *
 * So whoever reads an index asks for the same set: everything tracked in that repository. The stream
 * parser keeps only the packages requested either way, so widening the request costs nothing but makes
 * the key stable across callers. Adding or removing a track changes the set and therefore the key, which
 * is correct — there is a new package to find.
 */
fun fdroidTrackedPackagesIn(
    repoUrl: String,
    trackedItems: List<GitHubTrackedApp>,
): Set<String> {
    val target = repoUrl.trim().trimEnd('/').lowercase(Locale.ROOT)
    if (target.isBlank()) return emptySet()
    return trackedItems
        .asSequence()
        .filter { item -> item.isFdroidRepositoryTrack() }
        .mapNotNull { item -> buildFdroidRepositoryTrackIdentity(item.repoUrl, item.packageName) }
        .filter { identity -> identity.normalizedRepoUrl.lowercase(Locale.ROOT) == target }
        .map { identity -> identity.packageName.trim() }
        .filter { name -> name.isNotBlank() }
        .distinctBy { name -> name.lowercase(Locale.ROOT) }
        .toSet()
}

/**
 * The same set, read from the track store.
 *
 * A seam rather than a direct call so the version history can be tested without a device — the store is
 * MMKV-backed and there is no store in a unit test.
 */
fun interface FdroidTrackedPackageDirectory {
    fun packagesIn(repoUrl: String): Set<String>
}

/** Reads the live track list, which is what the refresh batch is iterating anyway. */
object FdroidTrackStorePackageDirectory : FdroidTrackedPackageDirectory {
    override fun packagesIn(repoUrl: String): Set<String> =
        fdroidTrackedPackagesIn(repoUrl = repoUrl, trackedItems = GitHubTrackStore.load())
}
