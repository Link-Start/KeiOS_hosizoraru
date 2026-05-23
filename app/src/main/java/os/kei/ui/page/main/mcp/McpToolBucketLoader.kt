package os.kei.ui.page.main.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import os.kei.ui.page.main.mcp.state.McpToolBucketInput
import os.kei.ui.page.main.mcp.state.McpToolBuckets

internal class McpToolBucketLoader(
    private val scope: CoroutineScope,
    private val repository: McpPageRepository,
) {
    private val _buckets = MutableStateFlow(McpToolBuckets.Empty)
    val buckets: StateFlow<McpToolBuckets> = _buckets.asStateFlow()

    private var input: McpToolBucketInput? = null
    private var job: Job? = null

    fun request(nextInput: McpToolBucketInput) {
        if (input == nextInput) return
        input = nextInput
        job?.cancel()
        job =
            scope.launch {
                val derived = repository.deriveToolBuckets(nextInput)
                if (input == nextInput) {
                    _buckets.value = derived
                }
            }
    }
}
