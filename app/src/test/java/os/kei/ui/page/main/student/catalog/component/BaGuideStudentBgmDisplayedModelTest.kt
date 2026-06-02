package os.kei.ui.page.main.student.catalog.component

import org.junit.Test
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.state.favoriteStudentBgmEntryContentIds
import os.kei.ui.page.main.student.catalog.state.filterAndSortStudentBgmEntries
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BaGuideStudentBgmDisplayedModelTest {
    @Test
    fun `favorite content ids resolve from normalized source urls`() {
        val entries =
            listOf(
                catalogEntry(contentId = 1L, name = "Alice", order = 1),
                catalogEntry(contentId = 2L, name = "Bob", order = 2),
            )
        val favorites =
            mapOf(
                "https://www.gamekee.com/ba/2" to favorite(sourceUrl = "https://www.gamekee.com/ba/2"),
            )

        val ids = favoriteStudentBgmEntryContentIds(entries, favorites)

        assertEquals(setOf(2L), ids)
    }

    @Test
    fun `filter and sort keeps base order when favorite set is empty`() {
        val entries =
            listOf(
                catalogEntry(contentId = 1L, name = "Alice", order = 1),
                catalogEntry(contentId = 2L, name = "Bob", order = 2),
                catalogEntry(contentId = 3L, name = "Carol", order = 3),
            )

        val result =
            filterAndSortStudentBgmEntries(
                entries = entries,
                searchQuery = "",
                favoriteContentIds = emptySet(),
            )

        assertEquals(listOf(1L, 2L, 3L), result.map { it.contentId })
    }

    @Test
    fun `filter and sort places favorites first then entry order`() {
        val entries =
            listOf(
                catalogEntry(contentId = 1L, name = "Alice", order = 1),
                catalogEntry(contentId = 2L, name = "Bob", order = 2),
                catalogEntry(contentId = 3L, name = "Carol", order = 3),
            )

        val result =
            filterAndSortStudentBgmEntries(
                entries = entries,
                searchQuery = "",
                favoriteContentIds = setOf(3L),
            )

        assertEquals(listOf(3L, 1L, 2L), result.map { it.contentId })
    }

    @Test
    fun `displayed model builds row state and favorite flags once`() {
        val entries =
            listOf(
                catalogEntry(contentId = 1L, name = "Alice", order = 1),
                catalogEntry(contentId = 2L, name = "Bob", order = 2),
            )
        val favorite = favorite(sourceUrl = "https://www.gamekee.com/ba/1")
        val readyFavorite = favorite(sourceUrl = "https://www.gamekee.com/ba/2")

        val model =
            buildBaGuideStudentBgmDisplayedModel(
                displayedEntries = entries,
                lookupStates =
                    mapOf(
                        2L to
                            BaGuideStudentBgmLookupState.Ready(
                                BaGuideStudentBgmResolvedItem(
                                    favorite = readyFavorite,
                                    fromCache = true,
                                ),
                            ),
                    ),
                favoriteByNormalizedSourceUrl =
                    mapOf(
                        "https://www.gamekee.com/ba/1" to favorite,
                    ),
                favoriteAudioUrls = setOf(favorite.audioUrl),
            )

        assertEquals(listOf(1L, 2L), model.contentIds)
        assertEquals(2, model.rows.size)
        assertIs<BaGuideStudentBgmLookupState.Ready>(model.rows[0].displayState)
        assertEquals(favorite.audioUrl, model.rows[0].readyAudioUrl)
        assertEquals(true, model.rows[0].favorite)
        assertEquals(readyFavorite.audioUrl, model.rows[1].readyAudioUrl)
        assertEquals(false, model.rows[1].favorite)
        assertEquals(listOf(favorite.audioUrl, readyFavorite.audioUrl), model.playableFavorites.map { it.audioUrl })
        assertEquals(2, model.resolvedCount)
    }

    @Test
    fun `displayed model fills favorite fallback artwork from entry`() {
        val entry =
            catalogEntry(
                contentId = 8L,
                name = "Aris",
                order = 1,
                iconUrl = "https://example.com/aris.png",
            )
        val favorite =
            favorite(sourceUrl = entry.detailUrl)
                .copy(studentTitle = "", studentImageUrl = "", imageUrl = "")

        val model =
            buildBaGuideStudentBgmDisplayedModel(
                displayedEntries = listOf(entry),
                lookupStates = emptyMap(),
                favoriteByNormalizedSourceUrl = mapOf(entry.detailUrl to favorite),
                favoriteAudioUrls = setOf(favorite.audioUrl),
            )

        val playableFavorite = model.playableFavorites.single()
        assertEquals(entry.name, playableFavorite.studentTitle)
        assertEquals(entry.iconUrl, playableFavorite.studentImageUrl)
        assertEquals(entry.iconUrl, playableFavorite.imageUrl)
        assertEquals(playableFavorite.audioUrl, model.rows.single().readyAudioUrl)
    }

    private fun catalogEntry(
        contentId: Long,
        name: String,
        order: Int,
        iconUrl: String = "",
    ): BaGuideCatalogEntry =
        BaGuideCatalogEntry(
            entryId = contentId.toInt(),
            pid = 49443,
            contentId = contentId,
            name = name,
            alias = "",
            aliasDisplay = "",
            iconUrl = iconUrl,
            type = 0,
            order = order,
            createdAtSec = 0L,
            detailUrl = "https://www.gamekee.com/ba/$contentId",
            tab = BaGuideCatalogTab.Student,
        )

    private fun favorite(sourceUrl: String): GuideBgmFavoriteItem =
        GuideBgmFavoriteItem(
            audioUrl = "$sourceUrl/audio.mp3",
            title = "BGM",
            studentTitle = "Demo",
            studentImageUrl = "",
            imageUrl = "",
            sourceUrl = sourceUrl,
            note = "",
            favoritedAtMs = 0L,
        )
}
