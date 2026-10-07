package os.kei.ui.page.main.student.model3d

import androidx.compose.runtime.Immutable

@Immutable
internal data class BaModel3dOptions(
    val speed: Float = 1f,
    val loop: Boolean = true,
    val outline: Boolean = true,
    val outlineWidth: Float = 0.25f,
    val scrubbing: Boolean = false,
)

internal data class BaModel3dPlaybackState(
    val actions: List<String> = emptyList(),
    val durations: List<Float> = emptyList(),
    val selected: String = "",
    val ready: Boolean = false,
    val position: Float = 0f,
    val duration: Float = 0f,
    val ended: Boolean = false,
)
