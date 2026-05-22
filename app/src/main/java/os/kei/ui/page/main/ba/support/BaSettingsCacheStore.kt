package os.kei.ui.page.main.ba.support

import com.tencent.mmkv.MMKV

internal class BaSettingsCacheStore(
    private val store: MMKV,
    private val notifyChanged: () -> Unit,
) {
    fun loadCalendarCache(serverIndex: Int): Pair<String, Long> =
        store.decodeString(calendarCacheKey(serverIndex), "").orEmpty() to
            store.decodeLong(calendarSyncKey(serverIndex), 0L)

    fun saveCalendarCache(
        serverIndex: Int,
        encodedEntries: String,
        syncMs: Long,
    ) {
        store.encode(calendarCacheKey(serverIndex), encodedEntries)
        store.encode(calendarSyncKey(serverIndex), syncMs.coerceAtLeast(0L))
        store.encode(calendarCacheVersionKey(serverIndex), BA_CALENDAR_CACHE_SCHEMA_VERSION)
        notifyChanged()
    }

    fun loadCalendarCacheVersion(serverIndex: Int): Int = store.decodeInt(calendarCacheVersionKey(serverIndex), 0)

    fun loadCalendarCacheSnapshot(serverIndex: Int): BaCacheSnapshot =
        BaCacheSnapshot(
            raw = store.decodeString(calendarCacheKey(serverIndex), "").orEmpty(),
            syncMs = store.decodeLong(calendarSyncKey(serverIndex), 0L),
            version = store.decodeInt(calendarCacheVersionKey(serverIndex), 0),
        )

    fun loadPoolCache(serverIndex: Int): Pair<String, Long> =
        store.decodeString(poolCacheKey(serverIndex), "").orEmpty() to
            store.decodeLong(poolSyncKey(serverIndex), 0L)

    fun savePoolCache(
        serverIndex: Int,
        encodedEntries: String,
        syncMs: Long,
    ) {
        store.encode(poolCacheKey(serverIndex), encodedEntries)
        store.encode(poolSyncKey(serverIndex), syncMs.coerceAtLeast(0L))
        store.encode(poolCacheVersionKey(serverIndex), BA_POOL_CACHE_SCHEMA_VERSION)
        notifyChanged()
    }

    fun loadPoolCacheVersion(serverIndex: Int): Int = store.decodeInt(poolCacheVersionKey(serverIndex), 0)

    fun loadPoolCacheSnapshot(serverIndex: Int): BaCacheSnapshot =
        BaCacheSnapshot(
            raw = store.decodeString(poolCacheKey(serverIndex), "").orEmpty(),
            syncMs = store.decodeLong(poolSyncKey(serverIndex), 0L),
            version = store.decodeInt(poolCacheVersionKey(serverIndex), 0),
        )

    fun clearCalendarAndPoolCaches() {
        for (serverIndex in 0..2) {
            store.removeValueForKey(calendarCacheKey(serverIndex))
            store.removeValueForKey(calendarSyncKey(serverIndex))
            store.removeValueForKey(calendarCacheVersionKey(serverIndex))
            store.removeValueForKey(poolCacheKey(serverIndex))
            store.removeValueForKey(poolSyncKey(serverIndex))
            store.removeValueForKey(poolCacheVersionKey(serverIndex))
        }
        store.trim()
        notifyChanged()
    }

    fun cacheBytesEstimated(): Long {
        var total = 0L
        for (serverIndex in 0..2) {
            val calendar = loadCalendarCacheSnapshot(serverIndex)
            val pool = loadPoolCacheSnapshot(serverIndex)
            total += calendar.raw.length.toLong() * 2 + 16L
            total += pool.raw.length.toLong() * 2 + 16L
        }
        return total
    }
}
