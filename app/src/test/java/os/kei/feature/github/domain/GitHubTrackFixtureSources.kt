package os.kei.feature.github.domain

import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubAtomFeed
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubStarListSummary
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubVersionCandidateSource

internal object GitHubTrackFixtureSources {
    fun discoverySource(items: List<GitHubTrackedApp>): GitHubRepositoryDiscoverySource {
        return ExportTrackDiscoverySource(items)
    }

    fun packageScanSource(items: List<GitHubTrackedApp>): GitHubApkPackageNameScanSource {
        return ExportTrackPackageScanSource(items)
    }

    fun importCandidates(items: List<GitHubTrackedApp>): List<GitHubRepositoryImportCandidate> {
        return items.mapIndexed { index, item ->
            GitHubRepositoryImportCandidate(
                repository = repositoryCandidate(item, index),
                trackedApp = item,
                alreadyTracked = false,
                score = 100
            )
        }
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

    fun localVersion(index: Int): String {
        return "v${index + 1}.0.0"
    }

    fun releaseSnapshot(
        item: GitHubTrackedApp,
        index: Int
    ): GitHubRepositoryReleaseSnapshot {
        val stableTag = "v${index + 2}.0.0"
        val preTag = "v${index + 2}.1.0-beta1"
        val stable = releaseSignal(
            tag = stableTag,
            title = "${item.appLabel} $stableTag",
            updatedAtMillis = 1_700_000_000_000L + index * 2L
        )
        val preRelease = if (item.preferPreRelease) {
            releaseSignal(
                tag = preTag,
                title = "${item.appLabel} $preTag",
                updatedAtMillis = 1_700_000_100_000L + index * 2L,
                channel = GitHubReleaseChannel.BETA
            )
        } else {
            null
        }
        val entries = buildList {
            add(releaseEntry(stableTag, "${item.appLabel} $stableTag"))
            if (preRelease != null) {
                add(
                    releaseEntry(
                        tag = preTag,
                        title = "${item.appLabel} $preTag",
                        channel = GitHubReleaseChannel.BETA,
                        isLikelyPreRelease = true
                    )
                )
            }
        }
        return GitHubRepositoryReleaseSnapshot(
            strategyId = "fixture",
            feed = GitHubAtomFeed(
                title = "${item.owner}/${item.repo}",
                feedUrl = "${item.repoUrl}/releases.atom",
                entries = entries
            ),
            latestStable = stable,
            latestPreRelease = preRelease
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

    fun actionsArtifacts(items: List<GitHubTrackedApp>): List<GitHubActionsArtifact> {
        return items.mapIndexed { index, item ->
            GitHubActionsArtifact(
                id = index.toLong() + 1L,
                name = actionArtifactEntryName(
                    index = index,
                    item = item,
                    selectedItem = items.first()
                ).substringAfterLast('/'),
                sizeBytes = 10_000_000L + index
            )
        }
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

    private fun releaseSignal(
        tag: String,
        title: String,
        updatedAtMillis: Long,
        channel: GitHubReleaseChannel = GitHubReleaseChannel.STABLE
    ): GitHubReleaseVersionSignals {
        return GitHubReleaseVersionSignals(
            displayVersion = tag,
            rawTag = tag,
            rawName = title,
            link = "https://github.com/fixture/repo/releases/tag/$tag",
            updatedAtMillis = updatedAtMillis,
            versionCandidates = GitHubVersionUtils.buildVersionCandidates(
                GitHubVersionCandidateSource.Tag to tag,
                GitHubVersionCandidateSource.Title to title
            ),
            source = GitHubReleaseSignalSource.AtomEntry,
            channel = channel
        )
    }

    private fun releaseEntry(
        tag: String,
        title: String,
        channel: GitHubReleaseChannel = GitHubReleaseChannel.STABLE,
        isLikelyPreRelease: Boolean = false
    ): GitHubAtomReleaseEntry {
        return GitHubAtomReleaseEntry(
            tag = tag,
            title = title,
            link = "https://github.com/fixture/repo/releases/tag/$tag",
            versionCandidates = GitHubVersionUtils.buildVersionCandidates(
                GitHubVersionCandidateSource.Tag to tag,
                GitHubVersionCandidateSource.Title to title
            ),
            channel = channel,
            isLikelyPreRelease = isLikelyPreRelease
        )
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
            return Result.success(requireTracks(owner, repo).map(::releaseAsset))
        }

        override fun readAndroidManifestBytes(
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
