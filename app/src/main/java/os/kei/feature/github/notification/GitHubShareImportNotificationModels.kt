package os.kei.feature.github.notification

import androidx.annotation.StringRes
import os.kei.R

private const val GITHUB_SHARE_IMPORT_MI_ISLAND_SUCCESS_COLOR = "#22C55E"
private const val GITHUB_SHARE_IMPORT_MI_ISLAND_DANGER_COLOR = "#EF4444"
private const val GITHUB_SHARE_IMPORT_MI_ISLAND_NEUTRAL_COLOR = "#64748B"

internal data class GitHubShareImportNotificationState(
    val phase: GitHubShareImportNotificationPhase,
    val owner: String = "",
    val repo: String = "",
    val releaseTag: String = "",
    val assetName: String = "",
    val appLabel: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val targetDisplayName: String = "",
    val primaryLabel: String = "",
    val count: Int = 0,
    val sendInstallActionEnabled: Boolean = false,
    val pageInstallConfirmActionEnabled: Boolean = false,
    val progressPercentOverride: Int? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L
) {
    val projectLabel: String
        get() {
            val normalizedOwner = owner.trim()
            val normalizedRepo = repo.trim()
            return when {
                normalizedOwner.isNotBlank() && normalizedRepo.isNotBlank() ->
                    "$normalizedOwner/$normalizedRepo"

                normalizedRepo.isNotBlank() -> normalizedRepo
                normalizedOwner.isNotBlank() -> normalizedOwner
                primaryLabel.isNotBlank() -> primaryLabel
                else -> "GitHub"
            }
        }

    val projectDisplayLabel: String
        get() = repo.trim()
            .ifBlank { owner.trim() }
            .ifBlank { githubProjectDisplayName(primaryLabel) }
            .ifBlank { "GitHub" }

    val appDisplayLabel: String
        get() = appLabel
            .ifBlank { targetDisplayName }
            .ifBlank { projectDisplayLabel }
            .ifBlank { packageName }
            .ifBlank { projectLabel }

    val installDetectedProjectLabel: String
        get() {
            val project = projectDisplayLabel
            val version = versionName.trim()
            return when {
                version.isBlank() -> project
                project.equals(version, ignoreCase = true) -> project
                else -> "$project · $version"
            }
        }

    private val versionDisplayLabel: String
        get() = versionName.trim()
            .ifBlank {
                releaseTag
                    .trim()
                    .takeIf { it.isNotBlank() && !it.equals("latest", ignoreCase = true) }
                    .orEmpty()
            }

    private val readableTargetLabel: String
        get() = appLabel.trim()
            .ifBlank { targetDisplayName.trim().takeUnless(::looksLikePackageName).orEmpty() }
            .ifBlank { projectDisplayLabel }
            .ifBlank { cleanNotificationAssetName(assetName) }
            .ifBlank { packageName.trim() }
            .ifBlank { projectLabel }

    val targetWithVersionLabel: String
        get() {
            val target = readableTargetLabel
            val version = versionDisplayLabel
            return when {
                version.isBlank() -> target
                target.equals(version, ignoreCase = true) -> target
                else -> "$target · $version"
            }
        }

    fun compactIslandTitle(shortText: String): String {
        if (phase == GitHubShareImportNotificationPhase.InstallDownloading &&
            totalBytes > 0L &&
            resolvedProgressPercent in 1..99
        ) {
            return "${resolvedProgressPercent}%"
        }
        return shortText.ifBlank { readableTargetLabel }
    }

    fun compactIslandSubtitle(shortText: String, islandTitle: String): String {
        if (phase == GitHubShareImportNotificationPhase.InstallDownloading &&
            totalBytes > 0L &&
            resolvedProgressPercent in 1..99
        ) {
            return shortText.takeIf { it.isNotBlank() && it != islandTitle }.orEmpty()
        }
        return ""
    }

    val resolvedProgressPercent: Int
        get() = progressPercentOverride?.coerceIn(0, 100) ?: phase.progressPercent

    val primaryActionRes: Int
        get() {
            if (
                phase == GitHubShareImportNotificationPhase.AssetReady &&
                sendInstallActionEnabled
            ) {
                return R.string.github_share_import_notify_action_send_install
            }
            return phase.primaryActionRes
        }
}

private fun looksLikePackageName(value: String): Boolean {
    val normalized = value.trim()
    if (normalized.isBlank() || normalized.any(Char::isWhitespace)) return false
    return packageNameDisplayRegex.matches(normalized)
}

