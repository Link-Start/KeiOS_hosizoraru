package os.kei.feature.github.domain

import org.junit.Test
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubTrackedApp
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubTrackExportFixtureBidirectionalScanTest {
    @Test
    fun `exported tracks json imports all array items as valid tracked apps`() {
        val payload = GitHubTrackStore.parseTrackedItemsImport(GitHubTrackExportFixture.rawJson)

        assertEquals(31, payload.sourceCount)
        assertEquals(31, payload.items.size)
        assertEquals(0, payload.invalidCount)
        assertEquals(0, payload.duplicateCount)
        assertNotNull(payload.items.firstOrNull { item ->
            item.owner == "LibChecker" &&
                    item.repo == "LibChecker" &&
                    item.packageName == "com.absinthe.libchecker"
        })
        assertNotNull(payload.items.firstOrNull { item ->
            item.owner == "vvb2060" &&
                    item.repo == "PackageInstaller" &&
                    item.packageName == "io.github.vvb2060.packageinstaller"
        })
    }

    @Test
    fun `project address to package scanner resolves every exported tracked app`() {
        val items = GitHubTrackExportFixture.trackedItems
        val scanSource = ExportTrackPackageScanSource(items)
        val scanner = GitHubApkPackageNameScanner(scanSource)

        items.forEach { item ->
            val apiResult = scanner.scan(
                GitHubApkPackageNameScanRequest(
                    repoUrl = item.repoUrl,
                    lookupConfig = GitHubLookupConfig(
                        selectedStrategy = GitHubLookupStrategyOption.GitHubApiToken,
                        apiToken = "token-123"
                    )
                )
            ).getOrThrow()
            val atomResult = scanner.scan(
                GitHubApkPackageNameScanRequest(
                    repoUrl = item.repoUrl,
                    lookupConfig = GitHubLookupConfig(
                        selectedStrategy = GitHubLookupStrategyOption.AtomFeed
                    )
                )
            ).getOrThrow()

            assertEquals(item.owner, apiResult.owner)
            assertEquals(item.repo, apiResult.repo)
            assertEquals(item.packageName, apiResult.packageName)
            assertEquals(item.packageName, atomResult.packageName)
        }
    }

    @Test
    fun `package to repository resolver confirms every exported tracked app from preferred repository`() {
        val items = GitHubTrackExportFixture.trackedItems
        val discovery = ExportTrackDiscoverySource(items)
        val scanSource = ExportTrackPackageScanSource(items)
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = discovery,
            packageNameScanner = GitHubApkPackageNameScanner(scanSource)
        )

        items.forEach { item ->
            val result = resolver.scanRepositoriesForPackage(
                GitHubPackageRepositoryScanRequest(
                    packageName = item.packageName,
                    appLabel = item.appLabel,
                    preferredRepoUrl = item.repoUrl,
                    lookupConfig = GitHubLookupConfig(
                        selectedStrategy = GitHubLookupStrategyOption.GitHubApiToken,
                        apiToken = "token-123"
                    ),
                    candidateLimit = items.size,
                    verificationLimit = items.size
                )
            ).getOrThrow()
            val matched = result.matchedCandidates.firstOrNull { candidate ->
                candidate.repository.owner.equals(item.owner, ignoreCase = true) &&
                        candidate.repository.repo.equals(item.repo, ignoreCase = true)
            }

            assertNotNull(matched, "Expected reverse scan to find ${item.owner}/${item.repo}")
            assertEquals(item.repoUrl, matched.trackedApp.repoUrl)
            assertEquals(item.packageName, matched.trackedApp.packageName)
            assertEquals(item.appLabel, matched.trackedApp.appLabel)
            assertTrue(result.scannedCandidateCount >= result.matchedCandidates.size)
        }
    }

    @Test
    fun `package to repository resolver discovers every exported tracked app from package and label`() {
        val items = GitHubTrackExportFixture.trackedItems
        val discovery = ExportTrackDiscoverySource(items)
        val scanSource = ExportTrackPackageScanSource(items)
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = discovery,
            packageNameScanner = GitHubApkPackageNameScanner(scanSource)
        )

        items.forEach { item ->
            val result = resolver.scanRepositoriesForPackage(
                GitHubPackageRepositoryScanRequest(
                    packageName = item.packageName,
                    appLabel = item.appLabel,
                    lookupConfig = GitHubLookupConfig(
                        selectedStrategy = GitHubLookupStrategyOption.GitHubApiToken,
                        apiToken = "token-123"
                    ),
                    candidateLimit = items.size,
                    verificationLimit = items.size
                )
            ).getOrThrow()
            val matched = result.matchedCandidates.firstOrNull { candidate ->
                candidate.repository.owner.equals(item.owner, ignoreCase = true) &&
                        candidate.repository.repo.equals(item.repo, ignoreCase = true)
            }

            assertNotNull(
                matched,
                "Expected package search to discover ${item.owner}/${item.repo}"
            )
            assertEquals(item.packageName, matched.trackedApp.packageName)
            assertTrue(result.queryCount >= 1)
        }
    }

    private class ExportTrackDiscoverySource(
        items: List<GitHubTrackedApp>
    ) : GitHubRepositoryDiscoverySource {
        private val candidates = items.map { item ->
            GitHubRepositoryCandidate(
                owner = item.owner,
                repo = item.repo,
                repoUrl = item.repoUrl,
                description = listOf(
                    item.appLabel,
                    item.packageName,
                    "Android"
                ).filter { it.isNotBlank() }.joinToString(" "),
                language = "Kotlin",
                starCount = 100,
                forkCount = 0,
                archived = false,
                fork = false,
                updatedAtMillis = 1_700_000_000_000,
                sourceType = GitHubRepositoryDiscoverySourceType.RepositorySearch,
                matchReason = GitHubRepositoryCandidateMatchReason.RepositoryName
            )
        }

        override fun fetchAuthenticatedStarredRepositories(
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> {
            return Result.success(candidates.take(limit))
        }

        override fun fetchUserStarredRepositories(
            username: String,
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> {
            return Result.success(candidates.take(limit))
        }

        override fun searchRepositories(
            query: String,
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> {
            val terms = query
                .replace(Regex("""\bin:[^\s]+"""), " ")
                .replace(Regex("""[^A-Za-z0-9_.-]+"""), " ")
                .split(' ')
                .map { it.trim().lowercase() }
                .filter { term ->
                    term.length >= 2 &&
                            term != "android" &&
                            term != "app"
                }
                .distinct()
            if (terms.isEmpty()) return Result.success(emptyList())
            return Result.success(
                candidates
                    .filter { candidate ->
                        val searchable = listOf(
                            candidate.owner,
                            candidate.repo,
                            candidate.fullName,
                            candidate.description
                        ).joinToString(" ").lowercase()
                        terms.all { term -> searchable.contains(term) }
                    }
                    .take(limit)
            )
        }
    }

    private class ExportTrackPackageScanSource(
        items: List<GitHubTrackedApp>
    ) : GitHubApkPackageNameScanSource {
        private val byRepo = items.associateBy { item ->
            "${item.owner.lowercase()}/${item.repo.lowercase()}"
        }

        override fun loadLatestStableRelease(
            owner: String,
            repo: String,
            lookupConfig: GitHubLookupConfig
        ): Result<GitHubStableReleaseTarget> {
            val item = requireTrack(owner, repo)
            return Result.success(
                GitHubStableReleaseTarget(
                    tag = "v-fixture",
                    releaseUrl = "${item.repoUrl}/releases/tag/v-fixture"
                )
            )
        }

        override fun fetchApkAssets(
            owner: String,
            repo: String,
            release: GitHubStableReleaseTarget,
            lookupConfig: GitHubLookupConfig
        ): Result<List<GitHubReleaseAssetFile>> {
            val item = requireTrack(owner, repo)
            return Result.success(
                listOf(
                    GitHubReleaseAssetFile(
                        name = "${item.repo}.apk",
                        downloadUrl = "${item.repoUrl}/releases/download/${release.tag}/${item.repo}.apk",
                        apiAssetUrl = "https://api.github.com/repos/${item.owner}/${item.repo}/releases/assets/1",
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
            val repoKey = asset.downloadUrl
                .substringAfter("https://github.com/")
                .substringBefore("/releases/")
                .lowercase()
            val item = byRepo[repoKey] ?: error("No exported track fixture for $repoKey")
            BinaryManifestFixture.build(item.packageName)
        }

        private fun requireTrack(
            owner: String,
            repo: String
        ): GitHubTrackedApp {
            val key = "${owner.lowercase()}/${repo.lowercase()}"
            return byRepo[key] ?: error("No exported track fixture for $key")
        }
    }
}
