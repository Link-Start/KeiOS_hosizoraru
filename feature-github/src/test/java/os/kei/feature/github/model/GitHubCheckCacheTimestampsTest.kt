package os.kei.feature.github.model

import org.junit.Test
import kotlin.test.assertEquals

class GitHubCheckCacheTimestampsTest {
    @Test
    fun `refresh timestamp follows latest checked result instead of newer fallback`() {
        val entries =
            mapOf(
                "one" to GitHubCheckCacheEntry(checkedAtMillis = 100L),
                "two" to GitHubCheckCacheEntry(checkedAtMillis = 250L),
            )

        assertEquals(250L, entries.resolvedRefreshTimestamp(fallbackMs = 1_000L))
    }

    @Test
    fun `refresh timestamp falls back only when entries have no checked result`() {
        val entries = mapOf("one" to GitHubCheckCacheEntry())

        assertEquals(500L, entries.resolvedRefreshTimestamp(fallbackMs = 500L))
    }

    @Test
    fun `empty cache has no refresh timestamp`() {
        assertEquals(
            0L,
            emptyMap<String, GitHubCheckCacheEntry>().resolvedRefreshTimestamp(fallbackMs = 500L),
        )
    }
}
