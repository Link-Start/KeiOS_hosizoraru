package os.kei.ui.page.main.student.catalog.state

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class BaGuideCatalogImageController(
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val repository: BaGuideCatalogImageRepository = BaGuideCatalogImageRepository(),
) {
    private val mutableState = MutableStateFlow(BaGuideCatalogImageUiState())
    private var loadingUrls: Set<String> = emptySet()

    val state: StateFlow<BaGuideCatalogImageUiState> = mutableState.asStateFlow()

    fun requestImages(imageUrls: List<String>) {
        val normalizedUrls =
            imageUrls
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        if (normalizedUrls.isEmpty()) return

        val currentState = mutableState.value
        val missingUrls =
            normalizedUrls.filter { imageUrl ->
                !currentState.bitmaps.containsKey(imageUrl) &&
                    !currentState.missingUrls.contains(imageUrl) &&
                    !loadingUrls.contains(imageUrl) &&
                    repository.cachedBitmap(imageUrl) == null
            }
        val cachedBitmaps =
            normalizedUrls
                .filterNot { currentState.bitmaps.containsKey(it) }
                .mapNotNull { imageUrl ->
                    repository.cachedBitmap(imageUrl)?.let { bitmap -> imageUrl to bitmap }
                }.toMap()
        if (cachedBitmaps.isNotEmpty()) {
            mutableState.update { state ->
                state.copy(bitmaps = state.bitmaps + cachedBitmaps)
            }
        }
        if (missingUrls.isEmpty()) return

        loadingUrls += missingUrls
        scope.launch {
            try {
                val result =
                    repository.loadImages(
                        context = appContext,
                        imageUrls = missingUrls,
                    )
                mutableState.update { state ->
                    state.copy(
                        bitmaps = state.bitmaps + result.bitmaps,
                        missingUrls = state.missingUrls + result.missingUrls,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                mutableState.update { state ->
                    state.copy(missingUrls = state.missingUrls + missingUrls)
                }
            } finally {
                loadingUrls -= missingUrls
            }
        }
    }

    fun clearLoadingState() {
        loadingUrls = emptySet()
    }
}
