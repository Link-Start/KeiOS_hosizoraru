package os.kei.ui.page.main.widget.markdown

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val APP_MARKDOWN_BLOCK_CACHE_MAX_ENTRIES = 24

private data class AppMarkdownBlockCacheKey(
    val sourceKey: String,
    val preserveLineBreaks: Boolean,
    val contentLength: Int,
    val contentHash: Int
)

private object AppMarkdownBlockCache {
    private val lock = Any()
    private val entries = object : LinkedHashMap<AppMarkdownBlockCacheKey, List<AppMarkdownBlock>>(
        APP_MARKDOWN_BLOCK_CACHE_MAX_ENTRIES,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<AppMarkdownBlockCacheKey, List<AppMarkdownBlock>>
        ): Boolean = size > APP_MARKDOWN_BLOCK_CACHE_MAX_ENTRIES
    }

    fun get(key: AppMarkdownBlockCacheKey): List<AppMarkdownBlock>? {
        return synchronized(lock) { entries[key] }
    }

    fun put(key: AppMarkdownBlockCacheKey, blocks: List<AppMarkdownBlock>) {
        synchronized(lock) { entries[key] = blocks }
    }
}

internal suspend fun parseCachedAppMarkdownBlocks(
    markdown: String,
    preserveLineBreaks: Boolean,
    sourceKey: String?
): List<AppMarkdownBlock> {
    val normalizedSourceKey = sourceKey
        ?.takeIf { it.isNotBlank() }
        ?: "inline:${markdown.length}:${markdown.hashCode()}"
    val cacheKey = AppMarkdownBlockCacheKey(
        sourceKey = normalizedSourceKey,
        preserveLineBreaks = preserveLineBreaks,
        contentLength = markdown.length,
        contentHash = markdown.hashCode()
    )
    AppMarkdownBlockCache.get(cacheKey)?.let { return it }
    val blocks = withContext(Dispatchers.Default) {
        parseAppMarkdownBlocks(markdown, preserveLineBreaks = preserveLineBreaks)
    }
    AppMarkdownBlockCache.put(cacheKey, blocks)
    return blocks
}
