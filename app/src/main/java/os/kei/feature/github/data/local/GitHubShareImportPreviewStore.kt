package os.kei.feature.github.data.local

import com.tencent.mmkv.MMKV
import org.json.JSONArray
import org.json.JSONObject
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile

data class GitHubPendingShareImportPreviewRecord(
    val sourceUrl: String,
    val projectUrl: String,
    val owner: String,
    val repo: String,
    val releaseTag: String,
    val releaseUrl: String,
    val strategyLabel: String,
    val assets: List<GitHubReleaseAssetFile>,
    val preferredAssetName: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

object GitHubShareImportPreviewStore {
    private const val KV_ID = "github_share_import_preview_store"
    private const val KEY_ACTIVE_PREVIEW = "github_active_share_import_preview"
    private const val ACTIVE_PREVIEW_MAX_AGE_MS = 25 * 60 * 1000L

    private val store: MMKV by lazy { MMKV.mmkvWithID(KV_ID) }

    fun loadActivePreview(nowMillis: Long = System.currentTimeMillis()): GitHubPendingShareImportPreviewRecord? {
        val raw = store.decodeString(KEY_ACTIVE_PREVIEW).orEmpty()
        if (raw.isBlank()) return null
        val record = runCatching {
            parsePreview(JSONObject(raw))
        }.getOrNull()
        if (record == null || record.isExpired(nowMillis)) {
            clearActivePreview()
            return null
        }
        return record
    }

    fun saveActivePreview(record: GitHubPendingShareImportPreviewRecord?) {
        if (record == null) {
            clearActivePreview()
            return
        }
        store.encode(KEY_ACTIVE_PREVIEW, previewToJson(record).toString())
    }

    fun clearActivePreview() {
        store.removeValueForKey(KEY_ACTIVE_PREVIEW)
    }

    private fun GitHubPendingShareImportPreviewRecord.isExpired(nowMillis: Long): Boolean {
        return createdAtMillis <= 0L ||
                (nowMillis - createdAtMillis).coerceAtLeast(0L) > ACTIVE_PREVIEW_MAX_AGE_MS
    }

    private fun parsePreview(obj: JSONObject): GitHubPendingShareImportPreviewRecord? {
        val sourceUrl = obj.optString("sourceUrl").trim()
        val projectUrl = obj.optString("projectUrl").trim()
        val owner = obj.optString("owner").trim()
        val repo = obj.optString("repo").trim()
        val assets = obj.optJSONArray("assets")?.let(::parseAssets).orEmpty()
        if (sourceUrl.isBlank() || projectUrl.isBlank() || owner.isBlank() || repo.isBlank() || assets.isEmpty()) {
            return null
        }
        return GitHubPendingShareImportPreviewRecord(
            sourceUrl = sourceUrl,
            projectUrl = projectUrl,
            owner = owner,
            repo = repo,
            releaseTag = obj.optString("releaseTag").trim(),
            releaseUrl = obj.optString("releaseUrl").trim(),
            strategyLabel = obj.optString("strategyLabel").trim(),
            assets = assets,
            preferredAssetName = obj.optString("preferredAssetName").trim(),
            createdAtMillis = obj.optLong("createdAtMillis", 0L)
        )
    }

    private fun parseAssets(array: JSONArray): List<GitHubReleaseAssetFile> {
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val name = obj.optString("name").trim()
                val downloadUrl = obj.optString("downloadUrl").trim()
                if (name.isBlank() || downloadUrl.isBlank()) continue
                add(
                    GitHubReleaseAssetFile(
                        name = name,
                        downloadUrl = downloadUrl,
                        apiAssetUrl = obj.optString("apiAssetUrl").trim(),
                        sizeBytes = obj.optLong("sizeBytes", 0L).coerceAtLeast(0L),
                        downloadCount = obj.optInt("downloadCount", 0).coerceAtLeast(0),
                        contentType = obj.optString("contentType").trim(),
                        updatedAtMillis = obj.optLong("updatedAtMillis", -1L)
                            .takeIf { it > 0L }
                    )
                )
            }
        }
    }

    private fun previewToJson(record: GitHubPendingShareImportPreviewRecord): JSONObject {
        return JSONObject()
            .put("sourceUrl", record.sourceUrl)
            .put("projectUrl", record.projectUrl)
            .put("owner", record.owner)
            .put("repo", record.repo)
            .put("releaseTag", record.releaseTag)
            .put("releaseUrl", record.releaseUrl)
            .put("strategyLabel", record.strategyLabel)
            .put("preferredAssetName", record.preferredAssetName)
            .put("createdAtMillis", record.createdAtMillis)
            .put(
                "assets",
                JSONArray().apply {
                    record.assets.forEach { asset ->
                        put(assetToJson(asset))
                    }
                }
            )
    }

    private fun assetToJson(asset: GitHubReleaseAssetFile): JSONObject {
        return JSONObject()
            .put("name", asset.name)
            .put("downloadUrl", asset.downloadUrl)
            .put("apiAssetUrl", asset.apiAssetUrl)
            .put("sizeBytes", asset.sizeBytes)
            .put("downloadCount", asset.downloadCount)
            .put("contentType", asset.contentType)
            .put("updatedAtMillis", asset.updatedAtMillis ?: -1L)
    }
}
