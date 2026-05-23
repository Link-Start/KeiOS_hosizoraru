@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.forTrackedItem
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.sheet.GitHubDecisionAssistDetailSheet
import os.kei.ui.page.main.github.sheet.GitHubReleaseNotesDetailInput
import os.kei.ui.page.main.github.sheet.GitHubReleaseNotesDetailUiState
import os.kei.ui.page.main.github.sheet.releaseNotesDetailRequestKey

@Composable
internal fun GitHubDecisionAssistSheetBinding(
    state: GitHubPageState,
    actions: GitHubPageActions,
    backdrop: LayerBackdrop,
    releaseNotesDetailState: GitHubReleaseNotesDetailUiState,
    onRequestReleaseNotesDetailState: (GitHubReleaseNotesDetailInput) -> Unit,
    onClearReleaseNotesDetailState: () -> Unit,
) {
    val request = state.decisionAssistDetailRequest
    val versionState =
        request
            ?.item
            ?.id
            ?.let { state.checkStates[it] }
            ?: VersionCheckUi()
    val releaseNotesBundle =
        request
            ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
            ?.item
            ?.id
            ?.let { state.releaseNotesBundles[it] }
    val releaseNotesDetailKey =
        releaseNotesDetailRequestKey(
            item = request?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }?.item,
            state = versionState,
            assetBundle = releaseNotesBundle,
        )
    val visibleReleaseNotesDetailState =
        if (releaseNotesDetailState.requestKey == releaseNotesDetailKey) {
            releaseNotesDetailState
        } else {
            GitHubReleaseNotesDetailUiState(requestKey = releaseNotesDetailKey)
        }
    LaunchedEffect(releaseNotesDetailKey, releaseNotesBundle, versionState) {
        val releaseNotesItem =
            request
                ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
                ?.item
        if (releaseNotesItem == null || releaseNotesDetailKey.isBlank()) {
            onClearReleaseNotesDetailState()
        } else {
            onRequestReleaseNotesDetailState(
                GitHubReleaseNotesDetailInput(
                    requestKey = releaseNotesDetailKey,
                    item = releaseNotesItem,
                    state = versionState,
                    assetBundle = releaseNotesBundle,
                ),
            )
        }
    }

    GitHubDecisionAssistDetailSheet(
        request = request,
        backdrop = backdrop,
        versionState = versionState,
        assetBundle = releaseNotesBundle,
        releaseNotesTargets =
            request
                ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
                ?.item
                ?.id
                ?.let { state.releaseNotesTargets[it] }
                .orEmpty(),
        selectedReleaseNotesTarget =
            request
                ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
                ?.item
                ?.id
                ?.let { state.releaseNotesSelectedTargets[it] },
        releaseNotesApkVersion =
            request
                ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
                ?.item
                ?.let { item ->
                    state.releaseNotesSelectedTargets[item.id]?.let { target ->
                        state.releaseNotesApkVersions[releaseNotesApkVersionKey(item.id, target)]
                    }
                },
        releaseNotesDetailState = visibleReleaseNotesDetailState,
        preciseApkVersionEnabled =
            request
                ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
                ?.item
                ?.let { state.lookupConfig.forTrackedItem(it).preciseApkVersionEnabled } == true,
        assetLoading =
            request
                ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
                ?.item
                ?.id
                ?.let { state.releaseNotesLoading[it] == true } == true,
        assetError =
            request
                ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
                ?.item
                ?.id
                ?.let { state.releaseNotesErrors[it] }
                .orEmpty(),
        healthRefreshing =
            request
                ?.takeIf { it.type == GitHubDecisionAssistDetailType.RepositoryHealth }
                ?.item
                ?.id
                ?.let { state.itemRefreshLoading[it] == true || state.checkStates[it]?.loading == true } == true,
        onDismissRequest = {
            actions.dismissDecisionAssistDetail()
            onClearReleaseNotesDetailState()
        },
        onRefreshHealth = { item ->
            actions.refreshTrackedItem(
                item = item,
                showToastOnError = true,
                profilePurposeOverride = GitHubRepositoryProfilePurpose.ManualDeepRefresh,
                forceRefresh = true,
            )
        },
        onRefreshReleaseNotes = { item, itemState ->
            actions.loadReleaseNotes(
                item = item,
                itemState = itemState,
                clearCache = true,
            )
        },
        onSelectReleaseNotesTarget = { item, target ->
            actions.selectReleaseNotesTarget(item, target)
        },
        onOpenExternalUrl = actions::openExternalUrl,
    )
}
