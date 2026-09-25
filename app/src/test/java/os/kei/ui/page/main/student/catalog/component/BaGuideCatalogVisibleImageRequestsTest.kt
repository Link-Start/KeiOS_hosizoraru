package os.kei.ui.page.main.student.catalog.component

import org.junit.Test
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.testCatalogEntry
import kotlin.test.assertEquals

class BaGuideCatalogVisibleImageRequestsTest {
    @Test
    fun `visible request prioritizes current rows then nearby rows`() {
        val entries = (0 until 16).map { index -> catalogEntry("student-$index") }

        val urls =
            buildBaGuideCatalogVisibleImageRequestUrls(
                displayedEntries = entries,
                visibleItemIndices = listOf(6, 7, 8),
                entryStartIndex = 2,
                beforeCount = 2,
                afterCount = 3,
                limit = 8,
            )

        assertEquals(
            listOf(
                "student-4",
                "student-5",
                "student-6",
                "student-3",
                "student-7",
                "student-2",
                "student-8",
                "student-9",
            ),
            urls,
        )
    }

    @Test
    fun `visible request ignores non entry lazy items`() {
        val entries = (0 until 3).map { index -> catalogEntry("student-$index") }

        val urls =
            buildBaGuideCatalogVisibleImageRequestUrls(
                displayedEntries = entries,
                visibleItemIndices = listOf(0, 1, 2, 3, 4),
                entryStartIndex = 1,
                beforeCount = 1,
                afterCount = 1,
                limit = 8,
            )

        assertEquals(
            listOf("student-0", "student-1", "student-2"),
            urls,
        )
    }

    @Test
    fun `visible request filters blank and duplicate urls`() {
        val entries =
            listOf(
                catalogEntry("student-0"),
                catalogEntry(" "),
                catalogEntry("student-0"),
                catalogEntry("student-3"),
            )

        val urls =
            buildBaGuideCatalogVisibleImageRequestUrls(
                displayedEntries = entries,
                visibleItemIndices = listOf(0, 1, 2),
                entryStartIndex = 0,
                beforeCount = 0,
                afterCount = 2,
                limit = 8,
            )

        assertEquals(listOf("student-0", "student-3"), urls)
    }

    @Test
    fun `visible entry indices are sorted and distinct`() {
        val indices =
            buildBaGuideVisibleEntryIndices(
                displayedEntryCount = 6,
                visibleItemIndices = listOf(7, 3, 4, 3, 9, 1),
                entryStartIndex = 2,
            )

        assertEquals(listOf(1, 2, 5), indices)
    }

    private fun catalogEntry(iconUrl: String): BaGuideCatalogEntry =
        testCatalogEntry(contentId = iconUrl.hashCode().toLong(), name = iconUrl, iconUrl = iconUrl, order = 0, detailUrl = "")
}
