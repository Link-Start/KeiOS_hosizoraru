package os.kei.ui.page.main.github.section

import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.ui.page.main.github.VersionCheckUi

internal data class GitHubAssetSummaryReleaseDisplay(
    val releaseName: String,
    val releaseTag: String,
    val apkVersionLabel: String?,
    val showReleaseMeta: Boolean
)

internal fun buildGitHubAssetSummaryReleaseDisplay(
    state: VersionCheckUi,
    assetBundle: GitHubReleaseAssetBundle?,
    targetRawTag: String,
    preciseApkVersionEnabled: Boolean
): GitHubAssetSummaryReleaseDisplay {
    val fallbackReleaseName = when {
        state.latestStableName.isNotBlank() -> state.latestStableName
        state.latestPreName.isNotBlank() -> state.latestPreName
        else -> ""
    }
    val releaseName = assetBundle?.releaseName?.trim().orEmpty()
        .ifBlank { fallbackReleaseName.ifBlank { targetRawTag } }
    val releaseTag = assetBundle?.tagName?.trim().orEmpty()
        .ifBlank { targetRawTag }
    val apkVersionLabel = if (preciseApkVersionEnabled) {
        null
    } else {
        when {
            releaseTag.equals(state.latestStableRawTag, ignoreCase = true) ||
                    releaseTag.equals(state.latestTag, ignoreCase = true) ->
                state.latestStableApkVersion?.versionLabel()

            releaseTag.equals(state.latestPreRawTag, ignoreCase = true) ->
                state.latestPreApkVersion?.versionLabel()

            else -> null
        }?.takeIf { it.isNotBlank() }
    }
    return GitHubAssetSummaryReleaseDisplay(
        releaseName = releaseName,
        releaseTag = releaseTag,
        apkVersionLabel = apkVersionLabel,
        showReleaseMeta = !preciseApkVersionEnabled &&
                (releaseName.isNotBlank() || releaseTag.isNotBlank())
    )
}
