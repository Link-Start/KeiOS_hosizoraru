package os.kei.ui.page.main.ba.support

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal fun gameKeeServerId(serverIndex: Int): Int {
    return when (serverIndex) {
        0 -> 16
        1 -> 17
        else -> 15
    }
}

internal fun normalizeGameKeeLink(url: String): String {
    val raw = url.trim()
    if (raw.isBlank()) return "https://www.gamekee.com/ba/huodong"
    if (raw.startsWith("http://", ignoreCase = true)) return raw.upgradeHttpSchemeToHttps()
    if (raw.startsWith("https://", ignoreCase = true)) return raw
    return if (raw.startsWith("/")) "https://www.gamekee.com$raw" else "https://www.gamekee.com/$raw"
}

internal fun normalizeGameKeeImageLink(url: String): String {
    val raw = url.trim()
    if (raw.isBlank()) return ""
    if (raw.startsWith("file://")) return raw
    if (raw.startsWith("http://", ignoreCase = true)) return raw.upgradeHttpSchemeToHttps()
    if (raw.startsWith("https://", ignoreCase = true)) return raw
    if (raw.startsWith("//")) return "https:$raw"
    return if (raw.startsWith("/")) "https://www.gamekee.com$raw" else "https://www.gamekee.com/$raw"
}

private fun String.upgradeHttpSchemeToHttps(): String {
    return replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
}

internal fun looksLikeImageUrl(raw: String): Boolean {
    val value = raw.lowercase(Locale.ROOT)
    if (value.isBlank()) return false
    val hasProtocol = value.startsWith("http://") || value.startsWith("https://") || value.startsWith("//") || value.startsWith("/")
    if (!hasProtocol) return false
    if (value.endsWith(".jpg") || value.endsWith(".jpeg") || value.endsWith(".png") || value.endsWith(".webp") || value.endsWith(".gif")) {
        return true
    }
    return value.contains("image") || value.contains("img") || value.contains("upload") || value.contains("cdn")
}

internal fun findImageLinkRecursively(any: Any?, depth: Int = 0): String {
    if (any == null || depth > 3) return ""
    return when (any) {
        is String -> {
            val normalized = normalizeGameKeeImageLink(any)
            if (looksLikeImageUrl(normalized)) normalized else ""
        }

        is JSONObject -> {
            val keys = any.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val found = findImageLinkRecursively(any.opt(key), depth + 1)
                if (found.isNotBlank()) return found
            }
            ""
        }

        is JSONArray -> {
            for (i in 0 until any.length()) {
                val found = findImageLinkRecursively(any.opt(i), depth + 1)
                if (found.isNotBlank()) return found
            }
            ""
        }

        else -> ""
    }
}

internal fun extractGameKeeImageLink(item: JSONObject): String {
    val directKeys = listOf(
        "image_url", "img_url", "cover_url", "cover", "cover_img", "cover_image",
        "topic_img", "topic_image", "title_img", "main_img", "list_img", "small_img",
        "image", "img", "pic_url", "pic", "thumb", "thumbnail", "avatar", "banner", "icon", "logo"
    )
    directKeys.forEach { key ->
        val rawValue = item.opt(key) as? String ?: return@forEach
        val value = normalizeGameKeeImageLink(rawValue)
        if (looksLikeImageUrl(value)) return value
    }

    val nestedKeys = listOf("cover", "image", "thumb", "thumbnail", "banner", "icon")
    val nestedValueKeys = listOf("url", "src", "image_url", "img_url", "cover", "thumb")
    nestedKeys.forEach { key ->
        val nested = item.optJSONObject(key) ?: return@forEach
        nestedValueKeys.forEach { nestedKey ->
            val value = normalizeGameKeeImageLink(nested.optString(nestedKey))
            if (value.isNotBlank()) return value
        }
    }

    val images = item.optJSONArray("images")
    if (images != null) {
        for (index in 0 until images.length()) {
            val imageObj = images.optJSONObject(index) ?: continue
            nestedValueKeys.forEach { nestedKey ->
                val value = normalizeGameKeeImageLink(imageObj.optString(nestedKey))
                if (value.isNotBlank()) return value
            }
        }
    }
    return findImageLinkRecursively(item)
}

internal fun parsePoolTagIds(raw: String): List<Int> {
    return Regex("\\d+")
        .findAll(raw)
        .mapNotNull { it.value.toIntOrNull() }
        .toList()
}

internal fun fetchBaCalendarEntries(
    serverIndex: Int,
    nowMs: Long = System.currentTimeMillis()
): List<BaCalendarEntry> {
    return fetchBaCalendarRemoteResult(serverIndex, nowMs).entries
}

