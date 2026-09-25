package os.kei.ui.page.main.student.catalog.component

import org.junit.Test
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.testCatalogEntry
import kotlin.test.assertEquals

class BaGuideStudentBgmVisibleRequestsTest {
    @Test
    fun `visible prewarm prioritizes current rows then nearby rows`() {
        val entries = (0 until 12).map { index -> catalogEntry(index) }

        val selected =
            buildBaGuideStudentBgmVisiblePrewarmEntries(
                displayedEntries = entries,
                visibleItemIndices = listOf(4, 5),
                entryStartIndex = 1,
                beforeCount = 2,
                afterCount = 3,
                limit = 7,
            )

        assertEquals(
            listOf(3L, 4L, 2L, 5L, 1L, 6L, 7L),
            selected.map { entry -> entry.contentId },
        )
    }

    @Test
    fun `visible prewarm ignores header and out of range items`() {
        val entries = (0 until 3).map { index -> catalogEntry(index) }

        val selected =
            buildBaGuideStudentBgmVisiblePrewarmEntries(
                displayedEntries = entries,
                visibleItemIndices = listOf(0, 1, 2, 9),
                entryStartIndex = 1,
                beforeCount = 1,
                afterCount = 1,
                limit = 8,
            )

        assertEquals(
            listOf(0L, 1L, 2L),
            selected.map { entry -> entry.contentId },
        )
    }

    @Test
    fun `visible range prewarm matches contiguous visible indices`() {
        val entries = (0 until 12).map { index -> catalogEntry(index) }

        val fromIndices =
            buildBaGuideStudentBgmVisiblePrewarmEntries(
                displayedEntries = entries,
                visibleItemIndices = listOf(4, 5),
                entryStartIndex = 1,
                beforeCount = 2,
                afterCount = 3,
                limit = 7,
            )
        val fromRange =
            buildBaGuideStudentBgmVisiblePrewarmEntries(
                displayedEntries = entries,
                visibleItemRange =
                    BaGuideVisibleItemRange(
                        firstItemIndex = 4,
                        lastItemIndex = 5,
                        visibleItemCount = 2,
                    ),
                entryStartIndex = 1,
                beforeCount = 2,
                afterCount = 3,
                limit = 7,
            )

        assertEquals(
            fromIndices.map { entry -> entry.contentId },
            fromRange.map { entry -> entry.contentId },
        )
    }

    private fun catalogEntry(index: Int): BaGuideCatalogEntry =
        testCatalogEntry(contentId = index.toLong(), iconUrl = "student-$index")
}
