@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.history

import android.content.pm.PackageInstaller
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.GitHubAppInstallHistoryAction
import os.kei.feature.github.model.GitHubAppInstallHistoryRecord
import os.kei.feature.github.model.GitHubAppInstallHistorySource
import os.kei.feature.github.model.GitHubAppInstallSourceInfo
import os.kei.ui.page.main.github.AppIconImage
import os.kei.ui.page.main.github.sheet.trackedSourceModeLabel
import os.kei.ui.page.main.os.appLucideAddIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideTrashIcon
import os.kei.ui.page.main.os.appLucideUndoIcon
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.core.AppInfoListBody
import os.kei.ui.page.main.widget.core.AppInfoRow
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubAppInstallHistoryRecordCard(
    item: GitHubAppInstallHistoryUiRecord,
    appIconBitmap: android.graphics.Bitmap?,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit,
) {
    val record = item.record
    val changedAt = rememberGitHubHistoryDateTime(record.changedAtMillis)
    val actionLabel = rememberAppInstallActionLabel(record.action)
    val versionChange = rememberAppInstallVersionChange(record)
    val repositoryLabel =
        listOf(record.owner, record.repo)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("/")
    val title =
        record.appLabel
            .ifBlank { repositoryLabel }
            .ifBlank { record.packageName }
            .ifBlank { record.trackId }
    val subtitle =
        stringResource(
            R.string.github_history_apps_record_summary,
            actionLabel,
            versionChange,
        )
    val installSourceInfo = effectiveInstallSourceInfo(record)
    val currentInstaller = rememberInstallSourcePackageLabel(
        packageName = installSourceInfo.installingPackageName,
        label = installSourceInfo.installingPackageLabel,
    )
    val previousInstaller = rememberInstallSourcePackageLabel(
        packageName = record.previousInstallSourceInfo.installingPackageName,
        label = record.previousInstallSourceInfo.installingPackageLabel,
    )
    val showPreviousInstaller =
        record.previousInstallSourceInfo.hasInstallSourceDetails() &&
            record.currentInstallSourceInfo.hasInstallSourceDetails() &&
            previousInstaller != currentInstaller
    AppFeatureCard(
        title = title,
        subtitle = subtitle,
        modifier = modifier.testTag(KeiOsTestTags.GitHubAppInstallHistoryCard),
        exportBackdropToContent = true,
        eyebrow = stringResource(R.string.github_history_apps_time_changed, changedAt),
        sectionStartAction = {
            val packageName = record.packageName.trim()
            if (packageName.isNotBlank()) {
                AppIconImage(
                    packageName = packageName,
                    bitmap = appIconBitmap,
                    size = 32.dp,
                )
            } else {
                Icon(
                    imageVector = rememberAppInstallActionIcon(record.action),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = appInstallActionColor(record.action),
                )
            }
        },
        collapsible = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        headerEndActions = {
            StatusPill(
                label = actionLabel,
                color = appInstallActionColor(record.action),
                size = AppStatusPillSize.Compact,
            )
        },
    ) {
        AppInfoListBody(
            modifier = Modifier.fillMaxWidth().testTag(KeiOsTestTags.GitHubAppInstallHistoryDetails),
            verticalSpacing = CardLayoutRhythm.compactSectionGap,
        ) {
            AppInfoRow(
                label = stringResource(R.string.github_history_tracking_label_action),
                value = actionLabel,
                valueColor = appInstallActionColor(record.action),
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_apps_label_version_change),
                value = versionChange,
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_tracking_label_source_mode),
                value = trackedSourceModeLabel(record.sourceMode),
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_repo),
                value = repositoryLabel.ifBlank { record.repoUrl.ifBlank { stringResource(R.string.common_na) } },
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_tracking_label_package),
                value = record.packageName.ifBlank { stringResource(R.string.common_na) },
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_apps_label_previous_version),
                value = rememberAppInstallVersionLabel(record.previousVersionName, record.previousVersionCode),
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_apps_label_current_version),
                value = rememberAppInstallVersionLabel(record.currentVersionName, record.currentVersionCode),
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_apps_label_install_source_type),
                value = rememberInstallSourceTypeLabel(installSourceInfo.packageSource),
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_apps_label_installer),
                value = currentInstaller,
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_apps_label_initiating_installer),
                value = rememberInstallSourcePackageLabel(
                    packageName = installSourceInfo.initiatingPackageName,
                    label = installSourceInfo.initiatingPackageLabel,
                ),
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_apps_label_originating_source),
                value = rememberInstallSourcePackageLabel(
                    packageName = installSourceInfo.originatingPackageName,
                    label = installSourceInfo.originatingPackageLabel,
                ),
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_apps_label_update_owner),
                value = rememberInstallSourcePackageLabel(
                    packageName = installSourceInfo.updateOwnerPackageName,
                    label = installSourceInfo.updateOwnerPackageLabel,
                ),
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
            if (showPreviousInstaller) {
                AppInfoRow(
                    label = stringResource(R.string.github_history_apps_label_previous_installer),
                    value = previousInstaller,
                    valueMaxLines = 2,
                    valueOverflow = TextOverflow.Ellipsis,
                )
            }
            AppInfoRow(
                label = stringResource(R.string.github_history_refresh_label_source),
                value = rememberAppInstallSourceLabel(record.source),
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_apps_label_broadcast),
                value = record.broadcastAction.ifBlank { stringResource(R.string.common_na) },
                stacked = true,
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_apps_label_broadcast_uid),
                value = rememberBroadcastUidLabel(record.broadcastUid),
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_apps_label_broadcast_flags),
                value = rememberBroadcastFlagsLabel(record),
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_tracking_label_track_id),
                value = record.trackId,
                stacked = true,
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun effectiveInstallSourceInfo(
    record: GitHubAppInstallHistoryRecord,
): GitHubAppInstallSourceInfo =
    when {
        record.currentInstallSourceInfo.hasInstallSourceDetails() -> record.currentInstallSourceInfo
        record.previousInstallSourceInfo.hasInstallSourceDetails() -> record.previousInstallSourceInfo
        else -> GitHubAppInstallSourceInfo()
    }

