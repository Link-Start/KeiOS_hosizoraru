package os.kei.ui.page.main.github.page

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The GitHub page's history unread count, as the long-lived page ViewModel holds it.
 *
 * The history pages mark a bucket read by moving its watermark in `GitHubHistoryUnreadStore`, which
 * publishes a process-local signal. The page ViewModel outlives those pages, so unless it listens to
 * that signal its badge keeps the count from before the history was read. Listening starts in `init`,
 * so constructing the badge is enough for it to follow every watermark change.
 *
 * [refresh] is also called directly after refreshes that add history. Only the newest request may set
 * the count, and a failed load keeps the previous one.
 */
internal class GitHubHistoryUnreadBadge(
    private val scope: CoroutineScope,
    signalVersions: Flow<Long>,
    private val loadCount: suspend () -> Int,
) {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    private var refreshJob: Job? = null
    private var refreshRequestSerial = 0L

    init {
        scope.launch {
            signalVersions.collect {
                refresh()
            }
        }
    }

    fun refresh() {
        val requestSerial = refreshRequestSerial + 1L
        refreshRequestSerial = requestSerial
        refreshJob?.cancel()
        refreshJob =
            scope.launch {
                val nextCount =
                    runCatching {
                        loadCount()
                    }.getOrElse { error ->
                        if (error is CancellationException) throw error
                        _count.value
                    }
                if (requestSerial == refreshRequestSerial) {
                    _count.value = nextCount
                }
            }
    }
}
