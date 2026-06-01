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

    @Test
    fun `student catalog warmup keeps first screen first then alternates student and npc`() {
        val studentEntries = (0 until 24).map { index -> catalogEntry("student-$index", BaGuideCatalogTab.Student) }
        val npcEntries = (0 until 3).map { index -> catalogEntry("npc-$index", BaGuideCatalogTab.NpcSatellite) }

        val urls =
            buildBaGuideCatalogImagePreloadUrls(
                activeTab = BaGuideCatalogPageTab.Student,
                catalogListDerivedStates =
                    mapOf(
                        BaGuideCatalogTab.Student to BaGuideCatalogListDerivedState(filteredEntries = studentEntries),
                        BaGuideCatalogTab.NpcSatellite to BaGuideCatalogListDerivedState(filteredEntries = npcEntries),
                    ),
                studentBgmEntries = emptyList(),
                favoriteBgms = emptyList(),
                artworkImageUrl = "",
                playbackFavorite = null,
            )

        assertEquals(
            (0 until 20).map { index -> "student-$index" } +
                listOf(
                    "student-20",
                    "npc-0",
                    "student-21",
                    "npc-1",
                    "student-22",
                    "npc-2",
                    "student-23",
                ),
            urls,
        )
    }

    private fun catalogEntry(
        iconUrl: String,
        tab: BaGuideCatalogTab = BaGuideCatalogTab.Student,
    ): BaGuideCatalogEntry =
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
            tab = tab,
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
