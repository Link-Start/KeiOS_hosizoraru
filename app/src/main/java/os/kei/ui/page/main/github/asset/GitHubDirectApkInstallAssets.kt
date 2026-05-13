package os.kei.ui.page.main.github.asset

import androidx.annotation.StringRes
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.isLocalAppUninstalled
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

internal data class GitHubDirectApkAssetPanelData(
    val bundle: GitHubReleaseAssetBundle,
    @get:StringRes val targetLabelRes: Int,
    val targetRawTag: String,
    val targetAccentKind: GitHubDirectApkAssetAccentKind
)

internal enum class GitHubDirectApkAssetAccentKind {
    Install,
    Stable,
    PreRelease
}

internal fun GitHubTrackedApp.directApkAssetPanelData(
    state: VersionCheckUi
): GitHubDirectApkAssetPanelData? {
    if (!isDirectApkTrack()) return null
    val isInstall = state.isLocalAppUninstalled()
    val preReleasePreferred = preferPreRelease ||
            state.recommendsPreRelease ||
            state.hasPreReleaseUpdate
    val target = when {
        preReleasePreferred && state.latestPreApkVersion != null -> state.latestPreApkVersion
        state.latestStableApkVersion != null -> state.latestStableApkVersion
        state.latestPreApkVersion != null -> state.latestPreApkVersion
        else -> fallbackDirectApkVersionInfo() ?: return null
    }
    val targetUrl = target.releaseUrl.trim().ifBlank { repoUrl.trim() }
    if (targetUrl.isBlank()) return null
    val assetName = target.assetName.trim()
        .ifBlank { assetNameFromUrl(targetUrl) }
        .ifBlank { "remote.apk" }
    val targetTag = target.releaseTag.trim()
        .ifBlank { target.versionName.trim() }
        .ifBlank { target.versionCode.trim() }
        .ifBlank { assetDisplayName(assetName) }
    val isPreRelease = target == state.latestPreApkVersion
    val releaseName = target.releaseName.trim()
        .ifBlank { appLabel.trim() }
        .ifBlank { assetDisplayName(assetName) }
    val bundle = GitHubReleaseAssetBundle(
        releaseName = releaseName,
        tagName = targetTag,
        htmlUrl = targetUrl,
        releaseUpdatedAtMillis = when {
            isPreRelease -> state.latestPreUpdatedAtMillis.takeIf { it > 0L }
            else -> state.latestStableUpdatedAtMillis.takeIf { it > 0L }
        },
        releaseNotesBody = target.releaseNotes,
        assets = listOf(
            GitHubReleaseAssetFile(
                name = assetName,
                downloadUrl = targetUrl,
                sizeBytes = -1L,
                downloadCount = 0,
                contentType = "application/vnd.android.package-archive"
            )
        ),
        showingAllAssets = false,
        fetchSource = target.fetchSource.trim().ifBlank { "direct_apk" }
    )
    return GitHubDirectApkAssetPanelData(
        bundle = bundle,
        targetLabelRes = when {
            isInstall -> R.string.github_asset_target_install
            isPreRelease -> R.string.github_asset_target_prerelease
            else -> R.string.github_asset_target_stable
        },
        targetRawTag = targetTag,
        targetAccentKind = when {
            isInstall -> GitHubDirectApkAssetAccentKind.Install
            isPreRelease -> GitHubDirectApkAssetAccentKind.PreRelease
            else -> GitHubDirectApkAssetAccentKind.Stable
        }
    )
}

private fun GitHubTrackedApp.fallbackDirectApkVersionInfo(): GitHubRemoteApkVersionInfo? {
    val url = repoUrl.trim()
    if (!url.lowercase(Locale.ROOT).substringBefore('?').endsWith(".apk")) return null
    val assetName = assetNameFromUrl(url).ifBlank { "remote.apk" }
    return GitHubRemoteApkVersionInfo(
        releaseName = appLabel.ifBlank { assetDisplayName(assetName) },
        releaseTag = assetDisplayName(assetName),
        releaseUrl = url,
        assetName = assetName,
        fetchSource = "direct_apk"
    )
}

private fun assetNameFromUrl(url: String): String {
    return runCatching {
        val rawName = URI(url).rawPath
            .orEmpty()
            .substringAfterLast('/')
            .substringBefore('?')
        URLDecoder.decode(rawName, Charsets.UTF_8.name())
    }.getOrDefault("")
}
