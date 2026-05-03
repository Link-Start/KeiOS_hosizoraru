package os.kei.ui.page.main.widget.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

enum class AppFloatingDockSide {
    Start,
    End
}

@Composable
fun rememberAppFloatingDockSide(): MutableState<AppFloatingDockSide> {
    return remember { mutableStateOf(AppFloatingDockSide.End) }
}

fun Modifier.appGripAwareDockTouchObserver(
    onDockSideChange: (AppFloatingDockSide) -> Unit
): Modifier = pointerInput(onDockSideChange) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val down = event.changes.firstOrNull { change ->
                change.pressed && !change.previousPressed
            } ?: continue
            val hotZoneTop = size.height * 0.56f
            if (down.position.y < hotZoneTop) continue
            val nextSide = when {
                down.position.x < size.width * 0.44f -> AppFloatingDockSide.Start
                down.position.x > size.width * 0.56f -> AppFloatingDockSide.End
                else -> null
            }
            nextSide?.let(onDockSideChange)
        }
    }
}
