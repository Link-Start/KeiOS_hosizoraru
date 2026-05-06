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
        manifestReader.readPackageName(
            asset = asset,
            lookupConfig = lookupConfig
        ).getOrThrow().trim().also { packageName ->
            check(androidPackageNamePattern.matches(packageName)) {
                "Actions artifact package name is invalid: $packageName"
            }
        }
    }

    private companion object {
        private val androidPackageNamePattern =
            Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
    }
}
