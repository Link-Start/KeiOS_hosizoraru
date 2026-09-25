package os.kei.feature.github.model

import org.junit.Test
import kotlin.test.assertEquals

class GitHubCheckCacheTimestampsTest {
    private data class RefreshTimestampCase(
        val label: String,
        val entries: Map<String, GitHubCheckCacheEntry>,
        val fallbackMs: Long,
        val expected: Long,
    )

    @Test
    fun `resolved refresh timestamp uses oldest checked result or falls back`() {
        val cases = listOf(
            RefreshTimestampCase(
                label = "oldest of two checked entries, not the partial refresh",
                entries = mapOf(
                    "one" to GitHubCheckCacheEntry(checkedAtMillis = 100L),
                    "two" to GitHubCheckCacheEntry(checkedAtMillis = 250L),
                ),
                fallbackMs = 1_000L,
                expected = 100L,
            ),
            RefreshTimestampCase(
                label = "an unchecked entry does not count as the oldest",
                entries = mapOf(
                    "missing" to GitHubCheckCacheEntry(),
                    "old" to GitHubCheckCacheEntry(checkedAtMillis = 100L),
                    "fresh" to GitHubCheckCacheEntry(checkedAtMillis = 250L),
                ),
                fallbackMs = 1_000L,
                expected = 100L,
            ),
            RefreshTimestampCase(
                label = "no entry has a checked result -> fallback",
                entries = mapOf("one" to GitHubCheckCacheEntry()),
                fallbackMs = 500L,
                expected = 500L,
            ),
            RefreshTimestampCase(
                label = "empty cache -> no refresh timestamp",
                entries = emptyMap(),
                fallbackMs = 500L,
                expected = 0L,
            ),
        )

        cases.forEach { case ->
            assertEquals(
                case.expected,
                case.entries.resolvedRefreshTimestamp(fallbackMs = case.fallbackMs),
                case.label,
            )
        }
    }
}
