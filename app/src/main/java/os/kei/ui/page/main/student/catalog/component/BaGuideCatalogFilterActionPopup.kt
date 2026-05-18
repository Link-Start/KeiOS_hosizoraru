@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.os.appLucideChevronLeftIcon
import os.kei.ui.page.main.os.appLucideChevronRightIcon
import os.kei.ui.page.main.os.appLucideFilterIcon
import os.kei.ui.page.main.student.catalog.BaGuideCatalogFilterDefinition
import os.kei.ui.page.main.widget.glass.AppLiquidGlassDropdownColumn
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownActionItem
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownItem
import os.kei.ui.page.main.widget.glass.LiquidGlassDropdownMaterial
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import top.yukonga.miuix.kmp.basic.PopupPositionProvider

private val BaCatalogFilterMenuMinWidth = 160.dp
private val BaCatalogFilterMenuMaxWidth = 200.dp
private val BaCatalogFilterMenuMaxHeight = 392.dp

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
        alignment = PopupPositionProvider.Align.BottomEnd,
        anchorBounds = anchorBounds,
        placement = SnapshotPopupPlacement.ButtonEnd,
        onDismissRequest = onDismissRequest,
        enableWindowDim = false,
    ) {
        var focusedFilterId by remember(definitions) {
            mutableStateOf<Int?>(null)
        }
        val clearText = stringResource(R.string.ba_catalog_filter_clear)
        val allText = stringResource(R.string.ba_catalog_filter_all)
        val focusedDefinition = definitions.firstOrNull { it.id == focusedFilterId }
        AppLiquidGlassDropdownColumn(
            minWidth = BaCatalogFilterMenuMinWidth,
            maxWidth = BaCatalogFilterMenuMaxWidth,
            maxHeight = BaCatalogFilterMenuMaxHeight,
            initialScrollItemIndex =
                focusedDefinition?.let { definition ->
                    definitions.indexOfFirst { it.id == definition.id }.takeIf { it >= 0 }
                },
            material = LiquidGlassDropdownMaterial.ActionMenu,
        ) {
            if (focusedDefinition == null) {
                LiquidGlassDropdownActionItem(
                    text = clearText,
                    onClick = onClearFilters,
                    index = 0,
                    optionSize = definitions.size + 1,
                    leadingIcon = appLucideFilterIcon(),
                    enabled = selectedOptionIdsByFilterId.values.any { it.isNotEmpty() },
                )
                definitions.forEachIndexed { index, definition ->
                    val selectedIds = selectedOptionIdsByFilterId[definition.id].orEmpty()
                    LiquidGlassDropdownActionItem(
                        text = definition.name,
                        subtitle =
                            selectedFilterSubtitle(
                                definition = definition,
                                selectedIds = selectedIds,
                                allText = allText,
                                selectedCountText =
                                    stringResource(
                                        R.string.ba_catalog_filter_selected_count,
                                        selectedIds.size,
                                    ),
                            ),
                        trailingIcon = appLucideChevronRightIcon(),
                        highlighted = selectedIds.isNotEmpty(),
                        onClick = { focusedFilterId = definition.id },
                        index = index + 1,
                        optionSize = definitions.size + 1,
                    )
                }
            } else {
                val selectedIds = selectedOptionIdsByFilterId[focusedDefinition.id].orEmpty()
                LiquidGlassDropdownActionItem(
                    text = focusedDefinition.name,
                    subtitle =
                        selectedFilterSubtitle(
                            definition = focusedDefinition,
                            selectedIds = selectedIds,
                            allText = allText,
                            selectedCountText =
                                stringResource(
                                    R.string.ba_catalog_filter_selected_count,
                                    selectedIds.size,
                                ),
                        ),
                    leadingIcon = appLucideChevronLeftIcon(),
                    onClick = { focusedFilterId = null },
                    index = 0,
                    optionSize = focusedDefinition.options.size + 1,
                )
                focusedDefinition.options.forEachIndexed { index, option ->
                    val selected = option.id in selectedIds
                    LiquidGlassDropdownItem(
                        text = option.name,
                        selected = selected,
                        onClick = { onToggleOption(focusedDefinition.id, option.id) },
                        reserveCheckSlot = true,
                        highlighted = selected,
                        showCheck = selected,
                        highlightContent = selected,
                        textMaxLines = 1,
                        index = index + 1,
                        optionSize = focusedDefinition.options.size + 1,
                    )
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
