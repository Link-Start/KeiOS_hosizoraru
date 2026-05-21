package os.kei.ui.page.main.home.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import os.kei.feature.home.model.HomeMcpOverview
import os.kei.ui.page.main.host.pager.MainPageRuntime

private const val HOME_RUNTIME_TICK_INTERVAL_MS = 30_000L

@Composable
internal fun rememberHomePageRuntimeNowMs(
    mcpOverview: HomeMcpOverview,
    runtime: MainPageRuntime,
): State<Long> {
    val initialNowMs = remember { System.currentTimeMillis() }
    return produceState(
        initialValue = initialNowMs,
        mcpOverview.running,
        mcpOverview.runningSinceEpochMs,
        runtime.isPageActive,
    ) {
        val active = runtime.isPageActive && mcpOverview.running && mcpOverview.runningSinceEpochMs > 0L
        value = System.currentTimeMillis()
        if (!active) return@produceState
        while (true) {
            delay(HOME_RUNTIME_TICK_INTERVAL_MS)
            value = System.currentTimeMillis()
        }
    }
}
