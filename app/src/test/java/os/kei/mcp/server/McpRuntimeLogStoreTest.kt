package os.kei.mcp.server

import org.junit.Test
import kotlin.test.assertEquals

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
}
