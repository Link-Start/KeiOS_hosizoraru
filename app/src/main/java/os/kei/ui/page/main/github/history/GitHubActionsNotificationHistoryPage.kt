@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.feature.github.model.GitHubActionsNotificationHistoryRecord
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideBellIcon
import os.kei.ui.page.main.os.appLucideHistoryIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.core.AppInfoRow
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)

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

                uiState.records.isEmpty() -> {
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
                    items(
                        items = uiState.records,
                        key = { record ->
                            "${record.trackId}|${record.runId}|${record.runNumber}|${record.notifiedAtMillis}"
                        },
                        contentType = { "github-actions-history-record" },
                    ) { record ->
                        GitHubActionsHistoryRecordCard(
                            record = record,
                            onClick = { onOpenTrackActions(record.trackId) },
                        )
                    }
                }
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
    record: GitHubActionsNotificationHistoryRecord,
    onClick: () -> Unit,
) {
    val repoLabel = record.repositoryLabel
    val workflowLabel =
        record.workflowName
            .ifBlank { record.workflowPath }
            .ifBlank { stringResource(R.string.common_na) }
    val runLabel =
        record.runDisplayName
            .ifBlank { record.runLabel }
            .ifBlank { stringResource(R.string.common_na) }
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
    val artifactValue =
        stringResource(
            R.string.github_actions_history_artifacts_value,
            record.androidArtifactCount,
            record.artifactCount,
        )

    AppFeatureCard(
        title = title,
        subtitle = subtitle,
        eyebrow =
            stringResource(
                R.string.github_actions_history_time_notified,
                notifiedAt,
            ),
        sectionIcon = appLucideBellIcon(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_repo),
                value = repoLabel,
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_workflow),
                value = workflowLabel,
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_run),
                value = runLabel,
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
            AppInfoRow(
                label = stringResource(R.string.github_actions_history_label_branch),
                value = record.headBranch.ifBlank { stringResource(R.string.common_na) },
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
                label = stringResource(R.string.github_actions_history_label_checked),
                value = checkedAt,
                valueMaxLines = 1,
                valueOverflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun rememberStatusValue(record: GitHubActionsNotificationHistoryRecord): String {
    val status = record.status.ifBlank { stringResource(R.string.common_na) }
    val conclusion = record.conclusion.ifBlank { stringResource(R.string.common_na) }
    return if (record.conclusion.isBlank()) {
        status
    } else {
        stringResource(R.string.github_actions_history_status_value, status, conclusion)
    }
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

private val ColorSuccess = androidx.compose.ui.graphics.Color(0xFF22C55E)
