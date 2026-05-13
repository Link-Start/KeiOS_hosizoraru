package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import os.kei.feature.github.model.GitHubReleaseChannel
import java.net.URI
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

internal data class GitHubDirectApkDirectoryIndexResolution(
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

internal class GitHubDirectApkDirectoryIndexResolver(
    private val client: OkHttpClient = defaultClient
) {
    fun resolve(
        rawUrl: String,
        localVersion: String = "",
        preferPreRelease: Boolean = false
    ): Result<GitHubDirectApkDirectoryIndexResolution?> = runCatching {
        val pattern = DirectApkDirectoryIndexPattern.from(rawUrl)
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
                "direct APK directory index failed (HTTP ${response.code})"
            }
            val html = response.body.string()
            check(html.length <= MAX_INDEX_HTML_CHARS) {
                "direct APK directory index is too large"
            }
            val candidates = parseApkCandidates(
                indexUri = pattern.indexUri,
                indexPath = pattern.indexPath,
                html = html
            )
            val familyCandidates = pattern.referenceFamilyKey
                ?.let { familyKey ->
                    candidates
                        .filter { it.familyKey == familyKey }
                        .takeIf { it.isNotEmpty() }
                }
                ?: candidates
            val variantPreference = DirectApkVariantPreference.from(
                referenceFileName = pattern.referenceFileName,
                localVersion = localVersion
            )
            val variantCandidates = variantPreference
                .preferredCandidates(familyCandidates)
                .takeIf { it.isNotEmpty() }
                ?: familyCandidates
            val channelCandidates = variantCandidates
                .preferredReleaseChannelCandidates(preferPreRelease)
                .takeIf { it.isNotEmpty() }
                ?: variantCandidates
            val latest = channelCandidates.maxWithOrNull(ApkFileCandidateComparator)
                ?: return@use null
            GitHubDirectApkDirectoryIndexResolution(
                indexUrl = pattern.indexUrl,
                version = latest.version.raw,
                downloadUrl = latest.uri.toString(),
                channel = latest.version.channel
            )
        }
    }

    private fun parseApkCandidates(
        indexUri: URI,
        indexPath: String,
        html: String
    ): List<ApkFileCandidate> {
        return hrefRegex.findAll(html)
            .mapNotNull { match -> match.groupValues.getOrNull(1)?.trim() }
            .filter { href -> href.isNotBlank() && href != ".." }
            .mapNotNull { href ->
                val uri = runCatching { indexUri.resolve(href) }.getOrNull()
                    ?: return@mapNotNull null
                val path = uri.path.orEmpty()
                if (!path.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
                if (path.parentDirectoryPath() != indexPath) return@mapNotNull null
                val fileName = path.substringAfterLast('/').trim()
                val version = DirectApkFileVersion.parse(fileName) ?: return@mapNotNull null
                ApkFileCandidate(
                    uri = uri,
                    fileName = fileName,
                    familyKey = version.familyKey,
                    variant = DirectApkFileVariant.fromFileName(fileName),
                    version = version
                )
            }
            .distinctBy { it.uri.normalize().toString() }
            .toList()
    }

    private data class DirectApkDirectoryIndexPattern(
        val indexUri: URI,
        val indexUrl: String,
        val indexPath: String,
        val referenceFileName: String,
        val referenceFamilyKey: String?
    ) {
        companion object {
            fun from(rawUrl: String): DirectApkDirectoryIndexPattern? {
                val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
                val scheme = uri.scheme.orEmpty().lowercase(Locale.ROOT)
                if (scheme != "http" && scheme != "https") return null
                val path = uri.path.orEmpty()
                val pointsToApk = path.endsWith(".apk", ignoreCase = true)
                val pointsToDirectory = path.endsWith("/")
                if (!pointsToApk && !pointsToDirectory) return null
                val indexUri = if (pointsToApk) {
                    uri.resolve(".").ensureDirectoryUri()
                } else {
                    uri.ensureDirectoryUri()
                }
                val indexPath = indexUri.path.orEmpty().ensureDirectoryPath()
                val referenceFileName = if (pointsToApk) {
                    path.substringAfterLast('/').trim()
                } else {
                    ""
                }
                return DirectApkDirectoryIndexPattern(
                    indexUri = indexUri,
                    indexUrl = indexUri.toString(),
                    indexPath = indexPath,
                    referenceFileName = referenceFileName,
                    referenceFamilyKey = DirectApkFileVersion.parse(referenceFileName)?.familyKey
                )
            }
        }
    }

    private data class ApkFileCandidate(
        val uri: URI,
        val fileName: String,
        val familyKey: String,
        val variant: DirectApkFileVariant,
        val version: DirectApkFileVersion
    )

    private data class DirectApkFileVersion(
        val raw: String,
        val familyKey: String,
        val numbers: List<Int>,
        val suffixRank: Int,
        val suffixNumber: Int,
        val channel: GitHubReleaseChannel
    ) {
        companion object {
            fun parse(fileName: String): DirectApkFileVersion? {
                val normalized = fileName.trim()
                    .removeSuffix(".apk")
                    .replace('_', ' ')
                    .replace('-', ' ')
                val match = versionInFileRegex.find(normalized) ?: return null
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
                    suffix.startsWith("preview") || suffix == "pre" -> 2
                    suffix.startsWith("nightly") ||
                            suffix.startsWith("canary") ||
                            suffix.startsWith("snapshot") ||
                            suffix.startsWith("unstable") ||
                            suffix.startsWith("dev") -> 1

                    else -> 0
                }
                val channel = suffix.toReleaseChannel()
                val suffixNumber = match.groupValues.getOrNull(3)
                    ?.toIntOrNull()
                    ?: 0
                val familyKey = normalized
                    .substring(0, match.range.first)
                    .sanitizeFamilyKey()
                return DirectApkFileVersion(
                    raw = match.value.trim(),
                    familyKey = familyKey,
                    numbers = numbers,
                    suffixRank = suffixRank,
                    suffixNumber = suffixNumber,
                    channel = channel
                )
            }
        }
    }

    private enum class DirectApkFileVariant {
        Standard,
        Core;

        companion object {
            fun fromFileName(fileName: String): DirectApkFileVariant {
                val normalized = fileName.lowercase(Locale.ROOT)
                return if (
                    "core edition" in normalized ||
                    "(core" in normalized ||
                    "_core" in normalized ||
                    "-core" in normalized
                ) {
                    Core
                } else {
                    Standard
                }
            }
        }
    }

    private data class DirectApkVariantPreference(
        val preferredVariant: DirectApkFileVariant
    ) {
        fun preferredCandidates(candidates: List<ApkFileCandidate>): List<ApkFileCandidate> {
            return candidates.filter { it.variant == preferredVariant }
        }

        companion object {
            fun from(
                referenceFileName: String,
                localVersion: String
            ): DirectApkVariantPreference {
                val referenceVariant = referenceFileName
                    .takeIf { it.isNotBlank() }
                    ?.let(DirectApkFileVariant::fromFileName)
                val localVariant = localVersion
                    .takeIf { it.contains("core", ignoreCase = true) }
                    ?.let { DirectApkFileVariant.Core }
                return DirectApkVariantPreference(
                    preferredVariant = referenceVariant
                        ?: localVariant
                        ?: DirectApkFileVariant.Standard
                )
            }
        }
    }

    private object ApkFileCandidateComparator : Comparator<ApkFileCandidate> {
        override fun compare(left: ApkFileCandidate, right: ApkFileCandidate): Int {
            val maxSize = maxOf(left.version.numbers.size, right.version.numbers.size)
            repeat(maxSize) { index ->
                val leftNumber = left.version.numbers.getOrElse(index) { 0 }
                val rightNumber = right.version.numbers.getOrElse(index) { 0 }
                if (leftNumber != rightNumber) return leftNumber.compareTo(rightNumber)
            }
            if (left.version.suffixRank != right.version.suffixRank) {
                return left.version.suffixRank.compareTo(right.version.suffixRank)
            }
            if (left.version.suffixNumber != right.version.suffixNumber) {
                return left.version.suffixNumber.compareTo(right.version.suffixNumber)
            }
            return left.fileName.compareTo(right.fileName, ignoreCase = true)
        }
    }

    private fun List<ApkFileCandidate>.preferredReleaseChannelCandidates(
        preferPreRelease: Boolean
    ): List<ApkFileCandidate> {
        return filter { candidate ->
            candidate.version.channel.isPreRelease == preferPreRelease
        }
    }

    private companion object {
        const val USER_AGENT = "KeiOS-App/1.0 (Android)"
        const val MAX_INDEX_HTML_CHARS = 512 * 1024
        val hrefRegex = Regex("""href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val versionInFileRegex = Regex(
            """(?i)(\d+(?:\.\d+){1,4})(?:[\s._-]*(alpha|beta|rc|preview|pre|nightly|canary|snapshot|unstable|dev)\s*([0-9]+)?)?"""
        )
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12.seconds)
            .readTimeout(20.seconds)
            .callTimeout(28.seconds)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}

private fun String.toReleaseChannel(): GitHubReleaseChannel {
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

private fun URI.ensureDirectoryUri(): URI {
    val value = toString()
    return if (value.endsWith("/")) this else URI("$value/")
}

private fun String.ensureDirectoryPath(): String {
    return when {
        isBlank() -> "/"
        endsWith("/") -> this
        else -> "$this/"
    }
}

private fun String.parentDirectoryPath(): String {
    return substringBeforeLast('/', missingDelimiterValue = "")
        .ensureDirectoryPath()
}

private fun String.sanitizeFamilyKey(): String {
    return lowercase(Locale.ROOT)
        .replace(Regex("""[^a-z0-9]+"""), "-")
        .trim('-')
}
