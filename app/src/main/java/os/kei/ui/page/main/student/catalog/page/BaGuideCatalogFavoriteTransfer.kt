package os.kei.ui.page.main.student.catalog.page

import org.json.JSONArray
import org.json.JSONObject

private const val CatalogFavoritesExportType = "keios.ba.catalog_favorites"
private const val CatalogAllFavoritesExportType = "keios.ba.catalog_all_favorites"
private const val CatalogFavoritesExportVersion = 1

internal data class CatalogFavoritesImportPreview(
    val importedCount: Int,
    val addedCount: Int,
    val updatedCount: Int
)

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

internal fun buildCatalogAllFavoritesExportJson(
    favorites: Map<Long, Long>,
    bgmFavoritesJson: String,
    nowMs: Long = System.currentTimeMillis()
): String {
    val catalogRoot = JSONObject(buildCatalogFavoritesExportJson(favorites, nowMs))
    val bgmRoot = runCatching { JSONObject(bgmFavoritesJson) }.getOrDefault(JSONObject())
    return JSONObject().apply {
        put("type", CatalogAllFavoritesExportType)
        put("version", CatalogFavoritesExportVersion)
        put("exportedAtMs", nowMs.coerceAtLeast(1L))
        put("catalogFavorites", catalogRoot.optJSONArray("favorites") ?: JSONArray())
        put("bgmFavorites", bgmRoot.optJSONArray("favorites") ?: JSONArray())
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

internal fun previewCatalogFavoritesImport(
    raw: String,
    currentFavorites: Map<Long, Long>
): CatalogFavoritesImportPreview {
    val imported = parseCatalogFavoritesExport(raw)
    if (imported.isEmpty()) {
        return CatalogFavoritesImportPreview(
            importedCount = 0,
            addedCount = 0,
            updatedCount = 0
        )
    }
    var added = 0
    var updated = 0
    imported.forEach { (contentId, favoritedAtMs) ->
        val current = currentFavorites[contentId]
        if (current == null) {
            added += 1
        } else if (current != favoritedAtMs) {
            updated += 1
        }
    }
    return CatalogFavoritesImportPreview(
        importedCount = imported.size,
        addedCount = added,
        updatedCount = updated
    )
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
