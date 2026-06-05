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
    private val deriveToolBuckets: suspend (McpToolBucketInput) -> McpToolBuckets,
) {
    constructor(
        scope: CoroutineScope,
        repository: McpPageRepository,
    ) : this(
        scope = scope,
        deriveToolBuckets = repository::deriveToolBuckets,
    )

    private val _buckets = MutableStateFlow(McpToolBuckets.Empty)
    val buckets: StateFlow<McpToolBuckets> = _buckets.asStateFlow()

    private var input: McpToolBucketInput? = null
    private var job: Job? = null

    fun request(nextInput: McpToolBucketInput) {
        val normalizedInput = nextInput.normalizedForDerivation()
        if (input == normalizedInput) return
        input = normalizedInput
        job?.cancel()
        job =
            scope.launch {
                val derived = deriveToolBuckets(normalizedInput)
                if (input == normalizedInput && _buckets.value != derived) {
                    _buckets.value = derived
                }
            }
    }
}
