package os.kei.feature.github.model

import os.kei.core.versioning.VersionCandidate
import os.kei.core.versioning.VersioningEngine
import java.util.Locale

enum class GitHubTrackedIgnoreMode(val storageId: String) {
    None("none"),
    Temporary("temporary"),
    AllVersions("all_versions"),
    CurrentStable("current_stable"),
    CurrentPreRelease("current_prerelease");

    companion object {
        fun fromStorageId(value: String?): GitHubTrackedIgnoreMode {
            val normalized = value.orEmpty().trim().lowercase(Locale.ROOT)
            return entries.firstOrNull { it.storageId == normalized }
                ?: when (normalized) {
                    "",
                    "normal",
                    "enabled",
                    "tracking" -> None

                    "paused",
                    "pause",
                    "skip",
                    "temporary_ignore" -> Temporary

                    "all",
                    "all_versions",
                    "ignore_all" -> AllVersions

                    "stable",
                    "current_release",
                    "current_stable_release" -> CurrentStable

                    "pre",
                    "prerelease",
                    "pre_release",
                    "current_pre_release" -> CurrentPreRelease

                    else -> None
                }
        }
    }
}

enum class GitHubTrackedReleaseStatus(
    val defaultMessage: String,
    private val legacyMessage: String,
) {
    UpdateAvailable("github.status.update_available", "\u53d1\u73b0\u66f4\u65b0"),
    PreReleaseUpdateAvailable("github.status.prerelease_update_available", "\u9884\u53d1\u6709\u66f4\u65b0"),
    PreReleaseOptional("github.status.prerelease_optional", "\u9884\u53d1\u53ef\u9009"),
    PreReleaseTracked("github.status.prerelease_tracked", "\u9884\u53d1\u884c"),
    UpToDate("github.status.up_to_date", "\u5df2\u662f\u6700\u65b0"),
    Ignored("github.status.ignored", "\u5df2\u5ffd\u7565\u66f4\u65b0"),
    MatchedRelease("github.status.matched_release", "\u5df2\u5339\u914d\u53d1\u884c"),
    ComparisonUncertain(
        "github.status.comparison_uncertain",
        "\u7248\u672c\u683c\u5f0f\u65e0\u6cd5\u7cbe\u786e\u6bd4\u8f83",
    ),
    Failed("github.status.failed", "\u68c0\u67e5\u5931\u8d25");

    fun failureMessage(detail: String): String {
        return "$defaultMessage: $detail"
    }

    companion object {
        const val ONLY_PRERELEASES_HINT_MESSAGE = "github.status.only_prereleases_hint"
        private const val LEGACY_ONLY_PRERELEASES_HINT =
            "\u8be5\u9879\u76ee\u6682\u65f6\u53ef\u80fd\u53ea\u6709\u9884\u53d1\u884c\u7248"
        private const val ENGLISH_FAILED = "Check failed"

        fun fromMessage(raw: String): GitHubTrackedReleaseStatus? {
            val message = raw.trim()
            if (message.isBlank()) return null
            return entries.firstOrNull { status ->
                message == status.defaultMessage ||
                    message == status.legacyMessage ||
                    message.startsWith("${status.defaultMessage}:") ||
                    message.startsWith("${status.legacyMessage}:") ||
                    (
                        status == Failed &&
                            (message == ENGLISH_FAILED || message.startsWith("$ENGLISH_FAILED:"))
                    )
            }
        }

        fun isFailureMessage(raw: String): Boolean {
            return fromMessage(raw) == Failed
        }

        fun isOnlyPreReleasesHint(raw: String): Boolean {
            val message = raw.trim()
            return message == ONLY_PRERELEASES_HINT_MESSAGE ||
                message == LEGACY_ONLY_PRERELEASES_HINT
        }

        fun localizedFailureDetail(raw: String, prefix: String): String {
            val message = raw.trim()
            val failed = Failed
            return when {
                message.startsWith("${failed.defaultMessage}:") ->
                    message.replaceFirst(failed.defaultMessage, prefix)
                message.startsWith("${failed.legacyMessage}:") ->
                    message.replaceFirst(failed.legacyMessage, prefix)
                message.startsWith("$ENGLISH_FAILED:") ->
                    message.replaceFirst(ENGLISH_FAILED, prefix)
                message == failed.defaultMessage || message == failed.legacyMessage -> prefix
                message == ENGLISH_FAILED -> prefix
                else -> message
            }
        }
    }
}

