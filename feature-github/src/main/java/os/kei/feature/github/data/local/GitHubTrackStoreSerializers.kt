package os.kei.feature.github.data.local

import org.json.JSONObject
import os.kei.feature.github.model.GitHubDirectApkRemoteHealth
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedLocalAppType
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.buildDirectApkTrackIdentity
import os.kei.feature.github.model.withSourceModeConstraints

fun parseTrackedItem(obj: JSONObject): GitHubTrackedApp? {
    val settings = obj.optJSONObject("settings")
    val source = obj.optJSONObject("source")
    val repository = obj.optJSONObject("repository")
    val repoUrl = obj.optString("repoUrl").trim()
    val sourceMode = parseTrackedSourceMode(obj)
    val directIdentity = if (sourceMode == GitHubTrackedSourceMode.DirectApk) {
        buildDirectApkTrackIdentity(repoUrl)
    } else {
        null
    }
    val owner = obj.optString("owner").trim().ifBlank {
        source?.optString("owner").orEmpty().trim()
    }.ifBlank {
        directIdentity?.owner.orEmpty()
    }
    val repo = obj.optString("repo").trim().ifBlank {
        source?.optString("repo").orEmpty().trim()
    }.ifBlank {
        directIdentity?.repo.orEmpty()
    }
    val packageName = obj.optString("packageName").trim()
    val appLabel = obj.optString("appLabel").trim()
    if (repoUrl.isBlank() || owner.isBlank() || repo.isBlank()) {
        return null
    }
    val fallbackLabel = packageName.ifBlank {
        directIdentity?.displayName ?: "$owner/$repo"
    }
    val preferPreRelease = when {
        settings?.has("preferPreRelease") == true ->
            settings.optBoolean("preferPreRelease", false)

        obj.has("preferPreRelease") -> obj.optBoolean("preferPreRelease", false)
        obj.has("checkPreRelease") -> obj.optBoolean("checkPreRelease", false)
        settings?.has("checkPreRelease") == true ->
            settings.optBoolean("checkPreRelease", false)

        else -> false
    }
    val alwaysShowLatestReleaseDownloadButton = when {
        sourceMode == GitHubTrackedSourceMode.DirectApk -> false
        settings?.has("alwaysShowLatestReleaseDownloadButton") == true ->
            settings.optBoolean("alwaysShowLatestReleaseDownloadButton", false)

        obj.has("alwaysShowLatestReleaseDownloadButton") ->
            obj.optBoolean("alwaysShowLatestReleaseDownloadButton", false)

        settings?.has("alwaysShowLatestReleaseDownload") == true ->
            settings.optBoolean("alwaysShowLatestReleaseDownload", false)

        obj.has("alwaysShowLatestReleaseDownload") ->
            obj.optBoolean("alwaysShowLatestReleaseDownload", false)

        else -> false
    }
    val checkActionsUpdates = when {
        sourceMode == GitHubTrackedSourceMode.DirectApk -> false
        settings?.has("checkActionsUpdates") == true ->
            settings.optBoolean("checkActionsUpdates", false)

        obj.has("checkActionsUpdates") -> obj.optBoolean("checkActionsUpdates", false)
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
        actionsUpdateIntervalMode = parseActionsUpdateIntervalMode(obj),
        preciseApkVersionMode = parsePreciseApkVersionMode(obj),
        repositoryArchived = when {
            repository?.has("archived") == true -> repository.optBoolean("archived", false)
            obj.has("repositoryArchived") -> obj.optBoolean("repositoryArchived", false)
            else -> false
        },
        repositoryFork = when {
            repository?.has("fork") == true -> repository.optBoolean("fork", false)
            obj.has("repositoryFork") -> obj.optBoolean("repositoryFork", false)
            else -> false
        },
        localAppType = parseTrackedLocalAppType(obj)
    ).withSourceModeConstraints()
}

