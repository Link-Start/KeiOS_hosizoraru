package os.kei.ui.page.main.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private data class McpRuntimeTickerInput(
    val running: Boolean,
    val runningSinceEpochMs: Long,
    val dataActive: Boolean,
)

internal class McpRuntimeTicker(
    private val scope: CoroutineScope,
    initialNowMs: Long = System.currentTimeMillis(),
) {
    private val _nowMs = MutableStateFlow(initialNowMs)
    val nowMs: StateFlow<Long> = _nowMs.asStateFlow()

    private var input: McpRuntimeTickerInput? = null
    private var job: Job? = null

    fun request(
        running: Boolean,
        runningSinceEpochMs: Long,
        dataActive: Boolean,
    ) {
        val nextInput =
            McpRuntimeTickerInput(
                running = running,
                runningSinceEpochMs = runningSinceEpochMs,
                dataActive = dataActive,
            )
        if (input == nextInput) return
        input = nextInput
        job?.cancel()
        _nowMs.value = System.currentTimeMillis()
        if (!running || runningSinceEpochMs <= 0L) return
        job =
            scope.launch {
                while (input == nextInput) {
                    delay((if (dataActive) 1_000L else 3_000L).milliseconds)
                    _nowMs.value = System.currentTimeMillis()
                }
            }
    }
}
