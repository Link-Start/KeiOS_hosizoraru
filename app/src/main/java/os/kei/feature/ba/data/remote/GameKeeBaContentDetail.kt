package os.kei.feature.ba.data.remote

import org.json.JSONObject

internal enum class GameKeeBaContentSource {
    InlineContentJson,
    InlineContent,
    ContentCdn,
    Empty
}

internal data class GameKeeBaContentDetail(
    val contentId: Long,
    val title: String,
    val subtitle: String,
    val summary: String,
    val mediaPayload: JSONObject,
    val inlineContentJson: String,
    val inlineContent: String,
    val contentCdnUrl: String,
    val resolvedContentJson: String,
    val contentSource: GameKeeBaContentSource,
    val errors: List<String>
) {
    val errorSummary: String
        get() = errors.joinToString(" | ").trim()
}

internal fun resolveBaContentDetailFromApiJson(
    contentId: Long,
    refererPath: String,
    apiBody: String,
    fetchCdnJson: (GameKeeNetworkRequest) -> GameKeeNetworkResult<String>
): GameKeeBaContentDetail {
    val root = JSONObject(apiBody)
    if (root.optInt("code", -1) != 0) {
        error("api code=${root.optInt("code", -1)}")
    }
    val data = root.optJSONObject("data") ?: error("empty data")
    val gameName = data.optJSONObject("game")?.optString("name").orEmpty()
    val inlineContentJson = data.optString("content_json").trim()
    val inlineContent = data.optString("content").trim()
    val contentCdnUrl = normalizeGameKeeContentCdnUrl(data.optString("content_cdn"))
    val errors = mutableListOf<String>()

    resolveGameKeeContentJson(inlineContentJson)?.let { content ->
        return data.toGameKeeBaContentDetail(
            contentId = contentId,
            gameName = gameName,
            inlineContentJson = inlineContentJson,
            inlineContent = inlineContent,
            contentCdnUrl = contentCdnUrl,
            resolvedContentJson = content,
            contentSource = GameKeeBaContentSource.InlineContentJson,
            errors = errors
        )
    }

    resolveGameKeeContentJson(inlineContent)?.let { content ->
        return data.toGameKeeBaContentDetail(
            contentId = contentId,
            gameName = gameName,
            inlineContentJson = inlineContentJson,
            inlineContent = inlineContent,
            contentCdnUrl = contentCdnUrl,
            resolvedContentJson = content,
            contentSource = GameKeeBaContentSource.InlineContent,
            errors = errors
        )
    }

    if (contentCdnUrl.isNotBlank()) {
        when (val cdnResult = fetchCdnJson(
            GameKeeNetworkRequest(
                pathOrUrl = contentCdnUrl,
                refererPath = refererPath
            )
        )) {
            is GameKeeNetworkResult.Success -> {
                resolveGameKeeContentJson(cdnResult.value)?.let { content ->
                    return data.toGameKeeBaContentDetail(
                        contentId = contentId,
                        gameName = gameName,
                        inlineContentJson = inlineContentJson,
                        inlineContent = inlineContent,
                        contentCdnUrl = contentCdnUrl,
                        resolvedContentJson = content,
                        contentSource = GameKeeBaContentSource.ContentCdn,
                        errors = errors
                    )
                }
                errors += "cdn-empty"
            }

            is GameKeeNetworkResult.Failure -> {
                errors += "cdn:${cdnResult.errorPreview.ifBlank { "failed" }}"
            }
        }
    } else {
        errors += "cdn-missing"
    }

    if (inlineContentJson.isBlank()) errors += "content_json-empty"
    if (inlineContent.isBlank()) errors += "content-empty"

    return data.toGameKeeBaContentDetail(
        contentId = contentId,
        gameName = gameName,
        inlineContentJson = inlineContentJson,
        inlineContent = inlineContent,
        contentCdnUrl = contentCdnUrl,
        resolvedContentJson = "",
        contentSource = GameKeeBaContentSource.Empty,
        errors = errors
    )
}

internal fun resolveGameKeeContentJson(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    if (trimmed.startsWith("[")) return trimmed
    if (!trimmed.startsWith("{")) return null

    val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
    if (root.has("baseData") || root.has("styleData")) return trimmed

    val content = root.optString("content").trim()
    if (content.isNotBlank()) {
        resolveGameKeeContentJson(content)?.let { return it }
    }

    val data = root.optJSONObject("data")
    if (data != null) {
        resolveGameKeeContentJson(data.toString())?.let { return it }
    }

    return null
}

internal fun normalizeGameKeeContentCdnUrl(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    if (value.startsWith("http://", ignoreCase = true)) {
        return value.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
    }
    if (value.startsWith("https://", ignoreCase = true)) return value
    if (value.startsWith("//")) return "https:$value"
    return value
}

private fun JSONObject.toGameKeeBaContentDetail(
    contentId: Long,
    gameName: String,
    inlineContentJson: String,
    inlineContent: String,
    contentCdnUrl: String,
    resolvedContentJson: String,
    contentSource: GameKeeBaContentSource,
    errors: List<String>
): GameKeeBaContentDetail {
    return GameKeeBaContentDetail(
        contentId = contentId,
        title = optString("title").trim().ifBlank { "图鉴信息" },
        subtitle = gameName.ifBlank { "GameKee" },
        summary = optString("summary"),
        mediaPayload = JSONObject().apply {
            put("thumb", opt("thumb"))
            put("image_list", opt("image_list"))
            put("thumb_list", opt("thumb_list"))
            put("video_list", opt("video_list"))
        },
        inlineContentJson = inlineContentJson,
        inlineContent = inlineContent,
        contentCdnUrl = contentCdnUrl,
        resolvedContentJson = resolvedContentJson,
        contentSource = contentSource,
        errors = errors.toList()
    )
}
