package os.kei.ui.page.main.student.page.support

import org.junit.Test
import os.kei.ui.page.main.student.BaGuideGalleryItem
import os.kei.ui.page.main.student.BaStudentGuideInfo
import kotlin.test.assertEquals

class BaStudentGuideMediaSupportTest {
    @Test
    fun `static image prefetch keeps order and stops at requested max`() {
        val info =
            guideInfo(
                imageUrl = "https://img.example.com/cover.png",
                galleryItems =
                    (0 until 20).map { index ->
                        BaGuideGalleryItem(
                            title = "影画$index",
                            imageUrl = "https://img.example.com/gallery-$index.png",
                            mediaUrl = "https://img.example.com/gallery-$index-large.webp",
                        )
                    },
            )

        val urls = collectGuideStaticImagePrefetchUrls(info, maxCount = 3)

        assertEquals(
            listOf(
                "https://img.example.com/cover.png",
                "https://img.example.com/gallery-0.png",
                "https://img.example.com/gallery-0-large.webp",
            ),
            urls,
        )
    }

    @Test
    fun `static image prefetch deduplicates and skips memory hall files`() {
        val info =
            guideInfo(
                imageUrl = "https://img.example.com/cover.png",
                galleryItems =
                    listOf(
                        BaGuideGalleryItem(
                            title = "回忆大厅文件 01",
                            imageUrl = "https://img.example.com/memory.png",
                            mediaUrl = "https://img.example.com/memory-large.webp",
                        ),
                        BaGuideGalleryItem(
                            title = "立绘",
                            imageUrl = "https://img.example.com/cover.png",
                            mediaUrl = "https://img.example.com/standing.webp",
                        ),
                        BaGuideGalleryItem(
                            title = "立绘 duplicate",
                            imageUrl = "https://img.example.com/cover.png",
                            mediaUrl = "https://img.example.com/standing.webp",
                        ),
                    ),
            )

        val urls = collectGuideStaticImagePrefetchUrls(info, maxCount = 8)

        assertEquals(
            listOf(
                "https://img.example.com/cover.png",
                "https://img.example.com/standing.webp",
            ),
            urls,
        )
    }

    private fun guideInfo(
        imageUrl: String,
        galleryItems: List<BaGuideGalleryItem>,
    ): BaStudentGuideInfo =
        BaStudentGuideInfo(
            sourceUrl = "https://www.gamekee.com/ba/161188.html",
            title = "凛",
            subtitle = "",
            description = "",
            imageUrl = imageUrl,
            summary = "",
            stats = emptyList(),
            galleryItems = galleryItems,
            syncedAtMs = 1L,
        )
}
