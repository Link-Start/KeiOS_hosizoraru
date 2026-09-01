@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * One rendered row of a catalogue list: [entries] laid out across the panel, left to right.
 *
 * The catalogue tabs are the app's only *ordered* lists wide enough to want two columns, and that changes
 * what "two columns" has to mean. The card pages split into two lanes that scroll independently, because
 * their columns hold unrelated cards. Here there is one sequence — filtered, sorted, paged — and splitting
 * it into a first half and a second half that scroll apart would make browsing it nonsense. So this flows
 * row-major through the single list instead: entry 0 and entry 1 share a row, entry 2 opens the next, and
 * one scroll moves all of it in order.
 *
 * [startIndex] is where [entries] begins in the flat list, carried rather than recomputed because the
 * image-preloading effects need to turn a visible *item* index back into visible *entry* indices, and with
 * full-span rows in the mix that is no longer a fixed multiple.
 */
internal data class BaGuideCatalogEntryRow<T>(
    val entries: List<T>,
    val startIndex: Int,
    /**
     * Whether this row's single entry was *asked* for the whole width, as opposed to being the odd one left
     * over at the end of the list.
     *
     * Both are rows of one, and they must lay out differently: a full span takes the panel, while a trailing
     * leftover keeps a column's width so it lines up with the column above it. Without the distinction an
     * expanded lobby entry rendered at half width with a blank half beside it -- the exact thing giving it
     * its own row was supposed to prevent.
     */
    val fullSpan: Boolean = false,
)

/**
 * Groups [entries] into rows of [columnsPerRow], keeping the list's order.
 *
 * An entry that [isFullSpan] takes a row to itself. The Memorial Lobby is why: an expanded entry opens a
 * full lobby illustration taller than the viewport, and pairing that against a collapsed row would leave
 * most of a panel-height cell empty — the same dead space the two-column work exists to remove. Giving it
 * the whole row also gives the illustration the whole width, which is what it wants anyway.
 */
internal fun <T> baGuideCatalogEntryRows(
    entries: List<T>,
    columnsPerRow: Int,
    isFullSpan: (T) -> Boolean = { false },
): List<BaGuideCatalogEntryRow<T>> {
    val columns = columnsPerRow.coerceAtLeast(1)
    if (entries.isEmpty()) return emptyList()
    if (columns == 1) {
        return entries.mapIndexed { index, entry ->
            BaGuideCatalogEntryRow(entries = listOf(entry), startIndex = index)
        }
    }
    val rows = ArrayList<BaGuideCatalogEntryRow<T>>((entries.size + columns - 1) / columns)
    var index = 0
    while (index < entries.size) {
        if (isFullSpan(entries[index])) {
            rows +=
                BaGuideCatalogEntryRow(
                    entries = listOf(entries[index]),
                    startIndex = index,
                    fullSpan = true,
                )
            index++
            continue
        }
        // A full-span entry also *ends* the row being filled, so order stays strictly sequential: whatever
        // follows it starts a fresh row rather than back-filling the gap beside it.
        val end = (index until minOf(index + columns, entries.size)).takeWhile { !isFullSpan(entries[it]) }.last() + 1
        rows += BaGuideCatalogEntryRow(entries = entries.subList(index, end), startIndex = index)
        index = end
    }
    return rows
}

/**
 * The entry range a run of visible *items* covers, ready to hand to the image-preload builders.
 *
 * Returned in the same shape the builders already take, with entry indices in place of item indices, so
 * they are called with `entryStartIndex = 0` and need no arithmetic of their own. With one column and no
 * full spans this is the identity the builders used to compute for themselves.
 */
internal fun <T> baGuideCatalogVisibleEntryRange(
    rows: List<BaGuideCatalogEntryRow<T>>,
    visibleItemRange: BaGuideVisibleItemRange,
    entryStartIndex: Int,
): BaGuideVisibleItemRange {
    if (rows.isEmpty() || visibleItemRange.isEmpty) return EmptyVisibleRange
    val firstRow = (visibleItemRange.firstItemIndex - entryStartIndex).coerceAtLeast(0)
    val lastRow = (visibleItemRange.lastItemIndex - entryStartIndex).coerceAtMost(rows.lastIndex)
    if (firstRow > lastRow) return EmptyVisibleRange
    val firstEntry = rows[firstRow].startIndex
    val lastRowEntries = rows[lastRow]
    val lastEntry = lastRowEntries.startIndex + lastRowEntries.entries.size - 1
    return BaGuideVisibleItemRange(
        firstItemIndex = firstEntry,
        lastItemIndex = lastEntry,
        // Entries on screen, not rows: the preload window scales with how much is actually visible, which
        // in two columns is twice what the row count would say.
        visibleItemCount = lastEntry - firstEntry + 1,
    )
}

private val EmptyVisibleRange =
    BaGuideVisibleItemRange(
        firstItemIndex = -1,
        lastItemIndex = -1,
        visibleItemCount = 0,
    )

/**
 * Lays one [row] out, padding a short final row so its entry keeps a column's width rather than the panel's.
 *
 * A single-entry row is emitted without a [Row] wrapper at all, which is what keeps the phone — where every
 * row is single — laying out exactly as it did before any of this existed.
 */
@Composable
internal fun <T> BaGuideCatalogEntryRowLayout(
    row: BaGuideCatalogEntryRow<T>,
    columnsPerRow: Int,
    horizontalGap: Dp,
    content: @Composable (entry: T, entryIndex: Int) -> Unit,
) {
    val columns = columnsPerRow.coerceAtLeast(1)
    if (row.fullSpan || (row.entries.size == 1 && columns == 1)) {
        content(row.entries.first(), row.startIndex)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(horizontalGap),
        // Top, not stretch: a row's two entries are independent cards, and a taller one must not force the
        // other to grow to match it.
        verticalAlignment = Alignment.Top,
    ) {
        row.entries.forEachIndexed { column, entry ->
            Box(modifier = Modifier.weight(1f)) { content(entry, row.startIndex + column) }
        }
        repeat(columns - row.entries.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
