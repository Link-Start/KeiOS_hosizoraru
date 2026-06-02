package os.kei.ui.page.main.student.catalog.component

import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import kotlin.math.max

private const val STUDENT_BGM_VISIBLE_PREWARM_MIN_BEFORE = 2
private const val STUDENT_BGM_VISIBLE_PREWARM_MIN_AFTER = 4
private const val STUDENT_BGM_VISIBLE_PREWARM_MAX_BEFORE = 8
private const val STUDENT_BGM_VISIBLE_PREWARM_MAX_AFTER = 12
private const val STUDENT_BGM_VISIBLE_PREWARM_LIMIT = 12

internal fun buildBaGuideStudentBgmVisiblePrewarmEntries(
    displayedEntries: List<BaGuideCatalogEntry>,
    visibleItemIndices: List<Int>,
    entryStartIndex: Int,
    beforeCount: Int = baGuideStudentBgmVisiblePrewarmBeforeCount(visibleItemIndices.size),
    afterCount: Int = baGuideStudentBgmVisiblePrewarmAfterCount(visibleItemIndices.size),
    limit: Int = STUDENT_BGM_VISIBLE_PREWARM_LIMIT,
): List<BaGuideCatalogEntry> {
    if (displayedEntries.isEmpty() || visibleItemIndices.isEmpty() || limit <= 0) return emptyList()
    val visibleEntryIndices =
        visibleItemIndices
            .map { itemIndex -> itemIndex - entryStartIndex }
            .filter { entryIndex -> entryIndex in displayedEntries.indices }
            .distinct()
            .sorted()
    if (visibleEntryIndices.isEmpty()) return emptyList()

    val indices = linkedSetOf<Int>()

    fun addEntryIndex(index: Int) {
        if (indices.size >= limit) return
        if (index in displayedEntries.indices) {
            indices += index
        }
    }

    visibleEntryIndices.forEach(::addEntryIndex)

    val firstVisibleEntryIndex = visibleEntryIndices.first()
    val lastVisibleEntryIndex = visibleEntryIndices.last()
    val safeBeforeCount = beforeCount.coerceAtLeast(0)
    val safeAfterCount = afterCount.coerceAtLeast(0)
    val maxDistance = max(safeBeforeCount, safeAfterCount)
    for (distance in 1..maxDistance) {
        if (indices.size >= limit) break
        if (distance <= safeBeforeCount) {
            addEntryIndex(firstVisibleEntryIndex - distance)
        }
        if (indices.size >= limit) break
        if (distance <= safeAfterCount) {
            addEntryIndex(lastVisibleEntryIndex + distance)
        }
    }
    return indices.map { index -> displayedEntries[index] }
}

internal fun baGuideStudentBgmVisiblePrewarmBeforeCount(viewportItemCount: Int): Int =
    max(STUDENT_BGM_VISIBLE_PREWARM_MIN_BEFORE, viewportItemCount.coerceAtLeast(1) / 2)
        .coerceAtMost(STUDENT_BGM_VISIBLE_PREWARM_MAX_BEFORE)

internal fun baGuideStudentBgmVisiblePrewarmAfterCount(viewportItemCount: Int): Int =
    max(STUDENT_BGM_VISIBLE_PREWARM_MIN_AFTER, viewportItemCount.coerceAtLeast(1))
        .coerceAtMost(STUDENT_BGM_VISIBLE_PREWARM_MAX_AFTER)
