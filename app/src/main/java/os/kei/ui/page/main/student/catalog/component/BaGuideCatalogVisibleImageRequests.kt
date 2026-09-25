package os.kei.ui.page.main.student.catalog.component

import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import kotlin.math.max

private const val BA_GUIDE_CATALOG_VISIBLE_IMAGE_MIN_BEFORE = 12
private const val BA_GUIDE_CATALOG_VISIBLE_IMAGE_MIN_AFTER = 18
private const val BA_GUIDE_CATALOG_VISIBLE_IMAGE_MAX_BEFORE = 40
private const val BA_GUIDE_CATALOG_VISIBLE_IMAGE_MAX_AFTER = 56
private const val BA_GUIDE_CATALOG_VISIBLE_IMAGE_REQUEST_LIMIT = 64

internal fun buildBaGuideCatalogVisibleImageRequestUrls(
    displayedEntries: List<BaGuideCatalogEntry>,
    visibleItemIndices: List<Int>,
    entryStartIndex: Int,
    beforeCount: Int = baGuideCatalogVisibleImagePreloadBeforeCount(visibleItemIndices.size),
    afterCount: Int = baGuideCatalogVisibleImagePreloadAfterCount(visibleItemIndices.size),
    limit: Int = BA_GUIDE_CATALOG_VISIBLE_IMAGE_REQUEST_LIMIT,
): List<String> =
    collectBaGuideVisibleWindow(
        visibleEntryIndices =
            buildBaGuideVisibleEntryIndices(
                displayedEntryCount = displayedEntries.size,
                visibleItemIndices = visibleItemIndices,
                entryStartIndex = entryStartIndex,
            ),
        beforeCount = beforeCount,
        afterCount = afterCount,
        limit = limit,
    ) { index -> displayedEntries.getOrNull(index)?.iconUrl?.trim()?.ifBlank { null } }

/**
 * The keys to request around what is on screen, most urgent first: every visible entry in order, then one
 * step further out at a time, before and after alternately, up to [beforeCount] and [afterCount] steps.
 *
 * [keyOf] returns null for an entry that has nothing to request, and equal keys count once, so [limit]
 * caps distinct requests rather than entries.
 */
internal fun <T : Any> collectBaGuideVisibleWindow(
    visibleEntryIndices: List<Int>,
    beforeCount: Int,
    afterCount: Int,
    limit: Int,
    keyOf: (Int) -> T?,
): List<T> {
    if (visibleEntryIndices.isEmpty() || limit <= 0) return emptyList()
    val keys = linkedSetOf<T>()

    fun add(index: Int) {
        if (keys.size < limit) keyOf(index)?.let(keys::add)
    }

    visibleEntryIndices.forEach(::add)
    val firstVisible = visibleEntryIndices.first()
    val lastVisible = visibleEntryIndices.last()
    val safeBefore = beforeCount.coerceAtLeast(0)
    val safeAfter = afterCount.coerceAtLeast(0)
    for (distance in 1..max(safeBefore, safeAfter)) {
        if (keys.size >= limit) break
        if (distance <= safeBefore) add(firstVisible - distance)
        if (distance <= safeAfter) add(lastVisible + distance)
    }
    return keys.toList()
}

internal fun buildBaGuideVisibleEntryIndices(
    displayedEntryCount: Int,
    visibleItemIndices: List<Int>,
    entryStartIndex: Int,
): List<Int> {
    if (displayedEntryCount <= 0 || visibleItemIndices.isEmpty()) return emptyList()
    val included = BooleanArray(displayedEntryCount)
    var firstIncluded = displayedEntryCount
    var lastIncluded = -1
    visibleItemIndices.forEach { itemIndex ->
        val entryIndex = itemIndex - entryStartIndex
        if (entryIndex in 0 until displayedEntryCount && !included[entryIndex]) {
            included[entryIndex] = true
            if (entryIndex < firstIncluded) firstIncluded = entryIndex
            if (entryIndex > lastIncluded) lastIncluded = entryIndex
        }
    }
    if (lastIncluded < 0) return emptyList()
    val indices = ArrayList<Int>(visibleItemIndices.size.coerceAtMost(lastIncluded - firstIncluded + 1))
    for (entryIndex in firstIncluded..lastIncluded) {
        if (included[entryIndex]) {
            indices += entryIndex
        }
    }
    return indices
}

internal fun baGuideCatalogVisibleImagePreloadBeforeCount(viewportItemCount: Int): Int =
    max(BA_GUIDE_CATALOG_VISIBLE_IMAGE_MIN_BEFORE, viewportItemCount.coerceAtLeast(1) * 2)
        .coerceAtMost(BA_GUIDE_CATALOG_VISIBLE_IMAGE_MAX_BEFORE)

internal fun baGuideCatalogVisibleImagePreloadAfterCount(viewportItemCount: Int): Int =
    max(BA_GUIDE_CATALOG_VISIBLE_IMAGE_MIN_AFTER, viewportItemCount.coerceAtLeast(1) * 3)
        .coerceAtMost(BA_GUIDE_CATALOG_VISIBLE_IMAGE_MAX_AFTER)
