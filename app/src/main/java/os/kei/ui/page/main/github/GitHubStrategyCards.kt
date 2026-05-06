package os.kei.ui.page.main.github

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.GitHubActionsLookupStrategyOption
import os.kei.feature.github.model.GitHubApiAuthMode
import os.kei.feature.github.model.GitHubApiCredentialStatus
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubStrategyBenchmarkReport
import os.kei.feature.github.model.GitHubStrategyBenchmarkResult
import os.kei.feature.github.model.GitHubStrategyBenchmarkSample
import os.kei.feature.github.model.GitHubStrategyBenchmarkTestType
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.sheet.SheetChoiceCard
import os.kei.ui.page.main.widget.sheet.SheetExpandableCard
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val BENCHMARK_SAMPLE_DETAIL_LIMIT = 12

@Composable
internal fun GitHubStrategyGuideCard(
    guide: GitHubStrategyGuide,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val accent = guide.option.accentColor()
    SheetChoiceCard(
        title = guide.option.label,
        summary = guide.summary,
        selected = selected,
        onSelect = onSelect,
        accentColor = accent,
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        details = {
            Text(
                text = stringResource(R.string.github_strategy_guide_pros, guide.pros.joinToString(" · ")),
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight
            )
            Text(
                text = stringResource(R.string.github_strategy_guide_cons, guide.cons.joinToString(" · ")),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight
            )
            Text(
                text = stringResource(R.string.github_strategy_guide_requirement, guide.requirement),
                color = accent,
                fontWeight = FontWeight.Medium,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight
            )
        }
    )
}

@Composable
internal fun GitHubActionsStrategyGuideCard(
    guide: GitHubActionsStrategyGuide,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val accent = guide.option.accentColor()
    SheetChoiceCard(
        title = guide.option.label,
        summary = guide.summary,
        selected = selected,
        onSelect = onSelect,
        accentColor = accent,
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        details = {
            Text(
                text = stringResource(R.string.github_strategy_guide_pros, guide.pros.joinToString(" · ")),
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight
            )
            Text(
                text = stringResource(R.string.github_strategy_guide_cons, guide.cons.joinToString(" · ")),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight
            )
            Text(
                text = stringResource(R.string.github_strategy_guide_requirement, guide.requirement),
                color = accent,
                fontWeight = FontWeight.Medium,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight
            )
        }
    )
}

@Composable
internal fun GitHubStrategyDraftSummaryCard(
    selectedStrategy: GitHubLookupStrategyOption,
    selectedActionsStrategy: GitHubActionsLookupStrategyOption,
    tokenInput: String,
    trackedCount: Int,
    changed: Boolean
) {
    val accent = selectedStrategy.accentColor()
    val tokenUsed = selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken ||
        selectedActionsStrategy == GitHubActionsLookupStrategyOption.GitHubApiToken
    val tokenStatusLabel = when {
        !tokenUsed -> stringResource(R.string.common_not_used)
        tokenInput.isNotBlank() -> stringResource(R.string.common_filled)
        else -> stringResource(R.string.common_guest)
    }
    val tokenStatusColor = when {
        !tokenUsed -> MiuixTheme.colorScheme.onBackgroundVariant
        tokenInput.isNotBlank() -> GitHubStatusPalette.Update
        else -> GitHubStatusPalette.PreRelease
    }

    SheetSummaryCard(
        title = stringResource(R.string.github_strategy_card_title_draft),
        accentColor = MiuixTheme.colorScheme.onBackground,
        badgeLabel = if (changed) {
            stringResource(R.string.common_pending_save)
        } else {
            stringResource(R.string.github_strategy_badge_same)
        },
        badgeColor = if (changed) accent else MiuixTheme.colorScheme.onBackgroundVariant,
        containerColor = if (changed) {
            MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f)
        } else {
            null
        },
        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
    ) {
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_release_option),
            value = selectedStrategy.label,
            valueColor = accent,
            emphasized = true,
            titleMinWidth = 44.dp
        )
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_actions_option),
            value = selectedActionsStrategy.label,
            valueColor = selectedActionsStrategy.accentColor(),
            emphasized = true,
            titleMinWidth = 44.dp
        )
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_token),
            value = tokenStatusLabel,
            valueColor = tokenStatusColor,
            emphasized = tokenUsed,
            titleMinWidth = 44.dp
        )
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_impact),
            value = if (trackedCount > 0) {
                stringResource(R.string.github_strategy_impact_recheck_count, trackedCount)
            } else {
                stringResource(R.string.github_strategy_impact_no_track)
            },
            valueColor = MiuixTheme.colorScheme.onBackgroundVariant,
            titleMinWidth = 44.dp
        )
    }
}

