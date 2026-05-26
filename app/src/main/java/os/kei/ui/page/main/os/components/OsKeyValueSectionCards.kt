@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.os.InfoRow
import os.kei.ui.page.main.os.OsSectionCard
import os.kei.ui.page.main.widget.glass.AppLiquidAccordionCard
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal const val SMALL_INFO_ROW_COUNT = 24

internal fun LazyListScope.addKeyValueSectionCard(
    visible: Boolean,
    card: OsSectionCard,
    contentBackdrop: LayerBackdrop,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    rows: List<InfoRow>,
    noMatchedResultsText: String,
    exportAction: @Composable () -> Unit,
) {
    if (!visible) return
    item(key = "os-section-${card.name}", contentType = "os_key_value_card") {
        AppLiquidAccordionCard(
            backdrop = contentBackdrop,
            title = title,
            subtitle = subtitle,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            headerStartAction = {
                OsSectionHeaderIcon(card = card)
            },
            headerActions = exportAction,
        ) {
            if (rows.isEmpty()) {
                Text(text = noMatchedResultsText, color = MiuixTheme.colorScheme.onBackgroundVariant)
            } else {
                OsVirtualizedInfoRows(rows = rows)
            }
        }
    }
    item(key = "os-section-space-${card.name}", contentType = "os_section_space") {
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
internal fun OsVirtualizedInfoRows(
    rows: List<InfoRow>,
    valueSingleLine: Boolean = false,
    labelMinWidth: Dp = 72.dp,
    labelMaxWidth: Dp = 136.dp,
    labelMaxLines: Int = Int.MAX_VALUE,
    valueMinWidth: Dp = Dp.Unspecified,
) {
    if (rows.size <= SMALL_INFO_ROW_COUNT) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEach { row ->
                OsSectionInfoRow(
                    label = row.key,
                    value = row.value,
                    valueSingleLine = valueSingleLine,
                    labelMinWidth = labelMinWidth,
                    labelMaxWidth = labelMaxWidth,
                    labelMaxLines = labelMaxLines,
                    valueMinWidth = valueMinWidth,
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
        items(
            items = rows,
            key = { row -> row.osInfoRowStableKey() },
            contentType = { "os_info_row" },
        ) { row ->
            OsSectionInfoRow(
                label = row.key,
                value = row.value,
                valueSingleLine = valueSingleLine,
                labelMinWidth = labelMinWidth,
                labelMaxWidth = labelMaxWidth,
                labelMaxLines = labelMaxLines,
                valueMinWidth = valueMinWidth,
            )
        }
    }
}
