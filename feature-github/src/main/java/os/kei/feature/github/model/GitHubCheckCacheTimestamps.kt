package os.kei.feature.github.model

fun Map<String, GitHubCheckCacheEntry>.latestCheckedAtMillis(): Long =
    values
        .asSequence()
        .map { it.checkedAtMillis }
        .filter { it > 0L }
        .maxOrNull()
        ?: 0L

fun Map<String, GitHubCheckCacheEntry>.resolvedRefreshTimestamp(fallbackMs: Long = 0L): Long {
    if (isEmpty()) return 0L
    val latestCheckedAtMillis = latestCheckedAtMillis()
    return if (latestCheckedAtMillis > 0L) {
        latestCheckedAtMillis
    } else {
        fallbackMs.coerceAtLeast(0L)
    }
}
