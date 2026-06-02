package os.kei.feature.github.data.local

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import os.kei.core.json.hasNonNull
import os.kei.core.json.optBoolean
import os.kei.core.json.optObject
import os.kei.core.json.optString
import os.kei.feature.github.model.GitHubDirectApkRemoteHealth
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedLocalAppType
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.GitHubTrackedUpdateIntervalMode
import os.kei.feature.github.model.buildDirectApkTrackIdentity
import os.kei.feature.github.model.buildGitRepositoryTrackIdentity
import os.kei.feature.github.model.withSourceModeConstraints

fun parseTrackedItem(obj: JsonObject): GitHubTrackedApp? {
    val settings = obj.optObject("settings")
    val source = obj.optObject("source")
    val repository = obj.optObject("repository")
    val repoUrl = obj.optString("repoUrl").trim().ifBlank {
        source?.optString("url").orEmpty().trim()
    }
    val sourceMode = parseTrackedSourceMode(obj)
    val directIdentity = if (sourceMode == GitHubTrackedSourceMode.DirectApk) {
        buildDirectApkTrackIdentity(repoUrl)
    } else {
        null
    }
    val gitIdentity = if (sourceMode == GitHubTrackedSourceMode.GitRepository) {
        buildGitRepositoryTrackIdentity(repoUrl)
    } else {
        null
    }
    val owner = obj.optString("owner").trim().ifBlank {
        source?.optString("owner").orEmpty().trim()
    }.ifBlank {
        gitIdentity?.owner.orEmpty()
    }.ifBlank {
        directIdentity?.owner.orEmpty()
    }
    val repo = obj.optString("repo").trim().ifBlank {
        source?.optString("repo").orEmpty().trim()
    }.ifBlank {
        gitIdentity?.repo.orEmpty()
    }.ifBlank {
        directIdentity?.repo.orEmpty()
    }
    val packageName = obj.optString("packageName").trim()
    val appLabel = obj.optString("appLabel").trim()
    if (repoUrl.isBlank() || owner.isBlank() || repo.isBlank()) {
        return null
    }
    val fallbackLabel = packageName.ifBlank {
        directIdentity?.displayName ?: gitIdentity?.displayName ?: "$owner/$repo"
    }
    val preferPreRelease = when {
        settings?.hasNonNull("preferPreRelease") == true ->
            settings.optBoolean("preferPreRelease", false)

        obj.hasNonNull("preferPreRelease") -> obj.optBoolean("preferPreRelease", false)
        obj.hasNonNull("checkPreRelease") -> obj.optBoolean("checkPreRelease", false)
        settings?.hasNonNull("checkPreRelease") == true ->
            settings.optBoolean("checkPreRelease", false)

        else -> false
    }
    val alwaysShowLatestReleaseDownloadButton = when {
        sourceMode == GitHubTrackedSourceMode.DirectApk ||
                sourceMode == GitHubTrackedSourceMode.GitRepository -> false
        settings?.hasNonNull("alwaysShowLatestReleaseDownloadButton") == true ->
            settings.optBoolean("alwaysShowLatestReleaseDownloadButton", false)

        obj.hasNonNull("alwaysShowLatestReleaseDownloadButton") ->
            obj.optBoolean("alwaysShowLatestReleaseDownloadButton", false)

        settings?.hasNonNull("alwaysShowLatestReleaseDownload") == true ->
            settings.optBoolean("alwaysShowLatestReleaseDownload", false)

        obj.hasNonNull("alwaysShowLatestReleaseDownload") ->
            obj.optBoolean("alwaysShowLatestReleaseDownload", false)

        else -> false
    }
    val checkActionsUpdates = when {
        sourceMode == GitHubTrackedSourceMode.DirectApk ||
                sourceMode == GitHubTrackedSourceMode.GitRepository -> false
        settings?.hasNonNull("checkActionsUpdates") == true ->
            settings.optBoolean("checkActionsUpdates", false)

        obj.hasNonNull("checkActionsUpdates") -> obj.optBoolean("checkActionsUpdates", false)
        else -> false
    }
    return GitHubTrackedApp(
        repoUrl = repoUrl,
        owner = owner,
        repo = repo,
        packageName = packageName,
        appLabel = appLabel.ifBlank { fallbackLabel },
        sourceMode = sourceMode,
        preferPreRelease = preferPreRelease,
        alwaysShowLatestReleaseDownloadButton = alwaysShowLatestReleaseDownloadButton,
        checkActionsUpdates = checkActionsUpdates,
        updateIntervalMode = parseUpdateIntervalMode(obj),
        actionsUpdateIntervalMode = parseActionsUpdateIntervalMode(obj),
        preciseApkVersionMode = parsePreciseApkVersionMode(obj),
        repositoryArchived = when {
            repository?.hasNonNull("archived") == true -> repository.optBoolean("archived", false)
            obj.hasNonNull("repositoryArchived") -> obj.optBoolean("repositoryArchived", false)
            else -> false
        },
        repositoryFork = when {
            repository?.hasNonNull("fork") == true -> repository.optBoolean("fork", false)
            obj.hasNonNull("repositoryFork") -> obj.optBoolean("repositoryFork", false)
            else -> false
        },
        localAppType = parseTrackedLocalAppType(obj)
    ).withSourceModeConstraints()
}

