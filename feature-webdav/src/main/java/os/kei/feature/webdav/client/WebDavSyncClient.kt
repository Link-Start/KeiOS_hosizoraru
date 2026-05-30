@file:Suppress("DEPRECATION")

package os.kei.feature.webdav.client

import at.bitfire.dav4jvm.okhttp.BasicDigestAuthHandler
import at.bitfire.dav4jvm.okhttp.DavCollection
import at.bitfire.dav4jvm.okhttp.DavResource
import at.bitfire.dav4jvm.okhttp.exception.ConflictException
import at.bitfire.dav4jvm.okhttp.exception.DavException
import at.bitfire.dav4jvm.okhttp.exception.ForbiddenException
import at.bitfire.dav4jvm.okhttp.exception.HttpException
import at.bitfire.dav4jvm.okhttp.exception.NotFoundException
import at.bitfire.dav4jvm.okhttp.exception.PreconditionFailedException
import at.bitfire.dav4jvm.okhttp.exception.UnauthorizedException
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.dav4jvm.property.webdav.WebDAV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import os.kei.core.log.AppLogger
import os.kei.feature.webdav.model.WebDavConfig
import os.kei.feature.webdav.model.WebDavRemoteFile
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Production-grade WebDAV client wrapping dav4jvm.
 *
 * Supports Basic + Digest authentication (auto-negotiated).
 * Handles recursive directory creation, ETag-based conditional writes,
 * and proper error classification.
 */
