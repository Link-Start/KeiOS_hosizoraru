package os.kei.feature.github.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import os.kei.core.io.SharedHttpClient
import os.kei.core.json.jsonPrimitiveOrNull
import os.kei.core.json.optArray
import os.kei.core.json.optObject
import os.kei.core.json.parseJsonArrayOrNull
import os.kei.core.json.parseJsonObjectOrNull
import java.net.URI
import kotlin.time.Duration.Companion.seconds

data class GitHubDirectApkJsonFallback(
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

class GitHubDirectApkJsonFallbackResolver(
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

    private fun parseJsonFeedObject(raw: String): JsonObject? {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("{") -> {
                val root = trimmed.parseJsonObjectOrNull() ?: return null
                root.findFeedObject() ?: root
            }

            trimmed.startsWith("[") -> trimmed.parseJsonArrayOrNull()?.firstFeedObject()
            else -> null
        }
    }

    private fun JsonObject.findFeedObject(): JsonObject? {
        if (firstNonBlankString("file_url", "download_url", "apk_url", "url").isNotBlank()) {
            return this
        }
        arrayOf("releases", "items", "assets", "downloads", "versions").forEach { key ->
            optArray(key)?.firstFeedObject()?.let { return it }
        }
        arrayOf("release", "latest", "android").forEach { key ->
            optObject(key)?.findFeedObject()?.let { return it }
        }
        return null
    }

    private fun JsonArray.firstFeedObject(): JsonObject? {
        for (element in this) {
            val obj = element as? JsonObject ?: continue
            obj.findFeedObject()?.let { return it }
        }
        return null
    }

    private fun JsonObject.firstNonBlankString(vararg keys: String): String {
        keys.forEach { key ->
            val element = this[key]?.takeUnless { it is JsonNull } ?: return@forEach
            val value = element.jsonPrimitiveOrNull()?.contentOrNull?.trim()
                ?: element.toString().trim()
            if (value.isNotBlank() && value != "null") return value
        }
        return ""
    }

    private companion object {
        const val USER_AGENT = "KeiOS-App/1.0 (Android)"
        val defaultClient: OkHttpClient = SharedHttpClient.base.newBuilder()
            .connectTimeout(12.seconds)
            .readTimeout(20.seconds)
            .callTimeout(28.seconds)
            .build()
    }
}

fun directApkFileNameFromUrl(url: String): String {
    return runCatching {
        URI(url).path.substringAfterLast('/').trim()
    }.getOrDefault("")
}
