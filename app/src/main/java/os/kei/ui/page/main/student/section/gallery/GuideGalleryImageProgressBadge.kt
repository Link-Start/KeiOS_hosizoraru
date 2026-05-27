@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.section.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.emptyFlow
import os.kei.ui.page.main.student.GuideMediaProgressState
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar

@Composable
internal fun GuideGalleryImageProgressBadge(
    imageLoading: Boolean,
    imageProgressState: GuideMediaProgressState,
) {
    val progressFlow =
        remember(imageLoading, imageProgressState) {
            if (imageLoading) {
                imageProgressState.progress
            } else {
                emptyFlow()
            }
        }
    val imageProgress by progressFlow.collectAsStateWithLifecycle(
        initialValue = imageProgressState.progress.value,
    )
    val imageProgressValue = if (imageLoading) imageProgress.coerceIn(0f, 1f) else 1f
    val progressForegroundColor =
        if (imageProgressValue >= 0.999f) Color(0xFF34C759) else Color(0xFF3B82F6)
    val progressBackgroundColor =
        if (imageProgressValue >= 0.999f) Color(0x5534C759) else Color(0x553B82F6)
    LiquidCircularProgressBar(
        progress = { imageProgressValue },
        size = 18.dp,
        strokeWidth = 2.dp,
        activeColor = progressForegroundColor,
        inactiveColor = progressBackgroundColor,
    )
}
