package os.kei.ui.page.main.student.catalog.state

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import kotlin.math.max

internal const val CATALOG_BATCH_SIZE = 20
private const val CATALOG_LOAD_MORE_THRESHOLD = 10
internal const val CATALOG_RELEASE_DATE_FETCH_LIMIT_PER_PASS = 12

@Stable
internal data class BaGuideCatalogTabListState(
    val listState: LazyListState,
    val secondaryListState: LazyListState,
    val filteredEntries: List<BaGuideCatalogEntry>,
    val displayedEntries: List<BaGuideCatalogEntry>,
    val hasMoreEntries: Boolean,
) {
    /** Both lanes when the list is split, one when it is not — see [laneStates]. */
    fun laneStates(columnCount: Int): List<LazyListState> =
        if (columnCount >= 2) listOf(listState, secondaryListState) else listOf(listState)
}

@Composable
internal fun rememberBaGuideCatalogTabListState(
    tab: BaGuideCatalogTab,
    filteredEntries: List<BaGuideCatalogEntry>,
    loading: Boolean,
    isPageActive: Boolean,
): BaGuideCatalogTabListState {
    // Use remember (not rememberLazyListState / rememberSaveable) to avoid
    // restoring a stale scroll position on Activity recreate. MainLoadedPager
    // composes ALL pages simultaneously via repeat(), so each tab's
    // LazyListState instance lives for the entire Activity session — scroll
    // position is naturally preserved across tab switches.
    val listState = remember(tab) { LazyListState() }
    // The second lane's state lives here rather than in the leaf, because paging is what has to see both:
    // each lane holds every other entry, so either one reaching its end means the list needs more.
    val secondaryListState = remember(tab) { LazyListState() }
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    // Use remember (not rememberSaveable) so it resets on Activity recreate.
    var loadedCount by remember(tab) { mutableIntStateOf(0) }
    // derivedStateOf computes the effective visible count synchronously during
    // composition — no LaunchedEffect delay. On first entry when filteredEntries
    // is empty, this returns 0. When data arrives, it immediately returns
    // minOf(loadedCount, filteredEntries.size), which starts at CATALOG_BATCH_SIZE
    // (since loadedCount starts at 0 and the load-more effect sets it on data arrival).
    val visibleCount by remember(filteredEntries, loadedCount) {
        derivedStateOf {
            if (loadedCount <= 0) {
                minOf(filteredEntries.size, CATALOG_BATCH_SIZE)
            } else {
                minOf(loadedCount, filteredEntries.size)
            }
        }
    }
    // Set loadedCount when data first arrives so subsequent recompositions
    // don't re-derive from loadedCount=0.
    LaunchedEffect(filteredEntries.size) {
        if (filteredEntries.isNotEmpty() && loadedCount <= 0) {
            loadedCount = minOf(filteredEntries.size, CATALOG_BATCH_SIZE)
        }
    }
    LaunchedEffect(
        isPageActive,
        listState,
        secondaryListState,
        filteredEntries.size,
        loading,
        snapshotFlowManager,
    ) {
        if (!isPageActive) return@LaunchedEffect
        snapshotFlowManager
            .snapshotFlow {
                // Both lanes, and each judged against its own end. A lane holds every other entry, so its
                // own `totalItemsCount` is the right yardstick for "nearly through it" -- and when there is
                // only one lane the second is empty, contributes nothing, and this is the old condition.
                val primary = listState.layoutInfo
                val secondary = secondaryListState.layoutInfo
                BaGuideCatalogLoadMoreSignal(
                    nearEnd =
                        primary.reachedLoadMoreTrigger() || secondary.reachedLoadMoreTrigger(),
                    // Items on screen across both lanes: the batch is measured in entries, and two lanes
                    // show twice as many for the same amount of scrolling.
                    viewportItems =
                        (primary.visibleItemsInfo.size + secondary.visibleItemsInfo.size)
                            .coerceAtLeast(6),
                )
            }.distinctUntilChanged()
            .collect { signal ->
                if (loading) return@collect
                if (loadedCount >= filteredEntries.size) return@collect
                if (!signal.nearEnd) return@collect

                val appendBatch =
                    max(CATALOG_BATCH_SIZE, signal.viewportItems * 3)
                        .coerceAtMost(CATALOG_BATCH_SIZE * 3)
                loadedCount = minOf(loadedCount + appendBatch, filteredEntries.size)
            }
    }

    // derivedStateOf auto-caps at filteredEntries.size, so displayedEntries
    // never exceeds available data even if visibleCount is stale.
    val displayedEntries by remember(filteredEntries, visibleCount) {
        derivedStateOf {
            val count = minOf(visibleCount, filteredEntries.size)
            if (count >= filteredEntries.size) {
                filteredEntries
            } else {
                filteredEntries.subList(0, count)
            }
        }
    }

    return remember(listState, secondaryListState, filteredEntries, displayedEntries) {
        BaGuideCatalogTabListState(
            listState = listState,
            secondaryListState = secondaryListState,
            filteredEntries = filteredEntries,
            displayedEntries = displayedEntries,
            hasMoreEntries = visibleCount < filteredEntries.size,
        )
    }
}

private data class BaGuideCatalogLoadMoreSignal(
    val nearEnd: Boolean,
    val viewportItems: Int,
)

/**
 * Whether this lane is within [CATALOG_LOAD_MORE_THRESHOLD] of its own last item.
 *
 * An empty lane never triggers: with one column the second lane has no items, and a `lastVisible` of -1
 * against a trigger index of 0 would otherwise read as "at the end" and append forever.
 */
private fun LazyListLayoutInfo.reachedLoadMoreTrigger(): Boolean {
    if (totalItemsCount <= 0) return false
    val lastVisible = visibleItemsInfo.lastOrNull()?.index ?: return false
    return lastVisible >= (totalItemsCount - 1 - CATALOG_LOAD_MORE_THRESHOLD).coerceAtLeast(0)
}