private fun githubProjectDisplayName(value: String): String {
    val path = value.trim()
        .substringAfter("://", value.trim())
        .removePrefix("github.com/")
        .removePrefix("www.github.com/")
        .substringBefore('?')
        .substringBefore('#')
        .trim('/')
    val segments = path.split('/').map { it.trim() }.filter { it.isNotBlank() }
    return when {
        segments.size >= 2 -> segments[1]
        segments.size == 1 -> segments[0]
        else -> ""
    }
}

private fun cleanNotificationAssetName(assetName: String): String {
    val fileName = assetName.trim()
        .substringAfterLast('/')
        .substringAfterLast('\\')
    return fileName
        .replace(apkFileSuffixRegex, "")
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(notificationWhitespaceRegex, " ")
        .trim()
}

private val packageNameDisplayRegex = Regex("""^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+$""")
private val apkFileSuffixRegex = Regex("""\.apk$""", RegexOption.IGNORE_CASE)
private val notificationWhitespaceRegex = Regex("""\s+""")

internal enum class GitHubShareImportNotificationPhase(
    @param:StringRes val titleRes: Int,
    @param:StringRes val shortTextRes: Int,
    @param:StringRes val primaryActionRes: Int,
    val progressPercent: Int,
    val ongoing: Boolean,
    val openGitHubPage: Boolean,
    val cancelActionEnabled: Boolean = false,
    val refreshActionEnabled: Boolean = false,
    val confirmActionEnabled: Boolean = false,
    val promotedLiveUpdate: Boolean = false,
    val miIslandProgressColor: String? = null,
    val progressTemplateEnabled: Boolean = true
) {
    Resolving(
        titleRes = R.string.github_share_import_notify_title_resolving,
        shortTextRes = R.string.github_share_import_notify_short_resolving,
        primaryActionRes = R.string.github_share_import_notify_action_view_progress,
        progressPercent = 12,
        ongoing = true,
        openGitHubPage = false
    ),
    AssetReady(
        titleRes = R.string.github_share_import_notify_title_asset_ready,
        shortTextRes = R.string.github_share_import_notify_short_asset_ready,
        primaryActionRes = R.string.github_share_import_notify_action_select_apk,
        progressPercent = 32,
        ongoing = true,
        openGitHubPage = false,
        cancelActionEnabled = true
    ),
    Delivering(
        titleRes = R.string.github_share_import_notify_title_delivering,
        shortTextRes = R.string.github_share_import_notify_short_delivering,
        primaryActionRes = R.string.github_share_import_notify_action_view_progress,
        progressPercent = 52,
        ongoing = true,
        openGitHubPage = false
    ),
    InstallDownloading(
        titleRes = R.string.github_share_import_notify_title_install_downloading,
        shortTextRes = R.string.github_share_import_notify_short_install_downloading,
        primaryActionRes = R.string.github_share_import_notify_action_view_progress,
        progressPercent = 56,
        ongoing = true,
        openGitHubPage = false,
        cancelActionEnabled = true
    ),
    Installing(
        titleRes = R.string.github_share_import_notify_title_installing,
        shortTextRes = R.string.github_share_import_notify_short_installing,
        primaryActionRes = R.string.github_share_import_notify_action_view_progress,
        progressPercent = 64,
        ongoing = true,
        openGitHubPage = false,
        cancelActionEnabled = true
    ),
    InstallReady(
        titleRes = R.string.github_share_import_notify_title_install_ready,
        shortTextRes = R.string.github_share_import_notify_short_install_ready,
        primaryActionRes = R.string.github_share_import_notify_action_view_status,
        progressPercent = 88,
        ongoing = true,
        openGitHubPage = false,
        cancelActionEnabled = true
    ),
    InstallCommitting(
        titleRes = R.string.github_share_import_notify_title_install_committing,
        shortTextRes = R.string.github_share_import_notify_short_install_committing,
        primaryActionRes = R.string.github_share_import_notify_action_view_status,
        progressPercent = 92,
        ongoing = true,
        openGitHubPage = false
    ),
    WaitingInstall(
        titleRes = R.string.github_share_import_notify_title_waiting_install,
        shortTextRes = R.string.github_share_import_notify_short_waiting_install,
        primaryActionRes = R.string.github_share_import_notify_action_view_status,
        progressPercent = 72,
        ongoing = true,
        openGitHubPage = false,
        cancelActionEnabled = true,
        refreshActionEnabled = true
    ),
    InstallDetected(
        titleRes = R.string.github_share_import_notify_title_install_detected,
        shortTextRes = R.string.github_share_import_notify_short_install_detected,
        primaryActionRes = R.string.github_share_import_notify_action_confirm_track,
        progressPercent = 86,
        ongoing = true,
        openGitHubPage = false,
        cancelActionEnabled = true,
        confirmActionEnabled = true
    ),
    AddingTrack(
        titleRes = R.string.github_share_import_notify_title_adding_track,
        shortTextRes = R.string.github_share_import_notify_short_adding_track,
        primaryActionRes = R.string.github_share_import_notify_action_view_progress,
        progressPercent = 94,
        ongoing = true,
        openGitHubPage = false
    ),
    Added(
        titleRes = R.string.github_share_import_notify_title_added,
        shortTextRes = R.string.github_share_import_notify_short_added,
        primaryActionRes = R.string.github_share_import_notify_action_view_tracking,
        progressPercent = 100,
        ongoing = false,
        openGitHubPage = true,
        promotedLiveUpdate = true,
        miIslandProgressColor = GITHUB_SHARE_IMPORT_MI_ISLAND_SUCCESS_COLOR,
        progressTemplateEnabled = false
    ),
    AlreadyTracked(
        titleRes = R.string.github_share_import_notify_title_already_tracked,
        shortTextRes = R.string.github_share_import_notify_short_already_tracked,
        primaryActionRes = R.string.github_share_import_notify_action_view_tracking,
        progressPercent = 100,
        ongoing = false,
        openGitHubPage = true,
        promotedLiveUpdate = true,
        miIslandProgressColor = GITHUB_SHARE_IMPORT_MI_ISLAND_SUCCESS_COLOR,
        progressTemplateEnabled = false
    ),
    PageInstallConfirm(
        titleRes = R.string.github_page_install_notify_title_confirm,
        shortTextRes = R.string.github_page_install_notify_short_confirm,
        primaryActionRes = R.string.github_share_import_notify_action_view_github,
        progressPercent = 18,
        ongoing = true,
        openGitHubPage = true,
        progressTemplateEnabled = false
    ),
    PageInstallCompleted(
        titleRes = R.string.github_page_install_notify_title_completed,
        shortTextRes = R.string.github_page_install_notify_short_completed,
        primaryActionRes = R.string.github_share_import_notify_action_view_github,
        progressPercent = 100,
        ongoing = false,
        openGitHubPage = true,
        promotedLiveUpdate = true,
        miIslandProgressColor = GITHUB_SHARE_IMPORT_MI_ISLAND_SUCCESS_COLOR,
        progressTemplateEnabled = false
    ),
    Failed(
        titleRes = R.string.github_share_import_notify_title_failed,
        shortTextRes = R.string.github_share_import_notify_short_failed,
        primaryActionRes = R.string.github_share_import_notify_action_view_github,
        progressPercent = 100,
        ongoing = false,
        openGitHubPage = true,
        promotedLiveUpdate = true,
        miIslandProgressColor = GITHUB_SHARE_IMPORT_MI_ISLAND_DANGER_COLOR,
        progressTemplateEnabled = false
    ),
    PageInstallFailed(
        titleRes = R.string.github_page_install_notify_title_failed,
        shortTextRes = R.string.github_share_import_notify_short_failed,
        primaryActionRes = R.string.github_share_import_notify_action_view_github,
        progressPercent = 100,
        ongoing = false,
        openGitHubPage = true,
        promotedLiveUpdate = true,
        miIslandProgressColor = GITHUB_SHARE_IMPORT_MI_ISLAND_DANGER_COLOR,
        progressTemplateEnabled = false
    ),
    Cancelled(
        titleRes = R.string.github_share_import_notify_title_cancelled,
        shortTextRes = R.string.github_share_import_notify_short_cancelled,
        primaryActionRes = R.string.github_share_import_notify_action_view_github,
        progressPercent = 100,
        ongoing = false,
        openGitHubPage = true,
        promotedLiveUpdate = true,
        miIslandProgressColor = GITHUB_SHARE_IMPORT_MI_ISLAND_NEUTRAL_COLOR,
        progressTemplateEnabled = false
    )
}
