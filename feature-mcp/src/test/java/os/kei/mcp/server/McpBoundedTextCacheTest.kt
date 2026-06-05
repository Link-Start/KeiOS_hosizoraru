package os.kei.mcp.server

import org.junit.Test
import kotlin.test.assertEquals

class McpBoundedTextCacheTest {
    @Test
    fun getOrPutReusesCachedTextForSameKey() {
        val cache = McpBoundedTextCache(maxEntries = 2)
        var builds = 0

        val first = cache.getOrPut("skill:zh") {
            builds += 1
            "content"
        }
        val second = cache.getOrPut("skill:zh") {
            builds += 1
            "new-content"
        }

        assertEquals("content", first)
        assertEquals("content", second)
        assertEquals(1, builds)
    }

    @Test
    fun getOrPutEvictsOldestTextWhenFull() {
        val cache = McpBoundedTextCache(maxEntries = 2)

        cache.getOrPut("one") { "1" }
        cache.getOrPut("two") { "2" }
        cache.getOrPut("three") { "3" }

        assertEquals("2", cache.getOrPut("two") { "new-2" })
        assertEquals("new-1", cache.getOrPut("one") { "new-1" })
    }

    @Test
    fun getOrPutKeepsRecentlyUsedTextWhenEvicting() {
        val cache = McpBoundedTextCache(maxEntries = 2)

        cache.getOrPut("one") { "1" }
        cache.getOrPut("two") { "2" }
        cache.getOrPut("one") { "new-1" }
        cache.getOrPut("three") { "3" }

        assertEquals("1", cache.getOrPut("one") { "new-1" })
        assertEquals("new-2", cache.getOrPut("two") { "new-2" })
    }
}
