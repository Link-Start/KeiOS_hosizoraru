package os.kei.ui.page.main.github.section

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.formatReleaseUpdatedAtCompact
import os.kei.ui.page.main.github.formatReleaseValue
import os.kei.ui.page.main.github.isLocalAppUninstalled

@Composable
internal fun formatActionsRunSnapshotValue(snapshot: GitHubActionsRecommendedRunSnapshot?): String {
    if (snapshot == null) {
        return stringResource(R.string.github_item_actions_run_not_recorded)
    }
    val checkedAt =
        formatReleaseUpdatedAtCompact(snapshot.checkedAtMillis)
            ?: stringResource(R.string.common_unknown)
    return stringResource(
        R.string.github_item_actions_run_value,
        snapshot.runLabel,
        checkedAt,
    )
}

internal fun formatLocalVersionText(
    context: Context,
    state: VersionCheckUi?,
): String? {
    state ?: return null
    val rawLocalVersion = state.localVersion.trim()
    if (state.isLocalAppUninstalled()) {
        return context.getString(R.string.github_item_value_local_version_uninstalled)
    }
    if (rawLocalVersion.isBlank()) return null
    val normalizedLocalVersion =
        formatReleaseValue(
            releaseName = rawLocalVersion,
            rawTag = rawLocalVersion,
        )
    return if (state.localVersionCode >= 0L) {
        "$normalizedLocalVersion (${state.localVersionCode})"
    } else {
        normalizedLocalVersion
    }
}

internal fun pendingVersionCheckUi(context: Context): VersionCheckUi {
    val pending = context.getString(R.string.github_item_value_check_pending)
    return VersionCheckUi(
        localVersion = pending,
        message = pending,
    )
}
