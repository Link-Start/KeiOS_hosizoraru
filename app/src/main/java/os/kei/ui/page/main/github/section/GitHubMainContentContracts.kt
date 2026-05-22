package os.kei.ui.page.main.github.section

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.GitHubSortDirection
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.GitHubTrackedFilterMode
import os.kei.ui.page.main.github.OverviewRefreshState
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.page.GitHubDecisionAssistDetailType
import os.kei.ui.page.main.github.share.GitHubPendingShareImportAttachCandidate
import os.kei.ui.page.main.github.share.GitHubPendingShareImportTrack
import os.kei.ui.page.main.github.share.GitHubShareImportPreview
import os.kei.ui.page.main.github.share.GitHubShareImportResult
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import top.yukonga.miuix.kmp.basic.ScrollBehavior

internal data class GitHubMainContentLayout(
    val contentBottomPadding: Dp,
    val listState: LazyListState,
    val scrollBehavior: ScrollBehavior,
    val addButtonScrollConnection: NestedScrollConnection,
    val bottomBarVisible: Boolean,
    val floatingDockSide: AppFloatingDockSide,
    val onShowBottomBar: () -> Unit,
)

internal data class GitHubMainContentSurfaces(
    val topBarBackdrop: LayerBackdrop,
    val contentBackdrop: LayerBackdrop,
    val topBarColor: Color,
    val liquidActionBarLayeredStyleEnabled: Boolean,
    val isDark: Boolean,
)

internal data class GitHubMainContentControls(
    val searchExpanded: Boolean,
    val trackedSearch: String,
    val sortMode: GitHubSortMode,
    val sortDirection: GitHubSortDirection,
    val trackedFilterMode: GitHubTrackedFilterMode,
    val refreshIntervalHours: Int,
    val showActionMenuPopup: Boolean,
    val deleteInProgress: Boolean,
    val tracksExporting: Boolean,
    val tracksImporting: Boolean,
)

internal data class GitHubMainContentOverview(
    val refreshState: OverviewRefreshState,
    val expanded: Boolean,
    val refreshProgress: Float,
    val lastRefreshMs: Long,
    val lookupConfig: GitHubLookupConfig,
    val visibleEntries: Set<GitHubOverviewEntry>,
    val metrics: GitHubOverviewMetrics,
)

internal data class GitHubMainContentTracked(
    val appList: List<InstalledAppItem>,
    val trackedItems: List<GitHubTrackedApp>,
    val filteredTracked: List<GitHubTrackedApp>,
    val sortedTracked: List<GitHubTrackedApp>,
    val appLastUpdatedAtByTrackId: Map<String, Long>,
    val checkStates: SnapshotStateMap<String, VersionCheckUi>,
    val itemRefreshLoading: SnapshotStateMap<String, Boolean>,
    val apkAssetBundles: SnapshotStateMap<String, GitHubReleaseAssetBundle>,
    val apkAssetLoading: SnapshotStateMap<String, Boolean>,
    val apkAssetErrors: SnapshotStateMap<String, String>,
    val apkAssetExpanded: SnapshotStateMap<String, Boolean>,
    val managedInstallLoading: SnapshotStateMap<String, Boolean>,
    val actionsRecommendedRunSnapshots: SnapshotStateMap<String, GitHubActionsRecommendedRunSnapshot>,
    val trackedCardExpanded: SnapshotStateMap<String, Boolean>,
    val trackedLocalVersionExpanded: SnapshotStateMap<String, Boolean>,
    val trackedStableVersionExpanded: SnapshotStateMap<String, Boolean>,
    val trackedPreReleaseVersionExpanded: SnapshotStateMap<String, Boolean>,
)

internal data class GitHubMainContentShareImport(
    val pendingPreview: GitHubShareImportPreview?,
    val pendingTrack: GitHubPendingShareImportTrack?,
    val pendingAttachCandidate: GitHubPendingShareImportAttachCandidate?,
    val pendingResult: GitHubShareImportResult?,
    val showPendingCard: Boolean,
    val pendingRepoOverlapCount: Int,
    val pendingNowMillis: Long,
)

internal data class GitHubMainContentActions(
    val onTrackedSearchChange: (String) -> Unit,
    val onSearchExpandedChange: (Boolean) -> Unit,
    val onShowActionMenuPopupChange: (Boolean) -> Unit,
    val onSortModeChange: (GitHubSortMode) -> Unit,
    val onSortDirectionChange: (GitHubSortDirection) -> Unit,
    val onTrackedFilterModeChange: (GitHubTrackedFilterMode) -> Unit,
    val onRefreshIntervalHoursChange: (Int) -> Unit,
    val onExportTrackedItems: () -> Unit,
    val onImportTrackedItems: () -> Unit,
    val onOpenStarImport: () -> Unit,
    val onOpenStrategySheet: () -> Unit,
    val onOpenCheckLogicSheet: () -> Unit,
    val onOverviewExpandedChange: (Boolean) -> Unit,
    val onLocalVersionExpandedChange: (String, Boolean) -> Unit,
    val onStableVersionExpandedChange: (String, Boolean) -> Unit,
    val onPreReleaseVersionExpandedChange: (String, Boolean) -> Unit,
    val onOpenOverviewEntrySheet: () -> Unit,
    val onRefreshVisibleTracked: () -> Unit,
    val onRetryFailedTracked: () -> Unit,
    val onFailedFilterToggle: (Boolean) -> Unit,
    val onRefreshTrackedItem: (GitHubTrackedApp) -> Unit,
    val onOpenActionsSheet: (GitHubTrackedApp) -> Unit,
    val onOpenTrackSheetForAdd: () -> Unit,
    val onOpenTrackSheetForEdit: (GitHubTrackedApp) -> Unit,
    val onRequestDeleteTrackedItem: (GitHubTrackedApp) -> Unit,
    val onCollapseTrackedCard: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    val onCollapseApkAssetPanel: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    val onLoadApkAssets: (GitHubTrackedApp, VersionCheckUi, Boolean, Boolean, Boolean) -> Unit,
    val onOpenDecisionAssistDetail: (GitHubDecisionAssistDetailType, GitHubTrackedApp) -> Unit,
    val onOpenExternalUrl: (String) -> Unit,
    val onOpenApkInfo: (GitHubTrackedApp, GitHubReleaseAssetFile) -> Unit,
    val onInstallApk: (GitHubTrackedApp, GitHubReleaseAssetFile) -> Unit,
    val onOpenApkInDownloader: (GitHubTrackedApp, GitHubReleaseAssetFile) -> Unit,
    val onShareApkLink: (GitHubReleaseAssetFile) -> Unit,
    val onOpenShareImportFlow: () -> Unit,
    val onOpenShareImportResult: () -> Unit,
    val onCancelActiveShareImportFlow: () -> Unit,
    val onCancelPendingShareImportTrack: () -> Unit,
    val onDismissShareImportResult: () -> Unit,
    val onActionBarInteractingChanged: (Boolean) -> Unit,
)
