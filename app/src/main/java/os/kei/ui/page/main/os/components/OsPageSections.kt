@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.os.OsSectionCard
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.osActivityShortcutIconKey
import os.kei.ui.page.main.os.osLucideEnterIcon
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.ShortcutActivityIcon
import os.kei.ui.page.main.os.titleText
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun OsCardVisibilityManagerSheet(
    show: Boolean,
    title: String,
    sheetBackdrop: LayerBackdrop,
    cardsHintText: String,
    onDismissRequest: () -> Unit,
    isCardVisible: (OsSectionCard) -> Boolean,
    onCardVisibilityChange: (OsSectionCard, Boolean) -> Unit,
) {
    SnapshotWindowBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = sheetBackdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismissRequest,
            )
        },
    ) {
        SheetContentColumn(
            verticalSpacing = 10.dp,
        ) {
            @Composable
            fun CardLabel(
                card: OsSectionCard,
                modifier: Modifier = Modifier,
            ) {
                Row(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val iconModifier =
                        Modifier
                            .size(18.dp)
                            .defaultMinSize(minHeight = 18.dp)
                    val icon = sectionCardIcon(card)
                    val title = card.titleText()
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier = iconModifier,
                    )
                    Text(text = title, color = MiuixTheme.colorScheme.onBackground)
                }
            }

            SheetSectionCard(verticalSpacing = 10.dp) {
                OsSectionCard.entries
                    .filter { card ->
                        card != OsSectionCard.GOOGLE_SYSTEM_SERVICE &&
                            card != OsSectionCard.SHELL_RUNNER
                    }.forEach { card ->
                        SheetControlRow(
                            labelContent = {
                                CardLabel(card = card, modifier = Modifier.defaultMinSize(minHeight = 24.dp))
                            },
                        ) {
                            AppSwitch(
                                checked = isCardVisible(card),
                                onCheckedChange = { checked -> onCardVisibilityChange(card, checked) },
                            )
                        }
                    }
            }

            SheetDescriptionText(text = cardsHintText)
        }
    }
}

@Composable
internal fun OsActivityVisibilityManagerSheet(
    show: Boolean,
    title: String,
    sheetBackdrop: LayerBackdrop,
    activityHintText: String,
    cards: List<OsActivityShortcutCard>,
    activityIconBitmaps: Map<String, Bitmap>,
    packageIconBitmaps: Map<String, Bitmap>,
    defaultCardTitle: String,
    transferInProgress: Boolean,
    onExportAllCards: () -> Unit,
    onImportAllCards: () -> Unit,
    onDismissRequest: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onCardVisibilityChange: (String, Boolean) -> Unit,
) {
    SnapshotWindowBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = sheetBackdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismissRequest,
            )
        },
    ) {
        SheetContentColumn(
            verticalSpacing = 10.dp,
        ) {
            val activityVisibilityItems =
                remember(cards, defaultCardTitle) {
                    cards.map { card ->
                        OsActivityVisibilityItem(
                            id = card.id,
                            title = card.config.title.ifBlank { defaultCardTitle },
                            packageName = card.config.packageName,
                            className = card.config.className,
                            builtInSample = card.isBuiltInSample,
                            visible = card.visible,
                        )
                    }
                }
            val filteredActivityVisibilityItems =
                remember(activityVisibilityItems, query) {
                    activityVisibilityItems.filter { item ->
                        item.matchesActivityVisibilityQuery(query)
                    }
                }
            val builtInItems =
                remember(filteredActivityVisibilityItems) {
                    filteredActivityVisibilityItems.filter { it.builtInSample }
                }
            val customItems =
                remember(filteredActivityVisibilityItems) {
                    filteredActivityVisibilityItems.filterNot { it.builtInSample }
                }
            SheetSectionCard(verticalSpacing = 8.dp) {
                AppLiquidSearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = stringResource(R.string.os_visibility_search_activity_label),
                    backdrop = sheetBackdrop,
                    modifier = Modifier.fillMaxWidth(),
                    variant = GlassVariant.SheetInput,
                    textColor = MiuixTheme.colorScheme.primary,
                )
            }
            ActivityVisibilityGroup(
                title = stringResource(R.string.os_visibility_group_built_in),
                items = builtInItems,
                activityIconBitmaps = activityIconBitmaps,
                packageIconBitmaps = packageIconBitmaps,
                emptySearchActive = query.isNotBlank() && filteredActivityVisibilityItems.isEmpty(),
                noMatchedResultsText = stringResource(R.string.common_no_matched_results),
                onCardVisibilityChange = onCardVisibilityChange,
            )
            ActivityVisibilityGroup(
                title = stringResource(R.string.os_visibility_group_custom),
                items = customItems,
                activityIconBitmaps = activityIconBitmaps,
                packageIconBitmaps = packageIconBitmaps,
                emptySearchActive = false,
                noMatchedResultsText = stringResource(R.string.common_no_matched_results),
                onCardVisibilityChange = onCardVisibilityChange,
            )
            SheetSectionCard(verticalSpacing = 8.dp) {
                Text(
                    text = stringResource(R.string.os_activity_sheet_transfer_title),
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppLiquidTextButton(
                            backdrop = sheetBackdrop,
                            text = stringResource(R.string.os_activity_sheet_action_export_backup),
                            onClick = onExportAllCards,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !transferInProgress,
                            variant = GlassVariant.SheetAction,
                            pressOverlayEnabled = true,
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AppLiquidTextButton(
                            backdrop = sheetBackdrop,
                            text = stringResource(R.string.os_activity_sheet_action_import_backup),
                            onClick = onImportAllCards,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !transferInProgress,
                            variant = GlassVariant.SheetAction,
                            pressOverlayEnabled = true,
                        )
                    }
                }
            }
            SheetDescriptionText(text = stringResource(R.string.os_activity_sheet_transfer_desc))
            SheetDescriptionText(text = activityHintText)
        }
    }
}

