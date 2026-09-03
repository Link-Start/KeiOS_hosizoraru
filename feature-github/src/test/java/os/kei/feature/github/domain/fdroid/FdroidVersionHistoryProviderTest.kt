package os.kei.feature.github.domain.fdroid

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import os.kei.feature.github.data.remote.fdroid.FdroidPackageSnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidVersionSnapshot
import os.kei.feature.github.model.FdroidTrackedAppConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedSourceMode

/**
 * The order the three sources are tried in, and what happens when one of them declines.
 *
 * Ordering is the whole design here and none of it is type-checked: page, then a small index, then the
 * thin package API. Getting it wrong is invisible — every path returns a plausible history, just a poorer
 * one, or a 58 MB download nobody asked for.
 */
class FdroidVersionHistoryProviderTest {
    @Test
    fun `the page wins outright, so no index is streamed for a repository that publishes one`() = runTest {
        val index = RecordingSource(snapshot(20L))
        val api = RecordingSource(snapshot(20L))
        val provider =
            FdroidVersionHistoryProvider(
                pageSource = RecordingSource(snapshot(30L, suggested = 30L)),
                indexSource = index,
                apiSource = api,
            )

        val result = provider.loadPackageSnapshot(track(), forceRefresh = false).getOrThrow()

        assertEquals(30L, result.versions.single().versionCode)
        assertEquals(0, index.calls, "the index must not be fetched once the page answered")
        assertEquals(0, api.calls, "nor the API, the page having stated a suggestion")
    }

    @Test
    fun `a repository with no page falls to its index rather than to the thin API`() = runTest {
        // IzzyOnDroid and the other third-party indexes: no page layout this app knows, but a modest
        // index that carries hashes, signers and release notes.
        val api = RecordingSource(snapshot(10L))
        val provider =
            FdroidVersionHistoryProvider(
                pageSource = FailingSource(),
                indexSource = RecordingSource(snapshot(20L, suggested = 20L)),
                apiSource = api,
            )

        val result = provider.loadPackageSnapshot(track(), forceRefresh = false).getOrThrow()

        assertEquals(20L, result.versions.single().versionCode)
        assertEquals(0, api.calls)
    }

    @Test
    fun `the thin API is the last resort, not the first choice`() = runTest {
        val provider =
            FdroidVersionHistoryProvider(
                pageSource = FailingSource(),
                indexSource = FailingSource(),
                apiSource = RecordingSource(snapshot(10L)),
            )

        assertEquals(
            10L,
            provider.loadPackageSnapshot(track(), forceRefresh = false).getOrThrow()
                .versions.single().versionCode,
        )
    }

    @Test
    fun `every source failing surfaces the failure rather than an empty history`() = runTest {
        // An empty list would render as "this package has no versions", which is a different and wrong
        // statement from "the repository could not be reached".
        val provider =
            FdroidVersionHistoryProvider(
                pageSource = FailingSource(),
                indexSource = FailingSource(),
                apiSource = FailingSource(),
            )

        assertTrue(provider.loadPackageSnapshot(track(), forceRefresh = false).isFailure)
    }

    @Test
    fun `a richer source with no suggestion of its own borrows the API's`() = runTest {
        // index-v2 has no suggestedVersionCode field at all, and it drives the track's default selection
        // mode -- leaving it null would move which build the page calls recommended.
        val api = RecordingSource(snapshot(20L, suggested = 20L))
        val provider =
            FdroidVersionHistoryProvider(
                pageSource = FailingSource(),
                indexSource = RecordingSource(snapshot(30L, suggested = null)),
                apiSource = api,
            )

        val result = provider.loadPackageSnapshot(track(), forceRefresh = false).getOrThrow()

        assertEquals(20L, result.suggestedVersionCode)
        // The history stays the index's; only the suggestion is borrowed.
        assertEquals(30L, result.versions.single().versionCode)
        assertEquals(1, api.calls)
    }

    @Test
    fun `an API that cannot answer leaves the suggestion unset instead of failing the load`() = runTest {
        val provider =
            FdroidVersionHistoryProvider(
                pageSource = RecordingSource(snapshot(30L, suggested = null)),
                indexSource = FailingSource(),
                apiSource = FailingSource(),
            )

        val result = provider.loadPackageSnapshot(track(), forceRefresh = false).getOrThrow()

        assertEquals(null, result.suggestedVersionCode)
        assertEquals(30L, result.versions.single().versionCode)
    }

    @Test
    fun `a track whose repository URL does not parse never reaches the network`() = runTest {
        val page = RecordingSource(snapshot(10L))
        val provider =
            FdroidVersionHistoryProvider(
                pageSource = page,
                indexSource = RecordingSource(snapshot(10L)),
                apiSource = RecordingSource(snapshot(10L)),
            )

        val result = provider.loadPackageSnapshot(track(repoUrl = "not a url"), forceRefresh = false)

        assertTrue(result.isFailure)
        assertEquals(0, page.calls)
    }

    @Test
    fun `forceRefresh reaches the source rather than being swallowed`() = runTest {
        val page = RecordingSource(snapshot(30L, suggested = 30L))
        val provider =
            FdroidVersionHistoryProvider(
                pageSource = page,
                indexSource = FailingSource(),
                apiSource = FailingSource(),
            )

        provider.loadPackageSnapshot(track(), forceRefresh = true)

        assertEquals(listOf(true), page.forceRefreshes)
    }
}

private class RecordingSource(
    private val snapshot: FdroidPackageSnapshot,
) : FdroidPackageDetailSource {
    var calls = 0
        private set
    val forceRefreshes = mutableListOf<Boolean>()

    override suspend fun load(
        repoUrl: String,
        packageName: String,
        forceRefresh: Boolean,
    ): Result<FdroidPackageSnapshot> {
        calls++
        forceRefreshes += forceRefresh
        return Result.success(snapshot)
    }
}

private class FailingSource : FdroidPackageDetailSource {
    override suspend fun load(
        repoUrl: String,
        packageName: String,
        forceRefresh: Boolean,
    ): Result<FdroidPackageSnapshot> = Result.failure(IllegalStateException("unavailable"))
}

private fun snapshot(
    versionCode: Long,
    suggested: Long? = null,
): FdroidPackageSnapshot =
    FdroidPackageSnapshot(
        repoUrl = "https://f-droid.org/repo",
        packageName = "com.example.app",
        suggestedVersionCode = suggested,
        versions =
            listOf(
                FdroidVersionSnapshot(
                    versionName = "1.$versionCode",
                    versionCode = versionCode,
                    apkName = "app_$versionCode.apk",
                    apkPath = "app_$versionCode.apk",
                    apkSha256 = "",
                    apkSizeBytes = 0L,
                    addedAtMillis = null,
                    minSdk = null,
                    targetSdk = null,
                    nativeAbis = emptyList(),
                    signerSha256 = emptyList(),
                    releaseChannels = emptyList(),
                    whatsNew = "",
                    antiFeatures = emptyList(),
                ),
            ),
    )

private fun track(repoUrl: String = "https://f-droid.org/repo"): GitHubTrackedApp =
    GitHubTrackedApp(
        repoUrl = repoUrl,
        owner = "f-droid.org",
        repo = "repo",
        packageName = "com.example.app",
        appLabel = "Example",
        sourceMode = GitHubTrackedSourceMode.FdroidRepository,
        fdroidConfig = FdroidTrackedAppConfig(),
    )
