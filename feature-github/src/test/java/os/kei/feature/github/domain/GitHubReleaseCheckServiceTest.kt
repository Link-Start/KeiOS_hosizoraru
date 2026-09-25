package os.kei.feature.github.domain

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.apk.RemoteZipEntryReader
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.rangeDispatcher
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubApkManifestInfoCache
import os.kei.feature.github.data.remote.GitHubApkManifestReader
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubAtomFeed
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubProfileField
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubRepositoryUpstreamProfile
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedIgnoreMode
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.GitHubVersionCandidateSource
import os.kei.feature.github.model.buildGitHubReleaseIgnoreKey
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubReleaseCheckServiceTest {
    @Test
    fun `external build track waits cleanly until repository publishes first release`() {
        val item = trackedApp(preferPreRelease = false).copy(externalBuildUntilRelease = true)
        val snapshot = snapshot(
            stable = signal(""),
            entries = emptyList(),
            hasStableRelease = false,
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "0.1.0",
            localVersionCode = 1L,
            snapshot = snapshot,
        )

        assertEquals(GitHubTrackedReleaseStatus.UpToDate, result.status)
        assertEquals(GitHubReleaseCheckService.EXTERNAL_BUILD_WAITING_RELEASE_MESSAGE, result.message)
        assertFalse(result.hasStableRelease)
        assertEquals(null, result.preRelease)
    }

    @Test
    fun `external build treats only empty release list as waiting`() {
        val external = trackedApp(preferPreRelease = false).copy(externalBuildUntilRelease = true)
        val regular = trackedApp(preferPreRelease = false)

        assertTrue(
            GitHubReleaseCheckService.run {
                external.shouldWaitForFirstRelease(IllegalStateException("no release entries"))
            },
        )
        assertFalse(
            GitHubReleaseCheckService.run {
                regular.shouldWaitForFirstRelease(IllegalStateException("no release entries"))
            },
        )
        assertFalse(
            GitHubReleaseCheckService.run {
                external.shouldWaitForFirstRelease(IllegalStateException("HTTP 404"))
            },
        )
        assertFalse(
            GitHubReleaseCheckService.run {
                external.shouldWaitForFirstRelease(IllegalStateException("network timeout"))
            },
        )
    }

    @Test
    fun `preferred prerelease recommends newer prerelease for BatteryRecorder style repo`() {
        val item = trackedApp(preferPreRelease = true)
        val snapshot = snapshot(
            stable = signal("v1.4.4-release"),
            preRelease = signal("v1.4.7-prerelease3"),
            entries = listOf(entry("v1.4.7-prerelease3"), entry("v1.4.4-release"), entry("v1.4.2-release"))
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "1.4.2-release",
            localVersionCode = 551L,
            snapshot = snapshot
        )

        assertEquals(GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable, result.status)
        assertTrue(result.hasUpdate == true)
        assertTrue(result.hasPreReleaseUpdate)
        assertTrue(result.recommendsPreRelease)
        assertEquals("v1.4.7-prerelease3", result.preRelease?.rawTag)
    }

    @Test
    fun `global prerelease checking keeps stable recommendation when item does not prefer prerelease`() {
        val item = trackedApp(preferPreRelease = false)
        val snapshot = snapshot(
            stable = signal("v1.4.4-release"),
            preRelease = signal("v1.4.7-prerelease3"),
            entries = listOf(entry("v1.4.7-prerelease3"), entry("v1.4.4-release"), entry("v1.4.2-release"))
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "1.4.2-release",
            localVersionCode = 551L,
            snapshot = snapshot,
            checkAllTrackedPreReleases = true
        )

        assertEquals(GitHubTrackedReleaseStatus.UpdateAvailable, result.status)
        assertTrue(result.hasUpdate == true)
        assertTrue(result.hasPreReleaseUpdate)
        assertFalse(result.recommendsPreRelease)
        assertEquals("v1.4.7-prerelease3", result.preReleaseInfo)
    }

    @Test
    fun `newer prerelease becomes optional when stable is already latest and item does not prefer prerelease`() {
        val item = trackedApp(preferPreRelease = false)
        val snapshot = snapshot(
            stable = signal("v1.4.4-release"),
            preRelease = signal("v1.4.7-prerelease3"),
            entries = listOf(entry("v1.4.7-prerelease3"), entry("v1.4.4-release"))
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "1.4.4-release",
            localVersionCode = 554L,
            snapshot = snapshot,
            checkAllTrackedPreReleases = true
        )

        assertEquals(GitHubTrackedReleaseStatus.PreReleaseOptional, result.status)
        assertFalse(result.hasUpdate == true)
        assertTrue(result.hasPreReleaseUpdate)
        assertFalse(result.recommendsPreRelease)
    }

    @Test
    fun `local prerelease older than stable prefers stable update for ImageToolbox style repo`() {
        val item = trackedApp(preferPreRelease = true)
        val snapshot = snapshot(
            stable = signal("3.8.0"),
            preRelease = signal("3.8.0-rc04"),
            entries = listOf(entry("3.8.0"), entry("3.8.0-rc04"))
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "3.8.0-rc04",
            localVersionCode = 224L,
            snapshot = snapshot
        )

        assertEquals(GitHubTrackedReleaseStatus.UpdateAvailable, result.status)
        assertTrue(result.hasUpdate == true)
        assertFalse(result.hasPreReleaseUpdate)
        assertFalse(result.recommendsPreRelease)
        assertTrue(result.isPreReleaseInstalled)
        assertEquals("3.8.0-rc04", result.preReleaseInfo)
    }

    @Test
    fun `unmatched local prerelease still gets prerelease update through channel inference for Capsulyric`() {
        val item = trackedApp(preferPreRelease = false)
        val snapshot = snapshot(
            stable = signal("Version.1.3.Fix2_C359", updatedAtMillis = 1_743_790_000_000L),
            preRelease = signal("Version.26.4.Alpha2_C384", updatedAtMillis = 1_744_137_000_000L),
            entries = listOf(
                entry("Version.26.4.Alpha2_C384"),
                entry("Canary.Version_C384", title = "Canary Build Version.26.4.Canary_C384"),
                entry("Version.1.3.Fix2_C359")
            )
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "Version.26.4.Canary_C378",
            localVersionCode = 378L,
            // Judged when the corpus was captured. The pre-release is four days newer than the
            // stable here -- a live line -- and only the calendar since would make it look stale.
            nowMillis = 1_744_200_000_000L,
            snapshot = snapshot,
            checkAllTrackedPreReleases = true
        )

        assertEquals(GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable, result.status)
        assertTrue(result.hasUpdate == true)
        assertTrue(result.hasPreReleaseUpdate)
        assertTrue(result.recommendsPreRelease)
        assertTrue(result.isPreReleaseInstalled)
        assertEquals("Version.26.4.Alpha2_C384", result.preRelease?.rawTag)
    }

    @Test
    fun `older prerelease remains visible for animeko but does not override stable recommendation`() {
        val item = trackedApp(preferPreRelease = true)
        val snapshot = snapshot(
            stable = signal("v5.4.3", title = "5.4.3", updatedAtMillis = 1_744_426_950_000L),
            preRelease = signal("v5.4.0-beta05", title = "5.4.0-beta05", updatedAtMillis = 1_742_888_753_000L),
            entries = listOf(entry("v5.4.3", title = "5.4.3"), entry("v5.4.0-beta05", title = "5.4.0-beta05"))
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "5.4.0",
            localVersionCode = 50400L,
            snapshot = snapshot,
            // The day `v5.4.3` shipped, so the eighteen-day gap is what decides this and not the
            // calendar since the corpus was captured.
            nowMillis = 1_744_426_950_000L
        )

        assertEquals(GitHubTrackedReleaseStatus.UpdateAvailable, result.status)
        assertTrue(result.hasUpdate == true)
        assertFalse(result.hasPreReleaseUpdate)
        assertFalse(result.recommendsPreRelease)
        // Changed deliberately. This used to assert the beta stayed on the card as history, and the
        // staleness rule retires it: `v5.4.0-beta05` is eighteen days behind `v5.4.3`, past the
        // fortnight in which a superseded preview still has readers. It is still not an update --
        // that half of the contract is asserted above and is what the test is named for -- it just
        // no longer takes a row. A preview inside the fortnight keeps one; see
        // `PreReleaseRelevanceTest`.
        assertEquals("", result.preReleaseInfo)
    }

    @Test
    fun `dev prerelease newer than stable is recommended when prerelease is preferred`() {
        val item = trackedApp(preferPreRelease = true)
        val snapshot = snapshot(
            stable = signal("v26.4.3.C01"),
            preRelease = signal("v26.4.9.C01-Dev"),
            entries = listOf(entry("v26.4.9.C01-Dev"), entry("v26.4.3.C01"))
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "26.4.3.C01",
            localVersionCode = 2026040301L,
            snapshot = snapshot
        )

        assertEquals(GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable, result.status)
        assertTrue(result.hasUpdate == true)
        assertTrue(result.hasPreReleaseUpdate)
        assertTrue(result.recommendsPreRelease)
        assertEquals("v26.4.9.C01-Dev", result.preRelease?.rawTag)
    }

    @Test
    fun `prerelease only repository keeps prerelease visible through hint when prerelease checking is off`() {
        val item = trackedApp(preferPreRelease = false)
        val snapshot = snapshot(
            stable = signal("0.0.8", title = "v0.0.8"),
            preRelease = signal("0.0.8", title = "v0.0.8"),
            entries = listOf(entry("0.0.8", title = "v0.0.8")),
            hasStableRelease = false
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "unknown",
            localVersionCode = -1L,
            snapshot = snapshot
        )

        assertFalse(result.hasStableRelease)
        assertEquals(GitHubTrackedReleaseStatus.ONLY_PRERELEASES_HINT_MESSAGE, result.releaseHint)
        assertFalse(result.showPreReleaseInfo)
        assertEquals("0.0.8", result.preRelease?.rawTag)
        assertEquals(null, result.stableRelease)
    }

    @Test
    fun `current stable signal overrides historical prerelease tagging for matched local build`() {
        val item = trackedApp(preferPreRelease = true)
        val promotedStableEntry = entry("v1.0.0-rc1").copy(
            channel = GitHubReleaseChannel.RC,
            isLikelyPreRelease = true
        )
        val snapshot = snapshot(
            stable = signal("v1.0.0-rc1"),
            preRelease = null,
            entries = listOf(promotedStableEntry),
            hasStableRelease = true
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "1.0.0-rc1",
            localVersionCode = 10001L,
            snapshot = snapshot
        )

        assertFalse(result.isPreReleaseInstalled)
        assertEquals(GitHubTrackedReleaseStatus.UpToDate, result.status)
    }

    @Test
    fun `release notes mentioning previous app version still reports self update`() {
        val item = trackedApp(preferPreRelease = false)
        val contentPreview = "这是从 v1.1.0 到 v1.2.0 的功能更新"
        val stable = signal(
            tag = "v1.2.0",
            title = "KeiOS v1.2.0",
            contentPreview = contentPreview
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "1.1.0",
            localVersionCode = 10_100_000L,
            snapshot = snapshot(
                stable = stable,
                entries = listOf(
                    entry(
                        tag = "v1.2.0",
                        title = "KeiOS v1.2.0",
                        contentPreview = contentPreview
                    ),
                    entry(tag = "v1.1.0", title = "KeiOS v1.1.0")
                )
            )
        )

        assertEquals(GitHubTrackedReleaseStatus.UpdateAvailable, result.status)
        assertEquals(true, result.hasUpdate)
        assertEquals("v1.2.0", result.stableRelease?.rawTag)
    }

    @Test
    fun `local prerelease newer than both stable and stale prerelease does not surface remote update`() {
        val item = trackedApp(preferPreRelease = false)
        val snapshot = snapshot(
            stable = signal("11.1.0-release-2026031101"),
            preRelease = signal("10.9.0-alpha03-2025070901"),
            entries = listOf(
                entry("11.1.0-release-2026031101"),
                entry("10.9.0-alpha03-2025070901")
            )
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "11.2.0-alpha01",
            localVersionCode = 11020001L,
            snapshot = snapshot
        )

        assertTrue(result.isPreReleaseInstalled)
        assertEquals(false, result.hasUpdate)
        assertFalse(result.hasPreReleaseUpdate)
        assertFalse(result.recommendsPreRelease)
        assertEquals("10.9.0-alpha03-2025070901", result.preReleaseInfo)
    }

    @Test
    fun `precise apk version overrides display payload and versionCode comparison`() {
        val item = trackedApp(preferPreRelease = false)
        val stable = signal(tag = "v1.0.0", title = "Demo 1.0.0")

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "1.0.0",
            localVersionCode = 10L,
            snapshot = snapshot(
                stable = stable,
                entries = listOf(entry(tag = "v1.0.0", title = "Demo 1.0.0"))
            ),
            preciseStableApkVersion = GitHubRemoteApkVersionInfo(
                releaseName = "Demo 1.0.0",
                releaseTag = "v1.0.0",
                assetName = "demo.apk",
                packageName = "demo.app",
                versionName = "2.0.0",
                versionCode = "20"
            ),
            sourceConfigSignature = "check-v2|fixture"
        )

        assertEquals(GitHubTrackedReleaseStatus.UpdateAvailable, result.status)
        assertEquals(true, result.hasUpdate)
        assertEquals("2.0.0 (20)", result.preciseStableApkVersion?.versionLabel())
        assertEquals("Demo 1.0.0 · v1.0.0", result.preciseStableApkVersion?.releaseLabel())
        assertEquals("check-v2|fixture", result.sourceConfigSignature)

        val cacheEntry = GitHubReleaseCheckService.run { result.toCacheEntry() }
        val restored = GitHubReleaseCheckService.fromCacheEntry(cacheEntry)
        assertEquals("2.0.0 (20)", restored.preciseStableApkVersion?.versionLabel())
        assertEquals("Demo 1.0.0 · v1.0.0", restored.preciseStableApkVersion?.releaseLabel())
        assertEquals("check-v2|fixture", restored.sourceConfigSignature)
    }

    @Test
    fun `ignored current stable release suppresses stable update`() {
        val ignoredKey = buildGitHubReleaseIgnoreKey(rawTag = "v1.2.0")
        val item = trackedApp(preferPreRelease = false).copy(
            ignoreMode = GitHubTrackedIgnoreMode.CurrentStable,
            ignoredStableReleaseKey = ignoredKey
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "1.1.0",
            localVersionCode = 10_100_000L,
            snapshot = snapshot(
                stable = signal("v1.2.0"),
                entries = listOf(entry("v1.2.0"), entry("v1.1.0"))
            )
        )

        assertEquals(GitHubTrackedReleaseStatus.Ignored, result.status)
        assertEquals(false, result.hasUpdate)
        assertFalse(result.hasPreReleaseUpdate)
    }

    @Test
    fun `new stable release after ignored stable key reports update again`() {
        val item = trackedApp(preferPreRelease = false).copy(
            ignoreMode = GitHubTrackedIgnoreMode.CurrentStable,
            ignoredStableReleaseKey = buildGitHubReleaseIgnoreKey(rawTag = "v1.2.0")
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "1.1.0",
            localVersionCode = 10_100_000L,
            snapshot = snapshot(
                stable = signal("v1.3.0"),
                entries = listOf(entry("v1.3.0"), entry("v1.2.0"), entry("v1.1.0"))
            )
        )

        assertEquals(GitHubTrackedReleaseStatus.UpdateAvailable, result.status)
        assertEquals(true, result.hasUpdate)
    }

    @Test
    fun `ignored prerelease keeps stable update visible`() {
        val item = trackedApp(preferPreRelease = true).copy(
            ignoreMode = GitHubTrackedIgnoreMode.CurrentPreRelease,
            ignoredPreReleaseKey = buildGitHubReleaseIgnoreKey(rawTag = "v1.4.7-beta")
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "1.4.2",
            localVersionCode = 14_200L,
            snapshot = snapshot(
                stable = signal("v1.4.4"),
                preRelease = signal("v1.4.7-beta"),
                entries = listOf(entry("v1.4.7-beta"), entry("v1.4.4"), entry("v1.4.2"))
            )
        )

        assertEquals(GitHubTrackedReleaseStatus.UpdateAvailable, result.status)
        assertEquals(true, result.hasUpdate)
        assertFalse(result.hasPreReleaseUpdate)
        assertFalse(result.recommendsPreRelease)
    }

    @Test
    fun `all version ignore suppresses stable and prerelease counters`() {
        val item = trackedApp(preferPreRelease = true).copy(
            ignoreMode = GitHubTrackedIgnoreMode.AllVersions
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "1.4.2",
            localVersionCode = 14_200L,
            snapshot = snapshot(
                stable = signal("v1.4.4"),
                preRelease = signal("v1.4.7-beta"),
                entries = listOf(entry("v1.4.7-beta"), entry("v1.4.4"), entry("v1.4.2"))
            )
        )

        assertEquals(GitHubTrackedReleaseStatus.Ignored, result.status)
        assertEquals(false, result.hasUpdate)
        assertFalse(result.hasPreReleaseUpdate)
        assertFalse(result.recommendsPreRelease)
    }

    @Test
    fun `Karing tag carrying versionName and versionCode compares against local versionCode`() {
        data class Case(
            val label: String,
            val localVersionCode: Long,
            val expectedStatus: GitHubTrackedReleaseStatus,
            val expectedHasUpdate: Boolean,
        )
        val cases = listOf(
            Case(
                label = "local versionCode matches tag suffix -> installed",
                localVersionCode = 2102L,
                expectedStatus = GitHubTrackedReleaseStatus.UpToDate,
                expectedHasUpdate = false,
            ),
            Case(
                label = "local versionCode differs from tag suffix -> update",
                localVersionCode = 2101L,
                expectedStatus = GitHubTrackedReleaseStatus.UpdateAvailable,
                expectedHasUpdate = true,
            ),
        )

        cases.forEach { case ->
            val result = GitHubReleaseCheckService.evaluateSnapshot(
                item = trackedApp(preferPreRelease = false),
                localVersion = "1.2.18",
                localVersionCode = case.localVersionCode,
                snapshot = snapshot(
                    stable = signal(tag = "v1.2.18.2102", title = "v1.2.18.2102"),
                    entries = listOf(entry(tag = "v1.2.18.2102", title = "v1.2.18.2102"))
                )
            )

            assertEquals(case.expectedStatus, result.status, case.label)
            assertEquals(case.expectedHasUpdate, result.hasUpdate, case.label)
            if (case.expectedStatus == GitHubTrackedReleaseStatus.UpToDate) {
                assertEquals("v1.2.18.2102", result.stableRelease?.rawTag, case.label)
            }
        }
    }

    @Test
    fun `repository profile survives release check cache round trip`() {
        val item = trackedApp(preferPreRelease = false)
        val stable = signal(
            tag = "v1.0.0",
            title = "Demo 1.0.0",
            authorAvatarUrl = "https://avatars.githubusercontent.com/u/42?v=4"
        )
        val profile = GitHubRepositoryProfileSnapshot(
            owner = "demo",
            repo = "app",
            sourceConfigSignature = "check-v2|fixture",
            fetchedAtMillis = 1_700_000_000_000L,
            lifecycle = GitHubRepositoryLifecycleProfile(
                fork = GitHubProfileField(
                    value = true,
                    source = GitHubRepositoryProfileSource.GitHubApiRepository,
                    fetchedAtMillis = 1_700_000_000_000L,
                    confidence = GitHubRepositoryProfileConfidence.High
                ),
                upstream = GitHubRepositoryUpstreamProfile(
                    fullName = GitHubProfileField(
                        value = "upstream/app",
                        source = GitHubRepositoryProfileSource.GitHubApiRepository,
                        fetchedAtMillis = 1_700_000_000_000L,
                        confidence = GitHubRepositoryProfileConfidence.High
                    )
                )
            )
        )

        val result = GitHubReleaseCheckService.evaluateSnapshot(
            item = item,
            localVersion = "1.0.0",
            localVersionCode = 10L,
            snapshot = snapshot(
                stable = stable,
                entries = listOf(entry(tag = "v1.0.0", title = "Demo 1.0.0")),
                repositoryProfile = profile
            ),
            sourceConfigSignature = "check-v2|fixture"
        )
        val restored = GitHubReleaseCheckService.fromCacheEntry(
            GitHubReleaseCheckService.run { result.toCacheEntry() }
        )

        assertEquals(
            "upstream/app",
            restored.repositoryProfile?.lifecycle?.upstream?.fullName?.value
        )
        assertEquals(
            "https://avatars.githubusercontent.com/u/42?v=4",
            restored.stableRelease?.authorAvatarUrl
        )
        assertEquals("check-v2|fixture", restored.repositoryProfile?.sourceConfigSignature)
    }

    @Test
    fun `direct apk manifest compares its versionCode against local versionCode`() {
        data class Case(
            val label: String,
            val manifestVersionName: String,
            val manifestVersionCode: String,
            val expectedStatus: GitHubTrackedReleaseStatus,
            val expectedHasUpdate: Boolean,
        )
        val cases = listOf(
            Case(
                label = "newer versionCode 101 -> update",
                manifestVersionName = "10.1.0",
                manifestVersionCode = "101",
                expectedStatus = GitHubTrackedReleaseStatus.UpdateAvailable,
                expectedHasUpdate = true,
            ),
            Case(
                label = "same versionCode 100 with different versionName -> up to date",
                manifestVersionName = "10.0.1",
                manifestVersionCode = "100",
                expectedStatus = GitHubTrackedReleaseStatus.UpToDate,
                expectedHasUpdate = false,
            ),
        )

        cases.forEach { case ->
            val result = GitHubDirectApkReleaseCheckSource.evaluateManifest(
                item = directApkTrackedApp(),
                localVersion = "10.0.0",
                localVersionCode = 100L,
                manifest = GitHubApkManifestInfo(
                    assetName = "apk.apk",
                    packageName = "org.telegram.messenger",
                    versionName = case.manifestVersionName,
                    versionCode = case.manifestVersionCode
                )
            )

            assertEquals(case.expectedStatus, result.status, case.label)
            assertEquals(case.expectedHasUpdate, result.hasUpdate, case.label)
            if (case.expectedHasUpdate) {
                assertEquals("10.1.0", result.preciseStableApkVersion?.versionName, case.label)
                assertEquals("101", result.preciseStableApkVersion?.versionCode, case.label)
                assertEquals("https://telegram.org/dl/android/apk", result.stableRelease?.link, case.label)
            }
        }
    }

    @Test
    fun `direct apk force refresh reads fixed url again and records diagnostics`() = runBlocking {
        val firstApkBytes = apkWithManifest(
            packageName = "org.telegram.messenger",
            versionName = "12.0.0",
            versionCode = 120000L
        )
        val secondApkBytes = apkWithManifest(
            packageName = "org.telegram.messenger",
            versionName = "12.1.0",
            versionCode = 121000L
        )
        MockWebServer().use { server ->
            val probeCount = AtomicInteger(0)
            var activeApkBytes = firstApkBytes
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.getHeader("Range").orEmpty() == "bytes=0-0") {
                        activeApkBytes = if (probeCount.incrementAndGet() == 1) {
                            firstApkBytes
                        } else {
                            secondApkBytes
                        }
                    }
                    return rangeDispatcher(activeApkBytes).dispatch(request)
                }
            }
            val source =
                GitHubDirectApkReleaseCheckSource(
                    apkInfoRepository =
                        GitHubApkInfoRepository(
                            manifestReader =
                                GitHubApkManifestReader(
                                    zipEntryReader = RemoteZipEntryReader(client = OkHttpClient())
                                ),
                            manifestCache = FakeManifestInfoCache(),
                        )
                )
            val item = directApkTrackedApp()
                .copy(repoUrl = server.url("/dl/android/apk").toString())

            val first = source.evaluate(
                item = item,
                lookupConfig = GitHubLookupConfig(),
                localVersion = "12.0.0",
                localVersionCode = 120000L,
                forceRefresh = false
            )
            val refreshed = source.evaluate(
                item = item,
                lookupConfig = GitHubLookupConfig(),
                localVersion = "12.0.0",
                localVersionCode = 120000L,
                forceRefresh = true
            )

            assertEquals(GitHubTrackedReleaseStatus.UpToDate, first.status)
            assertEquals(
                item.repoUrl,
                first.preciseStableApkVersion?.releaseUrl,
            )
            assertEquals(
                item.repoUrl,
                first.preciseStableApkVersion?.fetchSource,
            )
            assertEquals(GitHubTrackedReleaseStatus.UpdateAvailable, refreshed.status)
            assertEquals("12.1.0", refreshed.preciseStableApkVersion?.versionName)
            assertEquals(2, probeCount.get())
            assertTrue(refreshed.diagnostics.preciseApkRequested)
            assertTrue(refreshed.diagnostics.hasStageData)
        }
    }

    @Test
    fun `direct apk manifest release notes propagate into cache entry`() {
        val item = directApkTrackedApp()

        val result = GitHubDirectApkReleaseCheckSource.evaluateManifest(
            item = item,
            localVersion = "10.0.0",
            localVersionCode = 100L,
            manifest = GitHubApkManifestInfo(
                assetName = "apk.apk",
                packageName = "org.telegram.messenger",
                versionName = "10.1.0",
                versionCode = "101",
                releaseNotes = "Fixed media playback\nImproved push reliability"
            )
        )
        val restored = GitHubReleaseCheckService.fromCacheEntry(
            GitHubReleaseCheckService.run { result.toCacheEntry() }
        )

        assertEquals(
            "Fixed media playback\nImproved push reliability",
            result.preciseStableApkVersion?.releaseNotes
        )
        assertEquals(
            "Fixed media playback\nImproved push reliability",
            restored.preciseStableApkVersion?.releaseNotes
        )
    }

    @Test
    fun `direct apk pre-release manifest reports pre-release update`() {
        val item = directApkTrackedApp(preferPreRelease = true)

        val result = GitHubDirectApkReleaseCheckSource.evaluateManifest(
            item = item,
            localVersion = "10.0.0",
            localVersionCode = 100L,
            manifest = GitHubApkManifestInfo(
                assetName = "apk-alpha.apk",
                packageName = "org.telegram.messenger",
                versionName = "10.1.0 Alpha1",
                versionCode = "101"
            ),
            releaseChannel = GitHubReleaseChannel.ALPHA
        )

        assertEquals(GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable, result.status)
        assertEquals(true, result.hasUpdate)
        assertEquals(true, result.hasPreReleaseUpdate)
        assertEquals("10.1.0 Alpha1", result.precisePreApkVersion?.versionName)
        assertEquals(null, result.preciseStableApkVersion)
        assertEquals(GitHubReleaseChannel.ALPHA, result.preRelease?.channel)
    }

    @Test
    fun `direct apk manifests keep stable update when stable is newer than pre-release`() {
        val item = directApkTrackedApp(preferPreRelease = true)

        val result = GitHubDirectApkReleaseCheckSource.evaluateManifests(
            item = item,
            localVersion = "10.0.0",
            localVersionCode = 100L,
            stableManifest = GitHubApkManifestInfo(
                assetName = "apk.apk",
                packageName = "org.telegram.messenger",
                versionName = "10.2.0",
                versionCode = "102",
                fetchSource = "https://example.com/stable.apk"
            ),
            preReleaseManifest = GitHubApkManifestInfo(
                assetName = "apk-alpha.apk",
                packageName = "org.telegram.messenger",
                versionName = "10.1.0 Alpha1",
                versionCode = "101",
                fetchSource = "https://example.com/alpha.apk"
            ),
            checkAllTrackedPreReleases = true
        )

        assertEquals(GitHubTrackedReleaseStatus.UpdateAvailable, result.status)
        assertEquals(true, result.hasUpdate)
        assertEquals(false, result.hasPreReleaseUpdate)
        assertEquals("10.2.0", result.preciseStableApkVersion?.versionName)
        assertEquals("10.1.0 Alpha1", result.precisePreApkVersion?.versionName)
    }

    @Test
    fun `direct apk local pre-release keeps stable update when stable advances`() {
        val item = directApkTrackedApp(preferPreRelease = false)

        val result = GitHubDirectApkReleaseCheckSource.evaluateManifests(
            item = item,
            localVersion = "10.1.0 Alpha1",
            localVersionCode = 101L,
            stableManifest = GitHubApkManifestInfo(
                assetName = "apk.apk",
                packageName = "org.telegram.messenger",
                versionName = "10.2.0",
                versionCode = "102",
                fetchSource = "https://example.com/stable.apk"
            ),
            preReleaseManifest = GitHubApkManifestInfo(
                assetName = "apk-alpha.apk",
                packageName = "org.telegram.messenger",
                versionName = "10.1.0 Alpha1",
                versionCode = "101",
                fetchSource = "https://example.com/alpha.apk"
            )
        )

        assertEquals(GitHubTrackedReleaseStatus.UpdateAvailable, result.status)
        assertEquals(true, result.hasUpdate)
        assertEquals(false, result.hasPreReleaseUpdate)
        assertEquals(false, result.recommendsPreRelease)
        assertEquals(true, result.isPreReleaseInstalled)
        assertTrue(result.preReleaseInfo.contains("10.1.0 Alpha1"))
        assertEquals("10.2.0", result.preciseStableApkVersion?.versionName)
        assertEquals("10.1.0 Alpha1", result.precisePreApkVersion?.versionName)
    }

    @Test
    fun `direct apk manifests can show optional pre-release when global switch checks it`() {
        val item = directApkTrackedApp(preferPreRelease = false)

        val result = GitHubDirectApkReleaseCheckSource.evaluateManifests(
            item = item,
            localVersion = "10.0.0",
            localVersionCode = 100L,
            stableManifest = GitHubApkManifestInfo(
                assetName = "apk.apk",
                packageName = "org.telegram.messenger",
                versionName = "10.0.0",
                versionCode = "100"
            ),
            preReleaseManifest = GitHubApkManifestInfo(
                assetName = "apk-alpha.apk",
                packageName = "org.telegram.messenger",
                versionName = "10.1.0 Alpha1",
                versionCode = "101"
            ),
            checkAllTrackedPreReleases = true
        )

        assertEquals(GitHubTrackedReleaseStatus.PreReleaseOptional, result.status)
        assertEquals(false, result.hasUpdate)
        assertEquals(true, result.hasPreReleaseUpdate)
        assertEquals(false, result.recommendsPreRelease)
    }

    @Test
    fun `direct apk manifest package mismatch fails before version comparison`() {
        val item = directApkTrackedApp()

        val result = GitHubDirectApkReleaseCheckSource.evaluateManifest(
            item = item,
            localVersion = "10.0.0",
            localVersionCode = 100L,
            manifest = GitHubApkManifestInfo(
                assetName = "apk.apk",
                packageName = "org.telegram.other",
                versionName = "99.0",
                versionCode = "9900"
            )
        )

        assertEquals(GitHubTrackedReleaseStatus.Failed, result.status)
        assertTrue(result.message.contains("org.telegram.other"))
    }

    private fun trackedApp(preferPreRelease: Boolean): GitHubTrackedApp {
        return GitHubTrackedApp(
            repoUrl = "https://github.com/demo/app",
            owner = "demo",
            repo = "app",
            packageName = "demo.app",
            appLabel = "Demo",
            preferPreRelease = preferPreRelease
        )
    }

    private fun directApkTrackedApp(preferPreRelease: Boolean = false): GitHubTrackedApp {
        return GitHubTrackedApp(
            repoUrl = "https://telegram.org/dl/android/apk",
            owner = "telegram.org",
            repo = "dl-android-apk",
            packageName = "org.telegram.messenger",
            appLabel = "Telegram",
            sourceMode = GitHubTrackedSourceMode.DirectApk,
            preferPreRelease = preferPreRelease
        )
    }

    private fun apkWithManifest(
        packageName: String,
        versionName: String,
        versionCode: Long
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(
                BinaryManifestFixture.build(
                    packageName = packageName,
                    versionName = versionName,
                    versionCode = versionCode
                )
            )
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private class FakeManifestInfoCache : GitHubApkManifestInfoCache {
        private val values = linkedMapOf<String, GitHubApkManifestInfo>()

        override fun load(
            cacheKey: String,
            refreshIntervalHours: Int
        ): GitHubApkManifestInfo? = values[cacheKey]

        override fun save(
            cacheKey: String,
            info: GitHubApkManifestInfo
        ) {
            values[cacheKey] = info
        }

        override fun remove(cacheKey: String) {
            values.remove(cacheKey)
        }
    }

    private fun snapshot(
        stable: GitHubReleaseVersionSignals,
        preRelease: GitHubReleaseVersionSignals? = null,
        entries: List<GitHubAtomReleaseEntry>,
        hasStableRelease: Boolean = true,
        repositoryProfile: GitHubRepositoryProfileSnapshot? = null
    ): GitHubRepositoryReleaseSnapshot {
        return GitHubRepositoryReleaseSnapshot(
            strategyId = "github_api_token",
            feed = GitHubAtomFeed(
                title = "demo/app releases",
                feedUrl = "https://github.com/demo/app/releases",
                entries = entries
            ),
            latestStable = stable,
            hasStableRelease = hasStableRelease,
            latestPreRelease = preRelease,
            repositoryProfile = repositoryProfile
        )
    }

    private fun signal(
        tag: String,
        title: String = tag,
        updatedAtMillis: Long? = null,
        contentPreview: String = "",
        authorAvatarUrl: String = ""
    ): GitHubReleaseVersionSignals {
        return GitHubReleaseVersionSignals(
            displayVersion = title,
            rawTag = tag,
            rawName = title,
            link = GitHubVersionUtils.buildReleaseTagUrl("demo", "app", tag),
            updatedAtMillis = updatedAtMillis,
            versionCandidates = GitHubVersionUtils.buildVersionCandidates(
                GitHubVersionCandidateSource.Tag to tag,
                GitHubVersionCandidateSource.Title to title,
                GitHubVersionCandidateSource.Content to contentPreview
            ),
            source = GitHubReleaseSignalSource.GitHubApi,
            channel = GitHubVersionUtils.classifyVersionChannel(tag)
                ?: GitHubReleaseChannel.UNKNOWN,
            authorAvatarUrl = authorAvatarUrl
        )
    }

    private fun entry(
        tag: String,
        title: String = tag,
        contentPreview: String = ""
    ): GitHubAtomReleaseEntry {
        val channel = GitHubVersionUtils.classifyVersionChannel(tag) ?: GitHubReleaseChannel.UNKNOWN
        return GitHubAtomReleaseEntry(
            tag = tag,
            title = title,
            link = GitHubVersionUtils.buildReleaseTagUrl("demo", "app", tag),
            versionCandidates = GitHubVersionUtils.buildVersionCandidates(
                GitHubVersionCandidateSource.Tag to tag,
                GitHubVersionCandidateSource.Title to title,
                GitHubVersionCandidateSource.Content to contentPreview
            ),
            channel = channel,
            isLikelyPreRelease = channel.isPreRelease
        )
    }
}
