package os.kei.ui.page.main.student.catalog.component

import org.junit.Test
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import kotlin.test.assertEquals

class BaGuideStudentBgmDisplayedModelTest {
    @Test
    fun `favorite content ids resolve from normalized source urls`() {
        val entries = listOf(
            catalogEntry(contentId = 1L, name = "Alice", order = 1),
            catalogEntry(contentId = 2L, name = "Bob", order = 2)
        )
        val favorites = mapOf(
            "https://www.gamekee.com/ba/2" to favorite(sourceUrl = "https://www.gamekee.com/ba/2")
        )

        val ids = favoriteStudentBgmEntryContentIds(entries, favorites)

        assertEquals(setOf(2L), ids)
    }

    @Test
    fun `filter and sort keeps base order when favorite set is empty`() {
        val entries = listOf(
            catalogEntry(contentId = 1L, name = "Alice", order = 1),
            catalogEntry(contentId = 2L, name = "Bob", order = 2),
            catalogEntry(contentId = 3L, name = "Carol", order = 3)
        )

        val result = filterAndSortStudentBgmEntries(
            entries = entries,
            searchQuery = "",
            favoriteContentIds = emptySet()
        )

        assertEquals(listOf(1L, 2L, 3L), result.map { it.contentId })
    }

    @Test
    fun `filter and sort places favorites first then entry order`() {
        val entries = listOf(
            catalogEntry(contentId = 1L, name = "Alice", order = 1),
            catalogEntry(contentId = 2L, name = "Bob", order = 2),
            catalogEntry(contentId = 3L, name = "Carol", order = 3)
        )

        val result = filterAndSortStudentBgmEntries(
            entries = entries,
            searchQuery = "",
            favoriteContentIds = setOf(3L)
        )

        assertEquals(listOf(3L, 1L, 2L), result.map { it.contentId })
    }

    private fun catalogEntry(
        contentId: Long,
        name: String,
        order: Int
    ): BaGuideCatalogEntry {
        return BaGuideCatalogEntry(
            entryId = contentId.toInt(),
            pid = 49443,
            contentId = contentId,
            name = name,
            alias = "",
            aliasDisplay = "",
            iconUrl = "",
            type = 0,
            order = order,
            createdAtSec = 0L,
            detailUrl = "https://www.gamekee.com/ba/$contentId",
            tab = BaGuideCatalogTab.Student
        )
    }

    private fun favorite(sourceUrl: String): GuideBgmFavoriteItem {
        return GuideBgmFavoriteItem(
            audioUrl = "$sourceUrl/audio.mp3",
            title = "BGM",
            studentTitle = "Demo",
            studentImageUrl = "",
            imageUrl = "",
            sourceUrl = sourceUrl,
            note = "",
            favoritedAtMs = 0L
        )
    }
}
