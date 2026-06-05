package os.kei.feature.github.model

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

enum class GitHubReleaseIgnoreChannel {
    Stable,
    PreRelease
}

fun GitHubTrackedIgnoreMode.suppressesAllReleaseUpdates(): Boolean {
    return this == GitHubTrackedIgnoreMode.Temporary ||
        this == GitHubTrackedIgnoreMode.AllVersions
}

fun GitHubTrackedApp.excludesAutomaticReleaseRefresh(): Boolean {
    return ignoreMode == GitHubTrackedIgnoreMode.Temporary ||
        ignoreMode == GitHubTrackedIgnoreMode.AllVersions
}

fun GitHubTrackedApp.withReleaseIgnoreMode(
    mode: GitHubTrackedIgnoreMode,
    stableReleaseKey: String = ignoredStableReleaseKey,
    preReleaseKey: String = ignoredPreReleaseKey
): GitHubTrackedApp {
    return when (mode) {
        GitHubTrackedIgnoreMode.None,
        GitHubTrackedIgnoreMode.Temporary,
        GitHubTrackedIgnoreMode.AllVersions -> copy(
            ignoreMode = mode,
            ignoredStableReleaseKey = "",
            ignoredPreReleaseKey = ""
        )

        GitHubTrackedIgnoreMode.CurrentStable -> copy(
            ignoreMode = mode,
            ignoredStableReleaseKey = stableReleaseKey.normalizedReleaseIgnoreKey(),
            ignoredPreReleaseKey = ""
        )

        GitHubTrackedIgnoreMode.CurrentPreRelease -> copy(
            ignoreMode = mode,
            ignoredStableReleaseKey = "",
            ignoredPreReleaseKey = preReleaseKey.normalizedReleaseIgnoreKey()
        )
    }
}

fun GitHubTrackedReleaseCheck.currentIgnoredReleaseChannel(): GitHubReleaseIgnoreChannel? {
    return when {
        recommendsPreRelease &&
            releaseIgnoreKeyForChannel(GitHubReleaseIgnoreChannel.PreRelease).isNotBlank() -> {
            GitHubReleaseIgnoreChannel.PreRelease
        }

        hasUpdate == true &&
            releaseIgnoreKeyForChannel(GitHubReleaseIgnoreChannel.Stable).isNotBlank() -> {
            GitHubReleaseIgnoreChannel.Stable
        }

        hasPreReleaseUpdate &&
            releaseIgnoreKeyForChannel(GitHubReleaseIgnoreChannel.PreRelease).isNotBlank() -> {
            GitHubReleaseIgnoreChannel.PreRelease
        }

        else -> null
    }
}

fun GitHubTrackedReleaseCheck.releaseIgnoreKeyForChannel(
    channel: GitHubReleaseIgnoreChannel
): String {
    return when (channel) {
        GitHubReleaseIgnoreChannel.Stable -> buildGitHubReleaseIgnoreKey(
            release = stableRelease,
            preciseApkVersion = preciseStableApkVersion
        )

        GitHubReleaseIgnoreChannel.PreRelease -> buildGitHubReleaseIgnoreKey(
            release = preRelease,
            preciseApkVersion = precisePreApkVersion
        )
    }
}

fun buildGitHubReleaseIgnoreKey(
    release: GitHubReleaseVersionSignals?,
    preciseApkVersion: GitHubRemoteApkVersionInfo?
): String {
    return buildGitHubReleaseIgnoreKey(
        displayVersion = release?.displayVersion.orEmpty(),
        rawTag = release?.rawTag.orEmpty(),
        rawName = release?.rawName.orEmpty(),
        link = release?.link.orEmpty(),
        preciseApkVersion = preciseApkVersion
    )
}

fun buildGitHubReleaseIgnoreKey(
    displayVersion: String = "",
    rawTag: String = "",
    rawName: String = "",
    link: String = "",
    preciseApkVersion: GitHubRemoteApkVersionInfo? = null
): String {
    val apkKey = preciseApkVersion
        ?.takeIf { it.hasVersion() }
        ?.let { info ->
            listOf(
                "apk",
                info.packageName,
                info.versionName,
                info.versionCode,
                info.releaseTag,
            ).normalizedReleaseIgnoreKeyParts()
        }
    if (!apkKey.isNullOrBlank()) return apkKey

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
    releaseKey: String
): Boolean {
    val normalizedStored = storedKey.normalizedReleaseIgnoreKey()
    val normalizedRelease = releaseKey.normalizedReleaseIgnoreKey()
    return normalizedStored.isNotBlank() && normalizedStored == normalizedRelease
}

private fun List<String>.normalizedReleaseIgnoreKeyParts(): String {
    val parts = map { it.normalizedReleaseIgnoreKey() }
        .filter { it.isNotBlank() }
    return if (parts.size <= 1) "" else parts.joinToString("|")
}

private fun String.normalizedReleaseIgnoreKey(): String {
    return trim().lowercase(Locale.ROOT)
}
