package os.kei.ui.page.main.github.share

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import os.kei.R
import os.kei.ui.page.main.github.GitHubStatusPalette

internal enum class GitHubShareImportPhase(
    @param:StringRes val labelRes: Int,
    val color: Color
) {
    Idle(
        labelRes = R.string.github_share_import_phase_idle,
        color = GitHubStatusPalette.Stable
    ),
    Resolving(
        labelRes = R.string.github_share_import_phase_resolving,
        color = GitHubStatusPalette.Active
    ),
    AssetReady(
        labelRes = R.string.github_share_import_phase_asset_ready,
        color = GitHubStatusPalette.Update
    ),
    Delivering(
        labelRes = R.string.github_share_import_phase_delivering,
        color = GitHubStatusPalette.Active
    ),
    InstallDownloading(
        labelRes = R.string.github_share_import_phase_install_downloading,
        color = GitHubStatusPalette.Active
    ),
    Installing(
        labelRes = R.string.github_share_import_phase_installing,
        color = GitHubStatusPalette.Active
    ),
    InstallReady(
        labelRes = R.string.github_share_import_phase_install_ready,
        color = GitHubStatusPalette.Update
    ),
    InstallCommitting(
        labelRes = R.string.github_share_import_phase_install_committing,
        color = GitHubStatusPalette.Active
    ),
    WaitingInstall(
        labelRes = R.string.github_share_import_phase_waiting_install,
        color = GitHubStatusPalette.PreRelease
    ),
    InstallDetected(
        labelRes = R.string.github_share_import_phase_install_detected,
        color = GitHubStatusPalette.Update
    ),
    AddingTrack(
        labelRes = R.string.github_share_import_phase_adding_track,
        color = GitHubStatusPalette.Active
    ),
    Added(
        labelRes = R.string.github_share_import_phase_added,
        color = GitHubStatusPalette.Update
    ),
    Failed(
        labelRes = R.string.github_share_import_phase_failed,
        color = GitHubStatusPalette.Error
    )
}

internal fun shareImportRemainingMinutes(
    armedAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis()
): Int {
    val expiresAtMillis = armedAtMillis + shareImportTrackMaxAgeMs
    val remainingMs = (expiresAtMillis - nowMillis).coerceAtLeast(0L)
    return ((remainingMs + 59_999L) / 60_000L).toInt()
}
