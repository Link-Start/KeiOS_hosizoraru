package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import kotlin.time.Duration.Companion.seconds

internal data class GitHubDirectApkJsonFallback(
    val sourceUrl: String,
    val fileUrl: String,
    val versionName: String,
    val versionCode: String,
    val changelog: String
) {
    fun toAsset(): GitHubReleaseAssetFile {
        return GitHubReleaseAssetFile(
            name = directApkFileNameFromUrl(fileUrl).ifBlank { "remote.apk" },
            downloadUrl = fileUrl,
            sizeBytes = 0L,
            downloadCount = 0,
            contentType = "application/vnd.android.package-archive"
        )
    }
}

internal class GitHubDirectApkJsonFallbackResolver(
    private val client: OkHttpClient = defaultClient
) {
    fun resolve(directApkUrl: String): Result<GitHubDirectApkJsonFallback?> = runCatching {
        val jsonUrl = directApkUrl.jsonFeedUrl() ?: return@runCatching null
        val request = Request.Builder()
            .url(jsonUrl)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json,text/plain,*/*")
            .header("Cache-Control", "no-store")
            .header("Pragma", "no-cache")
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "direct APK companion JSON failed (HTTP ${response.code})"
            }
            val raw = response.body.string()
            val obj = parseJsonFeedObject(raw) ?: return@use null
            val fileUrl = obj.firstNonBlankString(
                "file_url",
                "download_url",
                "apk_url",
                "url"
            )
            if (fileUrl.isBlank()) return@use null
            GitHubDirectApkJsonFallback(
                sourceUrl = jsonUrl,
                fileUrl = fileUrl,
                versionName = obj.firstNonBlankString(
                    "version_name",
                    "versionName",
                    "version",
                    "tag_name",
                    "tag"
                ),
                versionCode = obj.firstNonBlankString(
                    "version_code",
                    "versionCode",
                    "build",
                    "build_number"
                ),
                changelog = obj.firstNonBlankString(
                    "changelog",
                    "release_notes",
                    "releaseNotes",
                    "notes",
                    "body"
                )
            )
        }
    }

    private fun String.jsonFeedUrl(): String? {
        val value = trim()
        if (value.isBlank()) return null
        if (value.endsWith(".json", ignoreCase = true)) return value
        return "$value.json"
    }

    private fun parseJsonFeedObject(raw: String): JSONObject? {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                root.findFeedObject() ?: root
            }

            trimmed.startsWith("[") -> JSONArray(trimmed).firstFeedObject()
            else -> null
        }
    }

    private fun JSONObject.findFeedObject(): JSONObject? {
        if (firstNonBlankString("file_url", "download_url", "apk_url", "url").isNotBlank()) {
            return this
        }
        arrayOf("releases", "items", "assets", "downloads", "versions").forEach { key ->
            optJSONArray(key)?.firstFeedObject()?.let { return it }
        }
        arrayOf("release", "latest", "android").forEach { key ->
            optJSONObject(key)?.findFeedObject()?.let { return it }
        }
        return null
    }

    private fun JSONArray.firstFeedObject(): JSONObject? {
        for (index in 0 until length()) {
            val obj = optJSONObject(index) ?: continue
            obj.findFeedObject()?.let { return it }
        }
        return null
    }

    private fun JSONObject.firstNonBlankString(vararg keys: String): String {
        keys.forEach { key ->
            val value = opt(key)?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && value != "null") return value
        }
        return ""
    }

    private companion object {
        const val USER_AGENT = "KeiOS-App/1.0 (Android)"
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12.seconds)
            .readTimeout(20.seconds)
            .callTimeout(28.seconds)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}

internal fun directApkFileNameFromUrl(url: String): String {
    return runCatching {
        URI(url).path.substringAfterLast('/').trim()
    }.getOrDefault("")
}
