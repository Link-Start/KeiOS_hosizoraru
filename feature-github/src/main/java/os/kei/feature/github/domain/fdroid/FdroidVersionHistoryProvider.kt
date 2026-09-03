package os.kei.feature.github.domain.fdroid

import os.kei.feature.github.data.remote.fdroid.FdroidPackageApiClient
import os.kei.feature.github.data.remote.fdroid.FdroidPackagePageClient
import os.kei.feature.github.data.remote.fdroid.FdroidPackageSnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidRepositoryIndexClient
import os.kei.feature.github.data.remote.fdroid.fdroidPackagePageUrl
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.buildFdroidRepositoryTrackIdentity

/**
 * The richest history one cheap request can get for a package.
 *
 * Three sources, cheapest-useful first, because no single one serves every repository.
 *
 * The reason there is a choice at all: the update check only ever needs the newest build or two, so the
 * thin package API suits it — but a *history* needs what each build **is**, and on f-droid.org that
 * endpoint publishes a version name, a version code, and nothing further. No file, no size, no date, no
 * ABIs.
 *
 * 1. **The package page**, for a repository whose page layout this app knows. 37 KB on f-droid.org, and
 *    it carries the whole set — see `FdroidPackagePageParser`.
 * 2. **`index-v2.json`, if it is small.** The richest source there is: hashes, signers, release notes,
 *    anti-features, release channels, every build. Also 58 MB on f-droid.org and a few on a third-party
 *    repository, so it is taken under a byte budget rather than from a per-host list — a repository whose
 *    index fits is read from it, and one whose index does not spends the budget and no more. This is what
 *    serves IzzyOnDroid and the other third-party indexes, which publish no page this parser knows but do
 *    publish a modest index.
 * 3. **The package API**, when neither answered. A thin history is still a history, and the page above it
 *    says what is missing.
 *
 * Deliberately not wired into the refresh path. What the checker fetches decides which builds existing
 * tracks are notified about and what the sidecar stores, and that is a tracking change rather than a
 * page. This provider serves the version history only.
 */
class FdroidVersionHistoryProvider(
    private val pageSource: FdroidPackageDetailSource = FdroidPackagePageSource(),
    private val indexSource: FdroidPackageDetailSource = FdroidIndexPackageSource(),
    private val apiSource: FdroidPackageDetailSource = FdroidPackageApiSource(),
) : FdroidPackageSnapshotProvider {
    /**
     * The sources that answer in a request or two, for a page that has to appear now.
     *
     * The page when the repository has one this app reads, and the thin API otherwise. Both are small
     * enough that waiting on them is not waiting.
     */
    suspend fun loadQuick(
        item: GitHubTrackedApp,
        forceRefresh: Boolean,
    ): Result<FdroidPackageSnapshot> {
        val identity = item.identityOrFailure().getOrElse { error -> return Result.failure(error) }
        val page = pageSource.load(identity.normalizedRepoUrl, identity.packageName, forceRefresh)
        if (page.isSuccess) return page.withSuggestionFrom(identity.normalizedRepoUrl, identity.packageName)
        return apiSource.load(identity.normalizedRepoUrl, identity.packageName, forceRefresh)
    }

    /**
     * The repository's index, which is worth its bytes only when the cheap sources left nothing.
     *
     * Megabytes rather than kilobytes — IzzyOnDroid's is fourteen and took half a minute on a first
     * load — so it is fetched *after* the history is already on screen rather than instead of it, and only
     * when [needsRicherSource] says the quick answer has no files in it. A repository with a page needs
     * none of this; one read through the thin API needs all of it, since that endpoint publishes version
     * numbers and nothing else.
     */
    suspend fun loadRich(
        item: GitHubTrackedApp,
        forceRefresh: Boolean,
    ): Result<FdroidPackageSnapshot> {
        val identity = item.identityOrFailure().getOrElse { error -> return Result.failure(error) }
        return indexSource
            .load(identity.normalizedRepoUrl, identity.packageName, forceRefresh)
            .withSuggestionFrom(identity.normalizedRepoUrl, identity.packageName)
    }

    override suspend fun loadPackageSnapshot(
        item: GitHubTrackedApp,
        forceRefresh: Boolean,
    ): Result<FdroidPackageSnapshot> {
        val quick = loadQuick(item, forceRefresh)
        val snapshot = quick.getOrNull()
        if (snapshot != null && !needsRicherSource(snapshot)) return quick
        val rich = loadRich(item, forceRefresh)
        return if (rich.isSuccess) rich else quick
    }

    private fun GitHubTrackedApp.identityOrFailure() =
        runCatching {
            buildFdroidRepositoryTrackIdentity(repoUrl, packageName)
                ?: error("invalid F-Droid repository URL or package")
        }

    /**
     * Fills in the repository's own recommendation when the richer source did not state one.
     *
     * `index-v2` has no `suggestedVersionCode` field at all and a page may not mark a build, while the
     * thin package API always answers with one. It drives the track's default selection mode, so leaving
     * it null would move which build this page calls recommended — and the request is cheap.
     */
    private suspend fun Result<FdroidPackageSnapshot>.withSuggestionFrom(
        repoUrl: String,
        packageName: String,
    ): Result<FdroidPackageSnapshot> {
        val snapshot = getOrNull() ?: return this
        if (snapshot.suggestedVersionCode != null) return this
        val suggested = apiSource
            .load(repoUrl, packageName, forceRefresh = false)
            .getOrNull()
            ?.suggestedVersionCode
            ?: return this
        return Result.success(snapshot.copy(suggestedVersionCode = suggested))
    }
}

