package os.kei.ui.page.main.student

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
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

    @Test
    fun `session scan rebuilds summary from existing files`() {
        val sessionDir = newScanDir("manual-session")
        File(sessionDir, "audio.mp3").writeText("12345")
        val summary = scanBaGuideMediaCacheSession(sessionDir)

        assertEquals(1, summary.count)
        assertEquals(5L, summary.bytes)
        sessionDir.deleteRecursively()
    }

    @Test
    fun `session scan reflects files removed from disk`() {
        val sessionDir = newScanDir("stale-session")
        val file = File(sessionDir, "image.webp").apply { writeText("1234") }

        assertEquals(1, scanBaGuideMediaCacheSession(sessionDir).count)

        file.delete()

        val summary = scanBaGuideMediaCacheSession(sessionDir)
        assertEquals(0, summary.count)
        assertEquals(0L, summary.bytes)
        sessionDir.deleteRecursively()
    }

    @Test
    fun `session scan skips partial download files`() {
        val sessionDir = newScanDir("partial-session")
        File(sessionDir, "video.mp4").writeText("ready")
        File(sessionDir, "video.mp4.part").writeText("partial")
        val summary = scanBaGuideMediaCacheSession(sessionDir)

        assertEquals(1, summary.count)
        assertEquals(5L, summary.bytes)
        sessionDir.deleteRecursively()
    }

    private fun newScanDir(name: String): File {
        val context = ApplicationProvider.getApplicationContext<Application>()
        return File(context.cacheDir, "$ROOT_DIR-${System.nanoTime()}/$name").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private companion object {
        private const val ROOT_DIR = "ba_student_guide_temp_media"
        private const val SOURCE_URL = "https://example.com/guide/1"
    }
}
