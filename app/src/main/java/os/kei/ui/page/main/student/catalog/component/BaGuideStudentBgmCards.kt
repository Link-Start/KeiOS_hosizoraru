@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideWarningIcon
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.status.AppStatusColors
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal sealed interface BaGuideStudentBgmLookupState {
    data object Idle : BaGuideStudentBgmLookupState

    data object Loading : BaGuideStudentBgmLookupState

    data object Missing : BaGuideStudentBgmLookupState

    data class Ready(
        val item: BaGuideStudentBgmResolvedItem,
    ) : BaGuideStudentBgmLookupState
}

internal fun BaGuideStudentBgmLookupState.readyFavoriteOrNull() = (this as? BaGuideStudentBgmLookupState.Ready)?.item?.favorite

@Composable
internal fun BaGuideStudentBgmHeader(
    totalCount: Int,
    displayedCount: Int,
    resolvedCount: Int,
    favoriteCount: Int,
    searchActive: Boolean,
    favoritesHidden: Boolean,
    accent: Color,
    onToggleFavoritesHidden: () -> Unit,
) {
    val matchedCount = if (searchActive) displayedCount else totalCount
    val hasFavorites = favoriteCount > 0
    AppSurfaceCard(
        containerColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.62f),
        borderColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.16f),
        showIndication = hasFavorites,
        onClick = if (hasFavorites) onToggleFavoritesHidden else null,
    ) {
        BaGuideStudentBgmHeaderMetrics(
            matchedLabel =
                stringResource(
                    if (searchActive) {
                        R.string.ba_catalog_student_bgm_metric_matched
                    } else {
                        R.string.ba_catalog_student_bgm_metric_students
                    },
                ),
            matchedCount = matchedCount,
            favoriteLabel = stringResource(R.string.ba_catalog_student_bgm_metric_favorites),
            favoriteCount = favoriteCount,
            resolvedLabel = stringResource(R.string.ba_catalog_student_bgm_metric_resolved),
            resolvedCount = resolvedCount,
            favoritesHidden = favoritesHidden,
            accent = accent,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BaGuideStudentBgmHeaderMetrics(
    matchedLabel: String,
    matchedCount: Int,
    favoriteLabel: String,
    favoriteCount: Int,
    resolvedLabel: String,
    resolvedCount: Int,
    favoritesHidden: Boolean,
    accent: Color,
) {
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        maxItemsInEachRow = 3,
    ) {
        BaGuideStudentBgmMetricPill(
            label = matchedLabel,
            value = matchedCount,
            color = Color(0xFF6366F1),
        )
        BaGuideStudentBgmMetricPill(
            label = favoriteLabel,
            value = favoriteCount,
            color =
                if (favoritesHidden) {
                    MiuixTheme.colorScheme.onBackgroundVariant
                } else {
                    Color(0xFFEC4899)
                },
        )
        BaGuideStudentBgmMetricPill(
            label = resolvedLabel,
            value = resolvedCount,
            color = accent,
        )
    }
}

@Composable
private fun BaGuideStudentBgmMetricPill(
    label: String,
    value: Int,
    color: Color,
) {
    StatusPill(
        label = "$label ${value.coerceAtLeast(0)}",
        color = color,
        size = AppStatusPillSize.Compact,
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp),
    )
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
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = lookupState == BaGuideStudentBgmLookupState.Loading
    val isMissing = lookupState == BaGuideStudentBgmLookupState.Missing
    val ready = lookupState as? BaGuideStudentBgmLookupState.Ready
    val cached = ready?.item?.fromCache == true
    val borderColor =
        when {
            favorite -> Color(0xFFEC4899).copy(alpha = 0.34f)
            selected -> accent.copy(alpha = 0.38f)
            cached -> accent.copy(alpha = 0.22f)
            else -> MiuixTheme.colorScheme.outline.copy(alpha = 0.16f)
        }
    val containerColor =
        when {
            favorite -> Color(0xFFEC4899).copy(alpha = 0.08f)
            cached -> accent.copy(alpha = 0.11f)
            else -> MiuixTheme.colorScheme.surface.copy(alpha = 0.58f)
        }
    val neutralTint = MiuixTheme.colorScheme.onBackgroundVariant
    val subtitle = entry.aliasDisplay.takeIf { it.isNotBlank() }
    AppSurfaceCard(
        containerColor = containerColor,
        borderColor = borderColor,
        modifier = modifier,
        onClick = onPlay,
        onLongClick = onOpenGuide,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.infoRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                BaGuideCatalogEntryAvatar(
                    imageUrl = entry.iconUrl,
                    fallbackRes = R.drawable.ba_tab_student_bgm,
                    size = 48.dp,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.name,
                        modifier = Modifier.weight(1f),
                        color = MiuixTheme.colorScheme.onBackground,
                        fontSize = AppTypographyTokens.CompactTitle.fontSize,
                        lineHeight = AppTypographyTokens.CompactTitle.lineHeight,
                        fontWeight = AppTypographyTokens.CompactTitle.fontWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isLoading || isMissing) {
                        BaGuideCatalogStatusIconPill(
                            label =
                                when {
                                    isLoading -> stringResource(R.string.ba_catalog_student_bgm_status_resolving)
                                    isMissing -> stringResource(R.string.ba_catalog_student_bgm_status_missing)
                                    else -> stringResource(R.string.ba_catalog_student_bgm_status_ready)
                                },
                            color =
                                when {
                                    isMissing -> AppStatusColors.Failed
                                    isLoading -> AppStatusColors.Refreshing
                                    else -> AppStatusColors.Fresh
                                },
                            icon = if (isMissing) appLucideWarningIcon() else appLucideRefreshIcon(),
                        )
                    }
                }
                subtitle?.let { value ->
                    Text(
                        text = value,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        fontSize = AppTypographyTokens.Supporting.fontSize,
                        lineHeight = AppTypographyTokens.Supporting.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.width(150.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppCompactIconAction(
                    icon = if (playing) appLucidePauseIcon() else appLucidePlayIcon(),
                    contentDescription =
                        stringResource(
                            if (playing) {
                                R.string.ba_catalog_bgm_action_pause
                            } else {
                                R.string.ba_catalog_student_bgm_action_resolve_play
                            },
                        ),
                    onClick = onPlay,
                    tint = if (playing || selected) accent else neutralTint,
                    visualSize = 38.dp,
                    enabled = !isLoading,
                )
                AppCompactIconAction(
                    icon = appLucideHeartIcon(),
                    contentDescription =
                        stringResource(
                            if (favorite) {
                                R.string.guide_bgm_cd_unfavorite
                            } else {
                                R.string.guide_bgm_cd_favorite
                            },
                        ),
                    onClick = onToggleFavorite,
                    tint = if (favorite) Color(0xFFEC4899) else neutralTint,
                    visualSize = 38.dp,
                    enabled = !isLoading,
                )
                AppCompactIconAction(
                    icon = appLucideExternalLinkIcon(),
                    contentDescription = stringResource(R.string.ba_catalog_bgm_action_open_gallery),
                    onClick = onOpenGuide,
                    tint = neutralTint,
                    visualSize = 38.dp,
                )
            }
        }
    }
}
