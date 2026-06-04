@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.history

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.intent.SafeExternalIntents
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.feature.github.model.GitHubActionsNotificationHistoryRecord
import os.kei.ui.page.main.github.AppIconImage
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucideFilterIcon
import os.kei.ui.page.main.os.appLucideHistoryIcon
import os.kei.ui.page.main.os.appLucideListIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.core.AppInfoRow
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun GitHubActionsNotificationHistoryPage(
    onBack: () -> Unit,
    onOpenTrackActions: (String) -> Unit,
    viewModel: GitHubActionsNotificationHistoryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appIconState by viewModel.appIconState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)
    var expandedRecordKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showActionMenuPopup by rememberSaveable { mutableStateOf(false) }
    val iconPackageNames =
        remember(uiState.records) {
            uiState.records
                .map { it.packageName.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        }

    LaunchedEffect(uiState.records) {
        val activeKeys = uiState.records.map(::githubActionsHistoryRecordKey).toSet()
        expandedRecordKeys = expandedRecordKeys.filter { it in activeKeys }
    }

    LaunchedEffect(context, iconPackageNames) {
        viewModel.requestAppIcons(context, iconPackageNames)
    }

    AppPageScaffold(
        title = stringResource(R.string.github_actions_history_title),
        modifier =
            Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .testTag(KeiOsTestTags.GitHubActionsHistoryPageRoot),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
        titleBackdrop = pageBackdrop,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onBack,
                backdrop = pageBackdrop,
            )
        },
        actions = {
            GitHubActionsNotificationHistoryActionBar(
                backdrop = pageBackdrop,
                loading = uiState.loading,
                hasRecords = uiState.totalRecordCount > 0,
                showActionMenuPopup = showActionMenuPopup,
                filterMode = uiState.filterMode,
                sortMode = uiState.sortMode,
                sortDirection = uiState.sortDirection,
                onRefresh = viewModel::refresh,
                onShowActionMenuPopupChange = { showActionMenuPopup = it },
                onFilterModeChange = viewModel::setFilterMode,
                onSortModeChange = viewModel::setSortMode,
                onSortDirectionChange = viewModel::setSortDirection,
                onCleanupAgeSelect = viewModel::pruneOlderThan,
            )
        },
        reserveTopEndActionSpace = true,
    ) { innerPadding ->
        AppPageLazyColumn(
            innerPadding = innerPadding,
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .layerBackdrop(pageBackdrop),
            sectionSpacing = CardLayoutRhythm.denseSectionGap,
        ) {
            when {
                uiState.loading -> {
                    item(
                        key = "github-actions-history-loading",
                        contentType = "github-actions-history-state",
                    ) {
                        GitHubActionsHistoryStateCard(
                            title = stringResource(R.string.github_actions_history_loading_title),
                            summary = stringResource(R.string.github_actions_history_loading_summary),
                        )
                    }
                }

                uiState.errorMessage.isNotBlank() -> {
                    item(
                        key = "github-actions-history-error",
                        contentType = "github-actions-history-state",
                    ) {
                        GitHubActionsHistoryStateCard(
                            title = stringResource(R.string.github_actions_history_error_title),
                            summary =
                                stringResource(
                                    R.string.github_actions_history_error_summary,
                                    uiState.errorMessage,
                                ),
                        )
                    }
                }

                uiState.totalRecordCount == 0 -> {
                    item(
                        key = "github-actions-history-empty",
                        contentType = "github-actions-history-state",
                    ) {
                        GitHubActionsHistoryStateCard(
                            title = stringResource(R.string.github_actions_history_empty_title),
                            summary = stringResource(R.string.github_actions_history_empty_summary),
                        )
                    }
                }

                else -> {
                    item(
                        key = "github-actions-history-summary",
                        contentType = "github-actions-history-summary",
                    ) {
                        GitHubActionsHistorySummaryCard(uiState = uiState)
                    }
                    if (uiState.records.isEmpty()) {
                        item(
                            key = "github-actions-history-filtered-empty",
                            contentType = "github-actions-history-state",
                        ) {
                            GitHubActionsHistoryStateCard(
                                title = stringResource(R.string.github_actions_history_empty_filtered_title),
                                summary = stringResource(R.string.github_actions_history_empty_filtered_summary),
                            )
                        }
                    }
                    items(
                        items = uiState.records,
                        key = ::githubActionsHistoryRecordKey,
                        contentType = { "github-actions-history-record" },
                    ) { item ->
                        val recordKey = githubActionsHistoryRecordKey(item)
                        val expanded = recordKey in expandedRecordKeys
                        GitHubActionsHistoryRecordCard(
                            item = item,
                            appIconBitmap = appIconState.bitmaps[item.packageName.trim()],
                            expanded = expanded,
                            onExpandedChange = { nextExpanded ->
                                expandedRecordKeys =
                                    if (nextExpanded) {
                                        (expandedRecordKeys + recordKey).distinct()
                                    } else {
                                        expandedRecordKeys - recordKey
                                    }
                            },
                            onOpenTrackActions = { onOpenTrackActions(item.record.trackId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubActionsHistorySummaryCard(
    uiState: GitHubActionsNotificationHistoryUiState,
) {
    val filterLabel = stringResource(uiState.filterMode.labelRes)
    val sortLabel = stringResource(uiState.sortMode.labelRes)
    val sortDirectionLabel = stringResource(uiState.sortDirection.labelRes)
    AppFeatureCard(
        title = stringResource(R.string.github_actions_history_summary_title),
        subtitle =
            stringResource(
                R.string.github_actions_history_summary_subtitle,
                uiState.records.size,
                uiState.totalRecordCount,
            ),
        sectionIcon = appLucideFilterIcon(),
        showIndication = false,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.controlRowTextGap),
        ) {
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_summary_label_filter),
                value = filterLabel,
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_summary_label_sort),
                value =
                    stringResource(
                        R.string.github_actions_history_summary_sort_value,
                        sortLabel,
                        sortDirectionLabel,
                    ),
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            uiState.lastCleanupRemovedCount?.let { removedCount ->
                AppInfoRow(
                    label = stringResource(R.string.github_actions_history_summary_label_cleanup),
                    value =
                        stringResource(
                            R.string.github_actions_history_cleanup_removed,
                            removedCount,
                        ),
                    valueMaxLines = 1,
                    valueOverflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GitHubActionsHistoryStateCard(
    title: String,
    summary: String,
) {
    AppFeatureCard(
        title = title,
        subtitle = summary,
        sectionIcon = appLucideHistoryIcon(),
        showIndication = false,
    ) {
    }
}

@Composable
private fun GitHubActionsHistoryRecordCard(
    item: GitHubActionsNotificationHistoryUiRecord,
    appIconBitmap: android.graphics.Bitmap?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenTrackActions: () -> Unit,
) {
    val context = LocalContext.current
    val record = item.record
    val packageName = item.packageName.trim()
    val repoLabel = record.repositoryLabel
    val workflowLabel =
        record.workflowName
            .ifBlank { record.workflowPath }
            .ifBlank { stringResource(R.string.common_na) }
    val runValue = rememberFullRunValue(record)
    val title = record.appLabel.ifBlank { repoLabel }
    val subtitle =
        record.notificationContent.ifBlank {
            stringResource(
                R.string.github_actions_history_record_summary,
                record.runLabel,
                workflowLabel,
            )
        }
    val notifiedAt = rememberHistoryDateTime(record.notifiedAtMillis)
    val checkedAt =
        if (record.checkedAtMillis > 0L) {
            rememberHistoryDateTime(record.checkedAtMillis)
        } else {
            stringResource(R.string.common_na)
        }
    val statusValue = rememberStatusValue(record)
    val statusPillLabel = rememberStatusPillLabel(record)
    val artifactValue =
        stringResource(
            R.string.github_actions_history_artifacts_value,
            record.androidArtifactCount,
            record.artifactCount,
        )
    val eventValue = rememberActionsEventLabel(record.event)
    val branchValue = record.headBranch.ifBlank { stringResource(R.string.common_na) }
    val commitValue = record.headSha.ifBlank { stringResource(R.string.common_na) }
    val openLinkFailed = stringResource(R.string.github_error_open_link)
    val openRun = {
        val runUrl = record.htmlUrl.trim()
        if (runUrl.isNotBlank() && !SafeExternalIntents.startBrowsableUrl(context, runUrl)) {
            context.showToast(openLinkFailed)
        }
    }

    AppFeatureCard(
        title = title,
        subtitle = subtitle,
        eyebrow =
            stringResource(
                R.string.github_actions_history_time_notified,
                notifiedAt,
            ),
        sectionStartAction = {
            AppIconImage(
                packageName = packageName,
                bitmap = appIconBitmap,
                size = 32.dp,
            )
        },
        collapsible = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        headerEndActions = {
            StatusPill(
                label = statusPillLabel,
                color = historyStatusColor(record),
                size = AppStatusPillSize.Compact,
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.compactSectionGap),
        ) {
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_repo),
                value = repoLabel,
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_workflow),
                value = workflowLabel,
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_run),
                value = runValue,
                stacked = true,
                valueTextAlign = TextAlign.Start,
                valueMaxLines = Int.MAX_VALUE,
                valueOverflow = TextOverflow.Clip,
            )
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_branch),
                value = branchValue,
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_event),
                value = eventValue,
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_status),
                value = statusValue,
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
                valueColor = historyStatusColor(record),
            )
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_artifacts),
                value = artifactValue,
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_commit),
                value = commitValue,
                stacked = true,
                valueTextAlign = TextAlign.Start,
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_checked),
                value = checkedAt,
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_notified),
                value = notifiedAt,
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            GitHubActionsHistoryActionRow(
                runUrl = record.htmlUrl,
                onOpenTrackActions = onOpenTrackActions,
                onOpenRun = openRun,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GitHubActionsHistoryActionRow(
    runUrl: String,
    onOpenTrackActions: () -> Unit,
    onOpenRun: () -> Unit,
) {
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = CardLayoutRhythm.controlRowTextGap),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        AppLiquidTextButton(
            backdrop = null,
            text = stringResource(R.string.github_actions_history_action_open_actions),
            leadingIcon = appLucideListIcon(),
            variant = GlassVariant.Compact,
            textMaxLines = 1,
            textOverflow = TextOverflow.Ellipsis,
            onClick = onOpenTrackActions,
        )
        if (runUrl.isNotBlank()) {
            AppLiquidTextButton(
                backdrop = null,
                text = stringResource(R.string.github_actions_history_action_open_run),
                leadingIcon = appLucideExternalLinkIcon(),
                variant = GlassVariant.Compact,
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis,
                onClick = onOpenRun,
            )
        }
    }
}

