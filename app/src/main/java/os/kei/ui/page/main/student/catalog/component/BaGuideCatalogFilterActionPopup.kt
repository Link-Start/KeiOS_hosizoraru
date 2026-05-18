@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.student.catalog.BaGuideCatalogFilterDefinition
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownActionItem
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownColumn
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownItem
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import top.yukonga.miuix.kmp.basic.PopupPositionProvider

@Composable
internal fun BaGuideCatalogFilterActionPopup(
    show: Boolean,
    anchorBounds: IntRect?,
    definitions: List<BaGuideCatalogFilterDefinition>,
    selectedOptionIdsByFilterId: Map<Int, Set<Int>>,
    onDismissRequest: () -> Unit,
    onToggleOption: (filterId: Int, optionId: Int) -> Unit,
    onClearFilters: () -> Unit,
) {
    if (!show || definitions.isEmpty()) return
    SnapshotWindowListPopup(
        show = show,
        alignment = PopupPositionProvider.Align.BottomStart,
        anchorBounds = anchorBounds,
        placement = SnapshotPopupPlacement.ActionBarCenter,
        onDismissRequest = onDismissRequest,
        enableWindowDim = false,
    ) {
        var expandedFilterId by remember(definitions) {
            mutableIntStateOf(definitions.firstOrNull()?.id ?: 0)
        }
        val clearText = stringResource(R.string.ba_catalog_filter_clear)
        LiquidGlassDropdownColumn(
            minWidth = 248.dp,
            maxWidth = 336.dp,
            maxHeight = 468.dp,
        ) {
            LiquidGlassDropdownActionItem(
                text = clearText,
                onClick = onClearFilters,
                index = 0,
                optionSize = 1,
                enabled = selectedOptionIdsByFilterId.values.any { it.isNotEmpty() },
            )
            definitions.forEach { definition ->
                val selectedIds = selectedOptionIdsByFilterId[definition.id].orEmpty()
                val subtitle =
                    selectedFilterSubtitle(
                        definition = definition,
                        selectedIds = selectedIds,
                        allText = stringResource(R.string.ba_catalog_filter_all),
                        selectedCountText =
                            stringResource(
                                R.string.ba_catalog_filter_selected_count,
                                selectedIds.size,
                            ),
                    )
                LiquidGlassDropdownActionItem(
                    text = definition.name,
                    subtitle = subtitle,
                    highlighted = expandedFilterId == definition.id,
                    onClick = { expandedFilterId = definition.id },
                )
                if (expandedFilterId == definition.id) {
                    definition.options.forEach { option ->
                        val selected = option.id in selectedIds
                        LiquidGlassDropdownItem(
                            text = option.name,
                            selected = selected,
                            onClick = { onToggleOption(definition.id, option.id) },
                            reserveCheckSlot = true,
                            highlighted = selected,
                            showCheck = selected,
                            highlightContent = selected,
                            textMaxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun selectedFilterSubtitle(
    definition: BaGuideCatalogFilterDefinition,
    selectedIds: Set<Int>,
    allText: String,
    selectedCountText: String,
): String {
    if (selectedIds.isEmpty()) return allText
    val labels =
        selectedIds
            .mapNotNull { id -> definition.optionLabel(id).takeIf { it.isNotBlank() } }
    if (labels.isEmpty()) return selectedCountText
    return labels.take(3).joinToString(" / ").let { label ->
        if (labels.size <= 3) label else selectedCountText
    }
}
