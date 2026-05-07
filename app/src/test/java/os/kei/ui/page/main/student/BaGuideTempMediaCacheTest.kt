package os.kei.ui.page.main.student

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class BaGuideTempMediaCacheTest {
    @Test
    fun `cached metadata reports normalized file shape without valid cached file`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val metadata = BaGuideTempMediaCache.cachedMediaMetadata(
            context = context,
            sourceUrl = "$SOURCE_URL-${System.nanoTime()}",
            rawUrl = "https://example.com/audio.mp3?token=1"
        )

        assertEquals("https://example.com/audio.mp3?token=1", metadata.normalizedUrl)
        assertEquals("mp3", metadata.extension)
        assertEquals("audio/mpeg", metadata.mimeType)
        assertEquals(0L, metadata.bytes)
        assertFalse(metadata.valid)
    }

    private companion object {
        private const val SOURCE_URL = "https://example.com/guide/1"
    }
}
