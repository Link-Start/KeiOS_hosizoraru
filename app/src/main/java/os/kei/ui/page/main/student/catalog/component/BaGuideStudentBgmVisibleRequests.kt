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
): List<BaGuideCatalogEntry> =
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
    ) { index -> index.takeIf { it in displayedEntries.indices } }
        .map(displayedEntries::get)

internal fun baGuideStudentBgmVisiblePrewarmBeforeCount(viewportItemCount: Int): Int =
    max(STUDENT_BGM_VISIBLE_PREWARM_MIN_BEFORE, viewportItemCount.coerceAtLeast(1) / 2)
        .coerceAtMost(STUDENT_BGM_VISIBLE_PREWARM_MAX_BEFORE)

internal fun baGuideStudentBgmVisiblePrewarmAfterCount(viewportItemCount: Int): Int =
    max(STUDENT_BGM_VISIBLE_PREWARM_MIN_AFTER, viewportItemCount.coerceAtLeast(1))
        .coerceAtMost(STUDENT_BGM_VISIBLE_PREWARM_MAX_AFTER)