@Composable
internal fun GitHubRecommendedTokenGuideCard(
    guide: GitHubRecommendedTokenGuide,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    SheetExpandableCard(
        title = stringResource(R.string.github_strategy_card_title_recommended),
        collapsedSummary = guide.collapsedSummary,
        expandedSummary = guide.summary,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        accentColor = GitHubStatusPalette.Update,
        badgeLabel = stringResource(R.string.github_strategy_badge_least_privilege),
        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
    ) {
        val accent = GitHubStatusPalette.Update
        guide.fields.forEach { field ->
            GitHubCompactInfoRow(
                label = field.label,
                value = field.value,
                valueColor = if (field.emphasized) accent else MiuixTheme.colorScheme.onBackground,
                emphasized = field.emphasized,
                titleMinWidth = 52.dp
            )
        }
        guide.notes.forEach { note ->
            Text(
                text = note,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun GitHubStrategyBenchmarkComparisonCard(
    report: GitHubStrategyBenchmarkReport
) {
    val rows = remember(report.results) { buildBenchmarkComparisonRows(report.results) }
    if (rows.isEmpty()) return
    val clearRows = rows.count { it.level == BenchmarkComparisonLevel.Clear }
    SheetSummaryCard(
        title = stringResource(R.string.github_strategy_benchmark_compare_title),
        accentColor = GitHubStatusPalette.Update,
        badgeLabel = stringResource(
            R.string.github_strategy_benchmark_compare_badge,
            clearRows,
            rows.size
        ),
        badgeColor = if (clearRows == rows.size) {
            GitHubStatusPalette.Update
        } else {
            GitHubStatusPalette.PreRelease
        },
        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            GitHubBenchmarkComparisonLine(row)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun GitHubBenchmarkComparisonLine(row: BenchmarkComparisonRow) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = row.testType.label(),
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        FlowRow(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusPill(
                label = row.winnerLabel,
                color = when (row.level) {
                    BenchmarkComparisonLevel.Clear -> GitHubStatusPalette.Update
                    BenchmarkComparisonLevel.Close -> GitHubStatusPalette.Cache
                    BenchmarkComparisonLevel.Insufficient -> GitHubStatusPalette.PreRelease
                }
            )
            row.metrics.forEach { metric ->
                StatusPill(label = metric, color = GitHubStatusPalette.Active)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun GitHubStrategyBenchmarkCard(
    result: GitHubStrategyBenchmarkResult
) {
    var samplesExpanded by remember(
        result.strategyId,
        result.authMode,
        result.coldSamples,
        result.warmSamples
    ) {
        mutableStateOf(false)
    }
    val accent = when (result.displayName) {
        "API" -> GitHubStatusPalette.Update
        else -> GitHubStatusPalette.Active
    }
    SheetSummaryCard(
        title = result.localizedSummaryLabel(),
        accentColor = accent,
        badgeLabel = "${result.coldSuccessCount}/${result.totalTargets}",
        badgeColor = if (result.failures.isEmpty()) GitHubStatusPalette.Update else GitHubStatusPalette.PreRelease,
        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
    ) {
        GitHubOverviewMetricItem(
            label = stringResource(R.string.github_strategy_metric_cold_avg),
            value = "${result.coldAverageMs} ms",
            valueColor = accent
        )
        GitHubOverviewMetricItem(
            label = stringResource(R.string.github_strategy_metric_warm_avg),
            value = "${result.warmAverageMs} ms",
            valueColor = GitHubStatusPalette.Stable
        )
        GitHubOverviewMetricItem(
            label = stringResource(R.string.github_strategy_metric_cache_hit),
            value = "${result.cacheHitCount}/${result.warmSamples.size}",
            valueColor = if (result.cacheHitCount == result.warmSamples.size) {
                GitHubStatusPalette.Update
            } else {
                GitHubStatusPalette.PreRelease
            }
        )
        GitHubOverviewMetricItem(
            label = stringResource(R.string.github_strategy_metric_failed),
            value = "${result.failures.size}",
            valueColor = if (result.failures.isEmpty()) {
                MiuixTheme.colorScheme.onBackgroundVariant
            } else {
                GitHubStatusPalette.Error
            }
        )
        GitHubBenchmarkTaskRow(
            label = stringResource(R.string.github_strategy_metric_release_snapshot),
            result = result,
            testType = GitHubStrategyBenchmarkTestType.ReleaseSnapshot,
            valueColor = accent
        )
        GitHubBenchmarkTaskRow(
            label = stringResource(R.string.github_strategy_metric_release_assets),
            result = result,
            testType = GitHubStrategyBenchmarkTestType.ReleaseAssets,
            valueColor = GitHubStatusPalette.Active
        )
        GitHubBenchmarkTaskRow(
            label = stringResource(R.string.github_strategy_metric_release_notes),
            result = result,
            testType = GitHubStrategyBenchmarkTestType.ReleaseNotes,
            valueColor = GitHubStatusPalette.Stable
        )
        GitHubBenchmarkTaskRow(
            label = stringResource(R.string.github_strategy_metric_apk_manifest),
            result = result,
            testType = GitHubStrategyBenchmarkTestType.ApkManifest,
            valueColor = GitHubStatusPalette.Update
        )
        GitHubBenchmarkTaskRow(
            label = stringResource(R.string.github_strategy_metric_package_scan),
            result = result,
            testType = GitHubStrategyBenchmarkTestType.PackageNameScan,
            valueColor = GitHubStatusPalette.Stable
        )
        GitHubBenchmarkTaskRow(
            label = stringResource(R.string.github_strategy_metric_repo_scan),
            result = result,
            testType = GitHubStrategyBenchmarkTestType.RepositoryScan,
            valueColor = GitHubStatusPalette.Update
        )
        if (result.failures.isNotEmpty()) {
            Text(
                text = result.failures.take(2).joinToString("\n"),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        GitHubBenchmarkSampleDetails(
            result = result,
            expanded = samplesExpanded,
            onToggle = { samplesExpanded = !samplesExpanded }
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun GitHubBenchmarkSampleDetails(
    result: GitHubStrategyBenchmarkResult,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val samples = (result.coldSamples + result.warmSamples)
        .sortedWith(
            compareBy<GitHubStrategyBenchmarkSample> { it.testType.ordinal }
                .thenBy { it.target.id.lowercase() }
                .thenBy { it.fromCache }
        )
    if (samples.isEmpty()) return
    Text(
        text = stringResource(
            if (expanded) {
                R.string.github_strategy_benchmark_samples_hide
            } else {
                R.string.github_strategy_benchmark_samples_show
            },
            samples.size
        ),
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        color = GitHubStatusPalette.Active,
        fontSize = AppTypographyTokens.Supporting.fontSize,
        lineHeight = AppTypographyTokens.Supporting.lineHeight,
        fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight
    )
    if (!expanded) return
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        samples.take(BENCHMARK_SAMPLE_DETAIL_LIMIT).forEach { sample ->
            GitHubBenchmarkSampleLine(sample)
        }
        val hidden = samples.size - BENCHMARK_SAMPLE_DETAIL_LIMIT
        if (hidden > 0) {
            Text(
                text = stringResource(R.string.github_strategy_benchmark_samples_more, hidden),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun GitHubBenchmarkSampleLine(sample: GitHubStrategyBenchmarkSample) {
    val stateColor = if (sample.success) GitHubStatusPalette.Update else GitHubStatusPalette.Error
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${sample.typeLabel()} · ${sample.target.id}",
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        FlowRow(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusPill(
                label = stringResource(
                    if (sample.success) {
                        R.string.github_strategy_benchmark_sample_success
                    } else {
                        R.string.github_strategy_benchmark_sample_failed
                    }
                ),
                color = stateColor
            )
            StatusPill(
                label = stringResource(
                    R.string.github_strategy_benchmark_sample_elapsed,
                    sample.elapsedMs
                ),
                color = GitHubStatusPalette.Active
            )
            if (sample.fromCache) {
                StatusPill(
                    label = stringResource(R.string.github_strategy_benchmark_sample_cache),
                    color = GitHubStatusPalette.Stable
                )
            }
            sample.detailPills().forEach { pill ->
                StatusPill(
                    label = pill,
                    color = GitHubStatusPalette.Cache
                )
            }
        }
        sample.message
            .takeIf { it.isNotBlank() && !sample.success }
            ?.let { message ->
                Text(
                    text = message,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
    }
}

@Composable
private fun GitHubBenchmarkTaskRow(
    label: String,
    result: GitHubStrategyBenchmarkResult,
    testType: GitHubStrategyBenchmarkTestType,
    valueColor: Color
) {
    val samples = result.samplesFor(testType)
    if (samples.isEmpty()) return
    GitHubOverviewMetricItem(
        label = label,
        value = stringResource(
            R.string.github_strategy_metric_task_result,
            result.successCountFor(testType),
            samples.size,
            result.averageMsFor(testType)
        ),
        valueColor = valueColor
    )
}

@Composable
private fun GitHubStrategyBenchmarkSample.typeLabel(): String = testType.label()

@Composable
private fun GitHubStrategyBenchmarkTestType.label(): String {
    return when (this) {
        GitHubStrategyBenchmarkTestType.ReleaseSnapshot ->
            stringResource(R.string.github_strategy_metric_release_snapshot)

        GitHubStrategyBenchmarkTestType.ReleaseAssets ->
            stringResource(R.string.github_strategy_metric_release_assets)

        GitHubStrategyBenchmarkTestType.ReleaseNotes ->
            stringResource(R.string.github_strategy_metric_release_notes)

        GitHubStrategyBenchmarkTestType.ApkManifest ->
            stringResource(R.string.github_strategy_metric_apk_manifest)

        GitHubStrategyBenchmarkTestType.PackageNameScan ->
            stringResource(R.string.github_strategy_metric_package_scan)

        GitHubStrategyBenchmarkTestType.RepositoryScan ->
            stringResource(R.string.github_strategy_metric_repo_scan)
    }
}

private fun buildBenchmarkComparisonRows(
    results: List<GitHubStrategyBenchmarkResult>
): List<BenchmarkComparisonRow> {
    if (results.size < 2) return emptyList()
    return GitHubStrategyBenchmarkTestType.entries.mapNotNull { testType ->
        val stats = results.mapNotNull { result ->
            BenchmarkStrategyStats.from(result, testType)
        }
        if (stats.size < 2) return@mapNotNull null
        val ranked = stats.sortedWith(
            compareByDescending<BenchmarkStrategyStats> { it.successCount }
                .thenBy { it.averageMs }
        )
        val best = ranked.first()
        val second = ranked.getOrNull(1)
        val level = when {
            best.sampleCount == 0 -> BenchmarkComparisonLevel.Insufficient
            second == null -> BenchmarkComparisonLevel.Insufficient
            best.successCount > second.successCount -> BenchmarkComparisonLevel.Clear
            second.averageMs <= 0L -> BenchmarkComparisonLevel.Close
            best.averageMs <= 0L -> BenchmarkComparisonLevel.Clear
            best.averageMs * 100L <= second.averageMs * 85L -> BenchmarkComparisonLevel.Clear
            else -> BenchmarkComparisonLevel.Close
        }
        val winnerLabel = when (level) {
            BenchmarkComparisonLevel.Clear -> best.displayName
            BenchmarkComparisonLevel.Close -> "${best.displayName} / ${second?.displayName.orEmpty()}"
            BenchmarkComparisonLevel.Insufficient -> "-"
        }
        BenchmarkComparisonRow(
            testType = testType,
            winnerLabel = winnerLabel,
            level = level,
            metrics = ranked.map { stat ->
                "${stat.displayName} ${stat.successCount}/${stat.sampleCount} · ${stat.averageMs} ms"
            }
        )
    }
}

private data class BenchmarkComparisonRow(
    val testType: GitHubStrategyBenchmarkTestType,
    val winnerLabel: String,
    val level: BenchmarkComparisonLevel,
    val metrics: List<String>
)

private data class BenchmarkStrategyStats(
    val displayName: String,
    val successCount: Int,
    val sampleCount: Int,
    val averageMs: Long
) {
    companion object {
        fun from(
            result: GitHubStrategyBenchmarkResult,
            testType: GitHubStrategyBenchmarkTestType
        ): BenchmarkStrategyStats? {
            val samples = result.samplesFor(testType)
            if (samples.isEmpty()) return null
            return BenchmarkStrategyStats(
                displayName = result.displayName,
                successCount = samples.count { it.success },
                sampleCount = samples.size,
                averageMs = samples.map { it.elapsedMs }.averageRounded()
            )
        }
    }
}

private enum class BenchmarkComparisonLevel {
    Clear,
    Close,
    Insufficient
}

private fun List<Long>.averageRounded(): Long {
    if (isEmpty()) return 0L
    return (sumOf { it } / size.toDouble()).toLong()
}

@Composable
private fun GitHubStrategyBenchmarkSample.detailPills(): List<String> {
    return buildList {
        stableTag.takeIf { it.isNotBlank() }?.let { tag ->
            add(stringResource(R.string.github_strategy_benchmark_sample_tag, tag))
        }
        preReleaseTag.takeIf { it.isNotBlank() }?.let { tag ->
            add(stringResource(R.string.github_strategy_benchmark_sample_pre_tag, tag))
        }
        if (assetCount > 0) {
            add(stringResource(R.string.github_strategy_benchmark_sample_assets, assetCount))
        }
        if (releaseNotesLength > 0) {
            add(stringResource(R.string.github_strategy_benchmark_sample_notes, releaseNotesLength))
        }
        packageName.takeIf { it.isNotBlank() }?.let { value ->
            add(value)
        }
        matchedRepository.takeIf { it.isNotBlank() }?.let { repo ->
            add(repo)
        }
    }
}

@Composable
internal fun GitHubCredentialStatusCard(
    status: GitHubApiCredentialStatus
) {
    val context = LocalContext.current
    val accent = when (status.authMode) {
        GitHubApiAuthMode.Token -> GitHubStatusPalette.Update
        GitHubApiAuthMode.Guest -> GitHubStatusPalette.PreRelease
    }
    SheetSummaryCard(
        title = stringResource(R.string.github_strategy_card_title_credential_status),
        accentColor = accent,
        badgeLabel = status.localizedSummaryLabel(),
        badgeColor = accent,
        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
    ) {
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_mode),
            value = status.authMode.localizedLabel(),
            valueColor = accent,
            emphasized = true,
            titleMinWidth = 44.dp
        )
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_quota),
            value = "${status.coreRemaining} / ${status.coreLimit}",
            valueColor = if (status.coreRemaining > 0) accent else GitHubStatusPalette.Error,
            emphasized = true,
            titleMinWidth = 44.dp
        )
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_used),
            value = status.coreUsed.toString(),
            valueColor = MiuixTheme.colorScheme.onBackgroundVariant,
            titleMinWidth = 44.dp
        )
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_strategy_label_reset),
            value = formatFutureEta(context, status.resetAtMillis),
            valueColor = MiuixTheme.colorScheme.onBackgroundVariant,
            titleMinWidth = 44.dp
        )
    }
}

@Composable
private fun GitHubApiAuthMode.localizedLabel(): String {
    return when (this) {
        GitHubApiAuthMode.Guest -> stringResource(R.string.common_guest)
        GitHubApiAuthMode.Token -> stringResource(R.string.github_strategy_label_token)
    }
}

@Composable
private fun GitHubApiCredentialStatus.localizedSummaryLabel(): String {
    return when (authMode) {
        GitHubApiAuthMode.Guest -> stringResource(R.string.github_strategy_credential_guest_available)
        GitHubApiAuthMode.Token -> stringResource(R.string.github_strategy_credential_token_available)
    }
}

@Composable
private fun GitHubStrategyBenchmarkResult.localizedSummaryLabel(): String {
    val authModeLabel = authMode?.localizedLabel()
    return if (authModeLabel == null) {
        displayName
    } else {
        stringResource(R.string.github_strategy_benchmark_title_with_auth, displayName, authModeLabel)
    }
}

internal fun GitHubLookupStrategyOption.accentColor(): Color {
    return when (this) {
        GitHubLookupStrategyOption.AtomFeed -> GitHubStatusPalette.Active
        GitHubLookupStrategyOption.GitHubApiToken -> GitHubStatusPalette.Update
    }
}

internal fun GitHubActionsLookupStrategyOption.accentColor(): Color {
    return when (this) {
        GitHubActionsLookupStrategyOption.NightlyLink -> GitHubStatusPalette.Active
        GitHubActionsLookupStrategyOption.GitHubApiToken -> GitHubStatusPalette.Update
    }
}
