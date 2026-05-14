package os.kei.ui.page.main.ba.support

import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import java.net.URI
import java.util.Locale

private val baPoolExplicitGuidePathRegexes = listOf(
    Regex("""^/ba/tj/(\d+)(?:\.html)?$""", RegexOption.IGNORE_CASE),
    Regex("""^/v1/content/detail/(\d+)$""", RegexOption.IGNORE_CASE)
)

internal val BaPoolEntry.studentGuideOpenUrl: String
    get() = studentGuideUrl.ifBlank { canonicalBaPoolStudentGuideUrlOrBlank(linkUrl) }

internal fun canonicalBaPoolStudentGuideUrlOrBlank(rawUrl: String): String {
    val normalized = normalizeGameKeeLink(rawUrl)
    if (normalized.isBlank()) return ""
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return ""
    val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
    if (host != "www.gamekee.com" && host != "gamekee.com") return ""
    val path = uri.path.orEmpty()
    baPoolExplicitGuidePathRegexes.forEach { regex ->
        val contentId = regex.find(path)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        if (contentId != null && contentId > 0L) {
            return "https://www.gamekee.com/ba/tj/$contentId.html"
        }
    }
    return ""
}

internal class BaPoolStudentGuideUrlResolver private constructor(
    private val detailUrlByNameKey: Map<String, String>,
    private val detailUrlByAliasKey: Map<String, String>,
) {
    fun resolve(
        name: String,
        linkUrl: String,
    ): String {
        val directGuideUrl = canonicalBaPoolStudentGuideUrlOrBlank(linkUrl)
        if (directGuideUrl.isNotBlank()) return directGuideUrl
        val lookupKey = normalizeBaPoolStudentLookupKey(name)
        if (lookupKey.isBlank()) return ""
        return detailUrlByNameKey[lookupKey]
            ?: detailUrlByAliasKey[lookupKey]
            ?: ""
    }

    fun resolve(entry: BaPoolEntry): BaPoolEntry {
        val resolvedGuideUrl = entry.studentGuideUrl.ifBlank {
            resolve(name = entry.name, linkUrl = entry.linkUrl)
        }
        return if (resolvedGuideUrl == entry.studentGuideUrl) {
            entry
        } else {
            entry.copy(studentGuideUrl = resolvedGuideUrl)
        }
    }

    companion object {
        val Empty = BaPoolStudentGuideUrlResolver(emptyMap(), emptyMap())

        fun fromCatalogEntries(entries: List<BaGuideCatalogEntry>): BaPoolStudentGuideUrlResolver {
            if (entries.isEmpty()) return Empty
            val byName = linkedMapOf<String, String>()
            val byAlias = linkedMapOf<String, String>()
            entries.forEach { entry ->
                val detailUrl = canonicalBaPoolStudentGuideUrlOrBlank(entry.detailUrl)
                    .ifBlank { entry.detailUrl.trim() }
                if (detailUrl.isBlank()) return@forEach
                byName.putIfAbsent(normalizeBaPoolStudentLookupKey(entry.name), detailUrl)
                entry.alias
                    .split(Regex("""[,，、/|｜;；·]"""))
                    .map(::normalizeBaPoolStudentLookupKey)
                    .filter { it.isNotBlank() }
                    .forEach { aliasKey ->
                        byAlias.putIfAbsent(aliasKey, detailUrl)
                    }
                entry.aliasDisplay
                    .split(Regex("""[,，、/|｜;；·]"""))
                    .map(::normalizeBaPoolStudentLookupKey)
                    .filter { it.isNotBlank() }
                    .forEach { aliasKey ->
                        byAlias.putIfAbsent(aliasKey, detailUrl)
                    }
            }
            return BaPoolStudentGuideUrlResolver(
                detailUrlByNameKey = byName,
                detailUrlByAliasKey = byAlias
            )
        }
    }
}

private fun normalizeBaPoolStudentLookupKey(raw: String): String {
    return raw
        .trim()
        .replace('（', '(')
        .replace('）', ')')
        .replace("　", "")
        .replace(" ", "")
        .lowercase(Locale.ROOT)
}
