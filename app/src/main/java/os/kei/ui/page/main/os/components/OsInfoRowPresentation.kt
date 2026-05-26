package os.kei.ui.page.main.os.components

import android.content.Context
import androidx.compose.runtime.Immutable
import os.kei.ui.page.main.os.InfoRow
import os.kei.ui.page.main.os.topInfoDisplayLabel
import os.kei.ui.page.main.os.topInfoDisplayValue

@Immutable
internal data class OsInfoRowDisplayItem(
    val stableKey: String,
    val label: String,
    val value: String,
)

internal fun InfoRow.osInfoRowStableKey(): String =
    buildString {
        append(key.trim())
        append('\u001F')
        append(value.trim().hashCode())
    }

internal fun buildTopInfoDisplayItems(
    context: Context,
    rows: List<InfoRow>,
): List<OsInfoRowDisplayItem> =
    rows.map { row ->
        OsInfoRowDisplayItem(
            stableKey = row.osInfoRowStableKey(),
            label = topInfoDisplayLabel(context, row.key),
            value = topInfoDisplayValue(context, row),
        )
    }
