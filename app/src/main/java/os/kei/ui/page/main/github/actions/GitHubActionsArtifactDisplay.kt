package os.kei.ui.page.main.github.actions

import os.kei.feature.github.model.GitHubActionsArtifactKind
import os.kei.feature.github.model.GitHubActionsArtifactMatch
import java.util.concurrent.ConcurrentHashMap

internal fun shortArtifactDigest(digest: String): String {
    val trimmed = digest.trim()
    val prefix = trimmed.substringBefore(':', missingDelimiterValue = "")
        .takeIf { ':' in trimmed }
        ?.let { "$it:" }
        .orEmpty()
    val value = if (prefix.isBlank()) trimmed else trimmed.substringAfter(':')
    if (value.length <= 18) return trimmed
    return "$prefix${value.take(8)}...${value.takeLast(4)}"
}

internal fun artifactDisplayName(match: GitHubActionsArtifactMatch): String {
    val original = match.artifact.name.trim()
    val traits = match.traits
    val display = artifactDisplayTokens(match)
        .sortedByDescending { it.length }
        .fold(stripArtifactExtension(original, traits.extension)) { current, token ->
            stripArtifactToken(current, token)
        }
        .cleanupArtifactDisplayName()
        .takeIf { it.isNotBlank() }
        ?: original
    if (display.length <= 180) return display
    return "${display.take(112)}...${display.takeLast(56)}"
}

internal fun artifactBuildTypeLabel(buildType: String): String {
    return when (buildType) {
        "qa" -> "QA"
        else -> buildType.replaceFirstChar { char -> char.uppercase() }
    }
}

private fun artifactDisplayTokens(match: GitHubActionsArtifactMatch): List<String> {
    val traits = match.traits
    return buildList {
        if (traits.extension in artifactDisplayKnownExtensions) {
            add(traits.extension)
        }
        when (traits.kind) {
            GitHubActionsArtifactKind.AndroidPackage -> add("apk")
            GitHubActionsArtifactKind.AndroidBundle -> addAll(listOf("aab", "apks", "apkm"))
            GitHubActionsArtifactKind.Archive -> addAll(listOf("zip", "tar.gz", "tgz"))
            GitHubActionsArtifactKind.Mapping -> addAll(listOf("mapping", "mappings"))
            GitHubActionsArtifactKind.Report -> addAll(listOf("report", "reports"))
            GitHubActionsArtifactKind.Source -> addAll(listOf("source", "sources"))
            GitHubActionsArtifactKind.Unknown -> Unit
        }
        add(traits.version)
        traits.version.removePrefix("v").takeIf { it != traits.version }?.let(::add)
        add(traits.abi)
        when (traits.abi) {
            "arm64-v8a" -> add("arm64")
            "armeabi-v7a" -> add("armeabi")
            "x86_64" -> add("x64")
        }
        addAll(traits.flavors)
        addAll(traits.buildTypes)
        if (traits.releaseLike) addAll(listOf("release", "prod", "signed"))
        if (traits.debugLike) addAll(listOf("debug", "dev", "unsigned"))
        if (traits.universalLike) addAll(listOf("universal", "fat", "all"))
    }
        .map { it.trim().trim('.') }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun stripArtifactExtension(value: String, extension: String): String {
    val normalizedExtension = extension.trim().trim('.')
    if (normalizedExtension !in artifactDisplayKnownExtensions) return value
    return value.replace(extensionStripRegex(normalizedExtension), "")
}

private fun stripArtifactToken(value: String, token: String): String {
    return value.replace(tokenStripRegex(token)) { matchResult ->
        matchResult.groups[1]?.value.takeIf { !it.isNullOrBlank() }.orEmpty()
    }
}

private fun String.cleanupArtifactDisplayName(): String {
    return trim()
        .replace(artifactDisplaySeparatorRunRegex, "-")
        .trim(' ', '.', '_', '-')
}

private val artifactDisplaySeparatorRunRegex = Regex("""[\s._-]{2,}""")
private val extensionStripRegexCache = ConcurrentHashMap<String, Regex>()
private val tokenStripRegexCache = ConcurrentHashMap<String, Regex>()

private fun extensionStripRegex(extension: String): Regex {
    return cachedArtifactRegex(extensionStripRegexCache, extension) {
        Regex("""\.${Regex.escape(extension)}$""", RegexOption.IGNORE_CASE)
    }
}

private fun tokenStripRegex(token: String): Regex {
    return cachedArtifactRegex(tokenStripRegexCache, token) {
        Regex("""(^|[\s._-]+)${Regex.escape(token)}(?=$|[\s._-]+)""", RegexOption.IGNORE_CASE)
    }
}

private fun cachedArtifactRegex(
    cache: ConcurrentHashMap<String, Regex>,
    key: String,
    build: () -> Regex
): Regex {
    if (cache.size > 96) {
        cache.clear()
    }
    return cache.getOrPut(key, build)
}

private val artifactDisplayKnownExtensions = setOf(
    "apk",
    "aab",
    "apks",
    "apkm",
    "zip",
    "tar.gz",
    "tgz",
    "gz"
)
