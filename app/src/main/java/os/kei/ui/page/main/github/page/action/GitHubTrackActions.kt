package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.defaultKeiOsTrackedApp
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.page.GitHubTrackEditorDraft
import os.kei.ui.page.main.github.page.GitHubTrackEditorResult
import os.kei.ui.page.main.github.section.GitHubTrackedReleaseUiStateStore

internal class GitHubTrackActions(
    private val env: GitHubPageActionEnvironment,
    private val refreshActions: GitHubRefreshActions,
    private val assetActions: GitHubAssetActions
) {
    private var appListRefreshJob: Job? = null
    private val state get() = env.state
    private val scope get() = env.scope
    private val repository get() = env.repository

    fun openTrackSheetForAdd() {
        state.resetTrackEditor()
        state.showAddSheet = true
        refreshAppListForTrackSheet()
    }

    fun openTrackSheetForEdit(item: GitHubTrackedApp) {
        state.editingTrackedItem = item
        state.repoUrlInput = item.repoUrl
        state.packageNameInput = item.packageName
        state.selectedApp = state.appList.firstOrNull {
            it.packageName.equals(item.packageName, ignoreCase = true)
        }
        state.appSearch = ""
        state.pickerExpanded = false
        state.repoScanCandidates = emptyList()
        state.preferPreReleaseInput = item.preferPreRelease
        state.alwaysShowLatestReleaseDownloadButtonInput = item.alwaysShowLatestReleaseDownloadButton
        state.preciseApkVersionModeInput = item.preciseApkVersionMode
        state.showAddSheet = true
        refreshAppListForTrackSheet()
    }

    fun dismissTrackSheet() {
        state.dismissTrackSheet()
    }

    fun setTrackAppPickerExpanded(value: Boolean) {
        state.pickerExpanded = value
        if (value) {
            refreshAppListForTrackSheet()
        }
    }

    fun refreshAppListForTrackSheet() {
        if (appListRefreshJob?.isActive == true) return
        appListRefreshJob = scope.launch {
            refreshActions.reloadApps(forceRefresh = true)
            syncSelectedAppFromPackageInput()
        }
    }

    fun ensureKeiOsSelfTrack() {
        val newItem = defaultKeiOsTrackedApp()
        if (state.trackedItems.any { it.id == newItem.id }) {
            env.toast(R.string.github_toast_track_exists)
            return
        }
        state.trackedItems.add(newItem)
        state.recordTrackedAddedAt(newItem.id, System.currentTimeMillis())
        state.requestTrackCardFocus(newItem.id)
        env.saveTrackedItems(refreshTrackIds = setOf(newItem.id))
        env.toast(R.string.github_toast_track_current_app_added)
    }

    fun requestDeleteItem(item: GitHubTrackedApp) {
        if (state.deleteInProgress) return
        if (state.trackedItems.none { it.id == item.id }) return
        state.pendingDeleteItem = item
    }

    fun scanPackageNameFromRepo() {
        if (state.packageNameScanRunning || state.repoUrlScanRunning) return
        if (state.repoUrlInput.isBlank()) {
            env.toast(R.string.github_toast_fill_repo_and_select_app)
            return
        }
        state.packageNameScanRunning = true
        state.repoScanCandidates = emptyList()
        scope.launch {
            try {
                val result = repository.scanPackageNameFromLatestStableApk(
                    GitHubApkPackageNameScanRequest(
                        repoUrl = state.repoUrlInput,
                        lookupConfig = state.lookupConfig
                    )
                ).getOrElse { error ->
                    env.toast(
                        R.string.github_toast_package_scan_failed,
                        error.message.orEmpty().ifBlank {
                            env.string(R.string.github_error_package_scan_failed)
                        }
                    )
                    return@launch
                }
                refreshActions.reloadApps(forceRefresh = true)
                state.packageNameInput = result.packageName
                state.selectedApp = state.appList.firstOrNull { app ->
                    app.packageName.equals(result.packageName, ignoreCase = true)
                }
                env.toast(R.string.github_toast_package_scan_success, result.packageName)
            } finally {
                state.packageNameScanRunning = false
            }
        }
    }

    fun scanRepoUrlFromPackage() {
        if (state.repoUrlScanRunning || state.packageNameScanRunning) return
        val packageName = state.packageNameInput.trim().ifBlank {
            state.selectedApp?.packageName.orEmpty().trim()
        }
        if (packageName.isBlank()) {
            env.toast(R.string.github_toast_repo_scan_requires_package)
            return
        }
        state.repoScanCandidates = emptyList()
        val appLabel = state.selectedApp?.label?.trim().orEmpty()
            .ifBlank {
                state.appList.firstOrNull { app ->
                    app.packageName.equals(packageName, ignoreCase = true)
                }?.label?.trim().orEmpty()
            }
        state.repoUrlScanRunning = true
        scope.launch {
            try {
                val result = repository.scanRepositoryFromPackage(
                    GitHubPackageRepositoryScanRequest(
                        packageName = packageName,
                        appLabel = appLabel,
                        preferredRepoUrl = state.repoUrlInput.trim(),
                        lookupConfig = state.lookupConfig
                    )
                ).getOrElse { error ->
                    env.toast(
                        R.string.github_toast_repo_scan_failed,
                        error.message.orEmpty().ifBlank {
                            env.string(R.string.github_error_repo_scan_failed)
                        }
                    )
                    return@launch
                }
                val candidate = result.matchedCandidates.firstOrNull()
                if (candidate == null) {
                    env.toast(R.string.github_toast_repo_scan_no_match)
                    return@launch
                }
                state.repoScanCandidates = result.matchedCandidates
                env.toast(
                    R.string.github_toast_repo_scan_candidates_found,
                    result.matchedCandidates.size
                )
            } finally {
                state.repoUrlScanRunning = false
            }
        }
    }

    fun selectRepoScanCandidate(candidate: GitHubPackageRepositoryScanCandidate) {
        state.repoUrlInput = candidate.trackedApp.repoUrl
        state.packageNameInput = candidate.trackedApp.packageName
        state.selectedApp = state.appList.firstOrNull { app ->
            app.packageName.equals(candidate.trackedApp.packageName, ignoreCase = true)
        } ?: state.selectedApp?.takeIf { selected ->
            selected.packageName.equals(candidate.trackedApp.packageName, ignoreCase = true)
        }
        env.toast(
            R.string.github_toast_repo_scan_success,
            candidate.repository.owner,
            candidate.repository.repo
        )
    }

    fun applyTrackSheet() {
        val draft = GitHubTrackEditorDraft(
            repoUrl = state.repoUrlInput,
            packageName = state.packageNameInput,
            preferPreRelease = state.preferPreReleaseInput,
            alwaysShowLatestReleaseDownloadButton = state.alwaysShowLatestReleaseDownloadButtonInput,
            preciseApkVersionMode = state.preciseApkVersionModeInput,
            appList = state.appList
        )
        scope.launch {
            val nowMillis = System.currentTimeMillis()
            val newItem = when (val result = repository.buildTrackedItem(draft)) {
                GitHubTrackEditorResult.InvalidRepository -> {
                    env.toast(R.string.github_toast_fill_repo_and_select_app)
                    return@launch
                }
                GitHubTrackEditorResult.InvalidPackageName -> {
                    env.toast(R.string.github_toast_invalid_package_name)
                    return@launch
                }
                is GitHubTrackEditorResult.Ready -> result.item
            }
            val editing = state.editingTrackedItem
            if (editing == null) {
                if (state.trackedItems.any { it.id == newItem.id }) {
                    env.toast(R.string.github_toast_track_exists)
                    return@launch
                }
                state.trackedItems.add(newItem)
                state.recordTrackedAddedAt(newItem.id, nowMillis)
                state.requestTrackCardFocus(newItem.id)
                env.saveTrackedItems(refreshTrackIds = setOf(newItem.id))
                env.toast(R.string.github_toast_track_added)
            } else {
                val duplicate = state.trackedItems.any { it.id == newItem.id && it.id != editing.id }
                if (duplicate) {
                    env.toast(R.string.github_toast_track_exists)
                    return@launch
                }
                val editingState = state.checkStates[editing.id] ?: VersionCheckUi()
                val itemChanged = editing != newItem
                val existingAddedAt = state.trackedAddedAtById[editing.id]
                    ?.takeIf { it > 0L }
                    ?: state.trackedAddedAtById[newItem.id]
                    ?.takeIf { it > 0L }
                    ?: nowMillis
                val index = state.trackedItems.indexOfFirst { it.id == editing.id }
                if (index >= 0) {
                    state.trackedItems[index] = newItem
                } else {
                    state.trackedItems.add(newItem)
                }
                if (itemChanged) {
                    assetActions.clearApkAssetStateAndCacheNow(
                        item = editing,
                        itemState = editingState
                    )
                }
                if (editing.id != newItem.id) {
                    state.checkStates.remove(editing.id)
                    state.trackedCardExpanded.remove(editing.id)
                    state.trackedLocalVersionExpanded.remove(editing.id)
                    state.trackedStableVersionExpanded.remove(editing.id)
                    state.trackedPreReleaseVersionExpanded.remove(editing.id)
                    GitHubTrackedReleaseUiStateStore.remove(editing.id)
                    state.trackedAddedAtById.remove(editing.id)
                }
                state.recordTrackedAddedAt(newItem.id, existingAddedAt)
                state.requestTrackCardFocus(newItem.id)
                env.saveTrackedItems(refreshTrackIds = setOf(newItem.id))
                env.toast(R.string.github_toast_track_updated)
            }
            state.dismissTrackSheet()
        }
    }

    fun confirmDeletePendingItem() {
        if (state.deleteInProgress) return
        state.pendingDeleteItem?.let { deleting ->
            state.deleteInProgress = true
            try {
                val deletingState = state.checkStates[deleting.id] ?: VersionCheckUi()
                refreshActions.cancelRefreshAll()
                assetActions.clearApkAssetStateAndCache(
                    item = deleting,
                    itemState = deletingState
                )
                state.trackedItems.remove(deleting)
                state.checkStates.remove(deleting.id)
                state.trackedCardExpanded.remove(deleting.id)
                state.trackedLocalVersionExpanded.remove(deleting.id)
                state.trackedStableVersionExpanded.remove(deleting.id)
                state.trackedPreReleaseVersionExpanded.remove(deleting.id)
                GitHubTrackedReleaseUiStateStore.remove(deleting.id)
                state.trackedAddedAtById.remove(deleting.id)
                env.saveTrackedItems()
                refreshActions.persistCheckCache()
                env.toast(R.string.github_toast_track_deleted, deleting.appLabel)
            } finally {
                state.deleteInProgress = false
            }
        }
        state.pendingDeleteItem = null
    }

    private fun syncSelectedAppFromPackageInput() {
        val packageName = state.packageNameInput.trim()
        if (packageName.isBlank()) return
        val refreshedApp = state.appList.firstOrNull { app ->
            app.packageName.equals(packageName, ignoreCase = true)
        } ?: return
        state.selectedApp = refreshedApp
    }
}
