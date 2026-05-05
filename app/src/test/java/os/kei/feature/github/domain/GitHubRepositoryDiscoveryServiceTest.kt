package os.kei.feature.github.domain

import org.junit.Test
import os.kei.feature.github.model.GitHubAppRepositorySearchRequest
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.feature.github.model.GitHubStarredRepositoryImportSource
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.InstalledAppItem
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubRepositoryDiscoveryServiceTest {
    @Test
    fun `starred import preview dedupes repositories and marks existing items`() {
        val source = FakeDiscoverySource(
            publicStars = listOf(
                candidate(owner = "alpha", repo = "one", stars = 9),
                candidate(owner = "beta", repo = "two", stars = 81),
                candidate(owner = "beta", repo = "two", stars = 1)
            )
        )
        val service = GitHubRepositoryDiscoveryService(source)
        val existing = listOf(
            tracked(owner = "alpha", repo = "one", packageName = "com.alpha.one")
        )

        val preview = service.previewStarredRepositoryImport(
            request = GitHubStarredRepositoryImportRequest(username = "voyager", limit = 20),
            existingItems = existing
        ).getOrThrow()

        assertEquals("voyager", preview.sourceLabel)
        assertEquals(3, preview.totalFetchedCount)
        assertEquals(1, preview.importableCount)
        assertEquals(1, preview.alreadyTrackedCount)
        assertEquals(
            listOf("beta/two", "alpha/one"),
            preview.candidates.map { it.repository.fullName })
        assertFalse(preview.candidates.first().alreadyTracked)
        assertEquals("", preview.candidates.first().trackedApp.packageName)
        assertEquals("beta/two", preview.candidates.first().trackedApp.appLabel)
    }

    @Test
    fun `star list url import uses list source and marks existing items`() {
        val source = FakeDiscoverySource(
            starList = listOf(
                candidate(
                    owner = "list",
                    repo = "one",
                    sourceType = GitHubRepositoryDiscoverySourceType.StarList,
                    matchReason = GitHubRepositoryCandidateMatchReason.Starred
                )
            )
        )
        val service = GitHubRepositoryDiscoveryService(source)

        val preview = service.previewStarredRepositoryImport(
            request = GitHubStarredRepositoryImportRequest(
                source = GitHubStarredRepositoryImportSource.StarListUrl,
                starListUrl = "https://github.com/stars/voyager/lists/android",
                limit = 20
            ),
            existingItems = emptyList()
        ).getOrThrow()

        assertEquals("https://github.com/stars/voyager/lists/android", preview.sourceLabel)
        assertEquals(1, preview.importableCount)
        assertEquals("list/one", preview.candidates.single().repository.fullName)
    }

    @Test
    fun `app repository search builds app queries and ranks package matches first`() {
        val app = InstalledAppItem(
            label = "KeiOS",
            packageName = "os.kei"
        )
        val packageMatch = candidate(
            owner = "hosizoraru",
            repo = "KeiOS",
            description = "Android system utility for os.kei",
            stars = 64,
            matchReason = GitHubRepositoryCandidateMatchReason.RepositoryName,
            sourceType = GitHubRepositoryDiscoverySourceType.RepositorySearch
        )
        val labelMatch = candidate(
            owner = "someone",
            repo = "keios-theme",
            description = "KeiOS theme experiments",
            stars = 120,
            matchReason = GitHubRepositoryCandidateMatchReason.RepositoryName,
            sourceType = GitHubRepositoryDiscoverySourceType.RepositorySearch
        )
        val source = FakeDiscoverySource(
            searchResults = mapOf(
                "KeiOS android in:name,description,readme" to listOf(labelMatch, packageMatch),
                "os.kei in:description,readme" to listOf(packageMatch),
                "keios android" to listOf(labelMatch),
                "kei android in:name,description,readme" to emptyList()
            )
        )
        val service = GitHubRepositoryDiscoveryService(source)

        val result = service.searchRepositoriesForApp(
            request = GitHubAppRepositorySearchRequest(app = app, limit = 10),
            existingItems = emptyList()
        ).getOrThrow()

        assertEquals(4, result.queryCount)
        assertEquals(
            listOf("hosizoraru/KeiOS", "someone/keios-theme"),
            result.candidates.map { it.repository.fullName })
        assertEquals(
            GitHubRepositoryCandidateMatchReason.PackageName,
            result.candidates.first().repository.matchReason
        )
        assertEquals("os.kei", result.candidates.first().trackedApp.packageName)
        assertEquals("KeiOS", result.candidates.first().trackedApp.appLabel)
        assertTrue(source.searchQueries.contains("os.kei in:description,readme"))
    }

    @Test
    fun `installed app query builder keeps label and package entry points`() {
        val queries = GitHubRepositoryDiscoveryQueries.forInstalledApp(
            InstalledAppItem(
                label = "Blue Archive",
                packageName = "com.nexon.bluearchive"
            )
        )

        assertEquals(
            listOf(
                "Blue Archive android in:name,description,readme",
                "com.nexon.bluearchive in:description,readme",
                "blue archive android",
                "bluearchive android in:name,description,readme"
            ),
            queries
        )
    }

    private class FakeDiscoverySource(
        private val authenticatedStars: List<GitHubRepositoryCandidate> = emptyList(),
        private val publicStars: List<GitHubRepositoryCandidate> = emptyList(),
        private val starList: List<GitHubRepositoryCandidate> = emptyList(),
        private val searchResults: Map<String, List<GitHubRepositoryCandidate>> = emptyMap()
    ) : GitHubRepositoryDiscoverySource {
        val searchQueries = mutableListOf<String>()

        override fun fetchAuthenticatedStarredRepositories(
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> {
            return Result.success(authenticatedStars.take(limit))
        }

        override fun fetchUserStarredRepositories(
            username: String,
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> {
            return Result.success(publicStars.take(limit))
        }

        override fun searchRepositories(
            query: String,
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> {
            searchQueries += query
            return Result.success(searchResults[query].orEmpty().take(limit))
        }

        override fun fetchStarListRepositories(
            starListUrl: String,
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> {
            return Result.success(starList.take(limit))
        }
    }

    private fun candidate(
        owner: String,
        repo: String,
        description: String = "",
        stars: Int = 0,
        matchReason: GitHubRepositoryCandidateMatchReason = GitHubRepositoryCandidateMatchReason.Starred,
        sourceType: GitHubRepositoryDiscoverySourceType = GitHubRepositoryDiscoverySourceType.PublicUserStars
    ): GitHubRepositoryCandidate {
        return GitHubRepositoryCandidate(
            owner = owner,
            repo = repo,
            repoUrl = "https://github.com/$owner/$repo",
            description = description,
            starCount = stars,
            sourceType = sourceType,
            matchReason = matchReason
        )
    }

    private fun tracked(
        owner: String,
        repo: String,
        packageName: String
    ): GitHubTrackedApp {
        return GitHubTrackedApp(
            repoUrl = "https://github.com/$owner/$repo",
            owner = owner,
            repo = repo,
            packageName = packageName,
            appLabel = "$owner/$repo"
        )
    }
}
