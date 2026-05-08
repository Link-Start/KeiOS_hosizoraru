package os.kei.feature.github.data.local

import org.json.JSONArray
import org.json.JSONObject
import os.kei.feature.github.model.GitHubProfileField
import os.kei.feature.github.model.GitHubRepositoryActionsProfile
import os.kei.feature.github.model.GitHubRepositoryActivityProfile
import os.kei.feature.github.model.GitHubRepositoryCommunityProfile
import os.kei.feature.github.model.GitHubRepositoryDistributionProfile
import os.kei.feature.github.model.GitHubRepositoryIdentityProfile
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryLocalFitProfile
import os.kei.feature.github.model.GitHubRepositoryProfileAvailabilityStatus
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryProfileSourceState
import os.kei.feature.github.model.GitHubRepositoryReleasesProfile
import os.kei.feature.github.model.GitHubRepositorySecurityProfile
import os.kei.feature.github.model.GitHubRepositoryUpstreamProfile

internal fun GitHubRepositoryProfileSnapshot.toCacheJson(): JSONObject {
    return JSONObject()
        .put("owner", owner)
        .put("repo", repo)
        .put("sourceConfigSignature", sourceConfigSignature)
        .put("fetchedAtMillis", fetchedAtMillis)
        .put("identity", identity.toCacheJson())
        .put("lifecycle", lifecycle.toCacheJson())
        .put("activity", activity.toCacheJson())
        .put("releases", releases.toCacheJson())
        .put("distribution", distribution.toCacheJson())
        .put("actions", actions.toCacheJson())
        .put("community", community.toCacheJson())
        .put("security", security.toCacheJson())
        .put("localFit", localFit.toCacheJson())
        .put(
            "sourceAvailability",
            JSONArray().apply {
                sourceAvailability.forEach { state ->
                    put(
                        JSONObject()
                            .put("source", state.source.name)
                            .put("status", state.status.name)
                            .put("fetchedAtMillis", state.fetchedAtMillis)
                            .put("message", state.message)
                    )
                }
            }
        )
}

internal fun parseGitHubRepositoryProfileSnapshot(
    obj: JSONObject?
): GitHubRepositoryProfileSnapshot? {
    obj ?: return null
    val owner = obj.optString("owner").trim()
    val repo = obj.optString("repo").trim()
    val signature = obj.optString("sourceConfigSignature").trim()
    val fetchedAtMillis = obj.optLong("fetchedAtMillis", -1L)
    if (owner.isBlank() || repo.isBlank() || signature.isBlank() || fetchedAtMillis <= 0L) {
        return null
    }
    return GitHubRepositoryProfileSnapshot(
        owner = owner,
        repo = repo,
        sourceConfigSignature = signature,
        fetchedAtMillis = fetchedAtMillis,
        identity = parseIdentity(obj.optJSONObject("identity")),
        lifecycle = parseLifecycle(obj.optJSONObject("lifecycle")),
        activity = parseActivity(obj.optJSONObject("activity")),
        releases = parseReleases(obj.optJSONObject("releases")),
        distribution = parseDistribution(obj.optJSONObject("distribution")),
        actions = parseActions(obj.optJSONObject("actions")),
        community = parseCommunity(obj.optJSONObject("community")),
        security = parseSecurity(obj.optJSONObject("security")),
        localFit = parseLocalFit(obj.optJSONObject("localFit")),
        sourceAvailability = parseSourceAvailability(obj.optJSONArray("sourceAvailability"))
    )
}

private fun GitHubRepositoryIdentityProfile.toCacheJson(): JSONObject =
    JSONObject()
        .putField("owner", owner)
        .putField("name", name)
        .putField("fullName", fullName)
        .putField("htmlUrl", htmlUrl)
        .putField("defaultBranch", defaultBranch)
        .putField("ownerType", ownerType)
        .putField("visibility", visibility)
        .putField("privateRepository", privateRepository)
        .putField("topics", topics)

private fun GitHubRepositoryLifecycleProfile.toCacheJson(): JSONObject =
    JSONObject()
        .putField("archived", archived)
        .putField("disabled", disabled)
        .putField("fork", fork)
        .putField("mirrorUrl", mirrorUrl)
        .put("upstream", upstream?.toCacheJson())

private fun GitHubRepositoryUpstreamProfile.toCacheJson(): JSONObject =
    JSONObject()
        .putField("fullName", fullName)
        .putField("htmlUrl", htmlUrl)
        .putField("archived", archived)
        .putField("disabled", disabled)
        .putField("pushedAtMillis", pushedAtMillis)
        .putField("defaultBranch", defaultBranch)

