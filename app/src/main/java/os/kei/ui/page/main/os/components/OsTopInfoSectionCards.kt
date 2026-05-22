@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.os.InfoRow
import os.kei.ui.page.main.os.OsSectionCard
import os.kei.ui.page.main.os.TopInfoRowsGroup
import os.kei.ui.page.main.os.topInfoDisplayLabel
import os.kei.ui.page.main.os.topInfoDisplayValue
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidAccordionCard
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun LazyListScope.addTopInfoCard(
    visible: Boolean,
    contentBackdrop: LayerBackdrop,
    displayedTopInfoRows: List<InfoRow>,
    groupedTopInfoRows: List<TopInfoRowsGroup>,
    query: String,
    noMatchedResultsText: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    exportAction: @Composable () -> Unit,
) {
    if (!visible) return
    item(key = "os-top-info-card", contentType = "os_top_info_card") {
        AppLiquidAccordionCard(
            backdrop = contentBackdrop,
            title = stringResource(R.string.os_section_top_info_title),
            subtitle = stringResource(R.string.common_item_count, displayedTopInfoRows.size),
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            headerStartAction = {
                OsSectionHeaderIcon(card = OsSectionCard.TOP_INFO)
            },
            headerActions = exportAction,
        ) {
            if (displayedTopInfoRows.isEmpty()) {
                Text(text = noMatchedResultsText, color = MiuixTheme.colorScheme.onBackgroundVariant)
            } else if (query.isBlank() && !expanded) {
                OsVirtualizedTopInfoRows(
                    rows = displayedTopInfoRows,
                    labelMinWidth = 104.dp,
                    labelMaxWidth = 150.dp,
                )
            } else {
                OsVirtualizedGroupedTopInfoRows(groupedRows = groupedTopInfoRows)
            }
        }
    }
    item(key = "os-top-info-space", contentType = "os_section_space") {
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private sealed interface TopInfoVirtualizedItem {
    data class Header(
        val group: TopInfoRowsGroup,
    ) : TopInfoVirtualizedItem

    data class Entry(
        val row: InfoRow,
    ) : TopInfoVirtualizedItem
}

@Composable
private fun OsVirtualizedGroupedTopInfoRows(groupedRows: List<TopInfoRowsGroup>) {
    val context = LocalContext.current
    val rows =
        remember(groupedRows) {
            buildList {
                groupedRows.forEach { group ->
                    if (group.rows.isNotEmpty()) {
                        add(TopInfoVirtualizedItem.Header(group))
                        group.rows.forEach { entry -> add(TopInfoVirtualizedItem.Entry(entry)) }
                    }
                }
            }
        }
    if (rows.size <= SMALL_INFO_ROW_COUNT) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, item ->
                when (item) {
                    is TopInfoVirtualizedItem.Header -> {
                        TopInfoGroupHeader(item.group, index)
                    }

                    is TopInfoVirtualizedItem.Entry -> {
                        OsTopInfoDisplayRow(
                            label = topInfoDisplayLabel(context, item.row.key),
                            value = topInfoDisplayValue(context, item.row),
                            labelMinWidth = 56.dp,
                            labelMaxWidth = 156.dp,
                        )
                    }
                }
            }
        }
        return
    }
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
        userScrollEnabled = true,
    ) {
        itemsIndexed(
            items = rows,
            key = { _, item ->
                when (item) {
                    is TopInfoVirtualizedItem.Header -> "header-${item.group.titleRes}"
                    is TopInfoVirtualizedItem.Entry -> "entry-${item.row.key}"
                }
            },
            contentType = { _, item ->
                when (item) {
                    is TopInfoVirtualizedItem.Header -> "os_top_info_header"
                    is TopInfoVirtualizedItem.Entry -> "os_top_info_entry"
                }
            },
        ) { index, item ->
            when (item) {
                is TopInfoVirtualizedItem.Header -> {
                    TopInfoGroupHeader(item.group, index)
                }

                is TopInfoVirtualizedItem.Entry -> {
                    OsTopInfoDisplayRow(
                        label = topInfoDisplayLabel(context, item.row.key),
                        value = topInfoDisplayValue(context, item.row),
                        labelMinWidth = 56.dp,
                        labelMaxWidth = 156.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopInfoGroupHeader(
    group: TopInfoRowsGroup,
    index: Int,
) {
    Text(
        text = stringResource(group.titleRes),
        color = MiuixTheme.colorScheme.onBackground,
        fontSize = AppTypographyTokens.CompactTitle.fontSize,
        lineHeight = AppTypographyTokens.CompactTitle.lineHeight,
        fontWeight = AppTypographyTokens.CompactTitle.fontWeight,
        modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun OsVirtualizedTopInfoRows(
    rows: List<InfoRow>,
    labelMinWidth: Dp = 104.dp,
    labelMaxWidth: Dp = 156.dp,
) {
    val context = LocalContext.current
    if (rows.size <= SMALL_INFO_ROW_COUNT) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEach { row ->
                OsTopInfoDisplayRow(
                    label = topInfoDisplayLabel(context, row.key),
                    value = topInfoDisplayValue(context, row),
                    labelMinWidth = labelMinWidth,
                    labelMaxWidth = labelMaxWidth,
                )
            }
        }
        return
    }
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
        userScrollEnabled = true,
    ) {
        itemsIndexed(
            items = rows,
            key = { _, row -> row.key },
            contentType = { _, _ -> "os_top_info_entry" },
        ) { _, row ->
            OsTopInfoDisplayRow(
                label = topInfoDisplayLabel(context, row.key),
                value = topInfoDisplayValue(context, row),
                labelMinWidth = labelMinWidth,
                labelMaxWidth = labelMaxWidth,
            )
        }
    }
}

@Composable
private fun OsTopInfoDisplayRow(
    label: String,
    value: String,
    labelMinWidth: Dp,
    labelMaxWidth: Dp,
) {
    OsSectionInfoRow(
        label = label,
        value = value,
        valueSingleLine = false,
        labelMinWidth = labelMinWidth,
        labelMaxWidth = labelMaxWidth,
        labelMaxLines = 2,
        valueMinWidth = 120.dp,
        copyValueOnly = false,
    )
}