fun parseTrackedSourceMode(obj: JsonObject): GitHubTrackedSourceMode {
    val settings = obj.optObject("settings")
    val source = obj.optObject("source")
    return when {
        source?.hasNonNull("mode") == true ->
            GitHubTrackedSourceMode.fromStorageId(source.optString("mode"))

        settings?.hasNonNull("sourceMode") == true ->
            GitHubTrackedSourceMode.fromStorageId(settings.optString("sourceMode"))

        obj.hasNonNull("sourceMode") ->
            GitHubTrackedSourceMode.fromStorageId(obj.optString("sourceMode"))

        else -> GitHubTrackedSourceMode.GitHubRepository
    }
}

fun parseTrackedLocalAppType(obj: JsonObject): GitHubTrackedLocalAppType {
    val settings = obj.optObject("settings")
    val local = obj.optObject("local")
    return when {
        local?.hasNonNull("appType") == true ->
            GitHubTrackedLocalAppType.fromStorageId(local.optString("appType"))

        local?.hasNonNull("isSystemApp") == true ->
            GitHubTrackedLocalAppType.fromSystemFlag(local.optBoolean("isSystemApp", false))

        settings?.hasNonNull("localAppType") == true ->
            GitHubTrackedLocalAppType.fromStorageId(settings.optString("localAppType"))

        settings?.hasNonNull("isSystemApp") == true ->
            GitHubTrackedLocalAppType.fromSystemFlag(settings.optBoolean("isSystemApp", false))

        obj.hasNonNull("localAppType") ->
            GitHubTrackedLocalAppType.fromStorageId(obj.optString("localAppType"))

        obj.hasNonNull("appType") ->
            GitHubTrackedLocalAppType.fromStorageId(obj.optString("appType"))

        obj.hasNonNull("isSystemApp") ->
            GitHubTrackedLocalAppType.fromSystemFlag(obj.optBoolean("isSystemApp", false))

        else -> GitHubTrackedLocalAppType.Unknown
    }
}

