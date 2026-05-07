package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.core.background.AppBackgroundScheduler
import os.kei.feature.github.data.local.GitHubStarImportApkVerificationCacheStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubApkPackageNameScanRepository
import os.kei.feature.github.data.remote.GitHubRepositoryDiscoveryRepository
import os.kei.feature.github.domain.GitHubApkPackageNameScanner
import os.kei.feature.github.domain.GitHubPackageNameValidator
import os.kei.feature.github.domain.GitHubPackageRepositoryResolver
import os.kei.feature.github.domain.GitHubRepositoryDiscoveryService
import os.kei.feature.github.domain.GitHubStarImportApkVerifier
import os.kei.feature.github.domain.GitHubStarImportClassifier
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportQuality
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.feature.github.model.GitHubStarredRepositoryImportSource
import java.util.Locale

internal class McpGitHubDiscoveryTools(
    private val environment: McpToolEnvironment
) {
    private val appContext get() = environment.appContext

    fun register(server: Server) {
        server.addMcpTextTool(environment, name = "keios.github.config.snapshot") { _ ->
            buildGitHubConfigSnapshotText()
        }

        server.addMcpTextTool(environment, name = "keios.github.discovery.search") { request ->
            val query = argString(request.arguments?.get("query")).trim()
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_ENTRY_LIMIT).coerceIn(1, 50)
            buildRepositorySearchText(query = query, limit = limit)
        }

        server.addMcpTextTool(environment, name = "keios.github.repo.package.scan") { request ->
            val repoUrl = argString(request.arguments?.get("repoUrl")).trim()
            buildRepoPackageScanText(repoUrl)
        }

        server.addMcpTextTool(environment, name = "keios.github.package.repo.scan") { request ->
            val packageName = argString(request.arguments?.get("packageName")).trim()
            val appLabel = argString(request.arguments?.get("appLabel")).trim()
            val preferredRepoUrl = argString(request.arguments?.get("preferredRepoUrl")).trim()
            val candidateLimit =
                argInt(request.arguments?.get("candidateLimit"), 16).coerceIn(1, 50)
            val verificationLimit = argInt(request.arguments?.get("verificationLimit"), 5)
                .coerceIn(1, candidateLimit)
            buildPackageRepoScanText(
                packageName = packageName,
                appLabel = appLabel,
                preferredRepoUrl = preferredRepoUrl,
                candidateLimit = candidateLimit,
                verificationLimit = verificationLimit
            )
        }

        server.addMcpTextTool(environment, name = "keios.github.stars.lists") { request ->
            val url = argString(request.arguments?.get("url")).trim()
            buildStarListsText(url)
        }

        server.addMcpTextTool(environment, name = "keios.github.stars.preview") { request ->
            buildStarImportText(request.arguments.orEmpty(), apply = false)
        }

        server.addMcpTextTool(environment, name = "keios.github.stars.import") { request ->
            val apply = argBoolean(request.arguments?.get("apply"), false)
            buildStarImportText(request.arguments.orEmpty(), apply = apply)
        }

        server.addMcpTextTool(environment, name = "keios.github.stars.apk.verify") { request ->
            val repoUrls = argString(request.arguments?.get("repoUrls"))
            val limit = argInt(request.arguments?.get("limit"), DEFAULT_ENTRY_LIMIT).coerceIn(1, 30)
            buildStarApkVerificationText(repoUrls = repoUrls, limit = limit)
        }
    }

    private fun buildGitHubConfigSnapshotText(): String {
        val snapshot = GitHubTrackStore.loadSnapshot()
        return buildString {
            appendLine("strategy=${snapshot.lookupConfig.selectedStrategy.storageId}")
            appendLine("apiTokenConfigured=${snapshot.lookupConfig.apiToken.isNotBlank()}")
            appendLine("refreshIntervalHours=${snapshot.refreshIntervalHours}")
            appendLine("checkAllTrackedPreReleases=${snapshot.lookupConfig.checkAllTrackedPreReleases}")
            appendLine("aggressiveApkFiltering=${snapshot.lookupConfig.aggressiveApkFiltering}")
            appendLine("shareImportLinkageEnabled=${snapshot.lookupConfig.shareImportLinkageEnabled}")
            appendLine("shareImportFlowMode=${snapshot.lookupConfig.shareImportFlowMode.storageId}")
            appendLine("trackedCount=${snapshot.items.size}")
            appendLine("checkCacheCount=${snapshot.checkCache.size}")
            appendLine("lastRefreshMs=${snapshot.lastRefreshMs}")
        }.trim()
    }

    private fun buildRepositorySearchText(query: String, limit: Int): String {
        if (query.isBlank()) return "ok=false\nmessage=query_required"
        val source = GitHubRepositoryDiscoveryRepository(
            apiToken = GitHubTrackStore.loadLookupConfig().apiToken
        )
        return source.searchRepositories(query, limit).fold(
            onSuccess = { repositories ->
                buildString {
                    appendLine("ok=true")
                    appendLine("query=$query")
                    appendLine("returned=${repositories.size}")
                    repositories.forEachIndexed { index, repo ->
                        appendLine(repo.toMcpRepositoryRow("repo[$index]"))
                    }
                }.trim()
            },
            onFailure = { error ->
                "ok=false\nmessage=${error.message ?: error.javaClass.simpleName}"
            }
        )
    }

    private fun buildRepoPackageScanText(repoUrl: String): String {
        if (repoUrl.isBlank()) return "ok=false\nmessage=repoUrl_required"
        val scanner = GitHubApkPackageNameScanner(GitHubApkPackageNameScanRepository())
        return scanner.scan(
            GitHubApkPackageNameScanRequest(
                repoUrl = repoUrl,
                lookupConfig = GitHubTrackStore.loadLookupConfig()
            )
        ).fold(
            onSuccess = { result ->
                buildString {
                    appendLine("ok=true")
                    appendLine("owner=${result.owner}")
                    appendLine("repo=${result.repo}")
                    appendLine("packageName=${result.packageName}")
                    appendLine("releaseTag=${result.releaseTag}")
                    appendLine("releaseUrl=${result.releaseUrl}")
                    appendLine("assetName=${result.assetName}")
                }.trim()
            },
            onFailure = { error ->
                "ok=false\nrepoUrl=$repoUrl\nmessage=${error.message ?: error.javaClass.simpleName}"
            }
        )
    }

    private fun buildPackageRepoScanText(
        packageName: String,
        appLabel: String,
        preferredRepoUrl: String,
        candidateLimit: Int,
        verificationLimit: Int
    ): String {
        if (!GitHubPackageNameValidator.isValid(packageName)) {
            return "ok=false\nmessage=invalid_package_name\npackageName=$packageName"
        }
        val lookupConfig = GitHubTrackStore.loadLookupConfig()
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = GitHubRepositoryDiscoveryRepository(apiToken = lookupConfig.apiToken),
            packageNameScanner = GitHubApkPackageNameScanner(GitHubApkPackageNameScanRepository())
        )
        return resolver.scanRepositoriesForPackage(
            GitHubPackageRepositoryScanRequest(
                packageName = packageName,
                appLabel = appLabel,
                preferredRepoUrl = preferredRepoUrl,
                lookupConfig = lookupConfig,
                candidateLimit = candidateLimit,
                verificationLimit = verificationLimit
            )
        ).fold(
            onSuccess = { result ->
                buildString {
                    appendLine("ok=true")
                    appendLine("packageName=${result.packageName}")
                    appendLine("appLabel=${result.appLabel}")
                    appendLine("queryCount=${result.queryCount}")
                    appendLine("fetchedCandidateCount=${result.fetchedCandidateCount}")
                    appendLine("scannedCandidateCount=${result.scannedCandidateCount}")
                    appendLine("matchedCount=${result.matchedCandidates.size}")
                    appendLine("mismatchedCandidateCount=${result.mismatchedCandidateCount}")
                    appendLine("failedCandidateCount=${result.failedCandidateCount}")
                    result.matchedCandidates.forEachIndexed { index, candidate ->
                        appendLine(
                            "match[$index]=repo:${candidate.repository.fullName} | package:${candidate.trackedApp.packageName} | score:${candidate.score} | tag:${candidate.releaseTag} | asset:${candidate.assetName} | url:${candidate.repository.repoUrl}"
                        )
                    }
                }.trim()
            },
            onFailure = { error ->
                "ok=false\npackageName=$packageName\nmessage=${error.message ?: error.javaClass.simpleName}"
            }
        )
    }

    private fun buildStarListsText(url: String): String {
        if (url.isBlank()) return "ok=false\nmessage=url_required"
        val source = GitHubRepositoryDiscoveryRepository(
            apiToken = GitHubTrackStore.loadLookupConfig().apiToken
        )
        return source.fetchStarLists(url).fold(
            onSuccess = { lists ->
                buildString {
                    appendLine("ok=true")
                    appendLine("listCount=${lists.size}")
                    lists.forEachIndexed { index, list ->
                        appendLine(
                            "list[$index]=name:${list.name} | count:${list.repositoryCount} | url:${list.url}"
                        )
                    }
                }.trim()
            },
            onFailure = { error ->
                "ok=false\nmessage=${error.message ?: error.javaClass.simpleName}"
            }
        )
    }

    private fun buildStarImportText(
        arguments: Map<String, Any?>,
        apply: Boolean
    ): String {
        val lookupConfig = GitHubTrackStore.loadLookupConfig()
        val source = normalizeStarImportSource(argString(arguments["source"]))
        val username = argString(arguments["username"]).trim()
        val listUrl = argString(arguments["listUrl"]).trim()
        val limit = argInt(arguments["limit"], 300).coerceIn(1, 1_000)
        val quality = argString(arguments["quality"]).trim()
        val request = GitHubStarredRepositoryImportRequest(
            source = source,
            username = username,
            starListUrl = listUrl,
            apiToken = lookupConfig.apiToken,
            limit = limit
        )
        val service = GitHubRepositoryDiscoveryService(
            GitHubRepositoryDiscoveryRepository(apiToken = lookupConfig.apiToken)
        )
        return service.previewStarredRepositoryImport(
            request = request,
            existingItems = GitHubTrackStore.load()
        ).fold(
            onSuccess = { preview ->
                val selected = selectStarImportCandidates(preview.candidates, quality)
                val imported = if (apply) applyGitHubStarImport(selected) else 0
                buildString {
                    appendLine("ok=true")
                    appendLine("apply=$apply")
                    appendLine("source=${preview.sourceLabel}")
                    appendLine("totalFetched=${preview.totalFetchedCount}")
                    appendLine("candidateCount=${preview.candidates.size}")
                    appendLine("importableCount=${preview.importableCount}")
                    appendLine("alreadyTrackedCount=${preview.alreadyTrackedCount}")
                    appendLine("selectedCount=${selected.size}")
                    appendLine("importedCount=$imported")
                    appendLine("quality=${quality.ifBlank { "default" }}")
                    appendStarImportQualityCounts(preview.candidates)
                    selected.take(DEFAULT_ENTRY_LIMIT).forEachIndexed { index, candidate ->
                        appendLine(candidate.toMcpImportCandidateRow("selected[$index]"))
                    }
                }.trim()
            },
            onFailure = { error ->
                "ok=false\nmessage=${error.message ?: error.javaClass.simpleName}"
            }
        )
    }

    private fun buildStarApkVerificationText(repoUrls: String, limit: Int): String {
        val lookupConfig = GitHubTrackStore.loadSnapshot().lookupConfig
        val refreshIntervalHours = GitHubTrackStore.loadSnapshot().refreshIntervalHours
        val verifier = GitHubStarImportApkVerifier(
            source = GitHubApkPackageNameScanRepository(),
            cache = GitHubStarImportApkVerificationCacheStore
        )
        val candidates = repoUrls.parseGitHubRepoUrls()
            .mapNotNull { url -> url.toSyntheticGitHubImportCandidate() }
            .take(limit)
        if (candidates.isEmpty()) return "ok=false\nmessage=repoUrls_required"
        return buildString {
            appendLine("ok=true")
            appendLine("requested=${candidates.size}")
            candidates.forEachIndexed { index, candidate ->
                val verification = verifier.verify(
                    candidate = candidate,
                    lookupConfig = lookupConfig,
                    refreshIntervalHours = refreshIntervalHours
                )
                appendLine(
                    "repo[$index]=${candidate.repository.fullName} | status:${verification.status.name} | tag:${verification.releaseTag} | apkCount:${verification.apkAssetCount} | asset:${verification.sampleAssetName} | cache:${verification.fromCache} | error:${verification.errorMessage}"
                )
            }
        }.trim()
    }

    private fun normalizeStarImportSource(raw: String): GitHubStarredRepositoryImportSource {
        return when (raw.trim().lowercase(Locale.ROOT)) {
            "me", "mine", "auth", "authenticated", "user_starred" ->
                GitHubStarredRepositoryImportSource.AuthenticatedUser

            "user", "public", "public_user" -> GitHubStarredRepositoryImportSource.PublicUser
            "list", "star_list", "star-list" -> GitHubStarredRepositoryImportSource.StarListUrl
            else -> GitHubStarredRepositoryImportSource.Auto
        }
    }

    private fun selectStarImportCandidates(
        candidates: List<GitHubRepositoryImportCandidate>,
        quality: String
    ): List<GitHubRepositoryImportCandidate> {
        val normalized = quality.trim().lowercase(Locale.ROOT)
        return candidates.filter { candidate ->
            if (candidate.alreadyTracked) return@filter false
            val candidateQuality = GitHubStarImportClassifier.classify(candidate)
            when (normalized) {
                "all" -> true
                "android", "apk", "likely_android" ->
                    candidateQuality == GitHubStarImportQuality.LikelyAndroid

                "review", "needs_review" -> candidateQuality == GitHubStarImportQuality.NeedsReview
                "other", "other_platform" -> candidateQuality == GitHubStarImportQuality.OtherPlatform
                "archived", "fork", "archived_or_fork" ->
                    candidateQuality == GitHubStarImportQuality.ArchivedOrFork

                else ->
                    candidateQuality == GitHubStarImportQuality.LikelyAndroid ||
                            candidateQuality == GitHubStarImportQuality.NeedsReview
            }
        }
    }

    private fun applyGitHubStarImport(candidates: List<GitHubRepositoryImportCandidate>): Int {
        if (candidates.isEmpty()) return 0
        val selectedItems = candidates.map { it.trackedApp }
        val existing = GitHubTrackStore.load()
        val merged = existing.toMutableList()
        val indexById = merged.withIndex().associate { it.value.id to it.index }.toMutableMap()
        var changedCount = 0
        selectedItems.forEach { item ->
            val existingIndex = indexById[item.id]
            if (existingIndex == null) {
                merged += item
                indexById[item.id] = merged.lastIndex
                changedCount += 1
            } else if (merged[existingIndex] != item) {
                merged[existingIndex] = item
                changedCount += 1
            }
        }
        if (changedCount == 0) return 0
        GitHubTrackStore.save(merged)
        selectedItems.forEach { item ->
            GitHubTrackStoreSignals.requestTrackRefresh(
                trackId = item.id,
                notifyChangeSignal = false
            )
        }
        GitHubTrackStoreSignals.notifyChanged()
        AppBackgroundScheduler.scheduleGitHubRefresh(appContext)
        return changedCount
    }

}
