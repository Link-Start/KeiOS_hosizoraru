package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
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
        val jsonUrl = directApkUrl.companionJsonUrl() ?: return@runCatching null
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
            val obj = JSONObject(raw)
            val fileUrl = obj.optString("file_url").trim()
            if (fileUrl.isBlank()) return@use null
            GitHubDirectApkJsonFallback(
                sourceUrl = jsonUrl,
                fileUrl = fileUrl,
                versionName = obj.optString("version").trim(),
                versionCode = obj.opt("version_code")?.toString()?.trim().orEmpty(),
                changelog = obj.optString("changelog").trim()
            )
        }
    }

    private fun String.companionJsonUrl(): String? {
        val value = trim()
        if (value.isBlank() || value.endsWith(".json", ignoreCase = true)) return null
        return "$value.json"
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

private fun directApkFileNameFromUrl(url: String): String {
    return runCatching {
        URI(url).path.substringAfterLast('/').trim()
    }.getOrDefault("")
}
