package os.kei.ui.page.main.ba.support

internal class BaCalendarPoolServerSelectionAccessor(
    private val store: BaAccountKeyValueStore,
) {
    fun load(legacyServerIndex: Int = DEFAULT_SERVER_INDEX): Int {
        val fallback = legacyServerIndex.coerceIn(0, 2)
        return if (store.containsKey(KEY_CALENDAR_POOL_SERVER_INDEX)) {
            store.decodeInt(KEY_CALENDAR_POOL_SERVER_INDEX, fallback).coerceIn(0, 2)
        } else {
            fallback
        }
    }

    fun save(serverIndex: Int): Int {
        val normalized = serverIndex.coerceIn(0, 2)
        store.encode(KEY_CALENDAR_POOL_SERVER_INDEX, normalized)
        return normalized
    }
}
