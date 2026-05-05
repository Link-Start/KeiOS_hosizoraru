package os.kei.ui.page.main.github.section

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.ui.page.main.github.GitHubOverviewMetricItem
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.OverviewRefreshState
import os.kei.ui.page.main.github.borderColor
import os.kei.ui.page.main.github.color
import os.kei.ui.page.main.github.formatRefreshAgo
import os.kei.ui.page.main.github.indicatorBackground
import os.kei.ui.page.main.github.overviewApiLabel
import os.kei.ui.page.main.github.overviewLabel
import os.kei.ui.page.main.github.surfaceColor
import os.kei.ui.page.main.widget.core.AppOverviewCard
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.motion.AppMotionTokens
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.status.StatusPill
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
    val failedCount: Int
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
    onOpenTrackSheetForAdd: () -> Unit
) {
    val context = LocalContext.current
    val overviewTitleColor = if (isDark) Color.White else MiuixTheme.colorScheme.onBackgroundVariant
    var expanded by rememberSaveable { mutableStateOf(true) }
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val strategyValue = lookupConfig.selectedStrategy.overviewLabel(context)
    val apiValue = lookupConfig.overviewApiLabel(context)
    val strategyColor = lookupConfig.selectedStrategy.run {
        when (this) {
            GitHubLookupStrategyOption.AtomFeed -> GitHubStatusPalette.Active
            GitHubLookupStrategyOption.GitHubApiToken -> GitHubStatusPalette.Update
        }
    }
    val apiColor = if (lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken) {
        if (lookupConfig.apiToken.isBlank()) {
            GitHubStatusPalette.PreRelease
        } else {
            GitHubStatusPalette.Active
        }
    } else {
        overviewMetricColor(
            color = GitHubStatusPalette.Active,
            emphasized = false,
            isDark = isDark
        )
    }
    val displayRefreshState = if (
        overviewRefreshState == OverviewRefreshState.Idle && lastRefreshMs > 0L
    ) {
        OverviewRefreshState.Cached
    } else {
        overviewRefreshState
    }
    AppOverviewCard(
        title = stringResource(R.string.github_overview_title),
        backdrop = backdrop,
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
        onClick = { expanded = !expanded },
        onLongClick = onOpenTrackSheetForAdd,
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
                    size = 18.dp,
                    strokeWidth = 2.dp,
                    activeColor = indicatorColor,
                    inactiveColor = indicatorBg
                )
            }
            StatusPill(
                label = formatRefreshAgo(context = context, lastRefreshMs = lastRefreshMs),
                color = displayRefreshState.color(
                    neutralColor = MiuixTheme.colorScheme.onBackgroundVariant
                ),
                backgroundAlphaOverride = if (isDark) 0.18f else 0.24f,
                borderAlphaOverride = if (isDark) 0.35f else 0.42f,
                backdrop = backdrop
            )
            StatusPill(
                label = when (displayRefreshState) {
                    OverviewRefreshState.Cached -> stringResource(R.string.common_status_cached)
                    OverviewRefreshState.Refreshing -> stringResource(R.string.common_status_checking)
                    OverviewRefreshState.Completed -> stringResource(R.string.common_status_checked)
                    OverviewRefreshState.Failed -> stringResource(R.string.common_status_failed)
                    OverviewRefreshState.Idle -> stringResource(R.string.common_status_pending_check)
                },
                color = displayRefreshState.color(
                    neutralColor = MiuixTheme.colorScheme.onBackgroundVariant
                ),
                backdrop = backdrop
            )
        }
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                if (transitionAnimationsEnabled) {
                    (
                            fadeIn(animationSpec = tween(durationMillis = AppMotionTokens.expandFadeInMs)) togetherWith
                                    fadeOut(animationSpec = tween(durationMillis = AppMotionTokens.expandFadeOutMs))
                            ).using(
                            SizeTransform(clip = false) { _, _ ->
                                tween(durationMillis = AppMotionTokens.expandSizeInMs)
                            }
                        )
                } else {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "github_overview_expand_content",
            modifier = Modifier.fillMaxWidth()
        ) {
            if (it) {
                GitHubOverviewExpandedContent(
                    backdrop = backdrop,
                    isDark = isDark,
                    overviewTitleColor = overviewTitleColor,
                    strategyValue = strategyValue,
                    strategyColor = strategyColor,
                    apiValue = apiValue,
                    apiColor = apiColor,
                    metrics = metrics
                )
            } else {
                GitHubOverviewCollapsedContent(
                    backdrop = backdrop,
                    isDark = isDark,
                    overviewTitleColor = overviewTitleColor,
                    isApiStrategy = lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken,
                    strategyValue = strategyValue,
                    strategyColor = strategyColor,
                    apiValue = apiValue,
                    apiColor = apiColor,
                    metrics = metrics
                )
            }
        }
    }
}