class WebDavSyncClient(
    private val config: WebDavConfig,
) {
    private val authHandler = BasicDigestAuthHandler(
        domain = null,
        username = config.username,
        password = config.appPassword.toCharArray(),
    )

    /**
     * User-Agent interceptor.
     *
     * Some commercial WebDAV providers — Jianguoyun (坚果云) being the canonical example —
     * reject the default OkHttp User-Agent (`okhttp/<ver>`) with **HTTP 410 Gone** at the
     * edge layer before the request ever reaches the WebDAV stack. Send a recognisable,
     * application-shaped UA so requests aren't filtered out. The exact UA value is not
     * sensitive; what matters is that it does not start with `okhttp/`.
     */
    private val userAgentInterceptor = Interceptor { chain ->
        val original = chain.request()
        if (original.header("User-Agent") != null) {
            chain.proceed(original)
        } else {
            chain.proceed(original.newBuilder().header("User-Agent", USER_AGENT).build())
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .authenticator(authHandler)
        .addInterceptor(userAgentInterceptor)
        .addNetworkInterceptor(authHandler)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl: okhttp3.HttpUrl

    init {
        val url = config.serverUrl
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw IllegalArgumentException("Server URL must start with http:// or https://, got: $url")
        }
        baseUrl = url.trimEnd('/').toHttpUrl()
    }

    private fun collectionUrl() = baseUrl.resolve(config.remoteDir)!!

    private fun fileUrl(fileName: String) = collectionUrl().resolve(fileName)!!


    // ── Test connection ──────────────────────────────────────────────

    /**
     * Test connection by attempting to PROPFIND the remote directory.
     * If the directory doesn't exist (404/409), creates it automatically.
     */
    suspend fun testConnection(): WebDavTestConnectionResult = withContext(Dispatchers.IO) {
        try {
            val collection = DavCollection(httpClient, collectionUrl())
            collection.propfind(0, WebDAV.ResourceType) { _, _ -> }
            WebDavTestConnectionResult.Success(dirCreated = false)
        } catch (e: NotFoundException) {
            AppLogger.i(TAG, "Remote dir not found, creating...")
            createDirectoryRecursive()
        } catch (e: ConflictException) {
            AppLogger.i(TAG, "Remote dir conflict (parent missing), creating...")
            createDirectoryRecursive()
        } catch (e: UnauthorizedException) {
            WebDavTestConnectionResult.AuthFailed
        } catch (e: ForbiddenException) {
            WebDavTestConnectionResult.PermissionDenied
        } catch (e: HttpException) {
            // Surface the actual HTTP status (e.g. 410 Gone from Jianguoyun's edge filter).
            // Without this, DavException's catch swallows the code into "WebDAV error" with no
            // hint about why the server refused the request.
            WebDavTestConnectionResult.Error("HTTP ${e.statusCode}: ${e.message ?: "request rejected"}")
        } catch (e: DavException) {
            WebDavTestConnectionResult.Error(e.message ?: "WebDAV error")
        } catch (e: IOException) {
            WebDavTestConnectionResult.NetworkError(e.message ?: "Network unreachable")
        } catch (e: IllegalArgumentException) {
            WebDavTestConnectionResult.InvalidUrl(e.message ?: "Invalid URL")
        }
    }


    // ── Upload ───────────────────────────────────────────────────────

    /**
     * Upload a file. Uses If-Match for conditional write when [etag] is provided.
     *
     * @param fileName remote file name
     * @param content JSON content to upload
     * @param etag previous ETag for conditional write (null = unconditional)
     * @return [WebDavUploadResult] with new ETag on success
     */
    suspend fun upload(fileName: String, content: String, etag: String? = null): WebDavUploadResult =
        withContext(Dispatchers.IO) {
            try {
                ensureCollection()
                val resource = DavResource(httpClient, fileUrl(fileName))
                val body = content.toRequestBody("application/json; charset=utf-8".toMediaType())
                var resultEtag: String? = null

                resource.put(body, ifETag = etag) { response ->
                    resultEtag = response.header("ETag")?.removeSurrounding("\"")
                }
                WebDavUploadResult.Success(resultEtag)
            } catch (e: PreconditionFailedException) {
                WebDavUploadResult.Conflict
            } catch (e: NotFoundException) {
                // Directory doesn't exist, try to create and retry
                try {
                    ensureCollection()
                    val resource = DavResource(httpClient, fileUrl(fileName))
                    val body = content.toRequestBody("application/json; charset=utf-8".toMediaType())
                    var resultEtag: String? = null
                    resource.put(body, ifETag = etag) { response ->
                        resultEtag = response.header("ETag")?.removeSurrounding("\"")
                    }
                    WebDavUploadResult.Success(resultEtag)
                } catch (e2: Exception) {
                    WebDavUploadResult.Error(classifyError(e2))
                }
            } catch (e: UnauthorizedException) {
                WebDavUploadResult.Error(WebDavError.AuthFailed)
            } catch (e: ForbiddenException) {
                WebDavUploadResult.Error(WebDavError.PermissionDenied)
            } catch (e: DavException) {
                WebDavUploadResult.Error(WebDavError.Unknown(0, e.message ?: "WebDAV error"))
            } catch (e: IOException) {
                WebDavUploadResult.Error(WebDavError.NetworkUnreachable)
            }
        }


    // ── Download ─────────────────────────────────────────────────────

    /**
     * Download a file. Returns content and ETag.
     *
     * @param fileName remote file name
     * @return [WebDavDownloadResult] with content and ETag
     */
    suspend fun download(fileName: String): WebDavDownloadResult = withContext(Dispatchers.IO) {
        try {
            val resource = DavResource(httpClient, fileUrl(fileName))
            var content: String? = null
            var etag: String? = null

            resource.get("*/*", null) { response ->
                content = response.body.string()
                etag = response.header("ETag")?.removeSurrounding("\"")
            }

            if (content.isNullOrBlank()) {
                WebDavDownloadResult.Empty
            } else {
                WebDavDownloadResult.Success(content!!, etag)
            }
        } catch (e: NotFoundException) {
            WebDavDownloadResult.Empty
        } catch (e: UnauthorizedException) {
            WebDavDownloadResult.Error(WebDavError.AuthFailed)
        } catch (e: ForbiddenException) {
            WebDavDownloadResult.Error(WebDavError.PermissionDenied)
        } catch (e: DavException) {
            WebDavDownloadResult.Error(WebDavError.Unknown(0, e.message ?: "WebDAV error"))
        } catch (e: IOException) {
            WebDavDownloadResult.Error(WebDavError.NetworkUnreachable)
        }
    }


    // ── List files ───────────────────────────────────────────────────

    /**
     * List files in the remote directory.
     */
    suspend fun listFiles(): List<WebDavRemoteFile> = withContext(Dispatchers.IO) {
        try {
            ensureCollection()
            val collection = DavCollection(httpClient, collectionUrl())
            val files = mutableListOf<WebDavRemoteFile>()

            collection.propfind(1, WebDAV.GetETag, WebDAV.ResourceType) { response, relation ->
                if (relation == at.bitfire.dav4jvm.okhttp.Response.HrefRelation.MEMBER) {
                    val getETag = response[GetETag::class.java]
                    val isCollection = response[ResourceType::class.java]
                        ?.types?.contains(WebDAV.Collection) == true
                    if (!isCollection) {
                        files.add(
                            WebDavRemoteFile(
                                href = response.href.toString(),
                                displayName = response.hrefName(),
                                lastModified = null,
                                contentLength = 0,
                                etag = getETag?.eTag,
                            )
                        )
                    }
                }
            }
            files
        } catch (e: Exception) {
            AppLogger.w(TAG, "listFiles failed", e)
            emptyList()
        }
    }


    // ── Directory management ─────────────────────────────────────────

    /**
     * Ensure the remote collection (directory) exists.
     * Creates it recursively if needed.
     */
    private fun ensureCollection() {
        try {
            val collection = DavCollection(httpClient, collectionUrl())
            collection.propfind(0, WebDAV.ResourceType) { _, _ -> }
        } catch (e: NotFoundException) {
            createDirectorySync()
        } catch (e: ConflictException) {
            createDirectorySync()
        }
    }

    /**
     * Create the remote directory synchronously.
     * Handles recursive creation for nested paths.
     */
    private fun createDirectorySync() {
        val collection = DavCollection(httpClient, collectionUrl())
        try {
            collection.mkCol(null) { _ -> }
            AppLogger.i(TAG, "Created directory: ${config.remoteDir}")
        } catch (e: ConflictException) {
            // Parent doesn't exist — create parent first, then retry
            AppLogger.i(TAG, "Parent directory missing, creating recursively...")
            createParentDirectories()
            collection.mkCol(null) { _ -> }
            AppLogger.i(TAG, "Created directory after parent creation: ${config.remoteDir}")
        } catch (e: HttpException) {
            // 405 Method Not Allowed = already exists (some servers return this)
            if (e.statusCode == 405) {
                AppLogger.i(TAG, "Directory already exists (405)")
            } else {
                throw e
            }
        }
    }

    /**
     * Create parent directories recursively.
     * For path "a/b/c/", creates "a/", then "a/b/", etc.
     */
    private fun createParentDirectories() {
        val segments = config.remoteDir.trim('/').split('/')
        var currentPath = ""
        for (segment in segments.dropLast(0)) {
            if (segment.isBlank()) continue
            currentPath = "$currentPath$segment/"
            try {
                val parentUrl = baseUrl.resolve(currentPath)!!
                val parentCollection = DavCollection(httpClient, parentUrl)
                parentCollection.mkCol(null) { _ -> }
                AppLogger.i(TAG, "Created parent directory: $currentPath")
            } catch (e: HttpException) {
                if (e.statusCode == 405) {
                    // Already exists, continue
                } else {
                    AppLogger.w(TAG, "Failed to create parent $currentPath: ${e.statusCode}")
                }
            }
        }
    }

    /**
     * Create directory with coroutine context (for test connection).
     */
    private suspend fun createDirectoryRecursive(): WebDavTestConnectionResult = withContext(Dispatchers.IO) {
        try {
            createDirectorySync()
            WebDavTestConnectionResult.Success(dirCreated = true)
        } catch (e: UnauthorizedException) {
            WebDavTestConnectionResult.AuthFailed
        } catch (e: ForbiddenException) {
            WebDavTestConnectionResult.PermissionDenied
        } catch (e: DavException) {
            WebDavTestConnectionResult.Error(e.message ?: "Failed to create directory")
        } catch (e: IOException) {
            WebDavTestConnectionResult.NetworkError(e.message ?: "Network error")
        }
    }


    // ── Error classification ─────────────────────────────────────────

    private fun classifyError(e: Exception): WebDavError = when (e) {
        is UnauthorizedException -> WebDavError.AuthFailed
        is ForbiddenException -> WebDavError.PermissionDenied
        is ConflictException -> WebDavError.Unknown(409, "Directory conflict")
        is HttpException -> WebDavError.Unknown(e.statusCode, e.message ?: "HTTP ${e.statusCode}")
        is IOException -> WebDavError.NetworkUnreachable
        is DavException -> WebDavError.Unknown(0, e.message ?: "WebDAV error")
        else -> WebDavError.Unknown(0, e.message ?: "Unknown error")
    }

    companion object {
        private const val TAG = "WebDavSyncClient"

        /**
         * User-Agent advertised on every request. Must NOT start with `okhttp/` — Jianguoyun's edge
         * layer drops such requests with HTTP 410 before they reach the WebDAV stack. The format
         * mirrors what well-behaved DAV clients (e.g. DAVx⁵) send so providers don't gate features
         * behind a known-client allow-list.
         */
        const val USER_AGENT: String = "KeiOS-WebDAV/1 (+https://github.com/KeiOS) okhttp"
    }
}


// ── Result types ─────────────────────────────────────────────────────

sealed interface WebDavTestConnectionResult {
    data class Success(val dirCreated: Boolean) : WebDavTestConnectionResult
    data object AuthFailed : WebDavTestConnectionResult
    data object PermissionDenied : WebDavTestConnectionResult
    data class NetworkError(val message: String) : WebDavTestConnectionResult
    data class InvalidUrl(val message: String) : WebDavTestConnectionResult
    data class Error(val message: String) : WebDavTestConnectionResult
}

sealed interface WebDavUploadResult {
    data class Success(val etag: String?) : WebDavUploadResult
    data object Conflict : WebDavUploadResult
    data class Error(val error: WebDavError) : WebDavUploadResult
}

sealed interface WebDavDownloadResult {
    data class Success(val content: String, val etag: String?) : WebDavDownloadResult
    data object Empty : WebDavDownloadResult
    data class Error(val error: WebDavError) : WebDavDownloadResult
}

sealed interface WebDavError {
    data object NetworkUnreachable : WebDavError
    data object AuthFailed : WebDavError
    data object PermissionDenied : WebDavError
    data class Unknown(val code: Int, val message: String) : WebDavError
}
