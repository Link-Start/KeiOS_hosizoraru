package os.kei.feature.github.data.local

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import os.kei.core.json.jsonArrayOrNull
import os.kei.core.json.jsonObjectOrNull
import os.kei.core.json.jsonPrimitiveOrNull
import os.kei.core.json.optArray
import os.kei.core.json.optBoolean
import os.kei.core.json.optLong
import os.kei.core.json.optObject
import os.kei.core.json.optString
import os.kei.feature.github.model.GitHubProfileField
import os.kei.feature.github.model.GitHubRepositoryActionsProfile
import os.kei.feature.github.model.GitHubRepositoryActivityProfile
import os.kei.feature.github.model.GitHubRepositoryCommunityProfile
import os.kei.feature.github.model.GitHubRepositoryDistributionProfile
import os.kei.feature.github.model.GitHubRepositoryForkSyncProfile
import os.kei.feature.github.model.GitHubRepositoryIdentityProfile
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryLocalFitProfile
import os.kei.feature.github.model.GitHubRepositoryProfileAvailabilityStatus
import os.kei.feature.github.model.GitHubRepositoryProfileCapability
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryProfileSourceState
import os.kei.feature.github.model.GitHubRepositoryReleasesProfile
import os.kei.feature.github.model.GitHubRepositorySecurityProfile
import os.kei.feature.github.model.GitHubRepositoryTrafficProfile
import os.kei.feature.github.model.GitHubRepositoryUpstreamProfile

fun GitHubRepositoryProfileSnapshot.toCacheJson(): JsonObject {
    return buildJsonObject {
        put("owner", owner)
        put("repo", repo)
        put("sourceConfigSignature", sourceConfigSignature)
        put("fetchedAtMillis", fetchedAtMillis)
        put("purpose", purpose.name)
        put(
            "capabilities",
            buildJsonArray {
                capabilities.sortedBy { it.name }.forEach { capability ->
                    add(JsonPrimitive(capability.name))
                }
            }
        )
        put("identity", identity.toCacheJson())
        put("lifecycle", lifecycle.toCacheJson())
        put("activity", activity.toCacheJson())
        put("releases", releases.toCacheJson())
        put("distribution", distribution.toCacheJson())
        put("actions", actions.toCacheJson())
        put("community", community.toCacheJson())
        put("traffic", traffic.toCacheJson())
        put("forkSync", forkSync.toCacheJson())
        put("security", security.toCacheJson())
        put("localFit", localFit.toCacheJson())
        put(
            "sourceAvailability",
            buildJsonArray {
                sourceAvailability.forEach { state ->
                    add(
                        buildJsonObject {
                            put("source", state.source.name)
                            put("status", state.status.name)
                            put("fetchedAtMillis", state.fetchedAtMillis)
                            put("message", state.message)
                            put("elapsedMs", state.elapsedMs)
                            put("fromCache", state.fromCache)
                            put("required", state.required)
                        }
                    )
                }
            }
        )
    }
}

fun parseGitHubRepositoryProfileSnapshot(
    obj: JsonObject?
): GitHubRepositoryProfileSnapshot? {
    obj ?: return null
    val owner = obj.optString("owner").trim()
    val repo = obj.optString("repo").trim()
    val signature = obj.optString("sourceConfigSignature").trim()
    val fetchedAtMillis = obj.optLong("fetchedAtMillis", -1L)
    if (owner.isBlank() || repo.isBlank() || signature.isBlank() || fetchedAtMillis <= 0L) {
        return null
    }
    val explicitCapabilities = parseCapabilities(obj.optArray("capabilities"))
    val snapshot = GitHubRepositoryProfileSnapshot(
        owner = owner,
        repo = repo,
        sourceConfigSignature = signature,
        fetchedAtMillis = fetchedAtMillis,
        purpose = enumValueOrNull<GitHubRepositoryProfilePurpose>(obj.optString("purpose"))
            ?: GitHubRepositoryProfilePurpose.VersionCheckFast,
        capabilities = explicitCapabilities,
        identity = parseIdentity(obj.optObject("identity")),
        lifecycle = parseLifecycle(obj.optObject("lifecycle")),
        activity = parseActivity(obj.optObject("activity")),
        releases = parseReleases(obj.optObject("releases")),
        distribution = parseDistribution(obj.optObject("distribution")),
        actions = parseActions(obj.optObject("actions")),
        community = parseCommunity(obj.optObject("community")),
        traffic = parseTraffic(obj.optObject("traffic")),
        forkSync = parseForkSync(obj.optObject("forkSync")),
        security = parseSecurity(obj.optObject("security")),
        localFit = parseLocalFit(obj.optObject("localFit")),
        sourceAvailability = parseSourceAvailability(obj.optArray("sourceAvailability"))
    )
    return if (explicitCapabilities.isEmpty()) {
        snapshot.copy(capabilities = inferCapabilities(snapshot))
    } else {
        snapshot
    }
}

