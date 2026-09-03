package os.kei.feature.github.data.remote.fdroid

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import os.kei.core.json.jsonArrayOrNull
import os.kei.core.json.jsonObjectOrNull
import os.kei.core.json.jsonPrimitiveOrNull
import os.kei.core.json.optArray
import os.kei.core.json.optObject
import os.kei.core.json.optString
import os.kei.core.json.parseJsonObjectOrNull
import os.kei.feature.github.model.FdroidIndexFormat

object FdroidIndexV2Parser {
    fun parseIndex(
        repoUrl: String,
        rawJson: String
    ): Result<FdroidRepositorySnapshot> = runCatching {
        val root = rawJson.parseJsonObjectOrNull()
            ?: error("F-Droid index-v2 JSON is invalid")
        val normalizedRepoUrl = repoUrl.trim().trimEnd('/')
        require(normalizedRepoUrl.isNotBlank()) { "F-Droid repository URL is blank" }
        val repo = root.optObject("repo") ?: JsonObject(emptyMap())
        val packagesObject = root.optObject("packages") ?: JsonObject(emptyMap())
        val packages = packagesObject.entries
            .mapNotNull { entry ->
                val packageName = entry.key.trim()
                val packageObject = entry.value.jsonObjectOrNull() ?: return@mapNotNull null
                val snapshot = packageObject.toPackageSnapshot(
                    repoUrl = normalizedRepoUrl,
                    packageName = packageName
                )
                snapshot.packageName to snapshot
            }
            .sortedBy { it.first }
            .toMap()
        FdroidRepositorySnapshot(
            repoUrl = normalizedRepoUrl,
            format = FdroidIndexFormat.V2,
            repoName = repo.localizedString("name"),
            repoDescription = repo.localizedString("description"),
            timestampMillis = repo.longValue("timestamp")
                ?: root.longValue("timestamp"),
            mirrors = repo.mirrorUrls(),
            packages = packages
        )
    }

    fun parsePackage(
        repoUrl: String,
        packageName: String,
        rawJson: String
    ): Result<FdroidPackageSnapshot> = runCatching {
        val packageObject = rawJson.parseJsonObjectOrNull()
            ?: error("F-Droid package JSON is invalid")
        packageObject.toPackageSnapshot(
            repoUrl = repoUrl.trim().trimEnd('/'),
            packageName = packageName.trim()
        )
    }

    private fun JsonObject.toPackageSnapshot(
        repoUrl: String,
        packageName: String
    ): FdroidPackageSnapshot {
        val metadata = optObject("metadata") ?: JsonObject(emptyMap())
        val versionsObject = optObject("versions") ?: JsonObject(emptyMap())
        val versions = versionsObject.entries
            .mapNotNull { entry ->
                entry.value.jsonObjectOrNull()?.toVersionSnapshot(
                    fallbackApkName = entry.key
                )
            }
            .sortedByDescending { it.versionCode }
        return FdroidPackageSnapshot(
            repoUrl = repoUrl,
            packageName = metadata.optString("packageName").trim()
                .ifBlank { metadata.optString("package_name").trim() }
                .ifBlank { packageName },
            suggestedVersionCode = metadata.longValue("suggestedVersionCode")
                ?: metadata.longValue("suggested_version_code")
                ?: metadata.longValue("currentVersionCode")
                ?: metadata.longValue("CurrentVersionCode"),
            versions = versions,
            appName = metadata.localizedString("name"),
            summary = metadata.localizedString("summary"),
            description = metadata.localizedString("description"),
            license = metadata.optString("license").trim(),
            sourceCodeUrl = metadata.optString("sourceCode").trim()
                .ifBlank { metadata.optString("source_code").trim() },
            webSiteUrl = metadata.optString("webSite").trim()
                .ifBlank { metadata.optString("web_site").trim() }
                .ifBlank { metadata.optString("website").trim() },
            issueTrackerUrl = metadata.optString("issueTracker").trim()
                .ifBlank { metadata.optString("issue_tracker").trim() },
            changelogUrl = metadata.optString("changelog").trim(),
            categories = metadata.stringListValue("categories"),
            antiFeatures = metadata.antiFeatureSnapshots()
        )
    }

    private fun JsonObject.toVersionSnapshot(
        fallbackApkName: String
    ): FdroidVersionSnapshot? {
        val manifest = optObject("manifest") ?: JsonObject(emptyMap())
        val file = optObject("file") ?: JsonObject(emptyMap())
        val usesSdk = manifest.optObject("usesSdk") ?: JsonObject(emptyMap())
        val versionCode = manifest.longValue("versionCode")
            ?: manifest.longValue("version_code")
            ?: longValue("versionCode")
            ?: return null
        val fileName = file.optString("name").trim()
            .ifBlank { optString("apkName").trim() }
            .ifBlank { fallbackApkName }
        val apkName = fileName.substringAfterLast('/').ifBlank { fallbackApkName }
        return FdroidVersionSnapshot(
            versionName = manifest.optString("versionName").trim()
                .ifBlank { manifest.optString("version_name").trim() },
            versionCode = versionCode,
            apkName = apkName,
            apkPath = fileName,
            apkSha256 = file.optString("sha256").trim()
                .ifBlank { file.optString("hash").trim() }
                .ifBlank { optString("sha256").trim() },
            apkSizeBytes = file.longValue("size")
                ?: longValue("size")
                ?: 0L,
            addedAtMillis = longValue("added")
                ?: longValue("addedAt")
                ?: longValue("added_at"),
            minSdk = usesSdk.intValue("minSdkVersion")
                ?: usesSdk.intValue("minSdk")
                ?: manifest.intValue("minSdkVersion"),
            targetSdk = usesSdk.intValue("targetSdkVersion")
                ?: usesSdk.intValue("targetSdk")
                ?: manifest.intValue("targetSdkVersion"),
            nativeAbis = manifest.stringListValue("nativecode")
                .ifEmpty { manifest.stringListValue("nativeCode") },
            signerSha256 = manifest.signerSha256Values(),
            releaseChannels = stringListValue("releaseChannels")
                .ifEmpty { stringListValue("release_channels") },
            whatsNew = localizedString("whatsNew")
                .ifBlank { localizedString("whats_new") },
            antiFeatures = antiFeatureSnapshots()
        )
    }

