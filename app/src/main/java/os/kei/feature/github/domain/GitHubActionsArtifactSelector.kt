package os.kei.feature.github.domain

import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubActionsArtifactKind
import os.kei.feature.github.model.GitHubActionsArtifactMatch
import os.kei.feature.github.model.GitHubActionsArtifactNameTraits
import os.kei.feature.github.model.GitHubActionsArtifactPlatform
import os.kei.feature.github.model.GitHubActionsArtifactSelectionOptions
import os.kei.feature.github.model.GitHubReleaseChannel
import java.util.Locale

object GitHubActionsArtifactSelector {
    fun inspectName(name: String): GitHubActionsArtifactNameTraits {
        return artifactNameTraitsCache.getOrPut(name) {
            inspectNameUncached(name)
        }
    }

    private fun inspectNameUncached(name: String): GitHubActionsArtifactNameTraits {
        val normalizedName = name.trim().lowercase(Locale.ROOT)
        val extension = detectExtension(normalizedName)
        val flavors = detectFlavors(normalizedName)
        val buildTypes = detectBuildTypes(normalizedName)
        val version = detectVersion(normalizedName)
        val platform = detectPlatform(
            normalizedName = normalizedName,
            extension = extension,
            flavors = flavors
        )
        val abi = detectAbi(normalizedName)
        val channel = detectChannel(normalizedName)
        val releaseLike = containsToken(normalizedName, "release") ||
            containsToken(normalizedName, "prod") ||
            containsToken(normalizedName, "signed")
        val debugLike = containsToken(normalizedName, "debug") ||
            containsToken(normalizedName, "dev") ||
            containsToken(normalizedName, "unsigned")
        val universalLike = containsToken(normalizedName, "universal") ||
            containsToken(normalizedName, "fat") ||
            containsToken(normalizedName, "all")
        val buildNoise = isBuildNoise(normalizedName)
        val kind = detectKind(
            normalizedName = normalizedName,
            extension = extension,
            platform = platform,
            buildNoise = buildNoise
        )
        return GitHubActionsArtifactNameTraits(
            normalizedName = normalizedName,
            extension = extension,
            kind = kind,
            platform = platform,
            abi = abi,
            flavors = flavors,
            buildTypes = buildTypes,
            version = version,
            channel = channel,
            releaseLike = releaseLike,
            debugLike = debugLike,
            universalLike = universalLike,
            buildNoise = buildNoise
        )
    }

    fun selectDisplayArtifacts(
        artifacts: List<GitHubActionsArtifact>,
        options: GitHubActionsArtifactSelectionOptions = GitHubActionsArtifactSelectionOptions()
    ): List<GitHubActionsArtifactMatch> {
        val primaryContext = ArtifactSelectionContext.from(options)
        val primaryMatches = artifacts
            .asSequence()
            .mapNotNull { artifact -> matchArtifact(artifact, options, primaryContext) }
            .toList()
        if (primaryMatches.isNotEmpty() || !options.fallbackToAllArtifacts) {
            return sortMatches(primaryMatches)
        }

        val relaxedOptions = options.copy(
            hideBuildNoise = false,
            includeNonAndroidArtifacts = true,
            aggressiveAbiFiltering = false,
            fallbackToAllArtifacts = false
        )
        val relaxedContext = ArtifactSelectionContext.from(relaxedOptions)
        return sortMatches(
            artifacts.mapNotNull { artifact ->
                matchArtifact(
                    artifact,
                    relaxedOptions,
                    relaxedContext
                )
            }
        )
    }

    fun matchArtifact(
        artifact: GitHubActionsArtifact,
        options: GitHubActionsArtifactSelectionOptions = GitHubActionsArtifactSelectionOptions()
    ): GitHubActionsArtifactMatch? {
        return matchArtifact(
            artifact = artifact,
            options = options,
            context = ArtifactSelectionContext.from(options)
        )
    }

