package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.share.GitHubPendingShareImportAttachCandidate
import os.kei.ui.page.main.github.share.GitHubPendingShareImportTrack
import os.kei.ui.page.main.github.share.GitHubShareImportPreview
import os.kei.ui.page.main.github.share.GitHubShareImportResult

@Stable
internal class GitHubPageSheetStateHolder {
    var showAddSheet by mutableStateOf(false)
    var showStrategySheet by mutableStateOf(false)
    var showCheckLogicSheet by mutableStateOf(false)
    var showActionsSheet by mutableStateOf(false)
    var showOverviewEntrySheet by mutableStateOf(false)
    var showDownloaderPopup by mutableStateOf(false)
    var editingTrackedItem by mutableStateOf<GitHubTrackedApp?>(null)
    var actionsTargetItem by mutableStateOf<GitHubTrackedApp?>(null)
    var showActionMenuPopup by mutableStateOf(false)
    var showOnlineShareTargetPopup by mutableStateOf(false)
    var pendingTrackImportPreview by mutableStateOf<GitHubTrackImportPreview?>(null)
    var pendingShareImportPreview by mutableStateOf<GitHubShareImportPreview?>(null)
    var pendingShareImportTrack by mutableStateOf<GitHubPendingShareImportTrack?>(null)
    var pendingShareImportAttachCandidate by mutableStateOf<GitHubPendingShareImportAttachCandidate?>(null)
    var pendingShareImportResult by mutableStateOf<GitHubShareImportResult?>(null)
    var decisionAssistDetailRequest by mutableStateOf<GitHubDecisionAssistDetailRequest?>(null)
    var actionsArtifactDetailRequest by mutableStateOf<GitHubActionsArtifactDetailRequest?>(null)
    var apkInfoDetailRequest by mutableStateOf<GitHubApkInfoDetailRequest?>(null)
    var managedInstallConfirmRequest by mutableStateOf<GitHubManagedInstallConfirmRequest?>(null)
    var pendingDeleteItem by mutableStateOf<GitHubTrackedApp?>(null)
}
