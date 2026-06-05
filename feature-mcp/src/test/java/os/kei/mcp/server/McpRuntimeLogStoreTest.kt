package os.kei.mcp.server

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class McpRuntimeLogStoreTest {
    @Test
    fun appendKeepsBoundedSnapshot() {
        var latest = emptyList<McpLogEntry>()
        val store = McpRuntimeLogStore(maxEntries = 2) { logs ->
            latest = logs
        }

        store.append("INFO", "one")
        store.append("INFO", "two")
        store.append("INFO", "three")

        assertEquals(listOf("two", "three"), latest.map { it.message })
        assertEquals(listOf("two", "three"), store.snapshot().map { it.message })
    }

    @Test
    fun clearPublishesEmptySnapshot() {
        var latest = emptyList<McpLogEntry>()
        val store = McpRuntimeLogStore(maxEntries = 2) { logs ->
            latest = logs
        }

        store.append("INFO", "one")
        store.clear()

        assertEquals(emptyList(), latest)
        assertEquals(emptyList(), store.snapshot())
    }

    @Test
    fun appendCoalescesFrequentUiPublishes() = runTest {
        val publishes = mutableListOf<List<String>>()
        val store = McpRuntimeLogStore(
            maxEntries = 10,
            minPublishIntervalMs = 500,
            publishScope = this,
            nowMs = { testScheduler.currentTime }
        ) { logs ->
            publishes += logs.map { it.message }
        }

        store.append("INFO", "one")
        store.append("INFO", "two")
        store.append("INFO", "three")

        assertEquals(listOf(listOf("one")), publishes)

        advanceTimeBy(499)
        runCurrent()
        assertEquals(listOf(listOf("one")), publishes)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(
            listOf(
                listOf("one"),
                listOf("one", "two", "three")
            ),
            publishes
        )
    }

    @Test
    fun clearCancelsPendingPublishAndNextAppendPublishesImmediately() = runTest {
        val publishes = mutableListOf<List<String>>()
        val store = McpRuntimeLogStore(
            maxEntries = 10,
            minPublishIntervalMs = 500,
            publishScope = this,
            nowMs = { testScheduler.currentTime }
        ) { logs ->
            publishes += logs.map { it.message }
        }

        store.append("INFO", "one")
        store.append("INFO", "two")
        store.clear()
        store.append("INFO", "after-clear")

        assertEquals(
            listOf(
                listOf("one"),
                emptyList(),
                listOf("after-clear")
            ),
            publishes
        )

        advanceTimeBy(500)
        runCurrent()
        assertEquals(
            listOf(
                listOf("one"),
                emptyList(),
                listOf("after-clear")
            ),
            publishes
        )
    }
}