@Composable
private fun rememberStatusValue(record: GitHubActionsNotificationHistoryRecord): String {
    val status = rememberActionsStatusLabel(record.status)
    val conclusion = rememberActionsConclusionLabel(record.conclusion)
    return if (record.conclusion.isBlank()) {
        status
    } else {
        stringResource(R.string.github_actions_history_status_value, status, conclusion)
    }
}

@Composable
private fun rememberStatusPillLabel(record: GitHubActionsNotificationHistoryRecord): String {
    return if (record.conclusion.isBlank()) {
        rememberActionsStatusLabel(record.status)
    } else {
        rememberActionsConclusionLabel(record.conclusion)
    }
}

@Composable
private fun rememberFullRunValue(record: GitHubActionsNotificationHistoryRecord): String {
    val runName = record.runDisplayName.trim()
    val runLabel = record.runLabel.takeIf { it != "#0" }.orEmpty()
    val attempt =
        if (record.runAttempt > 1) {
            stringResource(R.string.github_actions_value_run_attempt, record.runAttempt)
        } else {
            ""
        }
    val parts =
        listOf(
            runLabel,
            runName,
            attempt,
        ).filter { it.isNotBlank() }
    return parts.joinToString(separator = " · ").ifBlank { stringResource(R.string.common_na) }
}