@Composable
private fun GitHubOverviewExpandedContent(
    backdrop: Backdrop?,
    isDark: Boolean,
    overviewTitleColor: Color,
    strategyValue: String,
    strategyColor: Color,
    apiValue: String,
    apiColor: Color,
    metrics: GitHubOverviewMetrics,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.metricRowGap)
        ) {
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_overview_label_strategy),
                value = strategyValue,
                titleColor = overviewTitleColor,
                valueColor = strategyColor,
                backdrop = backdrop,
                modifier = Modifier.weight(1f)
            )
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_overview_label_api),
                value = apiValue,
                titleColor = overviewTitleColor,
                valueColor = apiColor,
                labelMaxLines = 1,
                valueMaxLines = 1,
                labelWeight = 0.24f,
                valueWeight = 0.76f,
                backdrop = backdrop,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.metricRowGap)
        ) {
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_overview_label_tracked),
                value = stringResource(R.string.github_overview_value_count, metrics.trackedCount),
                titleColor = overviewTitleColor,
                valueColor = overviewMetricColor(
                    color = GitHubStatusPalette.Stable,
                    emphasized = metrics.trackedCount > 0,
                    isDark = isDark
                ),
                backdrop = backdrop,
                modifier = Modifier.weight(1f)
            )
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_overview_label_stable_update),
                value = stringResource(
                    R.string.github_overview_value_count,
                    metrics.stableUpdateCount
                ),
                titleColor = overviewTitleColor,
                valueColor = overviewMetricColor(
                    color = GitHubStatusPalette.Update,
                    emphasized = metrics.stableUpdateCount > 0,
                    isDark = isDark
                ),
                backdrop = backdrop,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.metricRowGap)
        ) {
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_overview_label_stable_latest),
                value = stringResource(
                    R.string.github_overview_value_count,
                    metrics.stableLatestCount
                ),
                titleColor = overviewTitleColor,
                valueColor = overviewMetricColor(
                    color = GitHubStatusPalette.Stable,
                    emphasized = metrics.stableLatestCount > 0,
                    isDark = isDark
                ),
                backdrop = backdrop,
                modifier = Modifier.weight(1f)
            )
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_overview_label_prerelease_tracked),
                value = stringResource(
                    R.string.github_overview_value_count,
                    metrics.preReleaseCount
                ),
                titleColor = overviewTitleColor,
                valueColor = overviewMetricColor(
                    color = GitHubStatusPalette.PreRelease,
                    emphasized = metrics.preReleaseCount > 0,
                    isDark = isDark
                ),
                backdrop = backdrop,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.metricRowGap)
        ) {
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_overview_label_prerelease_update),
                value = stringResource(
                    R.string.github_overview_value_count,
                    metrics.preReleaseUpdateCount
                ),
                titleColor = overviewTitleColor,
                valueColor = overviewMetricColor(
                    color = GitHubStatusPalette.PreRelease,
                    emphasized = metrics.preReleaseUpdateCount > 0,
                    isDark = isDark
                ),
                backdrop = backdrop,
                modifier = Modifier.weight(1f)
            )
            GitHubOverviewMetricItem(
                label = stringResource(R.string.github_overview_label_check_failed),
                value = stringResource(R.string.github_overview_value_count, metrics.failedCount),
                titleColor = overviewTitleColor,
                valueColor = overviewMetricColor(
                    color = GitHubStatusPalette.Error,
                    emphasized = metrics.failedCount > 0,
                    isDark = isDark
                ),
                backdrop = backdrop,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GitHubOverviewCollapsedContent(
    backdrop: Backdrop?,
    isDark: Boolean,
    overviewTitleColor: Color,
    isApiStrategy: Boolean,
    strategyValue: String,
    strategyColor: Color,
    apiValue: String,
    apiColor: Color,
    metrics: GitHubOverviewMetrics,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.metricRowGap)
    ) {
        GitHubOverviewMetricItem(
            label = stringResource(R.string.github_overview_label_strategy_api),
            value = if (isApiStrategy) {
                stringResource(R.string.github_overview_value_api, apiValue)
            } else {
                stringResource(
                    R.string.github_overview_value_strategy_api,
                    strategyValue,
                    apiValue
                )
            },
            titleColor = overviewTitleColor,
            valueColor = if (apiColor == GitHubStatusPalette.PreRelease) apiColor else strategyColor,
            showLabel = false,
            valueMaxLines = 1,
            valueTextAlign = TextAlign.Start,
            backdrop = backdrop,
            modifier = Modifier.weight(1f)
        )
        GitHubOverviewMetricItem(
            label = stringResource(R.string.github_overview_label_update_tracked),
            value = stringResource(
                R.string.github_overview_value_update_tracked_compact,
                metrics.totalUpdatableCount,
                metrics.trackedCount
            ),
            titleColor = overviewTitleColor,
            valueColor = overviewMetricColor(
                color = GitHubStatusPalette.Update,
                emphasized = metrics.totalUpdatableCount > 0,
                isDark = isDark
            ),
            emphasized = metrics.totalUpdatableCount > 0,
            showLabel = false,
            valueMaxLines = 1,
            valueTextAlign = TextAlign.Start,
            backdrop = backdrop,
            modifier = Modifier.weight(1f)
        )
    }
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
                failedCount = 1
            ),
            onOpenTrackSheetForAdd = {}
        )
    }
}
