package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.launch
import os.kei.feature.github.data.local.GitHubActionsRecommendedRunStore
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.model.GitHubCheckCacheEntry
import os.kei.feature.github.model.GitHubDirectApkRemoteHealth
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.forTrackedItem
import os.kei.feature.github.model.hasSameGitHubTrackingConfigIgnoringLocalAppType
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isValidForTrackedItem
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.share.toShareImportAttachCandidate
import os.kei.ui.page.main.github.share.toShareImportPreview
import os.kei.ui.page.main.github.share.toShareImportResult
import os.kei.ui.page.main.github.share.toShareImportTrack
import os.kei.ui.page.main.github.state.toCacheEntry
import os.kei.ui.page.main.github.state.toUi

internal fun GitHubRefreshActions.launchDeferredTrackStoreSyncIfNeeded() {
    if (!consumeDeferredTrackStoreSyncAfterRefresh()) return
    scope.launch {
        syncSnapshotFromStore(forceRefreshApps = false)
    }
}

internal fun GitHubRefreshActions.consumeDeferredTrackStoreSyncAfterRefresh(): Boolean {
    val pending = state.deferredTrackStoreSyncAfterRefresh
    state.deferredTrackStoreSyncAfterRefresh = false
    return pending
}

internal suspend fun GitHubRefreshActions.persistCheckCacheNow(refreshTimestamp: Long = System.currentTimeMillis()) {
    repository.saveCheckCache(buildCheckCacheEntries(), refreshTimestamp)
}

internal fun GitHubRefreshActions.buildCheckCacheEntries(): Map<String, GitHubCheckCacheEntry> =
    state.trackedItems.associate { item ->
        val itemState = state.checkStates[item.id] ?: VersionCheckUi()
        item.id to itemState.toCacheEntry()
    }

internal fun GitHubRefreshActions.mergeDirectApkRemoteFallback(
    item: GitHubTrackedApp,
    resolvedState: VersionCheckUi,
    previousState: VersionCheckUi,
): VersionCheckUi {
    if (!item.isDirectApkTrack() || !resolvedState.failed) return resolvedState
    if (!previousState.hasDirectApkReusableRemoteResult()) return resolvedState
    return previousState.copy(
        loading = false,
        localVersion = resolvedState.localVersion,
        localVersionCode = resolvedState.localVersionCode,
        failed = false,
        directApkRemoteHealth = GitHubDirectApkRemoteHealth.Degraded,
        directApkRemoteHealthMessage =
            resolvedState.directApkRemoteHealthMessage
                .ifBlank { resolvedState.message },
        directApkRemoteCheckedAtMillis =
            resolvedState.directApkRemoteCheckedAtMillis
                .takeIf { it > 0L } ?: clock.nowMs(),
    )
}

internal fun VersionCheckUi.hasDirectApkReusableRemoteResult(): Boolean =
    latestStableApkVersion != null ||
        latestStableRawTag.isNotBlank() ||
        latestTag.isNotBlank() ||
        latestStableName.isNotBlank()

internal fun elapsedMsSince(startNs: Long): Long = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)

internal suspend fun GitHubRefreshActions.resolveItemState(
    item: GitHubTrackedApp,
    profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
    forceRefresh: Boolean = false,
): VersionCheckUi =
    repository.evaluateTrackedApp(
        context = context,
        item = item,
        profilePurposeOverride = profilePurposeOverride,
        forceRefresh = forceRefresh,
    )

