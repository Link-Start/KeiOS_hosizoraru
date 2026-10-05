package os.kei.ui.page.main.student

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

internal const val BA_GUIDE_GALLERY_PARSER_VERSION = 2

/** Resource metadata is independent of the Wiki's page layout. */
@Immutable
data class BaGuideWebMemoryLobby(
    val viewerUrl: String,
    val atlasUrl: String,
    val skeletonUrl: String = "",
    val jsonUrl: String = "",
    val textureUrls: List<String> = emptyList(),
    val animation: String = "",
    val skin: String = "",
    val viewport: BaGuideSpineViewport? = null,
    val bgmUrl: String = "",
)

/** Spine coordinates use an upward Y axis, unlike the Android/Pixi viewport. */
@Immutable
data class BaGuideSpineViewport(val x: Double, val y: Double, val width: Double, val height: Double)

internal fun decodeSpineViewport(value: JSONObject?): BaGuideSpineViewport? {
    value ?: return null
    val numbers = listOf("x", "y", "width", "height").map { value.optDouble(it, Double.NaN) }
    if (numbers.any { !it.isFinite() || kotlin.math.abs(it) > 100_000 } || numbers[2] <= 0 || numbers[3] <= 0) return null
    return BaGuideSpineViewport(numbers[0], numbers[1], numbers[2], numbers[3])
}

internal fun gameKeeSpineAssetUrl(source: String): String {
    val uri = runCatching { URI(source.trim()) }.getOrNull() ?: return ""
    val host = uri.host?.lowercase().orEmpty()
    return source.trim().takeIf {
        uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
            (host == "gamekee.com" || host.endsWith(".gamekee.com"))
    }.orEmpty()
}

internal fun gameKeeMemoryLobbyViewerUrl(source: String): String {
    val uri = runCatching { URI(source.trim()) }.getOrNull() ?: return ""
    if (uri.scheme !in listOf("https", "http") || uri.userInfo != null) return ""
    if (uri.host?.lowercase() !in listOf("www.gamekee.com", "gamekee.com")) return ""
    val id = Regex("^/ba/(?:tj/)?(\\d+)(?:\\.html)?/?$").matchEntire(uri.path.orEmpty())?.groupValues?.get(1)
        ?: return ""
    return "https://www.gamekee.com/ba/$id?tab=3"
}

internal fun BaGuideWebMemoryLobby.toJson(): JSONObject = JSONObject().apply {
    put("viewerUrl", viewerUrl)
    put("atlas", atlasUrl)
    put("skel", skeletonUrl)
    put("json", jsonUrl)
    put("image", JSONArray(textureUrls))
    put("animation", animation)
    put("skin", skin)
    put("bgm", bgmUrl)
    viewport?.let {
        put("viewport", JSONObject().put("x", it.x).put("y", it.y).put("width", it.width).put("height", it.height))
    }
}

internal fun decodeWebMemoryLobby(value: JSONObject?): BaGuideWebMemoryLobby? {
    value ?: return null
    val viewer = gameKeeMemoryLobbyViewerUrl(value.optString("viewerUrl"))
    val atlas = gameKeeSpineAssetUrl(value.optString("atlas"))
    val skeleton = gameKeeSpineAssetUrl(value.optString("skel"))
    val json = gameKeeSpineAssetUrl(value.optString("json"))
    if (viewer.isBlank() || atlas.isBlank() || (skeleton.isBlank() && json.isBlank())) return null
    val images = value.optJSONArray("image")
    return BaGuideWebMemoryLobby(
        viewerUrl = viewer,
        atlasUrl = atlas,
        skeletonUrl = skeleton,
        jsonUrl = json,
        textureUrls = buildList {
            if (images != null) for (i in 0 until images.length()) {
                gameKeeSpineAssetUrl(images.optString(i)).takeIf(String::isNotBlank)?.let(::add)
            }
        },
        animation = value.optString("animation"),
        skin = value.optString("skin"),
        viewport = decodeSpineViewport(value.optJSONObject("viewport")),
        bgmUrl = gameKeeSpineAssetUrl(value.optString("bgm")).takeIf(::isRenderableGalleryAudioUrl).orEmpty(),
    )
}

/** Derive music from cached gallery audio too, so older resource metadata needs no refetch. */
internal fun resolveWebMemoryLobbyBgm(items: List<BaGuideGalleryItem>): String = items.asSequence()
    .filter { it.mediaType.equals("audio", ignoreCase = true) && isGuideBgmFavoriteCandidateTitle(it.title, it.title) }
    .map { gameKeeSpineAssetUrl(it.mediaUrl) }
    .firstOrNull(::isRenderableGalleryAudioUrl).orEmpty()