fun parseTrackedSourceMode(obj: JSONObject): GitHubTrackedSourceMode {
    val settings = obj.optJSONObject("settings")
    val source = obj.optJSONObject("source")
    return when {
        source?.has("mode") == true ->
            GitHubTrackedSourceMode.fromStorageId(source.optString("mode"))

        settings?.has("sourceMode") == true ->
            GitHubTrackedSourceMode.fromStorageId(settings.optString("sourceMode"))

        obj.has("sourceMode") ->
            GitHubTrackedSourceMode.fromStorageId(obj.optString("sourceMode"))

        else -> GitHubTrackedSourceMode.GitHubRepository
    }
}

fun parseTrackedLocalAppType(obj: JSONObject): GitHubTrackedLocalAppType {
    val settings = obj.optJSONObject("settings")
    val local = obj.optJSONObject("local")
    return when {
        local?.has("appType") == true ->
            GitHubTrackedLocalAppType.fromStorageId(local.optString("appType"))

        local?.has("isSystemApp") == true ->
            GitHubTrackedLocalAppType.fromSystemFlag(local.optBoolean("isSystemApp", false))

        settings?.has("localAppType") == true ->
            GitHubTrackedLocalAppType.fromStorageId(settings.optString("localAppType"))

        settings?.has("isSystemApp") == true ->
            GitHubTrackedLocalAppType.fromSystemFlag(settings.optBoolean("isSystemApp", false))

        obj.has("localAppType") ->
            GitHubTrackedLocalAppType.fromStorageId(obj.optString("localAppType"))

        obj.has("appType") ->
            GitHubTrackedLocalAppType.fromStorageId(obj.optString("appType"))

        obj.has("isSystemApp") ->
            GitHubTrackedLocalAppType.fromSystemFlag(obj.optBoolean("isSystemApp", false))

        else -> GitHubTrackedLocalAppType.Unknown
    }
}

fun parsePreciseApkVersionMode(obj: JSONObject): GitHubTrackedPreciseApkVersionMode {
    val settings = obj.optJSONObject("settings")
    if (settings?.has("preciseApkVersionMode") == true) {
        return GitHubTrackedPreciseApkVersionMode.fromStorageId(
            settings.optString("preciseApkVersionMode")
        )
    }
    if (obj.has("preciseApkVersionMode")) {
        return GitHubTrackedPreciseApkVersionMode.fromStorageId(
            obj.optString("preciseApkVersionMode")
        )
    }
    return when {
        settings?.has("preciseApkVersionEnabled") == true ->
            GitHubTrackedPreciseApkVersionMode.fromLegacyEnabled(
                settings.optBoolean("preciseApkVersionEnabled", false)
            )
        obj.has("preciseApkVersionEnabled") ->
            GitHubTrackedPreciseApkVersionMode.fromLegacyEnabled(
                obj.optBoolean("preciseApkVersionEnabled", false)
            )
        settings?.has("preciseApkVersion") == true ->
            GitHubTrackedPreciseApkVersionMode.fromLegacyEnabled(
                settings.optBoolean("preciseApkVersion", false)
            )
        obj.has("preciseApkVersion") ->
            GitHubTrackedPreciseApkVersionMode.fromLegacyEnabled(
                obj.optBoolean("preciseApkVersion", false)
            )
        else -> GitHubTrackedPreciseApkVersionMode.FollowGlobal
    }
}

fun parseActionsUpdateIntervalMode(obj: JSONObject): GitHubTrackedActionsUpdateIntervalMode {
    val settings = obj.optJSONObject("settings")
    return when {
        settings?.has("actionsUpdateIntervalMode") == true ->
            GitHubTrackedActionsUpdateIntervalMode.fromStorageId(
                settings.optString("actionsUpdateIntervalMode")
            )

        obj.has("actionsUpdateIntervalMode") ->
            GitHubTrackedActionsUpdateIntervalMode.fromStorageId(
                obj.optString("actionsUpdateIntervalMode")
            )

        else -> GitHubTrackedActionsUpdateIntervalMode.FollowGlobal
    }
}

