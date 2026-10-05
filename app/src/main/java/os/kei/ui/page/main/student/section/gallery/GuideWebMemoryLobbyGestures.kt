@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.section.gallery

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import os.kei.R
import kotlin.math.pow

@Composable
internal fun GuideWebMemoryLobbyGestures(
    camera: GuideWebMemoryLobbyCamera,
    controlsVisible: Boolean,
    onShowControls: () -> Unit,
    modifier: Modifier = Modifier,
    viewportWidthFraction: Float = 1f,
) {
    val showControls = stringResource(R.string.guide_gallery_dynamic_lobby_show_controls)
    val gestures = stringResource(R.string.guide_gallery_dynamic_lobby_gestures)
    val zoomIn = stringResource(R.string.guide_gallery_dynamic_lobby_zoom_in)
    val zoomOut = stringResource(R.string.guide_gallery_dynamic_lobby_zoom_out)
    val reset = stringResource(R.string.guide_gallery_dynamic_lobby_reset_view)
    val onTap by rememberUpdatedState(if (controlsVisible) ({}) else onShowControls)
    Box(
        modifier
            .testTag(if (controlsVisible) GuideWebMemoryLobbyGesturesTag else GuideWebMemoryLobbyRestoreTag)
            .semantics {
                contentDescription = if (controlsVisible) gestures else showControls
                if (!controlsVisible) {
                    role = Role.Button
                    onClick(showControls) { onTap(); true }
                }
                customActions = listOf(
                    CustomAccessibilityAction(zoomIn) { camera.zoomIn(); true },
                    CustomAccessibilityAction(zoomOut) { camera.zoomOut(); true },
                    CustomAccessibilityAction(reset) { camera.reset(); true },
                )
            }
            .pointerInput(camera, controlsVisible) {
                // Restore hidden chrome immediately; only visible mode waits for a double tap.
                detectTapGestures(onTap = { onTap() },
                    onDoubleTap = if (controlsVisible) ({ camera.reset() }) else null)
            }
            .pointerInput(camera, viewportWidthFraction) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (size.width > 0 && size.height > 0) {
                        val width = size.width * viewportWidthFraction
                        val left = (size.width - width) / 2f
                        camera.move((centroid.x - left) / width, centroid.y / size.height,
                            pan.x / width, pan.y / size.height, zoom)
                    }
                }
            }
            .pointerInput(camera, viewportWidthFraction) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll && size.width > 0 && size.height > 0) {
                            event.changes.forEach { change ->
                                val width = size.width * viewportWidthFraction
                                val left = (size.width - width) / 2f
                                camera.move((change.position.x - left) / width, change.position.y / size.height,
                                    0f, 0f, 1.15f.pow(-change.scrollDelta.y))
                                change.consume()
                            }
                        }
                    }
                }
            },
    )
}

internal const val GuideWebMemoryLobbyGesturesTag = "guide_web_lobby_gestures"
