package os.kei.feature.webdav.model

/**
 * WebDAV server configuration.
 */
data class WebDavConfig(
    val serverUrl: String,
    val username: String,
    val appPassword: String,
    val remoteDir: String = "keios/",
)

/**
 * Result of a single WebDAV operation.
 */
sealed interface WebDavResult {
    data class Success(val etag: String? = null, val body: String? = null) : WebDavResult
    data class Failure(val code: Int, val message: String) : WebDavResult
}

/**
 * Metadata for a remote file discovered via PROPFIND.
 */
data class WebDavRemoteFile(
    val href: String,
    val displayName: String,
    val lastModified: String?,
    val contentLength: Long,
    val etag: String?,
)
