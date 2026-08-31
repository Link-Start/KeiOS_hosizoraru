@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.GitHubRefreshHistoryFailureSummary
import os.kei.feature.github.model.GitHubRefreshHistoryRecord
import os.kei.ui.page.main.widget.core.AppInfoRow
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GitHubRefreshHistoryDiagnosticPills(record: GitHubRefreshHistoryRecord) {
    if (!record.hasRefreshTraceDiagnostics()) return
    val warning = Color(0xFFF59E0B)
    val slowestElapsedMs = record.slowItems.maxOfOrNull { it.elapsedMs }.orZero()
    val showSlowDiagnostics =
        slowestElapsedMs >= REFRESH_HISTORY_SLOW_PILL_VISIBLE_MS ||
            (record.failedCount > 0 && record.slowItems.isNotEmpty())
    val pills =
        buildList {
            if (record.failedCount > 0) {
                add(
                    RefreshDiagnosticPill(
                        label = stringResource(R.string.github_history_refresh_pill_failed_count, record.failedCount),
                        color = MiuixTheme.colorScheme.error,
                    ),
                )
            }
            if (record.failureSummaries.isNotEmpty()) {
                add(
                    RefreshDiagnosticPill(
                        label =
                            stringResource(
                                R.string.github_history_refresh_pill_failure_detail_count,
                                record.failureSummaries.size,
                            ),
                        color = MiuixTheme.colorScheme.error,
                    ),
                )
            }
            if (showSlowDiagnostics) {
                add(
                    RefreshDiagnosticPill(
                        label = stringResource(R.string.github_history_refresh_pill_slow_count, record.slowItems.size),
                        color = warning,
                    ),
                )
            }
            if (showSlowDiagnostics && slowestElapsedMs > 0L) {
                add(
                    RefreshDiagnosticPill(
                        label =
                            stringResource(
                                R.string.github_history_refresh_pill_slowest,
                                rememberDurationLabel(slowestElapsedMs),
                            ),
                        color = warning,
                    ),
                )
            }
            if (record.schedulerStopReason.isNotBlank()) {
                add(
                    RefreshDiagnosticPill(
                        label =
                            stringResource(
                                R.string.github_history_refresh_pill_stop_reason,
                                record.schedulerStopReason,
                            ),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    ),
                )
            }
            if (record.schedulerRescheduled) {
                add(
                    RefreshDiagnosticPill(
                        label = stringResource(R.string.github_history_refresh_pill_rescheduled),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    ),
                )
            }
        }
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag(KeiOsTestTags.GitHubRefreshHistoryDiagnostics),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        pills.forEach { pill ->
            StatusPill(
                label = pill.label,
                color = pill.color,
                size = AppStatusPillSize.Compact,
                backgroundAlphaOverride = 0.14f,
                borderAlphaOverride = 0.24f,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GitHubRefreshFailureSummaryBlock(
    index: Int,
    failure: GitHubRefreshHistoryFailureSummary,
) {
    val displayName = rememberFailureDisplayName(failure)
    val repo = buildFailureRepoIdentity(failure)
    val detail = rememberRefreshMessageDetail(failure.message).ifBlank { failure.message.trim() }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = CardLayoutRhythm.infoRowVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.controlRowTextGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.infoRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.github_history_refresh_label_failure_index, index + 1),
                color = MiuixTheme.colorScheme.error,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = displayName,
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = AppTypographyTokens.Body.fontWeight,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(
                label = rememberRefreshSourceModeLabel(failure.sourceMode),
                color = MiuixTheme.colorScheme.primary,
                size = AppStatusPillSize.Compact,
                backgroundAlphaOverride = 0.14f,
                borderAlphaOverride = 0.24f,
            )
            if (failure.failureCategory.isNotBlank()) {
                StatusPill(
                    label = rememberFailureCategoryLabel(failure.failureCategory),
                    color = MiuixTheme.colorScheme.error,
                    size = AppStatusPillSize.Compact,
                    backgroundAlphaOverride = 0.14f,
                    borderAlphaOverride = 0.24f,
                )
            }
            if (failure.elapsedMs > 0L) {
                StatusPill(
                    label = rememberDurationLabel(failure.elapsedMs),
                    color = Color(0xFFF59E0B),
                    size = AppStatusPillSize.Compact,
                    backgroundAlphaOverride = 0.14f,
                    borderAlphaOverride = 0.24f,
                )
            }
        }
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                color = MiuixTheme.colorScheme.error,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
            )
        }
        if (repo.isNotBlank()) {
            AppInfoRow(
                label = stringResource(R.string.github_history_refresh_label_failure_repo),
                value = repo,
                rowVerticalPadding = 0.dp,
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
        }
        if (failure.responseType.isNotBlank()) {
            AppInfoRow(
                label = stringResource(R.string.github_history_refresh_label_failure_response_type),
                value = failure.responseType,
                rowVerticalPadding = 0.dp,
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
        }
        if (failure.limitBytes >= 0L) {
            AppInfoRow(
                label = stringResource(R.string.github_history_refresh_label_failure_size_limit),
                value = rememberFailureLimitDetail(failure),
                stacked = true,
                rowVerticalPadding = 0.dp,
                valueMaxLines = 3,
                valueOverflow = TextOverflow.Ellipsis,
            )
        }
        if (failure.packageName.isNotBlank()) {
            AppInfoRow(
                label = stringResource(R.string.github_history_refresh_label_failure_package),
                value = failure.packageName,
                rowVerticalPadding = 0.dp,
                valueMaxLines = 2,
                valueOverflow = TextOverflow.Ellipsis,
            )
        }
        if (failure.trackId.isNotBlank()) {
            AppInfoRow(
                label = stringResource(R.string.github_history_refresh_label_failure_track_id),
                value = failure.trackId,
                stacked = true,
                rowVerticalPadding = 0.dp,
                valueMaxLines = 3,
                valueOverflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class RefreshDiagnosticPill(
    val label: String,
    val color: Color,
)

@Composable
private fun rememberFailureDisplayName(failure: GitHubRefreshHistoryFailureSummary): String =
    failure.appLabel
        .ifBlank { buildFailureRepoIdentity(failure) }
        .ifBlank { failure.packageName }
        .ifBlank { failure.trackId }

private fun buildFailureRepoIdentity(failure: GitHubRefreshHistoryFailureSummary): String =
    listOf(failure.owner, failure.repo)
        .filter { it.isNotBlank() }
        .joinToString("/")

@Composable
private fun rememberFailureCategoryLabel(category: String): String =
    stringResource(
        when (category.trim().lowercase()) {
            "response_too_large" -> R.string.github_history_refresh_failure_category_response_too_large
            "timeout" -> R.string.github_history_refresh_failure_category_timeout
            "rate_limited" -> R.string.github_history_refresh_failure_category_rate_limited
            "http_error" -> R.string.github_history_refresh_failure_category_http
            "network_error" -> R.string.github_history_refresh_failure_category_network
            "parse_error" -> R.string.github_history_refresh_failure_category_parse
            "cancelled" -> R.string.github_history_refresh_failure_category_cancelled
            else -> R.string.github_history_refresh_failure_category_unknown
        },
    )

@Composable
private fun rememberFailureLimitDetail(failure: GitHubRefreshHistoryFailureSummary): String {
    val stage =
        when (failure.limitStage) {
            "DeclaredLength" -> stringResource(R.string.github_history_refresh_failure_stage_declared)
            "Streaming" -> stringResource(R.string.github_history_refresh_failure_stage_streaming)
            else -> failure.limitStage
        }
    return buildList {
        add(
            stringResource(
                R.string.github_history_refresh_failure_limit_value,
                formatDiagnosticBytes(failure.limitBytes),
            ),
        )
        failure.declaredBytes.takeIf { it >= 0L }?.let { bytes ->
            add(stringResource(R.string.github_history_refresh_failure_declared_value, formatDiagnosticBytes(bytes)))
        }
        failure.observedBytes.takeIf { it >= 0L }?.let { bytes ->
            add(stringResource(R.string.github_history_refresh_failure_observed_value, formatDiagnosticBytes(bytes)))
        }
        stage.takeIf { it.isNotBlank() }?.let { value ->
            add(stringResource(R.string.github_history_refresh_failure_stage_value, value))
        }
    }.joinToString(" · ")
}

private fun formatDiagnosticBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val mib = 1024L * 1024L
    val kib = 1024L
    return when {
        safeBytes >= mib -> "${safeBytes / mib} MiB"
        safeBytes >= kib -> "${safeBytes / kib} KiB"
        else -> "$safeBytes B"
    }
}

private fun GitHubRefreshHistoryRecord.hasRefreshTraceDiagnostics(): Boolean =
    failedCount > 0 ||
        failureSummaries.isNotEmpty() ||
        slowItems.any { it.elapsedMs >= REFRESH_HISTORY_SLOW_PILL_VISIBLE_MS } ||
        schedulerStopReason.isNotBlank() ||
        schedulerRescheduled

private fun Long?.orZero(): Long = this ?: 0L

private const val REFRESH_HISTORY_SLOW_PILL_VISIBLE_MS = 5_000L
