package os.kei.ui.page.main.github.section

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.page.GitHubDecisionAssistDetailType

internal data class GitHubTrackedItemsContent(
    val lookupConfig: GitHubLookupConfig,
    val trackedItems: List<GitHubTrackedApp>,
    val filteredTracked: List<GitHubTrackedApp>,
    val sortedTracked: List<GitHubTrackedApp>,
    val installedAppLabelsByPackage: Map<String, String>,
    val appLastUpdatedAtByTrackId: Map<String, Long>,
)

internal data class GitHubTrackedItemsSurfaces(
    val contentBackdrop: LayerBackdrop,
    val isDark: Boolean,
)

internal data class GitHubTrackedItemsCheckState(
    val checkStates: SnapshotStateMap<String, VersionCheckUi>,
    val itemRefreshLoading: SnapshotStateMap<String, Boolean>,
    val actionsRecommendedRunSnapshots: SnapshotStateMap<String, GitHubActionsRecommendedRunSnapshot>,
)

internal data class GitHubTrackedItemsAssetState(
    val apkAssetBundles: SnapshotStateMap<String, GitHubReleaseAssetBundle>,
    val apkAssetLoading: SnapshotStateMap<String, Boolean>,
    val apkAssetErrors: SnapshotStateMap<String, String>,
    val apkAssetExpanded: SnapshotStateMap<String, Boolean>,
    val managedInstallLoading: SnapshotStateMap<String, Boolean>,
)

@Immutable
internal data class GitHubTrackedItemsExpansionState(
    val trackedCardExpanded: Map<String, Boolean> = emptyMap(),
    val trackedLocalVersionExpanded: Map<String, Boolean> = emptyMap(),
    val trackedStableVersionExpanded: Map<String, Boolean> = emptyMap(),
    val trackedPreReleaseVersionExpanded: Map<String, Boolean> = emptyMap(),
)

internal data class GitHubTrackedItemsRuntime(
    val context: Context,
    val supportedAbis: List<String>,
    val relativeTimeNowMillis: Long,
)

internal data class GitHubTrackedItemsActions(
    val onRefreshTrackedItem: (GitHubTrackedApp) -> Unit,
    val onOpenActionsSheet: (GitHubTrackedApp) -> Unit,
    val onOpenTrackSheetForEdit: (GitHubTrackedApp) -> Unit,
    val onIgnoreCurrentTrackedVersion: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    val onRequestDeleteTrackedItem: (GitHubTrackedApp) -> Unit,
    val onTrackedCardExpandedChange: (String, Boolean) -> Unit,
    val onCollapseTrackedCard: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    val onLocalVersionExpandedChange: (String, Boolean) -> Unit,
    val onStableVersionExpandedChange: (String, Boolean) -> Unit,
    val onPreReleaseVersionExpandedChange: (String, Boolean) -> Unit,
    val onCollapseApkAssetPanel: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    val onLoadApkAssets: (GitHubTrackedApp, VersionCheckUi, Boolean, Boolean, Boolean) -> Unit,
    val onOpenDecisionAssistDetail: (GitHubDecisionAssistDetailType, GitHubTrackedApp) -> Unit,
    val onOpenExternalUrl: (String) -> Unit,
    val onOpenApkInfo: (GitHubTrackedApp, GitHubReleaseAssetFile) -> Unit,
    val onInstallApk: (GitHubTrackedApp, GitHubReleaseAssetFile) -> Unit,
    val onOpenApkInDownloader: (GitHubTrackedApp, GitHubReleaseAssetFile) -> Unit,
    val onShareApkLink: (GitHubReleaseAssetFile) -> Unit,
)
