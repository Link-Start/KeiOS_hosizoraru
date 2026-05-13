package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

internal data class GitHubDirectApkVersionedDirectoryResolution(
    val indexUrl: String,
    val version: String,
    val downloadUrl: String
) {
    fun toAsset(fallbackName: String): GitHubReleaseAssetFile {
        return GitHubReleaseAssetFile(
            name = directApkFileNameFromUrl(downloadUrl).ifBlank { fallbackName },
            downloadUrl = downloadUrl,
            sizeBytes = 0L,
            downloadCount = 0,
            contentType = "application/vnd.android.package-archive"
        )
    }
}

internal class GitHubDirectApkVersionedDirectoryResolver(
    private val client: OkHttpClient = defaultClient
) {
    fun resolve(directApkUrl: String): Result<GitHubDirectApkVersionedDirectoryResolution?> =
        runCatching {
            val pattern = DirectApkVersionedDirectoryPattern.from(directApkUrl)
                ?: return@runCatching null
            val request = Request.Builder()
                .url(pattern.indexUrl)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,*/*")
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "direct APK version directory failed (HTTP ${response.code})"
                }
                val html = response.body.string()
                check(html.length <= MAX_INDEX_HTML_CHARS) {
                    "direct APK version directory is too large"
                }
                val latest = parseVersionDirectories(
                    indexUri = pattern.indexUri,
                    indexPath = pattern.indexPath,
                    html = html
                ).maxWithOrNull(VersionDirectoryCandidateComparator)
                    ?: return@use null
                val downloadUrl = latest.uri.ensureDirectoryUri()
                    .resolve(pattern.suffixPath)
                    .toString()
                GitHubDirectApkVersionedDirectoryResolution(
                    indexUrl = pattern.indexUrl,
                    version = latest.version.raw,
                    downloadUrl = downloadUrl
                )
            }
        }

    private fun parseVersionDirectories(
        indexUri: URI,
        indexPath: String,
        html: String
    ): List<VersionDirectoryCandidate> {
        return hrefRegex.findAll(html)
            .mapNotNull { match -> match.groupValues.getOrNull(1)?.trim() }
            .filter { href -> href.isNotBlank() && href != ".." }
            .mapNotNull { href ->
                val uri = runCatching { indexUri.resolve(href) }.getOrNull()
                    ?: return@mapNotNull null
                val normalizedPath = uri.path.orEmpty()
                if (!normalizedPath.startsWith(indexPath)) return@mapNotNull null
                val segment = normalizedPath
                    .trimEnd('/')
                    .substringAfterLast('/')
                val version = DirectApkDirectoryVersion.parse(segment) ?: return@mapNotNull null
                VersionDirectoryCandidate(uri = uri, version = version)
            }
            .distinctBy { it.uri.normalize().toString() }
            .toList()
    }

    private fun URI.ensureDirectoryUri(): URI {
        val value = toString()
        return if (value.endsWith("/")) this else URI("$value/")
    }

    private data class DirectApkVersionedDirectoryPattern(
        val indexUri: URI,
        val indexUrl: String,
        val indexPath: String,
        val suffixPath: String
    ) {
        companion object {
            fun from(rawUrl: String): DirectApkVersionedDirectoryPattern? {
                val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
                val scheme = uri.scheme.orEmpty().lowercase(Locale.ROOT)
                if (scheme != "http" && scheme != "https") return null
                val segments = uri.path.orEmpty()
                    .split('/')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                if (segments.size < 3) return null
                val versionIndex = segments
                    .dropLast(1)
                    .indexOfLast { DirectApkDirectoryVersion.parse(it) != null }
                if (versionIndex < 0) return null
                val prefixSegments = segments.take(versionIndex)
                val suffixSegments = segments.drop(versionIndex + 1)
                if (suffixSegments.isEmpty()) return null
                val indexPath = "/" + prefixSegments.joinToString("/").trim('/') + "/"
                val normalizedIndexPath = indexPath.replace("//", "/")
                val authority = uri.rawAuthority ?: return null
                val indexUri = URI("${uri.scheme}://$authority$normalizedIndexPath")
                return DirectApkVersionedDirectoryPattern(
                    indexUri = indexUri,
                    indexUrl = indexUri.toString(),
                    indexPath = normalizedIndexPath,
                    suffixPath = suffixSegments.joinToString("/")
                )
            }
        }
    }

    private data class VersionDirectoryCandidate(
        val uri: URI,
        val version: DirectApkDirectoryVersion
    )

    private data class DirectApkDirectoryVersion(
        val raw: String,
        val numbers: List<Int>,
        val suffixRank: Int
    ) {
        companion object {
            fun parse(segment: String): DirectApkDirectoryVersion? {
                val match = versionSegmentRegex.matchEntire(segment.trim()) ?: return null
                val numbers = match.groupValues[1]
                    .split('.')
                    .mapNotNull { it.toIntOrNull() }
                if (numbers.size < 2) return null
                val suffix = match.groupValues.getOrNull(2).orEmpty().lowercase(Locale.ROOT)
                val suffixRank = when {
                    suffix.isBlank() -> 4
                    suffix.startsWith("rc") -> 3
                    suffix.startsWith("beta") -> 2
                    suffix.startsWith("alpha") -> 1
                    else -> 0
                }
                return DirectApkDirectoryVersion(
                    raw = segment,
                    numbers = numbers,
                    suffixRank = suffixRank
                )
            }
        }
    }

    private object VersionDirectoryCandidateComparator : Comparator<VersionDirectoryCandidate> {
        override fun compare(
            left: VersionDirectoryCandidate,
            right: VersionDirectoryCandidate
        ): Int {
            val maxSize = maxOf(left.version.numbers.size, right.version.numbers.size)
            repeat(maxSize) { index ->
                val leftNumber = left.version.numbers.getOrElse(index) { 0 }
                val rightNumber = right.version.numbers.getOrElse(index) { 0 }
                if (leftNumber != rightNumber) return leftNumber.compareTo(rightNumber)
            }
            return left.version.suffixRank.compareTo(right.version.suffixRank)
        }
    }

    private companion object {
        const val USER_AGENT = "KeiOS-App/1.0 (Android)"
        const val MAX_INDEX_HTML_CHARS = 512 * 1024
        val hrefRegex = Regex("""href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val versionSegmentRegex =
            Regex("""^[vV]?(\d+(?:\.\d+){1,4})(?:[-_]?([A-Za-z][A-Za-z0-9._-]*))?/?$""")
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12.seconds)
            .readTimeout(20.seconds)
            .callTimeout(28.seconds)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