fun parsePreciseApkVersionMode(obj: JsonObject): GitHubTrackedPreciseApkVersionMode {
    val settings = obj.optObject("settings")
    if (settings?.hasNonNull("preciseApkVersionMode") == true) {
        return GitHubTrackedPreciseApkVersionMode.fromStorageId(
            settings.optString("preciseApkVersionMode")
        )
    }
    if (obj.hasNonNull("preciseApkVersionMode")) {
        return GitHubTrackedPreciseApkVersionMode.fromStorageId(
            obj.optString("preciseApkVersionMode")
        )
    }
    return when {
        settings?.hasNonNull("preciseApkVersionEnabled") == true ->
            GitHubTrackedPreciseApkVersionMode.fromLegacyEnabled(
                settings.optBoolean("preciseApkVersionEnabled", false)
            )
        obj.hasNonNull("preciseApkVersionEnabled") ->
            GitHubTrackedPreciseApkVersionMode.fromLegacyEnabled(
                obj.optBoolean("preciseApkVersionEnabled", false)
            )
        settings?.hasNonNull("preciseApkVersion") == true ->
            GitHubTrackedPreciseApkVersionMode.fromLegacyEnabled(
                settings.optBoolean("preciseApkVersion", false)
            )
        obj.hasNonNull("preciseApkVersion") ->
            GitHubTrackedPreciseApkVersionMode.fromLegacyEnabled(
                obj.optBoolean("preciseApkVersion", false)
            )
        else -> GitHubTrackedPreciseApkVersionMode.FollowGlobal
    }
}

fun parseUpdateIntervalMode(obj: JsonObject): GitHubTrackedUpdateIntervalMode {
    val settings = obj.optObject("settings")
    return when {
        settings?.hasNonNull("updateIntervalMode") == true ->
            GitHubTrackedUpdateIntervalMode.fromStorageId(
                settings.optString("updateIntervalMode")
            )

        obj.hasNonNull("updateIntervalMode") ->
            GitHubTrackedUpdateIntervalMode.fromStorageId(
                obj.optString("updateIntervalMode")
            )

        else -> GitHubTrackedUpdateIntervalMode.FollowGlobal
    }
}

fun parseActionsUpdateIntervalMode(obj: JsonObject): GitHubTrackedActionsUpdateIntervalMode {
    val settings = obj.optObject("settings")
    return when {
        settings?.hasNonNull("actionsUpdateIntervalMode") == true ->
            GitHubTrackedActionsUpdateIntervalMode.fromStorageId(
                settings.optString("actionsUpdateIntervalMode")
            )

        obj.hasNonNull("actionsUpdateIntervalMode") ->
            GitHubTrackedActionsUpdateIntervalMode.fromStorageId(
                obj.optString("actionsUpdateIntervalMode")
            )

        else -> GitHubTrackedActionsUpdateIntervalMode.FollowGlobal
    }
}

fun trackedItemToJson(item: GitHubTrackedApp): JsonObject {
    val normalizedItem = item.withSourceModeConstraints()
    val settings = buildJsonObject {
        put("sourceMode", normalizedItem.sourceMode.storageId)
        put("preferPreRelease", normalizedItem.preferPreRelease)
        put(
            "alwaysShowLatestReleaseDownloadButton",
            normalizedItem.alwaysShowLatestReleaseDownloadButton
        )
        put("checkActionsUpdates", normalizedItem.checkActionsUpdates)
        put("updateIntervalMode", normalizedItem.updateIntervalMode.storageId)
        put("actionsUpdateIntervalMode", normalizedItem.actionsUpdateIntervalMode.storageId)
        put("preciseApkVersionMode", normalizedItem.preciseApkVersionMode.storageId)
        put("localAppType", normalizedItem.localAppType.storageId)
    }
    val source = buildJsonObject {
        put("mode", normalizedItem.sourceMode.storageId)
        put("url", normalizedItem.repoUrl)
        put("owner", normalizedItem.owner)
        put("repo", normalizedItem.repo)
    }
    val repository = buildJsonObject {
        put("archived", normalizedItem.repositoryArchived)
        put("fork", normalizedItem.repositoryFork)
    }
    val local = buildJsonObject {
        put("appType", normalizedItem.localAppType.storageId)
        put("isSystemApp", normalizedItem.localAppType == GitHubTrackedLocalAppType.System)
    }
    return buildJsonObject {
        put("sourceMode", normalizedItem.sourceMode.storageId)
        put("repoUrl", normalizedItem.repoUrl)
        put("packageName", normalizedItem.packageName)
        put("appLabel", normalizedItem.appLabel)
        put("source", source)
        put("settings", settings)
        put("local", local)
        put("preferPreRelease", normalizedItem.preferPreRelease)
        put("checkPreRelease", normalizedItem.preferPreRelease)
        put(
            "alwaysShowLatestReleaseDownloadButton",
            normalizedItem.alwaysShowLatestReleaseDownloadButton
        )
        put(
            "alwaysShowLatestReleaseDownload",
            normalizedItem.alwaysShowLatestReleaseDownloadButton
        )
        put("checkActionsUpdates", normalizedItem.checkActionsUpdates)
        put("updateIntervalMode", normalizedItem.updateIntervalMode.storageId)
        put("actionsUpdateIntervalMode", normalizedItem.actionsUpdateIntervalMode.storageId)
        put("preciseApkVersionMode", normalizedItem.preciseApkVersionMode.storageId)
        put("localAppType", normalizedItem.localAppType.storageId)
        put("repository", repository)
        put("repositoryArchived", normalizedItem.repositoryArchived)
        put("repositoryFork", normalizedItem.repositoryFork)
        if (normalizedItem.sourceMode != GitHubTrackedSourceMode.DirectApk) {
            put("owner", normalizedItem.owner)
            put("repo", normalizedItem.repo)
        }
    }
}

