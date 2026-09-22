package os.kei.ui.page.main.github.asset

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.core.ext.showToast
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig

/**
 * Share and download for an asset row on a page that has no GitHub page action environment: the release
 * history and the F-Droid version history.
 *
 * The GitHub page reaches the same [GitHubAssetHandoff] through its own action classes. What this adds is
 * only what those classes already have — a scope to resolve the link in and somewhere to say the result —
 * so a row on either page does exactly what the same row does on the tracked card.
 */
@Stable
internal class GitHubAssetHandoffActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val lookupConfig: () -> GitHubLookupConfig,
) {
    fun share(asset: GitHubReleaseAssetFile) {
        scope.launch { GitHubAssetHandoff.shareAsset(context, lookupConfig(), asset).report(::toast) }
    }

    fun download(asset: GitHubReleaseAssetFile) {
        scope.launch { GitHubAssetHandoff.downloadAsset(context, lookupConfig(), asset).report(::toast) }
    }

    private fun toast(@StringRes messageRes: Int) = context.showToast(messageRes)
}

/** One instance per page, reading the settings the page loaded at the moment a button is pressed. */
@Composable
internal fun rememberGitHubAssetHandoffActions(lookupConfig: GitHubLookupConfig): GitHubAssetHandoffActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentLookupConfig by rememberUpdatedState(lookupConfig)
    return remember(context, scope) {
        GitHubAssetHandoffActions(
            context = context,
            scope = scope,
            lookupConfig = { currentLookupConfig },
        )
    }
}
