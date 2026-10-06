@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.os.appLucideChevronLeftIcon
import os.kei.ui.page.main.os.appLucideChevronRightIcon
import os.kei.ui.page.main.os.appLucideFilterIcon
import os.kei.ui.page.main.student.catalog.BaGuideCatalogFilterDefinition
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenu
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuActionRow
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuMultipleChoiceRow
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuSubmenuRow
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import top.yukonga.miuix.kmp.basic.PopupPositionProvider

private val BaCatalogFilterMenuMinWidth = 160.dp
private val BaCatalogFilterMenuMaxWidth = 200.dp
private val BaCatalogFilterMenuMaxHeight = 392.dp

@Composable
internal fun BaGuideCatalogFilterActionPopup(
    show: Boolean,
    anchorBoundsProvider: () -> IntRect?,
    definitions: List<BaGuideCatalogFilterDefinition>,
    selectedOptionIdsByFilterId: Map<Int, Set<Int>>,
    onDismissRequest: () -> Unit,
    onToggleOption: (filterId: Int, optionId: Int) -> Unit,
    onClearFilters: () -> Unit,
) {
    SnapshotWindowListPopup(
        show = show && definitions.isNotEmpty(),
        alignment = PopupPositionProvider.Align.BottomEnd,
        anchorBoundsProvider = anchorBoundsProvider,
        placement = SnapshotPopupPlacement.ButtonEnd,
        onDismissRequest = onDismissRequest,
    ) {
        val clearText = stringResource(R.string.ba_catalog_filter_clear)
        val allText = stringResource(R.string.ba_catalog_filter_all)
        val filterIcon = appLucideFilterIcon()
        val chevronLeftIcon = appLucideChevronLeftIcon()
        val chevronRightIcon = appLucideChevronRightIcon()
        LiquidGlassActionMenu(
            modifier = Modifier.testTag(BaCatalogFilterMenuTestTag),
            minWidth = BaCatalogFilterMenuMinWidth,
            maxWidth = BaCatalogFilterMenuMaxWidth,
            maxHeight = BaCatalogFilterMenuMaxHeight,
            items =
                buildList {
                    add(
                        LiquidGlassActionMenuActionRow(
                            id = "clear",
                            text = clearText,
                            leadingIcon = filterIcon,
                            enabled = selectedOptionIdsByFilterId.values.any { it.isNotEmpty() },
                            dismissOnClick = false,
                            onClick = onClearFilters,
                        ),
                    )
                    definitions.forEachIndexed { definitionIndex, definition ->
                        val selectedIds = selectedOptionIdsByFilterId[definition.id].orEmpty()
                        add(
                            LiquidGlassActionMenuSubmenuRow(
                                id = "filter_${definition.id}",
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
                                trailingIcon = chevronRightIcon,
                                highlighted = selectedIds.isNotEmpty(),
                                backLeadingIcon = chevronLeftIcon,
                                initialScrollItemIndex = definitionIndex,
                                submenuItems =
                                    definition.options.map { option ->
                                        LiquidGlassActionMenuMultipleChoiceRow(
                                            id = "filter_${definition.id}_option_${option.id}",
                                            text = option.name,
                                            checked = option.id in selectedIds,
                                            onCheckedChange = {
                                                onToggleOption(definition.id, option.id)
                                            },
                                        )
                                    },
                            ),
                        )
                    }
                },
            onDismissRequest = onDismissRequest,
        )
    }
}

internal const val BaCatalogFilterMenuTestTag = "ba_catalog_filter_action_menu"

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
