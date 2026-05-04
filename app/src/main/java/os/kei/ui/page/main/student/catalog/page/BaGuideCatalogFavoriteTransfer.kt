package os.kei.ui.page.main.student.catalog.page

import org.json.JSONArray
import org.json.JSONObject

private const val CatalogFavoritesExportType = "keios.ba.catalog_favorites"
private const val CatalogFavoritesExportVersion = 1

internal fun buildCatalogFavoritesExportJson(
    favorites: Map<Long, Long>,
    nowMs: Long = System.currentTimeMillis()
): String {
    val normalized = favorites
        .filterKeys { it > 0L }
        .filterValues { it > 0L }
        .toSortedMap()
    return JSONObject().apply {
        put("type", CatalogFavoritesExportType)
        put("version", CatalogFavoritesExportVersion)
        put("exportedAtMs", nowMs.coerceAtLeast(1L))
        put(
            "favorites",
            JSONArray().apply {
                normalized.forEach { (contentId, favoritedAtMs) ->
                    put(
                        JSONObject().apply {
                            put("contentId", contentId)
                            put("favoritedAtMs", favoritedAtMs)
                        }
                    )
                }
            }
        )
    }.toString()
}

internal fun parseCatalogFavoritesExport(raw: String): Map<Long, Long> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return emptyMap()
    return runCatching {
        if (trimmed.startsWith("[")) {
            parseCatalogFavoritesArray(JSONArray(trimmed))
        } else {
            val root = JSONObject(trimmed)
            val array = root.optJSONArray("favorites")
                ?: root.optJSONArray("catalogFavorites")
                ?: root.optJSONArray("students")
            if (array != null) {
                parseCatalogFavoritesArray(array)
            } else {
                parseCatalogFavoritesObject(root)
            }
        }
    }.getOrDefault(emptyMap())
}

private fun parseCatalogFavoritesArray(array: JSONArray): Map<Long, Long> {
    return buildMap {
        for (index in 0 until array.length()) {
            val value = array.opt(index)
            when (value) {
                is Number -> putNormalized(contentId = value.toLong(), favoritedAtMs = 0L)
                is String -> putNormalized(contentId = value.toLongOrNull() ?: 0L, favoritedAtMs = 0L)
                is JSONObject -> putNormalized(
                    contentId = value.optLong("contentId", value.optLong("id", 0L)),
                    favoritedAtMs = value.optLong(
                        "favoritedAtMs",
                        value.optLong("favoritedAt", value.optLong("timestamp", 0L))
                    )
                )
            }
        }
    }
}

private fun parseCatalogFavoritesObject(root: JSONObject): Map<Long, Long> {
    return buildMap {
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next().trim()
            val contentId = key.toLongOrNull() ?: continue
            putNormalized(
                contentId = contentId,
                favoritedAtMs = root.optLong(key, 0L)
            )
        }
    }
}

private fun MutableMap<Long, Long>.putNormalized(
    contentId: Long,
    favoritedAtMs: Long
) {
    if (contentId <= 0L) return
    this[contentId] = favoritedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis().coerceAtLeast(1L)
}
