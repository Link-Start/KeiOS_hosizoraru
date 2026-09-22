@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.install

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.kyant.backdrop.Backdrop
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.page.githubApkInfoKey
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheet
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheetInput
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheetUiState

@Composable
internal fun GitHubApkInfoSheetBinding(
    controller: GitHubApkInstallController,
    backdrop: Backdrop,
    sheetState: GitHubApkInfoSheetUiState,
    onRequestSheetState: (GitHubApkInfoSheetInput) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSheetState: () -> Unit,
    onDownload: (GitHubTrackedApp, GitHubReleaseAssetFile) -> Unit,
    onShare: (GitHubReleaseAssetFile) -> Unit,
) {
    val state = controller.state
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
    GitHubApkInfoSheet(
        asset = asset,
        info = info,
        installedInfo = state.apkInfoInstalledResults[key],
        loading = state.apkInfoLoading[key] == true,
        error = state.apkInfoErrors[key].orEmpty(),
        sheetState = visibleSheetState,
        backdrop = backdrop,
        managedInstallEnabled =
            request?.let { controller.managedInstallAvailable(it.item, it.asset) } == true,
        managedInstallRunning =
            request?.let { controller.managedInstallRunning(it.item, it.asset) } == true,
        onSearchQueryChange = onSearchQueryChange,
        onRefresh = {
            request?.let { controller.openApkInfo(it.item, it.asset, forceRefresh = true) }
        },
        onInstall = {
            request?.let { controller.installOrFallback(it.item, it.asset) }
        },
        onDownload = {
            request?.let { onDownload(it.item, it.asset) }
        },
        onShare = { asset?.let(onShare) },
        onDismissRequest = {
            controller.dismissApkInfo()
            onClearSheetState()
        },
    )
}