fun GitHubTrackedItemsOptionCounts.toJson(): JsonObject {
    return buildJsonObject {
        put("preferPreRelease", preferPreReleaseCount)
        put("latestReleaseDownload", latestReleaseDownloadCount)
        put("actionsUpdate", actionsUpdateCount)
        put("updateIntervalOverride", updateIntervalOverrideCount)
        put("preciseApkVersionOverride", preciseApkVersionOverrideCount)
        put("archivedOrFork", archivedOrForkCount)
    }
}

fun GitHubTrackedItemsSourceCounts.toJson(): JsonObject {
    return buildJsonObject {
        put("githubRepository", githubRepositoryCount)
        put("gitRepository", gitRepositoryCount)
        put("directApk", directApkCount)
    }
}

fun parseRemoteApkVersionInfo(obj: JsonObject?): GitHubRemoteApkVersionInfo? {
    obj ?: return null
    val info = GitHubRemoteApkVersionInfo(
        releaseName = obj.optString("releaseName").trim(),
        releaseTag = obj.optString("releaseTag").trim(),
        releaseUrl = obj.optString("releaseUrl").trim(),
        assetName = obj.optString("assetName").trim(),
        packageName = obj.optString("packageName").trim(),
        versionName = obj.optString("versionName").trim(),
        versionCode = obj.optString("versionCode").trim(),
        fetchSource = obj.optString("fetchSource").trim(),
        releaseNotes = obj.optString("releaseNotes").trim()
    )
    return info.takeIf { it.hasVersion() || it.releaseLabel().isNotBlank() }
}

fun parseDirectApkRemoteHealth(raw: String): GitHubDirectApkRemoteHealth {
    return GitHubDirectApkRemoteHealth.entries.firstOrNull { health ->
        health.name.equals(raw.trim(), ignoreCase = true)
    } ?: GitHubDirectApkRemoteHealth.Unknown
}

fun remoteApkVersionInfoToJson(info: GitHubRemoteApkVersionInfo?): JsonObject? {
    info ?: return null
    return buildJsonObject {
        put("releaseName", info.releaseName)
        put("releaseTag", info.releaseTag)
        put("releaseUrl", info.releaseUrl)
        put("assetName", info.assetName)
        put("packageName", info.packageName)
        put("versionName", info.versionName)
        put("versionCode", info.versionCode)
        put("fetchSource", info.fetchSource)
        put("releaseNotes", info.releaseNotes)
    }
}