/** One way of getting a package's builds, so the order they are tried in can be tested. */
fun interface FdroidPackageDetailSource {
    suspend fun load(
        repoUrl: String,
        packageName: String,
        forceRefresh: Boolean,
    ): Result<FdroidPackageSnapshot>
}

/** Declines rather than guessing for a repository whose page layout is unknown. */
class FdroidPackagePageSource(
    private val client: FdroidPackagePageClient = FdroidPackagePageClient(),
) : FdroidPackageDetailSource {
    override suspend fun load(
        repoUrl: String,
        packageName: String,
        forceRefresh: Boolean,
    ): Result<FdroidPackageSnapshot> {
        if (fdroidPackagePageUrl(repoUrl, packageName) == null) {
            return Result.failure(IllegalStateException("no known package page for $repoUrl"))
        }
        return client.fetchPackagePage(repoBaseUrl = repoUrl, packageName = packageName)
    }
}

/**
 * The one package, out of an index small enough to be worth streaming for it.
 *
 * The index has its own 12-hour cache keyed by the set of packages asked for, so this does not share the
 * refresh batch's entry — a repository tracked with several packages downloads its index once for the
 * batch and once for this, then revalidates by ETag. Worth the duplicate: what comes back is the only
 * source with hashes, signers and release notes in it.
 */
class FdroidIndexPackageSource(
    private val client: FdroidRepositoryIndexClient = FdroidRepositoryIndexClient(),
    private val trackedPackages: FdroidTrackedPackageDirectory = FdroidTrackStorePackageDirectory,
    private val maxIndexBytes: Long = DEFAULT_MAX_HISTORY_INDEX_BYTES,
) : FdroidPackageDetailSource {
    override suspend fun load(
        repoUrl: String,
        packageName: String,
        forceRefresh: Boolean,
    ): Result<FdroidPackageSnapshot> =
        client
            .fetchIndexV2Packages(
                repoBaseUrl = repoUrl,
                // Every package tracked in this repository, not just this one. The index cache is keyed by
                // the set asked for, so asking for the same set the refresh batch asks for means the two
                // share one entry and one download instead of pulling the same megabytes twice. See
                // fdroidTrackedPackagesIn.
                packageNames = trackedPackages.packagesIn(repoUrl) + packageName,
                forceRefresh = forceRefresh,
                maxIndexBytes = maxIndexBytes,
            ).mapCatching { repository ->
                val snapshot = repository.packageSnapshot(packageName)
                    ?: error("F-Droid index-v2 did not list $packageName")
                check(snapshot.versions.isNotEmpty()) {
                    "F-Droid index-v2 listed no versions for $packageName"
                }
                snapshot
            }
}

class FdroidPackageApiSource(
    private val client: FdroidPackageApiClient = FdroidPackageApiClient(),
) : FdroidPackageDetailSource {
    override suspend fun load(
        repoUrl: String,
        packageName: String,
        forceRefresh: Boolean,
    ): Result<FdroidPackageSnapshot> =
        client.fetchPackage(repoBaseUrl = repoUrl, packageName = packageName)
}

/**
 * Whether a history arrived without any file details, so the repository's index is worth fetching.
 *
 * The one question that decides whether megabytes get spent. A page-sourced history already names every
 * APK; a history from `/api/v1/packages` names none, because that endpoint publishes version numbers and
 * nothing else — no file, no size, no hash.
 */
fun needsRicherSource(snapshot: FdroidPackageSnapshot): Boolean =
    snapshot.versions.none { version ->
        version.apkPath.isNotBlank() || version.apkName.isNotBlank()
    }

/**
 * Big enough for a third-party repository's whole index, far too small for f-droid.org's.
 *
 * Measured rather than guessed: f-droid.org publishes ~58 MB and IzzyOnDroid ~14 MB. Sixteen takes the
 * latter and every repository of that order, and stops well short of the former — which does not need it
 * anyway, having a package page this app reads instead.
 */
private const val DEFAULT_MAX_HISTORY_INDEX_BYTES: Long = 16L * 1024L * 1024L
