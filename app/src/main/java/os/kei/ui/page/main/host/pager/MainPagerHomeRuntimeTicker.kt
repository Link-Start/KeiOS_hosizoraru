package os.kei.ui.page.main.host.pager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import os.kei.feature.home.model.HomeMcpOverview

private const val HOME_RUNTIME_TICK_INTERVAL_MS = 30_000L

internal class MainPagerHomeRuntimeTicker(
    private val scope: CoroutineScope,
    initialNowMs: Long = System.currentTimeMillis(),
) {
    private val _nowMs = MutableStateFlow(initialNowMs)
    val nowMs: StateFlow<Long> = _nowMs.asStateFlow()

    private var tickerJob: Job? = null
    private var lastRequest: Request? = null

    fun request(
        mcpOverview: HomeMcpOverview,
        runtime: MainPageRuntime,
    ) {
        val nextRequest =
            Request(
                running = mcpOverview.running,
                runningSinceEpochMs = mcpOverview.runningSinceEpochMs,
                pageActive = runtime.isPageActive,
                dataActive = runtime.isDataActive,
            )
        if (nextRequest == lastRequest) return
        lastRequest = nextRequest

        tickerJob?.cancel()
        _nowMs.value = System.currentTimeMillis()
        if (!nextRequest.shouldTick) return

        tickerJob =
            scope.launch {
                while (true) {
                    delay(HOME_RUNTIME_TICK_INTERVAL_MS)
                    _nowMs.value = System.currentTimeMillis()
                }
            }
    }

    override fun toString(): String = "MainPagerHomeRuntimeTicker(nowMs=${_nowMs.value})"

    private data class Request(
        val running: Boolean,
        val runningSinceEpochMs: Long,
        val pageActive: Boolean,
        val dataActive: Boolean,
    ) {
        val shouldTick: Boolean
            get() = pageActive && dataActive && running && runningSinceEpochMs > 0L
    }
}