fun trackedItemToJson(item: GitHubTrackedApp): JSONObject {
    val normalizedItem = item.withSourceModeConstraints()
    val settings = JSONObject()
        .put("sourceMode", normalizedItem.sourceMode.storageId)
        .put("preferPreRelease", normalizedItem.preferPreRelease)
        .put(
            "alwaysShowLatestReleaseDownloadButton",
            normalizedItem.alwaysShowLatestReleaseDownloadButton
        )
        .put("checkActionsUpdates", normalizedItem.checkActionsUpdates)
        .put("actionsUpdateIntervalMode", normalizedItem.actionsUpdateIntervalMode.storageId)
        .put("preciseApkVersionMode", normalizedItem.preciseApkVersionMode.storageId)
        .put("localAppType", normalizedItem.localAppType.storageId)
    val source = JSONObject()
        .put("mode", normalizedItem.sourceMode.storageId)
        .put("url", normalizedItem.repoUrl)
        .put("owner", normalizedItem.owner)
        .put("repo", normalizedItem.repo)
    val repository = JSONObject()
        .put("archived", normalizedItem.repositoryArchived)
        .put("fork", normalizedItem.repositoryFork)
    val local = JSONObject()
        .put("appType", normalizedItem.localAppType.storageId)
        .put("isSystemApp", normalizedItem.localAppType == GitHubTrackedLocalAppType.System)
    val payload = JSONObject()
        .put("sourceMode", normalizedItem.sourceMode.storageId)
        .put("repoUrl", normalizedItem.repoUrl)
        .put("packageName", normalizedItem.packageName)
        .put("appLabel", normalizedItem.appLabel)
        .put("source", source)
        .put("settings", settings)
        .put("local", local)
        .put("preferPreRelease", normalizedItem.preferPreRelease)
        .put("checkPreRelease", normalizedItem.preferPreRelease)
        .put(
            "alwaysShowLatestReleaseDownloadButton",
            normalizedItem.alwaysShowLatestReleaseDownloadButton
        )
        .put(
            "alwaysShowLatestReleaseDownload",
            normalizedItem.alwaysShowLatestReleaseDownloadButton
        )
        .put("checkActionsUpdates", normalizedItem.checkActionsUpdates)
        .put("actionsUpdateIntervalMode", normalizedItem.actionsUpdateIntervalMode.storageId)
        .put("preciseApkVersionMode", normalizedItem.preciseApkVersionMode.storageId)
        .put("localAppType", normalizedItem.localAppType.storageId)
        .put("repository", repository)
        .put("repositoryArchived", normalizedItem.repositoryArchived)
        .put("repositoryFork", normalizedItem.repositoryFork)
    if (normalizedItem.sourceMode == GitHubTrackedSourceMode.GitHubRepository) {
        payload
            .put("owner", normalizedItem.owner)
            .put("repo", normalizedItem.repo)
    }
    return payload
}

fun GitHubTrackedItemsOptionCounts.toJson(): JSONObject {
    return JSONObject()
        .put("preferPreRelease", preferPreReleaseCount)
        .put("latestReleaseDownload", latestReleaseDownloadCount)
        .put("actionsUpdate", actionsUpdateCount)
        .put("preciseApkVersionOverride", preciseApkVersionOverrideCount)
        .put("archivedOrFork", archivedOrForkCount)
}

fun GitHubTrackedItemsSourceCounts.toJson(): JSONObject {
    return JSONObject()
        .put("githubRepository", githubRepositoryCount)
        .put("directApk", directApkCount)
}

fun parseRemoteApkVersionInfo(obj: JSONObject?): GitHubRemoteApkVersionInfo? {
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

fun remoteApkVersionInfoToJson(info: GitHubRemoteApkVersionInfo?): JSONObject? {
    info ?: return null
    return JSONObject()
        .put("releaseName", info.releaseName)
        .put("releaseTag", info.releaseTag)
        .put("releaseUrl", info.releaseUrl)
        .put("assetName", info.assetName)
        .put("packageName", info.packageName)
        .put("versionName", info.versionName)
        .put("versionCode", info.versionCode)
        .put("fetchSource", info.fetchSource)
        .put("releaseNotes", info.releaseNotes)
}
