package os.kei.ui.page.main.github.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.OverviewRefreshState
import os.kei.ui.page.main.github.borderColor
import os.kei.ui.page.main.github.color
import os.kei.ui.page.main.github.formatRefreshAgo
import os.kei.ui.page.main.github.indicatorBackground
import os.kei.ui.page.main.github.overviewLookupPillLabel
import os.kei.ui.page.main.github.surfaceColor
import os.kei.ui.page.main.widget.core.AppOverviewCard
import os.kei.ui.page.main.widget.core.AppOverviewPill
import os.kei.ui.page.main.widget.core.AppOverviewPillFlow
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

internal data class GitHubOverviewMetrics(
    val trackedCount: Int,
    val stableUpdateCount: Int,
    val totalUpdatableCount: Int,
    val stableLatestCount: Int,
    val preReleaseCount: Int,
    val preReleaseUpdateCount: Int,
    val failedCount: Int,
    val oldestCheckedAtMillis: Long = 0L,
    val latestCheckedAtMillis: Long = 0L
)

private fun overviewMetricColor(
    color: Color,
    emphasized: Boolean,
    isDark: Boolean
): Color {
    return if (emphasized) {
        color
    } else {
        color.copy(alpha = if (isDark) 0.76f else 0.84f)
    }
}

@Composable
internal fun GitHubOverviewCard(
    backdrop: Backdrop? = null,
    isDark: Boolean,
    lookupConfig: GitHubLookupConfig,
    overviewRefreshState: OverviewRefreshState,
    refreshProgress: Float,
    lastRefreshMs: Long,
    metrics: GitHubOverviewMetrics,
    failedFilterActive: Boolean,
    onRetryFailedTracked: () -> Unit,
    onFailedFilterToggle: (Boolean) -> Unit,
    onAddTracked: () -> Unit
) {
    val context = LocalContext.current
    val lookupValue = lookupConfig.overviewLookupPillLabel(context)
    val lookupColor =
        when {
            lookupConfig.selectedStrategy != GitHubLookupStrategyOption.GitHubApiToken ->
                GitHubStatusPalette.Active
            lookupConfig.apiToken.isBlank() ->
                GitHubStatusPalette.PreRelease
            else ->
                GitHubStatusPalette.Active
        }
    val displayRefreshState = if (
        overviewRefreshState == OverviewRefreshState.Idle && lastRefreshMs > 0L
    ) {
        OverviewRefreshState.Cached
    } else {
        overviewRefreshState
    }
    AppOverviewCard(
        modifier = Modifier.testTag(KeiOsTestTags.GitHubAddTrackedButton),
        title = stringResource(R.string.github_overview_title),
        backdrop = backdrop,
        // Adding a tracked repo used to be a dock button. The dock is the scarcer
        // surface, and this card had no click of its own to give up.
        onClick = onAddTracked,
        titleColor = MiuixTheme.colorScheme.onBackground,
        subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant,
        containerColor = displayRefreshState.surfaceColor(
            isDark = isDark,
            neutralSurface = MiuixTheme.colorScheme.surface
        ),
        borderColor = displayRefreshState.borderColor(
            isDark = isDark,
            neutralColor = MiuixTheme.colorScheme.onBackgroundVariant
        ),
        contentColor = MiuixTheme.colorScheme.onBackground,
        titleAccessory = {
            GitHubOverviewLookupModePill(
                label = lookupValue,
                color = lookupColor,
            )
        },
        headerEndActions = {
            if (displayRefreshState != OverviewRefreshState.Idle) {
                val indicatorColor = displayRefreshState.color(
                    neutralColor = MiuixTheme.colorScheme.onBackgroundVariant
                )
                val indicatorBg = displayRefreshState.indicatorBackground(
                    neutralSurface = MiuixTheme.colorScheme.surface
                )
                val progressValue = when (displayRefreshState) {
                    OverviewRefreshState.Refreshing -> refreshProgress.coerceIn(0f, 1f)
                    OverviewRefreshState.Completed,
                    OverviewRefreshState.Failed,
                    OverviewRefreshState.Cached -> 1f
                    OverviewRefreshState.Idle -> 0f
                }
                LiquidCircularProgressBar(
                    progress = { progressValue },
                    modifier =
                        if (displayRefreshState == OverviewRefreshState.Refreshing) {
                            Modifier.testTag(KeiOsTestTags.GitHubOverviewRefreshing)
                        } else {
                            Modifier
                        },
                    size = 18.dp,
                    strokeWidth = 2.dp,
                    activeColor = indicatorColor,
                    inactiveColor = indicatorBg
                )
            }
            StatusPill(
                label = formatRefreshAgo(
                    context = context,
                    lastRefreshMs = metrics.latestCheckedAtMillis,
                ),
                color = GitHubStatusPalette.Cache,
                modifier = Modifier.semantics {
                    contentDescription = context.getString(
                        R.string.github_overview_incremental_refresh_time,
                        formatRefreshAgo(
                            context = context,
                            lastRefreshMs = metrics.latestCheckedAtMillis,
                        ),
                    )
                },
                backgroundAlphaOverride = if (isDark) 0.18f else 0.24f,
                borderAlphaOverride = if (isDark) 0.35f else 0.42f,
            )
            StatusPill(
                label = formatRefreshAgo(context = context, lastRefreshMs = lastRefreshMs),
                color = GitHubStatusPalette.Update,
                modifier = Modifier.semantics {
                    contentDescription = context.getString(
                        R.string.github_overview_full_refresh_time,
                        formatRefreshAgo(context = context, lastRefreshMs = lastRefreshMs),
                    )
                },
                backgroundAlphaOverride = if (isDark) 0.18f else 0.24f,
                borderAlphaOverride = if (isDark) 0.35f else 0.42f,
            )
        }
    ) {
        GitHubOverviewExpandedContent(
            isDark = isDark,
            metrics = metrics,
            failedFilterActive = failedFilterActive,
            onRetryFailedTracked = onRetryFailedTracked,
            onFailedFilterToggle = onFailedFilterToggle
        )
    }
}

