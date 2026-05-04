package os.kei.ui.page.main.student.catalog.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucideHeartIcon
import os.kei.ui.page.main.os.appLucidePauseIcon
import os.kei.ui.page.main.os.appLucidePlayIcon
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppOverviewCard
import os.kei.ui.page.main.widget.core.AppOverviewMetricTile
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal sealed interface BaGuideStudentBgmLookupState {
    data object Idle : BaGuideStudentBgmLookupState
    data object Loading : BaGuideStudentBgmLookupState
    data object Missing : BaGuideStudentBgmLookupState
    data class Ready(val item: BaGuideStudentBgmResolvedItem) : BaGuideStudentBgmLookupState
}

internal fun BaGuideStudentBgmLookupState.readyFavoriteOrNull() =
    (this as? BaGuideStudentBgmLookupState.Ready)?.item?.favorite

@Composable
internal fun BaGuideStudentBgmHeader(
    totalCount: Int,
    displayedCount: Int,
    resolvedCount: Int,
    favoriteCount: Int,
    loadingCount: Int,
    searchActive: Boolean,
    accent: Color
) {
    val matchedCount = if (searchActive) displayedCount else totalCount
    AppOverviewCard(
        title = stringResource(R.string.ba_catalog_student_bgm_title),
        subtitle = if (searchActive) {
            stringResource(R.string.ba_catalog_student_bgm_overview_search_subtitle, matchedCount, totalCount)
        } else {
            stringResource(R.string.ba_catalog_student_bgm_overview_subtitle)
        },
        containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f),
        borderColor = accent.copy(alpha = 0.18f),
        contentVerticalSpacing = CardLayoutRhythm.denseSectionGap
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.metricRowGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppOverviewMetricTile(
                label = stringResource(
                    if (searchActive) {
                        R.string.ba_catalog_student_bgm_metric_matched
                    } else {
                        R.string.ba_catalog_student_bgm_metric_students
                    }
                ),
                value = matchedCount.coerceAtLeast(0).toString(),
                valueColor = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            AppOverviewMetricTile(
                label = stringResource(R.string.ba_catalog_student_bgm_metric_resolved),
                value = resolvedCount.coerceAtLeast(0).toString(),
                valueColor = if (loadingCount > 0) Color(0xFFF59E0B) else accent,
                modifier = Modifier.weight(1f)
            )
            AppOverviewMetricTile(
                label = stringResource(R.string.ba_catalog_student_bgm_metric_favorites),
                value = favoriteCount.coerceAtLeast(0).toString(),
                valueColor = Color(0xFFEC4899),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun BaGuideStudentBgmCard(
    entry: BaGuideCatalogEntry,
    lookupState: BaGuideStudentBgmLookupState,
    selected: Boolean,
    playing: Boolean,
    favorite: Boolean,
    accent: Color,
    onOpenGuide: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val isLoading = lookupState == BaGuideStudentBgmLookupState.Loading
    val isMissing = lookupState == BaGuideStudentBgmLookupState.Missing
    val ready = lookupState as? BaGuideStudentBgmLookupState.Ready
    val borderColor = when {
        selected -> accent.copy(alpha = 0.38f)
        favorite -> Color(0xFFEC4899).copy(alpha = 0.34f)
        else -> MiuixTheme.colorScheme.outline.copy(alpha = 0.16f)
    }
    val containerColor = when {
        selected -> accent.copy(alpha = 0.11f)
        favorite -> Color(0xFFEC4899).copy(alpha = 0.08f)
        else -> MiuixTheme.colorScheme.surface.copy(alpha = 0.58f)
    }
    val neutralTint = MiuixTheme.colorScheme.onBackgroundVariant
    val subtitle = entry.aliasDisplay.takeIf { it.isNotBlank() }
    AppSurfaceCard(
        containerColor = containerColor,
        borderColor = borderColor,
        onClick = onPlay,
        onLongClick = onOpenGuide
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.infoRowGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                BaGuideCatalogEntryAvatar(
                    imageUrl = entry.iconUrl,
                    fallbackRes = R.drawable.ba_tab_student_bgm,
                    size = 48.dp
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.name,
                        modifier = Modifier.weight(1f),
                        color = MiuixTheme.colorScheme.onBackground,
                        fontSize = AppTypographyTokens.CompactTitle.fontSize,
                        lineHeight = AppTypographyTokens.CompactTitle.lineHeight,
                        fontWeight = AppTypographyTokens.CompactTitle.fontWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                subtitle?.let { value ->
                    Text(
                        text = value,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        fontSize = AppTypographyTokens.Supporting.fontSize,
                        lineHeight = AppTypographyTokens.Supporting.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoading || isMissing || ready != null) {
                        StatusPill(
                            label = when {
                                isLoading -> stringResource(R.string.ba_catalog_student_bgm_status_resolving)
                                isMissing -> stringResource(R.string.ba_catalog_student_bgm_status_missing)
                                ready?.item?.fromCache == true -> stringResource(R.string.ba_catalog_student_bgm_status_cached_detail)
                                else -> stringResource(R.string.ba_catalog_student_bgm_status_ready)
                            },
                            color = when {
                                isMissing -> Color(0xFFEF4444)
                                isLoading -> Color(0xFFF59E0B)
                                else -> Color(0xFF22C55E)
                            },
                            size = AppStatusPillSize.Compact
                        )
                    }
                }
            }
            AppCompactIconAction(
                icon = if (playing) appLucidePauseIcon() else appLucidePlayIcon(),
                contentDescription = stringResource(
                    if (playing) {
                        R.string.ba_catalog_bgm_action_pause
                    } else {
                        R.string.ba_catalog_student_bgm_action_resolve_play
                    }
                ),
                onClick = onPlay,
                modifier = Modifier.size(44.dp),
                tint = if (playing || selected) accent else neutralTint,
                minSize = 44.dp,
                enabled = !isLoading
            )
            AppCompactIconAction(
                icon = appLucideHeartIcon(),
                contentDescription = stringResource(
                    if (favorite) {
                        R.string.guide_bgm_cd_unfavorite
                    } else {
                        R.string.guide_bgm_cd_favorite
                    }
                ),
                onClick = onToggleFavorite,
                modifier = Modifier.size(42.dp),
                tint = if (favorite) Color(0xFFEC4899) else neutralTint,
                minSize = 42.dp,
                enabled = !isLoading
            )
            AppCompactIconAction(
                icon = appLucideExternalLinkIcon(),
                contentDescription = stringResource(R.string.ba_catalog_bgm_action_open_gallery),
                onClick = onOpenGuide,
                modifier = Modifier.size(42.dp),
                tint = neutralTint,
                minSize = 42.dp
            )
        }
    }
}