    private fun JsonObject.signerSha256Values(): List<String> {
        val signer = optObject("signer")
        if (signer != null) {
            return signer.stringListValue("sha256")
                .ifEmpty { signer.stringListValue("sha256Digest") }
        }
        return stringListValue("signer")
            .ifEmpty { stringListValue("signerSha256") }
    }

    private fun JsonObject.mirrorUrls(): List<String> {
        return optArray("mirrors")
            ?.mapNotNull { element ->
                when {
                    element is JsonPrimitive -> element.contentOrNull?.trim()
                    element is JsonObject -> element.optString("url").trim()
                    else -> null
                }?.takeIf { it.isNotBlank() }
            }
            .orEmpty()
    }

    private fun JsonObject.antiFeatureSnapshots(): List<FdroidAntiFeatureSnapshot> {
        val array = optArray("antiFeatures") ?: optArray("anti_features")
        if (array != null) return array.toAntiFeatureSnapshots()
        val obj = optObject("antiFeatures") ?: optObject("anti_features")
        return obj?.entries
            ?.map { entry ->
                val detail = entry.value.jsonObjectOrNull()
                // index-v2 uses this key for two different shapes, and the interesting one was being
                // dropped. The repository's own table describes each anti-feature in general --
                // `{"Tracking": {"name": {...}, "description": {...}}}`. A *version* instead states why
                // that build carries it, as a bare locale map: `{"Tracking": {"en-US": "The app uses
                // Bugsnag."}}`. Read as the general shape, that reason vanished and the pill fell back to
                // showing the raw id.
                val named = detail?.localizedString("name").orEmpty()
                val described = detail?.localizedString("description").orEmpty()
                val reason = when {
                    described.isNotBlank() || named.isNotBlank() -> described
                    // No `name`/`description` wrapper, so the value itself is the localised reason.
                    else -> entry.value.localizedStringValue()
                }
                FdroidAntiFeatureSnapshot(
                    id = entry.key,
                    label = named,
                    description = reason
                )
            }
            .orEmpty()
    }

    private fun JsonArray.toAntiFeatureSnapshots(): List<FdroidAntiFeatureSnapshot> {
        return mapNotNull { element ->
            when {
                element is JsonPrimitive -> element.contentOrNull?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { FdroidAntiFeatureSnapshot(id = it) }

                element is JsonObject -> {
                    val id = element.optString("id").trim()
                        .ifBlank { element.optString("name").trim() }
                    id.takeIf { it.isNotBlank() }?.let {
                        FdroidAntiFeatureSnapshot(
                            id = it,
                            label = element.localizedString("label"),
                            description = element.localizedString("description")
                        )
                    }
                }

                else -> null
            }
        }
    }

    private fun JsonObject.localizedString(key: String): String {
        val element = this[key] ?: return ""
        return element.localizedStringValue()
    }

    private fun JsonElement.localizedStringValue(): String {
        jsonPrimitiveOrNull()?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        val obj = jsonObjectOrNull() ?: return ""
        return obj.optString("en-US").trim()
            .ifBlank { obj.optString("en").trim() }
            .ifBlank {
                obj.values.firstNotNullOfOrNull { element ->
                    element.jsonPrimitiveOrNull()?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                }.orEmpty()
            }
    }

    private fun JsonObject.longValue(key: String): Long? {
        val element = this[key] ?: return null
        return element.jsonPrimitiveOrNull()?.longOrNull
            ?: element.jsonPrimitiveOrNull()?.contentOrNull?.trim()?.toLongOrNull()
    }

    private fun JsonObject.intValue(key: String): Int? {
        val element = this[key] ?: return null
        return element.jsonPrimitiveOrNull()?.intOrNull
            ?: element.jsonPrimitiveOrNull()?.contentOrNull?.trim()?.toIntOrNull()
    }

    private fun JsonObject.stringListValue(key: String): List<String> {
        val element = this[key] ?: return emptyList()
        element.jsonPrimitiveOrNull()?.contentOrNull?.let { primitive ->
            return primitive.split(',', ';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
        return element.jsonArrayOrNull()
            ?.mapNotNull { item ->
                item.jsonPrimitiveOrNull()?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            }
            .orEmpty()
    }
}
