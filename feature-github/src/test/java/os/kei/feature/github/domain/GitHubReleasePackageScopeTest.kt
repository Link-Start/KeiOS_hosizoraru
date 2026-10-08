package os.kei.feature.github.domain

import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.engine.release.GitHubReleaseSelector
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubAtomFeed
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubReleaseRejection
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.GitHubVersionCandidateSource
import os.kei.feature.github.model.toReleaseVersionSignals
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GitHubReleasePackageScopeTest {
    @Test
    fun `cancelling apk identity lookup cancels the whole check`() = runBlocking<Unit> {
        val source = object : GitHubPreciseApkVersionSource by source() {
            override suspend fun inspectApk(asset: GitHubReleaseAssetFile, lookupConfig: GitHubLookupConfig): Result<GitHubApkManifestInfo> {
                throw CancellationException("cancelled")
            }
        }
        assertFailsWith<CancellationException> {
            GitHubReleasePackageScope.resolve(item(), snapshot(entries()), GitHubLookupConfig(), GitHubPreciseApkVersionResolver(source))
        }
    }

    @Test
    fun `default metadata lookup finds main preview behind independently versioned plugins`() = runBlocking {
        val source = source()
        val original = snapshot(entries())
        assertEquals("plugin-mieru-v3.38.0-0", original.latestPreRelease?.rawTag)
        val scoped = scope(original, source)

        assertEquals("v2.2.0", scoped.latestStable.rawTag)
        assertEquals("v2.3.0-alpha.1", scoped.latestPreRelease?.rawTag)
        assertTrue(scoped.feed.entries.all { it.tag.startsWith("v") })
        assertEquals("plugin-mieru-v3.38.0-0", original.latestPreRelease?.rawTag)
        assertTrue(scoped.selection!!.rejected.any { it.reason == GitHubReleaseRejection.OtherPackage })
        val check = GitHubReleaseCheckService.evaluateSnapshot(
            item = item(), localVersion = "2.2.0", localVersionCode = 550,
            snapshot = scoped, nowMillis = NOW,
        )
        assertEquals(GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable, check.status)
        assertTrue(check.hasPreReleaseUpdate)
        assertEquals("v2.3.0-alpha.1", check.preRelease?.rawTag)
        assertEquals(null, check.precisePreApkVersion)
    }

    @Test
    fun `plugin can track its own prereleases in the same repository`() = runBlocking {
        val target = item("fr.husi.plugin.shadowquic")
        val scoped = scope(snapshot(entries()), source(), target)
        assertFalse(scoped.hasStableRelease)
        assertEquals("plugin-shadowquic-v0.4.2-0", scoped.latestPreRelease?.rawTag)
        val check = GitHubReleaseCheckService.evaluateSnapshot(
            item = target, localVersion = "0.4.1-0", localVersionCode = 26,
            snapshot = scoped, nowMillis = NOW,
        )
        assertTrue(check.hasPreReleaseUpdate)
        assertEquals(GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable, check.status)
    }

    @Test
    fun `installed numbered plugin is up to date with metadata comparison`() = runBlocking {
        val target = item("fr.husi.plugin.shadowquic")
        val scoped = scope(snapshot(entries()), source(), target)
        val check = GitHubReleaseCheckService.evaluateSnapshot(
            item = target, localVersion = "0.4.2-0", localVersionCode = 27,
            snapshot = scoped, nowMillis = NOW,
        )
        assertEquals(GitHubTrackedReleaseStatus.PreReleaseTracked, check.status)
        assertFalse(check.hasPreReleaseUpdate)
        assertTrue(check.isPreReleaseInstalled)
    }

    @Test
    fun `main alpha superseded by stable remains history and is not an update`() = runBlocking {
        val actualShape = entries().map { entry ->
            if (entry.tag == "v2.3.0-alpha.1") entry("v2.2.0-alpha.1", true) else entry
        }
        val scoped = scope(snapshot(actualShape), source())
        assertEquals("v2.2.0-alpha.1", scoped.latestPreRelease?.rawTag)
        val check = GitHubReleaseCheckService.evaluateSnapshot(
            item = item(), localVersion = "2.2.0", localVersionCode = 550,
            snapshot = scoped, nowMillis = NOW,
        )
        assertFalse(check.hasPreReleaseUpdate)
        assertEquals(GitHubReleaseRejection.SupersededByStable, check.preReleaseRejection)
    }

    @Test
    fun `single product history does not incur extra apk requests`() = runBlocking {
        val source = source()
        val original = snapshot(entries().filter { it.tag.startsWith("v") })
        assertSame(original, scope(original, source))
        assertTrue(source.loadedTags.isEmpty())
    }

    @Test
    fun `repositories tracked without a package retain repository level releases`() = runBlocking {
        val source = source()
        val original = snapshot(entries())
        assertSame(original, scope(original, source, item("")))
        assertTrue(source.loadedTags.isEmpty())
    }

    @Test
    fun `network and partial inspection failures do not prove another package`() = runBlocking {
        val source = source(failedFamily = "plugin-mieru")
        val scoped = scope(snapshot(entries()), source)
        assertTrue(scoped.feed.entries.any { it.tag.startsWith("plugin-mieru") })
        assertTrue(scoped.selection!!.rejected.none {
            it.tag.startsWith("plugin-mieru") && it.reason == GitHubReleaseRejection.OtherPackage
        })
    }

    @Test
    fun `multiple tag spellings for the same application are retained`() = runBlocking {
        val tags = listOf(entry("app-v2.2.0", false), entry("preview-v2.3.0-alpha.1", true))
        val original = snapshot(tags)
        val source = source()
        assertSame(original, scope(original, source))
        assertEquals(setOf("app-v2.2.0", "preview-v2.3.0-alpha.1"), source.loadedTags)
    }

    @Test
    fun `bounded incomplete asset inspection cannot exclude a release family`() = runBlocking {
        val original = snapshot(entries())
        val scoped = scope(original, source(oversizedFamily = "plugin-mieru"))
        assertTrue(scoped.feed.entries.any { it.tag.startsWith("plugin-mieru") })
    }

    @Test
    fun `atom forge stable survives target scoping without polluting source provenance`() = runBlocking {
        val original = snapshot(entries(), GitHubReleaseSignalSource.AtomFallback)
        val authoritative = original.latestStable.copy(source = GitHubReleaseSignalSource.LatestRedirect)
        val selection = GitHubReleaseSelector.plan(
            original.feed.entries, source = GitHubReleaseSignalSource.AtomFallback,
        ).resolve(authoritativeStable = authoritative)
        val scoped = scope(original.copy(latestStable = authoritative, selection = selection), source())
        assertEquals("v2.2.0", scoped.latestStable.rawTag)
        assertEquals(GitHubReleaseSignalSource.LatestRedirect, scoped.latestStable.source)
        assertTrue(scoped.selection!!.stableCameFromForgeLatest)
        assertEquals("v2.3.0-alpha.1", scoped.latestPreRelease?.rawTag)
        assertEquals(GitHubReleaseSignalSource.AtomFallback, scoped.latestPreRelease?.source)
    }

    @Test
    fun `atom numbered plugin releases use page prerelease labels instead of stable guesses`() = runBlocking {
        val feed = entries().map {
            if (it.tag.startsWith("plugin-")) it.copy(isLikelyPreRelease = false, channel = GitHubReleaseChannel.STABLE) else it
        }
        val original = snapshot(feed, GitHubReleaseSignalSource.AtomFallback)
        val stable = entry("v2.2.0", false).toReleaseVersionSignals(GitHubReleaseSignalSource.LatestRedirect)
        val selection = GitHubReleaseSelector.plan(feed, source = GitHubReleaseSignalSource.AtomFallback)
            .resolve(authoritativeStable = stable)
        val scoped = scope(original.copy(latestStable = stable, selection = selection), source(), item("fr.husi.plugin.shadowquic"))
        assertFalse(scoped.hasStableRelease)
        assertEquals("plugin-shadowquic-v0.4.2-0", scoped.latestPreRelease?.rawTag)
        assertTrue(scoped.feed.entries.all { it.isLikelyPreRelease })
    }

    @Test
    fun `forge stable outside the feed window cannot leak into plugin checks`() = runBlocking {
        val feed = entries().filter { it.tag.startsWith("plugin-shadowquic") }
        val outsideStable = entry("v2.2.0", false).toReleaseVersionSignals(GitHubReleaseSignalSource.GitHubApi)
        val selection = GitHubReleaseSelector.plan(feed).resolve(authoritativeStable = outsideStable)
        val original = snapshot(feed).copy(latestStable = outsideStable, hasStableRelease = true, selection = selection)
        val scoped = scope(original, source(), item("fr.husi.plugin.shadowquic"))
        assertFalse(scoped.hasStableRelease)
        assertEquals("plugin-shadowquic-v0.4.2-0", scoped.latestPreRelease?.rawTag)
    }

    private suspend fun scope(
        snapshot: GitHubRepositoryReleaseSnapshot,
        source: Source,
        item: GitHubTrackedApp = item(),
    ) = GitHubReleasePackageScope.resolve(
        item, snapshot, GitHubLookupConfig(preciseApkVersionEnabled = false),
        GitHubPreciseApkVersionResolver(source),
    )

    private fun item(packageName: String = "fr.husi") = GitHubTrackedApp(
        owner = "xchacha20-poly1305", repo = "husi",
        repoUrl = "https://github.com/xchacha20-poly1305/husi",
        packageName = packageName, appLabel = "Husi", preferPreRelease = true,
    )

    private fun entries() = listOf(
        entry("plugin-shadowquic-v0.4.2-0", true),
        entry("plugin-shadowquic-v0.4.1-0", true),
        entry("plugin-hysteria2-v2.13.0-0", true),
        entry("plugin-mieru-v3.38.0-0", true),
        entry("v2.3.0-alpha.1", true),
        entry("v2.2.0", false),
    )

    private fun entry(tag: String, pre: Boolean) = GitHubAtomReleaseEntry(
        tag = tag, title = tag, link = "https://github.com/xchacha20-poly1305/husi/releases/tag/$tag",
        updatedAtMillis = NOW - 1_000,
        versionCandidates = GitHubVersionUtils.buildVersionCandidates(GitHubVersionCandidateSource.Tag to tag),
        channel = GitHubVersionUtils.classifyVersionChannel(tag)?.takeIf { it.isPreRelease }
            ?: if (pre) GitHubReleaseChannel.PREVIEW else GitHubReleaseChannel.STABLE,
        isLikelyPreRelease = pre,
    )

    private fun snapshot(
        entries: List<GitHubAtomReleaseEntry>,
        source: GitHubReleaseSignalSource = GitHubReleaseSignalSource.GitHubApi,
    ): GitHubRepositoryReleaseSnapshot {
        val selection = GitHubReleaseSelector.plan(entries, source = source).resolve()
        return GitHubRepositoryReleaseSnapshot(
            strategyId = "fixture", feed = GitHubAtomFeed(entries = entries),
            latestStable = selection.stable ?: requireNotNull(selection.preRelease),
            hasStableRelease = selection.hasStableRelease,
            latestPreRelease = selection.preRelease, selection = selection,
        )
    }

    private fun source(failedFamily: String = "", oversizedFamily: String = "") = Source(failedFamily, oversizedFamily)

    private class Source(private val failedFamily: String, private val oversizedFamily: String) : GitHubPreciseApkVersionSource {
        val loadedTags: MutableSet<String> = ConcurrentHashMap.newKeySet()

        override suspend fun loadReleaseAssetBundle(
            owner: String, repo: String, rawTag: String, releaseUrl: String, lookupConfig: GitHubLookupConfig,
        ): Result<GitHubReleaseAssetBundle> {
            loadedTags += rawTag
            val count = if (oversizedFamily.isNotEmpty() && rawTag.startsWith(oversizedFamily)) 14 else 2
            return Result.success(GitHubReleaseAssetBundle(
                releaseName = rawTag, tagName = rawTag, htmlUrl = releaseUrl,
                isPreRelease = rawTag.startsWith("plugin-") || "alpha" in rawTag,
                assets = (1..count).map { index -> GitHubReleaseAssetFile(
                    name = "$rawTag-$index.apk", downloadUrl = "https://example.test/$rawTag/$index.apk",
                    sizeBytes = 100, downloadCount = 0,
                ) },
            ))
        }

        override suspend fun inspectApk(asset: GitHubReleaseAssetFile, lookupConfig: GitHubLookupConfig): Result<GitHubApkManifestInfo> {
            if (failedFamily.isNotEmpty() && asset.name.startsWith(failedFamily) && asset.name.endsWith("2.apk")) {
                return Result.failure(IOException("timeout"))
            }
            val pkg = when {
                asset.name.startsWith("plugin-shadowquic") -> "fr.husi.plugin.shadowquic"
                asset.name.startsWith("plugin-hysteria2") -> "fr.husi.plugin.hysteria2"
                asset.name.startsWith("plugin-mieru") -> "fr.husi.plugin.mieru"
                else -> "fr.husi"
            }
            return Result.success(GitHubApkManifestInfo(
                assetName = asset.name, packageName = pkg, versionName = "1.0", versionCode = "10",
            ))
        }
    }

    private companion object {
        const val NOW = 1_791_417_600_000L
    }
}