@Composable
private fun historyStatusColor(record: GitHubActionsNotificationHistoryRecord) =
    when {
        record.conclusion.equals("success", ignoreCase = true) -> ColorSuccess
        record.conclusion.equals("failure", ignoreCase = true) ||
            record.conclusion.equals("cancelled", ignoreCase = true) -> MiuixTheme.colorScheme.error
        record.status.equals("completed", ignoreCase = true) -> MiuixTheme.colorScheme.onBackground
        else -> MiuixTheme.colorScheme.primary
    }

@Composable
private fun rememberHistoryDateTime(millis: Long): String {
    val locale = Locale.getDefault()
    val formatter =
        androidx.compose.runtime.remember(locale) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", locale)
        }
    return formatter.format(Date(millis))
}

@Composable
private fun rememberActionsStatusLabel(value: String): String {
    val normalized = value.trim().lowercase(Locale.ROOT)
    return when (normalized) {
        "completed" -> stringResource(R.string.github_actions_badge_completed)
        "in_progress" -> stringResource(R.string.github_actions_badge_running)
        "queued" -> stringResource(R.string.github_actions_badge_queued)
        "requested" -> stringResource(R.string.github_actions_history_status_requested)
        "waiting" -> stringResource(R.string.github_actions_history_status_waiting)
        "pending" -> stringResource(R.string.github_actions_history_status_pending)
        else -> value.ifBlank { stringResource(R.string.common_na) }
    }
}

