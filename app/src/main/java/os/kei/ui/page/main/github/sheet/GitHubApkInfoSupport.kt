package os.kei.ui.page.main.github.sheet

import android.os.Build
import androidx.compose.ui.graphics.Color
import os.kei.R
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubApkManifestNode
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.github.GitHubStatusPalette

internal const val INFO_LIST_LIMIT = 12
internal const val MANIFEST_NODE_LIMIT = 8

internal fun buildApkDifferenceSignals(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?,
    strings: ApkDifferenceStrings
): List<ApkDifferenceSignal> {
    val signals = mutableListOf<ApkDifferenceSignal>()
    val remoteVersionCode = info.versionCode.toLongOrNull()
    val remoteTargetSdk = info.targetSdk.toIntOrNull()
    if (installedInfo == null) {
        signals += ApkDifferenceSignal(
            label = strings.notInstalled,
            color = GitHubStatusPalette.Active
        )
    } else {
        signals += when {
            remoteVersionCode == null || installedInfo.versionCode < 0L ->
                ApkDifferenceSignal(strings.versionManual, GitHubStatusPalette.Cache)

            remoteVersionCode > installedInfo.versionCode ->
                ApkDifferenceSignal(strings.remoteVersionHigher, GitHubStatusPalette.Update)

            remoteVersionCode < installedInfo.versionCode ->
                ApkDifferenceSignal(strings.localVersionHigher, GitHubStatusPalette.PreRelease)

            else -> ApkDifferenceSignal(strings.sameVersion, GitHubStatusPalette.Stable)
        }
        if (remoteTargetSdk != null && installedInfo.targetSdk >= 0) {
            signals += when {
                remoteTargetSdk > installedInfo.targetSdk ->
                    ApkDifferenceSignal(strings.targetHigher, GitHubStatusPalette.Update)

                remoteTargetSdk < installedInfo.targetSdk ->
                    ApkDifferenceSignal(strings.targetLower, GitHubStatusPalette.Cache)

                else -> ApkDifferenceSignal(strings.targetSame, GitHubStatusPalette.Stable)
            }
        }
    }
    signals += buildAbiSignal(info.nativeAbis, strings)
    return signals
}

internal fun compareVersionColors(
    remoteVersionCode: Long?,
    localVersionCode: Long
): ComparisonColors {
    return when {
        remoteVersionCode == null || localVersionCode < 0L ->
            ComparisonColors(GitHubStatusPalette.Cache, GitHubStatusPalette.Cache)

        remoteVersionCode > localVersionCode ->
            ComparisonColors(GitHubStatusPalette.Update, GitHubStatusPalette.Stable)

        remoteVersionCode < localVersionCode ->
            ComparisonColors(GitHubStatusPalette.Cache, GitHubStatusPalette.PreRelease)

        else -> ComparisonColors(GitHubStatusPalette.Stable, GitHubStatusPalette.Stable)
    }
}

internal fun compareApiColors(
    remoteTargetSdk: Int?,
    localTargetSdk: Int
): ComparisonColors {
    return when {
        remoteTargetSdk == null || localTargetSdk < 0 ->
            ComparisonColors(GitHubStatusPalette.Cache, GitHubStatusPalette.Cache)

        remoteTargetSdk > localTargetSdk ->
            ComparisonColors(GitHubStatusPalette.Update, GitHubStatusPalette.Stable)

        remoteTargetSdk < localTargetSdk ->
            ComparisonColors(GitHubStatusPalette.Cache, GitHubStatusPalette.Active)

        else -> ComparisonColors(GitHubStatusPalette.Stable, GitHubStatusPalette.Stable)
    }
}

internal fun GitHubApkManifestInfo.remoteVersionLabel(): String {
    return listOf(versionName, versionCode)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
        .ifBlank { "-" }
}

internal fun GitHubInstalledPackageInfo.localVersionLabel(): String {
    return listOf(
        versionName,
        versionCode.takeIf { it >= 0L }?.toString().orEmpty()
    ).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "-" }
}

internal fun List<String>.filterStringsByQuery(query: String): List<String> {
    val normalized = query.trim()
    if (normalized.isBlank()) return this
    return filter { it.contains(normalized, ignoreCase = true) }
}

internal fun List<GitHubApkManifestNode>.filterNodesByQuery(
    query: String
): List<GitHubApkManifestNode> {
    val normalized = query.trim()
    if (normalized.isBlank()) return this
    return filter { node ->
        node.tagName.contains(normalized, ignoreCase = true) ||
                node.displayName.contains(normalized, ignoreCase = true) ||
                node.attributes.any { (key, value) ->
                    key.contains(normalized, ignoreCase = true) ||
                            value.contains(normalized, ignoreCase = true)
                }
    }
}

