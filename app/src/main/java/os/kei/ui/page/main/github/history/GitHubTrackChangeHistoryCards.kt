@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.history

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
import os.kei.feature.github.model.GitHubTrackChangeField
import os.kei.feature.github.model.GitHubTrackChangeHistoryAction
import os.kei.feature.github.model.GitHubTrackChangeHistoryRecord
import os.kei.feature.github.model.GitHubTrackChangeHistorySource
import os.kei.ui.page.main.github.AppIconImage
import os.kei.ui.page.main.github.sheet.trackedSourceModeLabel
import os.kei.ui.page.main.os.appLucideAddIcon
import os.kei.ui.page.main.os.appLucideEditIcon
import os.kei.ui.page.main.os.appLucideTrashIcon
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
internal fun GitHubTrackChangeHistoryRecordCard(
    item: GitHubTrackChangeHistoryUiRecord,
    appIconBitmap: android.graphics.Bitmap?,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit,
) {
    val record = item.record
    val changedAt = rememberGitHubHistoryDateTime(record.changedAtMillis)
    val actionLabel = rememberTrackChangeActionLabel(record.action)
    val sourceLabel = rememberTrackChangeSourceLabel(record.source)
    val fieldLabel = rememberTrackChangeFieldsLabel(record)
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
            R.string.github_history_tracking_record_summary,
            sourceLabel,
            fieldLabel,
        )
    AppFeatureCard(
        title = title,
        subtitle = subtitle,
        modifier = modifier.testTag(KeiOsTestTags.GitHubTrackChangeHistoryCard),
        exportBackdropToContent = true,
        eyebrow = stringResource(R.string.github_history_tracking_time_changed, changedAt),
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
                    imageVector = rememberTrackChangeActionIcon(record.action),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = trackChangeActionColor(record.action),
                )
            }
        },
        collapsible = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        headerEndActions = {
            StatusPill(
                label = actionLabel,
                color = trackChangeActionColor(record.action),
                size = AppStatusPillSize.Compact,
            )
        },
    ) {
        AppInfoListBody(
            modifier = Modifier.fillMaxWidth().testTag(KeiOsTestTags.GitHubTrackChangeHistoryDetails),
            verticalSpacing = CardLayoutRhythm.compactSectionGap,
        ) {
            AppInfoRow(
                label = stringResource(R.string.github_history_tracking_label_action),
                value = actionLabel,
                valueColor = trackChangeActionColor(record.action),
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_history_refresh_label_source),
                value = sourceLabel,
                valueMaxLines = 1,
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
                label = stringResource(R.string.github_history_tracking_label_fields),
                value = fieldLabel,
                stacked = true,
                valueMaxLines = 3,
                valueOverflow = TextOverflow.Ellipsis,
            )
            if (record.previousTrackId.isNotBlank()) {
                AppInfoRow(
                    label = stringResource(R.string.github_history_tracking_label_previous_id),
                    value = record.previousTrackId,
                    stacked = true,
                    valueMaxLines = 2,
                    valueOverflow = TextOverflow.Ellipsis,
                )
            }
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

@Composable
private fun rememberTrackChangeFieldsLabel(record: GitHubTrackChangeHistoryRecord): String {
    if (record.changedFields.isEmpty()) {
        return when (record.action) {
            GitHubTrackChangeHistoryAction.Added ->
                stringResource(R.string.github_history_tracking_fields_created)
            GitHubTrackChangeHistoryAction.Deleted ->
                stringResource(R.string.github_history_tracking_fields_deleted)
            GitHubTrackChangeHistoryAction.Updated ->
                stringResource(R.string.github_history_tracking_fields_unknown)
        }
    }
    return record.changedFields
        .map { field -> rememberTrackChangeFieldLabel(field) }
        .joinToString(" · ")
}

@Composable
private fun rememberTrackChangeActionLabel(action: GitHubTrackChangeHistoryAction): String =
    when (action) {
        GitHubTrackChangeHistoryAction.Added ->
            stringResource(R.string.github_history_tracking_action_added)
        GitHubTrackChangeHistoryAction.Updated ->
            stringResource(R.string.github_history_tracking_action_updated)
        GitHubTrackChangeHistoryAction.Deleted ->
            stringResource(R.string.github_history_tracking_action_deleted)
    }

@Composable
private fun rememberTrackChangeSourceLabel(source: GitHubTrackChangeHistorySource): String =
    when (source) {
        GitHubTrackChangeHistorySource.Page ->
            stringResource(R.string.github_history_tracking_source_page)
        GitHubTrackChangeHistorySource.Import ->
            stringResource(R.string.github_history_tracking_source_import)
        GitHubTrackChangeHistorySource.StarImport ->
            stringResource(R.string.github_history_tracking_source_star_import)
    }

@Composable
private fun rememberTrackChangeFieldLabel(field: GitHubTrackChangeField): String =
    when (field) {
        GitHubTrackChangeField.Repository ->
            stringResource(R.string.github_history_tracking_field_repository)
        GitHubTrackChangeField.PackageName ->
            stringResource(R.string.github_history_tracking_field_package_name)
        GitHubTrackChangeField.AppLabel ->
            stringResource(R.string.github_history_tracking_field_app_label)
        GitHubTrackChangeField.SourceMode ->
            stringResource(R.string.github_history_tracking_field_source_mode)
        GitHubTrackChangeField.PreferPreRelease ->
            stringResource(R.string.github_history_tracking_field_pre_release)
        GitHubTrackChangeField.LatestReleaseDownloadButton ->
            stringResource(R.string.github_history_tracking_field_download_button)
        GitHubTrackChangeField.ActionsUpdates ->
            stringResource(R.string.github_history_tracking_field_actions)
        GitHubTrackChangeField.UpdateInterval ->
            stringResource(R.string.github_history_tracking_field_update_interval)
        GitHubTrackChangeField.ActionsUpdateInterval ->
            stringResource(R.string.github_history_tracking_field_actions_interval)
        GitHubTrackChangeField.PreciseApkVersion ->
            stringResource(R.string.github_history_tracking_field_precise_apk)
        GitHubTrackChangeField.IgnoreMode ->
            stringResource(R.string.github_history_tracking_field_ignore_mode)
        GitHubTrackChangeField.IgnoredStableRelease ->
            stringResource(R.string.github_history_tracking_field_ignored_stable)
        GitHubTrackChangeField.IgnoredPreRelease ->
            stringResource(R.string.github_history_tracking_field_ignored_pre)
        GitHubTrackChangeField.FdroidConfig ->
            stringResource(R.string.github_history_tracking_field_fdroid)
    }

@Composable
private fun rememberTrackChangeActionIcon(action: GitHubTrackChangeHistoryAction) =
    when (action) {
        GitHubTrackChangeHistoryAction.Added -> appLucideAddIcon()
        GitHubTrackChangeHistoryAction.Updated -> appLucideEditIcon()
        GitHubTrackChangeHistoryAction.Deleted -> appLucideTrashIcon()
    }

@Composable
private fun trackChangeActionColor(action: GitHubTrackChangeHistoryAction): Color =
    when (action) {
        GitHubTrackChangeHistoryAction.Added -> Color(0xFF22C55E)
        GitHubTrackChangeHistoryAction.Updated -> MiuixTheme.colorScheme.primary
        GitHubTrackChangeHistoryAction.Deleted -> MiuixTheme.colorScheme.error
    }