internal suspend fun GitHubRefreshActions.applyTrackSnapshot(trackSnapshot: GitHubTrackSnapshot) {
    val previousItemsById = state.trackedItems.associateBy { it.id }
    val previousCheckStatesById = state.checkStates.toMap()
    val activeStrategyId = trackSnapshot.lookupConfig.selectedStrategy.storageId
    val snapshotAssetSourceSignature = state.buildAssetSourceSignature(trackSnapshot.lookupConfig)
    val incomingItemsById = trackSnapshot.items.associateBy { it.id }
    val changedAssetTrackIds =
        (previousItemsById.keys - incomingItemsById.keys) +
            incomingItemsById
                .filter { (trackId, incomingItem) ->
                    previousItemsById[trackId]?.let { previousItem ->
                        !previousItem.hasSameGitHubTrackingConfigIgnoringLocalAppType(
                            incomingItem,
                        )
                    } == true
                }.keys
    val assetSignatureChanged =
        state.assetSourceSignature.isNotBlank() &&
            state.assetSourceSignature != snapshotAssetSourceSignature
    state.lookupConfig = trackSnapshot.lookupConfig
    state.selectedStrategyInput = trackSnapshot.lookupConfig.selectedStrategy
    state.selectedActionsStrategyInput = trackSnapshot.lookupConfig.actionsStrategy
    state.githubApiTokenInput = trackSnapshot.lookupConfig.apiToken
    state.checkAllTrackedPreReleasesInput = trackSnapshot.lookupConfig.checkAllTrackedPreReleases
    state.checkAllDirectApkPreReleasesInput =
        trackSnapshot.lookupConfig.checkAllDirectApkPreReleases
    state.aggressiveApkFilteringInput = trackSnapshot.lookupConfig.aggressiveApkFiltering
    state.preciseApkVersionEnabledInput = trackSnapshot.lookupConfig.preciseApkVersionEnabled
    state.profileDepthInput = trackSnapshot.lookupConfig.profileDepth
    state.shareImportFlowModeInput = trackSnapshot.lookupConfig.shareImportFlowMode
    state.appManagedShareInstallEnabledInput =
        trackSnapshot.lookupConfig.appManagedShareInstallEnabled
    state.scanSystemAppsByDefaultInput = trackSnapshot.lookupConfig.scanSystemAppsByDefault
    state.onlineShareTargetPackageInput = trackSnapshot.lookupConfig.onlineShareTargetPackage
    state.preferredDownloaderPackageInput = trackSnapshot.lookupConfig.preferredDownloaderPackage
    state.refreshIntervalHours = trackSnapshot.refreshIntervalHours
    when {
        assetSignatureChanged -> {
            assetActions.clearAllApkAssetStateAndCacheNow()
        }

        changedAssetTrackIds.isNotEmpty() -> {
            val staleAssetTargets =
                changedAssetTrackIds.mapNotNull { trackId ->
                    previousItemsById[trackId]?.let { item ->
                        item to (previousCheckStatesById[trackId] ?: VersionCheckUi())
                    }
                }
            assetActions.clearApkAssetStatesAndCachesNow(
                targets = staleAssetTargets,
                clearItemIds = changedAssetTrackIds,
            )
        }
    }
    state.assetSourceSignature = snapshotAssetSourceSignature

    state.trackedItems.clear()
    state.trackedItems.addAll(trackSnapshot.items)
    val validItemIds = trackSnapshot.items.map { it.id }.toSet()
    state.retainTrackedUiState(validItemIds)
    env.viewModel.retainTrackedExpansion(validItemIds)
    repository.retainTrackedReleaseExpansion(validItemIds)
    state.trackedFirstInstallAtByPackage.clear()
    state.trackedFirstInstallAtByPackage.putAll(trackSnapshot.trackedFirstInstallAtByPackage)
    state.retainTrackedFirstInstallAtByTrackedItems()
    state.trackedAddedAtById.clear()
    state.trackedAddedAtById.putAll(trackSnapshot.trackedAddedAtById)
    state.retainTrackedAddedAtByTrackedItems()
    state.trackedModifiedAtById.clear()
    state.trackedModifiedAtById.putAll(trackSnapshot.trackedModifiedAtById)
    state.retainTrackedModifiedAtByTrackedItems()
    state.actionsRecommendedRunSnapshots.clear()
    trackSnapshot.items.filter { it.checkActionsUpdates }.forEach { item ->
        GitHubActionsRecommendedRunStore.load(item.id)?.let { snapshot ->
            state.actionsRecommendedRunSnapshots[item.id] = snapshot
        }
    }
    state.pendingShareImportTrack = trackSnapshot.pendingShareImportTrack?.toShareImportTrack()
    state.pendingShareImportPreview =
        GitHubShareImportFlowStore
            .loadActivePreview()
            ?.toShareImportPreview()
    state.pendingShareImportAttachCandidate =
        GitHubShareImportFlowStore
            .loadActiveAttachCandidate()
            ?.toShareImportAttachCandidate()
    state.pendingShareImportResult =
        GitHubShareImportFlowStore
            .loadActiveResult()
            ?.toShareImportResult()

    val cachedStates = trackSnapshot.checkCache
    val nextCheckStates = linkedMapOf<String, VersionCheckUi>()
    state.checkStates.clear()
    trackSnapshot.items.forEach { item ->
        val itemLookupConfig = trackSnapshot.lookupConfig.forTrackedItem(item)
        cachedStates[item.id]
            ?.takeIf { cache ->
                cache.isValidForTrackedItem(
                    item = item,
                    lookupConfig = itemLookupConfig,
                    activeStrategyId = activeStrategyId,
                )
            }?.let { cached ->
                nextCheckStates[item.id] = cached.toUi()
            }
    }
    state.checkStates.putAll(nextCheckStates)
    val externallyChangedCheckTrackIds =
        nextCheckStates
            .filter { (trackId, nextState) ->
                previousCheckStatesById[trackId]?.let { it != nextState } == true
            }.keys
    if (!assetSignatureChanged && changedAssetTrackIds.isEmpty() && externallyChangedCheckTrackIds.isNotEmpty()) {
        val staleAssetTargets =
            externallyChangedCheckTrackIds.mapNotNull { trackId ->
                previousItemsById[trackId]?.let { item ->
                    item to (previousCheckStatesById[trackId] ?: VersionCheckUi())
                }
            }
        assetActions.clearApkAssetStatesAndCachesNow(
            targets = staleAssetTargets,
            clearItemIds = externallyChangedCheckTrackIds,
        )
    }
    state.lastRefreshMs =
        if (state.checkStates.isNotEmpty()) {
            trackSnapshot.lastRefreshMs
        } else {
            0L
        }
}
