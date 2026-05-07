package os.kei.ui.page.main.student.catalog.component

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.media3.common.MediaMetadata
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class BaGuideBgmMediaItemMapperTest {
    @After
    fun tearDown() {
        BaGuideBgmMediaArtworkPayloadResolver.clearMemoryCache()
    }

    @Test
    fun `media item preserves queue metadata`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val artworkData = byteArrayOf(1, 2, 3, 4)
        val item = track(
            id = "https://example.com/bgm.mp3?token=1",
            title = "Theme",
            student = "Hoshino",
            image = "https://example.com/hoshino.png"
        ).toBaGuideBgmMediaItem(
            context = context,
            playbackUrlResolver = { _, favorite -> favorite.audioUrl },
            artworkUrlResolver = { favorite -> favorite.studentImageUrl },
            artworkData = artworkData
        )

        assertNotNull(item)
        assertEquals("https://example.com/bgm.mp3?token=1", item.mediaId)
        assertEquals("https://example.com/bgm.mp3?token=1", item.localConfiguration?.uri.toString())
        assertEquals("Hoshino", item.mediaMetadata.title.toString())
        assertEquals("Hoshino", item.mediaMetadata.displayTitle.toString())
        assertEquals("Memorial Lobby BGM", item.mediaMetadata.artist.toString())
        assertEquals("Memorial Lobby BGM", item.mediaMetadata.subtitle.toString())
        assertEquals("https://example.com/hoshino.png", item.mediaMetadata.artworkUri.toString())
        assertContentEquals(artworkData, item.mediaMetadata.artworkData)
        assertEquals(MediaMetadata.PICTURE_TYPE_FRONT_COVER, item.mediaMetadata.artworkDataType)
    }

    @Test
    fun `media item uses stable fallback metadata`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val item = track(
            id = "https://example.com/fallback.ogg",
            title = "",
            student = "",
            image = ""
        ).toBaGuideBgmMediaItem(
            context = context,
            playbackUrlResolver = { _, favorite -> favorite.audioUrl },
            artworkUrlResolver = { favorite -> favorite.studentImageUrl }
        )

        assertNotNull(item)
        assertEquals("https://example.com/fallback.ogg", item.mediaId)
        assertEquals("Unknown student", item.mediaMetadata.title.toString())
        assertEquals("Memorial Lobby BGM", item.mediaMetadata.artist.toString())
    }

    @Test
    fun `artwork resolver produces cached png payload`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val artworkUrl = "https://example.com/artwork-${System.nanoTime()}.png"
        var loadCount = 0
        val loader: (Context, String) -> Bitmap? = { _, url ->
            loadCount += 1
            assertEquals(artworkUrl, url)
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.RED)
            }
        }

        val first = BaGuideBgmMediaArtworkPayloadResolver.resolveUrl(
            context = context,
            artworkUrl = artworkUrl,
            bitmapLoader = loader
        )
        val second = BaGuideBgmMediaArtworkPayloadResolver.resolveUrl(
            context = context,
            artworkUrl = artworkUrl,
            bitmapLoader = loader
        )

        assertNotNull(first)
        assertContentEquals(first, second)
        assertEquals(1, loadCount)
    }

    @Test
    fun `artwork hydration targets start from selected track and stay bounded`() {
        val queue = listOf(
            track("https://example.com/1.mp3", "One", "One", ""),
            track("https://example.com/2.mp3", "Two", "Two", ""),
            track("https://example.com/3.mp3", "Three", "Three", ""),
            track("https://example.com/4.mp3", "Four", "Four", "")
        )

        val targets = queue.orderedArtworkHydrationTargets(
            selectedAudioUrl = "https://example.com/3.mp3",
            maxCount = 3
        )

        assertEquals(
            listOf(
                "https://example.com/3.mp3",
                "https://example.com/4.mp3",
                "https://example.com/1.mp3"
            ),
            targets.map { it.audioUrl }
        )
    }

    private fun track(
        id: String,
        title: String,
        student: String,
        image: String
    ): GuideBgmFavoriteItem {
        return GuideBgmFavoriteItem(
            audioUrl = id,
            title = title,
            studentTitle = student,
            studentImageUrl = image,
            imageUrl = "",
            sourceUrl = "https://www.gamekee.com/ba/tj/1.html",
            note = "",
            favoritedAtMs = 1L
        )
    }
}