private fun GitHubRepositoryActivityProfile.toCacheJson(): JSONObject =
    JSONObject()
        .putField("createdAtMillis", createdAtMillis)
        .putField("updatedAtMillis", updatedAtMillis)
        .putField("pushedAtMillis", pushedAtMillis)
        .putField("stargazersCount", stargazersCount)
        .putField("forksCount", forksCount)
        .putField("watchersCount", watchersCount)
        .putField("subscribersCount", subscribersCount)
        .putField("openIssuesCount", openIssuesCount)
        .putField("sizeKb", sizeKb)

private fun GitHubRepositoryReleasesProfile.toCacheJson(): JSONObject =
    JSONObject()
        .putField("releaseCount", releaseCount)
        .putField("hasStableRelease", hasStableRelease)
        .putField("latestStableTag", latestStableTag)
        .putField("latestStableName", latestStableName)
        .putField("latestStablePublishedAtMillis", latestStablePublishedAtMillis)
        .putField("latestStableAuthor", latestStableAuthor)
        .putField("latestPreReleaseTag", latestPreReleaseTag)
        .putField("latestPreReleaseName", latestPreReleaseName)
        .putField("latestPreReleasePublishedAtMillis", latestPreReleasePublishedAtMillis)
        .putField("latestPreReleaseAuthor", latestPreReleaseAuthor)

private fun GitHubRepositoryDistributionProfile.toCacheJson(): JSONObject =
    JSONObject()
        .putField("latestAssetCount", latestAssetCount)
        .putField("apkLikeAssetCount", apkLikeAssetCount)
        .putField("androidBundleAssetCount", androidBundleAssetCount)
        .putField("totalDownloadCount", totalDownloadCount)
        .putField("assetDigestCount", assetDigestCount)
        .putField("hasInstallableAndroidAsset", hasInstallableAndroidAsset)
        .putField("latestStableApkPackageName", latestStableApkPackageName)
        .putField("latestStableApkVersionName", latestStableApkVersionName)
        .putField("latestStableApkVersionCode", latestStableApkVersionCode)

private fun GitHubRepositoryActionsProfile.toCacheJson(): JSONObject =
    JSONObject()
        .putField("workflowRunCount", workflowRunCount)
        .putField("successfulRunCount", successfulRunCount)
        .putField("failedRunCount", failedRunCount)
        .putField("latestRunStatus", latestRunStatus)
        .putField("latestRunConclusion", latestRunConclusion)
        .putField("latestRunUpdatedAtMillis", latestRunUpdatedAtMillis)
        .putField("artifactCount", artifactCount)
        .putField("nonExpiredArtifactCount", nonExpiredArtifactCount)
        .putField("androidArtifactCount", androidArtifactCount)

private fun GitHubRepositoryCommunityProfile.toCacheJson(): JSONObject =
    JSONObject()
        .putField("healthPercentage", healthPercentage)
        .putField("hasReadme", hasReadme)
        .putField("hasLicense", hasLicense)
        .putField("licenseName", licenseName)
        .putField("licenseSpdxId", licenseSpdxId)
        .putField("hasContributing", hasContributing)
        .putField("hasCodeOfConduct", hasCodeOfConduct)
        .putField("hasIssueTemplate", hasIssueTemplate)
        .putField("hasPullRequestTemplate", hasPullRequestTemplate)

private fun GitHubRepositorySecurityProfile.toCacheJson(): JSONObject =
    JSONObject()
        .putField("dependabotAlertsAvailable", dependabotAlertsAvailable)
        .putField("codeScanningAvailable", codeScanningAvailable)
        .putField("secretScanningAvailable", secretScanningAvailable)

private fun GitHubRepositoryLocalFitProfile.toCacheJson(): JSONObject =
    JSONObject()
        .putField("localPackageName", localPackageName)
        .putField("remotePackageName", remotePackageName)
        .putField("packageNameMatched", packageNameMatched)
        .putField("localVersionName", localVersionName)
        .putField("remoteVersionName", remoteVersionName)
        .putField("localVersionCode", localVersionCode)
        .putField("remoteVersionCode", remoteVersionCode)

private fun parseIdentity(obj: JSONObject?): GitHubRepositoryIdentityProfile {
    obj ?: return GitHubRepositoryIdentityProfile()
    return GitHubRepositoryIdentityProfile(
        owner = obj.stringField("owner"),
        name = obj.stringField("name"),
        fullName = obj.stringField("fullName"),
        htmlUrl = obj.stringField("htmlUrl"),
        defaultBranch = obj.stringField("defaultBranch"),
        ownerType = obj.stringField("ownerType"),
        visibility = obj.stringField("visibility"),
        privateRepository = obj.booleanField("privateRepository"),
        topics = obj.stringListField("topics")
    )
}