@Composable
private fun GitHubOverviewExpandedContent(
    isDark: Boolean,
    metrics: GitHubOverviewMetrics,
    failedFilterActive: Boolean,
    onRetryFailedTracked: () -> Unit,
    onFailedFilterToggle: (Boolean) -> Unit
) {
    val metricPills =
        buildGitHubOverviewExpandedPillPlan().map { pill ->
            pill.toDisplayPill(
                isDark = isDark,
                metrics = metrics
            )
        }
    val pills =
        listOf(
            AppOverviewPill(
                label = metrics.trackedCount.toString(),
                color = overviewMetricColor(
                    color = GitHubStatusPalette.Stable,
                    emphasized = metrics.trackedCount > 0,
                    isDark = isDark,
                ),
            )
        ) + metricPills
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap)
    ) {
        AppOverviewPillFlow(
            pills = pills,
            batchLiquidBackdrop = true,
        )
        if (metrics.failedCount > 0) {
            if (failedFilterActive) {
                StatusPill(
                    label = stringResource(R.string.github_overview_failed_filter_active),
                    color = GitHubStatusPalette.Error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.controlRowGap)
            ) {
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(
                        if (failedFilterActive) {
                            R.string.github_overview_action_clear_failed_filter
                        } else {
                            R.string.github_overview_action_show_failed
                        }
                    ),
                    onClick = { onFailedFilterToggle(!failedFilterActive) },
                    containerColor = GitHubStatusPalette.Error,
                    variant = GlassVariant.SheetDangerAction
                )
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.github_overview_action_retry_failed),
                    onClick = onRetryFailedTracked,
                    containerColor = GitHubStatusPalette.Update
                )
            }
        }
    }
}

internal enum class GitHubOverviewExpandedPillKind {
    Stable,
    PreRelease,
    CheckFailed
}

internal data class GitHubOverviewExpandedPillPlan(
    val kind: GitHubOverviewExpandedPillKind
)

internal fun buildGitHubOverviewExpandedPillPlan(): List<GitHubOverviewExpandedPillPlan> =
    listOf(
        GitHubOverviewExpandedPillPlan(GitHubOverviewExpandedPillKind.Stable),
        GitHubOverviewExpandedPillPlan(GitHubOverviewExpandedPillKind.PreRelease),
        GitHubOverviewExpandedPillPlan(GitHubOverviewExpandedPillKind.CheckFailed),
    )

@Composable
private fun GitHubOverviewExpandedPillPlan.toDisplayPill(
    isDark: Boolean,
    metrics: GitHubOverviewMetrics
): AppOverviewPill {
    val stableTotal = metrics.stableUpdateCount + metrics.stableLatestCount
    val label = when (kind) {
        GitHubOverviewExpandedPillKind.Stable ->
            stringResource(
                R.string.github_overview_pill_stable_pair,
                metrics.stableUpdateCount,
                stableTotal
            )

        GitHubOverviewExpandedPillKind.PreRelease ->
            stringResource(
                R.string.github_overview_pill_prerelease_pair,
                metrics.preReleaseUpdateCount,
                metrics.preReleaseCount
            )

        GitHubOverviewExpandedPillKind.CheckFailed ->
            stringResource(R.string.github_overview_pill_failed, metrics.failedCount)
    }
    val color = when (kind) {
        GitHubOverviewExpandedPillKind.Stable ->
            overviewMetricColor(
                color = GitHubStatusPalette.Update,
                emphasized = metrics.stableUpdateCount > 0,
                isDark = isDark
            )

        GitHubOverviewExpandedPillKind.PreRelease ->
            overviewMetricColor(
                color = GitHubStatusPalette.PreRelease,
                emphasized = metrics.preReleaseCount > 0 || metrics.preReleaseUpdateCount > 0,
                isDark = isDark
            )

        GitHubOverviewExpandedPillKind.CheckFailed ->
            overviewMetricColor(
                color = GitHubStatusPalette.Error,
                emphasized = metrics.failedCount > 0,
                isDark = isDark
            )
    }
    return AppOverviewPill(label = label, color = color)
}

@Composable
private fun GitHubOverviewLookupModePill(
    label: String,
    color: Color,
) {
    StatusPill(
        label = label,
        color = color,
        size = AppStatusPillSize.Compact,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Preview(name = "GitHub Overview Light", showBackground = true, backgroundColor = 0xFFF3F4F6)
@Composable
private fun GitHubOverviewCardPreview() {
    MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
        GitHubOverviewCard(
            isDark = false,
            lookupConfig = GitHubLookupConfig(
                selectedStrategy = GitHubLookupStrategyOption.GitHubApiToken,
                apiToken = "github_pat_preview_token"
            ),
            overviewRefreshState = OverviewRefreshState.Completed,
            refreshProgress = 1f,
            lastRefreshMs = System.currentTimeMillis() - 180_000L,
            metrics = GitHubOverviewMetrics(
                trackedCount = 18,
                stableUpdateCount = 4,
                totalUpdatableCount = 6,
                stableLatestCount = 11,
                preReleaseCount = 3,
                preReleaseUpdateCount = 2,
                failedCount = 1,
                oldestCheckedAtMillis = System.currentTimeMillis() - 7_200_000L,
                latestCheckedAtMillis = System.currentTimeMillis() - 180_000L
            ),
            failedFilterActive = false,
            onRetryFailedTracked = {},
            onFailedFilterToggle = {},
            onAddTracked = {}
        )
    }
}
