package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.launch
import os.kei.R
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.data.remote.directApkFileNameFromUrl
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitRepositoryTrackIdentity
import os.kei.feature.github.model.buildGitRepositoryTrackIdentity
import os.kei.feature.github.model.forTrackedItem
import os.kei.feature.github.model.githubReleaseLookupItemOrNull
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isGitRepositoryTrack
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.page.releaseNotesApkVersionKey
import os.kei.ui.page.main.github.statusActionUrl

internal class GitHubReleaseNotesActions(
    private val env: GitHubPageActionEnvironment,
    private val apkInfoRepository: GitHubApkInfoRepository,
) {
    private val context get() = env.context
    private val scope get() = env.scope
    private val state get() = env.state
    private val repository get() = env.repository
    private val clock get() = env.clock

    fun loadReleaseNotes(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        clearCache: Boolean,
    ) {
        if (clearCache) {
            loadReleaseNotesTargets(
                item = item,
                itemState = itemState,
                forceRefresh = true,
            )
            return
        }
        val selectedTarget = state.releaseNotesSelectedTargets[item.id]
        if (selectedTarget == null) {
            loadReleaseNotesTargets(
                item = item,
                itemState = itemState,
                forceRefresh = clearCache,
            )
            return
        }
        loadReleaseNotesBundle(
            item = item,
            target = selectedTarget,
            clearCache = clearCache,
        )
    }

    fun loadReleaseNotesTargets(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        forceRefresh: Boolean = false,
    ) {
        val cachedTargets = state.releaseNotesTargets[item.id].orEmpty()
        val cachedSelected = state.releaseNotesSelectedTargets[item.id]
        directApkReleaseNotesTarget(item, itemState)?.let { directTarget ->
            val directTargets = listOf(directTarget)
            val selectedTarget =
                selectRefreshedReleaseNotesTarget(
                    previousTarget = cachedSelected,
                    targets = directTargets,
                    preferPreRelease = item.preferPreRelease,
                ) ?: directTarget
            state.releaseNotesTargets[item.id] = directTargets
            state.releaseNotesTargetsLoadedAtMs[item.id] = clock.nowMs()
            state.releaseNotesSelectedTargets[item.id] = selectedTarget
            state.releaseNotesErrors.remove(item.id)
            loadReleaseNotesBundle(
                item = item,
                target = selectedTarget,
                clearCache = forceRefresh,
            )
            return
        }
        if (
            !forceRefresh &&
            cachedTargets.isNotEmpty() &&
            cachedSelected != null &&
            isRuntimeCacheFresh(state.releaseNotesTargetsLoadedAtMs[item.id])
        ) {
            loadReleaseNotesBundle(item = item, target = cachedSelected, clearCache = false)
            return
        }
        state.releaseNotesLoading[item.id] = true
        state.releaseNotesErrors.remove(item.id)
        scope.launch {
            val lookupConfig = state.lookupConfig.forTrackedItem(item)
            val gitIdentity = gitRepositoryReleaseIdentityOrNull(item)
            val repositoryItem = item.githubReleaseLookupItemOrNull() ?: item
            val remoteTargets =
                if (gitIdentity != null) {
                    repository.fetchGitRepositoryReleaseNotesTargets(gitIdentity)
                } else {
                    repository.fetchReleaseNotesTargets(
                        owner = repositoryItem.owner,
                        repo = repositoryItem.repo,
                        apiToken = lookupConfig.apiToken,
                    )
                }.getOrElse { error ->
                    fallbackReleaseNotesTargets(item, itemState).ifEmpty {
                        state.releaseNotesLoading[item.id] = false
                        state.releaseNotesErrors[item.id] = error.message
                            ?: context.getString(R.string.github_error_load_apk_assets_failed)
                        return@launch
                    }
                }
            val selectedTarget =
                selectRefreshedReleaseNotesTarget(
                    previousTarget = cachedSelected,
                    targets = remoteTargets,
                    preferPreRelease = item.preferPreRelease,
                )
            if (selectedTarget == null) {
                state.releaseNotesLoading[item.id] = false
                state.releaseNotesErrors[item.id] =
                    context.getString(R.string.github_release_notes_detail_empty)
                return@launch
            }
            state.releaseNotesTargets[item.id] = remoteTargets
            state.releaseNotesTargetsLoadedAtMs[item.id] = clock.nowMs()
            state.releaseNotesSelectedTargets[item.id] = selectedTarget
            state.releaseNotesBundles.remove(item.id)
            state.releaseNotesBundleLoadedAtMs.remove(item.id)
            if (forceRefresh) {
                state.releaseNotesApkVersions.keys.removeAll { key -> key.startsWith("${item.id}|") }
            }
            loadReleaseNotesBundle(
                item = item,
                target = selectedTarget,
                clearCache = forceRefresh,
            )
        }
    }

    fun selectReleaseNotesTarget(
        item: GitHubTrackedApp,
        target: GitHubReleaseNotesTarget,
    ) {
        state.releaseNotesSelectedTargets[item.id] = target
        state.releaseNotesBundles.remove(item.id)
        state.releaseNotesErrors.remove(item.id)
        loadReleaseNotesBundle(item = item, target = target, clearCache = false)
    }

    private fun loadReleaseNotesBundle(
        item: GitHubTrackedApp,
        target: GitHubReleaseNotesTarget,
        clearCache: Boolean,
    ) {
        directApkReleaseNotesBundle(item, target)?.let { bundle ->
            val key = releaseNotesApkVersionKey(item.id, target)
            state.releaseNotesLoading[item.id] = false
            state.releaseNotesErrors.remove(item.id)
            state.releaseNotesBundles[item.id] = bundle
            state.releaseNotesBundleLoadedAtMs[item.id] = clock.nowMs()
            state.checkStates[item.id]
                ?.latestStableApkVersion
                ?.takeIf { it.hasVersion() }
                ?.let { state.releaseNotesApkVersions[key] = it }
            return
        }
        val lookupConfig = state.lookupConfig.forTrackedItem(item)
        val cachedBundle = state.releaseNotesBundles[item.id]
        if (
            !clearCache &&
            cachedBundle != null &&
            isRuntimeCacheFresh(state.releaseNotesBundleLoadedAtMs[item.id]) &&
            state.matchesAssetSourceSignature(cachedBundle, lookupConfig) &&
            cachedBundle.tagName.equals(target.tagName, ignoreCase = true) &&
            cachedBundle.releaseNotesBody.isNotBlank()
        ) {
            state.releaseNotesLoading[item.id] = false
            state.releaseNotesErrors.remove(item.id)
            resolveReleaseNotesApkVersionIfNeeded(
                item = item,
                target = target,
                bundle = cachedBundle,
                lookupConfig = lookupConfig,
                forceRefresh = false,
            )
            return
        }
        state.releaseNotesLoading[item.id] = true
        state.releaseNotesErrors.remove(item.id)
        scope.launch {
            val gitIdentity = gitRepositoryReleaseIdentityOrNull(item)
            val repositoryItem = item.githubReleaseLookupItemOrNull() ?: item
            val preferHtml = lookupConfig.selectedStrategy == GitHubLookupStrategyOption.AtomFeed
            val cacheKey =
                if (gitIdentity != null) {
                    repository.buildGitRepositoryAssetCacheKey(
                        identity = gitIdentity,
                        rawTag = target.tagName,
                        releaseUrl = target.htmlUrl,
                        lookupConfig = lookupConfig,
                        includeAllAssets = true,
                    )
                } else {
                    repository.buildAssetCacheKey(
                        owner = repositoryItem.owner,
                        repo = repositoryItem.repo,
                        rawTag = target.tagName,
                        releaseUrl = target.htmlUrl,
                        preferHtml = preferHtml,
                        aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
                        includeAllAssets = true,
                        hasApiToken = lookupConfig.apiToken.isNotBlank(),
                    )
                }
            if (clearCache) {
                repository.clearAssetCache(cacheKey)
            }
            val refreshIntervalHours = repository.loadRefreshIntervalHours()
            val persistedBundle =
                if (clearCache) {
                    null
                } else {
                    repository.loadAssetBundle(cacheKey, refreshIntervalHours)
                }
            if (state.releaseNotesSelectedTargets[item.id]?.id != target.id) return@launch
            if (
                persistedBundle != null &&
                state.matchesAssetSourceSignature(persistedBundle, lookupConfig) &&
                persistedBundle.releaseNotesBody.isNotBlank()
            ) {
                state.releaseNotesLoading[item.id] = false
                state.releaseNotesBundles[item.id] = persistedBundle
                state.releaseNotesBundleLoadedAtMs[item.id] = clock.nowMs()
                resolveReleaseNotesApkVersionIfNeeded(
                    item = item,
                    target = target,
                    bundle = persistedBundle,
                    lookupConfig = lookupConfig,
                    forceRefresh = false,
                )
                return@launch
            } else if (persistedBundle != null) {
                repository.clearAssetCache(cacheKey)
            }
            val bundleResult =
                if (gitIdentity != null) {
                    repository.fetchGitRepositoryReleaseAssetBundle(
                        identity = gitIdentity,
                        rawTag = target.tagName,
                        releaseUrl = target.htmlUrl,
                        lookupConfig = lookupConfig,
                        includeAllAssets = true,
                    )
                } else {
                    repository.fetchApkAssets(
                        owner = repositoryItem.owner,
                        repo = repositoryItem.repo,
                        rawTag = target.tagName,
                        releaseUrl = target.htmlUrl,
                        preferHtml = preferHtml,
                        aggressiveFiltering = lookupConfig.aggressiveApkFiltering,
                        includeAllAssets = true,
                        apiToken = lookupConfig.apiToken,
                    )
                }
            bundleResult.onSuccess { bundle ->
                if (state.releaseNotesSelectedTargets[item.id]?.id != target.id) return@onSuccess
                val persisted =
                    bundle.copy(
                        sourceConfigSignature = state.buildAssetSourceSignature(lookupConfig),
                    )
                state.releaseNotesLoading[item.id] = false
                state.releaseNotesBundles[item.id] = persisted
                state.releaseNotesBundleLoadedAtMs[item.id] = clock.nowMs()
                repository.saveAssetBundle(cacheKey, persisted)
                resolveReleaseNotesApkVersionIfNeeded(
                    item = item,
                    target = target,
                    bundle = persisted,
                    lookupConfig = lookupConfig,
                    forceRefresh = true,
                )
            }.onFailure { error ->
                if (state.releaseNotesSelectedTargets[item.id]?.id != target.id) return@onFailure
                state.releaseNotesLoading[item.id] = false
                state.releaseNotesErrors[item.id] = error.message
                    ?: context.getString(R.string.github_error_load_apk_assets_failed)
            }
        }
    }

    private fun isRuntimeCacheFresh(
        loadedAtMs: Long?,
        nowMs: Long = clock.nowMs(),
    ): Boolean {
        val loadedAt = loadedAtMs ?: return false
        if (loadedAt <= 0L) return false
        val intervalMs = state.refreshIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
        return (nowMs - loadedAt).coerceAtLeast(0L) < intervalMs
    }

    private fun gitRepositoryReleaseIdentityOrNull(
        item: GitHubTrackedApp,
    ): GitRepositoryTrackIdentity? {
        if (!item.isGitRepositoryTrack()) return null
        if (item.githubReleaseLookupItemOrNull() != null) return null
        return buildGitRepositoryTrackIdentity(item.repoUrl)
    }

    private fun fallbackReleaseNotesTargets(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
    ): List<GitHubReleaseNotesTarget> =
        buildList {
            directApkReleaseNotesTarget(item, itemState)?.let(::add)
            itemState.latestStableRawTag.trim().takeIf { it.isNotBlank() }?.let { tag ->
                add(
                    GitHubReleaseNotesTarget(
                        releaseName = itemState.latestStableName.trim().ifBlank { tag },
                        tagName = tag,
                        htmlUrl =
                            itemState.latestStableUrl.trim().ifBlank {
                                itemState.statusActionUrl(item.owner, item.repo)
                            },
                        prerelease = false,
                        latestInChannel = true,
                        updatedAtMillis =
                            itemState.latestStableUpdatedAtMillis
                                .takeIf { it > 0L },
                    ),
                )
            }
            itemState.latestPreRawTag.trim().takeIf { it.isNotBlank() }?.let { tag ->
                add(
                    GitHubReleaseNotesTarget(
                        releaseName = itemState.latestPreName.trim().ifBlank { tag },
                        tagName = tag,
                        htmlUrl =
                            itemState.latestPreUrl.trim().ifBlank {
                                itemState.statusActionUrl(item.owner, item.repo)
                            },
                        prerelease = true,
                        latestInChannel = true,
                        updatedAtMillis =
                            itemState.latestPreUpdatedAtMillis
                                .takeIf { it > 0L },
                    ),
                )
            }
        }.distinctBy { it.id.lowercase() }

    private fun directApkReleaseNotesTarget(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
    ): GitHubReleaseNotesTarget? {
        if (!item.isDirectApkTrack()) return null
        val preInfo =
            itemState.latestPreApkVersion
                ?.takeIf { info -> info.releaseNotes.isNotBlank() }
        val stableInfo =
            itemState.latestStableApkVersion
                ?.takeIf { info -> info.releaseNotes.isNotBlank() }
        val info =
            preInfo ?: stableInfo
                ?: return null
        val prerelease = preInfo != null
        val tag =
            info
                .versionLabel()
                .ifBlank { info.releaseTag.trim() }
                .ifBlank {
                    if (prerelease) itemState.latestPreRawTag.trim() else itemState.latestStableRawTag.trim()
                }.ifBlank {
                    if (prerelease) itemState.preReleaseInfo.trim() else itemState.latestTag.trim()
                }.ifBlank { info.assetName.trim() }
                .ifBlank { item.repo.trim() }
        return GitHubReleaseNotesTarget(
            releaseName =
                info.releaseName
                    .trim()
                    .ifBlank {
                        if (prerelease) itemState.latestPreName.trim() else itemState.latestStableName.trim()
                    }.ifBlank { item.appLabel.trim() }
                    .ifBlank { item.repo.trim() },
            tagName = tag,
            htmlUrl = info.releaseUrl.trim().ifBlank { item.repoUrl.trim() },
            prerelease = prerelease,
            latestInChannel = true,
            updatedAtMillis =
                if (prerelease) {
                    itemState.latestPreUpdatedAtMillis.takeIf { it > 0L }
                } else {
                    itemState.latestStableUpdatedAtMillis.takeIf { it > 0L }
                },
        )
    }

    private fun directApkReleaseNotesBundle(
        item: GitHubTrackedApp,
        target: GitHubReleaseNotesTarget,
    ): GitHubReleaseAssetBundle? {
        if (!item.isDirectApkTrack()) return null
        val itemState = state.checkStates[item.id] ?: return null
        val info =
            if (target.prerelease) {
                itemState.latestPreApkVersion
            } else {
                itemState.latestStableApkVersion
            }?.takeIf { version -> version.releaseNotes.isNotBlank() }
                ?: return null
        val updatedAtMillis =
            if (target.prerelease) {
                itemState.latestPreUpdatedAtMillis.takeIf { it > 0L }
            } else {
                itemState.latestStableUpdatedAtMillis.takeIf { it > 0L }
            }
        val assetUrl =
            info.fetchSource
                .trim()
                .ifBlank { info.releaseUrl.trim() }
                .ifBlank { item.repoUrl.trim() }
        val assetName =
            info.assetName
                .trim()
                .ifBlank { directApkFileNameFromUrl(assetUrl) }
                .ifBlank { "remote.apk" }
        return GitHubReleaseAssetBundle(
            releaseName = target.releaseName,
            tagName = target.tagName,
            htmlUrl = target.htmlUrl.ifBlank { item.repoUrl },
            releaseUpdatedAtMillis = updatedAtMillis,
            releaseNotesBody = info.releaseNotes,
            assets =
                listOf(
                    GitHubReleaseAssetFile(
                        name = assetName,
                        downloadUrl = assetUrl,
                        sizeBytes = 0L,
                        downloadCount = 0,
                        contentType = "application/vnd.android.package-archive",
                        updatedAtMillis = updatedAtMillis,
                    ),
                ),
            showingAllAssets = true,
            fetchSource = DIRECT_APK_RELEASE_NOTES_FETCH_SOURCE,
        )
    }

    private fun resolveReleaseNotesApkVersionIfNeeded(
        item: GitHubTrackedApp,
        target: GitHubReleaseNotesTarget,
        bundle: GitHubReleaseAssetBundle,
        lookupConfig: GitHubLookupConfig,
        forceRefresh: Boolean,
    ) {
        if (!lookupConfig.preciseApkVersionEnabled) return
        val key = releaseNotesApkVersionKey(item.id, target)
        if (!forceRefresh && state.releaseNotesApkVersions[key]?.hasVersion() == true) return
        val apkAssets =
            bundle.assets
                .filter { asset -> asset.name.endsWith(".apk", ignoreCase = true) }
                .take(MAX_RELEASE_NOTES_APK_VERSION_CANDIDATES)
        if (apkAssets.isEmpty()) return
        scope.launch {
            val resolved =
                resolveReleaseNotesApkVersion(
                    item = item,
                    target = target,
                    bundle = bundle,
                    apkAssets = apkAssets,
                    lookupConfig = lookupConfig,
                    forceRefresh = forceRefresh,
                )
            if (state.releaseNotesSelectedTargets[item.id]?.id != target.id) return@launch
            resolved?.takeIf { it.hasVersion() }?.let { state.releaseNotesApkVersions[key] = it }
        }
    }

    private suspend fun resolveReleaseNotesApkVersion(
        item: GitHubTrackedApp,
        target: GitHubReleaseNotesTarget,
        bundle: GitHubReleaseAssetBundle,
        apkAssets: List<GitHubReleaseAssetFile>,
        lookupConfig: GitHubLookupConfig,
        forceRefresh: Boolean,
    ): GitHubRemoteApkVersionInfo? {
        val inspected =
            GitHubExecution.mapOrderedBounded(
                items = apkAssets,
                maxConcurrency = MAX_RELEASE_NOTES_APK_VERSION_PARALLEL,
            ) { asset ->
                asset to
                    apkInfoRepository.inspect(
                        asset = asset,
                        lookupConfig = lookupConfig,
                        forceRefresh = forceRefresh,
                    )
            }
        val requestedPackageName = item.packageName.trim()
        val matched =
            inspected.firstNotNullOfOrNull { (asset, result) ->
                result
                    .getOrNull()
                    ?.takeIf { info ->
                        info.versionName.isNotBlank() || info.versionCode.isNotBlank()
                    }?.takeIf { info ->
                        requestedPackageName.isBlank() ||
                            info.packageName.equals(requestedPackageName, ignoreCase = true)
                    }?.let { info -> asset to info }
            }
        val fallback =
            inspected.firstNotNullOfOrNull { (asset, result) ->
                result
                    .getOrNull()
                    ?.takeIf { info ->
                        info.versionName.isNotBlank() || info.versionCode.isNotBlank()
                    }?.let { info -> asset to info }
            }
        val (asset, info) = matched ?: fallback ?: return null
        return GitHubRemoteApkVersionInfo(
            releaseName = bundle.releaseName.ifBlank { target.releaseName },
            releaseTag = bundle.tagName.ifBlank { target.tagName },
            releaseUrl = bundle.htmlUrl.ifBlank { target.htmlUrl },
            assetName = asset.name,
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = info.versionCode,
            fetchSource = info.fetchSource.ifBlank { bundle.fetchSource },
        )
    }

    private fun selectDefaultReleaseNotesTarget(
        targets: List<GitHubReleaseNotesTarget>,
        preferPreRelease: Boolean,
    ): GitHubReleaseNotesTarget? {
        val preferred =
            if (preferPreRelease) {
                targets.firstOrNull { it.prerelease && it.latestInChannel }
                    ?: targets.firstOrNull { it.prerelease }
            } else {
                targets.firstOrNull { !it.prerelease && it.latestInChannel }
                    ?: targets.firstOrNull { !it.prerelease }
            }
        return preferred ?: targets.firstOrNull()
    }

    private fun selectRefreshedReleaseNotesTarget(
        previousTarget: GitHubReleaseNotesTarget?,
        targets: List<GitHubReleaseNotesTarget>,
        preferPreRelease: Boolean,
    ): GitHubReleaseNotesTarget? {
        previousTarget?.let { previous ->
            targets.firstOrNull { it.id.equals(previous.id, ignoreCase = true) }?.let { return it }
            targets
                .firstOrNull {
                    it.tagName.equals(previous.tagName, ignoreCase = true) &&
                        it.htmlUrl.equals(previous.htmlUrl, ignoreCase = true)
                }?.let { return it }
            targets
                .firstOrNull {
                    it.tagName.equals(previous.tagName, ignoreCase = true)
                }?.let { return it }
        }
        return selectDefaultReleaseNotesTarget(
            targets = targets,
            preferPreRelease = preferPreRelease,
        )
    }

    private companion object {
        const val DIRECT_APK_RELEASE_NOTES_FETCH_SOURCE = "subscription"
        const val MAX_RELEASE_NOTES_APK_VERSION_CANDIDATES = 4
        const val MAX_RELEASE_NOTES_APK_VERSION_PARALLEL = 2
    }
}