private fun parseLifecycle(obj: JSONObject?): GitHubRepositoryLifecycleProfile {
    obj ?: return GitHubRepositoryLifecycleProfile()
    return GitHubRepositoryLifecycleProfile(
        archived = obj.booleanField("archived"),
        disabled = obj.booleanField("disabled"),
        fork = obj.booleanField("fork"),
        mirrorUrl = obj.stringField("mirrorUrl"),
        upstream = parseUpstream(obj.optJSONObject("upstream"))
    )
}

private fun parseUpstream(obj: JSONObject?): GitHubRepositoryUpstreamProfile? {
    obj ?: return null
    val upstream = GitHubRepositoryUpstreamProfile(
        fullName = obj.stringField("fullName"),
        htmlUrl = obj.stringField("htmlUrl"),
        archived = obj.booleanField("archived"),
        disabled = obj.booleanField("disabled"),
        pushedAtMillis = obj.longField("pushedAtMillis"),
        defaultBranch = obj.stringField("defaultBranch")
    )
    return upstream.takeIf {
        it.fullName != null ||
                it.htmlUrl != null ||
                it.archived != null ||
                it.pushedAtMillis != null ||
                it.defaultBranch != null
    }
}

private fun parseActivity(obj: JSONObject?): GitHubRepositoryActivityProfile {
    obj ?: return GitHubRepositoryActivityProfile()
    return GitHubRepositoryActivityProfile(
        createdAtMillis = obj.longField("createdAtMillis"),
        updatedAtMillis = obj.longField("updatedAtMillis"),
        pushedAtMillis = obj.longField("pushedAtMillis"),
        stargazersCount = obj.intField("stargazersCount"),
        forksCount = obj.intField("forksCount"),
        watchersCount = obj.intField("watchersCount"),
        subscribersCount = obj.intField("subscribersCount"),
        openIssuesCount = obj.intField("openIssuesCount"),
        sizeKb = obj.intField("sizeKb")
    )
}

private fun parseReleases(obj: JSONObject?): GitHubRepositoryReleasesProfile {
    obj ?: return GitHubRepositoryReleasesProfile()
    return GitHubRepositoryReleasesProfile(
        releaseCount = obj.intField("releaseCount"),
        hasStableRelease = obj.booleanField("hasStableRelease"),
        latestStableTag = obj.stringField("latestStableTag"),
        latestStableName = obj.stringField("latestStableName"),
        latestStablePublishedAtMillis = obj.longField("latestStablePublishedAtMillis"),
        latestStableAuthor = obj.stringField("latestStableAuthor"),
        latestPreReleaseTag = obj.stringField("latestPreReleaseTag"),
        latestPreReleaseName = obj.stringField("latestPreReleaseName"),
        latestPreReleasePublishedAtMillis = obj.longField("latestPreReleasePublishedAtMillis"),
        latestPreReleaseAuthor = obj.stringField("latestPreReleaseAuthor")
    )
}

private fun parseDistribution(obj: JSONObject?): GitHubRepositoryDistributionProfile {
    obj ?: return GitHubRepositoryDistributionProfile()
    return GitHubRepositoryDistributionProfile(
        latestAssetCount = obj.intField("latestAssetCount"),
        apkLikeAssetCount = obj.intField("apkLikeAssetCount"),
        androidBundleAssetCount = obj.intField("androidBundleAssetCount"),
        totalDownloadCount = obj.intField("totalDownloadCount"),
        assetDigestCount = obj.intField("assetDigestCount"),
        hasInstallableAndroidAsset = obj.booleanField("hasInstallableAndroidAsset"),
        latestStableApkPackageName = obj.stringField("latestStableApkPackageName"),
        latestStableApkVersionName = obj.stringField("latestStableApkVersionName"),
        latestStableApkVersionCode = obj.longField("latestStableApkVersionCode")
    )
}

private fun parseActions(obj: JSONObject?): GitHubRepositoryActionsProfile {
    obj ?: return GitHubRepositoryActionsProfile()
    return GitHubRepositoryActionsProfile(
        workflowRunCount = obj.intField("workflowRunCount"),
        successfulRunCount = obj.intField("successfulRunCount"),
        failedRunCount = obj.intField("failedRunCount"),
        latestRunStatus = obj.stringField("latestRunStatus"),
        latestRunConclusion = obj.stringField("latestRunConclusion"),
        latestRunUpdatedAtMillis = obj.longField("latestRunUpdatedAtMillis"),
        artifactCount = obj.intField("artifactCount"),
        nonExpiredArtifactCount = obj.intField("nonExpiredArtifactCount"),
        androidArtifactCount = obj.intField("androidArtifactCount")
    )
}

