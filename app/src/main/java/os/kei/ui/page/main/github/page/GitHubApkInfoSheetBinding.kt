@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheet
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheetInput
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheetUiState

@Composable
internal fun GitHubApkInfoSheetBinding(
    state: GitHubPageState,
    actions: GitHubPageActions,
    backdrop: LayerBackdrop,
    sheetState: GitHubApkInfoSheetUiState,
    onRequestSheetState: (GitHubApkInfoSheetInput) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSheetState: () -> Unit,
) {
    val request = state.apkInfoDetailRequest
    val asset = request?.asset
    val key = asset?.githubApkInfoKey().orEmpty()
    val info = state.apkInfoResults[key]
    val visibleSheetState =
        if (sheetState.assetKey == key) {
            sheetState
        } else {
            GitHubApkInfoSheetUiState(assetKey = key)
        }
    LaunchedEffect(key, info) {
        if (key.isBlank()) {
            onClearSheetState()
        } else {
            onRequestSheetState(
                GitHubApkInfoSheetInput(
                    assetKey = key,
                    info = info,
                ),
            )
        }
    }
    val managedInstallRunning =
        request?.let { request ->
            state.managedInstallLoading[request.item.githubManagedInstallKey(request.asset)] == true
        } == true
    GitHubApkInfoSheet(
        asset = asset,
        info = info,
        installedInfo = state.apkInfoInstalledResults[key],
        loading = state.apkInfoLoading[key] == true,
        error = state.apkInfoErrors[key].orEmpty(),
        sheetState = visibleSheetState,
        backdrop = backdrop,
        managedInstallEnabled = state.lookupConfig.appManagedShareInstallEnabled,
        managedInstallRunning = managedInstallRunning,
        onSearchQueryChange = onSearchQueryChange,
        onRefresh = {
            request?.let { actions.refreshApkInfo(it.item, it.asset) }
        },
        onInstall = {
            request?.let { actions.installApkWithKeiOs(it.item, it.asset) }
        },
        onDownload = {
            request?.let { actions.openApkInDownloader(it.item, it.asset) }
        },
        onShare = { asset?.let(actions::shareApkLink) },
        onDismissRequest = {
            actions.dismissApkInfoDetail()
            onClearSheetState()
        },
    )
}
