package os.kei.ui.page.main.github.install

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.feature.github.model.GitHubApkTrustReason
import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.github.GitHubStatusPalette

internal data class InstallComparisonVerdictModel(
    val label: String,
    val color: Color
)

internal fun GitHubApkInstallPhase.labelRes(): Int {
    return when (this) {
        GitHubApkInstallPhase.RemoteResolving -> R.string.github_apk_install_phase_remote_resolving
        GitHubApkInstallPhase.RemoteReady -> R.string.github_apk_install_phase_remote_ready
        GitHubApkInstallPhase.Downloading -> R.string.github_apk_install_phase_downloading
        GitHubApkInstallPhase.SelectingApk -> R.string.github_apk_install_phase_selecting
        GitHubApkInstallPhase.InspectingLocal -> R.string.github_apk_install_phase_inspecting
        GitHubApkInstallPhase.ReadyToInstall -> R.string.github_apk_install_phase_ready
        GitHubApkInstallPhase.Installing -> R.string.github_apk_install_phase_installing
        GitHubApkInstallPhase.PendingUserAction -> R.string.github_apk_install_phase_pending
        GitHubApkInstallPhase.Success -> R.string.github_apk_install_phase_success
        GitHubApkInstallPhase.Failed -> R.string.github_apk_install_phase_failed
        GitHubApkInstallPhase.Cancelled -> R.string.github_apk_install_cancelled
        GitHubApkInstallPhase.Idle -> R.string.github_apk_install_phase_installing
    }
}

internal fun GitHubApkInstallPhase.stepLabelRes(): Int {
    return when (this) {
        GitHubApkInstallPhase.RemoteResolving -> R.string.github_apk_install_notify_short_resolving
        GitHubApkInstallPhase.RemoteReady -> R.string.github_apk_install_notify_short_ready
        GitHubApkInstallPhase.Downloading -> R.string.github_apk_install_notify_short_downloading
        GitHubApkInstallPhase.SelectingApk -> R.string.github_apk_install_notify_short_selecting
        GitHubApkInstallPhase.InspectingLocal -> R.string.github_apk_install_notify_short_inspecting
        GitHubApkInstallPhase.ReadyToInstall -> R.string.github_apk_install_notify_short_review
        GitHubApkInstallPhase.Installing,
        GitHubApkInstallPhase.PendingUserAction -> R.string.github_apk_install_notify_short_installing

        GitHubApkInstallPhase.Success -> R.string.github_apk_install_notify_short_success
        GitHubApkInstallPhase.Failed -> R.string.github_apk_install_notify_short_failed
        GitHubApkInstallPhase.Cancelled -> R.string.github_apk_install_notify_short_cancelled
        GitHubApkInstallPhase.Idle -> R.string.github_apk_install_notify_short_installing
    }
}

internal fun GitHubApkInstallPhase.statusColor(): Color {
    return when (this) {
        GitHubApkInstallPhase.Success -> GitHubStatusPalette.Update
        GitHubApkInstallPhase.Failed,
        GitHubApkInstallPhase.Cancelled -> GitHubStatusPalette.Error

        GitHubApkInstallPhase.RemoteReady,
        GitHubApkInstallPhase.ReadyToInstall,
        GitHubApkInstallPhase.PendingUserAction -> GitHubStatusPalette.Cache

        else -> GitHubStatusPalette.Active
    }
}

internal fun Long?.orZero(): Long = this ?: 0L

internal fun GitHubApkInstallFlowState.displayFileName(): String {
    return selectedCandidateName
        .ifBlank { asset?.name.orEmpty() }
        .ifBlank { request.externalFileName }
        .ifBlank { request.displayLabel }
}

internal fun GitHubApkInstallFlowState.identityName(): String {
    return candidateAppLabel()
        .ifBlank { installedPackageInfo?.appLabel.orEmpty() }
        .ifBlank { request.sourceLabel }
        .ifBlank { request.displayLabel }
}

internal fun GitHubApkInstallFlowState.candidateAppLabel(): String {
    return localArchiveInfo?.appLabel.orEmpty()
}

internal fun GitHubApkInstallFlowState.candidateVersionName(): String {
    return localArchiveInfo
        ?.versionName
        .orEmpty()
        .ifBlank {
            (remoteManifestInfo ?: request.remoteManifestInfo)
                ?.versionName
                ?.trim()
                .orEmpty()
        }
}

@Composable
internal fun GitHubApkInstallFlowState.versionVerdict(): InstallComparisonVerdictModel? {
    val candidateCode = candidateVersionCode() ?: return null
    val localCode = installedPackageInfo?.versionCode?.takeIf { it >= 0L }
    val (labelRes, color) = when {
        localCode == null ->
            R.string.github_apk_install_version_status_new to GitHubStatusPalette.Active

        candidateCode > localCode ->
            R.string.github_apk_install_version_status_upgrade to GitHubStatusPalette.Update

        candidateCode < localCode ->
            R.string.github_apk_install_version_status_downgrade to GitHubStatusPalette.Error

        else ->
            R.string.github_apk_install_version_status_same to GitHubStatusPalette.Cache
    }
    return InstallComparisonVerdictModel(
        label = stringResource(labelRes),
        color = color
    )
}

