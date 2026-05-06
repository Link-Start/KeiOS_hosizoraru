package os.kei.feature.github.data.remote

import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubLookupConfig

internal class GitHubActionsArtifactManifestProbe(
    private val manifestReader: GitHubApkManifestReader = GitHubApkManifestReader()
) {
    fun readPackageName(
        artifact: GitHubActionsArtifact,
        resolvedDownloadUrl: String,
        lookupConfig: GitHubLookupConfig
    ): Result<String> = runCatching {
        val url = resolvedDownloadUrl.trim()
        check(url.isNotBlank()) { "Actions artifact download URL is blank" }
        val asset = GitHubReleaseAssetFile(
            name = artifact.name.ifBlank { "actions-artifact-${artifact.id}" },
            downloadUrl = url,
            sizeBytes = artifact.sizeBytes,
            downloadCount = 0
        )
        val directFirst = asset.name.endsWith(".apk", ignoreCase = true)
        if (directFirst) {
            readDirectPackageName(asset, lookupConfig).getOrNull()?.let { return@runCatching it }
            return@runCatching readNestedApkPackageName(asset, lookupConfig).getOrThrow()
        }
        readNestedApkPackageName(asset, lookupConfig).getOrNull()
            ?: readDirectPackageName(asset, lookupConfig).getOrThrow()
    }

    private fun readDirectPackageName(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<String> {
        return manifestReader.readPackageName(
            asset = asset,
            lookupConfig = lookupConfig
        ).mapCatching(::validatedPackageName)
    }

    private fun readNestedApkPackageName(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<String> {
        return manifestReader.readSelectedNestedApkPackageName(
            asset = asset,
            lookupConfig = lookupConfig,
            selectNestedApkEntryNames = ::selectNestedApkEntryNames
        ).mapCatching(::validatedPackageName)
    }

    private fun validatedPackageName(packageName: String): String {
        val normalized = packageName.trim()
        check(androidPackageNamePattern.matches(normalized)) {
            "Actions artifact package name is invalid: $normalized"
        }
        return normalized
    }

    private fun nestedApkEntryScore(entryName: String): Int {
        val name = entryName.substringAfterLast('/').lowercase()
        return when {
            "universal" in name && "release" in name -> 0
            "universal" in name -> 1
            "release" in name -> 2
            "debug" in name -> 4
            else -> 3
        }
    }

    private fun selectNestedApkEntryNames(entryNames: List<String>): List<String> {
        return entryNames
            .asSequence()
            .filter { it.endsWith(".apk", ignoreCase = true) }
            .sortedWith(
                compareBy<String> { nestedApkEntryScore(it) }
                    .thenBy { it.length }
                    .thenBy { it.lowercase() }
            )
            .take(MAX_NESTED_APK_SCAN_CANDIDATES)
            .toList()
    }

    private companion object {
        private const val MAX_NESTED_APK_SCAN_CANDIDATES = 4
        private val androidPackageNamePattern =
            Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
    }
}
