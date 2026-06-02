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
): List<String> {
    if (displayedEntries.isEmpty() || visibleItemIndices.isEmpty() || limit <= 0) return emptyList()
    val visibleEntryIndices =
        visibleItemIndices
            .map { itemIndex -> itemIndex - entryStartIndex }
            .filter { entryIndex -> entryIndex in displayedEntries.indices }
            .distinct()
            .sorted()
    if (visibleEntryIndices.isEmpty()) return emptyList()

    val urls = linkedSetOf<String>()

    fun addEntry(index: Int) {
        if (urls.size >= limit) return
        val url = displayedEntries.getOrNull(index)?.iconUrl?.trim().orEmpty()
        if (url.isNotBlank()) {
            urls.add(url)
        }
    }

    visibleEntryIndices.forEach(::addEntry)

    val firstVisibleEntryIndex = visibleEntryIndices.first()
    val lastVisibleEntryIndex = visibleEntryIndices.last()
    val safeBeforeCount = beforeCount.coerceAtLeast(0)
    val safeAfterCount = afterCount.coerceAtLeast(0)
    val maxDistance = max(safeBeforeCount, safeAfterCount)
    for (distance in 1..maxDistance) {
        if (urls.size >= limit) break
        if (distance <= safeBeforeCount) {
            addEntry(firstVisibleEntryIndex - distance)
        }
        if (urls.size >= limit) break
        if (distance <= safeAfterCount) {
            addEntry(lastVisibleEntryIndex + distance)
        }
    }
    return urls.toList()
}

internal fun baGuideCatalogVisibleImagePreloadBeforeCount(viewportItemCount: Int): Int =
    max(BA_GUIDE_CATALOG_VISIBLE_IMAGE_MIN_BEFORE, viewportItemCount.coerceAtLeast(1) * 2)
        .coerceAtMost(BA_GUIDE_CATALOG_VISIBLE_IMAGE_MAX_BEFORE)

internal fun baGuideCatalogVisibleImagePreloadAfterCount(viewportItemCount: Int): Int =
    max(BA_GUIDE_CATALOG_VISIBLE_IMAGE_MIN_AFTER, viewportItemCount.coerceAtLeast(1) * 3)
        .coerceAtMost(BA_GUIDE_CATALOG_VISIBLE_IMAGE_MAX_AFTER)