private fun GitHubAppInstallSourceInfo.hasInstallSourceDetails(): Boolean =
    installingPackageName.isNotBlank() ||
        initiatingPackageName.isNotBlank() ||
        originatingPackageName.isNotBlank() ||
        updateOwnerPackageName.isNotBlank() ||
        packageSource >= 0

@Composable
private fun rememberAppInstallVersionChange(record: GitHubAppInstallHistoryRecord): String {
    val previous = rememberAppInstallVersionLabel(record.previousVersionName, record.previousVersionCode)
    val current = rememberAppInstallVersionLabel(record.currentVersionName, record.currentVersionCode)
    return when (record.action) {
        GitHubAppInstallHistoryAction.Installed ->
            stringResource(R.string.github_history_apps_version_installed, current)
        GitHubAppInstallHistoryAction.Uninstalled ->
            stringResource(R.string.github_history_apps_version_uninstalled, previous)
        GitHubAppInstallHistoryAction.Updated,
        GitHubAppInstallHistoryAction.Downgraded ->
            stringResource(R.string.github_history_apps_version_changed, previous, current)
    }
}

@Composable
private fun rememberAppInstallVersionLabel(
    versionName: String,
    versionCode: Long,
): String {
    val name = versionName.trim()
    return when {
        name.isNotBlank() && versionCode >= 0L ->
            stringResource(R.string.github_history_apps_version_name_code, name, versionCode)
        name.isNotBlank() -> name
        versionCode >= 0L -> versionCode.toString()
        else -> stringResource(R.string.common_na)
    }
}

