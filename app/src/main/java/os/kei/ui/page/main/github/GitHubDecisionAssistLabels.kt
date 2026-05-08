package os.kei.ui.page.main.github

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import os.kei.R

internal fun GitHubDecisionLevel.repositoryHealthStatusColor(): Color {
    return when (this) {
        GitHubDecisionLevel.Good -> GitHubStatusPalette.Update
        GitHubDecisionLevel.Review -> GitHubStatusPalette.Cache
        GitHubDecisionLevel.Risk -> GitHubStatusPalette.Error
    }
}

@StringRes
internal fun GitHubDecisionLevel.repositoryHealthLabelRes(): Int {
    return when (this) {
        GitHubDecisionLevel.Good -> R.string.github_health_level_good
        GitHubDecisionLevel.Review -> R.string.github_health_level_review
        GitHubDecisionLevel.Risk -> R.string.github_health_level_risk
    }
}

@StringRes
internal fun GitHubRepositoryHealthReason.labelRes(): Int {
    return when (this) {
        GitHubRepositoryHealthReason.RepositoryArchived -> R.string.github_health_reason_repository_archived
        GitHubRepositoryHealthReason.RepositoryDisabled -> R.string.github_health_reason_repository_disabled
        GitHubRepositoryHealthReason.RepositoryFork -> R.string.github_health_reason_repository_fork
        GitHubRepositoryHealthReason.ForkUpstreamArchived -> R.string.github_health_reason_fork_upstream_archived
        GitHubRepositoryHealthReason.ForkBehindUpstream -> R.string.github_health_reason_fork_behind_upstream
        GitHubRepositoryHealthReason.ForkCompareCurrent -> R.string.github_health_reason_fork_compare_current
        GitHubRepositoryHealthReason.ForkCompareBehind -> R.string.github_health_reason_fork_compare_behind
        GitHubRepositoryHealthReason.ForkMaintainedIndependently -> R.string.github_health_reason_fork_independent
        GitHubRepositoryHealthReason.ForkTracksUpstream -> R.string.github_health_reason_fork_tracks_upstream
        GitHubRepositoryHealthReason.StaleRepositoryActivity -> R.string.github_health_reason_stale_repository_activity
        GitHubRepositoryHealthReason.StaleRelease -> R.string.github_health_reason_stale_release
        GitHubRepositoryHealthReason.TrafficRecentlyActive -> R.string.github_health_reason_traffic_recent
        GitHubRepositoryHealthReason.ActionsHealthy -> R.string.github_health_reason_actions_healthy
        GitHubRepositoryHealthReason.ActionsFailing -> R.string.github_health_reason_actions_failing
        GitHubRepositoryHealthReason.AndroidAssetsDetected -> R.string.github_health_reason_android_assets_detected
        GitHubRepositoryHealthReason.MissingAndroidAssets -> R.string.github_health_reason_missing_android_assets
        GitHubRepositoryHealthReason.CommunityProfileComplete -> R.string.github_health_reason_community_complete
        GitHubRepositoryHealthReason.MissingReadme -> R.string.github_health_reason_missing_readme
        GitHubRepositoryHealthReason.MissingLicense -> R.string.github_health_reason_missing_license
        GitHubRepositoryHealthReason.SecuritySignalsAvailable -> R.string.github_health_reason_security_available
        GitHubRepositoryHealthReason.OpenSecurityAlerts -> R.string.github_health_reason_security_alerts
        GitHubRepositoryHealthReason.LocalPackageMatched -> R.string.github_health_reason_local_package_matched
        GitHubRepositoryHealthReason.LocalPackageMismatch -> R.string.github_health_reason_local_package_mismatch
        GitHubRepositoryHealthReason.UpdateAvailable -> R.string.github_health_reason_update_available
        GitHubRepositoryHealthReason.PreReleaseRecommended -> R.string.github_health_reason_prerelease
        GitHubRepositoryHealthReason.CheckFailed -> R.string.github_health_reason_check_failed
        GitHubRepositoryHealthReason.MissingPackageName -> R.string.github_health_reason_missing_package
        GitHubRepositoryHealthReason.MissingStableRelease -> R.string.github_health_reason_missing_stable
        GitHubRepositoryHealthReason.LocalMissing -> R.string.github_health_reason_local_missing
        GitHubRepositoryHealthReason.StableDetected -> R.string.github_health_reason_stable_detected
        GitHubRepositoryHealthReason.FreshRelease -> R.string.github_health_reason_fresh_release
    }
}