    private fun matchArtifact(
        artifact: GitHubActionsArtifact,
        options: GitHubActionsArtifactSelectionOptions,
        context: ArtifactSelectionContext
    ): GitHubActionsArtifactMatch? {
        if (artifact.name.isBlank()) return null
        if (options.hideExpired && artifact.expired) return null

        val traits = inspectName(artifact.name)
        if (options.hideBuildNoise && traits.buildNoise && options.query.isBlank()) return null
        if (!options.includeNonAndroidArtifacts &&
            traits.platform != GitHubActionsArtifactPlatform.Android &&
            traits.platform != GitHubActionsArtifactPlatform.Generic
        ) {
            return null
        }
        if (!options.includeNonAndroidArtifacts &&
            traits.platform == GitHubActionsArtifactPlatform.Unknown &&
            !traits.androidLike
        ) {
            return null
        }
        if (options.includeRegex?.containsMatchIn(artifact.name) == false) return null
        if (options.excludeRegex?.containsMatchIn(artifact.name) == true) return null
        if (!context.matchesQuery(traits.normalizedName)) return null

        if (
            options.aggressiveAbiFiltering &&
            context.preferredAbis.isNotEmpty() &&
            traits.abi.isNotBlank() &&
            traits.abi !in context.preferredAbis &&
            !traits.universalLike
        ) {
            return null
        }

        val reasons = mutableListOf<String>()
        var score = 0
        when (traits.kind) {
            GitHubActionsArtifactKind.AndroidPackage -> {
                score += 100
                reasons += "android-package"
            }
            GitHubActionsArtifactKind.AndroidBundle -> {
                score += 92
                reasons += "android-bundle"
            }
            GitHubActionsArtifactKind.Archive -> {
                score += 55
                if (traits.platform == GitHubActionsArtifactPlatform.Android) {
                    score += 45
                }
                reasons += "archive"
            }
            GitHubActionsArtifactKind.Mapping -> score -= 60
            GitHubActionsArtifactKind.Report -> score -= 55
            GitHubActionsArtifactKind.Source -> score -= 70
            GitHubActionsArtifactKind.Unknown -> Unit
        }
        if (traits.releaseLike) {
            score += 16
            reasons += "release"
        }
        traits.flavors.forEach { flavor ->
            score += when (flavor) {
                "market" -> 10
                "foss" -> 8
                "offline", "online" -> 6
                else -> 4
            }
            reasons += flavor
        }
        traits.buildTypes
            .filterNot { it in buildTypeSkipTokens }
            .forEach { buildType ->
                score += when (buildType) {
                    "benchmark" -> 6
                    "profile" -> 4
                    else -> 2
                }
                reasons += buildType
            }
        if (traits.debugLike) {
            score -= 8
            reasons += "debug"
        }
        if (traits.channel.isPreRelease) {
            score += 4
            reasons += traits.channel.name.lowercase(Locale.ROOT)
        }
        when {
            traits.abi.isNotBlank() && traits.abi in context.preferredAbis -> {
                score += 22
                reasons += traits.abi
            }
            traits.universalLike -> {
                score += 14
                reasons += "universal"
            }
        }
        if (context.queryActive) {
            score += 20
            reasons += "query"
        }
        if (artifact.sizeBytes > 0L) score += 2
        if (traits.version.isNotBlank()) {
            score += 2
            reasons += "version"
        }
        val lastDownload = GitHubActionsDownloadHistoryMatcher.latestForArtifact(
            artifact = artifact,
            history = options.downloadHistory
        )
        if (lastDownload != null) {
            val exactArtifact = artifact.id > 0L && lastDownload.artifactId == artifact.id
            score += if (exactArtifact) 72 else 42
            score += GitHubActionsDownloadHistoryMatcher.recencyScore(lastDownload)
            reasons += "last-downloaded"
        }

        return GitHubActionsArtifactMatch(
            artifact = artifact,
            traits = traits,
            score = score,
            lastDownload = lastDownload,
            reasons = reasons
        )
    }

    private fun sortMatches(
        matches: List<GitHubActionsArtifactMatch>
    ): List<GitHubActionsArtifactMatch> {
        return matches.sortedWith(
            compareByDescending<GitHubActionsArtifactMatch> { it.score }
                .thenByDescending { it.artifact.updatedAtMillis ?: Long.MIN_VALUE }
                .thenBy { it.artifact.name.lowercase(Locale.ROOT) }
        )
    }