private fun GitHubRepositoryIdentityProfile.toCacheJson(): JsonObject =
    buildJsonObject {
        putField("owner", owner)
        putField("name", name)
        putField("fullName", fullName)
        putField("htmlUrl", htmlUrl)
        putField("ownerAvatarUrl", ownerAvatarUrl)
        putField("defaultBranch", defaultBranch)
        putField("ownerType", ownerType)
        putField("visibility", visibility)
        putField("privateRepository", privateRepository)
        putField("topics", topics)
    }

private fun GitHubRepositoryLifecycleProfile.toCacheJson(): JsonObject =
    buildJsonObject {
        putField("archived", archived)
        putField("disabled", disabled)
        putField("fork", fork)
        putField("mirrorUrl", mirrorUrl)
        upstream?.toCacheJson()?.let { put("upstream", it) }
    }

private fun GitHubRepositoryUpstreamProfile.toCacheJson(): JsonObject =
    buildJsonObject {
        putField("fullName", fullName)
        putField("htmlUrl", htmlUrl)
        putField("archived", archived)
        putField("disabled", disabled)
        putField("pushedAtMillis", pushedAtMillis)
        putField("defaultBranch", defaultBranch)
    }

private fun GitHubRepositoryActivityProfile.toCacheJson(): JsonObject =
    buildJsonObject {
        putField("createdAtMillis", createdAtMillis)
        putField("updatedAtMillis", updatedAtMillis)
        putField("pushedAtMillis", pushedAtMillis)
        putField("stargazersCount", stargazersCount)
        putField("forksCount", forksCount)
        putField("watchersCount", watchersCount)
        putField("subscribersCount", subscribersCount)
        putField("openIssuesCount", openIssuesCount)
        putField("sizeKb", sizeKb)
    }

private fun GitHubRepositoryReleasesProfile.toCacheJson(): JsonObject =
    buildJsonObject {
        putField("releaseCount", releaseCount)
        putField("hasStableRelease", hasStableRelease)
        putField("latestStableTag", latestStableTag)
        putField("latestStableName", latestStableName)
        putField("latestStablePublishedAtMillis", latestStablePublishedAtMillis)
        putField("latestStableAuthor", latestStableAuthor)
        putField("latestPreReleaseTag", latestPreReleaseTag)
        putField("latestPreReleaseName", latestPreReleaseName)
        putField("latestPreReleasePublishedAtMillis", latestPreReleasePublishedAtMillis)
        putField("latestPreReleaseAuthor", latestPreReleaseAuthor)
    }

private fun GitHubRepositoryDistributionProfile.toCacheJson(): JsonObject =
    buildJsonObject {
        putField("latestAssetCount", latestAssetCount)
        putField("apkLikeAssetCount", apkLikeAssetCount)
        putField("androidBundleAssetCount", androidBundleAssetCount)
        putField("totalDownloadCount", totalDownloadCount)
        putField("assetDigestCount", assetDigestCount)
        putField("hasInstallableAndroidAsset", hasInstallableAndroidAsset)
        putField("latestStableApkPackageName", latestStableApkPackageName)
        putField("latestStableApkVersionName", latestStableApkVersionName)
        putField("latestStableApkVersionCode", latestStableApkVersionCode)
    }

private fun GitHubRepositoryActionsProfile.toCacheJson(): JsonObject =
    buildJsonObject {
        putField("workflowRunCount", workflowRunCount)
        putField("successfulRunCount", successfulRunCount)
        putField("failedRunCount", failedRunCount)
        putField("latestRunStatus", latestRunStatus)
        putField("latestRunConclusion", latestRunConclusion)
        putField("latestRunUpdatedAtMillis", latestRunUpdatedAtMillis)
        putField("artifactCount", artifactCount)
        putField("nonExpiredArtifactCount", nonExpiredArtifactCount)
        putField("androidArtifactCount", androidArtifactCount)
    }