@Composable
private fun rememberActionsConclusionLabel(value: String): String {
    val normalized = value.trim().lowercase(Locale.ROOT)
    return when (normalized) {
        "success" -> stringResource(R.string.github_actions_badge_success)
        "failure" -> stringResource(R.string.github_actions_badge_failed)
        "cancelled" -> stringResource(R.string.github_actions_history_conclusion_cancelled)
        "skipped" -> stringResource(R.string.github_actions_history_conclusion_skipped)
        "neutral" -> stringResource(R.string.github_actions_history_conclusion_neutral)
        "timed_out" -> stringResource(R.string.github_actions_history_conclusion_timed_out)
        "action_required" -> stringResource(R.string.github_actions_history_conclusion_action_required)
        "startup_failure" -> stringResource(R.string.github_actions_history_conclusion_startup_failure)
        "stale" -> stringResource(R.string.github_actions_history_conclusion_stale)
        else -> value.ifBlank { stringResource(R.string.common_na) }
    }
}

@Composable
private fun rememberActionsEventLabel(value: String): String {
    val normalized = value.trim().lowercase(Locale.ROOT)
    return when (normalized) {
        "push" -> stringResource(R.string.github_actions_history_event_push)
        "pull_request" -> stringResource(R.string.github_actions_history_event_pull_request)
        "pull_request_target" -> stringResource(R.string.github_actions_history_event_pull_request_target)
        "workflow_dispatch" -> stringResource(R.string.github_actions_history_event_workflow_dispatch)
        "schedule" -> stringResource(R.string.github_actions_history_event_schedule)
        "release" -> stringResource(R.string.github_actions_history_event_release)
        "repository_dispatch" -> stringResource(R.string.github_actions_history_event_repository_dispatch)
        "issues" -> stringResource(R.string.github_actions_history_event_issues)
        "issue_comment" -> stringResource(R.string.github_actions_history_event_issue_comment)
        else -> value.ifBlank { stringResource(R.string.common_na) }
    }
}

private fun githubActionsHistoryRecordKey(item: GitHubActionsNotificationHistoryUiRecord): String =
    githubActionsHistoryRecordKey(item.record)

private fun githubActionsHistoryRecordKey(record: GitHubActionsNotificationHistoryRecord): String =
    "${record.trackId}|${record.runId}|${record.runNumber}|${record.notifiedAtMillis}"

private val ColorSuccess = androidx.compose.ui.graphics.Color(0xFF22C55E)