    private fun detectKind(
        normalizedName: String,
        extension: String,
        platform: GitHubActionsArtifactPlatform,
        buildNoise: Boolean
    ): GitHubActionsArtifactKind {
        if (buildNoise) {
            return when {
                containsAny(normalizedName, "mapping", "symbols", "symbol") -> GitHubActionsArtifactKind.Mapping
                containsAny(normalizedName, "source", "sources") -> GitHubActionsArtifactKind.Source
                else -> GitHubActionsArtifactKind.Report
            }
        }
        return when {
            extension == "apk" || containsToken(normalizedName, "apk") -> GitHubActionsArtifactKind.AndroidPackage
            extension in setOf("apks", "apkm", "aab") ||
                containsAny(normalizedName, "apks", "apkm", "aab") -> GitHubActionsArtifactKind.AndroidBundle
            extension in setOf("zip", "tar.gz", "tgz", "gz") -> GitHubActionsArtifactKind.Archive
            else -> GitHubActionsArtifactKind.Unknown
        }
    }

    private fun detectExtension(normalizedName: String): String {
        return when {
            normalizedName.endsWith(".tar.gz") -> "tar.gz"
            else -> normalizedName.substringAfterLast('.', "").takeIf { it != normalizedName }.orEmpty()
        }
    }

    private fun detectPlatform(
        normalizedName: String,
        extension: String,
        flavors: List<String>
    ): GitHubActionsArtifactPlatform {
        return when {
            extension in setOf("apk", "apks", "apkm", "aab") -> GitHubActionsArtifactPlatform.Android
            containsAny(normalizedName, "android", "apk") -> GitHubActionsArtifactPlatform.Android
            flavors.any { it in androidFlavorTokens } -> GitHubActionsArtifactPlatform.Android
            looksLikeAndroidAppArchive(normalizedName) -> GitHubActionsArtifactPlatform.Android
            containsAny(normalizedName, "windows", "win32", "win64", "x64-exe") ||
                extension in setOf("exe", "msi") -> GitHubActionsArtifactPlatform.Windows
            containsAny(normalizedName, "linux", "appimage") ||
                extension in setOf("deb", "rpm") -> GitHubActionsArtifactPlatform.Linux
            containsAny(normalizedName, "darwin", "macos", "mac-arm", "mac-x64") ||
                extension == "dmg" -> GitHubActionsArtifactPlatform.MacOS
            containsAny(normalizedName, "github-pages", "pages") -> GitHubActionsArtifactPlatform.Web
            extension in setOf("zip", "tar.gz", "tgz", "gz") -> GitHubActionsArtifactPlatform.Generic
            else -> GitHubActionsArtifactPlatform.Unknown
        }
    }

    private fun detectFlavors(normalizedName: String): List<String> {
        return androidFlavorTokens.filter { token -> containsToken(normalizedName, token) }
    }

    private fun detectBuildTypes(normalizedName: String): List<String> {
        return androidBuildTypeTokens.filter { token -> containsToken(normalizedName, token) }
    }

    private fun detectVersion(normalizedName: String): String {
        val semanticVersion = semanticVersionRegex.find(normalizedName)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim('.', '-', '_')
            .orEmpty()
        if (semanticVersion.isNotBlank()) {
            return semanticVersion.let { version ->
                if (version.startsWith("v", ignoreCase = true)) version else "v$version"
            }
        }
        return namedVersionRegex.find(normalizedName)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { versionCode -> "v$versionCode" }
            .orEmpty()
    }

    private fun looksLikeAndroidAppArchive(normalizedName: String): Boolean {
        if (!containsToken(normalizedName, "app")) return false
        return containsAny(normalizedName, "release", "debug", "universal") ||
            detectAbi(normalizedName).isNotBlank()
    }

    private fun detectAbi(normalizedName: String): String {
        return when {
            containsAny(normalizedName, "arm64-v8a", "arm64") -> "arm64-v8a"
            containsAny(normalizedName, "armeabi-v7a", "armeabi") -> "armeabi-v7a"
            containsAny(normalizedName, "x86_64", "x64") -> "x86_64"
            containsToken(normalizedName, "x86") -> "x86"
            else -> ""
        }
    }

