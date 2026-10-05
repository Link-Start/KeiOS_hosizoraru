package os.kei.ui.page.main.student.section.gallery

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class GuideLobbyCameraTransform(
    val scale: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    /** Coordinates and translation are fractions of the media viewport, independent of density. */
    fun transform(focusX: Float, focusY: Float, dx: Float, dy: Float, zoom: Float): GuideLobbyCameraTransform {
        if (listOf(focusX, focusY, dx, dy, zoom).any { !it.isFinite() } || zoom <= 0f) return this
        val nextScale = (scale * zoom).coerceIn(0.5f, 4f)
        val ratio = nextScale / scale
        // Keep the point beneath the fingers anchored; retain part of the scene at the pan limit.
        val limit = (nextScale + 1f) / 2f - 0.15f
        return GuideLobbyCameraTransform(
            nextScale,
            ((focusX - 0.5f) * (1f - ratio) + panX * ratio + dx).coerceIn(-limit, limit),
            ((focusY - 0.5f) * (1f - ratio) + panY * ratio + dy).coerceIn(-limit, limit),
        )
    }
}

/** High-frequency input stays out of composition; the player collects absolute, conflated views. */
internal class GuideWebMemoryLobbyCamera {
    private val mutableTransform = MutableStateFlow(GuideLobbyCameraTransform())
    val transform = mutableTransform.asStateFlow()

    fun move(focusX: Float, focusY: Float, dx: Float, dy: Float, zoom: Float) {
        mutableTransform.update { it.transform(focusX, focusY, dx, dy, zoom) }
    }

    fun zoomIn() = move(0.5f, 0.5f, 0f, 0f, 1.25f)
    fun zoomOut() = move(0.5f, 0.5f, 0f, 0f, 0.8f)
    fun reset() { mutableTransform.value = GuideLobbyCameraTransform() }
}
