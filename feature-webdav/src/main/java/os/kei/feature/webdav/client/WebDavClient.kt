package os.kei.feature.webdav.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import os.kei.core.log.AppLogger
import os.kei.feature.webdav.model.WebDavConfig
import os.kei.feature.webdav.model.WebDavRemoteFile
import os.kei.feature.webdav.model.WebDavResult
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Lightweight WebDAV client built on OkHttp.
 * Supports GET, PUT, DELETE, PROPFIND, MKCOL.
 */
class WebDavClient(
    private val config: WebDavConfig,
    okHttpClient: OkHttpClient? = null,
) {
    private val credential = Credentials.basic(config.username, config.appPassword)
    private val client = (okHttpClient ?: defaultClient()).newBuilder()
        .followRedirects(false)
        .build()

    private fun baseUrl(): String =
        config.serverUrl.trimEnd('/') + "/" + config.remoteDir.trimStart('/')

    private fun url(fileName: String): String =
        baseUrl() + fileName

    // ── GET ──────────────────────────────────────────────────────────

    suspend fun get(fileName: String): WebDavResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url(fileName))
            .header("Authorization", credential)
            .get()
            .build()
        executeString(request, "GET $fileName")
    }

    // ── PUT ──────────────────────────────────────────────────────────

    suspend fun put(fileName: String, json: String, etag: String? = null): WebDavResult =
        withContext(Dispatchers.IO) {
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val builder = Request.Builder()
                .url(url(fileName))
                .header("Authorization", credential)
                .put(body)
            if (etag != null) builder.header("If-Match", etag)
            executeString(builder.build(), "PUT $fileName")
        }

    // ── DELETE ───────────────────────────────────────────────────────

    suspend fun delete(fileName: String): WebDavResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url(fileName))
            .header("Authorization", credential)
            .delete()
            .build()
        executeString(request, "DELETE $fileName")
    }

    // ── MKCOL (create directory) ─────────────────────────────────────

    suspend fun mkdir(): WebDavResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl())
            .header("Authorization", credential)
            .method("MKCOL", null)
            .build()
        executeString(request, "MKCOL ${config.remoteDir}")
    }

    // ── PROPFIND (list files) ────────────────────────────────────────

    suspend fun list(): List<WebDavRemoteFile> = withContext(Dispatchers.IO) {
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
                <D:prop>
                    <D:displayname/>
                    <D:getlastmodified/>
                    <D:getcontentlength/>
                    <D:getetag/>
                </D:prop>
            </D:propfind>
        """.trimIndent()
        val request = Request.Builder()
            .url(baseUrl())
            .header("Authorization", credential)
            .header("Depth", "1")
            .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code !in 200..299) return@withContext emptyList()
                val body = response.body?.string().orEmpty()
                parsePropfindResponse(body)
            }
        } catch (e: IOException) {
            AppLogger.w(TAG, "PROPFIND failed", e)
            emptyList()
        } catch (e: Exception) {
            AppLogger.w(TAG, "PROPFIND unexpected exception", e)
            emptyList()
        }
    }

    // ── Test connection ──────────────────────────────────────────────

    suspend fun testConnection(): WebDavResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl())
            .header("Authorization", credential)
            .header("Depth", "0")
            .method(
                "PROPFIND",
                """<?xml version="1.0"?><D:propfind xmlns:D="DAV:"><D:prop><D:resourcetype/></D:prop></D:propfind>""".trimIndent()
                    .toRequestBody("application/xml".toMediaType()),
            )
            .build()
        executeString(request, "PROPFIND (test)")
    }

    // ── Internal ─────────────────────────────────────────────────────

    private fun executeString(request: Request, label: String): WebDavResult {
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val etag = response.header("ETag")
                if (response.code in 200..299) {
                    WebDavResult.Success(etag = etag, body = body)
                } else {
                    AppLogger.w(TAG, "$label failed: ${response.code} $body")
                    WebDavResult.Failure(response.code, body.ifBlank { "HTTP ${response.code}" })
                }
            }
        } catch (e: IOException) {
            AppLogger.w(TAG, "$label exception", e)
            WebDavResult.Failure(0, e.message ?: "IOException")
        } catch (e: Exception) {
            AppLogger.w(TAG, "$label unexpected exception", e)
            WebDavResult.Failure(0, e.message ?: "Unknown error")
        }
    }

    companion object {
        private const val TAG = "WebDavClient"

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
    }
}

// ── PROPFIND XML parsing (minimal, no XML library needed) ────────────

private fun parsePropfindResponse(xml: String): List<WebDavRemoteFile> {
    val results = mutableListOf<WebDavRemoteFile>()
    val responseBlocks = xml.split("<D:response>").drop(1)
    for (block in responseBlocks) {
        val endIdx = block.indexOf("</D:response>")
        val content = if (endIdx >= 0) block.substring(0, endIdx) else block
        val href = extractTag(content, "D:href") ?: continue
        val displayName = extractTag(content, "D:displayname").orEmpty()
        val lastModified = extractTag(content, "D:getlastmodified")
        val contentLength = extractTag(content, "D:getcontentlength")?.toLongOrNull() ?: 0L
        val etag = extractTag(content, "D:getetag")
        if (displayName.isNotBlank()) {
            results.add(
                WebDavRemoteFile(
                    href = href,
                    displayName = displayName,
                    lastModified = lastModified,
                    contentLength = contentLength,
                    etag = etag,
                ),
            )
        }
    }
    return results
}

private fun extractTag(xml: String, tag: String): String? {
    val start = xml.indexOf("<$tag>")
    if (start < 0) return null
    val valueStart = start + tag.length + 2
    val end = xml.indexOf("</$tag>", valueStart)
    if (end < 0) return null
    return xml.substring(valueStart, end).trim()
}