internal fun normalizeBaCalendarEntries(
    entries: List<BaCalendarEntry>,
    nowMs: Long = System.currentTimeMillis()
): List<BaCalendarEntry> {
    val normalized = entries
        .asSequence()
        .filter { it.title.isNotBlank() }
        .map { entry ->
            val beginAtMs = entry.beginAtMs.coerceAtLeast(0L)
            val endAtMs = entry.endAtMs.coerceAtLeast(beginAtMs)
            entry.copy(
                beginAtMs = beginAtMs,
                endAtMs = endAtMs,
                isRunning = nowMs in beginAtMs until endAtMs,
                linkUrl = normalizeGameKeeLink(entry.linkUrl),
                imageUrl = normalizeGameKeeImageLink(entry.imageUrl)
            )
        }
        .toList()

    if (normalized.isEmpty()) return emptyList()

    val activeOrUpcoming = normalized.filter { it.endAtMs > nowMs }
    val activeKindIds = activeOrUpcoming.map { it.kindId }.toSet()
    val fallbackMissingKinds = normalized
        .asSequence()
        .map { it.kindId }
        .distinct()
        .filter { it !in activeKindIds }
        .mapNotNull { missingKindId ->
            normalized
                .asSequence()
                .filter { it.kindId == missingKindId }
                .maxWithOrNull(
                    compareBy<BaCalendarEntry> { it.endAtMs }
                        .thenBy { it.beginAtMs }
                )
        }
        .toList()

    val merged = buildList {
        addAll(activeOrUpcoming)
        fallbackMissingKinds.forEach { candidate ->
            if (none { it.id == candidate.id }) add(candidate)
        }
    }

    val sorted = merged
        .sortedWith(
            compareBy<BaCalendarEntry>(
                {
                    when {
                        it.isRunning -> 0
                        it.endAtMs > nowMs -> 1
                        else -> 2
                    }
                },
                {
                    when {
                        it.isRunning -> it.endAtMs
                        it.endAtMs > nowMs -> it.beginAtMs
                        else -> -it.endAtMs
                    }
                },
                { it.kindId }
            )
        )

    return sorted.take(BA_CALENDAR_MAX_ITEMS.coerceAtLeast(1))
}

internal fun normalizeBaPoolEntries(
    entries: List<BaPoolEntry>,
    nowMs: Long = System.currentTimeMillis()
): List<BaPoolEntry> {
    val normalized = entries
        .asSequence()
        .filter { it.name.isNotBlank() }
        .map { entry ->
            val startAtMs = entry.startAtMs.coerceAtLeast(0L)
            val endAtMs = entry.endAtMs.coerceAtLeast(startAtMs)
            entry.copy(
                startAtMs = startAtMs,
                endAtMs = endAtMs,
                isRunning = nowMs in startAtMs until endAtMs,
                linkUrl = normalizeGameKeeLink(entry.linkUrl),
                imageUrl = normalizeGameKeeImageLink(entry.imageUrl),
                studentGuideUrl = canonicalBaPoolStudentGuideUrlOrBlank(
                    entry.studentGuideUrl.ifBlank { entry.linkUrl }
                )
            )
        }
        .toList()
    if (normalized.isEmpty()) return emptyList()

    val activeOrUpcoming = normalized.filter { it.endAtMs > nowMs }
    val activeTagIds = activeOrUpcoming.map { it.tagId }.toSet()
    val fallbackMissingTags = BA_POOL_TAG_IDS
        .asSequence()
        .filter { it !in activeTagIds }
        .mapNotNull { missingTagId ->
            normalized
                .asSequence()
                .filter { it.tagId == missingTagId }
                .maxWithOrNull(
                    compareBy<BaPoolEntry> { it.endAtMs }
                        .thenBy { it.startAtMs }
                )
        }
        .toList()

    val merged = buildList {
        addAll(activeOrUpcoming)
        fallbackMissingTags.forEach { candidate ->
            if (none { it.id == candidate.id }) add(candidate)
        }
    }

    val sorted = merged.sortedWith(
        compareBy<BaPoolEntry>(
            {
                when {
                    it.isRunning -> 0
                    it.endAtMs > nowMs -> 1
                    else -> 2
                }
            },
            {
                when {
                    it.isRunning -> it.endAtMs
                    it.endAtMs > nowMs -> it.startAtMs
                    else -> -it.endAtMs
                }
            },
            { it.tagId },
            { it.id }
        )
    )

    return sorted.take(BA_POOL_MAX_ITEMS.coerceAtLeast(1))
}

internal fun fetchBaPoolEntriesFromAll(
    serverIndex: Int,
    nowMs: Long
): List<BaPoolEntry> {
    return fetchBaPoolEntriesFromAllRemote(serverIndex, nowMs).entries
}

internal fun fetchBaPoolEntries(
    serverIndex: Int,
    nowMs: Long = System.currentTimeMillis()
): List<BaPoolEntry> {
    return fetchBaPoolRemoteResult(serverIndex, nowMs).entries
}
