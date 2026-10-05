package os.kei.ui.page.main.student.fetch

import org.json.JSONArray
import org.json.JSONObject
import os.kei.ui.page.main.student.BaGuideWebMemoryLobby
import os.kei.ui.page.main.student.gameKeeMemoryLobbyViewerUrl
import os.kei.ui.page.main.student.gameKeeSpineAssetUrl
import os.kei.ui.page.main.student.decodeSpineViewport
import java.net.URI

internal fun extractWebMemoryLobbies(sourceUrl: String, value: Any?): List<BaGuideWebMemoryLobby> {
    val viewerUrl = gameKeeMemoryLobbyViewerUrl(sourceUrl)
    if (viewerUrl.isBlank()) return emptyList()
    fun asset(raw: String): String {
        if (isPlaceholderMediaToken(raw)) return ""
        val original = runCatching { URI(raw.trim()) }.getOrNull() ?: return ""
        if (original.scheme != null && original.scheme !in listOf("https", "http")) return ""
        val normalized = normalizeMediaUrl(sourceUrl, raw)
        return gameKeeSpineAssetUrl(normalized)
    }
    fun extract(any: Any?, depth: Int): List<BaGuideWebMemoryLobby> {
        if (depth > 5) return emptyList()
        return when (any) {
            is JSONArray -> (0 until any.length()).flatMap { extract(any.opt(it), depth + 1) }
            is String -> runCatching { extract(JSONObject(any), depth + 1) }.getOrDefault(emptyList())
            is JSONObject -> {
                val atlas = asset(any.optString("atlas"))
                val skeleton = asset(any.optString("skel"))
                val json = asset(any.optString("json"))
                if (atlas.isBlank() || (skeleton.isBlank() && json.isBlank())) emptyList()
                else {
                    val images = any.opt("image")
                    val textures = if (images is JSONArray) {
                        (0 until images.length()).map { images.optString(it) }
                    } else any.optString("image").split(',')
                    listOf(BaGuideWebMemoryLobby(
                        viewerUrl = viewerUrl,
                        atlasUrl = atlas,
                        skeletonUrl = skeleton,
                        jsonUrl = json,
                        textureUrls = textures.map { asset(it.trim()) }.filter(String::isNotBlank).distinct(),
                        animation = any.optString("animation").trim(),
                        skin = any.optString("skin").trim(),
                        viewport = decodeSpineViewport(any.optJSONObject("position")?.optJSONObject("pc")?.optJSONObject("fullscreen")),
                    ))
                }
            }
            else -> emptyList()
        }
    }
    return extract(value, 0).distinct()
}
