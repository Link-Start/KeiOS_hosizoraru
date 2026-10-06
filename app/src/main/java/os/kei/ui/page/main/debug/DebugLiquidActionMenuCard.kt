@file:Suppress("FunctionName")

package os.kei.ui.page.main.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideChevronRightIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideHeartIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.os.appLucideListIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideMusicIcon
import os.kei.ui.page.main.os.appLucidePlayIcon
import os.kei.ui.page.main.os.appLucideShareIcon
import os.kei.ui.page.main.os.appLucideTrashIcon
import os.kei.ui.page.main.os.appLucideVolume2Icon
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenu
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuActionRow
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuInfoRow
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuMultipleChoiceRow
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuQuickAction
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuSingleChoiceRow
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuSubmenuRow
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import os.kei.ui.page.main.widget.sheet.capturePopupAnchor
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DebugLiquidActionMenuCard(
    accent: Color,
    backdrop: Backdrop,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf<IntRect?>(null) }
    var selectedQualityIndex by remember { mutableIntStateOf(0) }
    var compactRowsSelected by remember { mutableStateOf(false) }
    var showLyricsSelected by remember { mutableStateOf(true) }
    var normalizeVolumeSelected by remember { mutableStateOf(false) }
    var dismissFinishedCount by remember { mutableIntStateOf(0) }
    val idleAction = stringResource(R.string.debug_component_lab_liquid_action_menu_idle)
    var lastAction by remember { mutableStateOf(idleAction) }
    val contentColor = MiuixTheme.colorScheme.onBackground
    val secondaryColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.78f)
    val playLabel = stringResource(R.string.debug_component_lab_action_play)
    val shareLabel = stringResource(R.string.debug_component_lab_action_share)
    val downloadLabel = stringResource(R.string.debug_component_lab_action_download)
    val favoriteLabel = stringResource(R.string.debug_component_lab_action_favorite)
    val compactRowsLabel = stringResource(R.string.debug_component_lab_liquid_action_menu_compact_rows)
    val playbackOptionsLabel =
        stringResource(R.string.debug_component_lab_liquid_action_menu_playback_options)
    val playbackOptionsSummary =
        stringResource(R.string.debug_component_lab_liquid_action_menu_playback_options_summary)
    val showLyricsLabel =
        stringResource(R.string.debug_component_lab_liquid_action_menu_show_lyrics)
    val normalizeVolumeLabel =
        stringResource(R.string.debug_component_lab_liquid_action_menu_normalize_volume)
    val infoLabel = stringResource(R.string.debug_component_lab_liquid_action_menu_info)
    val infoSummary = stringResource(R.string.debug_component_lab_liquid_action_menu_info_summary)
    val disabledLabel = stringResource(R.string.debug_component_lab_liquid_action_menu_disabled)
    val dangerLabel = stringResource(R.string.debug_component_lab_liquid_action_menu_danger)
    val qualityLabel = stringResource(R.string.debug_component_lab_liquid_action_menu_quality)
    val qualityLabels =
        listOf(
            stringResource(R.string.debug_component_lab_liquid_action_menu_quality_auto),
            stringResource(R.string.debug_component_lab_liquid_action_menu_quality_high),
            stringResource(R.string.debug_component_lab_liquid_action_menu_quality_lossless),
        )

    AppFeatureCard(
        title = stringResource(R.string.debug_component_lab_liquid_action_menu_title),
        subtitle = stringResource(R.string.debug_component_lab_liquid_action_menu_subtitle),
        backdrop = backdrop,
        exportBackdropToContent = true,
        sectionIcon = appLucideMoreIcon(),
        titleColor = accent,
        borderColor = accent.copy(alpha = 0.20f),
        contentVerticalSpacing = CardLayoutRhythm.sectionGap,
    ) {
        val cardBackdrop = LocalLiquidParentBackdrop.current ?: backdrop
        Text(
            text = stringResource(R.string.debug_component_lab_liquid_action_menu_lifecycle_hint),
            color = secondaryColor,
            fontSize = AppTypographyTokens.Body.fontSize,
            lineHeight = AppTypographyTokens.Body.lineHeight,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    stringResource(
                        R.string.debug_component_lab_liquid_action_menu_last_action,
                        lastAction,
                    ),
                color = contentColor,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    stringResource(
                        R.string.debug_component_lab_liquid_action_menu_dismiss_count,
                        dismissFinishedCount,
                    ),
                color = secondaryColor,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppLiquidTextButton(
                backdrop = cardBackdrop,
                text = stringResource(R.string.debug_component_lab_liquid_action_menu_open),
                onClick = { expanded = true },
                modifier = Modifier.capturePopupAnchor { anchorBounds = it },
                textColor = contentColor,
                leadingIcon = appLucideMoreIcon(),
                iconTint = contentColor,
                variant = GlassVariant.SheetAction,
                minHeight = 44.dp,
                horizontalPadding = 14.dp,
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis,
            )
        }

        key("debug-liquid-action-menu-popup") {
            SnapshotWindowListPopup(
                show = expanded,
                alignment = PopupPositionProvider.Align.BottomEnd,
                anchorBoundsProvider = { anchorBounds },
                placement = SnapshotPopupPlacement.ButtonEnd,
                onDismissRequest = { expanded = false },
                onDismissFinished = { dismissFinishedCount += 1 },
                minWidth = 276.dp,
                maxWidth = 324.dp,
            ) {
                LiquidGlassActionMenu(
                    backdrop = backdrop,
                    accentColor = accent,
                    minWidth = 276.dp,
                    maxWidth = 324.dp,
                    quickActions =
                        listOf(
                            LiquidGlassActionMenuQuickAction(
                                id = "play",
                                icon = appLucidePlayIcon(),
                                label = playLabel,
                                onClick = { lastAction = playLabel },
                            ),
                            LiquidGlassActionMenuQuickAction(
                                id = "share",
                                icon = appLucideShareIcon(),
                                label = shareLabel,
                                onClick = { lastAction = shareLabel },
                            ),
                            LiquidGlassActionMenuQuickAction(
                                id = "offline",
                                icon = appLucideDownloadIcon(),
                                label = downloadLabel,
                                enabled = false,
                                onClick = {},
                            ),
                        ),
                    items =
                        listOf(
                            LiquidGlassActionMenuActionRow(
                                id = "favorite",
                                text = favoriteLabel,
                                leadingIcon = appLucideHeartIcon(),
                                highlighted = true,
                                onClick = { lastAction = favoriteLabel },
                            ),
                            LiquidGlassActionMenuMultipleChoiceRow(
                                id = "compact_rows",
                                text = compactRowsLabel,
                                checked = compactRowsSelected,
                                leadingIcon = appLucideListIcon(),
                                onCheckedChange = { checked ->
                                    compactRowsSelected = checked
                                    lastAction = compactRowsLabel
                                },
                            ),
                            LiquidGlassActionMenuSubmenuRow(
                                id = "quality",
                                text = qualityLabel,
                                subtitle = qualityLabels[selectedQualityIndex],
                                leadingIcon = appLucideMusicIcon(),
                                trailingIcon = appLucideChevronRightIcon(),
                                submenuItems =
                                    qualityLabels.mapIndexed { index, label ->
                                        LiquidGlassActionMenuSingleChoiceRow(
                                            id = "quality_$index",
                                            text = label,
                                            selected = selectedQualityIndex == index,
                                            onClick = {
                                                selectedQualityIndex = index
                                                lastAction = label
                                            },
                                        )
                                    },
                            ),
                            LiquidGlassActionMenuSubmenuRow(
                                id = "playback_options",
                                text = playbackOptionsLabel,
                                subtitle = playbackOptionsSummary,
                                leadingIcon = appLucideVolume2Icon(),
                                trailingIcon = appLucideChevronRightIcon(),
                                highlighted = showLyricsSelected || normalizeVolumeSelected,
                                submenuItems =
                                    listOf(
                                        LiquidGlassActionMenuMultipleChoiceRow(
                                            id = "show_lyrics",
                                            text = showLyricsLabel,
                                            checked = showLyricsSelected,
                                            onCheckedChange = { checked ->
                                                showLyricsSelected = checked
                                                lastAction = showLyricsLabel
                                            },
                                        ),
                                        LiquidGlassActionMenuMultipleChoiceRow(
                                            id = "normalize_volume",
                                            text = normalizeVolumeLabel,
                                            checked = normalizeVolumeSelected,
                                            onCheckedChange = { checked ->
                                                normalizeVolumeSelected = checked
                                                lastAction = normalizeVolumeLabel
                                            },
                                        ),
                                    ),
                            ),
                            LiquidGlassActionMenuInfoRow(
                                id = "passive_info",
                                text = infoLabel,
                                subtitle = infoSummary,
                                leadingIcon = appLucideInfoIcon(),
                            ),
                            LiquidGlassActionMenuActionRow(
                                id = "disabled",
                                text = disabledLabel,
                                subtitle = downloadLabel,
                                leadingIcon = appLucideDownloadIcon(),
                                enabled = false,
                                onClick = {},
                            ),
                            LiquidGlassActionMenuActionRow(
                                id = "danger",
                                text = dangerLabel,
                                leadingIcon = appLucideTrashIcon(),
                                variant = GlassVariant.SheetDangerAction,
                                onClick = { lastAction = dangerLabel },
                            ),
                        ),
                    onDismissRequest = { expanded = false },
                )
            }
        }
    }
}
