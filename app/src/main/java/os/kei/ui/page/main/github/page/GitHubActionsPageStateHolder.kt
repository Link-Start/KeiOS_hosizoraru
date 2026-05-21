package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import os.kei.feature.github.model.GitHubActionsBranchOption
import os.kei.feature.github.model.GitHubActionsDownloadRecord
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubActionsRunMatch
import os.kei.feature.github.model.GitHubActionsRunTrackingPlan
import os.kei.feature.github.model.GitHubActionsWorkflow
import os.kei.feature.github.model.GitHubActionsWorkflowArtifactSignal
import os.kei.feature.github.model.GitHubActionsWorkflowArtifactsSnapshot
import os.kei.feature.github.model.GitHubActionsWorkflowMatch
import os.kei.feature.github.model.GitHubApiAuthMode
import os.kei.ui.page.main.github.actions.GitHubActionsSectionExpansionState

@Stable
internal class GitHubActionsPageStateHolder(
    actionsSectionExpansionState: GitHubActionsSectionExpansionState,
) {
    var actionsLoading by mutableStateOf(false)
    var actionsRunsLoading by mutableStateOf(false)
    var actionsError by mutableStateOf<String?>(null)
    var actionsAuthMode by mutableStateOf<GitHubApiAuthMode?>(null)
    var actionsDefaultBranch by mutableStateOf("")
    var actionsSelectedBranch by mutableStateOf("")
    var actionsBranchManuallySelected by mutableStateOf(false)
    var actionsBranchOptions by mutableStateOf<List<GitHubActionsBranchOption>>(emptyList())
    var actionsRawWorkflows by mutableStateOf<List<GitHubActionsWorkflow>>(emptyList())
    var actionsWorkflowSignals by mutableStateOf<Map<Long, GitHubActionsWorkflowArtifactSignal>>(emptyMap())
    var actionsWorkflows by mutableStateOf<List<GitHubActionsWorkflowMatch>>(emptyList())
    var actionsSelectedWorkflowId by mutableStateOf<Long?>(null)
    var actionsWorkflowManuallySelected by mutableStateOf(false)
    var actionsSnapshot by mutableStateOf<GitHubActionsWorkflowArtifactsSnapshot?>(null)
    var actionsRuns by mutableStateOf<List<GitHubActionsRunMatch>>(emptyList())
    var actionsRunLimit by mutableStateOf(6)
    var actionsSelectedRunId by mutableStateOf<Long?>(null)
    var actionsBranchesExpanded by mutableStateOf(actionsSectionExpansionState.branchesExpanded)
    var actionsWorkflowsExpanded by mutableStateOf(actionsSectionExpansionState.workflowsExpanded)
    var actionsRunsExpanded by mutableStateOf(actionsSectionExpansionState.runsExpanded)
    var actionsArtifactsExpanded by mutableStateOf(true)
    var actionsArtifactFilter by mutableStateOf(GitHubActionsArtifactFilter.Recommended)
    var actionsDownloadHistory by mutableStateOf<List<GitHubActionsDownloadRecord>>(emptyList())
    var actionsRunTrackingPlans by mutableStateOf<Map<Long, GitHubActionsRunTrackingPlan>>(emptyMap())
    var actionsArtifactDownloadLoadingId by mutableStateOf<Long?>(null)
    var actionsArtifactShareLoadingId by mutableStateOf<Long?>(null)
    var actionsRunWatchJob by mutableStateOf<Job?>(null)
    var debugActionsUpdateNotificationLoading by mutableStateOf(false)
    var actionsRecommendedRunRefreshJob by mutableStateOf<Job?>(null)
    val actionsStatusRefreshingRunIds = mutableStateMapOf<Long, Boolean>()
    val actionsRecommendedRunSnapshots =
        mutableStateMapOf<String, GitHubActionsRecommendedRunSnapshot>()
}