private fun GitHubRepositoryCommunityProfile.toCacheJson(): JsonObject =
    buildJsonObject {
        putField("healthPercentage", healthPercentage)
        putField("hasReadme", hasReadme)
        putField("hasLicense", hasLicense)
        putField("licenseName", licenseName)
        putField("licenseSpdxId", licenseSpdxId)
        putField("hasContributing", hasContributing)
        putField("hasCodeOfConduct", hasCodeOfConduct)
        putField("hasIssueTemplate", hasIssueTemplate)
        putField("hasPullRequestTemplate", hasPullRequestTemplate)
    }

private fun GitHubRepositoryTrafficProfile.toCacheJson(): JsonObject =
    buildJsonObject {
        putField("viewCount", viewCount)
        putField("viewUniques", viewUniques)
        putField("cloneCount", cloneCount)
        putField("cloneUniques", cloneUniques)
        putField("latestViewBucketAtMillis", latestViewBucketAtMillis)
        putField("latestCloneBucketAtMillis", latestCloneBucketAtMillis)
    }

private fun GitHubRepositoryForkSyncProfile.toCacheJson(): JsonObject =
    buildJsonObject {
        putField("baseFullName", baseFullName)
        putField("headFullName", headFullName)
        putField("aheadBy", aheadBy)
        putField("behindBy", behindBy)
        putField("status", status)
        putField("totalCommits", totalCommits)
        putField("comparedAtMillis", comparedAtMillis)
    }

private fun GitHubRepositorySecurityProfile.toCacheJson(): JsonObject =
    buildJsonObject {
        putField("dependabotAlertsAvailable", dependabotAlertsAvailable)
        putField("openDependabotAlertsCount", openDependabotAlertsCount)
        putField("codeScanningAvailable", codeScanningAvailable)
        putField("openCodeScanningAlertsCount", openCodeScanningAlertsCount)
        putField("secretScanningAvailable", secretScanningAvailable)
    }

private fun GitHubRepositoryLocalFitProfile.toCacheJson(): JsonObject =
    buildJsonObject {
        putField("localPackageName", localPackageName)
        putField("remotePackageName", remotePackageName)
        putField("packageNameMatched", packageNameMatched)
        putField("localVersionName", localVersionName)
        putField("remoteVersionName", remoteVersionName)
        putField("localVersionCode", localVersionCode)
        putField("remoteVersionCode", remoteVersionCode)
    }

private fun parseIdentity(obj: JsonObject?): GitHubRepositoryIdentityProfile {
    obj ?: return GitHubRepositoryIdentityProfile()
    return GitHubRepositoryIdentityProfile(
        owner = obj.stringField("owner"),
        name = obj.stringField("name"),
        fullName = obj.stringField("fullName"),
        htmlUrl = obj.stringField("htmlUrl"),
        ownerAvatarUrl = obj.stringField("ownerAvatarUrl"),
        defaultBranch = obj.stringField("defaultBranch"),
        ownerType = obj.stringField("ownerType"),
        visibility = obj.stringField("visibility"),
        privateRepository = obj.booleanField("privateRepository"),
        topics = obj.stringListField("topics")
    )
}

private fun parseLifecycle(obj: JsonObject?): GitHubRepositoryLifecycleProfile {
    obj ?: return GitHubRepositoryLifecycleProfile()
    return GitHubRepositoryLifecycleProfile(
        archived = obj.booleanField("archived"),
        disabled = obj.booleanField("disabled"),
        fork = obj.booleanField("fork"),
        mirrorUrl = obj.stringField("mirrorUrl"),
        upstream = parseUpstream(obj.optObject("upstream"))
    )
}

