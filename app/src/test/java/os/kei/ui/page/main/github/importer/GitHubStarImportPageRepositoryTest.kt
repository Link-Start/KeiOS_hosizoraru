package os.kei.ui.page.main.github.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.domain.GitHubApkPackageNameScanSource
import os.kei.feature.github.domain.GitHubRepositoryDiscoverySource
import os.kei.feature.github.domain.GitHubStableReleaseApkAssets
import os.kei.feature.github.domain.GitHubStableReleaseTarget
import os.kei.feature.github.domain.GitHubStarImportApkVerificationCache
import os.kei.feature.github.domain.GitHubStarImportApkVerifier
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportApkVerification
import os.kei.feature.github.model.GitHubStarImportApkVerificationStatus
import os.kei.feature.github.model.GitHubStarListSummary
import os.kei.feature.github.model.GitHubTrackedApp
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GitHubStarImportPageRepositoryTest {
    @Test
    fun `list overview returns star lists before repository preview`() = runBlocking {
        val repository = GitHubStarImportPageRepository(
            ioDispatcher = Dispatchers.Unconfined,
            snapshotLoader = {
                GitHubTrackSnapshot(
                    lookupConfig = GitHubLookupConfig(apiToken = "token")
                )
            },
            discoverySourceFactory = {
                FakeDiscoverySource(
                    lists = listOf(
                        GitHubStarListSummary(
                            name = "Android",
                            repositoryCount = 2,
                            url = "https://github.com/stars/demo/lists/android"
                        )
                    )
                )
            }
        )

        val result = repository.loadPreview(
            StarImportLoadRequest(
                source = StarImportUiSource.ListUrl,
                usernameInput = "",
                listUrlInput = "https://github.com/stars/demo"
            )
        )

        val lists = assertIs<StarImportLoadResult.Lists>(result)
        assertEquals("Android", lists.items.single().name)
    }

    @Test
    fun `preview reads latest snapshot at action time`() = runBlocking {
        var token = "first"
        val seenTokens = mutableListOf<String>()
        val repository = GitHubStarImportPageRepository(
            ioDispatcher = Dispatchers.Unconfined,
            snapshotLoader = {
                GitHubTrackSnapshot(
                    lookupConfig = GitHubLookupConfig(apiToken = token)
                )
            },
            discoverySourceFactory = { apiToken ->
                seenTokens += apiToken
                FakeDiscoverySource(
                    starred = listOf(repositoryCandidate("demo", "app"))
                )
            }
        )

        repository.loadPreview(
            StarImportLoadRequest(
                source = StarImportUiSource.PublicUser,
                usernameInput = "demo",
                listUrlInput = ""
            )
        )
        token = "second"
        repository.loadPreview(
            StarImportLoadRequest(
                source = StarImportUiSource.PublicUser,
                usernameInput = "demo",
                listUrlInput = ""
            )
        )

        assertEquals(listOf("first", "second"), seenTokens)
    }

    @Test
    fun `apk verification keeps target order and uses snapshot config`() = runBlocking {
        var token = "fresh"
        val repository = GitHubStarImportPageRepository(
            ioDispatcher = Dispatchers.Unconfined,
            snapshotLoader = {
                GitHubTrackSnapshot(
                    lookupConfig = GitHubLookupConfig(apiToken = token),
                    refreshIntervalHours = 12
                )
            },
            apkVerifierFactory = {
                GitHubStarImportApkVerifier(
                    source = FakeApkSource(),
                    cache = FixedVerificationCache()
                )
            }
        )
        val targets = listOf(
            importCandidate("one"),
            importCandidate("two")
        )

        val results = repository.verifyApkAssets(targets)

        assertEquals(targets.map { it.trackedApp.id }, results.map { it.first })
        assertEquals(
            listOf("com.example.one", "com.example.two"),
            results.map { it.second.packageName }
        )
    }

    private class FakeDiscoverySource(
        private val starred: List<GitHubRepositoryCandidate> = emptyList(),
        private val lists: List<GitHubStarListSummary> = emptyList()
    ) : GitHubRepositoryDiscoverySource {
        override fun fetchAuthenticatedStarredRepositories(
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> = Result.success(starred.take(limit))

        override fun fetchUserStarredRepositories(
            username: String,
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> = Result.success(starred.take(limit))

        override fun searchRepositories(
            query: String,
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> = Result.success(emptyList())

        override fun fetchStarListRepositories(
            starListUrl: String,
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> = Result.success(starred.take(limit))

        override fun fetchStarLists(starListsUrl: String): Result<List<GitHubStarListSummary>> {
            return Result.success(lists)
        }
    }

    private class FakeApkSource : GitHubApkPackageNameScanSource {
        override suspend fun loadLatestStableRelease(
            owner: String,
            repo: String,
            lookupConfig: GitHubLookupConfig
        ): Result<GitHubStableReleaseTarget> = Result.failure(IllegalStateException("unused"))

        override suspend fun fetchApkAssets(
            owner: String,
            repo: String,
            release: GitHubStableReleaseTarget,
            lookupConfig: GitHubLookupConfig
        ): Result<List<GitHubReleaseAssetFile>> = Result.failure(IllegalStateException("unused"))

        override suspend fun loadLatestStableApkAssets(
            owner: String,
            repo: String,
            lookupConfig: GitHubLookupConfig
        ): Result<GitHubStableReleaseApkAssets> = Result.failure(IllegalStateException("unused"))

        override suspend fun readAndroidManifestBytes(
            asset: GitHubReleaseAssetFile,
            lookupConfig: GitHubLookupConfig
        ): Result<ByteArray> = Result.failure(IllegalStateException("unused"))
    }

    private class FixedVerificationCache : GitHubStarImportApkVerificationCache {
        override fun load(
            owner: String,
            repo: String,
            lookupConfig: GitHubLookupConfig,
            refreshIntervalHours: Int,
            nowMillis: Long
        ): GitHubStarImportApkVerification {
            return GitHubStarImportApkVerification(
                owner = owner,
                repo = repo,
                status = GitHubStarImportApkVerificationStatus.HasApk,
                packageName = "com.example.$repo",
                checkedAtMillis = nowMillis
            )
        }

        override fun save(
            owner: String,
            repo: String,
            lookupConfig: GitHubLookupConfig,
            verification: GitHubStarImportApkVerification
        ) = Unit
    }

    private fun importCandidate(repo: String): GitHubRepositoryImportCandidate {
        val candidate = repositoryCandidate("demo", repo)
        return GitHubRepositoryImportCandidate(
            repository = candidate,
            trackedApp = GitHubTrackedApp(
                repoUrl = candidate.repoUrl,
                owner = candidate.owner,
                repo = candidate.repo,
                packageName = "",
                appLabel = candidate.fullName
            ),
            alreadyTracked = false,
            score = 1
        )
    }

    private fun repositoryCandidate(
        owner: String,
        repo: String
    ): GitHubRepositoryCandidate {
        return GitHubRepositoryCandidate(
            owner = owner,
            repo = repo,
            repoUrl = "https://github.com/$owner/$repo",
            language = "Kotlin",
            sourceType = GitHubRepositoryDiscoverySourceType.PublicUserStars,
            matchReason = GitHubRepositoryCandidateMatchReason.Starred
        )
    }
}
