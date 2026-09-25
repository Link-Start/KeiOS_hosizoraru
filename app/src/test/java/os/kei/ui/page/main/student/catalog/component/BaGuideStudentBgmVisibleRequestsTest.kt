package os.kei.ui.page.main.student.catalog.component

import org.junit.Test
import os.kei.ui.page.main.student.catalog.testCatalogEntry
import kotlin.test.assertEquals

/**
 * The window order and the mapping from lazy items to entries are shared with the icon requests and
 * tested once, in BaGuideCatalogVisibleImageRequestsTest. What differs here is what counts as one
 * request: an entry, not an icon URL.
 */
class BaGuideStudentBgmVisibleRequestsTest {
    @Test
    fun `prewarm counts entries, so blank and shared icon urls are still prewarmed`() {
        val entries =
            listOf("shared", "", "shared", "own", "").mapIndexed { index, iconUrl ->
                testCatalogEntry(contentId = index.toLong(), iconUrl = iconUrl)
            }

        val selected =
            buildBaGuideStudentBgmVisiblePrewarmEntries(
                displayedEntries = entries,
                visibleItemIndices = listOf(1, 2),
                entryStartIndex = 0,
                beforeCount = 1,
                afterCount = 2,
                limit = 8,
            )

        assertEquals(listOf(1L, 2L, 0L, 3L, 4L), selected.map { entry -> entry.contentId })
    }
}
