@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import os.kei.ui.page.main.widget.chrome.appPageAlternatingLanes

/**
 * Splits an ordered catalogue list into [columnCount] lanes by alternating entries.
 *
 * Alternating, not halving, and the difference is the whole point. Two lanes side by side scroll
 * independently — that is what they are for — so a "first half / second half" split would put entry 138
 * at the top of the right lane and make browsing the list nonsense. Alternating means each lane is still
 * the list in order, and while the lanes sit level with each other, reading across a row gives the list's
 * order exactly: entry 0, entry 1, entry 2, entry 3. Scroll one lane and the pairing drifts, but neither
 * lane stops being sorted, which is the property that survives the drift.
 *
 * The same rule the card pages use — see `appPageAlternatingLanes`.
 */
internal fun <T> baGuideCatalogEntryLanes(
    entries: List<T>,
    columnCount: Int,
): List<List<T>> = appPageAlternatingLanes(entries, columnCount)

/**
 * Where a lane's entry sits in the flat list.
 *
 * Falls straight out of the alternating rule, which is the reason to alternate rather than carry an index
 * map around: lane 1's third entry is flat entry 5, and that is arithmetic, not bookkeeping.
 */
internal fun baGuideCatalogLaneEntryIndex(
    laneIndex: Int,
    laneEntryIndex: Int,
    columnCount: Int,
): Int = laneEntryIndex * columnCount.coerceAtLeast(1) + laneIndex

/**
 * Flat entry indices for what every lane currently has on screen.
 *
 * [laneVisibleItemIndices] is one list of visible *item* indices per lane, and [laneEntryStartIndices] is
 * how many items precede that lane's first entry. Per lane, not one number, because a status card sits in
 * the leading lane only — one error, not one per column — so the lanes genuinely have different offsets.
 * The result feeds the preload builders' index overloads directly with an `entryStartIndex` of zero,
 * because the offset has already been taken out here.
 *
 * Both lanes together, because preloading follows the screen, and with two lanes the screen is both.
 */
internal fun baGuideCatalogVisibleLaneEntryIndices(
    laneVisibleItemIndices: List<List<Int>>,
    laneEntryStartIndices: List<Int>,
    columnCount: Int,
    entryCount: Int,
): List<Int> {
    if (entryCount <= 0) return emptyList()
    val columns = columnCount.coerceAtLeast(1)
    val indices = sortedSetOf<Int>()
    laneVisibleItemIndices.forEachIndexed { lane, itemIndices ->
        if (lane >= columns) return@forEachIndexed
        val entryStartIndex = laneEntryStartIndices.getOrElse(lane) { 0 }
        itemIndices.forEach { itemIndex ->
            val laneEntryIndex = itemIndex - entryStartIndex
            if (laneEntryIndex < 0) return@forEach
            val entryIndex = baGuideCatalogLaneEntryIndex(lane, laneEntryIndex, columns)
            if (entryIndex < entryCount) {
                indices += entryIndex
            }
        }
    }
    return indices.toList()
}

/**
 * The catalogue lists' body: one lane, or [primary] and [secondary] side by side and scrolling apart.
 *
 * With one column this is the single [LazyColumn] these tabs have always had, with the same content
 * padding, so nothing about a phone moves. With two, the page's outer padding moves onto the row — the
 * lanes now have an inside edge that is not a page edge, and [horizontalGap] is that edge — while each
 * lane keeps the vertical insets, because each lane scrolls under its own chrome.
 */
@Composable
internal fun BaGuideCatalogLaneLists(
    laneStates: List<LazyListState>,
    startPadding: Dp,
    endPadding: Dp,
    topPadding: Dp,
    bottomPadding: Dp,
    horizontalGap: Dp,
    verticalGap: Dp,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    lanes: List<LazyListScope.() -> Unit>,
) {
    if (lanes.size <= 1) {
        LazyColumn(
            state = laneStates.first(),
            userScrollEnabled = userScrollEnabled,
            modifier = modifier,
            contentPadding =
                PaddingValues(
                    top = topPadding,
                    bottom = bottomPadding,
                    start = startPadding,
                    end = endPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(verticalGap),
            content = lanes.first(),
        )
        return
    }
    Row(
        modifier = modifier.padding(start = startPadding, end = endPadding),
        horizontalArrangement = Arrangement.spacedBy(horizontalGap),
    ) {
        lanes.forEachIndexed { lane, content ->
            LazyColumn(
                state = laneStates[lane],
                userScrollEnabled = userScrollEnabled,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(verticalGap),
                content = content,
            )
        }
    }
}

/** Every lane's currently visible item indices, for the preload effects to resolve together. */
internal fun List<LazyListState>.baGuideCatalogLaneVisibleItemIndices(): List<List<Int>> =
    map { state -> state.layoutInfo.visibleItemsInfo.map { item -> item.index } }

/** True when at least one lane still has somewhere to go, so the chrome must not settle. */
internal fun List<LazyListState>.baGuideCatalogAnyLaneCanScrollBackward(): Boolean =
    any { state -> state.canScrollBackward }

internal fun List<LazyListState>.baGuideCatalogAnyLaneCanScrollForward(): Boolean =
    any { state -> state.canScrollForward }
