package os.kei.ui.page.main.student.catalog.component

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class BaGuideBgmMediaItemMapperTest {
    @Test
    fun `media item preserves queue metadata`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val item = track(
            id = "https://example.com/bgm.mp3?token=1",
            title = "Theme",
            student = "Hoshino",
            image = "https://example.com/hoshino.png"
        ).toBaGuideBgmMediaItem(
            context = context,
            playbackUrlResolver = { _, favorite -> favorite.audioUrl },
            artworkUrlResolver = { favorite -> favorite.studentImageUrl }
        )

        assertNotNull(item)
        assertEquals("https://example.com/bgm.mp3?token=1", item.mediaId)
        assertEquals("https://example.com/bgm.mp3?token=1", item.localConfiguration?.uri.toString())
        assertEquals("Theme", item.mediaMetadata.title.toString())
        assertEquals("Hoshino", item.mediaMetadata.artist.toString())
        assertEquals("https://example.com/hoshino.png", item.mediaMetadata.artworkUri.toString())
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
        assertEquals("Memorial Lobby BGM", item.mediaMetadata.title.toString())
        assertEquals("Unknown student", item.mediaMetadata.artist.toString())
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
