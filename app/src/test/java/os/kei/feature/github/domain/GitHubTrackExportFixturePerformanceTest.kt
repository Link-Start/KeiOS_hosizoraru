package os.kei.feature.github.domain

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import os.kei.feature.github.data.apk.AndroidBinaryXmlPackageNameParser
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.apk.RemoteZipEntryReader
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.rangeDispatcher
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.zipWithManifest
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.zipWithStoredEntries
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubActionsArtifactManifestProbe
import os.kei.feature.github.data.remote.GitHubApkManifestReader
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubActionsArtifactSelectionOptions
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubAppRepositorySearchRequest
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import os.kei.feature.github.model.GitHubRepoTarget
import os.kei.feature.github.model.GitHubStarImportApkVerificationStatus
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.feature.github.model.GitHubStarredRepositoryImportSource
import os.kei.feature.github.model.GitHubStrategyBenchmarkTestType
import os.kei.feature.github.model.GitHubStrategyLoadTrace
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.GitHubTrackedFilterMode
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.page.GitHubPageContentInput
import os.kei.ui.page.main.github.page.GitHubPageContentStateDeriver
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubTrackExportFixturePerformanceTest {
    @Test
    fun `exported 30-track fixture exercises github local chains with performance report`() =
        runBlocking {
            val items = GitHubTrackExportFixture.trackedItems
            val expectedCount = GitHubTrackExportFixture.expectedItemCount
            assertEquals(expectedCount, items.size)
            val lookupConfig = GitHubLookupConfig()
            val apiLookupConfig = GitHubLookupConfig(
                selectedStrategy = GitHubLookupStrategyOption.GitHubApiToken,
                apiToken = "token-123"
            )
            val scanSource = GitHubTrackFixtureSources.packageScanSource(items)
            val scanner = GitHubApkPackageNameScanner(scanSource)
            val discoverySource = GitHubTrackFixtureSources.discoverySource(items)
            val resolver = GitHubPackageRepositoryResolver(
                discoverySource = discoverySource,
                packageNameScanner = scanner
            )
            val discoveryService = GitHubRepositoryDiscoveryService(discoverySource)
            val importCandidates = GitHubTrackFixtureSources.importCandidates(items)
            val releaseSnapshots = items.mapIndexed { index, item ->
                GitHubTrackFixtureSources.releaseSnapshot(item, index)
            }
            val preciseSource = FixturePreciseApkVersionSource(items)
            val preciseResolver = GitHubPreciseApkVersionResolver(preciseSource)
            val metrics = mutableListOf<FixturePerformanceMetric>()

            metrics += measureFixtureChain(
                name = "track-json-import",
                repeatCount = 20,
                itemCount = items.size
            ) {
                val payload = GitHubTrackStore.parseTrackedItemsImport(
                    GitHubTrackExportFixture.rawJson
                )
                assertEquals(expectedCount, payload.items.size)
            }

            metrics += measureFixtureChain(
                name = "release-snapshot-evaluate",
                repeatCount = 10,
                itemCount = items.size
            ) {
                items.forEachIndexed { index, item ->
                    val result = GitHubReleaseCheckService.evaluateSnapshot(
                        item = item,
                        localVersion = GitHubTrackFixtureSources.localVersion(index),
                        localVersionCode = index.toLong(),
                        snapshot = releaseSnapshots[index],
                        checkAllTrackedPreReleases = true
                    )
                    assertTrue(result.status != GitHubTrackedReleaseStatus.Failed)
                }
            }

            metrics += measureFixtureChain(
                name = "precise-apk-version-resolver-cached",
                repeatCount = 5,
                itemCount = items.size
            ) {
                items.forEachIndexed { index, item ->
                    val result = preciseResolver.resolve(
                        GitHubPreciseApkVersionRequest(
                            owner = item.owner,
                            repo = item.repo,
                            release = releaseSnapshots[index].latestStable,
                            packageName = item.packageName,
                            lookupConfig = lookupConfig.copy(preciseApkVersionEnabled = true)
                        )
                    ).getOrThrow()
                    assertEquals(item.packageName, result.packageName)
                    assertTrue(result.versionLabel().isNotBlank())
                }
                assertTrue(preciseSource.inspectCount <= expectedCount)
            }

            metrics += measureFixtureChain(
                name = "package-name-scan-atom",
                repeatCount = 5,
                itemCount = items.size
            ) {
                items.forEach { item ->
                    val result = scanner.scan(
                        GitHubApkPackageNameScanRequest(
                            repoUrl = item.repoUrl,
                            lookupConfig = lookupConfig
                        )
                    ).getOrThrow()
                    assertEquals(item.packageName, result.packageName)
                }
            }

            metrics += measureFixtureChain(
                name = "package-name-scan-api",
                repeatCount = 5,
                itemCount = items.size
            ) {
                items.forEach { item ->
                    val result = scanner.scan(
                        GitHubApkPackageNameScanRequest(
                            repoUrl = item.repoUrl,
                            lookupConfig = apiLookupConfig
                        )
                    ).getOrThrow()
                    assertEquals(item.packageName, result.packageName)
                }
            }

            metrics += measureFixtureChain(
                name = "repository-resolver-preferred",
                repeatCount = 3,
                itemCount = items.size
            ) {
                items.forEach { item ->
                    val result = resolver.scanRepositoriesForPackage(
                        GitHubPackageRepositoryScanRequest(
                            packageName = item.packageName,
                            appLabel = item.appLabel,
                            preferredRepoUrl = item.repoUrl,
                            lookupConfig = apiLookupConfig,
                            candidateLimit = items.size,
                            verificationLimit = items.size
                        )
                    ).getOrThrow()
                    assertNotNull(result.matchedCandidates.firstOrNull())
                }
            }

            metrics += measureFixtureChain(
                name = "repository-resolver-discovery",
                repeatCount = 3,
                itemCount = items.size
            ) {
                items.forEach { item ->
                    val result = resolver.scanRepositoriesForPackage(
                        GitHubPackageRepositoryScanRequest(
                            packageName = item.packageName,
                            appLabel = item.appLabel,
                            lookupConfig = apiLookupConfig,
                            candidateLimit = items.size,
                            verificationLimit = items.size
                        )
                    ).getOrThrow()
                    assertNotNull(result.matchedCandidates.firstOrNull())
                }
            }

            metrics += measureFixtureChain(
                name = "star-import-apk-verifier",
                repeatCount = 5,
                itemCount = items.size
            ) {
                val verifier = GitHubStarImportApkVerifier(scanSource)
                importCandidates.forEach { candidate ->
                    val result = verifier.verify(
                        candidate = candidate,
                        lookupConfig = lookupConfig,
                        nowMillis = 1_700_000_000_000L
                    )
                    assertEquals(GitHubStarImportApkVerificationStatus.HasApk, result.status)
                    assertTrue(result.packageName.isNotBlank())
                }
            }

            metrics += measureFixtureChain(
                name = "star-list-import-preview",
                repeatCount = 10,
                itemCount = items.size
            ) {
                val preview = discoveryService.previewStarredRepositoryImport(
                    request = GitHubStarredRepositoryImportRequest(
                        source = GitHubStarredRepositoryImportSource.StarListUrl,
                        starListUrl = "https://github.com/stars/fixture",
                        limit = items.size
                    ),
                    existingItems = emptyList()
                ).getOrThrow()
                assertEquals(expectedCount, preview.totalFetchedCount)
                assertEquals(expectedCount, preview.importableCount)
            }

            metrics += measureFixtureChain(
                name = "installed-app-repository-search",
                repeatCount = 3,
                itemCount = items.size
            ) {
                items.forEach { item ->
                    val result = discoveryService.searchRepositoriesForApp(
                        request = GitHubAppRepositorySearchRequest(
                            app = InstalledAppItem(
                                label = item.appLabel,
                                packageName = item.packageName
                            ),
                            limit = items.size
                        ),
                        existingItems = emptyList()
                    ).getOrThrow()
                    assertTrue(result.candidates.isNotEmpty())
                }
            }

            metrics += measureFixtureChain(
                name = "actions-artifact-selector",
                repeatCount = 20,
                itemCount = items.size
            ) {
                val matches = GitHubActionsArtifactSelector.selectDisplayArtifacts(
                    artifacts = GitHubTrackFixtureSources.actionsArtifacts(items),
                    options = GitHubActionsArtifactSelectionOptions(
                        preferredAbis = listOf("arm64-v8a")
                    )
                )
                assertTrue(matches.isNotEmpty())
                assertTrue(matches.first().artifact.name.contains("release", ignoreCase = true))
            }

            metrics += measureFixtureChain(
                name = "page-content-derivation",
                repeatCount = 10,
                itemCount = items.size
            ) {
                val derived = GitHubPageContentStateDeriver().build(
                    input = GitHubPageContentInput(
                        trackedItems = items,
                        trackedSearch = "",
                        trackedFilterMode = GitHubTrackedFilterMode.All,
                        sortMode = GitHubSortMode.UpdateFirst,
                        checkStates = items.mapIndexed { index, item ->
                            item.id to VersionCheckUi(
                                hasUpdate = index % 3 == 0,
                                isPreRelease = item.preferPreRelease,
                                hasPreReleaseUpdate = item.preferPreRelease
                            )
                        }.toMap(),
                        appList = items.mapIndexed { index, item ->
                            InstalledAppItem(
                                label = item.appLabel,
                                packageName = item.packageName,
                                lastUpdateTimeMs = 1_700_000_000_000L + index
                            )
                        },
                        trackedFirstInstallAtByPackage = emptyMap(),
                        trackedAddedAtById = emptyMap(),
                        pendingShareImportTrack = null,
                        nowMillis = 1_700_000_000_000L
                    )
                )
                assertEquals(expectedCount, derived.trackedUi.overviewMetrics.trackedCount)
                assertEquals(expectedCount, derived.trackedUi.sortedTracked.size)
            }

            metrics += measureFixtureChain(
                name = "strategy-benchmark-fixture",
                repeatCount = 3,
                itemCount = items.size
            ) {
                val targets = GitHubStrategyBenchmarkService.buildTargets(
                    trackedItems = items,
                    limit = items.size
                )
                val result = GitHubStrategyBenchmarkService.compareTargetsWithRunners(
                    targets = targets,
                    runners = listOf(fixtureBenchmarkRunner()),
                    maxConcurrency = 4
                ).results.single()
                assertEquals(expectedCount, result.totalTargets)
                assertEquals(expectedCount, result.warmSamples.size)
                assertEquals(
                    3,
                    result.samplesFor(GitHubStrategyBenchmarkTestType.ReleaseAssets).size
                )
                assertEquals(
                    2,
                    result.samplesFor(GitHubStrategyBenchmarkTestType.ApkManifest).size
                )
            }

            metrics += measureFixtureChain(
                name = "actions-nested-manifest-probe",
                repeatCount = 3,
                itemCount = items.size
            ) {
                val selectedItem = items.first()
                val artifactBytes = zipWithStoredEntries(
                    GitHubTrackFixtureSources.actionArtifactEntryNames(
                        items = items,
                        selectedItem = selectedItem
                    ).map { (entryName, item) ->
                        entryName to zipWithManifest(
                            BinaryManifestFixture.build(item.packageName)
                        )
                    }
                )
                MockWebServer().use { server ->
                    server.dispatcher = rangeDispatcher(artifactBytes)
                    val probe = GitHubActionsArtifactManifestProbe(
                        manifestReader = GitHubApkManifestReader(
                            zipEntryReader = RemoteZipEntryReader(client = OkHttpClient())
                        )
                    )
                    val packageName = probe.readPackageName(
                        artifact = GitHubActionsArtifact(
                            id = expectedCount.toLong(),
                            name = "fixture-30-track-artifact",
                            sizeBytes = artifactBytes.size.toLong()
                        ),
                        resolvedDownloadUrl = server.url("/download/artifact.zip").toString(),
                        lookupConfig = lookupConfig
                    ).getOrThrow()
                    assertEquals(selectedItem.packageName, packageName)
                }
            }

            val report = writePerformanceReport(metrics)
            println(report.readText())
        }

    private suspend fun measureFixtureChain(
        name: String,
        repeatCount: Int,
        itemCount: Int,
        block: suspend () -> Unit
    ): FixturePerformanceMetric {
        block()
        val durationsNs = buildList {
            repeat(repeatCount) {
                val startedAt = System.nanoTime()
                block()
                add(System.nanoTime() - startedAt)
            }
        }
        return FixturePerformanceMetric(
            name = name,
            repeatCount = repeatCount,
            itemCount = itemCount,
            durationsNs = durationsNs
        )
    }

    private fun fixtureBenchmarkRunner(): GitHubStrategyBenchmarkRunner {
        return GitHubStrategyBenchmarkRunner(
            strategyId = "fixture",
            displayName = "Fixture",
            clearCaches = {},
            load = { target ->
                val index = target.repo.length % GitHubTrackExportFixture.trackedItems.size
                GitHubStrategyLoadTrace(
                    result = Result.success(
                        GitHubTrackFixtureSources.releaseSnapshot(
                            item = GitHubTrackExportFixture.trackedItems[index],
                            index = index
                        )
                    ),
                    fromCache = false,
                    elapsedMs = 1L
                )
            },
            loadReleaseAssets = { target ->
                GitHubStrategyLoadTrace(
                    result = Result.success(releaseBundle(target = target, notes = "")),
                    fromCache = false,
                    elapsedMs = 1L
                )
            },
            loadReleaseNotes = { target ->
                GitHubStrategyLoadTrace(
                    result = Result.success(
                        releaseBundle(
                            target = target,
                            notes = "Fixture release notes for ${target.id}"
                        )
                    ),
                    fromCache = false,
                    elapsedMs = 1L
                )
            },
            inspectApkManifest = { target ->
                GitHubStrategyLoadTrace(
                    result = Result.success(
                        GitHubApkManifestInfo(
                            assetName = "${target.repo}.apk",
                            packageName = target.packageName
                        )
                    ),
                    fromCache = false,
                    elapsedMs = 1L
                )
            },
            scanPackageName = { target ->
                GitHubStrategyLoadTrace(
                    result = Result.success(target.packageName),
                    fromCache = false,
                    elapsedMs = 1L
                )
            },
            scanRepository = { target ->
                GitHubStrategyLoadTrace(
                    result = Result.success(target.id),
                    fromCache = false,
                    elapsedMs = 1L
                )
            }
        )
    }

    private fun releaseBundle(
        target: GitHubRepoTarget,
        notes: String
    ): GitHubReleaseAssetBundle {
        return GitHubReleaseAssetBundle(
            releaseName = target.repo,
            tagName = "v-fixture",
            htmlUrl = "${target.normalizedRepoUrl}/releases/tag/v-fixture",
            releaseNotesBody = notes,
            assets = listOf(
                GitHubReleaseAssetFile(
                    name = "${target.repo}.apk",
                    downloadUrl = "${target.normalizedRepoUrl}/releases/download/v-fixture/${target.repo}.apk",
                    sizeBytes = 1L,
                    downloadCount = 1
                )
            )
        )
    }

    private class FixturePreciseApkVersionSource(
        items: List<os.kei.feature.github.model.GitHubTrackedApp>
    ) : GitHubPreciseApkVersionSource {
        private val itemByRepo = items.associateBy { item -> "${item.owner}/${item.repo}" }
        private val itemByAssetName =
            ConcurrentHashMap<String, os.kei.feature.github.model.GitHubTrackedApp>()
        private val manifestCache = ConcurrentHashMap<String, GitHubApkManifestInfo>()
        private val inspectCounter = AtomicInteger(0)

        val inspectCount: Int
            get() = inspectCounter.get()

        override suspend fun loadReleaseAssetBundle(
            owner: String,
            repo: String,
            rawTag: String,
            releaseUrl: String,
            lookupConfig: GitHubLookupConfig
        ): Result<GitHubReleaseAssetBundle> = runCatching {
            val item = requireNotNull(itemByRepo["$owner/$repo"])
            val assetName = "${repo}-${rawTag}.apk"
            itemByAssetName[assetName] = item
            GitHubReleaseAssetBundle(
                releaseName = "${item.appLabel} $rawTag",
                tagName = rawTag,
                htmlUrl = releaseUrl,
                assets = listOf(
                    GitHubReleaseAssetFile(
                        name = assetName,
                        downloadUrl = "${item.repoUrl}/releases/download/$rawTag/$assetName",
                        sizeBytes = 1024L,
                        downloadCount = 1
                    )
                )
            )
        }

        override suspend fun inspectApk(
            asset: GitHubReleaseAssetFile,
            lookupConfig: GitHubLookupConfig
        ): Result<GitHubApkManifestInfo> = runCatching {
            manifestCache.getOrPut(asset.name) {
                val item = requireNotNull(itemByAssetName[asset.name])
                val versionSeed = item.repo.length + item.owner.length
                inspectCounter.incrementAndGet()
                AndroidBinaryXmlPackageNameParser.parseManifestInfo(
                    BinaryManifestFixture.build(
                        packageName = item.packageName,
                        versionName = "1.0.$versionSeed",
                        versionCode = 10_000L + versionSeed
                    )
                ).getOrThrow().copy(assetName = asset.name)
            }
        }
    }

    private fun writePerformanceReport(
        metrics: List<FixturePerformanceMetric>
    ): File {
        val workingDir = File(System.getProperty("user.dir").orEmpty())
        val appDir = if (workingDir.name == "app") {
            workingDir
        } else {
            File(workingDir, "app")
        }
        val reportDir = File(appDir, "build/reports/github-fixture-performance")
        reportDir.mkdirs()
        val report = File(reportDir, "github-30-fixture-performance.md")
        report.writeText(buildPerformanceMarkdown(metrics))
        return report
    }

    private fun buildPerformanceMarkdown(
        metrics: List<FixturePerformanceMetric>
    ): String {
        return buildString {
            appendLine("# GitHub 30-Track Fixture Performance")
            appendLine()
            appendLine("| chain | repeats | items | avg run ms | avg item ms | min run ms | max run ms |")
            appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: |")
            metrics.forEach { metric ->
                append("| ")
                append(metric.name)
                append(" | ")
                append(metric.repeatCount)
                append(" | ")
                append(metric.itemCount)
                append(" | ")
                append(metric.avgRunMs.formatMs())
                append(" | ")
                append(metric.avgItemMs.formatMs())
                append(" | ")
                append(metric.minRunMs.formatMs())
                append(" | ")
                append(metric.maxRunMs.formatMs())
                appendLine(" |")
            }
            appendLine()
            append("overall avg item ms: ")
            appendLine(metrics.map { it.avgItemMs }.average().formatMs())
        }
    }

    private fun Double.formatMs(): String {
        return String.format(Locale.US, "%.3f", this)
    }

    private data class FixturePerformanceMetric(
        val name: String,
        val repeatCount: Int,
        val itemCount: Int,
        val durationsNs: List<Long>
    ) {
        val avgRunMs: Double
            get() = durationsNs.average() / 1_000_000.0

        val avgItemMs: Double
            get() = avgRunMs / itemCount.coerceAtLeast(1)

        val minRunMs: Double
            get() = durationsNs.minOrNull().orZero() / 1_000_000.0

        val maxRunMs: Double
            get() = durationsNs.maxOrNull().orZero() / 1_000_000.0
    }
}

private fun Long?.orZero(): Long {
    return this ?: 0L
}
