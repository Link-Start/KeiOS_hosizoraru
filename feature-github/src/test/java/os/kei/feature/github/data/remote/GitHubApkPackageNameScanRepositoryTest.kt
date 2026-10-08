package os.kei.feature.github.data.remote

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.domain.GitHubScanReleaseTarget
import os.kei.feature.github.model.GitHubAtomFeed
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.toReleaseVersionSignals

class GitHubApkPackageNameScanRepositoryTest {
    private val api = GitHubLookupConfig(selectedStrategy = GitHubLookupStrategyOption.GitHubApiToken)
    private val atom = GitHubLookupConfig(selectedStrategy = GitHubLookupStrategyOption.AtomFeed)

    @Test
    fun `prerelease only repository scans numeric preview only when permitted`() = runBlocking {
        val source = FakeReleases(snapshot(entry("v0.3.1", true)), bundles = mapOf("v0.3.1" to bundle("v0.3.1", true)))
        val repository = GitHubApkPackageNameScanRepository(releases = source)
        assertEquals("This repository has no stable release", repository.loadScanReleaseApkAssets("dingwen07", "hyperos-fcm-fix", api, false).exceptionOrNull()?.message)
        source.calls.clear()
        assertEquals("v0.3.1", repository.loadScanReleaseApkAssets("dingwen07", "hyperos-fcm-fix", api, true).getOrThrow().release.tag)
        assertFalse(source.calls.contains("latest"))
        assertEquals("This repository has no stable release", repository.loadScanReleaseApkAssets("dingwen07", "hyperos-fcm-fix", api, false).exceptionOrNull()?.message)
    }

    @Test
    fun `Atom numeric tag uses release badge and does not leak into stable scan`() = runBlocking {
        val source = FakeReleases(snapshot(entry("v0.3.1", false)), bundles = mapOf("v0.3.1" to bundle("v0.3.1", true)))
        val repository = GitHubApkPackageNameScanRepository(releases = source)
        assertEquals("This repository has no stable release", repository.loadScanReleaseApkAssets("owner", "repo", atom, false).exceptionOrNull()?.message)
        assertEquals("v0.3.1", repository.loadScanReleaseApkAssets("owner", "repo", atom, true).getOrThrow().release.tag)
        assertEquals("This repository has no stable release", repository.loadScanReleaseApkAssets("owner", "repo", atom, false).exceptionOrNull()?.message)
    }

    @Test
    fun `Atom inferred stable preview falls back to actual older stable`() = runBlocking {
        val source = FakeReleases(snapshot(entry("v0.3.1", false), entry("v0.3.0", false)), bundles = mapOf(
            "v0.3.1" to bundle("v0.3.1", true), "v0.3.0" to bundle("v0.3.0", false),
        ))
        assertEquals("v0.3.0", GitHubApkPackageNameScanRepository(releases = source).loadScanReleaseApkAssets("owner", "repo", atom, false).getOrThrow().release.tag)
    }

    @Test
    fun `API stable scan retains latest endpoint fast path`() = runBlocking {
        val source = FakeReleases(snapshot(entry("v1.0.0", false)), latest = bundle("v1.0.0", false))
        val result = GitHubApkPackageNameScanRepository(releases = source).loadScanReleaseApkAssets("owner", "repo", api, false).getOrThrow()
        assertEquals("v1.0.0", result.release.tag)
        assertEquals(listOf("latest"), source.calls)
    }

    @Test
    fun `Atom prerelease looking tag retains authoritative stable fallback`() = runBlocking {
        val source = FakeReleases(snapshot(entry("v1.0.0-beta", true)), latest = bundle("v1.0.0-beta", false))
        val result = GitHubApkPackageNameScanRepository(releases = source).loadScanReleaseApkAssets("owner", "repo", atom, false).getOrThrow()
        assertEquals("v1.0.0-beta", result.release.tag)
        assertEquals(listOf("snapshot:AtomFeed", "latest"), source.calls)
    }

    @Test
    fun `preview preference chooses usable preview over stable`() = runBlocking {
        val source = FakeReleases(snapshot(entry("v1.1.0-rc1", true), entry("v1.0.0", false)), bundles = mapOf(
            "v1.1.0-rc1" to bundle("v1.1.0-rc1", true), "v1.0.0" to bundle("v1.0.0", false),
        ))
        assertEquals("v1.1.0-rc1", GitHubApkPackageNameScanRepository(releases = source).loadScanReleaseApkAssets("owner", "repo", api, true).getOrThrow().release.tag)
    }

    @Test
    fun `empty preview falls back to usable older preview before stable`() = runBlocking {
        val source = FakeReleases(snapshot(entry("v1.2.0-rc1", true), entry("v1.1.0-rc1", true), entry("v1.0.0", false)), bundles = mapOf(
            "v1.2.0-rc1" to bundle("v1.2.0-rc1", true, empty = true),
            "v1.1.0-rc1" to bundle("v1.1.0-rc1", true), "v1.0.0" to bundle("v1.0.0", false),
        ))
        assertEquals("v1.1.0-rc1", GitHubApkPackageNameScanRepository(releases = source).loadScanReleaseApkAssets("owner", "repo", api, true).getOrThrow().release.tag)
    }

