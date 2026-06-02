package os.kei.feature.github.model

fun Map<String, GitHubCheckCacheEntry>.latestCheckedAtMillis(): Long =
    values
        .asSequence()
        .map { it.checkedAtMillis }
        .filter { it > 0L }
        .maxOrNull()
        ?: 0L

fun Map<String, GitHubCheckCacheEntry>.oldestCheckedAtMillis(): Long =
    values
        .asSequence()
        .map { it.checkedAtMillis }
        .filter { it > 0L }
        .minOrNull()
        ?: 0L

fun Map<String, GitHubCheckCacheEntry>.resolvedRefreshTimestamp(fallbackMs: Long = 0L): Long {
    if (isEmpty()) return 0L
    val oldestCheckedAtMillis = oldestCheckedAtMillis()
    return if (oldestCheckedAtMillis > 0L) {
        oldestCheckedAtMillis
    } else {
        fallbackMs.coerceAtLeast(0L)
    }
}
