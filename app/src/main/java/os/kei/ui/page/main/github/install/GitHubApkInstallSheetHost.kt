@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.install

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.ext.showToast
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.asset.GitHubAssetHandoffActions
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheetInput
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheetUiState
import os.kei.ui.page.main.github.sheet.GitHubManagedInstallConfirmSheetInput
import os.kei.ui.page.main.github.sheet.GitHubManagedInstallConfirmSheetUiState
import os.kei.ui.page.main.github.sheet.deriveGitHubApkInfoSheetState
import os.kei.ui.page.main.github.sheet.deriveGitHubManagedInstallConfirmSheetState

/**
 * A [GitHubApkInstallController] for a page that is not the GitHub page, which builds its own inside
 * its action environment.
 *
 * [state] belongs to the page's view model rather than to this composition, so a rotation keeps the open
 * sheet and what has been inspected. A fallback download goes through [assetHandoff], the same hand-off
 * the row's ⬇ uses.
 */
@Composable
internal fun rememberGitHubApkInstallController(
    state: GitHubApkInstallState,
    lookupConfig: GitHubLookupConfig,
    assetHandoff: GitHubAssetHandoffActions,
    onInstalled: (GitHubInstalledPackageInfo) -> Unit,
): GitHubApkInstallController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentLookupConfig by rememberUpdatedState(lookupConfig)
    val currentOnInstalled by rememberUpdatedState(onInstalled)
    val controller =
        remember(context, scope, state, assetHandoff) {
            GitHubApkInstallController(
                context = context,
                scope = scope,
                state = state,
                lookupConfig = { currentLookupConfig },
                host =
                    object : GitHubApkInstallHost {
                        override fun toast(message: String) = context.showToast(message)

                        override suspend fun downloadInstead(asset: GitHubReleaseAssetFile) =
                            assetHandoff.download(asset)

                        override fun onInstalled(
                            item: GitHubTrackedApp,
                            installedInfo: GitHubInstalledPackageInfo,
                            appLabel: String,
                        ) = currentOnInstalled(installedInfo)
                    },
            )
        }
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }
    return controller
}

/**
 * The APK info and install confirm sheets, over the page the reader is on.
 *
 * The GitHub page renders the same two bindings from its view model's derivations; this derives the same
 * states here with the same functions, since a history page has no sheet derivation of its own.
 */
@Composable
internal fun GitHubApkInstallSheetHost(
    controller: GitHubApkInstallController,
    backdrop: Backdrop,
    onDownload: (GitHubReleaseAssetFile) -> Unit,
    onShare: (GitHubReleaseAssetFile) -> Unit,
) {
    var apkInfoInput by remember { mutableStateOf<GitHubApkInfoSheetInput?>(null) }
    val apkInfoSheetState by
        produceState(GitHubApkInfoSheetUiState(), apkInfoInput) {
            val input = apkInfoInput
            value =
                if (input == null) {
                    GitHubApkInfoSheetUiState()
                } else {
                    withContext(AppDispatchers.uiDerivation) { deriveGitHubApkInfoSheetState(input) }
                }
        }
    GitHubApkInfoSheetBinding(
        controller = controller,
        backdrop = backdrop,
        sheetState = apkInfoSheetState,
        onRequestSheetState = { input ->
            // The GitHub page's rule: a search survives the manifest arriving, not a different file.
            val keptQuery = apkInfoInput?.takeIf { it.assetKey == input.assetKey }?.query.orEmpty()
            apkInfoInput = input.copy(query = keptQuery)
        },
        onSearchQueryChange = { query -> apkInfoInput = apkInfoInput?.copy(query = query) },
        onClearSheetState = { apkInfoInput = null },
        onDownload = { _, asset -> onDownload(asset) },
        onShare = onShare,
    )

    var confirmInput by remember { mutableStateOf<GitHubManagedInstallConfirmSheetInput?>(null) }
    val confirmSheetState by
        produceState(GitHubManagedInstallConfirmSheetUiState(), confirmInput) {
            val input = confirmInput
            value =
                if (input == null) {
                    GitHubManagedInstallConfirmSheetUiState()
                } else {
                    withContext(AppDispatchers.uiDerivation) { deriveGitHubManagedInstallConfirmSheetState(input) }
                }
        }
    GitHubManagedInstallConfirmSheetBinding(
        controller = controller,
        backdrop = backdrop,
        sheetState = confirmSheetState,
        onRequestSheetState = { input -> confirmInput = input },
        onClearSheetState = { confirmInput = null },
    )
}
