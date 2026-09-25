package os.kei.feature.github.domain.fdroid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.data.remote.fdroid.FdroidPackageSnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidRepositorySnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidSearchApiApp
import os.kei.feature.github.data.remote.fdroid.FdroidSearchApiClient
import os.kei.feature.github.model.FdroidAppSearchRequest
import os.kei.feature.github.model.FdroidAppSearchSource
import os.kei.feature.github.model.FdroidIndexFormat
import os.kei.feature.github.model.FdroidRepositoryPresets
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FdroidAppSearchServiceTest {
    @Test
    fun `search uses official search api for fdroid main name lookup`() = runBlocking {
        val service = FdroidAppSearchService(
            searchApiClient = fakeSearchApiClient(
                apps = listOf(
                    FdroidSearchApiApp(
                        name = "AntennaPod",
                        summary = "Podcast player",
                        iconUrl = "https://example.test/icon.png",
                        packagePageUrl = "https://f-droid.org/en/packages/de.danoeh.antennapod",
                        packageName = "de.danoeh.antennapod"
                    )
                )
            ),
            repositorySearchProvider = emptyRepositorySearchProvider()
        )

        val result = service.search(
            FdroidAppSearchRequest(
                query = "antenna",
                repoUrls = listOf(FdroidRepositoryPresets.Main.repoUrl)
            )
        ).getOrThrow()

        assertEquals(1, result.candidates.size)
        val candidate = result.candidates.single()
        assertEquals("de.danoeh.antennapod", candidate.packageName)
        assertEquals("AntennaPod", candidate.appName)
        assertEquals(FdroidAppSearchSource.OfficialSearchApi, candidate.source)
        assertEquals(FdroidRepositoryPresets.MAIN_ID, candidate.repoPresetId)
    }

    @Test
    fun `package scan can find app on izzy when fdroid main misses`() = runBlocking {
        val service = FdroidAppSearchService(
            searchApiClient = fakeSearchApiClient(apps = emptyList()),
            repositorySearchProvider = repositorySearchProvider(
                FdroidRepositoryPresets.IzzyOnDroid.repoUrl to repository(
                    repoUrl = FdroidRepositoryPresets.IzzyOnDroid.repoUrl,
                    repoName = "IzzyOnDroid",
                    packages = listOf(
                        packageSnapshot(
                            packageName = "com.perol.asdpl.play.pixivez",
                            appName = "PixEz",
                            summary = "Pixiv client"
                        )
                    )
                )
            )
        )

        val result = service.search(
            FdroidAppSearchRequest(
                packageName = "com.perol.asdpl.play.pixivez",
                repoUrls = listOf(
                    FdroidRepositoryPresets.Main.repoUrl,
                    FdroidRepositoryPresets.IzzyOnDroid.repoUrl
                )
            )
        ).getOrThrow()

        assertEquals(1, result.candidates.size)
        val candidate = result.candidates.single()
        assertEquals(FdroidRepositoryPresets.IzzyOnDroid.repoUrl, candidate.repoUrl)
        assertEquals(FdroidRepositoryPresets.IZZY_ID, candidate.repoPresetId)
        assertEquals("PixEz", candidate.appName)
        assertEquals("com.perol.asdpl.play.pixivez", candidate.packageName)
        assertEquals("1.0", candidate.latestVersionName)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `fdroid main keeps official empty result without scanning repository index`() =
        runBlocking {
            val service = FdroidAppSearchService(
                searchApiClient = fakeSearchApiClient(apps = emptyList()),
                repositorySearchProvider = FdroidRepositorySearchProvider { _, _, _, _, _ ->
                    error("repository index should not be scanned after a successful official search")
                }
            )

            val result = service.search(
                FdroidAppSearchRequest(
                    query = "AntennaPod",
                    repoUrls = listOf(FdroidRepositoryPresets.Main.repoUrl)
                )
            ).getOrThrow()

            assertTrue(result.candidates.isEmpty())
            assertTrue(result.failures.isEmpty())
        }

    @Test
    fun `fdroid main package scan uses package api when search api misses`() =
        runBlocking {
            val service = FdroidAppSearchService(
                searchApiClient = fakeSearchApiClient(apps = emptyList()),
                packageApiProvider = FdroidPackageSearchProvider { repoUrl, packageName ->
                    Result.success(
                        packageSnapshot(
                            packageName = packageName,
                            appName = "AntennaPod",
                            summary = "Podcast player",
                        ).copy(repoUrl = repoUrl)
                    )
                },
                repositorySearchProvider = FdroidRepositorySearchProvider { _, _, _, _, _ ->
                    error("repository index should not be scanned after package api hit")
                }
            )

            val result = service.search(
                FdroidAppSearchRequest(
                    packageName = "de.danoeh.antennapod",
                    repoUrls = listOf(FdroidRepositoryPresets.Main.repoUrl)
                )
            ).getOrThrow()

            assertEquals(1, result.candidates.size)
            val candidate = result.candidates.single()
            assertEquals("de.danoeh.antennapod", candidate.packageName)
            assertEquals("AntennaPod", candidate.appName)
            assertEquals(FdroidAppSearchSource.PackageApi, candidate.source)
            assertEquals(FdroidRepositoryPresets.MAIN_ID, candidate.repoPresetId)
            assertTrue(result.failures.isEmpty())
        }

    @Test
    fun `fdroid main package api miss stays quiet when search api succeeds empty`() =
        runBlocking {
            val service = FdroidAppSearchService(
                searchApiClient = fakeSearchApiClient(apps = emptyList()),
                packageApiProvider = FdroidPackageSearchProvider { _, _ ->
                    Result.failure(IllegalStateException("package not found"))
                },
                repositorySearchProvider = FdroidRepositorySearchProvider { _, _, _, _, _ ->
                    error("repository index should stay idle after official empty result")
                }
            )

            val result = service.search(
                FdroidAppSearchRequest(
                    packageName = "missing.package",
                    repoUrls = listOf(FdroidRepositoryPresets.Main.repoUrl)
                )
            ).getOrThrow()

            assertTrue(result.candidates.isEmpty())
            assertTrue(result.failures.isEmpty())
        }

    @Test
    fun `fdroid main falls back to repository index when official search api fails`() =
        runBlocking {
            val service = FdroidAppSearchService(
                searchApiClient = failingSearchApiClient(),
                repositorySearchProvider = repositorySearchProvider(
                    FdroidRepositoryPresets.Main.repoUrl to repository(
                        repoUrl = FdroidRepositoryPresets.Main.repoUrl,
                        repoName = "F-Droid",
                        packages = listOf(
                            packageSnapshot(
                                packageName = "de.danoeh.antennapod",
                                appName = "AntennaPod",
                                summary = "Podcast player"
                            )
                        )
                    )
                )
            )

            val result = service.search(
                FdroidAppSearchRequest(
                    query = "AntennaPod",
                    repoUrls = listOf(FdroidRepositoryPresets.Main.repoUrl)
                )
            ).getOrThrow()

            assertEquals(listOf("de.danoeh.antennapod"), result.candidates.map { it.packageName })
            assertEquals(FdroidAppSearchSource.RepositoryIndex, result.candidates.single().source)
        }

    @Test
    fun `installed app scan can fall back to app name when fdroid package differs`() = runBlocking {
        val service = FdroidAppSearchService(
            searchApiClient = fakeSearchApiClient(apps = emptyList()),
            repositorySearchProvider = repositorySearchProvider(
                FdroidRepositoryPresets.IzzyOnDroid.repoUrl to repository(
                    repoUrl = FdroidRepositoryPresets.IzzyOnDroid.repoUrl,
                    repoName = "IzzyOnDroid",
                    packages = listOf(
                        packageSnapshot(
                            packageName = "com.perol.pixez",
                            appName = "PixEz",
                            summary = "Pixiv client"
                        )
                    )
                )
            )
        )

        val result = service.search(
            FdroidAppSearchRequest(
                query = "PixEz",
                packageName = "com.perol.play.pixez",
                repoUrls = listOf(
                    FdroidRepositoryPresets.Main.repoUrl,
                    FdroidRepositoryPresets.IzzyOnDroid.repoUrl
                )
            )
        ).getOrThrow()

        assertEquals(listOf("com.perol.pixez"), result.candidates.map { it.packageName })
        val candidate = result.candidates.single()
        assertEquals(FdroidRepositoryPresets.IzzyOnDroid.repoUrl, candidate.repoUrl)
        assertEquals("PixEz", candidate.appName)
        assertTrue(candidate.score < 1000)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `name lookup filters third party repository index`() = runBlocking {
        val service = FdroidAppSearchService(
            searchApiClient = fakeSearchApiClient(apps = emptyList()),
            repositorySearchProvider = repositorySearchProvider(
                FdroidRepositoryPresets.IzzyOnDroid.repoUrl to repository(
                    repoUrl = FdroidRepositoryPresets.IzzyOnDroid.repoUrl,
                    repoName = "IzzyOnDroid",
                    packages = listOf(
                        packageSnapshot("dev.imranr.obtainium", "Obtainium", "App updater"),
                        packageSnapshot("com.perol.asdpl.play.pixivez", "PixEz", "Pixiv client")
                    )
                )
            )
        )

        val result = service.search(
            FdroidAppSearchRequest(
                query = "pixez",
                repoUrls = listOf(FdroidRepositoryPresets.IzzyOnDroid.repoUrl)
            )
        ).getOrThrow()

        assertEquals(listOf("com.perol.asdpl.play.pixivez"), result.candidates.map { it.packageName })
    }

    @Test
    fun `search passes force refresh to repository index provider`() = runBlocking {
        val forceRefreshValues = mutableListOf<Boolean>()
        val service = FdroidAppSearchService(
            searchApiClient = fakeSearchApiClient(apps = emptyList()),
            repositorySearchProvider = FdroidRepositorySearchProvider { repoUrl, _, _, _, forceRefresh ->
                forceRefreshValues += forceRefresh
                Result.success(
                    repository(
                        repoUrl = repoUrl,
                        repoName = "IzzyOnDroid",
                        packages = listOf(
                            packageSnapshot("dev.imranr.obtainium", "Obtainium", "App updater")
                        )
                    )
                )
            }
        )

        service.search(
            FdroidAppSearchRequest(
                query = "Obtainium",
                repoUrls = listOf(FdroidRepositoryPresets.IzzyOnDroid.repoUrl),
                forceRefresh = true
            )
        ).getOrThrow()

        assertEquals(listOf(true), forceRefreshValues)
    }

    @Test
    fun `search reports per repository timing and outcome`() = runBlocking {
        var nowMillis = 1_000L
        val service = FdroidAppSearchService(
            searchApiClient = fakeSearchApiClient(apps = emptyList()),
            repositorySearchProvider = FdroidRepositorySearchProvider { repoUrl, _, _, _, _ ->
                nowMillis += 37L
                if (repoUrl == FdroidRepositoryPresets.IzzyOnDroid.repoUrl) {
                    Result.success(
                        repository(
                            repoUrl = repoUrl,
                            repoName = "IzzyOnDroid",
                            packages = listOf(
                                packageSnapshot("dev.imranr.obtainium", "Obtainium", "App updater")
                            )
                        )
                    )
                } else {
                    Result.failure(IllegalStateException("repo offline"))
                }
            },
            clock = { nowMillis }
        )

        val result = service.search(
            FdroidAppSearchRequest(
                query = "Obtainium",
                repoUrls = listOf(
                    FdroidRepositoryPresets.IzzyOnDroid.repoUrl,
                    "https://offline.example/fdroid/repo"
                )
            )
        ).getOrThrow()

        assertEquals(2, result.repoReports.size)
        assertEquals(
            listOf(FdroidRepositoryPresets.IzzyOnDroid.repoUrl, "https://offline.example/fdroid/repo"),
            result.repoReports.map { report -> report.repoUrl }
        )
        assertEquals(listOf(1, 0), result.repoReports.map { report -> report.candidateCount })
        assertEquals(listOf(0, 1), result.repoReports.map { report -> report.failureCount })
        assertTrue(result.repoReports.all { report -> report.elapsedMillis > 0L })
    }

    @Test
    fun `search rethrows cancellation from repository index provider`() {
        runBlocking {
            val service = FdroidAppSearchService(
                searchApiClient = fakeSearchApiClient(apps = emptyList()),
                repositorySearchProvider = FdroidRepositorySearchProvider { _, _, _, _, _ ->
                    throw CancellationException("cancelled")
                },
                dispatcher = Dispatchers.Unconfined
            )

            assertFailsWith<CancellationException> {
                service.search(
                    FdroidAppSearchRequest(
                        query = "PixEz",
                        repoUrls = listOf(FdroidRepositoryPresets.IzzyOnDroid.repoUrl)
                    )
                )
            }
        }
    }

    private fun fakeSearchApiClient(apps: List<FdroidSearchApiApp>): FdroidSearchApiClient {
        return object : FdroidSearchApiClient() {
            override suspend fun searchApps(
                query: String,
                limit: Int
            ): Result<List<FdroidSearchApiApp>> {
                return Result.success(apps.take(limit))
            }
        }
    }

    private fun failingSearchApiClient(): FdroidSearchApiClient {
        return object : FdroidSearchApiClient() {
            override suspend fun searchApps(
                query: String,
                limit: Int
            ): Result<List<FdroidSearchApiApp>> {
                return Result.failure(IllegalStateException("official search unavailable"))
            }
        }
    }

    private fun emptyRepositorySearchProvider(): FdroidRepositorySearchProvider =
        FdroidRepositorySearchProvider { repoUrl, _, _, _, _ ->
            Result.failure(IllegalStateException("No fixture for $repoUrl"))
        }

    private fun repositorySearchProvider(
        vararg snapshots: Pair<String, FdroidRepositorySnapshot>
    ): FdroidRepositorySearchProvider {
        val byUrl = snapshots.associateBy(
            keySelector = { (url, _) -> url },
            valueTransform = { (_, snapshot) -> snapshot }
        )
        return FdroidRepositorySearchProvider { repoUrl, _, _, _, _ ->
            byUrl[repoUrl]?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("No fixture for $repoUrl"))
        }
    }

    private fun repository(
        repoUrl: String,
        repoName: String,
        packages: List<FdroidPackageSnapshot>
    ): FdroidRepositorySnapshot {
        return FdroidRepositorySnapshot(
            repoUrl = repoUrl,
            format = FdroidIndexFormat.V2,
            repoName = repoName,
            repoDescription = "",
            timestampMillis = 1780000000000,
            mirrors = emptyList(),
            packages = packages.associateBy { snapshot -> snapshot.packageName }
        )
    }

    private fun packageSnapshot(
        packageName: String,
        appName: String,
        summary: String
    ): FdroidPackageSnapshot {
        return FdroidPackageSnapshot(
            repoUrl = "",
            packageName = packageName,
            suggestedVersionCode = 100,
            appName = appName,
            summary = summary,
            categories = listOf("Internet"),
            versions = listOf(
                fdroidVersionFixture(versionCode = 100, versionName = "1.0", apkName = "${packageName}_100.apk")
            )
        )
    }
}