@Composable
private fun ActivityVisibilityGroup(
    title: String,
    items: List<OsActivityVisibilityItem>,
    activityIconBitmaps: Map<String, Bitmap>,
    packageIconBitmaps: Map<String, Bitmap>,
    emptySearchActive: Boolean,
    noMatchedResultsText: String,
    onCardVisibilityChange: (String, Boolean) -> Unit,
) {
    if (items.isEmpty() && !emptySearchActive) return
    SheetSectionTitle(
        text = visibilityGroupTitle(title, items.size),
    )
    SheetSectionCard(verticalSpacing = 10.dp) {
        if (items.isEmpty()) {
            Text(
                text = noMatchedResultsText,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            return@SheetSectionCard
        }
        items.forEach { item ->
            ActivityVisibilityRow(
                item = item,
                activityIconBitmaps = activityIconBitmaps,
                packageIconBitmaps = packageIconBitmaps,
                onCardVisibilityChange = onCardVisibilityChange,
            )
        }
    }
}

@Composable
private fun ActivityVisibilityRow(
    item: OsActivityVisibilityItem,
    activityIconBitmaps: Map<String, Bitmap>,
    packageIconBitmaps: Map<String, Bitmap>,
    onCardVisibilityChange: (String, Boolean) -> Unit,
) {
    SheetControlRow(
        labelContent = {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.packageName.isNotBlank() || item.className.isNotBlank()) {
                    val iconKey =
                        osActivityShortcutIconKey(
                            packageName = item.packageName,
                            className = item.className,
                        )
                    ShortcutActivityIcon(
                        packageName = item.packageName,
                        className = item.className,
                        size = 18.dp,
                        bitmap = activityIconBitmaps[iconKey],
                        packageBitmap = packageIconBitmaps[item.packageName.trim()],
                    )
                } else {
                    Icon(
                        imageVector = osLucideEnterIcon(),
                        contentDescription = item.title,
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier =
                            Modifier
                                .size(18.dp)
                                .defaultMinSize(minHeight = 18.dp),
                    )
                }
                Text(
                    text = item.title,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.builtInSample) {
                    StatusPill(
                        label = stringResource(R.string.os_activity_card_builtin_badge),
                        color = Color(0xFF3B82F6),
                        size = AppStatusPillSize.Compact,
                    )
                }
            }
        },
    ) {
        AppSwitch(
            checked = item.visible,
            onCheckedChange = { checked ->
                onCardVisibilityChange(item.id, checked)
            },
        )
    }
}

private fun OsActivityVisibilityItem.matchesActivityVisibilityQuery(query: String): Boolean {
    val normalized = query.trim()
    if (normalized.isBlank()) return true
    return title.contains(normalized, ignoreCase = true) ||
        packageName.contains(normalized, ignoreCase = true) ||
        className.contains(normalized, ignoreCase = true)
}

@Composable
internal fun visibilityGroupTitle(
    title: String,
    count: Int,
): String =
    stringResource(
        R.string.os_visibility_group_title_count,
        title,
        stringResource(R.string.common_item_count, count),
    )
