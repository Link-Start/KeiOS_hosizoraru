package os.kei.ui.page.main.ba

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

internal const val BA_UI_SECOND_TICK_MS = 1_000L
internal const val BA_UI_MINUTE_TICK_MS = 60_000L

internal fun baUiMinuteTickDelayMs(nowMs: Long = System.currentTimeMillis()): Long {
    val elapsedInMinute = nowMs % BA_UI_MINUTE_TICK_MS
    return if (elapsedInMinute == 0L) BA_UI_MINUTE_TICK_MS else BA_UI_MINUTE_TICK_MS - elapsedInMinute
}

@Composable
internal fun rememberBaMinuteTickMs(enabled: Boolean = true): Long {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        nowMs = System.currentTimeMillis()
        while (true) {
            delay(baUiMinuteTickDelayMs().milliseconds)
            nowMs = System.currentTimeMillis()
        }
    }
    return nowMs
}
