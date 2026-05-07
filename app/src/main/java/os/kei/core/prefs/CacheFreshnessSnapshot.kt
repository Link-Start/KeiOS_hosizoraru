package os.kei.core.prefs

data class CacheFreshnessSnapshot(
    val hasData: Boolean,
    val fresh: Boolean,
    val stale: Boolean,
    val lastUpdatedAtMs: Long,
    val bytes: Long,
    val rebuildable: Boolean
) {
    companion object {
        val Empty = CacheFreshnessSnapshot(
            hasData = false,
            fresh = false,
            stale = false,
            lastUpdatedAtMs = 0L,
            bytes = 0L,
            rebuildable = false
        )

        fun from(
            lastUpdatedAtMs: Long,
            bytes: Long,
            rebuildable: Boolean,
            ttlMs: Long? = null,
            nowMs: Long = System.currentTimeMillis()
        ): CacheFreshnessSnapshot {
            val normalizedBytes = bytes.coerceAtLeast(0L)
            val normalizedUpdatedAtMs = lastUpdatedAtMs.coerceAtLeast(0L)
            val hasData = normalizedBytes > 0L || normalizedUpdatedAtMs > 0L
            val fresh = hasData && when (ttlMs) {
                null -> true
                else -> normalizedUpdatedAtMs > 0L &&
                        (nowMs - normalizedUpdatedAtMs).coerceAtLeast(0L) <= ttlMs.coerceAtLeast(1L)
            }
            return CacheFreshnessSnapshot(
                hasData = hasData,
                fresh = fresh,
                stale = hasData && !fresh,
                lastUpdatedAtMs = normalizedUpdatedAtMs,
                bytes = normalizedBytes,
                rebuildable = rebuildable
            )
        }
    }
}
