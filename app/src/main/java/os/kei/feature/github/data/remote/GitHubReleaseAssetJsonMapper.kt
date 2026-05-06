package os.kei.feature.github.data.remote

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

internal object GitHubReleaseAssetJsonMapper {
    fun buildReleaseStub(
        releaseName: String,
        rawTag: String,
        releaseUrl: String,
        releaseUpdatedAtMillis: Long? = null,
        releaseNotesBody: String = "",
        assets: List<GitHubReleaseAssetFile> = emptyList()
    ): JSONObject {
        return JSONObject()
            .put("name", releaseName)
            .put("tag_name", rawTag)
            .put("html_url", releaseUrl)
            .put("body", releaseNotesBody)
            .put(
                "published_at",
                releaseUpdatedAtMillis?.let { Instant.ofEpochMilli(it).toString() }
                    ?: JSONObject.NULL)
            .put(
                "assets",
                JSONArray().apply {
                    assets.forEach { asset ->
                        put(
                            JSONObject()
                                .put("name", asset.name)
                                .put("browser_download_url", asset.downloadUrl)
                                .put("url", asset.apiAssetUrl)
                                .put("size", asset.sizeBytes)
                                .put("download_count", asset.downloadCount)
                                .put("content_type", asset.contentType)
                                .put(
                                    "updated_at",
                                    asset.updatedAtMillis?.let {
                                        Instant.ofEpochMilli(it).toString()
                                    } ?: JSONObject.NULL)
                        )
                    }
                }
            )
    }

    fun parseReleaseBundle(release: JSONObject): GitHubReleaseAssetBundle {
        val releaseName = release.optString("name").trim()
        val tagName = release.optString("tag_name").trim().ifBlank { releaseName }
        val htmlUrl = release.optString("html_url").trim()
        val releaseUpdatedAtMillis = release.optString("published_at").parseIsoInstantOrNull()
            ?: release.optString("updated_at").parseIsoInstantOrNull()
            ?: release.optString("created_at").parseIsoInstantOrNull()
        val assetsArray = release.optJSONArray("assets") ?: JSONArray()
        val assets = buildList {
            for (index in 0 until assetsArray.length()) {
                val asset = assetsArray.optJSONObject(index) ?: continue
                val name = asset.optString("name").trim()
                val downloadUrl = asset.optString("browser_download_url").trim()
                if (name.isBlank() || downloadUrl.isBlank()) continue
                add(
                    GitHubReleaseAssetFile(
                        name = name,
                        downloadUrl = downloadUrl,
                        apiAssetUrl = asset.optString("url").trim(),
                        sizeBytes = asset.optLong("size", 0L),
                        downloadCount = when (val count = asset.opt("download_count")) {
                            is Number -> count.toInt()
                            is String -> count.toIntOrNull() ?: 0
                            else -> 0
                        },
                        contentType = asset.optString("content_type").trim(),
                        updatedAtMillis = asset.optString("updated_at").parseIsoInstantOrNull()
                            ?: asset.optString("created_at").parseIsoInstantOrNull()
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
