package os.kei.feature.github.domain

import org.junit.Test
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.InstalledAppItem
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubPackageRepositoryResolverTest {
    @Test
    fun `resolver confirms repository by scanning latest stable apk package name`() {
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
        val discovery = FakeDiscoverySource(listOf(target, mismatch, failed, target))
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
        assertEquals("LibChecker", result.appLabel)
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
        assertEquals("LibChecker", result.matchedCandidates.single().trackedApp.appLabel)
        assertEquals("v1.0.0", result.matchedCandidates.single().releaseTag)
        assertEquals("LibChecker.apk", result.matchedCandidates.single().assetName)
        assertEquals(1, result.mismatchedCandidateCount)
        assertEquals(1, result.failedCandidateCount)
        assertEquals(
            List(result.scannedCandidateCount) { GitHubLookupStrategyOption.GitHubApiToken },
            scanSource.scannedStrategies
        )
        assertTrue(discovery.queries.any { it.contains("com.absinthe.libchecker") })
    }

    @Test
    fun `resolver rejects invalid package name before repository search`() {
        val discovery = FakeDiscoverySource(emptyList())
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
    fun `resolver keeps atom lookup config while scanning package candidates`() {
        val discovery = FakeDiscoverySource(
            listOf(
                candidate(
                    owner = "owner",
                    repo = "app",
                    description = "com.example.app android",
                    stars = 10
                )
            )
        )
        val scanSource = FakePackageScanSource(
            packagesByRepo = mapOf("owner/app" to "com.example.app")
        )
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = discovery,
            packageNameScanner = GitHubApkPackageNameScanner(scanSource)
        )

        val result = resolver.scanRepositoriesForPackage(
            GitHubPackageRepositoryScanRequest(
                packageName = "com.example.app",
                lookupConfig = GitHubLookupConfig(
                    selectedStrategy = GitHubLookupStrategyOption.AtomFeed
                ),
                verificationLimit = 1
            )
        ).getOrThrow()

        assertEquals(1, result.matchedCandidates.size)
        assertEquals(GitHubLookupStrategyOption.AtomFeed, scanSource.scannedStrategies.single())
    }

    @Test
    fun `resolver verifies preferred repository before live search`() {
        val discovery = FakeDiscoverySource(emptyList())
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
    fun `resolver falls back to repository search after preferred repository mismatch`() {
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
    fun `resolver expands to fallback queries when exact package candidates do not match`() {
        val mismatch = candidate(
            owner = "demo",
            repo = "package-name-docs",
            description = "Mentions com.example.realapp",
            stars = 50
        )
        val target = candidate(
            owner = "example",
            repo = "RealApp",
            description = "RealApp android client",
            stars = 200
        )
        val discovery = QueryAwareDiscoverySource { query ->
            when {
                query.contains("com.example.realapp") -> listOf(mismatch)
                query.contains("RealApp") && query.contains("realapp") -> listOf(target)
                else -> emptyList()
            }
        }
        val scanSource = FakePackageScanSource(
            packagesByRepo = mapOf(
                "demo/package-name-docs" to "com.other.app",
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
                lookupConfig = GitHubLookupConfig()
            )
        ).getOrThrow()

        assertEquals(2, result.queryCount)
        assertEquals(2, result.scannedCandidateCount)
        assertEquals("example", result.matchedCandidates.single().repository.owner)
        assertEquals("RealApp", result.matchedCandidates.single().repository.repo)
    }

    @Test
    fun `package repository queries use installed app label and package tail`() {
        val queries = GitHubPackageRepositoryQueries.forInstalledApp(
            InstalledAppItem(
                label = "My Great App",
                packageName = "io.github.owner.greatapp"
            )
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

    private class FakeDiscoverySource(
        private val candidates: List<GitHubRepositoryCandidate>
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
            return Result.success(candidates.take(limit))
        }
    }

    private class QueryAwareDiscoverySource(
        private val candidatesForQuery: (String) -> List<GitHubRepositoryCandidate>
    ) : GitHubRepositoryDiscoverySource {
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
            return Result.success(candidatesForQuery(query).take(limit))
        }
    }

    private class FakePackageScanSource(
        private val packagesByRepo: Map<String, String>
    ) : GitHubApkPackageNameScanSource {
        val scannedStrategies: MutableList<GitHubLookupStrategyOption> =
            Collections.synchronizedList(mutableListOf())

        override fun loadLatestStableRelease(
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

        override fun fetchApkAssets(
            owner: String,
            repo: String,
            release: GitHubStableReleaseTarget,
            lookupConfig: GitHubLookupConfig
        ): Result<List<GitHubReleaseAssetFile>> {
            return Result.success(
                listOf(
                    GitHubReleaseAssetFile(
                        name = "${repo}.apk",
                        downloadUrl = "https://github.com/$owner/$repo/releases/download/${release.tag}/${repo}.apk",
                        apiAssetUrl = "https://api.github.com/repos/$owner/$repo/releases/assets/1",
                        sizeBytes = 1024L,
                        downloadCount = 1
                    )
                )
            )
        }

        override fun readAndroidManifestBytes(
            asset: GitHubReleaseAssetFile,
            lookupConfig: GitHubLookupConfig
        ): Result<ByteArray> = runCatching {
            scannedStrategies += lookupConfig.selectedStrategy
            val repoKey = asset.downloadUrl
                .substringAfter("https://github.com/")
                .substringBefore("/releases/")
                .lowercase()
            val packageName = packagesByRepo[repoKey]
                ?: error("No package fixture for $repoKey")
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
