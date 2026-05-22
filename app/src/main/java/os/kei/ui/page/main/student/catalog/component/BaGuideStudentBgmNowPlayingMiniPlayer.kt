@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import os.kei.ui.page.main.student.GuideBgmFavoriteItem

@Composable
internal fun BaGuideStudentBgmNowPlayingMiniPlayer(
    playbackCoordinator: BaGuideBgmPlaybackCoordinator,
    favorite: GuideBgmFavoriteItem,
    seekPreviewProgress: Float?,
    queueIndex: Int,
    queueSize: Int,
    queueMode: BaGuideBgmQueueMode,
    accent: Color,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenQueue: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onSeekChanged: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onSliderInteractionChanged: (Boolean) -> Unit,
    onToggleQueueMode: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    val runtimeState by playbackCoordinator.runtimeStateFlow.collectAsStateWithLifecycle()
    val displayedRuntimeState =
        remember(runtimeState, seekPreviewProgress) {
            if (seekPreviewProgress != null && runtimeState.durationMs > 0L) {
                val durationMs = runtimeState.durationMs
                runtimeState.copy(
                    positionMs =
                        (durationMs * seekPreviewProgress.coerceIn(0f, 1f))
                            .toLong()
                            .coerceIn(0L, durationMs),
                )
            } else {
                runtimeState
            }
        }

    BaGuideBgmMiniPlayer(
        favorite = favorite,
        runtimeState = displayedRuntimeState,
        queueIndex = queueIndex,
        queueSize = queueSize,
        queueMode = queueMode,
        accent = accent,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        onOpenQueue = onOpenQueue,
        onPrevious = onPrevious,
        onTogglePlayback = onTogglePlayback,
        onNext = onNext,
        onSeekChanged = onSeekChanged,
        onSeekFinished = onSeekFinished,
        onVolumeChanged = onVolumeChanged,
        onSliderInteractionChanged = onSliderInteractionChanged,
        onToggleQueueMode = onToggleQueueMode,
        onOpenGuide = onOpenGuide,
    )
}
