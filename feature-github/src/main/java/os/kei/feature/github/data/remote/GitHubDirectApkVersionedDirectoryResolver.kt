package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import os.kei.core.io.SharedHttpClient
import os.kei.feature.github.model.GitHubReleaseChannel
import java.net.URI
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

data class GitHubDirectApkVersionedDirectoryResolution(
    val indexUrl: String,
    val version: String,
    val downloadUrl: String,
    val channel: GitHubReleaseChannel
) {
    val isPreRelease: Boolean
        get() = channel.isPreRelease

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

data class GitHubDirectApkVersionedDirectoryTargets(
    val stable: GitHubDirectApkVersionedDirectoryResolution?,
    val preRelease: GitHubDirectApkVersionedDirectoryResolution?
)

class GitHubDirectApkVersionedDirectoryResolver(
    private val client: OkHttpClient = defaultClient
) {
    fun resolve(
        directApkUrl: String,
        preferPreRelease: Boolean = false
    ): Result<GitHubDirectApkVersionedDirectoryResolution?> {
        return resolveTargets(
            directApkUrl = directApkUrl,
            includePreRelease = preferPreRelease
        ).map { targets ->
            if (preferPreRelease) {
                targets?.preRelease ?: targets?.stable
            } else {
                targets?.stable ?: targets?.preRelease
            }
        }
    }

    fun resolveTargets(
        directApkUrl: String,
        includePreRelease: Boolean = false
    ): Result<GitHubDirectApkVersionedDirectoryTargets?> =
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
                val candidates = parseVersionDirectories(
                    indexUri = pattern.indexUri,
                    indexPath = pattern.indexPath,
                    html = html
                )
                val latestStable = candidates
                    .preferredReleaseChannelCandidates(preferPreRelease = false)
                    .maxWithOrNull(VersionDirectoryCandidateComparator)
                val latestPreRelease = candidates
                    .preferredReleaseChannelCandidates(preferPreRelease = true)
                    .maxWithOrNull(VersionDirectoryCandidateComparator)
                val fallbackLatest = candidates.maxWithOrNull(VersionDirectoryCandidateComparator)
                val stableCandidate = latestStable
                val preReleaseCandidate = when {
                    includePreRelease -> latestPreRelease
                    stableCandidate == null -> latestPreRelease ?: fallbackLatest
                        ?.takeIf { it.version.channel.isPreRelease }

                    else -> null
                }
                val stable = stableCandidate?.toResolution(pattern)
                val preRelease = preReleaseCandidate?.toResolution(pattern)
                if (stable == null && preRelease == null) return@use null
                GitHubDirectApkVersionedDirectoryTargets(
                    stable = stable,
                    preRelease = preRelease
                )
            }
        }

    private fun VersionDirectoryCandidate.toResolution(
        pattern: DirectApkVersionedDirectoryPattern
    ): GitHubDirectApkVersionedDirectoryResolution {
        val downloadUrl = uri.ensureDirectoryUri()
            .resolve(pattern.suffixPath)
            .toString()
        return GitHubDirectApkVersionedDirectoryResolution(
            indexUrl = pattern.indexUrl,
            version = version.raw,
            downloadUrl = downloadUrl,
            channel = version.channel
        )
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
        val suffixRank: Int,
        val suffixNumber: Int,
        val channel: GitHubReleaseChannel
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
                    suffix.isBlank() -> 6
                    suffix.startsWith("rc") -> 5
                    suffix.startsWith("beta") -> 4
                    suffix.startsWith("alpha") -> 3
                    suffix.startsWith("preview") || suffix.startsWith("pre") -> 2
                    suffix.startsWith("nightly") ||
                            suffix.startsWith("canary") ||
                            suffix.startsWith("snapshot") ||
                            suffix.startsWith("unstable") ||
                            suffix.startsWith("dev") -> 1
                    else -> 0
                }
                val suffixNumber = suffixNumberRegex.find(suffix)
                    ?.value
                    ?.toIntOrNull()
                    ?: 0
                val channel = suffix.toVersionDirectoryReleaseChannel()
                return DirectApkDirectoryVersion(
                    raw = segment,
                    numbers = numbers,
                    suffixRank = suffixRank,
                    suffixNumber = suffixNumber,
                    channel = channel
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
            if (left.version.suffixRank != right.version.suffixRank) {
                return left.version.suffixRank.compareTo(right.version.suffixRank)
            }
            return left.version.suffixNumber.compareTo(right.version.suffixNumber)
        }
    }

    private fun List<VersionDirectoryCandidate>.preferredReleaseChannelCandidates(
        preferPreRelease: Boolean
    ): List<VersionDirectoryCandidate> {
        return filter { candidate ->
            candidate.version.channel.isPreRelease == preferPreRelease
        }
    }

    private companion object {
        const val USER_AGENT = "KeiOS-App/1.0 (Android)"
        const val MAX_INDEX_HTML_CHARS = 512 * 1024
        val hrefRegex = Regex("""href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val versionSegmentRegex =
            Regex("""^[vV]?(\d+(?:\.\d+){1,4})(?:[-_]?([A-Za-z][A-Za-z0-9._-]*))?/?$""")
        val suffixNumberRegex = Regex("""\d+$""")
        val defaultClient: OkHttpClient = SharedHttpClient.base.newBuilder()
            .connectTimeout(12.seconds)
            .readTimeout(20.seconds)
            .callTimeout(28.seconds)
            .build()
    }
}

private fun String.toVersionDirectoryReleaseChannel(): GitHubReleaseChannel {
    return when {
        isBlank() -> GitHubReleaseChannel.STABLE
        startsWith("rc") -> GitHubReleaseChannel.RC
        startsWith("beta") -> GitHubReleaseChannel.BETA
        startsWith("alpha") -> GitHubReleaseChannel.ALPHA
        startsWith("preview") || startsWith("pre") -> GitHubReleaseChannel.PREVIEW
        startsWith("nightly") ||
                startsWith("canary") ||
                startsWith("snapshot") ||
                startsWith("unstable") ||
                startsWith("dev") ->
            GitHubReleaseChannel.DEV

        else -> GitHubReleaseChannel.UNKNOWN
    }
}
