package os.kei.ui.page.main.student.catalog.state

import org.junit.Test
import os.kei.ui.page.main.student.BaGuideGalleryItem
import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.testCatalogEntry
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BaGuideMemoryLobbyResolveRepositoryTest {
    @Test
    fun `resolved item keeps memory lobby images videos and unlock level`() {
        val entry = catalogEntry()
        val info =
            studentGuideInfo(
                galleryItems =
                    listOf(
                        BaGuideGalleryItem(
                            title = "回忆大厅",
                            imageUrl = "https://example.com/lobby.png",
                            mediaUrl = "https://example.com/lobby.png",
                            memoryUnlockLevel = "5",
                        ),
                        BaGuideGalleryItem(
                            title = "回忆大厅视频",
                            imageUrl = "https://example.com/lobby-poster.png",
                            mediaType = "video",
                            mediaUrl = "https://example.com/lobby.mp4",
                            memoryUnlockLevel = "5",
                        ),
                        BaGuideGalleryItem(
                            title = "官方介绍",
                            imageUrl = "https://example.com/intro.png",
                            mediaUrl = "https://example.com/intro.png",
                        ),
                    ),
            )

        val result = info.toMemoryLobbyResolvedItem(entry = entry, fromCache = true)

        assertNotNull(result)
        assertEquals("Demo", result.studentTitle)
        assertEquals("5", result.memoryUnlockLevel)
        assertEquals(true, result.fromCache)
        assertEquals(
            listOf("https://example.com/lobby.png", "https://example.com/lobby.mp4"),
            result.galleryItems.map { item -> item.mediaUrl },
        )
    }

    @Test
    fun `missing memory lobby media returns null`() {
        val info =
            studentGuideInfo(
                galleryItems =
                    listOf(
                        BaGuideGalleryItem(
                            title = "官方介绍",
                            imageUrl = "https://example.com/intro.png",
                            mediaUrl = "https://example.com/intro.png",
                        ),
                    ),
            )

        val result = info.toMemoryLobbyResolvedItem(entry = catalogEntry(), fromCache = false)

        assertNull(result)
    }

    @Test
    fun `video only memory lobby keeps unlock level from profile rows`() {
        val info =
            studentGuideInfo(
                galleryItems =
                    listOf(
                        BaGuideGalleryItem(
                            title = "回忆大厅视频",
                            imageUrl = "https://example.com/lobby-preview.png",
                            mediaType = "video",
                            mediaUrl = "https://example.com/lobby.mp4",
                        ),
                    ),
                profileRows =
                    listOf(
                        BaGuideRow(key = "回忆大厅解锁等级", value = "羁绊等级 8"),
                    ),
            )

        val result = info.toMemoryLobbyResolvedItem(entry = catalogEntry(), fromCache = false)

        assertNotNull(result)
        assertEquals("8", result.memoryUnlockLevel)
        assertEquals(
            listOf("https://example.com/lobby.mp4"),
            result.galleryItems.map { item -> item.mediaUrl },
        )
    }

    private fun catalogEntry(): BaGuideCatalogEntry =
        testCatalogEntry(name = "Demo", iconUrl = "https://example.com/icon.png", order = 0, detailUrl = "https://www.gamekee.com/ba/1.html")

    private fun studentGuideInfo(
        galleryItems: List<BaGuideGalleryItem>,
        profileRows: List<BaGuideRow> = emptyList(),
    ): BaStudentGuideInfo =
        BaStudentGuideInfo(
            sourceUrl = "https://www.gamekee.com/ba/1.html",
            title = "Demo",
            subtitle = "",
            description = "",
            imageUrl = "https://example.com/profile.png",
            summary = "",
            stats = emptyList(),
            profileRows = profileRows,
            galleryItems = galleryItems,
            syncedAtMs = 1_000L,
        )
}