private fun parseUpstream(obj: JsonObject?): GitHubRepositoryUpstreamProfile? {
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

private fun parseActivity(obj: JsonObject?): GitHubRepositoryActivityProfile {
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

private fun parseReleases(obj: JsonObject?): GitHubRepositoryReleasesProfile {
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

private fun parseDistribution(obj: JsonObject?): GitHubRepositoryDistributionProfile {
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

private fun parseActions(obj: JsonObject?): GitHubRepositoryActionsProfile {
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

private fun parseCommunity(obj: JsonObject?): GitHubRepositoryCommunityProfile {
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

private fun parseTraffic(obj: JsonObject?): GitHubRepositoryTrafficProfile {
    obj ?: return GitHubRepositoryTrafficProfile()
    return GitHubRepositoryTrafficProfile(
        viewCount = obj.intField("viewCount"),
        viewUniques = obj.intField("viewUniques"),
        cloneCount = obj.intField("cloneCount"),
        cloneUniques = obj.intField("cloneUniques"),
        latestViewBucketAtMillis = obj.longField("latestViewBucketAtMillis"),
        latestCloneBucketAtMillis = obj.longField("latestCloneBucketAtMillis")
    )
}

private fun parseForkSync(obj: JsonObject?): GitHubRepositoryForkSyncProfile {
    obj ?: return GitHubRepositoryForkSyncProfile()
    return GitHubRepositoryForkSyncProfile(
        baseFullName = obj.stringField("baseFullName"),
        headFullName = obj.stringField("headFullName"),
        aheadBy = obj.intField("aheadBy"),
        behindBy = obj.intField("behindBy"),
        status = obj.stringField("status"),
        totalCommits = obj.intField("totalCommits"),
        comparedAtMillis = obj.longField("comparedAtMillis")
    )
}

private fun parseSecurity(obj: JsonObject?): GitHubRepositorySecurityProfile {
    obj ?: return GitHubRepositorySecurityProfile()
    return GitHubRepositorySecurityProfile(
        dependabotAlertsAvailable = obj.booleanField("dependabotAlertsAvailable"),
        openDependabotAlertsCount = obj.intField("openDependabotAlertsCount"),
        codeScanningAvailable = obj.booleanField("codeScanningAvailable"),
        openCodeScanningAlertsCount = obj.intField("openCodeScanningAlertsCount"),
        secretScanningAvailable = obj.booleanField("secretScanningAvailable")
    )
}

private fun parseLocalFit(obj: JsonObject?): GitHubRepositoryLocalFitProfile {
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

private fun parseSourceAvailability(array: JsonArray?): List<GitHubRepositoryProfileSourceState> {
    array ?: return emptyList()
    return buildList {
        for (element in array) {
            val obj = element.jsonObjectOrNull() ?: continue
            val source =
                enumValueOrNull<GitHubRepositoryProfileSource>(obj.optString("source")) ?: continue
            add(
                GitHubRepositoryProfileSourceState(
                    source = source,
                    status = enumValueOrNull<GitHubRepositoryProfileAvailabilityStatus>(
                        obj.optString("status")
                    ) ?: GitHubRepositoryProfileAvailabilityStatus.Failed,
                    fetchedAtMillis = obj.optLong("fetchedAtMillis", -1L),
                    message = obj.optString("message").trim(),
                    elapsedMs = obj.optLong("elapsedMs", 0L),
                    fromCache = true,
                    required = obj.optBoolean("required", false)
                )
            )
        }
    }
}

private fun parseCapabilities(
    array: JsonArray?
): Set<GitHubRepositoryProfileCapability> {
    array ?: return emptySet()
    return buildSet {
        for (element in array) {
            enumValueOrNull<GitHubRepositoryProfileCapability>(
                element.jsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            )
                ?.let(::add)
        }
    }
}

private fun inferCapabilities(
    snapshot: GitHubRepositoryProfileSnapshot
): Set<GitHubRepositoryProfileCapability> {
    return buildSet {
        add(GitHubRepositoryProfileCapability.RepositoryCore)
        if (
            snapshot.releases.releaseCount != null ||
            snapshot.releases.latestStableTag != null ||
            snapshot.releases.latestPreReleaseTag != null
        ) {
            add(GitHubRepositoryProfileCapability.ReleaseSignals)
        }
        if (
            snapshot.distribution.latestAssetCount != null ||
            snapshot.distribution.apkLikeAssetCount != null ||
            snapshot.distribution.latestStableApkPackageName != null
        ) {
            add(GitHubRepositoryProfileCapability.Distribution)
        }
        if (
            snapshot.actions.workflowRunCount != null ||
            snapshot.actions.latestRunStatus != null ||
            snapshot.actions.artifactCount != null
        ) {
            add(GitHubRepositoryProfileCapability.Actions)
        }
        if (
            snapshot.community.healthPercentage != null ||
            snapshot.community.hasReadme != null ||
            snapshot.community.hasLicense != null
        ) {
            add(GitHubRepositoryProfileCapability.Community)
        }
        if (
            snapshot.identity.topics?.source == GitHubRepositoryProfileSource.HtmlRepositoryPage ||
            snapshot.lifecycle.archived?.source == GitHubRepositoryProfileSource.HtmlRepositoryPage ||
            snapshot.sourceAvailability.any {
                it.source == GitHubRepositoryProfileSource.HtmlRepositoryPage
            }
        ) {
            add(GitHubRepositoryProfileCapability.HtmlRepository)
        }
        if (
            snapshot.traffic.viewCount != null ||
            snapshot.traffic.cloneCount != null ||
            snapshot.sourceAvailability.any {
                it.source == GitHubRepositoryProfileSource.TrafficViewsApi ||
                        it.source == GitHubRepositoryProfileSource.TrafficClonesApi
            }
        ) {
            add(GitHubRepositoryProfileCapability.Traffic)
        }
        if (
            snapshot.forkSync.behindBy != null ||
            snapshot.forkSync.aheadBy != null ||
            snapshot.sourceAvailability.any {
                it.source == GitHubRepositoryProfileSource.ForkCompareApi
            }
        ) {
            add(GitHubRepositoryProfileCapability.ForkSync)
        }
        if (
            snapshot.security.dependabotAlertsAvailable != null ||
            snapshot.security.codeScanningAvailable != null ||
            snapshot.sourceAvailability.any {
                it.source == GitHubRepositoryProfileSource.DependabotAlertsApi ||
                        it.source == GitHubRepositoryProfileSource.CodeScanningAlertsApi
            }
        ) {
            add(GitHubRepositoryProfileCapability.Security)
        }
        if (
            snapshot.localFit.localPackageName != null ||
            snapshot.localFit.remotePackageName != null ||
            snapshot.distribution.latestStableApkPackageName != null
        ) {
            add(GitHubRepositoryProfileCapability.LocalFit)
        }
    }
}

private fun JsonObjectBuilder.putField(
    key: String,
    field: GitHubProfileField<*>?
): JsonObjectBuilder {
    field ?: return this
    put(
        key,
        buildJsonObject {
            put("value", profileValueToJsonElement(field.value))
            put("source", field.source.name)
            put("fetchedAtMillis", field.fetchedAtMillis)
            put("confidence", field.confidence.name)
        }
    )
    return this
}

private fun profileValueToJsonElement(raw: Any?): JsonElement {
    return when (raw) {
        null -> JsonNull
        is String -> JsonPrimitive(raw)
        is Number -> JsonPrimitive(raw)
        is Boolean -> JsonPrimitive(raw)
        is List<*> -> buildJsonArray {
            raw.forEach { value ->
                add(profileValueToJsonElement(value))
            }
        }
        else -> JsonPrimitive(raw.toString())
    }
}

private fun JsonObject.stringField(key: String): GitHubProfileField<String>? {
    return parseField(key) { value ->
        value.jsonPrimitiveOrNull()
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}

private fun JsonObject.intField(key: String): GitHubProfileField<Int>? {
    return parseField(key) { value ->
        val primitive = value.jsonPrimitiveOrNull() ?: return@parseField null
        primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull()
    }
}

private fun JsonObject.longField(key: String): GitHubProfileField<Long>? {
    return parseField(key) { value ->
        val primitive = value.jsonPrimitiveOrNull() ?: return@parseField null
        primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull()
    }
}

private fun JsonObject.booleanField(key: String): GitHubProfileField<Boolean>? {
    return parseField(key) { value ->
        val primitive = value.jsonPrimitiveOrNull() ?: return@parseField null
        primitive.booleanOrNull ?: primitive.contentOrNull?.toBooleanStrictOrNull()
    }
}

private fun JsonObject.stringListField(key: String): GitHubProfileField<List<String>>? {
    return parseField(key) { value ->
        val array = value.jsonArrayOrNull() ?: return@parseField null
        array.mapNotNull { element ->
            element.jsonPrimitiveOrNull()
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }
}

private fun <T> JsonObject.parseField(
    key: String,
    valueReader: (JsonElement) -> T?
): GitHubProfileField<T>? {
    val field = optObject(key) ?: return null
    val rawValue = field["value"] ?: return null
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
