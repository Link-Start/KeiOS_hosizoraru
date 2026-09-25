package os.kei.feature.github.domain

import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubStarListSummary
import os.kei.feature.github.model.GitHubTrackedApp

internal object GitHubTrackFixtureSources {
    fun discoverySource(items: List<GitHubTrackedApp>): GitHubRepositoryDiscoverySource {
        return ExportTrackDiscoverySource(items)
    }

    fun packageScanSource(items: List<GitHubTrackedApp>): GitHubApkPackageNameScanSource {
        return ExportTrackPackageScanSource(items)
    }

    fun repositoryCandidate(
        item: GitHubTrackedApp,
        index: Int = 0,
        sourceType: GitHubRepositoryDiscoverySourceType =
            GitHubRepositoryDiscoverySourceType.RepositorySearch
    ): GitHubRepositoryCandidate {
        return GitHubRepositoryCandidate(
            owner = item.owner,
            repo = item.repo,
            repoUrl = item.repoUrl,
            description = listOf(
                item.appLabel,
                item.packageName,
                "Android"
            ).filter { it.isNotBlank() }.joinToString(" "),
            language = if (index % 3 == 0) "Kotlin" else "Java",
            starCount = 100 + index,
            forkCount = 0,
            archived = false,
            fork = false,
            updatedAtMillis = 1_700_000_000_000L + index,
            sourceType = sourceType,
            matchReason = GitHubRepositoryCandidateMatchReason.RepositoryName
        )
    }

    fun releaseAsset(item: GitHubTrackedApp): GitHubReleaseAssetFile {
        val assetName = releaseAssetName(item)
        return GitHubReleaseAssetFile(
            name = assetName,
            downloadUrl = "${item.repoUrl}/releases/download/v-fixture/$assetName",
            apiAssetUrl = "https://api.github.com/repos/${item.owner}/${item.repo}/releases/assets/${
                item.packageName.hashCode().toUInt()
            }",
            sizeBytes = 1024L,
            downloadCount = 1
        )
    }

    private fun releaseAssetName(item: GitHubTrackedApp): String {
        val repoName = item.repo.replace(Regex("""[^A-Za-z0-9_.-]+"""), "-")
        val packageName = item.packageName.replace(Regex("""[^A-Za-z0-9_.-]+"""), "-")
        return "$repoName-$packageName.apk"
    }

    fun actionArtifactEntryNames(
        items: List<GitHubTrackedApp>,
        selectedItem: GitHubTrackedApp = items.first()
    ): List<Pair<String, GitHubTrackedApp>> {
        return items.mapIndexed { index, item ->
            actionArtifactEntryName(
                index = index,
                item = item,
                selectedItem = selectedItem
            ) to item
        }
    }

    fun actionArtifactEntryName(
        index: Int,
        item: GitHubTrackedApp,
        selectedItem: GitHubTrackedApp
    ): String {
        val prefix = index.toString().padStart(2, '0')
        val repoName = item.repo.replace(Regex("""[^A-Za-z0-9_.-]+"""), "-")
        val variant = if (item.id == selectedItem.id) {
            "universal-release"
        } else {
            "arm64-debug"
        }
        return "outputs/$prefix-$repoName-$variant.apk"
    }

    private class ExportTrackDiscoverySource(
        items: List<GitHubTrackedApp>
    ) : GitHubRepositoryDiscoverySource {
        private val candidates = items.mapIndexed { index, item ->
            repositoryCandidate(item, index)
        }

        override fun fetchAuthenticatedStarredRepositories(
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> {
            return Result.success(
                candidates
                    .map {
                        it.copy(
                            sourceType = GitHubRepositoryDiscoverySourceType.AuthenticatedStars,
                            matchReason = GitHubRepositoryCandidateMatchReason.Starred
                        )
                    }
                    .take(limit)
            )
        }

        override fun fetchUserStarredRepositories(
            username: String,
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> {
            return Result.success(
                candidates
                    .map {
                        it.copy(
                            sourceType = GitHubRepositoryDiscoverySourceType.PublicUserStars,
                            matchReason = GitHubRepositoryCandidateMatchReason.Starred
                        )
                    }
                    .take(limit)
            )
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

        override fun fetchStarListRepositories(
            starListUrl: String,
            limit: Int
        ): Result<List<GitHubRepositoryCandidate>> {
            return Result.success(
                candidates
                    .map {
                        it.copy(
                            sourceType = GitHubRepositoryDiscoverySourceType.StarList,
                            matchReason = GitHubRepositoryCandidateMatchReason.Starred
                        )
                    }
                    .take(limit)
            )
        }

        override fun fetchStarLists(starListsUrl: String): Result<List<GitHubStarListSummary>> {
            return Result.success(
                listOf(
                    GitHubStarListSummary(
                        name = "Fixture",
                        repositoryCount = candidates.size,
                        url = "$starListsUrl/fixture"
                    )
                )
            )
        }
    }

    private class ExportTrackPackageScanSource(
        items: List<GitHubTrackedApp>
    ) : GitHubApkPackageNameScanSource {
        private val byRepo = items.groupBy { item ->
            "${item.owner.lowercase()}/${item.repo.lowercase()}"
        }
        private val byDownloadUrl = items.associateBy { item ->
            releaseAsset(item).downloadUrl
        }

        override suspend fun loadLatestStableRelease(
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

        override suspend fun fetchApkAssets(
            owner: String,
            repo: String,
            release: GitHubStableReleaseTarget,
            lookupConfig: GitHubLookupConfig
        ): Result<List<GitHubReleaseAssetFile>> {
            return Result.success(requireTracks(owner, repo).map(::releaseAsset))
        }

        override suspend fun readAndroidManifestBytes(
            asset: GitHubReleaseAssetFile,
            lookupConfig: GitHubLookupConfig
        ): Result<ByteArray> = runCatching {
            val repoKey = asset.downloadUrl
                .substringAfter("https://github.com/")
                .substringBefore("/releases/")
                .lowercase()
            val item = byDownloadUrl[asset.downloadUrl]
                ?: byRepo[repoKey]?.singleOrNull()
                ?: error("No exported track fixture for ${asset.name}")
            BinaryManifestFixture.build(item.packageName)
        }

        private fun requireTrack(
            owner: String,
            repo: String
        ): GitHubTrackedApp {
            return requireTracks(owner, repo).first()
        }

        private fun requireTracks(
            owner: String,
            repo: String
        ): List<GitHubTrackedApp> {
            val key = "${owner.lowercase()}/${repo.lowercase()}"
            return byRepo[key].orEmpty().ifEmpty {
                error("No exported track fixture for $key")
            }
        }
    }
}