    private fun detectChannel(normalizedName: String): GitHubReleaseChannel {
        return when {
            containsToken(normalizedName, "alpha") -> GitHubReleaseChannel.ALPHA
            containsToken(normalizedName, "preview") -> GitHubReleaseChannel.PREVIEW
            containsToken(normalizedName, "beta") -> GitHubReleaseChannel.BETA
            rcChannelRegex.containsMatchIn(normalizedName) -> GitHubReleaseChannel.RC
            containsAny(normalizedName, "nightly", "snapshot", "canary", "unstable") -> GitHubReleaseChannel.DEV
            containsToken(normalizedName, "release") -> GitHubReleaseChannel.STABLE
            else -> GitHubReleaseChannel.UNKNOWN
        }
    }

    private fun isBuildNoise(normalizedName: String): Boolean {
        return containsAny(
            normalizedName,
            "mapping",
            "mappings",
            "symbols",
            "symbol",
            "source",
            "sources",
            "javadoc",
            "lint",
            "report",
            "reports",
            "results",
            "test-results",
            "test_result",
            "coverage",
            "logs",
            "dependency-graph",
            "metadata"
        )
    }

    private fun containsAny(value: String, vararg needles: String): Boolean {
        return needles.any { value.contains(it) }
    }

    private fun containsToken(value: String, token: String): Boolean {
        val needle = token.lowercase(Locale.ROOT)
        if (needle.isBlank()) return false
        var start = value.indexOf(needle)
        while (start >= 0) {
            val end = start + needle.length
            val beforeBoundary = start == 0 || !value[start - 1].isAsciiLetterOrDigit()
            val afterBoundary = end == value.length || !value[end].isAsciiLetterOrDigit()
            if (beforeBoundary && afterBoundary) return true
            start = value.indexOf(needle, startIndex = start + 1)
        }
        return false
    }

    private fun Char.isAsciiLetterOrDigit(): Boolean {
        return (this in 'a'..'z') || (this in '0'..'9')
    }

    private val androidFlavorTokens = listOf(
        "foss",
        "market",
        "play",
        "fdroid",
        "offline",
        "online",
        "full",
        "free",
        "paid"
    )

    private val androidBuildTypeTokens = listOf(
        "release",
        "debug",
        "benchmark",
        "profile",
        "staging",
        "qa",
        "internal",
        "nightly",
        "preview",
        "canary"
    )
    private val buildTypeSkipTokens = setOf("release", "debug")
    private val semanticVersionRegex = Regex(
        """(?:^|[^a-z0-9])(v?\d+\.\d+(?:\.\d+){0,2}(?:[-._]?(?:alpha|beta|rc|preview|dev|canary|nightly|snapshot)[-._]?\d*)?)(?=$|[^a-z0-9])"""
    )
    private val namedVersionRegex = Regex(
        """(?:^|[^a-z0-9])(?:version|ver|versioncode|vc)[-._ ]?(\d{2,})(?=$|[^a-z0-9])"""
    )
    private val rcChannelRegex = Regex("""(^|[^a-z0-9])rc\d*([^a-z0-9]|$)""")
    private val querySplitRegex = Regex("""\s+""")
    private val artifactNameTraitsCache =
        BoundedArtifactSelectorCache<String, GitHubActionsArtifactNameTraits>(512)

    private data class ArtifactSelectionContext(
        val preferredAbis: Set<String>,
        val queryTokens: List<String>
    ) {
        val queryActive: Boolean
            get() = queryTokens.isNotEmpty()

        fun matchesQuery(normalizedName: String): Boolean {
            if (queryTokens.isEmpty()) return true
            return queryTokens.all { normalizedName.contains(it) }
        }

        companion object {
            fun from(options: GitHubActionsArtifactSelectionOptions): ArtifactSelectionContext {
                return ArtifactSelectionContext(
                    preferredAbis = options.preferredAbis
                        .asSequence()
                        .map { it.trim().lowercase(Locale.ROOT) }
                        .filter { it.isNotBlank() }
                        .toSet(),
                    queryTokens = options.query
                        .trim()
                        .lowercase(Locale.ROOT)
                        .split(querySplitRegex)
                        .filter { it.isNotBlank() }
                )
            }
        }
    }

    private class BoundedArtifactSelectorCache<K, V>(
        private val maxSize: Int
    ) {
        private val values = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxSize
            }
        }

        fun getOrPut(key: K, createValue: () -> V): V {
            synchronized(values) {
                if (values.containsKey(key)) {
                    @Suppress("UNCHECKED_CAST")
                    return values[key] as V
                }
                return createValue().also { value -> values[key] = value }
            }
        }
    }
}
