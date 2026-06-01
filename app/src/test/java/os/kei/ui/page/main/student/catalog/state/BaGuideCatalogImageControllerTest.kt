package os.kei.ui.page.main.student.catalog.state

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class BaGuideCatalogImageControllerTest {
    @Test
    fun `request images drains through bounded batches`() =
        runTest {
            val repository = RecordingCatalogImageRepository()
            val controller =
                BaGuideCatalogImageController(
                    scope = this,
                    appContext = ApplicationProvider.getApplicationContext<Application>(),
                    repository = repository,
                    clock = { 1_000L },
                )

            controller.requestImages((0 until 18).map { index -> "icon-$index" })
            advanceUntilIdle()

            assertEquals(
                listOf(8, 4, 4, 2),
                repository.batches.map { it.size },
            )
        }

    @Test
    fun `new request is moved ahead of queued warmup work`() =
        runTest {
            val repository = PausingCatalogImageRepository()
            val controller =
                BaGuideCatalogImageController(
                    scope = this,
                    appContext = ApplicationProvider.getApplicationContext<Application>(),
                    repository = repository,
                    clock = { 1_000L },
                )

            controller.requestImages((0 until 18).map { index -> "warm-$index" })
            runCurrent()
            controller.requestImages(listOf("active-0", "active-1"))
            repository.releaseFirstBatch.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                listOf("active-0", "active-1", "warm-8", "warm-9"),
                repository.batches[1],
            )
        }

    private open class RecordingCatalogImageRepository : BaGuideCatalogImageRepository() {
        val batches = mutableListOf<List<String>>()

        override fun cachedBitmap(imageUrl: String): Bitmap? = null

        open override suspend fun loadImages(
            context: Context,
            imageUrls: List<String>,
            onResult: (imageUrl: String, bitmap: Bitmap?) -> Unit,
        ): BaGuideCatalogImageLoadResult {
            batches += imageUrls
            imageUrls.forEach { imageUrl ->
                onResult(imageUrl, null)
            }
            return BaGuideCatalogImageLoadResult(
                bitmaps = emptyMap(),
                missingUrls = imageUrls.toSet(),
            )
        }
    }

    private class PausingCatalogImageRepository : RecordingCatalogImageRepository() {
        val releaseFirstBatch = CompletableDeferred<Unit>()

        override suspend fun loadImages(
            context: Context,
            imageUrls: List<String>,
            onResult: (imageUrl: String, bitmap: Bitmap?) -> Unit,
        ): BaGuideCatalogImageLoadResult {
            val firstBatch = batches.isEmpty()
            batches += imageUrls
            if (firstBatch) {
                releaseFirstBatch.await()
            }
            imageUrls.forEach { imageUrl ->
                onResult(imageUrl, null)
            }
            return BaGuideCatalogImageLoadResult(
                bitmaps = emptyMap(),
                missingUrls = imageUrls.toSet(),
            )
        }
    }
}