internal fun GitHubApkInstallFlowState.candidateVersionCode(): Long? {
    return localArchiveInfo
        ?.versionCode
        ?.takeIf { it >= 0L }
        ?: (remoteManifestInfo ?: request.remoteManifestInfo)
            ?.versionCode
            ?.trim()
            ?.toLongOrNull()
}

internal fun GitHubApkInstallFlowState.candidateVersionCodeLabel(): String {
    return candidateVersionCode()?.toString().orEmpty()
}

internal fun GitHubInstalledPackageInfo.versionCodeLabel(): String {
    return versionCode.takeIf { it >= 0L }?.toString().orEmpty()
}

internal fun GitHubApkInstallFlowState.candidatePackageName(): String {
    return localArchiveInfo?.packageName.orEmpty()
        .ifBlank { remoteManifestInfo?.packageName.orEmpty() }
        .ifBlank { request.remoteManifestInfo?.packageName.orEmpty() }
        .ifBlank { request.expectedPackageName }
}

internal fun GitHubApkInstallFlowState.candidateTargetSdkLabel(): String {
    localArchiveInfo?.targetSdk?.takeIf { it >= 0 }?.let { return it.toString() }
    return (remoteManifestInfo ?: request.remoteManifestInfo)
        ?.targetSdk
        ?.trim()
        .orEmpty()
}

internal fun GitHubApkInstallFlowState.candidateMinSdkLabel(): String {
    localArchiveInfo?.minSdk?.takeIf { it >= 0 }?.let { return it.toString() }
    return (remoteManifestInfo ?: request.remoteManifestInfo)
        ?.minSdk
        ?.trim()
        .orEmpty()
}

internal fun Int?.sdkLabel(): String {
    return this?.takeIf { it >= 0 }?.toString().orEmpty()
}

@Composable
internal fun GitHubApkInstallFlowState.candidateAbiLabel(): String {
    val universal = stringResource(R.string.github_apk_install_value_universal)
    val unknown = stringResource(R.string.github_apk_install_value_unknown)
    val archive = localArchiveInfo
    if (archive != null) {
        return archive.nativeAbis.shortAbiList().ifBlank { universal }
    }
    val remoteAbis = (remoteManifestInfo ?: request.remoteManifestInfo)?.nativeAbis.orEmpty()
    if (remoteAbis.isNotEmpty()) {
        return remoteAbis.shortAbiList()
    }
    return inferAbiNames(displayFileName()).shortAbiList().ifBlank { unknown }
}

internal fun GitHubApkInstallFlowState.candidateSignatureShortLabel(): String {
    val localSignature = localArchiveInfo?.signatureSha256?.firstOrNull()
    if (!localSignature.isNullOrBlank()) {
        return localSignature.shortSha()
    }
    val remoteSignature = (remoteManifestInfo ?: request.remoteManifestInfo)
        ?.signatureInfo
        ?.sha256
        .orEmpty()
    if (remoteSignature.isNotBlank()) {
        return remoteSignature.shortSha()
    }
    return ""
}

internal fun comparisonValue(
    context: android.content.Context,
    localValue: String,
    candidateValue: String
): String {
    val local = localValue.trim()
    val candidate = candidateValue.trim()
    return when {
        local.isNotBlank() && candidate.isNotBlank() && local != candidate ->
            context.getString(R.string.github_apk_install_reference_compare_value, local, candidate)

        candidate.isNotBlank() -> candidate
        else -> local
    }
}

internal fun GitHubApkInstallFlowState.installPrimaryActionLabelRes(): Int {
    val candidateCode = candidateVersionCode()
    val localCode = installedPackageInfo?.versionCode?.takeIf { it >= 0L }
    return when {
        candidateCode != null && localCode != null && candidateCode > localCode ->
            R.string.github_apk_install_action_upgrade

        candidateCode != null && localCode != null && candidateCode < localCode ->
            R.string.github_apk_install_action_downgrade

        else -> R.string.github_apk_install_action_install
    }
}

internal fun deviceAbiLabel(): String {
    return Build.SUPPORTED_ABIS
        .orEmpty()
        .toList()
        .shortAbiList()
}

private fun List<String>.shortAbiList(maxItems: Int = 2): String {
    val normalized = map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    if (normalized.isEmpty()) return ""
    val head = normalized.take(maxItems).joinToString("/")
    val extra = normalized.size - maxItems
    return if (extra > 0) "$head +$extra" else head
}

private fun inferAbiNames(name: String): List<String> {
    val lowerName = name.lowercase()
    return buildList {
        if ("arm64-v8a" in lowerName || "arm64" in lowerName || "aarch64" in lowerName) {
            add("arm64-v8a")
        }
        if ("armeabi-v7a" in lowerName || "armv7" in lowerName || "arm-v7" in lowerName) {
            add("armeabi-v7a")
        }
        if ("x86_64" in lowerName || "x64" in lowerName) {
            add("x86_64")
        }
        if (Regex("""(?:^|[^a-z0-9])x86(?:[^a-z0-9]|$)""").containsMatchIn(lowerName)) {
            add("x86")
        }
    }
}

