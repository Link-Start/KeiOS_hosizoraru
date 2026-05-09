package os.kei.ui.page.main.github.asset

import android.content.Context
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFetchSources
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseAssetRepository
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.ui.page.main.github.VersionCheckUi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class ApkAssetTarget(
    val rawTag: String,
    val releaseUrl: String,
    val label: String
)

private val releaseUpdatedAtFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yy-MM-dd HH:mm", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

private val releaseUpdatedAtNoYearFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("MM-dd HH:mm", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

private val arm64TokenRegex = Regex("""(?:^|[^a-z0-9])arm64(?:[^a-z0-9]|$)""")
private val armeabiTokenRegex = Regex("""(?:^|[^a-z0-9])armeabi(?:[^a-z0-9]|$)""")
private val x86TokenRegex = Regex("""(?:^|[^a-z0-9])x86(?:[^a-z0-9]|$)""")

private data class LatestReleaseCandidate(
    val rawTag: String,
    val releaseUrl: String,
    val publishedAtMillis: Long?
) {
    fun hasTarget(): Boolean = rawTag.isNotBlank() || releaseUrl.isNotBlank()
}

internal fun VersionCheckUi.apkAssetTarget(
    owner: String,
    repo: String,
    context: Context,
    alwaysLatestRelease: Boolean = false,
    allowLatestReleaseFallback: Boolean = false
): ApkAssetTarget? {
    val stableTag = latestStableRawTag.ifBlank {
        GitHubReleaseAssetRepository.parseReleaseTagFromUrl(latestStableUrl)
    }
    val preTag = latestPreRawTag.ifBlank {
        GitHubReleaseAssetRepository.parseReleaseTagFromUrl(latestPreUrl)
    }
    if (alwaysLatestRelease || allowLatestReleaseFallback) {
        val stableCandidate = LatestReleaseCandidate(
            rawTag = stableTag.trim().ifBlank { latestTag.trim() },
            releaseUrl = latestStableUrl.trim(),
            publishedAtMillis = latestStableUpdatedAtMillis.takeIf { it > 0L }
        )
        val preCandidate = LatestReleaseCandidate(
            rawTag = preTag.trim(),
            releaseUrl = latestPreUrl.trim(),
            publishedAtMillis = latestPreUpdatedAtMillis.takeIf { it > 0L }
        )
        val latestByDate = listOf(stableCandidate, preCandidate)
            .filter { it.hasTarget() && it.publishedAtMillis != null }
            .maxByOrNull { it.publishedAtMillis ?: Long.MIN_VALUE }
        val selected = latestByDate
            ?: stableCandidate.takeIf { it.hasTarget() }
            ?: preCandidate.takeIf { it.hasTarget() }
            ?: return null
        val targetTag = selected.rawTag.ifBlank {
            GitHubReleaseAssetRepository.parseReleaseTagFromUrl(selected.releaseUrl)
        }
        if (targetTag.isBlank()) return null
        val releaseUrl = selected.releaseUrl.ifBlank {
            GitHubVersionUtils.buildReleaseTagUrl(owner, repo, targetTag)
        }
        return ApkAssetTarget(
            rawTag = targetTag,
            releaseUrl = releaseUrl,
            label = context.getString(
                if (alwaysLatestRelease) {
                    R.string.github_asset_target_latest
                } else {
                    R.string.github_release_notes_detail_target_latest
                }
            )
        )
    }
    return when {
        recommendsPreRelease && preTag.isNotBlank() -> ApkAssetTarget(
            rawTag = preTag,
            releaseUrl = latestPreUrl.ifBlank { GitHubVersionUtils.buildReleaseTagUrl(owner, repo, preTag) },
            label = context.getString(R.string.github_asset_target_prerelease)
        )
        hasUpdate == true && stableTag.isNotBlank() -> ApkAssetTarget(
            rawTag = stableTag,
            releaseUrl = latestStableUrl.ifBlank { GitHubVersionUtils.buildReleaseTagUrl(owner, repo, stableTag) },
            label = context.getString(R.string.github_asset_target_stable)
        )
        hasPreReleaseUpdate && preTag.isNotBlank() -> ApkAssetTarget(
            rawTag = preTag,
            releaseUrl = latestPreUrl.ifBlank { GitHubVersionUtils.buildReleaseTagUrl(owner, repo, preTag) },
            label = context.getString(R.string.github_asset_target_prerelease)
        )
        else -> null
    }
}

internal fun formatAssetSize(sizeBytes: Long, context: Context): String {
    if (sizeBytes <= 0L) return context.getString(R.string.common_unknown)
    val kb = 1024L
    val mb = kb * 1024L
    val gb = mb * 1024L
    return when {
        sizeBytes >= gb -> String.format("%.1fG", sizeBytes.toDouble() / gb.toDouble())
        sizeBytes >= mb -> String.format("%.1fM", sizeBytes.toDouble() / mb.toDouble())
        sizeBytes >= kb -> String.format("%.0fK", sizeBytes.toDouble() / kb.toDouble())
        else -> "${sizeBytes}B"
    }
}

internal fun prefersApiAssetTransport(asset: GitHubReleaseAssetFile): Boolean {
    return asset.apiAssetUrl.isNotBlank()
}

internal fun assetTransportLabel(asset: GitHubReleaseAssetFile, context: Context): String {
    return if (prefersApiAssetTransport(asset)) "API" else context.getString(R.string.github_asset_transport_direct)
}

internal fun bundleTransportLabel(bundle: GitHubReleaseAssetBundle?, context: Context): String? {
    bundle ?: return null
    return when (bundle.fetchSource.trim().lowercase()) {
        GitHubReleaseAssetFetchSources.API -> context.getString(R.string.github_asset_fetch_source_api)
        GitHubReleaseAssetFetchSources.HTML -> context.getString(R.string.github_asset_fetch_source_html)
        else -> if (bundle.assets.any { prefersApiAssetTransport(it) }) {
            context.getString(R.string.github_asset_fetch_source_api)
        } else {
            context.getString(R.string.github_asset_transport_direct)
        }
    }
}

internal fun bundleCommitLabel(bundle: GitHubReleaseAssetBundle?): String? {
    return bundle?.shortCommitSha?.trim().takeIf { !it.isNullOrBlank() }
}

internal fun bundleReleaseUpdatedAtMillis(bundle: GitHubReleaseAssetBundle?): Long? {
    bundle ?: return null
    val releaseUpdatedAt = bundle.releaseUpdatedAtMillis?.takeIf { it > 0L } ?: Long.MIN_VALUE
    val assetsUpdatedAt = bundle.assets
        .maxOfOrNull { it.updatedAtMillis ?: Long.MIN_VALUE }
        ?.takeIf { it > 0L } ?: Long.MIN_VALUE
    val latestUpdatedAt = maxOf(releaseUpdatedAt, assetsUpdatedAt)
    return latestUpdatedAt.takeIf { it > 0L }
}

internal fun formatReleaseUpdatedAtCompact(updatedAtMillis: Long?): String? {
    val millis = updatedAtMillis?.takeIf { it > 0L } ?: return null
    return runCatching {
        releaseUpdatedAtFormatter.format(Instant.ofEpochMilli(millis))
    }.getOrNull()
}

internal fun formatReleaseUpdatedAtNoYear(updatedAtMillis: Long?): String? {
    val millis = updatedAtMillis?.takeIf { it > 0L } ?: return null
    return runCatching {
        releaseUpdatedAtNoYearFormatter.format(Instant.ofEpochMilli(millis))
    }.getOrNull()
}

internal fun assetAbiLabel(fileName: String): String? {
    val lower = fileName.lowercase()
    return when {
        "arm64-v8a" in lower || "aarch64" in lower ||
                arm64TokenRegex.containsMatchIn(lower) -> "arm64"
        "universal" in lower || "fat" in lower -> "universal"
        "armeabi-v7a" in lower || "armv7" in lower -> "armeabi-v7a"
        armeabiTokenRegex.containsMatchIn(lower) -> "armeabi"
        "x86_64" in lower -> "x86_64"
        x86TokenRegex.containsMatchIn(lower) -> "x86"
        else -> null
    }
}

internal fun assetFileExtensionLabel(fileName: String): String? {
    val trimmedName = fileName.trim()
    val extension = trimmedName.substringAfterLast('.', "")
        .takeIf { '.' in trimmedName && it.isNotBlank() }
        ?.lowercase()
        ?: return null
    return extension
}

internal fun assetDisplayName(fileName: String): String {
    val trimmedName = fileName.trim()
    val extension = assetFileExtensionLabel(trimmedName) ?: return trimmedName
    return trimmedName.removeSuffix(".$extension")
}

internal fun assetRelativeTimeLabel(
    updatedAtMillis: Long?,
    context: Context
): String? {
    val updatedAt = updatedAtMillis ?: return null
    val diffMillis = (System.currentTimeMillis() - updatedAt).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
    val days = TimeUnit.MILLISECONDS.toDays(diffMillis)
    return when {
        minutes < 1L -> context.getString(R.string.github_asset_relative_just_now)
        minutes < 60L -> context.getString(R.string.github_asset_relative_min_ago, minutes)
        hours < 24L -> context.getString(R.string.github_asset_relative_hr_ago, hours)
        days < 7L && days == 1L -> context.getString(R.string.github_asset_relative_day_ago, days)
        days < 7L -> context.getString(R.string.github_asset_relative_days_ago, days)
        days < 30L -> context.getString(R.string.github_asset_relative_last_week)
        days < 60L -> context.getString(R.string.github_asset_relative_last_month)
        else -> context.getString(R.string.github_asset_relative_months_ago, days / 30L)
    }
}

internal fun assetIsPreferredForDevice(
    fileName: String,
    supportedAbis: List<String>
): Boolean {
    val assetAbi = assetAbiLabel(fileName) ?: return false
    val deviceAbiSet = supportedAbis
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .toSet()
    val hasArm64 = deviceAbiSet.any { abi -> "arm64" in abi || "aarch64" in abi }
    val hasArm32 = deviceAbiSet.any { abi ->
        "armeabi" in abi || "armv7" in abi || abi.startsWith("arm")
    }
    val hasX86_64 = deviceAbiSet.any { abi -> "x86_64" in abi }
    val hasX86 = deviceAbiSet.any { abi ->
        x86TokenRegex.containsMatchIn(abi)
    }
    return when (assetAbi) {
        "arm64" -> hasArm64
        "armeabi-v7a", "armeabi" -> !hasArm64 && hasArm32
        "x86_64" -> hasX86_64
        "x86" -> !hasX86_64 && hasX86
        else -> false
    }
}

internal fun assetLikelyCompatibleWithDevice(
    fileName: String,
    supportedAbis: List<String>
): Boolean {
    val assetAbi = assetAbiLabel(fileName) ?: return true
    val deviceAbiSet = supportedAbis
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .toSet()
    val hasArm64 = deviceAbiSet.any { abi -> "arm64" in abi || "aarch64" in abi }
    val hasArm32 = deviceAbiSet.any { abi ->
        "armeabi" in abi || "armv7" in abi || abi.startsWith("arm")
    }
    val hasX86_64 = deviceAbiSet.any { abi -> "x86_64" in abi }
    val hasX86 = deviceAbiSet.any { abi ->
        x86TokenRegex.containsMatchIn(abi)
    }
    return when (assetAbi) {
        "universal" -> true
        "arm64" -> hasArm64
        "armeabi-v7a", "armeabi" -> hasArm64 || hasArm32
        "x86_64" -> hasX86_64
        "x86" -> hasX86_64 || hasX86
        else -> true
    }
}