internal fun permissionRiskColor(permission: String): Color {
    val lower = permission.lowercase()
    return when {
        sensitivePermissionSignals.any { lower.contains(it) } -> GitHubStatusPalette.Error
        reviewPermissionSignals.any { lower.contains(it) } -> GitHubStatusPalette.Cache
        else -> GitHubStatusPalette.Active
    }
}

internal fun GitHubApkManifestNode.isExportedComponent(): Boolean {
    return tagName in exportedComponentTags && attributes["exported"].equals(
        "true",
        ignoreCase = true
    )
}

internal fun GitHubApkManifestNode.groupLabelRes(): Int {
    return when {
        isExportedComponent() -> R.string.github_apk_info_group_exported
        tagName in componentTags -> R.string.github_apk_info_group_components
        tagName in queryTags -> R.string.github_apk_info_group_queries
        tagName in intentTags -> R.string.github_apk_info_group_intents
        tagName == "application" -> R.string.github_apk_info_group_application
        else -> R.string.github_apk_info_group_other_manifest
    }
}

internal fun GitHubApkManifestNode.displayLine(): String {
    val useful = listOf("exported", "permission", "authorities", "process", "enabled")
        .mapNotNull { key -> attributes[key]?.takeIf { it.isNotBlank() }?.let { "$key=$it" } }
    return buildString {
        append('<')
        append(tagName)
        append("> ")
        append(displayName)
        if (useful.isNotEmpty()) {
            append(" · ")
            append(useful.joinToString(" · "))
        }
    }
}

internal fun GitHubApkManifestNode.riskPills(): List<ApkDifferenceSignal> {
    val signals = mutableListOf<ApkDifferenceSignal>()
    if (isExportedComponent()) {
        signals += ApkDifferenceSignal("exported", GitHubStatusPalette.Cache)
        if (attributes["permission"].isNullOrBlank()) {
            signals += ApkDifferenceSignal("no permission", GitHubStatusPalette.Error)
        }
    }
    attributes["permission"]?.takeIf { it.isNotBlank() }?.let { permission ->
        signals += ApkDifferenceSignal("perm $permission", permissionRiskColor(permission))
    }
    return signals
}

internal fun buildAbiSignal(
    nativeAbis: List<String>,
    strings: ApkDifferenceStrings
): ApkDifferenceSignal {
    if (nativeAbis.isEmpty()) {
        return ApkDifferenceSignal(strings.abiUniversal, GitHubStatusPalette.Stable)
    }
    val supportedAbis = Build.SUPPORTED_ABIS.orEmpty().map { it.lowercase() }.toSet()
    val hasDeviceAbi = nativeAbis.any { abi -> abi.lowercase() in supportedAbis }
    return if (hasDeviceAbi) {
        ApkDifferenceSignal(strings.abiMatch, GitHubStatusPalette.Update)
    } else {
        ApkDifferenceSignal(strings.abiMismatch, GitHubStatusPalette.Error)
    }
}

internal data class ApkDifferenceSignal(
    val label: String,
    val color: Color
)

internal data class ComparisonColors(
    val remote: Color,
    val local: Color
)

internal data class ApkDifferenceStrings(
    val notInstalled: String,
    val remoteVersionHigher: String,
    val localVersionHigher: String,
    val sameVersion: String,
    val versionManual: String,
    val targetHigher: String,
    val targetSame: String,
    val targetLower: String,
    val abiUniversal: String,
    val abiMatch: String,
    val abiMismatch: String
)

private val sensitivePermissionSignals = listOf(
    "install_packages",
    "delete_packages",
    "manage_external_storage",
    "read_sms",
    "send_sms",
    "receive_sms",
    "read_contacts",
    "record_audio",
    "camera",
    "access_fine_location",
    "bind_accessibility_service",
    "system_alert_window",
    "request_ignore_battery_optimizations",
    "query_all_packages"
)

private val reviewPermissionSignals = listOf(
    "post_notifications",
    "read_external_storage",
    "write_external_storage",
    "read_media",
    "access_coarse_location",
    "bluetooth",
    "nearby",
    "wake_lock"
)

private val componentTags = setOf("activity", "activity-alias", "service", "receiver", "provider")
private val exportedComponentTags = componentTags
private val queryTags = setOf("queries", "package", "intent")
private val intentTags = setOf("intent-filter", "action", "category", "data")
