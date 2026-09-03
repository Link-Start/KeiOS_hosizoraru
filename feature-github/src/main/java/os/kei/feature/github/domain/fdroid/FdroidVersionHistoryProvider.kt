package os.kei.feature.github.domain.fdroid

import os.kei.feature.github.data.remote.fdroid.FdroidPackageApiClient
import os.kei.feature.github.data.remote.fdroid.FdroidPackagePageClient
import os.kei.feature.github.data.remote.fdroid.FdroidPackageSnapshot
import os.kei.feature.github.data.remote.fdroid.fdroidPackagePageUrl
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.buildFdroidRepositoryTrackIdentity

/**
 * The richest history one cheap request can get for a package.
 *
 * The page first, the package API second, and the two combined when each knows something the other does
 * not.
 *
 * The reason there is a choice at all: the update check only ever needs the newest build or two, so the
 * package API suits it — but a *history* page needs what each build is, and on f-droid.org that endpoint
 * publishes a version name, a version code, and nothing further. No file, no size, no date, no ABIs. The
 * package page publishes all of it in a few tens of kilobytes, where the index that also would is 58 MB.
 *
 * Deliberately not wired into the refresh path. What the checker fetches decides which builds existing
 * tracks are notified about and what the sidecar stores, and that is a tracking change rather than a
 * page. This provider serves the version history only.
 */
class FdroidVersionHistoryProvider(
    private val pageClient: FdroidPackagePageClient = FdroidPackagePageClient(),
    private val apiClient: FdroidPackageApiClient = FdroidPackageApiClient(),
) : FdroidPackageSnapshotProvider {
    override suspend fun loadPackageSnapshot(
        item: GitHubTrackedApp,
        forceRefresh: Boolean,
    ): Result<FdroidPackageSnapshot> {
        val identity = buildFdroidRepositoryTrackIdentity(item.repoUrl, item.packageName)
            ?: return Result.failure(IllegalArgumentException("invalid F-Droid repository URL or package"))
        val repoUrl = identity.normalizedRepoUrl
        val packageName = identity.packageName
        val hasKnownPage = fdroidPackagePageUrl(repoUrl, packageName) != null
        if (!hasKnownPage) {
            return apiClient.fetchPackage(repoBaseUrl = repoUrl, packageName = packageName)
        }
        val page = pageClient.fetchPackagePage(repoBaseUrl = repoUrl, packageName = packageName)
        val api = apiClient.fetchPackage(repoBaseUrl = repoUrl, packageName = packageName)
        return when {
            // Both answered: the page's records, and the API's suggestion when the page marked none. The
            // suggestion drives the track's default selection mode, so losing it would move which build
            // this page calls recommended.
            page.isSuccess && api.isSuccess -> {
                val pageSnapshot = page.getOrThrow()
                Result.success(
                    pageSnapshot.copy(
                        suggestedVersionCode = pageSnapshot.suggestedVersionCode
                            ?: api.getOrThrow().suggestedVersionCode,
                    ),
                )
            }

            page.isSuccess -> page
            // The page moved, or the repository is not the layout this parser was written against. The
            // thin history is still a history, and the page above it says what is missing.
            else -> api
        }
    }
}
