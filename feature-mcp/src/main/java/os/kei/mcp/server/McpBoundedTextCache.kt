package os.kei.mcp.server

import java.util.Locale

internal class McpBoundedTextCache(
    private val maxEntries: Int = 48
) {
    private val entries =
        object : LinkedHashMap<String, String>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > maxEntries
            }
        }

    fun getOrPut(key: String, producer: () -> String): String {
        synchronized(this) {
            entries[key]?.let { return it }
        }
        val value = producer()
        return synchronized(this) {
            entries[key] ?: value.also { entries[key] = it }
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}

internal fun Locale.mcpCacheTag(): String = toLanguageTag()

internal fun McpServerUiState?.mcpResourceStateCacheTag(locale: Locale): String {
    val state = this
    return buildString {
        append(locale.mcpCacheTag())
        append('|')
        append(state?.serverName ?: McpServerDefaults.SERVER_NAME)
        append('|')
        append(state?.localEndpoint ?: DEFAULT_ENDPOINT)
        append('|')
        append(state?.lanEndpoints?.joinToString(",").orEmpty())
    }
}
