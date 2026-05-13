package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import os.kei.feature.github.data.local.GitHubStarImportApkVerificationCacheStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubApkPackageNameScanRepository
import os.kei.feature.github.data.remote.GitHubRepositoryDiscoveryRepository
import os.kei.feature.github.domain.GitHubApkPackageNameScanner
import os.kei.feature.github.domain.GitHubDirectApkReleaseCheckSource
import os.kei.feature.github.domain.GitHubPackageNameValidator
import os.kei.feature.github.domain.GitHubPackageRepositoryResolver
import os.kei.feature.github.domain.GitHubRepositoryDiscoveryService
import os.kei.feature.github.domain.GitHubStarImportApkVerifier
import os.kei.feature.github.domain.GitHubStarImportApplier
import os.kei.feature.github.domain.GitHubStarImportClassifier
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportQuality
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.feature.github.model.GitHubStarredRepositoryImportSource
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.buildDirectApkTrackIdentity
import os.kei.feature.github.model.forTrackedItem
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
            val expectedPackageName =
                argString(request.arguments?.get("expectedPackageName")).trim()
            buildRepoPackageScanText(
                repoUrl = repoUrl,
                expectedPackageName = expectedPackageName
            )
        }

        server.addMcpTextTool(environment, name = "keios.github.direct_apk.inspect") { request ->
            val url = argString(request.arguments?.get("url")).trim()
            val expectedPackageName =
                argString(request.arguments?.get("expectedPackageName")).trim()
            val appLabel = argString(request.arguments?.get("appLabel")).trim()
            val forceRefresh = argBoolean(request.arguments?.get("forceRefresh"), false)
            buildDirectApkInspectText(
                url = url,
                expectedPackageName = expectedPackageName,
                appLabel = appLabel,
                forceRefresh = forceRefresh
            )
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
            appendLine("actionsStrategy=${snapshot.lookupConfig.actionsStrategy.storageId}")
            appendLine("apiTokenConfigured=${snapshot.lookupConfig.apiToken.isNotBlank()}")
            appendLine("refreshIntervalHours=${snapshot.refreshIntervalHours}")
            appendLine("checkAllTrackedPreReleases=${snapshot.lookupConfig.checkAllTrackedPreReleases}")
            appendLine("aggressiveApkFiltering=${snapshot.lookupConfig.aggressiveApkFiltering}")
            appendLine("preciseApkVersionEnabled=${snapshot.lookupConfig.preciseApkVersionEnabled}")
            appendLine("scanSystemAppsByDefault=${snapshot.lookupConfig.scanSystemAppsByDefault}")
            appendLine("profileDepth=${snapshot.lookupConfig.profileDepth.storageId}")
            appendLine("shareImportLinkageEnabled=${snapshot.lookupConfig.shareImportLinkageEnabled}")
            appendLine("shareImportFlowMode=${snapshot.lookupConfig.shareImportFlowMode.storageId}")
            appendLine("appManagedShareInstallEnabled=${snapshot.lookupConfig.appManagedShareInstallEnabled}")
            appendLine("repositoryHealthCardEnabled=${snapshot.lookupConfig.repositoryHealthCardEnabled}")
            appendLine("apkTrustCheckEnabled=${snapshot.lookupConfig.apkTrustCheckEnabled}")
            appendLine("releaseNotesMode=${snapshot.lookupConfig.releaseNotesMode.storageId}")
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

    private fun buildRepoPackageScanText(repoUrl: String, expectedPackageName: String): String {
        if (repoUrl.isBlank()) return "ok=false\nmessage=repoUrl_required"
        val scanner = GitHubApkPackageNameScanner(GitHubApkPackageNameScanRepository())
        return scanner.scan(
            GitHubApkPackageNameScanRequest(
                repoUrl = repoUrl,
                lookupConfig = GitHubTrackStore.loadLookupConfig(),
                expectedPackageName = expectedPackageName
            )
        ).fold(
            onSuccess = { result ->
                buildString {
                    appendLine("ok=true")
                    appendLine("owner=${result.owner}")
                    appendLine("repo=${result.repo}")
                    appendLine("packageName=${result.packageName}")
                    appendLine("expectedPackageName=$expectedPackageName")
                    appendLine(
                        "packageMatched=${
                            expectedPackageName.isBlank() ||
                                    result.packageName.equals(
                                        expectedPackageName,
                                        ignoreCase = true
                                    )
                        }"
                    )
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

    private suspend fun buildDirectApkInspectText(
        url: String,
        expectedPackageName: String,
        appLabel: String,
        forceRefresh: Boolean
    ): String {
        val identity = buildDirectApkTrackIdentity(url)
            ?: return "ok=false\nmessage=invalid_direct_apk_url"
        val item = GitHubTrackedApp(
            repoUrl = identity.url,
            owner = identity.owner,
            repo = identity.repo,
            packageName = expectedPackageName,
            appLabel = appLabel.ifBlank { identity.displayName },
            sourceMode = GitHubTrackedSourceMode.DirectApk
        )
        val asset = GitHubDirectApkReleaseCheckSource.buildDirectApkAsset(item)
            ?: return "ok=false\nmessage=invalid_direct_apk_url"
        return GitHubApkInfoRepository().inspectAsync(
            asset = asset,
            lookupConfig = GitHubTrackStore.loadLookupConfig().forTrackedItem(item),
            forceRefresh = forceRefresh
        ).fold(
            onSuccess = { manifest ->
                buildString {
                    appendLine("ok=true")
                    appendLine("url=${identity.url}")
                    appendLine("displayName=${identity.displayName}")
                    appendLine("assetName=${manifest.assetName.ifBlank { asset.name }}")
                    appendLine("packageName=${manifest.packageName}")
                    appendLine("expectedPackageName=$expectedPackageName")
                    appendLine(
                        "packageMatched=${
                            expectedPackageName.isBlank() ||
                                    manifest.packageName.equals(
                                        expectedPackageName,
                                        ignoreCase = true
                                    )
                        }"
                    )
                    appendLine("appLabel=${manifest.appLabel}")
                    appendLine("versionName=${manifest.versionName}")
                    appendLine("versionCode=${manifest.versionCode}")
                    appendLine("minSdk=${manifest.minSdk}")
                    appendLine("targetSdk=${manifest.targetSdk}")
                    appendLine("nativeAbis=${manifest.nativeAbis.joinToString(",")}")
                    appendLine("permissionCount=${manifest.permissions.size}")
                    appendLine("featureCount=${manifest.features.size}")
                    appendLine("signatureSha256=${manifest.signatureInfo?.sha256.orEmpty()}")
                    appendLine("fetchSource=${manifest.fetchSource}")
                    appendLine("forceRefresh=$forceRefresh")
                }.trim()
            },
            onFailure = { error ->
                "ok=false\nurl=${identity.url}\nmessage=${error.message ?: error.javaClass.simpleName}"
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

    private suspend fun buildStarImportText(
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

    private suspend fun applyGitHubStarImport(candidates: List<GitHubRepositoryImportCandidate>): Int {
        return GitHubStarImportApplier.apply(
            context = appContext,
            candidates = candidates
        ).changedCount
    }

}
