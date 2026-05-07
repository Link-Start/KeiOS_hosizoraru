package os.kei.ui.page.main.student

import android.app.Application
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class BaGuideBgmMediaIslandShareCompatTest {
    @Test
    fun `Xiaomi devices support media island drag share params`() {
        assertTrue(
            BaGuideBgmMediaOemCompat.mediaIslandDragShareSupported(
                BaGuideBgmMediaOemCompat.DeviceSignals(
                    brand = "Xiaomi",
                    manufacturer = "Xiaomi",
                    display = "OS2.0.1",
                    model = "Xiaomi",
                    properties = mapOf("ro.mi.os.version.name" to "OS2")
                )
            )
        )
    }

    @Test
    fun `share params follow HyperOS media island schema`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val mediaItem = MediaItem.Builder()
            .setMediaId("https://cdn.example.com/bgm.ogg")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Fallback")
                    .setExtras(
                        Bundle().apply {
                            putString(BA_GUIDE_BGM_MEDIA_METADATA_STUDENT_TITLE, "MicYou")
                            putString(
                                BA_GUIDE_BGM_MEDIA_METADATA_SOURCE_URL,
                                "https://www.gamekee.com/ba/tj/591006.html"
                            )
                            putString(
                                BA_GUIDE_BGM_MEDIA_METADATA_AUDIO_URL,
                                "https://cdn.example.com/bgm.ogg"
                            )
                        }
                    )
                    .build()
            )
            .build()

        val raw = BaGuideBgmMediaIslandShareCompat.buildShareParams(context, mediaItem)

        assertNotNull(raw)
        val shareData = JSONObject(raw)
            .getJSONObject("param_v2")
            .getJSONObject("param_island")
            .getJSONObject("shareData")
        assertEquals("MicYou", shareData.getString("title"))
        assertEquals(
            "Listening to MicYou Memorial Lobby BGM",
            shareData.getString("content")
        )
        assertEquals(
            "Listening to MicYou Memorial Lobby BGM in KeiOS.\n" +
                    "Source: https://www.gamekee.com/ba/tj/591006.html",
            shareData.getString("shareContent")
        )
    }

    @Test
    fun `share params skip local-only media`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val mediaItem = MediaItem.Builder()
            .setMediaId("file:///tmp/bgm.ogg")
            .setUri("file:///tmp/bgm.ogg")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("MicYou")
                    .build()
            )
            .build()

        assertNull(BaGuideBgmMediaIslandShareCompat.buildShareParams(context, mediaItem))
    }
}
