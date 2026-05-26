package os.kei.ui.page.main.ba

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun rememberBaPageSettledWorkActive(
    active: Boolean,
    delayMs: Long = BA_PAGE_SETTLED_WORK_DELAY_MS,
): Boolean {
    var workActive by remember { mutableStateOf(false) }
    LaunchedEffect(active, delayMs) {
        if (!active) {
            workActive = false
            return@LaunchedEffect
        }
        if (delayMs > 0L) {
            delay(delayMs.milliseconds)
        }
        workActive = true
    }
    return workActive
}

private const val BA_PAGE_SETTLED_WORK_DELAY_MS = 520L