private fun parseCommunity(obj: JSONObject?): GitHubRepositoryCommunityProfile {
    obj ?: return GitHubRepositoryCommunityProfile()
    return GitHubRepositoryCommunityProfile(
        healthPercentage = obj.intField("healthPercentage"),
        hasReadme = obj.booleanField("hasReadme"),
        hasLicense = obj.booleanField("hasLicense"),
        licenseName = obj.stringField("licenseName"),
        licenseSpdxId = obj.stringField("licenseSpdxId"),
        hasContributing = obj.booleanField("hasContributing"),
        hasCodeOfConduct = obj.booleanField("hasCodeOfConduct"),
        hasIssueTemplate = obj.booleanField("hasIssueTemplate"),
        hasPullRequestTemplate = obj.booleanField("hasPullRequestTemplate")
    )
}

private fun parseSecurity(obj: JSONObject?): GitHubRepositorySecurityProfile {
    obj ?: return GitHubRepositorySecurityProfile()
    return GitHubRepositorySecurityProfile(
        dependabotAlertsAvailable = obj.booleanField("dependabotAlertsAvailable"),
        codeScanningAvailable = obj.booleanField("codeScanningAvailable"),
        secretScanningAvailable = obj.booleanField("secretScanningAvailable")
    )
}

private fun parseLocalFit(obj: JSONObject?): GitHubRepositoryLocalFitProfile {
    obj ?: return GitHubRepositoryLocalFitProfile()
    return GitHubRepositoryLocalFitProfile(
        localPackageName = obj.stringField("localPackageName"),
        remotePackageName = obj.stringField("remotePackageName"),
        packageNameMatched = obj.booleanField("packageNameMatched"),
        localVersionName = obj.stringField("localVersionName"),
        remoteVersionName = obj.stringField("remoteVersionName"),
        localVersionCode = obj.longField("localVersionCode"),
        remoteVersionCode = obj.longField("remoteVersionCode")
    )
}

private fun parseSourceAvailability(array: JSONArray?): List<GitHubRepositoryProfileSourceState> {
    array ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val source =
                enumValueOrNull<GitHubRepositoryProfileSource>(obj.optString("source")) ?: continue
            add(
                GitHubRepositoryProfileSourceState(
                    source = source,
                    status = enumValueOrNull<GitHubRepositoryProfileAvailabilityStatus>(
                        obj.optString("status")
                    ) ?: GitHubRepositoryProfileAvailabilityStatus.Failed,
                    fetchedAtMillis = obj.optLong("fetchedAtMillis", -1L),
                    message = obj.optString("message").trim()
                )
            )
        }
    }
}

private fun JSONObject.putField(
    key: String,
    field: GitHubProfileField<*>?
): JSONObject {
    field ?: return this
    val value = when (val raw = field.value) {
        is List<*> -> JSONArray(raw)
        else -> raw
    }
    put(
        key,
        JSONObject()
            .put("value", value)
            .put("source", field.source.name)
            .put("fetchedAtMillis", field.fetchedAtMillis)
            .put("confidence", field.confidence.name)
    )
    return this
}

private fun JSONObject.stringField(key: String): GitHubProfileField<String>? {
    return parseField(key) { value ->
        value.toString().trim().takeIf { it.isNotBlank() }
    }
}

private fun JSONObject.intField(key: String): GitHubProfileField<Int>? {
    return parseField(key) { value ->
        when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }
}

private fun JSONObject.longField(key: String): GitHubProfileField<Long>? {
    return parseField(key) { value ->
        when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }
}

private fun JSONObject.booleanField(key: String): GitHubProfileField<Boolean>? {
    return parseField(key) { value ->
        when (value) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull()
            else -> null
        }
    }
}

private fun JSONObject.stringListField(key: String): GitHubProfileField<List<String>>? {
    return parseField(key) { value ->
        val array = value as? JSONArray ?: return@parseField null
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}

private fun <T> JSONObject.parseField(
    key: String,
    valueReader: (Any) -> T?
): GitHubProfileField<T>? {
    val field = optJSONObject(key) ?: return null
    val rawValue = field.opt("value") ?: return null
    val value = valueReader(rawValue) ?: return null
    val source = enumValueOrNull<GitHubRepositoryProfileSource>(field.optString("source"))
        ?: GitHubRepositoryProfileSource.Cache
    val confidence =
        enumValueOrNull<GitHubRepositoryProfileConfidence>(field.optString("confidence"))
            ?: GitHubRepositoryProfileConfidence.Low
    return GitHubProfileField(
        value = value,
        source = source,
        fetchedAtMillis = field.optLong("fetchedAtMillis", -1L),
        confidence = confidence
    )
}

private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String): T? {
    return enumValues<T>().firstOrNull { it.name == raw }
}