fun GitHubTrackedIgnoreMode.suppressesAllReleaseUpdates(): Boolean {
    return this == GitHubTrackedIgnoreMode.Temporary ||
        this == GitHubTrackedIgnoreMode.AllVersions
}

fun buildGitHubReleaseIgnoreKey(
    release: GitHubReleaseVersionSignals?,
    preciseApkVersion: GitHubRemoteApkVersionInfo?,
): String {
    return buildGitHubReleaseIgnoreKey(
        displayVersion = release?.displayVersion.orEmpty(),
        rawTag = release?.rawTag.orEmpty(),
        rawName = release?.rawName.orEmpty(),
        link = release?.link.orEmpty(),
        preciseApkVersion = preciseApkVersion,
    )
}

/**
 * A stable name for "this release", so that ignoring it stays ignored.
 *
 * The one shape this has to get right is the rolling tag: a fixed tag — `preview`, `nightly`,
 * `continuous` — re-pointed by CI, whose *name* carries the build stamp and therefore changes on
 * every run. Keying on anything that moves with the build means the user dismisses the same row
 * daily and it comes back daily, which is what a rolling tag's whole point guarantees.
 *
 * So a tag carrying no version at all is taken at face value: it is the identity, because it is the
 * only part of that release that holds still. It is checked ahead of the APK identity for the same
 * reason — a rolling tag's APK version moves with every build too.
 */
fun buildGitHubReleaseIgnoreKey(
    displayVersion: String = "",
    rawTag: String = "",
    rawName: String = "",
    link: String = "",
    preciseApkVersion: GitHubRemoteApkVersionInfo? = null,
): String {
    val rollingTag = rawTag.trim().takeIf { tag ->
        tag.isNotBlank() &&
            !VersioningEngine.hasComparableCandidates(
                candidates = listOf(VersionCandidate(tag, sourcePriority = 0)),
            )
    }
    if (rollingTag != null) return "release|${rollingTag.normalizedReleaseIgnoreKey()}"

    val apkKey = preciseApkVersion
        ?.takeIf { it.hasVersion() }
        ?.let { info ->
            listOf(
                "apk",
                info.packageName,
                info.versionName,
                info.versionCode,
            ).normalizedReleaseIgnoreKeyParts()
        }
    if (!apkKey.isNullOrBlank()) return apkKey

    val structuredReleaseKey = VersioningEngine.releaseIdentityKey(
        candidates = buildList {
            if (rawTag.isNotBlank()) add(VersionCandidate(rawTag, sourcePriority = 0))
            if (rawName.isNotBlank()) add(VersionCandidate(rawName, sourcePriority = 1))
            if (displayVersion.isNotBlank()) {
                add(VersionCandidate(displayVersion, sourcePriority = 2))
            }
        },
    )
    if (!structuredReleaseKey.isNullOrBlank()) return "version|$structuredReleaseKey"

    val releaseKey = listOf(rawTag, link, rawName, displayVersion)
        .firstNotNullOfOrNull { value ->
            value.normalizedReleaseIgnoreKey().takeIf { it.isNotBlank() }
        }
        .orEmpty()
    if (releaseKey.isBlank()) return ""
    return "release|$releaseKey"
}

fun githubReleaseIgnoreKeyMatches(
    storedKey: String,
    releaseKey: String,
): Boolean {
    val normalizedStored = storedKey.normalizedComparableReleaseIgnoreKey()
    val normalizedRelease = releaseKey.normalizedComparableReleaseIgnoreKey()
    return normalizedStored.isNotBlank() && normalizedStored == normalizedRelease
}

private fun String.normalizedComparableReleaseIgnoreKey(): String {
    val normalized = normalizedReleaseIgnoreKey()
    if (normalized.startsWith("apk|")) {
        val parts = normalized.split('|')
        if (parts.size >= 4) return parts.take(4).joinToString("|")
    }
    if (normalized.startsWith("release|")) {
        val legacyValue = normalized.substringAfter("release|")
        val identity = VersioningEngine.releaseIdentityKey(
            candidates = listOf(VersionCandidate(legacyValue, sourcePriority = 0)),
        )
        if (!identity.isNullOrBlank()) {
            return "version|$identity".normalizedReleaseIgnoreKey()
        }
    }
    return normalized
}

private fun List<String>.normalizedReleaseIgnoreKeyParts(): String {
    val parts = map { it.normalizedReleaseIgnoreKey() }
        .filter { it.isNotBlank() }
    return if (parts.size <= 1) "" else parts.joinToString("|")
}

private fun String.normalizedReleaseIgnoreKey(): String {
    return trim().lowercase(Locale.ROOT)
}
