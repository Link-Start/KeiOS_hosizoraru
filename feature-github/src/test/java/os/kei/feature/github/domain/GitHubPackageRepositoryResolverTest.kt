package os.kei.feature.github.domain

import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubPackageRepositoryResolverTest {
    @Test
    fun `fixed repository discovery corpus bounds query cost without losing matches`() = runBlocking {
        val cases = listOf(
            RepositoryDiscoveryCorpusCase(
                name = "exact-package-libchecker",
                packageName = "com.absinthe.libchecker",
                appLabel = "LibChecker",
                expectedRepoKey = "absinthe/libchecker",
                candidatesForQuery = { query ->
                    if (query.contains("com.absinthe.libchecker")) {
                        listOf(
                            candidate(
                                owner = "Absinthe",
                                repo = "LibChecker",
                                description = "Android app com.absinthe.libchecker",
                                stars = 12_000,
                            ),
                        )
                    } else {
                        emptyList()
                    }
                },
                packagesByRepo = mapOf("absinthe/libchecker" to "com.absinthe.libchecker"),
                maxQueryCount = 1,
            ),
            RepositoryDiscoveryCorpusCase(
                name = "readme-false-positive-fallback",
                packageName = "com.example.realapp",
                appLabel = "RealApp",
                expectedRepoKey = "example/realapp",
                candidatesForQuery = { query ->
                    when {
                        query.contains("com.example.realapp") -> listOf(
                            candidate(
                                owner = "demo",
                                repo = "package-name-docs",
                                description = "Mentions com.example.realapp",
                                stars = 50,
                            ),
                        )
                        query.contains("RealApp") && query.contains("realapp") -> listOf(
                            candidate(
                                owner = "example",
                                repo = "RealApp",
                                description = "RealApp Android client",
                                stars = 200,
                            ),
                        )
                        else -> emptyList()
                    }
                },
                packagesByRepo = mapOf(
                    "demo/package-name-docs" to "com.other.app",
                    "example/realapp" to "com.example.realapp",
                ),
                maxQueryCount = 2,
                expectedMismatchCount = 1,
            ),
            RepositoryDiscoveryCorpusCase(
                name = "separator-neutral-hma",
                packageName = "org.frknkrc44.hma_oss",
                appLabel = "HMA",
                expectedRepoKey = "frknkrc44/hma-oss",
                candidatesForQuery = { query ->
                    if (query == "hma oss in:name,description,readme") {
                        listOf(
                            candidate(
                                owner = "frknkrc44",
                                repo = "HMA-OSS",
                                description = "Hide My Applist Android module",
                                stars = 1_821,
                            ),
                        )
                    } else {
                        emptyList()
                    }
                },
                packagesByRepo = mapOf("frknkrc44/hma-oss" to "org.frknkrc44.hma_oss"),
                maxQueryCount = 2,
            ),
        )

        var totalQueries = 0
        var totalScans = 0
        cases.forEach { corpusCase ->
            val discovery = QueryAwareDiscoverySource(
                candidatesForQuery = corpusCase.candidatesForQuery,
            )
            val resolver = GitHubPackageRepositoryResolver(
                discoverySource = discovery,
                packageNameScanner = GitHubApkPackageNameScanner(
                    FakePackageScanSource(corpusCase.packagesByRepo),
                ),
            )

            val result = resolver.scanRepositoriesForPackage(
                GitHubPackageRepositoryScanRequest(
                    packageName = corpusCase.packageName,
                    appLabel = corpusCase.appLabel,
                    preferredRepoUrl = corpusCase.preferredRepoUrl,
                    lookupConfig = GitHubLookupConfig(),
                    candidateLimit = 10,
                    verificationLimit = 3,
                ),
            ).getOrThrow()

            val match = result.matchedCandidates.single()
            assertEquals(
                corpusCase.expectedRepoKey,
                "${match.repository.owner.lowercase()}/${match.repository.repo.lowercase()}",
                corpusCase.name,
            )
            assertTrue(result.queryCount <= corpusCase.maxQueryCount, corpusCase.name)
            assertEquals(corpusCase.expectedMismatchCount, result.mismatchedCandidateCount)
            totalQueries += result.queryCount
            totalScans += result.scannedCandidateCount
        }

        assertTrue(totalQueries <= 5)
        assertTrue(totalScans <= 5)
    }

    @Test
    fun `resolver confirms repository by scanning latest stable apk package name`() = runBlocking {
        val target = candidate(
            owner = "Absinthe",
            repo = "LibChecker",
            description = "Android app com.absinthe.libchecker",
            stars = 12_000
        )
        val mismatch = candidate(
            owner = "demo",
            repo = "libchecker-theme",
            description = "Mentions com.absinthe.libchecker",
            stars = 40
        )
        val failed = candidate(
            owner = "demo",
            repo = "libchecker-docs",
            description = "LibChecker android docs",
            stars = 1
        )
        val discovery = QueryAwareDiscoverySource { listOf(target, mismatch, failed, target) }
        val scanSource = FakePackageScanSource(
            packagesByRepo = mapOf(
                "absinthe/libchecker" to "com.absinthe.libchecker",
                "demo/libchecker-theme" to "com.example.theme"
            )
        )
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = discovery,
            packageNameScanner = GitHubApkPackageNameScanner(scanSource)
        )

        val result = resolver.scanRepositoriesForPackage(
            GitHubPackageRepositoryScanRequest(
                packageName = "com.absinthe.libchecker",
                appLabel = "LibChecker",
                lookupConfig = GitHubLookupConfig(
                    selectedStrategy = GitHubLookupStrategyOption.GitHubApiToken,
                    apiToken = "token-123"
                ),
                candidateLimit = 10,
                verificationLimit = 3
            )
        ).getOrThrow()

        assertEquals("com.absinthe.libchecker", result.packageName)
        assertEquals(1, result.queryCount)
        assertEquals(3, result.fetchedCandidateCount)
        assertEquals(3, result.scannedCandidateCount)
        assertEquals(1, result.matchedCandidates.size)
        assertEquals("Absinthe", result.matchedCandidates.single().repository.owner)
        assertEquals("LibChecker", result.matchedCandidates.single().repository.repo)
        assertEquals(
            "com.absinthe.libchecker",
            result.matchedCandidates.single().trackedApp.packageName
        )
        assertEquals("v1.0.0", result.matchedCandidates.single().releaseTag)
        assertEquals("LibChecker.apk", result.matchedCandidates.single().assetName)
        assertEquals(1, result.mismatchedCandidateCount)
        assertEquals(1, result.failedCandidateCount)
        // The caller's lookup strategy (token or public) must reach every manifest read.
        assertTrue(scanSource.scannedStrategies.isNotEmpty())
        assertTrue(scanSource.scannedStrategies.all { it == GitHubLookupStrategyOption.GitHubApiToken })
        assertTrue(discovery.queries.any { it.contains("com.absinthe.libchecker") })
    }

    @Test
    fun `resolver keeps multiple package-matched repositories for user choice`() = runBlocking {
        val official = candidate(
            owner = "Absinthe",
            repo = "LibChecker",
            description = "Android app com.absinthe.libchecker",
            stars = 12_000
        )
        val fork = candidate(
            owner = "forker",
            repo = "LibChecker",
            description = "LibChecker fork for com.absinthe.libchecker",
            stars = 240
        ).copy(fork = true)
        val discovery = QueryAwareDiscoverySource { listOf(fork, official) }
        val scanSource = FakePackageScanSource(
            packagesByRepo = mapOf(
                "absinthe/libchecker" to "com.absinthe.libchecker",
                "forker/libchecker" to "com.absinthe.libchecker"
            )
        )
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = discovery,
            packageNameScanner = GitHubApkPackageNameScanner(scanSource)
        )

        val result = resolver.scanRepositoriesForPackage(
            GitHubPackageRepositoryScanRequest(
                packageName = "com.absinthe.libchecker",
                appLabel = "LibChecker",
                lookupConfig = GitHubLookupConfig(),
                candidateLimit = 10,
                verificationLimit = 4
            )
        ).getOrThrow()

        assertEquals(2, result.matchedCandidates.size)
        assertEquals(
            listOf("Absinthe/LibChecker", "forker/LibChecker"),
            result.matchedCandidates.map { it.repository.fullName }
        )
        assertTrue(result.matchedCandidates[1].repository.fork)
    }

    @Test
    fun `resolver verifies target package when release publishes multiple app variants`() = runBlocking {
        val project = candidate(
            owner = "hosizoraru",
            repo = "KeiOS",
            description = "KeiOS android os.kei",
            stars = 100
        )
        val discovery = QueryAwareDiscoverySource { listOf(project) }
        val scanSource = FakePackageScanSource(
            packagesByRepo = emptyMap(),
            packageVariantsByRepo = mapOf(
                "hosizoraru/keios" to listOf(
                    "os.kei",
                    "os.kei.benchmark",
                    "os.kei.debug"
                )
            )
        )
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = discovery,
            packageNameScanner = GitHubApkPackageNameScanner(scanSource)
        )

        val result = resolver.scanRepositoriesForPackage(
            GitHubPackageRepositoryScanRequest(
                packageName = "os.kei.benchmark",
                appLabel = "KeiOS Benchmark",
                preferredRepoUrl = "https://github.com/hosizoraru/KeiOS",
                lookupConfig = GitHubLookupConfig()
            )
        ).getOrThrow()

        val matched = result.matchedCandidates.single()
        assertEquals("hosizoraru", matched.repository.owner)
        assertEquals("KeiOS", matched.repository.repo)
        assertEquals("os.kei.benchmark", matched.trackedApp.packageName)
        assertEquals("KeiOS-os.kei.benchmark.apk", matched.assetName)
    }

    @Test
    fun `resolver rejects invalid package name before repository search`() = runBlocking {
        val discovery = QueryAwareDiscoverySource { emptyList() }
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = discovery,
            packageNameScanner = GitHubApkPackageNameScanner(FakePackageScanSource(emptyMap()))
        )

        val message = resolver.scanRepositoriesForPackage(
            GitHubPackageRepositoryScanRequest(
                packageName = "bad package",
                appLabel = "Bad",
                lookupConfig = GitHubLookupConfig()
            )
        ).exceptionOrNull()?.message.orEmpty()

        assertTrue(message.contains("Invalid package name"))
        assertTrue(discovery.queries.isEmpty())
    }

    @Test
    fun `resolver verifies preferred repository before live search`() = runBlocking {
        val discovery = QueryAwareDiscoverySource { emptyList() }
        val scanSource = FakePackageScanSource(
            packagesByRepo = mapOf("yukonga/updater-kmp" to "top.yukonga.updater.kmp")
        )
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = discovery,
            packageNameScanner = GitHubApkPackageNameScanner(scanSource)
        )

        val result = resolver.scanRepositoriesForPackage(
            GitHubPackageRepositoryScanRequest(
                packageName = "top.yukonga.updater.kmp",
                appLabel = "Updater",
                preferredRepoUrl = "https://github.com/YuKongA/Updater-KMP",
                lookupConfig = GitHubLookupConfig()
            )
        ).getOrThrow()

        assertEquals(0, result.queryCount)
        assertEquals(1, result.fetchedCandidateCount)
        assertEquals(1, result.scannedCandidateCount)
        assertTrue(discovery.queries.isEmpty())
        assertEquals("YuKongA", result.matchedCandidates.single().repository.owner)
        assertEquals("Updater-KMP", result.matchedCandidates.single().repository.repo)
        assertEquals(
            GitHubRepositoryDiscoverySourceType.PreferredRepository,
            result.matchedCandidates.single().repository.sourceType
        )
        assertEquals(
            "https://github.com/YuKongA/Updater-KMP",
            result.matchedCandidates.single().trackedApp.repoUrl
        )
    }

    @Test
    fun `resolver falls back to repository search after preferred repository mismatch`() = runBlocking {
        val target = candidate(
            owner = "example",
            repo = "RealApp",
            description = "RealApp android client",
            stars = 200
        )
        val discovery = QueryAwareDiscoverySource { query ->
            when {
                query.contains("RealApp") && query.contains("realapp") -> listOf(target)
                else -> emptyList()
            }
        }
        val scanSource = FakePackageScanSource(
            packagesByRepo = mapOf(
                "wrong/project" to "com.wrong.app",
                "example/realapp" to "com.example.realapp"
            )
        )
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = discovery,
            packageNameScanner = GitHubApkPackageNameScanner(scanSource)
        )

        val result = resolver.scanRepositoriesForPackage(
            GitHubPackageRepositoryScanRequest(
                packageName = "com.example.realapp",
                appLabel = "RealApp",
                preferredRepoUrl = "https://github.com/wrong/project",
                lookupConfig = GitHubLookupConfig()
            )
        ).getOrThrow()

        assertEquals(2, result.queryCount)
        assertEquals(2, result.scannedCandidateCount)
        assertEquals(1, result.mismatchedCandidateCount)
        assertEquals("example", result.matchedCandidates.single().repository.owner)
        assertEquals("RealApp", result.matchedCandidates.single().repository.repo)
    }

    @Test
    fun `resolver keeps scanning when one repository search query fails`() = runBlocking {
        val target = candidate(
            owner = "example",
            repo = "RealApp",
            description = "RealApp android client",
            stars = 200
        )
        val discovery = QueryAwareDiscoverySource(
            failureForQuery = { query ->
                if (query.contains("com.example.realapp")) {
                    IllegalStateException("failed exact package query")
                } else {
                    null
                }
            },
            candidatesForQuery = { query ->
                when {
                    query.contains("RealApp") && query.contains("realapp") -> listOf(target)
                    else -> emptyList()
                }
            }
        )
        val scanSource = FakePackageScanSource(
            packagesByRepo = mapOf("example/realapp" to "com.example.realapp")
        )
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = discovery,
            packageNameScanner = GitHubApkPackageNameScanner(scanSource)
        )

        val result = resolver.scanRepositoriesForPackage(
            GitHubPackageRepositoryScanRequest(
                packageName = "com.example.realapp",
                appLabel = "RealApp",
                lookupConfig = GitHubLookupConfig()
            )
        ).getOrThrow()

        assertEquals(2, result.queryCount)
        assertEquals("example", result.matchedCandidates.single().repository.owner)
    }

    @Test
    fun `resolver throws first search error when every repository query fails`() = runBlocking {
        val discovery = QueryAwareDiscoverySource(
            failureForQuery = { query -> IllegalStateException("failed $query") },
            candidatesForQuery = { emptyList() }
        )
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = discovery,
            packageNameScanner = GitHubApkPackageNameScanner(FakePackageScanSource(emptyMap()))
        )

        val error = assertFailsWith<IllegalStateException> {
            resolver.scanRepositoriesForPackage(
                GitHubPackageRepositoryScanRequest(
                    packageName = "com.example.realapp",
                    appLabel = "RealApp",
                    lookupConfig = GitHubLookupConfig()
                )
            ).getOrThrow()
        }

        assertTrue(error.message.orEmpty().contains("com.example.realapp"))
    }

    @Test
    fun `package repository queries use app label and package tail`() {
        val queries = GitHubPackageRepositoryQueries.forPackage(
            packageName = "io.github.owner.greatapp",
            appLabel = "My Great App"
        )

        assertTrue(queries.any { it.contains("io.github.owner.greatapp") })
        assertTrue(queries.any { it.contains("My Great App android") })
        assertTrue(queries.any { it.contains("greatapp android") })
        assertFalse(queries.any { it.isBlank() })
        assertEquals(queries.distinct(), queries)
    }

    @Test
    fun `package repository queries combine app label with package suffix tokens`() {
        val queries = GitHubPackageRepositoryQueries.forPackage(
            packageName = "top.yukonga.updater.kmp",
            appLabel = "Updater"
        )

        assertTrue(queries.any { it.contains("Updater kmp android") })
        assertTrue(queries.any { it.contains("yukonga updater kmp android") })
        assertEquals(queries.distinct(), queries)
    }

    @Test
    fun `package repository queries include separator neutral package tail tokens`() {
        val queries = GitHubPackageRepositoryQueries.forPackage(
            packageName = "org.frknkrc44.hma_oss",
            appLabel = "HMA"
        )

        assertTrue(queries.any { it.contains("HMA hma oss android") })
        assertTrue(queries.any { it == "hma oss in:name,description,readme" })
        assertTrue(queries.any { it.contains("frknkrc44 hma oss android") })
        assertTrue(queries.any { it.contains("hma oss android") })
        assertTrue(queries.any { it.contains("hma_oss android") })
        assertEquals(queries.distinct(), queries)
    }

    private data class RepositoryDiscoveryCorpusCase(
        val name: String,
        val packageName: String,
        val appLabel: String,
        val preferredRepoUrl: String = "",
        val expectedRepoKey: String,
        val candidatesForQuery: (String) -> List<GitHubRepositoryCandidate>,
        val packagesByRepo: Map<String, String>,
        val maxQueryCount: Int,
        val expectedMismatchCount: Int = 0,
    )

    private class QueryAwareDiscoverySource(
        private val failureForQuery: (String) -> Throwable? = { null },
        private val candidatesForQuery: (String) -> List<GitHubRepositoryCandidate>
    ) : GitHubRepositoryDiscoverySource {
        val queries = mutableListOf<String>()

        override fun fetchAuthenticatedStarredRepositories(
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> {
            return Result.success(emptyList())
        }

        override fun fetchUserStarredRepositories(
            username: String,
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> {
            return Result.success(emptyList())
        }

        override fun searchRepositories(
            query: String,
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> {
            queries += query
            failureForQuery(query)?.let { error ->
                return Result.failure(error)
            }
            return Result.success(candidatesForQuery(query).take(limit))
        }

        override fun fetchStarListRepositories(
            starListUrl: String,
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> {
            return Result.success(emptyList())
        }
    }

    private class FakePackageScanSource(
        packagesByRepo: Map<String, String>,
        private val packageVariantsByRepo: Map<String, List<String>> =
            packagesByRepo.mapValues { (_, packageName) -> listOf(packageName) }
    ) : GitHubApkPackageNameScanSource {
        val scannedStrategies: MutableList<GitHubLookupStrategyOption> =
            Collections.synchronizedList(mutableListOf())

        override suspend fun loadLatestStableRelease(
            owner: String,
            repo: String,
            lookupConfig: GitHubLookupConfig
        ): Result<GitHubStableReleaseTarget> {
            return Result.success(
                GitHubStableReleaseTarget(
                    tag = "v1.0.0",
                    releaseUrl = "https://github.com/$owner/$repo/releases/tag/v1.0.0"
                )
            )
        }

        override suspend fun fetchApkAssets(
            owner: String,
            repo: String,
            release: GitHubStableReleaseTarget,
            lookupConfig: GitHubLookupConfig
        ): Result<List<GitHubReleaseAssetFile>> {
            val repoKey = "${owner.lowercase()}/${repo.lowercase()}"
            val packages = packageVariantsByRepo[repoKey].orEmpty()
            return Result.success(
                packages.mapIndexed { index, packageName ->
                    val assetName = if (packages.size == 1) {
                        "$repo.apk"
                    } else {
                        "$repo-$packageName.apk"
                    }
                    GitHubReleaseAssetFile(
                        name = assetName,
                        downloadUrl = "https://github.com/$owner/$repo/releases/download/${release.tag}/$assetName",
                        apiAssetUrl = "https://api.github.com/repos/$owner/$repo/releases/assets/${index + 1}",
                        sizeBytes = 1024L,
                        downloadCount = 1
                    )
                }
            )
        }

        override suspend fun readAndroidManifestBytes(
            asset: GitHubReleaseAssetFile,
            lookupConfig: GitHubLookupConfig
        ): Result<ByteArray> = runCatching {
            scannedStrategies += lookupConfig.selectedStrategy
            val repoKey = asset.downloadUrl
                .substringAfter("https://github.com/")
                .substringBefore("/releases/")
                .lowercase()
            val repoName = repoKey.substringAfter('/')
            val packages = packageVariantsByRepo[repoKey].orEmpty()
            val packageName = when (packages.size) {
                1 -> packages.single()
                else -> packages.firstOrNull { packageName ->
                    asset.name.equals("$repoName-$packageName.apk", ignoreCase = true)
                }
            }
                ?: error("No package fixture for ${asset.name}")
            BinaryManifestFixture.build(packageName)
        }
    }

    private fun candidate(
        owner: String,
        repo: String,
        description: String,
        stars: Int
    ): GitHubRepositoryCandidate {
        return GitHubRepositoryCandidate(
            owner = owner,
            repo = repo,
            repoUrl = "https://github.com/$owner/$repo",
            description = description,
            language = "Kotlin",
            starCount = stars,
            forkCount = 0,
            archived = false,
            fork = false,
            updatedAtMillis = 1_700_000_000_000,
            sourceType = GitHubRepositoryDiscoverySourceType.RepositorySearch,
            matchReason = GitHubRepositoryCandidateMatchReason.RepositoryName
        )
    }
}
