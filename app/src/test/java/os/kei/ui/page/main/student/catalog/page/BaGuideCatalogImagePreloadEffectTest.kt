package os.kei.ui.page.main.student.catalog.page

import org.junit.Test
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListDerivedState
import kotlin.test.assertEquals

class BaGuideCatalogImagePreloadEffectTest {
    @Test
    fun `student tab uses active catalog entries and playback artwork`() {
        val urls =
            buildBaGuideCatalogImagePreloadUrls(
                activeTab = BaGuideCatalogPageTab.Student,
                catalogListDerivedStates =
                    mapOf(
                        BaGuideCatalogTab.Student to
                            BaGuideCatalogListDerivedState(
                                filteredEntries =
                                    listOf(
                                        catalogEntry("student-1"),
                                        catalogEntry("student-2"),
                                    ),
                            ),
                    ),
                studentBgmEntries = listOf(catalogEntry("bgm-student")),
                favoriteBgms = listOf(favorite("favorite-image")),
                artworkImageUrl = "artwork",
                playbackFavorite = favorite("playback-student"),
            )

        assertEquals(
            listOf("student-1", "student-2", "artwork", "playback-student"),
            urls,
        )
    }

    @Test
    fun `favorite bgm tab uses displayed favorites`() {
        val urls =
            buildBaGuideCatalogImagePreloadUrls(
                activeTab = BaGuideCatalogPageTab.Bgm,
                catalogListDerivedStates = emptyMap(),
                studentBgmEntries = listOf(catalogEntry("student-bgm")),
                favoriteBgms =
                    listOf(
                        favorite(studentImageUrl = "favorite-student"),
                        favorite(studentImageUrl = "", imageUrl = "fallback-image"),
                    ),
                artworkImageUrl = "",
                playbackFavorite = null,
            )

        assertEquals(
            listOf("favorite-student", "fallback-image"),
            urls,
        )
    }

    private fun catalogEntry(iconUrl: String): BaGuideCatalogEntry =
        BaGuideCatalogEntry(
            entryId = iconUrl.hashCode(),
            pid = 0,
            contentId = iconUrl.hashCode().toLong(),
            name = iconUrl,
            alias = "",
            aliasDisplay = "",
            iconUrl = iconUrl,
            type = 0,
            order = 0,
            createdAtSec = 0L,
            detailUrl = "",
            tab = BaGuideCatalogTab.Student,
        )

    private fun favorite(
        studentImageUrl: String,
        imageUrl: String = "",
    ): GuideBgmFavoriteItem =
        GuideBgmFavoriteItem(
            audioUrl = "audio-$studentImageUrl-$imageUrl",
            title = "",
            studentTitle = "",
            studentImageUrl = studentImageUrl,
            imageUrl = imageUrl,
            sourceUrl = "",
            note = "",
            favoritedAtMs = 1L,
        )
}
