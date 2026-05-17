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

    @Test
    fun `static image prefetch returns empty list when max count is zero`() {
        val info =
            guideInfo(
                imageUrl = "https://img.example.com/cover.png",
                galleryItems =
                    listOf(
                        BaGuideGalleryItem(
                            title = "立绘",
                            imageUrl = "https://img.example.com/standing.png",
                            mediaUrl = "https://img.example.com/standing.webp",
                        ),
                    ),
            )

        val urls = collectGuideStaticImagePrefetchUrls(info, maxCount = 0)

        assertEquals(emptyList(), urls)
    }

    @Test
    fun `static image prefetch ignores animated and playable media`() {
        val info =
            guideInfo(
                imageUrl = "https://img.example.com/cover.gif",
                galleryItems =
                    listOf(
                        BaGuideGalleryItem(
                            title = "动画",
                            imageUrl = "https://img.example.com/animated.gif",
                            mediaUrl = "https://img.example.com/animated.gif",
                        ),
                        BaGuideGalleryItem(
                            title = "视频",
                            imageUrl = "https://img.example.com/video-poster.mp4",
                            mediaType = "video",
                            mediaUrl = "https://img.example.com/video.mp4",
                        ),
                        BaGuideGalleryItem(
                            title = "语音",
                            imageUrl = "https://img.example.com/audio.mp3",
                            mediaType = "audio",
                            mediaUrl = "https://img.example.com/audio.mp3",
                        ),
                        BaGuideGalleryItem(
                            title = "静态",
                            imageUrl = "https://img.example.com/static.png",
                            mediaUrl = "https://img.example.com/static.webp",
                        ),
                    ),
            )

        val urls = collectGuideStaticImagePrefetchUrls(info, maxCount = 4)

        assertEquals(
            listOf(
                "https://img.example.com/static.png",
                "https://img.example.com/static.webp",
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
