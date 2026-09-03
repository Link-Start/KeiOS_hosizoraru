package os.kei.feature.github.domain.fdroid

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.data.remote.fdroid.FdroidPackageSnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidRepositorySnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidVersionSnapshot
import os.kei.feature.github.model.FdroidIndexFormat
import os.kei.feature.github.model.FdroidTrustPolicy
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedSourceMode
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FdroidBatchPackageSnapshotProviderTest {
    @Test
    fun `loadPackageSnapshot fans out same repo packages from one repository snapshot`() = runBlocking {
        val repositoryLoads = AtomicInteger(0)
        val provider = FdroidBatchPackageSnapshotProvider(
            trackedItems = listOf(
                fdroidItem("demo.one"),
                fdroidItem("demo.two")
            ),
            packageProvider = FdroidPackageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("package api should not be used"))
            },
            repositoryPackagesProvider = FdroidRepositoryPackagesSnapshotProvider { repoUrl, packageNames, _, _ ->
                repositoryLoads.incrementAndGet()
                delay(30)
                Result.success(
                    repositorySnapshot(
                        repoUrl = repoUrl,
                        packages = packageNames.toList()
                    )
                )
            },
            // These exercise the index and API paths, so the page -- which f-droid.org does have -- is
            // stubbed out rather than reached. The routing itself is covered separately below.
            pageProvider = FdroidPackagePageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("page should not be used"))
            }
        )

        val results = listOf(
            async { provider.loadPackageSnapshot(fdroidItem("demo.one"), forceRefresh = true) },
            async { provider.loadPackageSnapshot(fdroidItem("demo.two"), forceRefresh = true) }
        ).awaitAll()

        assertEquals(1, repositoryLoads.get())
        assertEquals(
            listOf("demo.one", "demo.two"),
            results.map { result -> result.getOrThrow().packageName }
        )
    }

    @Test
    fun `loadPackageSnapshot falls to the package api when neither page nor index answers`() = runBlocking {
        val packageLoads = AtomicInteger(0)
        val provider = FdroidBatchPackageSnapshotProvider(
            trackedItems = listOf(fdroidItem("demo.one")),
            packageProvider = FdroidPackageSnapshotProvider { item, _ ->
                packageLoads.incrementAndGet()
                Result.success(packageSnapshot(item.packageName))
            },
            repositoryPackagesProvider = FdroidRepositoryPackagesSnapshotProvider { _, _, _, _ ->
                Result.failure(IllegalStateException("repository index should not be used"))
            },
            // These exercise the index and API paths, so the page -- which f-droid.org does have -- is
            // stubbed out rather than reached. The routing itself is covered separately below.
            pageProvider = FdroidPackagePageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("page should not be used"))
            }
        )

        val result = provider.loadPackageSnapshot(fdroidItem("demo.one"), forceRefresh = true)

        assertEquals(1, packageLoads.get())
        assertEquals("demo.one", result.getOrThrow().packageName)
    }

    @Test
    fun `loadPackageSnapshot falls back to repository index when package api fails`() = runBlocking {
        val packageLoads = AtomicInteger(0)
        val repositoryLoads = AtomicInteger(0)
        val provider = FdroidBatchPackageSnapshotProvider(
            trackedItems = listOf(fdroidItem("demo.one")),
            packageProvider = FdroidPackageSnapshotProvider { _, _ ->
                packageLoads.incrementAndGet()
                Result.failure(IllegalStateException("package api unavailable"))
            },
            repositoryPackagesProvider = FdroidRepositoryPackagesSnapshotProvider { repoUrl, packageNames, _, _ ->
                repositoryLoads.incrementAndGet()
                Result.success(
                    repositorySnapshot(
                        repoUrl = repoUrl,
                        packages = packageNames.toList()
                    )
                )
            },
            // These exercise the index and API paths, so the page -- which f-droid.org does have -- is
            // stubbed out rather than reached. The routing itself is covered separately below.
            pageProvider = FdroidPackagePageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("page should not be used"))
            }
        )

        val result = provider.loadPackageSnapshot(fdroidItem("demo.one"), forceRefresh = true)

        assertEquals(1, packageLoads.get())
        assertEquals(1, repositoryLoads.get())
        assertEquals("demo.one", result.getOrThrow().packageName)
    }

    @Test
    fun `loadPackageSnapshot shares concurrent package api requests for the same package`() = runBlocking {
        val packageLoads = AtomicInteger(0)
        val provider = FdroidBatchPackageSnapshotProvider(
            trackedItems = listOf(fdroidItem("demo.one")),
            packageProvider = FdroidPackageSnapshotProvider { item, _ ->
                packageLoads.incrementAndGet()
                delay(30)
                Result.success(packageSnapshot(item.packageName))
            },
            repositoryPackagesProvider = FdroidRepositoryPackagesSnapshotProvider { _, _, _, _ ->
                Result.failure(IllegalStateException("repository index should not be used"))
            },
            // These exercise the index and API paths, so the page -- which f-droid.org does have -- is
            // stubbed out rather than reached. The routing itself is covered separately below.
            pageProvider = FdroidPackagePageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("page should not be used"))
            }
        )

        val results = listOf(
            async { provider.loadPackageSnapshot(fdroidItem("demo.one"), forceRefresh = true) },
            async { provider.loadPackageSnapshot(fdroidItem("demo.one"), forceRefresh = true) },
            async { provider.loadPackageSnapshot(fdroidItem("demo.one"), forceRefresh = true) }
        ).awaitAll()

        assertEquals(1, packageLoads.get())
        assertEquals(
            listOf("demo.one", "demo.one", "demo.one"),
            results.map { result -> result.getOrThrow().packageName }
        )
    }

    @Test
    fun `loadPackageSnapshot shares concurrent repository index requests for same repo`() = runBlocking {
        val repositoryLoads = AtomicInteger(0)
        val provider = FdroidBatchPackageSnapshotProvider(
            trackedItems = listOf(
                fdroidItem("demo.one"),
                fdroidItem("demo.two")
            ),
            packageProvider = FdroidPackageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("package api should not be used"))
            },
            repositoryPackagesProvider = FdroidRepositoryPackagesSnapshotProvider { repoUrl, packageNames, _, _ ->
                repositoryLoads.incrementAndGet()
                delay(30)
                Result.success(
                    repositorySnapshot(
                        repoUrl = repoUrl,
                        packages = packageNames.toList()
                    )
                )
            },
            // These exercise the index and API paths, so the page -- which f-droid.org does have -- is
            // stubbed out rather than reached. The routing itself is covered separately below.
            pageProvider = FdroidPackagePageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("page should not be used"))
            }
        )

        val results = listOf(
            async { provider.loadPackageSnapshot(fdroidItem("demo.one"), forceRefresh = true) },
            async { provider.loadPackageSnapshot(fdroidItem("demo.two"), forceRefresh = true) }
        ).awaitAll()

        assertEquals(1, repositoryLoads.get())
        assertEquals(
            listOf("demo.one", "demo.two"),
            results.map { result -> result.getOrThrow().packageName }
        )
    }

    @Test
    fun `loadPackageSnapshot force refresh bypasses completed package cache`() = runBlocking {
        val packageLoads = AtomicInteger(0)
        val provider = FdroidBatchPackageSnapshotProvider(
            trackedItems = listOf(fdroidItem("demo.one")),
            packageProvider = FdroidPackageSnapshotProvider { item, _ ->
                val load = packageLoads.incrementAndGet()
                Result.success(packageSnapshot(item.packageName, versionName = "1.$load"))
            },
            repositoryPackagesProvider = FdroidRepositoryPackagesSnapshotProvider { _, _, _, _ ->
                Result.failure(IllegalStateException("repository index should not be used"))
            },
            // These exercise the index and API paths, so the page -- which f-droid.org does have -- is
            // stubbed out rather than reached. The routing itself is covered separately below.
            pageProvider = FdroidPackagePageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("page should not be used"))
            }
        )

        val cached = provider.loadPackageSnapshot(fdroidItem("demo.one"), forceRefresh = false)
        val cachedAgain = provider.loadPackageSnapshot(fdroidItem("demo.one"), forceRefresh = false)
        val refreshed = provider.loadPackageSnapshot(fdroidItem("demo.one"), forceRefresh = true)

        assertEquals(2, packageLoads.get())
        assertEquals("1.1", cached.getOrThrow().versions.single().versionName)
        assertEquals("1.1", cachedAgain.getOrThrow().versions.single().versionName)
        assertEquals("1.2", refreshed.getOrThrow().versions.single().versionName)
    }

    @Test
    fun `loadPackageSnapshot force refresh bypasses completed repository cache`() = runBlocking {
        val repositoryLoads = AtomicInteger(0)
        val provider = FdroidBatchPackageSnapshotProvider(
            trackedItems = listOf(
                fdroidItem("demo.one"),
                fdroidItem("demo.two")
            ),
            packageProvider = FdroidPackageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("package api should not be used"))
            },
            repositoryPackagesProvider = FdroidRepositoryPackagesSnapshotProvider { repoUrl, packageNames, _, _ ->
                val load = repositoryLoads.incrementAndGet()
                Result.success(
                    repositorySnapshot(
                        repoUrl = repoUrl,
                        packages = packageNames.toList(),
                        versionName = "1.$load"
                    )
                )
            },
            // These exercise the index and API paths, so the page -- which f-droid.org does have -- is
            // stubbed out rather than reached. The routing itself is covered separately below.
            pageProvider = FdroidPackagePageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("page should not be used"))
            }
        )

        val cached = provider.loadPackageSnapshot(fdroidItem("demo.one"), forceRefresh = false)
        val cachedAgain = provider.loadPackageSnapshot(fdroidItem("demo.two"), forceRefresh = false)
        val refreshed = provider.loadPackageSnapshot(fdroidItem("demo.one"), forceRefresh = true)

        assertEquals(2, repositoryLoads.get())
        assertEquals("1.1", cached.getOrThrow().versions.single().versionName)
        assertEquals("1.1", cachedAgain.getOrThrow().versions.single().versionName)
        assertEquals("1.2", refreshed.getOrThrow().versions.single().versionName)
    }

    @Test
    fun `a repository with a package page is read from it rather than from its index`() = runBlocking {
        // The change this replaced a heuristic with: four or more tracked packages in one repository used
        // to read index-v2 unconditionally, so tracking four f-droid.org apps meant pulling ~58 MB every
        // refresh cycle. Four pages is about 150 KB, and a page carries the size, date, ABIs and minSdk
        // the thin API does not.
        val pageLoads = AtomicInteger(0)
        val provider = FdroidBatchPackageSnapshotProvider(
            trackedItems = (1..4).map { index -> fdroidItem("demo.$index") },
            packageProvider = FdroidPackageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("package api should not be used"))
            },
            repositoryPackagesProvider = FdroidRepositoryPackagesSnapshotProvider { _, _, _, _ ->
                Result.failure(IllegalStateException("index should not be downloaded for a paged repo"))
            },
            pageProvider = FdroidPackagePageSnapshotProvider { _, packageName ->
                pageLoads.incrementAndGet()
                Result.success(packageSnapshot(packageName))
            }
        )

        val result = provider.loadPackageSnapshot(fdroidItem("demo.1"), forceRefresh = true)

        assertEquals("demo.1", result.getOrThrow().packageName)
        assertEquals(1, pageLoads.get())
    }

    @Test
    fun `a trust policy that needs an apk hash reads the index even though a page exists`() = runBlocking {
        // The one case where a background refresh must spend the bytes: no cheaper source publishes a
        // hash or a signer, and FdroidReleaseCheckSource fails the check outright without them.
        val repositoryLoads = AtomicInteger(0)
        val hashRequired = fdroidItem("demo.one").let { item ->
            item.copy(fdroidConfig = item.fdroidConfig.copy(trustPolicy = FdroidTrustPolicy.RequireApkHash))
        }
        val provider = FdroidBatchPackageSnapshotProvider(
            trackedItems = listOf(hashRequired),
            packageProvider = FdroidPackageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("package api should not be used"))
            },
            repositoryPackagesProvider = FdroidRepositoryPackagesSnapshotProvider { repoUrl, packageNames, _, _ ->
                repositoryLoads.incrementAndGet()
                Result.success(repositorySnapshot(repoUrl = repoUrl, packages = packageNames.toList()))
            },
            pageProvider = FdroidPackagePageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("page should not be preferred for a hash policy"))
            }
        )

        val result = provider.loadPackageSnapshot(hashRequired, forceRefresh = true)

        assertEquals(1, repositoryLoads.get())
        assertEquals("demo.one", result.getOrThrow().packageName)
    }

    @Test
    fun `a repository with no page reads its index, under a budget`() = runBlocking {
        // IzzyOnDroid: no page layout this app knows, and a ~14 MB index that carries hashes and signers.
        // The budget has to arrive with the request, or the default is unbounded and f-droid.org's ~58 MB
        // becomes reachable by accident.
        val budgets = mutableListOf<Long>()
        val izzy = fdroidItem("demo.one").copy(
            repoUrl = "https://apt.izzysoft.de/fdroid/repo",
            owner = "apt.izzysoft.de",
            repo = "fdroid-repo"
        )
        val provider = FdroidBatchPackageSnapshotProvider(
            trackedItems = listOf(izzy),
            packageProvider = FdroidPackageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("package api should not be used"))
            },
            repositoryPackagesProvider =
                FdroidRepositoryPackagesSnapshotProvider { repoUrl, packageNames, _, maxIndexBytes ->
                    budgets += maxIndexBytes
                    Result.success(repositorySnapshot(repoUrl = repoUrl, packages = packageNames.toList()))
                },
            pageProvider = FdroidPackagePageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("izzyondroid publishes no page this app reads"))
            }
        )

        val result = provider.loadPackageSnapshot(izzy, forceRefresh = true)

        assertEquals("demo.one", result.getOrThrow().packageName)
        assertEquals(1, budgets.size)
        assertTrue(budgets.single() in 1L..(32L * 1024L * 1024L), "unbudgeted index read: ${budgets.single()}")
    }

    @Test
    fun `an index read asks for every package tracked in that repository`() = runBlocking {
        // So the refresh and the version-history page produce the same cache key and share one download.
        // The index cache is keyed by the set asked for -- ask for one package and the same file is pulled
        // again for the page. See fdroidTrackedPackagesIn.
        val requested = mutableListOf<Set<String>>()
        val izzyItems = (1..3).map { index ->
            fdroidItem("demo.$index").copy(
                repoUrl = "https://apt.izzysoft.de/fdroid/repo",
                owner = "apt.izzysoft.de",
                repo = "fdroid-repo"
            )
        }
        val provider = FdroidBatchPackageSnapshotProvider(
            trackedItems = izzyItems,
            packageProvider = FdroidPackageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("package api should not be used"))
            },
            repositoryPackagesProvider =
                FdroidRepositoryPackagesSnapshotProvider { repoUrl, packageNames, _, _ ->
                    requested += packageNames
                    Result.success(repositorySnapshot(repoUrl = repoUrl, packages = packageNames.toList()))
                },
            pageProvider = FdroidPackagePageSnapshotProvider { _, _ ->
                Result.failure(IllegalStateException("no page"))
            }
        )

        provider.loadPackageSnapshot(izzyItems.first(), forceRefresh = true)

        assertEquals(setOf("demo.1", "demo.2", "demo.3"), requested.single())
    }

    private fun fdroidItem(packageName: String): GitHubTrackedApp {
        return GitHubTrackedApp(
            repoUrl = "https://f-droid.org/repo",
            owner = "f-droid.org",
            repo = "repo",
            packageName = packageName,
            appLabel = packageName,
            sourceMode = GitHubTrackedSourceMode.FdroidRepository
        )
    }

    private fun repositorySnapshot(
        repoUrl: String,
        packages: List<String>,
        versionName: String = "1.0"
    ): FdroidRepositorySnapshot {
        return FdroidRepositorySnapshot(
            repoUrl = repoUrl,
            format = FdroidIndexFormat.V2,
            repoName = "F-Droid",
            repoDescription = "",
            timestampMillis = null,
            mirrors = emptyList(),
            packages = packages.associateWith { packageName ->
                packageSnapshot(packageName, versionName)
            }
        )
    }

    private fun packageSnapshot(
        packageName: String,
        versionName: String = "1.0"
    ): FdroidPackageSnapshot {
        return FdroidPackageSnapshot(
            repoUrl = "https://f-droid.org/repo",
            packageName = packageName,
            suggestedVersionCode = 1L,
            versions = listOf(version(packageName, versionName))
        )
    }

    private fun version(
        packageName: String,
        versionName: String
    ): FdroidVersionSnapshot {
        return FdroidVersionSnapshot(
            versionName = versionName,
            versionCode = 1L,
            apkName = "$packageName.apk",
            apkPath = "/repo/$packageName.apk",
            apkSha256 = "sha256",
            apkSizeBytes = 1L,
            addedAtMillis = null,
            minSdk = 23,
            targetSdk = 37,
            nativeAbis = emptyList(),
            signerSha256 = emptyList(),
            releaseChannels = emptyList(),
            whatsNew = "",
            antiFeatures = emptyList()
        )
    }
}
