package os.kei.ui.page.main.student.fetch

import os.kei.ui.page.main.student.fetch.parser.parseGuideDetailFromArrayContentJson
import os.kei.ui.page.main.student.fetch.parser.parseGuideDetailFromObjectContentJson

internal fun parseGuideDetailFromContentJson(raw: String, sourceUrl: String): GuideDetailExtract {
    val trimmed = unwrapGuideContentJson(raw).trimStart()
    if (trimmed.isBlank()) return GuideDetailExtract()
    if (trimmed.startsWith("[")) {
        return parseGuideDetailFromArrayContentJson(trimmed, sourceUrl)
    }
    return parseGuideDetailFromObjectContentJson(trimmed, sourceUrl)
}

private fun unwrapGuideContentJson(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.startsWith("[")) return trimmed
    if (!trimmed.startsWith("{")) return ""

    val root = runCatching { org.json.JSONObject(trimmed) }.getOrNull() ?: return ""
    if (root.has("baseData") || root.has("styleData")) return trimmed

    val content = root.optString("content").trim()
    if (content.isNotBlank()) return unwrapGuideContentJson(content)

    val data = root.optJSONObject("data")
    if (data != null) return unwrapGuideContentJson(data.toString())

    return ""
}
