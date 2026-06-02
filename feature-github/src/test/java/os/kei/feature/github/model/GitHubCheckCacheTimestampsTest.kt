package os.kei.feature.github.model

import org.junit.Test
import kotlin.test.assertEquals

class GitHubCheckCacheTimestampsTest {
    @Test
    fun `refresh timestamp follows oldest checked result instead of partial refresh`() {
        val entries =
            mapOf(
                "one" to GitHubCheckCacheEntry(checkedAtMillis = 100L),
                "two" to GitHubCheckCacheEntry(checkedAtMillis = 250L),
            )

        assertEquals(100L, entries.resolvedRefreshTimestamp(fallbackMs = 1_000L))
    }

    @Test
    fun `latest checked timestamp still reports newest entry`() {
        val entries =
            mapOf(
                "one" to GitHubCheckCacheEntry(checkedAtMillis = 100L),
                "two" to GitHubCheckCacheEntry(checkedAtMillis = 250L),
            )

        assertEquals(250L, entries.latestCheckedAtMillis())
    }

    @Test
    fun `oldest checked timestamp ignores missing entries`() {
        val entries =
            mapOf(
                "missing" to GitHubCheckCacheEntry(),
                "old" to GitHubCheckCacheEntry(checkedAtMillis = 100L),
                "fresh" to GitHubCheckCacheEntry(checkedAtMillis = 250L),
            )

        assertEquals(100L, entries.oldestCheckedAtMillis())
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
