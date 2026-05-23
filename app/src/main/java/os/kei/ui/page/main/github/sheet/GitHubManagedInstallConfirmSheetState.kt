package os.kei.ui.page.main.github.sheet

import androidx.compose.runtime.Immutable
import os.kei.feature.github.data.remote.isGitHubActionsApkArtifactArchive
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.github.GitHubApkTrustSignal
import os.kei.ui.page.main.github.GitHubDecisionLevel
import os.kei.ui.page.main.github.asset.assetAbiLabel
import os.kei.ui.page.main.github.asset.assetDisplayName
import os.kei.ui.page.main.github.asset.assetIsPreferredForDevice
import os.kei.ui.page.main.github.asset.assetLikelyCompatibleWithDevice
import os.kei.ui.page.main.github.buildGitHubApkTrustSignal
import os.kei.ui.page.main.github.page.GitHubManagedInstallConfirmRequest

@Immutable
internal data class GitHubManagedInstallConfirmSheetInput(
    val requestKey: String = "",
    val request: GitHubManagedInstallConfirmRequest? = null,
    val info: GitHubApkManifestInfo? = null,
    val installedInfo: GitHubInstalledPackageInfo? = null,
    val supportedAbis: List<String> = emptyList(),
)

@Immutable
internal data class GitHubManagedInstallConfirmSheetUiState(
    val requestKey: String = "",
    val canConfirmWithoutManifest: Boolean = false,
    val trustSignal: GitHubApkTrustSignal = GitHubApkTrustSignal(GitHubDecisionLevel.Good, emptyList()),
    val preferredForDevice: Boolean = false,
    val likelyCompatible: Boolean = true,
    val abiLabel: String? = null,
    val supportedAbis: List<String> = emptyList(),
    val title: String = "",
    val remoteAppLabel: String = "",
    val versionDecision: InstallVersionDecision? = null,
    val abiDecision: InstallAbiDecision? = null,
)

internal enum class InstallVersionDecision {
    Install,
    Update,
    Downgrade,
    Same,
}

internal enum class InstallAbiDecision {
    Match,
    Universal,
    Mismatch,
    Unknown,
}

internal fun deriveGitHubManagedInstallConfirmSheetState(
    input: GitHubManagedInstallConfirmSheetInput,
): GitHubManagedInstallConfirmSheetUiState {
    val request = input.request ?: return GitHubManagedInstallConfirmSheetUiState(requestKey = input.requestKey)
    val asset = request.asset
    val trustSignal = buildGitHubApkTrustSignal(asset, input.supportedAbis)
    val likelyCompatible = assetLikelyCompatibleWithDevice(asset.name, input.supportedAbis)
    val remoteAppLabel =
        input.info
            ?.let { manifestInfo ->
                resolvedRemoteAppLabel(
                    info = manifestInfo,
                    installedInfo = input.installedInfo,
                    fallbackAppLabel = request.item.appLabel,
                    assetName = asset.name,
                )
            }.orEmpty()
            .ifBlank { input.installedInfo?.appLabel.orEmpty() }
            .ifBlank { request.item.appLabel }
            .ifBlank { assetDisplayName(asset.name) }
    return GitHubManagedInstallConfirmSheetUiState(
        requestKey = input.requestKey,
        canConfirmWithoutManifest = asset.isGitHubActionsApkArtifactArchive(),
        trustSignal = trustSignal,
        preferredForDevice = assetIsPreferredForDevice(asset.name, input.supportedAbis),
        likelyCompatible = likelyCompatible,
        abiLabel = assetAbiLabel(asset.name),
        supportedAbis = input.supportedAbis,
        title = remoteAppLabel,
        remoteAppLabel = remoteAppLabel,
        versionDecision =
            input.info?.let { manifestInfo ->
                buildInstallVersionDecision(manifestInfo, input.installedInfo)
            },
        abiDecision =
            input.info?.let { manifestInfo ->
                buildInstallAbiDecision(
                    info = manifestInfo,
                    supportedAbis = input.supportedAbis,
                    likelyCompatible = likelyCompatible,
                )
            },
    )
}

private fun resolvedRemoteAppLabel(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?,
    fallbackAppLabel: String,
    assetName: String,
): String =
    info.appLabel
        .trim()
        .ifBlank { installedInfo?.appLabel.orEmpty().trim() }
        .ifBlank { fallbackAppLabel.trim() }
        .ifBlank { assetDisplayName(assetName) }

private fun buildInstallVersionDecision(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?,
): InstallVersionDecision {
    installedInfo ?: return InstallVersionDecision.Install
    val remoteVersionCode = info.versionCode.trim().toLongOrNull()
    val localVersionCode = installedInfo.versionCode.takeIf { it >= 0L }
    val sameVersionCode =
        remoteVersionCode != null &&
            localVersionCode != null &&
            remoteVersionCode == localVersionCode
    val sameVersionName =
        info.versionName
            .trim()
            .equals(installedInfo.versionName.trim(), ignoreCase = false)
    return when {
        sameVersionCode && sameVersionName -> {
            InstallVersionDecision.Same
        }

        remoteVersionCode != null && localVersionCode != null && remoteVersionCode < localVersionCode -> {
            InstallVersionDecision.Downgrade
        }

        else -> {
            InstallVersionDecision.Update
        }
    }
}

private fun buildInstallAbiDecision(
    info: GitHubApkManifestInfo,
    supportedAbis: List<String>,
    likelyCompatible: Boolean,
): InstallAbiDecision {
    val remoteAbis = info.nativeAbis.map { it.trim() }.filter { it.isNotBlank() }
    if (remoteAbis.isEmpty()) return InstallAbiDecision.Universal
    val hasMatch =
        remoteAbis.any { remoteAbi ->
            supportedAbis.any { supported -> supported.equals(remoteAbi, ignoreCase = true) }
        }
    return when {
        hasMatch -> InstallAbiDecision.Match
        likelyCompatible -> InstallAbiDecision.Unknown
        else -> InstallAbiDecision.Mismatch
    }
}