@Composable
private fun rememberInstallSourceTypeLabel(packageSource: Int): String {
    val label =
        when (packageSource) {
            PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED ->
                stringResource(R.string.github_history_apps_install_source_unspecified)
            PackageInstaller.PACKAGE_SOURCE_OTHER ->
                stringResource(R.string.github_history_apps_install_source_other)
            PackageInstaller.PACKAGE_SOURCE_STORE ->
                stringResource(R.string.github_history_apps_install_source_store)
            PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE ->
                stringResource(R.string.github_history_apps_install_source_local_file)
            PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE ->
                stringResource(R.string.github_history_apps_install_source_downloaded_file)
            else ->
                stringResource(R.string.github_history_apps_install_source_unknown)
        }
    return if (packageSource >= 0) {
        stringResource(R.string.github_history_apps_install_source_value, label, packageSource)
    } else {
        label
    }
}

@Composable
private fun rememberInstallSourcePackageLabel(
    packageName: String,
    label: String,
): String {
    val normalizedPackage = packageName.trim()
    val normalizedLabel = label.trim()
    return when {
        normalizedPackage.isBlank() && normalizedLabel.isBlank() -> stringResource(R.string.common_na)
        normalizedPackage.isBlank() -> normalizedLabel
        normalizedLabel.isBlank() || normalizedLabel == normalizedPackage -> normalizedPackage
        else -> stringResource(
            R.string.github_history_apps_package_label_value,
            normalizedLabel,
            normalizedPackage,
        )
    }
}

@Composable
private fun rememberBroadcastUidLabel(uid: Int): String =
    if (uid >= 0) uid.toString() else stringResource(R.string.common_na)

@Composable
private fun rememberBroadcastFlagsLabel(record: GitHubAppInstallHistoryRecord): String {
    val flags =
        buildList {
            if (record.replacing) add(stringResource(R.string.github_history_apps_broadcast_flag_replacing))
            if (record.broadcastDataRemoved) {
                add(stringResource(R.string.github_history_apps_broadcast_flag_data_removed))
            }
            if (record.broadcastUserInitiated) {
                add(stringResource(R.string.github_history_apps_broadcast_flag_user_initiated))
            }
            if (record.broadcastArchival) add(stringResource(R.string.github_history_apps_broadcast_flag_archival))
        }
    return flags.joinToString(" · ").ifBlank {
        stringResource(R.string.github_history_apps_broadcast_flag_none)
    }
}

@Composable
private fun rememberAppInstallActionLabel(action: GitHubAppInstallHistoryAction): String =
    when (action) {
        GitHubAppInstallHistoryAction.Installed ->
            stringResource(R.string.github_history_apps_action_installed)
        GitHubAppInstallHistoryAction.Updated ->
            stringResource(R.string.github_history_apps_action_updated)
        GitHubAppInstallHistoryAction.Downgraded ->
            stringResource(R.string.github_history_apps_action_downgraded)
        GitHubAppInstallHistoryAction.Uninstalled ->
            stringResource(R.string.github_history_apps_action_uninstalled)
    }

@Composable
private fun rememberAppInstallSourceLabel(source: GitHubAppInstallHistorySource): String =
    when (source) {
        GitHubAppInstallHistorySource.PackageBroadcast ->
            stringResource(R.string.github_history_apps_source_package_broadcast)
    }

@Composable
private fun appInstallActionColor(action: GitHubAppInstallHistoryAction): Color =
    when (action) {
        GitHubAppInstallHistoryAction.Installed -> Color(0xFF22C55E)
        GitHubAppInstallHistoryAction.Updated -> MiuixTheme.colorScheme.primary
        GitHubAppInstallHistoryAction.Downgraded -> Color(0xFFF59E0B)
        GitHubAppInstallHistoryAction.Uninstalled -> MiuixTheme.colorScheme.error
    }

@Composable
private fun rememberAppInstallActionIcon(action: GitHubAppInstallHistoryAction) =
    when (action) {
        GitHubAppInstallHistoryAction.Installed -> appLucideAddIcon()
        GitHubAppInstallHistoryAction.Updated -> appLucideRefreshIcon()
        GitHubAppInstallHistoryAction.Downgraded -> appLucideUndoIcon()
        GitHubAppInstallHistoryAction.Uninstalled -> appLucideTrashIcon()
    }