internal fun String.shortSha(): String {
    return trim()
        .replace(":", "")
        .take(12)
        .takeIf { it.isNotBlank() }
        .orEmpty()
}

internal fun GitHubApkInstallPhase.passiveActionLabelRes(): Int {
    return when (this) {
        GitHubApkInstallPhase.RemoteResolving -> R.string.github_apk_install_action_preparing
        GitHubApkInstallPhase.RemoteReady -> R.string.github_apk_install_action_prepare_install
        GitHubApkInstallPhase.Installing -> R.string.github_apk_install_phase_installing
        GitHubApkInstallPhase.SelectingApk -> R.string.github_apk_install_phase_selecting
        else -> R.string.github_apk_install_action_preparing
    }
}

internal fun GitHubDecisionLevel.statusColor(): Color {
    return when (this) {
        GitHubDecisionLevel.Good -> GitHubStatusPalette.Update
        GitHubDecisionLevel.Review -> GitHubStatusPalette.Cache
        GitHubDecisionLevel.Risk -> GitHubStatusPalette.Error
    }
}

internal fun GitHubApkTrustReason.statusColor(): Color {
    return when (this) {
        GitHubApkTrustReason.PackageMismatch,
        GitHubApkTrustReason.SignatureMismatch,
        GitHubApkTrustReason.VersionDowngrade,
        GitHubApkTrustReason.MinSdkTooHigh,
        GitHubApkTrustReason.IncompatibleAbi,
        GitHubApkTrustReason.UnsignedBuild,
        GitHubApkTrustReason.SourceArchive,
        GitHubApkTrustReason.UnknownFormat -> GitHubStatusPalette.Error

        GitHubApkTrustReason.DebugBuild,
        GitHubApkTrustReason.TestOnly,
        GitHubApkTrustReason.SensitivePermission,
        GitHubApkTrustReason.ExportedComponent,
        GitHubApkTrustReason.SignatureUnknown -> GitHubStatusPalette.Cache

        GitHubApkTrustReason.PackageMatched,
        GitHubApkTrustReason.SignatureMatched,
        GitHubApkTrustReason.VersionUpgrade,
        GitHubApkTrustReason.PreferredAbi,
        GitHubApkTrustReason.UniversalAsset -> GitHubStatusPalette.Update

        GitHubApkTrustReason.ApkLike -> GitHubStatusPalette.Active
    }
}

internal fun GitHubDecisionLevel.labelRes(): Int {
    return when (this) {
        GitHubDecisionLevel.Good -> R.string.github_apk_trust_good
        GitHubDecisionLevel.Review -> R.string.github_apk_trust_review
        GitHubDecisionLevel.Risk -> R.string.github_apk_trust_risk
    }
}

internal fun GitHubApkTrustReason.labelRes(): Int {
    return when (this) {
        GitHubApkTrustReason.PreferredAbi -> R.string.github_apk_trust_reason_preferred_abi
        GitHubApkTrustReason.UniversalAsset -> R.string.github_apk_trust_reason_universal
        GitHubApkTrustReason.IncompatibleAbi -> R.string.github_apk_trust_reason_incompatible
        GitHubApkTrustReason.DebugBuild -> R.string.github_apk_trust_reason_debug
        GitHubApkTrustReason.UnsignedBuild -> R.string.github_apk_trust_reason_unsigned
        GitHubApkTrustReason.SourceArchive -> R.string.github_apk_trust_reason_source
        GitHubApkTrustReason.ApkLike -> R.string.github_apk_trust_reason_apk
        GitHubApkTrustReason.UnknownFormat -> R.string.github_apk_trust_reason_unknown_format
        GitHubApkTrustReason.PackageMatched -> R.string.github_apk_trust_reason_package_matched
        GitHubApkTrustReason.PackageMismatch -> R.string.github_apk_trust_reason_package_mismatch
        GitHubApkTrustReason.SignatureMatched -> R.string.github_apk_trust_reason_signature_matched
        GitHubApkTrustReason.SignatureMismatch -> R.string.github_apk_trust_reason_signature_mismatch
        GitHubApkTrustReason.SignatureUnknown -> R.string.github_apk_trust_reason_signature_unknown
        GitHubApkTrustReason.VersionUpgrade -> R.string.github_apk_trust_reason_version_upgrade
        GitHubApkTrustReason.VersionDowngrade -> R.string.github_apk_trust_reason_version_downgrade
        GitHubApkTrustReason.MinSdkTooHigh -> R.string.github_apk_trust_reason_min_sdk_high
        GitHubApkTrustReason.TestOnly -> R.string.github_apk_trust_reason_test_only
        GitHubApkTrustReason.SensitivePermission -> R.string.github_apk_trust_reason_sensitive_permission
        GitHubApkTrustReason.ExportedComponent -> R.string.github_apk_trust_reason_exported_component
    }
}