    @Test
    fun `empty preview falls back to stable APK and ignores non APK assets`() = runBlocking {
        val source = FakeReleases(snapshot(entry("v1.1.0-rc1", true), entry("v1.0.0", false)), bundles = mapOf(
            "v1.1.0-rc1" to bundle("v1.1.0-rc1", true, empty = true), "v1.0.0" to bundle("v1.0.0", false),
        ))
        val result = GitHubApkPackageNameScanRepository(releases = source).loadScanReleaseApkAssets("owner", "repo", api, true).getOrThrow()
        assertEquals("v1.0.0", result.release.tag)
        assertEquals(listOf("app.apk"), result.assets.map { it.name })
    }

    @Test
    fun `repository with releases but no APK reports usable APK error`() = runBlocking {
        val source = FakeReleases(snapshot(entry("v0.3.1", true)), bundles = mapOf("v0.3.1" to bundle("v0.3.1", true, empty = true)))
        assertEquals("The target release contains no usable APK", GitHubApkPackageNameScanRepository(releases = source).loadScanReleaseApkAssets("owner", "repo", api, true).exceptionOrNull()?.message)
    }

    @Test
    fun `empty public release window reports no usable release`() = runBlocking {
        assertEquals("This repository has no usable release", GitHubApkPackageNameScanRepository(releases = FakeReleases(snapshot())).loadScanReleaseApkAssets("owner", "repo", api, true).exceptionOrNull()?.message)
    }

    @Test
    fun `snapshot transport failure tries alternate strategy`() = runBlocking {
        val source = FakeReleases(snapshot(entry("v0.3.1", true)), bundles = mapOf("v0.3.1" to bundle("v0.3.1", true)))
        source.snapshotFailure = IOException("offline")
        val result = GitHubApkPackageNameScanRepository(releases = source).loadScanReleaseApkAssets("owner", "repo", api, true).getOrThrow()
        assertEquals("v0.3.1", result.release.tag)
        assertTrue(source.calls.contains("snapshot:AtomFeed"))
    }

    @Test
    fun `cancellation during stable endpoint or snapshot does not start fallback`() = runBlocking {
        for (preview in listOf(false, true)) {
            val source = FakeReleases(snapshot(entry("v1.0.0", false)))
            if (preview) source.snapshotFailure = CancellationException("cancelled")
            else source.latestFailure = CancellationException("cancelled")
            var cancelled = false
            try { GitHubApkPackageNameScanRepository(releases = source).loadScanReleaseApkAssets("owner", "repo", api, preview) }
            catch (_: CancellationException) { cancelled = true }
            assertTrue(cancelled)
            assertEquals(1, source.calls.size)
        }
    }

    private fun entry(tag: String, preview: Boolean) = GitHubAtomReleaseEntry(
        tag = tag, title = tag, link = "https://github.com/owner/repo/releases/tag/$tag", isLikelyPreRelease = preview,
    )

    private fun snapshot(vararg entries: GitHubAtomReleaseEntry) = GitHubRepositoryReleaseSnapshot(
        strategyId = "fixture",
        feed = GitHubAtomFeed(entries = entries.toList()),
        latestStable = (entries.firstOrNull { !it.isLikelyPreRelease } ?: entry("", false)).toReleaseVersionSignals(),
        hasStableRelease = entries.any { !it.isLikelyPreRelease },
        latestPreRelease = entries.firstOrNull { it.isLikelyPreRelease }?.toReleaseVersionSignals(),
    )

    private fun bundle(tag: String, preview: Boolean, empty: Boolean = false) = GitHubReleaseAssetBundle(
        releaseName = tag, tagName = tag, htmlUrl = "https://github.com/owner/repo/releases/tag/$tag", isPreRelease = preview,
        assets = if (empty) listOf(asset("source.zip")) else listOf(asset("app.apk"), asset("source.zip")),
    )

    private fun asset(name: String) = GitHubReleaseAssetFile(name = name, downloadUrl = "https://example.com/$name", sizeBytes = 2048, downloadCount = 0)

    private class FakeReleases(
        val snapshot: GitHubRepositoryReleaseSnapshot,
        val bundles: Map<String, GitHubReleaseAssetBundle> = emptyMap(),
        val latest: GitHubReleaseAssetBundle? = null,
    ) : GitHubPackageScanReleaseSource {
        val calls = mutableListOf<String>()
        var snapshotFailure: Throwable? = null
        var latestFailure: Throwable? = null
        override suspend fun loadSnapshot(owner: String, repo: String, config: GitHubLookupConfig): Result<GitHubRepositoryReleaseSnapshot> {
            calls += "snapshot:${config.selectedStrategy}"
            return if (config.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken && snapshotFailure != null) Result.failure(snapshotFailure!!) else Result.success(snapshot)
        }
        override suspend fun fetchLatestStableApkAssets(owner: String, repo: String, config: GitHubLookupConfig): Result<GitHubReleaseAssetBundle> {
            calls += "latest"
            return latest?.let { Result.success(it) } ?: Result.failure(latestFailure ?: IOException("HTTP 404"))
        }
        override suspend fun fetchApkAssets(owner: String, repo: String, release: GitHubScanReleaseTarget, config: GitHubLookupConfig): Result<GitHubReleaseAssetBundle> {
            calls += "assets:${release.tag}"
            return Result.success(requireNotNull(bundles[release.tag]))
        }
    }
}
