package os.kei.ui.page.main.student.catalog.page

import org.junit.Test
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListDerivedState
import os.kei.ui.page.main.student.catalog.testBgmFavorite
import os.kei.ui.page.main.student.catalog.testCatalogEntry
import kotlin.test.assertEquals

class BaGuideCatalogImagePreloadEffectTest {
    @Test
    fun `student tab uses active catalog entries and playback artwork`() {
        val urls =
            buildBaGuideCatalogImagePreloadUrls(
                activeTab = BaGuideCatalogPageTab.Student,
                activeCatalogTab = BaGuideCatalogTab.Student,
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
                activeCatalogTab = null,
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
    fun `student catalog preload stays within the active first batch`() {
        val studentEntries = (0 until 24).map { index -> catalogEntry("student-$index", BaGuideCatalogTab.Student) }
        val npcEntries = (0 until 3).map { index -> catalogEntry("npc-$index", BaGuideCatalogTab.NpcSatellite) }

        val urls =
            buildBaGuideCatalogImagePreloadUrls(
                activeTab = BaGuideCatalogPageTab.Student,
                activeCatalogTab = BaGuideCatalogTab.Student,
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
            (0 until 20).map { index -> "student-$index" },
            urls,
        )
    }

    @Test
    fun `student bgm preload stays within the first visible batch`() {
        val entries = (0 until 28).map { index -> catalogEntry("bgm-$index") }

        val urls =
            buildBaGuideCatalogImagePreloadUrls(
                activeTab = BaGuideCatalogPageTab.StudentBgm,
                activeCatalogTab = null,
                catalogListDerivedStates = emptyMap(),
                studentBgmEntries = entries,
                favoriteBgms = emptyList(),
                artworkImageUrl = "artwork",
                playbackFavorite = null,
            )

        assertEquals(
            (0 until 20).map { index -> "bgm-$index" } + "artwork",
            urls,
        )
    }

    private fun catalogEntry(
        iconUrl: String,
        tab: BaGuideCatalogTab = BaGuideCatalogTab.Student,
    ): BaGuideCatalogEntry =
        testCatalogEntry(contentId = iconUrl.hashCode().toLong(), name = iconUrl, tab = tab, iconUrl = iconUrl, order = 0, detailUrl = "")

    private fun favorite(
        studentImageUrl: String,
        imageUrl: String = "",
    ): GuideBgmFavoriteItem =
        testBgmFavorite(audioUrl = "audio-$studentImageUrl-$imageUrl", studentImageUrl = studentImageUrl, imageUrl = imageUrl)
}
