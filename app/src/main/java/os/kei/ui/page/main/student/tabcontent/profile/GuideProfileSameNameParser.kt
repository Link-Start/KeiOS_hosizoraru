package os.kei.ui.page.main.student.tabcontent.profile

import androidx.core.net.toUri
import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.containsGuideWebLink
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import os.kei.ui.page.main.student.isRenderableGalleryImageUrl

internal data class SameNameRoleItem(
    val name: String,
    val linkUrl: String,
    val imageUrl: String,
)

internal fun isSameNameRoleRow(row: BaGuideRow): Boolean {
    val key = normalizeProfileFieldKey(row.key)
    return key == relatedSameNameRoleHeaderKey || key == sameNameRoleNameRowKey
}

internal fun isRelatedRoleRow(row: BaGuideRow): Boolean {
    val key = normalizeProfileFieldKey(row.key)
    return key == relatedRoleHeaderKey ||
        key == relatedRoleNameRowKey ||
        key == relatedSameNameRoleHeaderKey ||
        key == sameNameRoleNameRowKey
}

internal fun splitRoleRowTokens(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return raw
        .split(Regex("""\s*(?:/|／|\||｜|\n)\s*"""))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

internal val sameNameGuideEmbeddedLinkRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
internal val sameNameGuidePathPattern = Regex("""^/(?:ba/tj/\d+(?:\.html)?|ba/\d+(?:\.html)?|v1/content/detail/\d+)$""")

internal fun sanitizeSameNameLinkToken(raw: String): String {
    return raw.trim().trimEnd(')', ']', '}', ',', '。', '，', ';', '；')
}

internal fun extractSameNameGuideLink(raw: String): String {
    val source = raw.trim()
    if (source.isBlank()) return ""

    val directCandidates = buildList {
        val cleaned = sanitizeSameNameLinkToken(source)
        if (cleaned.startsWith("http://", ignoreCase = true) || cleaned.startsWith("https://", ignoreCase = true)) {
            add(cleaned)
        } else if (cleaned.startsWith("www.", ignoreCase = true)) {
            add("https://$cleaned")
        } else if (cleaned.matches(Regex("""^\d{4,}$"""))) {
            add("https://www.gamekee.com/ba/tj/$cleaned.html")
        } else if (cleaned.startsWith("/") && sameNameGuidePathPattern.matches(cleaned)) {
            add(normalizeGuideUrl(cleaned))
        }
        addAll(
            sameNameGuideEmbeddedLinkRegex.findAll(source).map { match ->
                sanitizeSameNameLinkToken(match.value)
            }
        )
    }.distinct()

    for (candidate in directCandidates) {
        val normalized = normalizeGuideUrl(candidate)
        if (normalized.isBlank()) continue
        val uri = runCatching { normalized.toUri() }.getOrNull() ?: continue
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty()
        val hostAccepted = host == "www.gamekee.com" || host == "gamekee.com"
        val pathAccepted = sameNameGuidePathPattern.matches(path)
        if (!hostAccepted || !pathAccepted) continue
        val contentId = extractGuideContentIdFromUrl(normalized) ?: continue
        if (contentId <= 0L) continue
        return "https://www.gamekee.com/ba/tj/$contentId.html"
    }
    return ""
}

internal val sameNameRoleHintKeywords = listOf(
    "暂无同名角色",
    "未填写",
    "占位",
    "说明",
    "备注",
    "复制",
    "不用写",
    "暂时没",
    "待补充"
)

internal fun isSameNameRoleHintText(raw: String): Boolean {
    val value = raw.trim()
    if (value.isBlank()) return false
    val compact = value
        .replace(" ", "")
        .replace("　", "")
        .lowercase()
    if (compact.length >= 20) return true
    return sameNameRoleHintKeywords.any { keyword ->
        compact.contains(keyword.lowercase())
    }
}

internal fun extractSameNameRoleHint(row: BaGuideRow): String? {
    if (normalizeProfileFieldKey(row.key) != relatedSameNameRoleHeaderKey) return null
    if (isRelationSectionTitle(row.value)) return null
    return extractRelationRoleHint(row)
}

internal fun extractRelatedRoleHint(row: BaGuideRow): String? {
    if (normalizeProfileFieldKey(row.key) != relatedRoleHeaderKey) return null
    if (isRelationSectionTitle(row.value)) return null
    return extractRelationRoleHint(row)
}

private fun extractRelationRoleHint(row: BaGuideRow): String? {
    val rawValue = row.value.trim().trim('*')
    if (rawValue.isBlank()) return null
    if (isProfileValuePlaceholder(rawValue)) return null
    val hasLink = extractProfileExternalLink(rawValue).isNotBlank()
    val hasImage = buildList {
        add(row.imageUrl.trim())
        addAll(row.imageUrls.map { it.trim() })
    }.any { candidate ->
        candidate.isNotBlank() && isRenderableGalleryImageUrl(candidate)
    }
    if (hasLink || hasImage) return null
    if (!isSameNameRoleHintText(rawValue)) return null
    return rawValue
}

internal fun buildSameNameRoleItems(rows: List<BaGuideRow>): List<SameNameRoleItem> {
    return buildRelationRoleItems(
        rows = rows,
        headerKey = relatedSameNameRoleHeaderKey,
        itemKey = sameNameRoleNameRowKey,
        fallbackName = "同名角色",
    )
}

internal fun buildRelatedRoleItems(rows: List<BaGuideRow>): List<SameNameRoleItem> {
    return buildRelationRoleItems(
        rows = rows,
        headerKey = relatedRoleHeaderKey,
        itemKey = relatedRoleNameRowKey,
        fallbackName = "相关角色",
    )
}

internal fun extractRelatedRoleSectionTitle(rows: List<BaGuideRow>): String {
    return rows.firstNotNullOfOrNull { row ->
        extractRelationSectionTitle(row, relatedRoleHeaderKey)
    }.orEmpty()
}

internal fun extractSameNameRoleSectionTitle(rows: List<BaGuideRow>): String {
    return rows.firstNotNullOfOrNull { row ->
        extractRelationSectionTitle(row, relatedSameNameRoleHeaderKey)
    }.orEmpty()
}

private fun extractRelationSectionTitle(
    row: BaGuideRow,
    headerKey: String,
): String? {
    if (normalizeProfileFieldKey(row.key) != headerKey) return null
    val title = row.value.trim().trim('*')
    if (!isRelationSectionTitle(title)) return null
    return title
}

private fun isRelationSectionTitle(raw: String): Boolean {
    val compact = normalizeProfileFieldKey(raw)
    if (compact.isBlank()) return false
    return compact == relatedRoleHeaderKey ||
        compact == normalizeProfileFieldKey("相关人物") ||
        compact == relatedSameNameRoleHeaderKey ||
        compact == normalizeProfileFieldKey("同名角色")
}

private fun buildRelationRoleItems(
    rows: List<BaGuideRow>,
    headerKey: String,
    itemKey: String,
    fallbackName: String,
): List<SameNameRoleItem> {
    if (rows.isEmpty()) return emptyList()
    val items = rows.mapNotNull { row ->
        val normalizedKey = normalizeProfileFieldKey(row.key)
        if (normalizedKey != itemKey && normalizedKey != headerKey) {
            return@mapNotNull null
        }
        if (normalizedKey == headerKey && isRelationSectionTitle(row.value)) {
            return@mapNotNull null
        }
        val tokens = splitRoleRowTokens(row.value)
        val link = sequence<String> {
            tokens.forEach { yield(it) }
            yield(row.value)
        }.map { token -> extractSameNameGuideLink(token) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val name = tokens.firstOrNull { token ->
            token.isNotBlank() &&
                !isProfileValuePlaceholder(token) &&
                extractProfileExternalLink(token).isBlank() &&
                !isSameNameRoleHintText(token)
        }.orEmpty()
        val image = (row.imageUrls + row.imageUrl)
            .firstOrNull { candidate -> isRenderableGalleryImageUrl(candidate) }
            .orEmpty()
        if (name.isBlank() && link.isBlank() && image.isBlank()) {
            return@mapNotNull null
        }
        if (name.isBlank() && link.isBlank() && isSameNameRoleHintText(row.value)) {
            return@mapNotNull null
        }
        val resolvedFallbackName = when {
            link.isNotBlank() -> fallbackProfileLinkTitle(link)
            else -> fallbackName
        }
        SameNameRoleItem(
            name = name.ifBlank { resolvedFallbackName },
            linkUrl = link,
            imageUrl = image,
        )
    }

    return items.distinctBy { item ->
        "${item.name.trim()}|${item.linkUrl.trim()}|${item.imageUrl.trim()}"
    }
}

internal val galleryRelatedProfileLinkKeyTokens = listOf(
    "影画相关链接",
    "相关链接",
    "来源链接",
    "个人账号主页",
    "账号主页",
    "个人主页",
    "主页链接",
    "主页"
).map(::normalizeProfileFieldKey)

internal fun isGalleryRelatedProfileLinkRow(row: BaGuideRow): Boolean {
    val key = normalizeProfileFieldKey(row.key)
    if (key.isBlank()) return false
    val hasGalleryLinkKey = galleryRelatedProfileLinkKeyTokens.any { token ->
        token.isNotBlank() && key.contains(token)
    }
    if (!hasGalleryLinkKey) return false
    val linkSource = buildString {
        append(row.value)
        append(' ')
        append(row.key)
    }
    return containsGuideWebLink(linkSource)
}
