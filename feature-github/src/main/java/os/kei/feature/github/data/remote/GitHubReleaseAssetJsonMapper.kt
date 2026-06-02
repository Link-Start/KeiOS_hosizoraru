package os.kei.feature.github.data.remote

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import os.kei.core.json.optArray
import os.kei.core.json.optInt
import os.kei.core.json.optLong
import os.kei.core.json.optString
import java.time.Instant

object GitHubReleaseAssetJsonMapper {
    fun buildReleaseStub(
        releaseName: String,
        rawTag: String,
        releaseUrl: String,
        releaseUpdatedAtMillis: Long? = null,
        releaseNotesBody: String = "",
        assets: List<GitHubReleaseAssetFile> = emptyList()
    ): JsonObject {
        return buildJsonObject {
            put("name", releaseName)
            put("tag_name", rawTag)
            put("html_url", releaseUrl)
            put("body", releaseNotesBody)
            put(
                "published_at",
                releaseUpdatedAtMillis?.let { Instant.ofEpochMilli(it).toString() }
                    ?.let(::JsonPrimitive) ?: JsonNull
            )
            put(
                "assets",
                buildJsonArray {
                    assets.forEach { asset ->
                        add(
                            buildJsonObject {
                                put("name", asset.name)
                                put("browser_download_url", asset.downloadUrl)
                                put("url", asset.apiAssetUrl)
                                put("size", asset.sizeBytes)
                                put("download_count", asset.downloadCount)
                                put("content_type", asset.contentType)
                                put("digest", asset.digest)
                                put(
                                    "updated_at",
                                    asset.updatedAtMillis?.let {
                                        Instant.ofEpochMilli(it).toString()
                                    }?.let(::JsonPrimitive) ?: JsonNull
                                )
                            }
                        )
                    }
                }
            )
        }
    }

    fun parseReleaseBundle(release: JsonObject): GitHubReleaseAssetBundle {
        val releaseName = release.optString("name").trim()
        val tagName = release.optString("tag_name").trim().ifBlank { releaseName }
        val htmlUrl = release.optString("html_url").trim()
        val releaseUpdatedAtMillis = release.optString("published_at").parseIsoInstantOrNull()
            ?: release.optString("updated_at").parseIsoInstantOrNull()
            ?: release.optString("created_at").parseIsoInstantOrNull()
        val assetsArray = release.optArray("assets").orEmpty()
        val assets = buildList {
            for (element in assetsArray) {
                val asset = element as? JsonObject ?: continue
                val name = asset.optString("name").trim()
                val downloadUrl = asset.optString("browser_download_url").trim()
                if (name.isBlank() || downloadUrl.isBlank()) continue
                add(
                    GitHubReleaseAssetFile(
                        name = name,
                        downloadUrl = downloadUrl,
                        apiAssetUrl = asset.optString("url").trim(),
                        sizeBytes = asset.optLong("size", 0L),
                        downloadCount = asset.optInt("download_count", 0),
                        contentType = asset.optString("content_type").trim(),
                        updatedAtMillis = asset.optString("updated_at").parseIsoInstantOrNull()
                            ?: asset.optString("created_at").parseIsoInstantOrNull(),
                        digest = asset.optString("digest").trim()
                    )
                )
            }
        }
        return GitHubReleaseAssetBundle(
            releaseName = releaseName,
            tagName = tagName,
            htmlUrl = htmlUrl,
            releaseUpdatedAtMillis = releaseUpdatedAtMillis,
            releaseNotesBody = release.optString("body").trim(),
            assets = assets,
            shortCommitSha = ""
        )
    }

    private fun String.parseIsoInstantOrNull(): Long? {
        return runCatching {
            if (isBlank()) null else Instant.parse(this).toEpochMilli()
        }.getOrNull()
    }
}
