@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.page

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.github.sheet.GitHubManagedInstallConfirmSheet
import os.kei.ui.page.main.github.sheet.GitHubManagedInstallConfirmSheetInput
import os.kei.ui.page.main.github.sheet.GitHubManagedInstallConfirmSheetUiState

@Composable
internal fun GitHubManagedInstallConfirmSheetBinding(
    state: GitHubPageState,
    actions: GitHubPageActions,
    backdrop: LayerBackdrop,
    sheetState: GitHubManagedInstallConfirmSheetUiState,
    onRequestSheetState: (GitHubManagedInstallConfirmSheetInput) -> Unit,
    onClearSheetState: () -> Unit,
) {
    val request = state.managedInstallConfirmRequest
    val asset = request?.asset
    val infoKey = asset?.githubApkInfoKey().orEmpty()
    val requestKey =
        request
            ?.let { request -> request.item.githubManagedInstallKey(request.asset) }
            .orEmpty()
    val supportedAbis = remember { Build.SUPPORTED_ABIS?.toList().orEmpty() }
    val running =
        request?.let { request ->
            state.managedInstallLoading[request.item.githubManagedInstallKey(request.asset)] == true
        } == true
    val visibleSheetState =
        if (sheetState.requestKey == requestKey) {
            sheetState
        } else {
            GitHubManagedInstallConfirmSheetUiState(requestKey = requestKey)
        }

    LaunchedEffect(
        requestKey,
        request,
        infoKey,
        supportedAbis,
        state.apkInfoResults[infoKey],
        state.apkInfoInstalledResults[infoKey],
    ) {
        if (request == null || requestKey.isBlank()) {
            onClearSheetState()
        } else {
            onRequestSheetState(
                GitHubManagedInstallConfirmSheetInput(
                    requestKey = requestKey,
                    request = request,
                    info = state.apkInfoResults[infoKey],
                    installedInfo = state.apkInfoInstalledResults[infoKey],
                    supportedAbis = supportedAbis,
                ),
            )
        }
    }

    GitHubManagedInstallConfirmSheet(
        request = request,
        derivedState = visibleSheetState,
        info = state.apkInfoResults[infoKey],
        installedInfo = state.apkInfoInstalledResults[infoKey],
        loading = state.apkInfoLoading[infoKey] == true,
        error = state.apkInfoErrors[infoKey].orEmpty(),
        running = running,
        backdrop = backdrop,
        onConfirm = actions::confirmManagedInstall,
        onDismissRequest = actions::dismissManagedInstallConfirm,
    )
}
