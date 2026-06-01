package os.kei.ui.page.main.github.sheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.GitHubTrackedUpdateIntervalMode

@Composable
internal fun trackedSourceModeLabel(mode: GitHubTrackedSourceMode): String =
    when (mode) {
        GitHubTrackedSourceMode.GitHubRepository -> {
            stringResource(R.string.github_track_sheet_source_mode_github)
        }

        GitHubTrackedSourceMode.GitRepository -> {
            stringResource(R.string.github_track_sheet_source_mode_git)
        }

        GitHubTrackedSourceMode.DirectApk -> {
            stringResource(R.string.github_track_sheet_source_mode_direct_apk)
        }
    }

@Composable
internal fun preciseApkVersionModeLabel(mode: GitHubTrackedPreciseApkVersionMode): String =
    when (mode) {
        GitHubTrackedPreciseApkVersionMode.FollowGlobal -> {
            stringResource(R.string.github_track_sheet_precise_apk_version_follow_global)
        }

        GitHubTrackedPreciseApkVersionMode.Enabled -> {
            stringResource(R.string.github_track_sheet_precise_apk_version_enabled)
        }

        GitHubTrackedPreciseApkVersionMode.Disabled -> {
            stringResource(R.string.github_track_sheet_precise_apk_version_disabled)
        }
    }

@Composable
internal fun updateIntervalModeLabel(
    mode: GitHubTrackedUpdateIntervalMode,
    globalRefreshIntervalHours: Int,
): String =
    when (mode) {
        GitHubTrackedUpdateIntervalMode.FollowGlobal -> {
            stringResource(
                R.string.github_track_sheet_update_interval_follow_global_format,
                refreshIntervalLabel(globalRefreshIntervalHours),
            )
        }

        GitHubTrackedUpdateIntervalMode.Hour1 -> {
            stringResource(R.string.github_refresh_interval_1h)
        }

        GitHubTrackedUpdateIntervalMode.Hours3 -> {
            stringResource(R.string.github_refresh_interval_3h)
        }

        GitHubTrackedUpdateIntervalMode.Hours6 -> {
            stringResource(R.string.github_refresh_interval_6h)
        }

        GitHubTrackedUpdateIntervalMode.Hours12 -> {
            stringResource(R.string.github_refresh_interval_12h)
        }
    }

@Composable
internal fun actionsUpdateIntervalModeLabel(
    mode: GitHubTrackedActionsUpdateIntervalMode,
    globalRefreshIntervalHours: Int,
): String =
    when (mode) {
        GitHubTrackedActionsUpdateIntervalMode.FollowGlobal -> {
            stringResource(
                R.string.github_track_sheet_actions_update_interval_follow_global_format,
                refreshIntervalLabel(globalRefreshIntervalHours),
            )
        }

        GitHubTrackedActionsUpdateIntervalMode.Minutes15 -> {
            stringResource(R.string.github_track_sheet_actions_update_interval_15m)
        }

        GitHubTrackedActionsUpdateIntervalMode.Minutes30 -> {
            stringResource(R.string.github_track_sheet_actions_update_interval_30m)
        }

        GitHubTrackedActionsUpdateIntervalMode.Hour1 -> {
            stringResource(R.string.github_track_sheet_actions_update_interval_1h)
        }

        GitHubTrackedActionsUpdateIntervalMode.Hours2 -> {
            stringResource(R.string.github_track_sheet_actions_update_interval_2h)
        }

        GitHubTrackedActionsUpdateIntervalMode.Hours3 -> {
            stringResource(R.string.github_track_sheet_actions_update_interval_3h)
        }
    }

@Composable
internal fun refreshIntervalLabel(hours: Int): String =
    when (hours) {
        1 -> stringResource(R.string.github_refresh_interval_1h)
        3 -> stringResource(R.string.github_refresh_interval_3h)
        6 -> stringResource(R.string.github_refresh_interval_6h)
        12 -> stringResource(R.string.github_refresh_interval_12h)
        else -> stringResource(R.string.github_refresh_interval_3h)
    }
