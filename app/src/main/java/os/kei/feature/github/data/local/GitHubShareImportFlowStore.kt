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
    val targetDisplayName: String = "",
    val selectedAssetName: String = "",
    val sendInstallActionEnabled: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class GitHubPendingShareImportAttachCandidateRecord(
    val projectUrl: String,
    val owner: String,
    val repo: String,
    val packageName: String,
    val appLabel: String,
    val eventAction: String,
    val detectedAtMillis: Long = System.currentTimeMillis(),
    val firstInstallTimeMs: Long = -1L
)

data class GitHubPendingShareImportManagedInstallRecord(
    val requestId: String,
    val projectUrl: String,
    val owner: String,
    val repo: String,
    val releaseTag: String = "",
    val assetName: String = "",
    val packageName: String = "",
    val targetDisplayName: String = "",
    val sessionId: Int = -1,
    val progressPhase: String = "",
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val startedAtMillis: Long = System.currentTimeMillis()
)

const val GITHUB_SHARE_IMPORT_RESULT_STATUS_ADDED = "added"
const val GITHUB_SHARE_IMPORT_RESULT_STATUS_ALREADY_TRACKED = "already_tracked"
const val GITHUB_SHARE_IMPORT_RESULT_STATUS_FAILED = "failed"
const val GITHUB_SHARE_IMPORT_RESULT_STATUS_CANCELLED = "cancelled"

internal const val GITHUB_SHARE_IMPORT_ACTIVE_PREVIEW_MAX_AGE_MS = 25 * 60 * 1000L
internal const val GITHUB_SHARE_IMPORT_ACTIVE_RESULT_MAX_AGE_MS = 2 * 60 * 60 * 1000L

internal fun isGitHubShareImportPreviewExpired(createdAtMillis: Long, nowMillis: Long): Boolean {
    return createdAtMillis <= 0L ||
        (nowMillis - createdAtMillis).coerceAtLeast(0L) > GITHUB_SHARE_IMPORT_ACTIVE_PREVIEW_MAX_AGE_MS
}

internal fun isGitHubShareImportResultExpired(completedAtMillis: Long, nowMillis: Long): Boolean {
    return completedAtMillis <= 0L ||
        (nowMillis - completedAtMillis).coerceAtLeast(0L) > GITHUB_SHARE_IMPORT_ACTIVE_RESULT_MAX_AGE_MS
}

data class GitHubShareImportResultRecord(
    val status: String,
    val projectUrl: String = "",
    val owner: String = "",
    val repo: String = "",
    val appLabel: String = "",
    val packageName: String = "",
    val targetDisplayName: String = "",
    val message: String = "",
    val completedAtMillis: Long = System.currentTimeMillis()
)

object GitHubShareImportFlowStore {
    private const val KV_ID = "github_share_import_preview_store"
    private const val KEY_ACTIVE_PREVIEW = "github_active_share_import_preview"
    private const val KEY_ACTIVE_ATTACH_CANDIDATE = "github_active_share_import_attach_candidate"
    private const val KEY_ACTIVE_MANAGED_INSTALL = "github_active_share_import_managed_install"
    private const val KEY_ACTIVE_RESULT = "github_active_share_import_result"

    private val store: MMKV by lazy {
        MMKV.mmkvWithID(KV_ID, MMKV.MULTI_PROCESS_MODE)
    }

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

    fun loadActiveAttachCandidate(
        nowMillis: Long = System.currentTimeMillis()
    ): GitHubPendingShareImportAttachCandidateRecord? {
        val raw = store.decodeString(KEY_ACTIVE_ATTACH_CANDIDATE).orEmpty()
        if (raw.isBlank()) return null
        val record = runCatching {
            parseAttachCandidate(JSONObject(raw))
        }.getOrNull()
        if (record == null || record.isExpired(nowMillis)) {
            clearActiveAttachCandidate()
            return null
        }
        return record
    }

    fun saveActiveAttachCandidate(record: GitHubPendingShareImportAttachCandidateRecord?) {
        if (record == null) {
            clearActiveAttachCandidate()
            return
        }
        store.encode(KEY_ACTIVE_ATTACH_CANDIDATE, attachCandidateToJson(record).toString())
    }

    fun clearActiveAttachCandidate() {
        store.removeValueForKey(KEY_ACTIVE_ATTACH_CANDIDATE)
    }

    fun loadActiveManagedInstall(
        nowMillis: Long = System.currentTimeMillis()
    ): GitHubPendingShareImportManagedInstallRecord? {
        val raw = store.decodeString(KEY_ACTIVE_MANAGED_INSTALL).orEmpty()
        if (raw.isBlank()) return null
        val record = runCatching {
            parseManagedInstall(JSONObject(raw))
        }.getOrNull()
        if (record == null || record.isExpired(nowMillis)) {
            clearActiveManagedInstall()
            return null
        }
        return record
    }

    fun saveActiveManagedInstall(record: GitHubPendingShareImportManagedInstallRecord?) {
        if (record == null) {
            clearActiveManagedInstall()
            return
        }
        store.encode(KEY_ACTIVE_MANAGED_INSTALL, managedInstallToJson(record).toString())
    }

    fun clearActiveManagedInstall() {
        store.removeValueForKey(KEY_ACTIVE_MANAGED_INSTALL)
    }

    fun loadActiveResult(nowMillis: Long = System.currentTimeMillis()): GitHubShareImportResultRecord? {
        val raw = store.decodeString(KEY_ACTIVE_RESULT).orEmpty()
        if (raw.isBlank()) return null
        val record = runCatching {
            parseResult(JSONObject(raw))
        }.getOrNull()
        if (record == null || record.isExpired(nowMillis)) {
            clearActiveResult()
            return null
        }
        return record
    }

    fun saveActiveResult(record: GitHubShareImportResultRecord?) {
        if (record == null) {
            clearActiveResult()
            return
        }
        store.encode(KEY_ACTIVE_RESULT, resultToJson(record).toString())
    }

    fun clearActiveResult() {
        store.removeValueForKey(KEY_ACTIVE_RESULT)
    }

    fun clearActiveFlow() {
        clearActivePreview()
        clearActiveAttachCandidate()
        clearActiveManagedInstall()
        clearActiveResult()
    }

    private fun GitHubPendingShareImportPreviewRecord.isExpired(nowMillis: Long): Boolean {
        return isGitHubShareImportPreviewExpired(createdAtMillis, nowMillis)
    }

    private fun GitHubPendingShareImportAttachCandidateRecord.isExpired(nowMillis: Long): Boolean {
        return isGitHubShareImportPreviewExpired(detectedAtMillis, nowMillis)
    }

    private fun GitHubPendingShareImportManagedInstallRecord.isExpired(nowMillis: Long): Boolean {
        return isGitHubShareImportPreviewExpired(startedAtMillis, nowMillis)
    }

    private fun GitHubShareImportResultRecord.isExpired(nowMillis: Long): Boolean {
        return isGitHubShareImportResultExpired(completedAtMillis, nowMillis)
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
            targetDisplayName = obj.optString("targetDisplayName").trim(),
            selectedAssetName = obj.optString("selectedAssetName").trim(),
            sendInstallActionEnabled = obj.optBoolean("sendInstallActionEnabled", false),
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
            .put("targetDisplayName", record.targetDisplayName)
            .put("selectedAssetName", record.selectedAssetName)
            .put("sendInstallActionEnabled", record.sendInstallActionEnabled)
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

    private fun parseAttachCandidate(obj: JSONObject): GitHubPendingShareImportAttachCandidateRecord? {
        val projectUrl = obj.optString("projectUrl").trim()
        val owner = obj.optString("owner").trim()
        val repo = obj.optString("repo").trim()
        val packageName = obj.optString("packageName").trim()
        val appLabel = obj.optString("appLabel").trim()
        if (projectUrl.isBlank() || owner.isBlank() || repo.isBlank() || packageName.isBlank()) {
            return null
        }
        return GitHubPendingShareImportAttachCandidateRecord(
            projectUrl = projectUrl,
            owner = owner,
            repo = repo,
            packageName = packageName,
            appLabel = appLabel,
            eventAction = obj.optString("eventAction").trim(),
            detectedAtMillis = obj.optLong("detectedAtMillis", 0L),
            firstInstallTimeMs = obj.optLong("firstInstallTimeMs", -1L)
        )
    }

    private fun attachCandidateToJson(record: GitHubPendingShareImportAttachCandidateRecord): JSONObject {
        return JSONObject()
            .put("projectUrl", record.projectUrl)
            .put("owner", record.owner)
            .put("repo", record.repo)
            .put("packageName", record.packageName)
            .put("appLabel", record.appLabel)
            .put("eventAction", record.eventAction)
            .put("detectedAtMillis", record.detectedAtMillis)
            .put("firstInstallTimeMs", record.firstInstallTimeMs)
    }

    private fun parseManagedInstall(obj: JSONObject): GitHubPendingShareImportManagedInstallRecord? {
        val requestId = obj.optString("requestId").trim()
        val projectUrl = obj.optString("projectUrl").trim()
        val owner = obj.optString("owner").trim()
        val repo = obj.optString("repo").trim()
        val assetName = obj.optString("assetName").trim()
        val startedAtMillis = obj.optLong("startedAtMillis", 0L)
        if (
            requestId.isBlank() ||
            projectUrl.isBlank() ||
            owner.isBlank() ||
            repo.isBlank() ||
            assetName.isBlank() ||
            startedAtMillis <= 0L
        ) {
            return null
        }
        return GitHubPendingShareImportManagedInstallRecord(
            requestId = requestId,
            projectUrl = projectUrl,
            owner = owner,
            repo = repo,
            releaseTag = obj.optString("releaseTag").trim(),
            assetName = assetName,
            packageName = obj.optString("packageName").trim(),
            targetDisplayName = obj.optString("targetDisplayName").trim(),
            sessionId = obj.optInt("sessionId", -1),
            progressPhase = obj.optString("progressPhase").trim(),
            progressPercent = obj.optInt("progressPercent", 0),
            downloadedBytes = obj.optLong("downloadedBytes", 0L),
            totalBytes = obj.optLong("totalBytes", -1L),
            startedAtMillis = startedAtMillis
        )
    }

    private fun managedInstallToJson(
        record: GitHubPendingShareImportManagedInstallRecord
    ): JSONObject {
        return JSONObject()
            .put("requestId", record.requestId)
            .put("projectUrl", record.projectUrl)
            .put("owner", record.owner)
            .put("repo", record.repo)
            .put("releaseTag", record.releaseTag)
            .put("assetName", record.assetName)
            .put("packageName", record.packageName)
            .put("targetDisplayName", record.targetDisplayName)
            .put("sessionId", record.sessionId)
            .put("progressPhase", record.progressPhase)
            .put("progressPercent", record.progressPercent)
            .put("downloadedBytes", record.downloadedBytes)
            .put("totalBytes", record.totalBytes)
            .put("startedAtMillis", record.startedAtMillis)
    }

    private fun parseResult(obj: JSONObject): GitHubShareImportResultRecord? {
        val status = obj.optString("status").trim()
        if (status !in activeResultStatuses) return null
        val completedAtMillis = obj.optLong("completedAtMillis", 0L)
        if (completedAtMillis <= 0L) return null
        val owner = obj.optString("owner").trim()
        val repo = obj.optString("repo").trim()
        val appLabel = obj.optString("appLabel").trim()
        val packageName = obj.optString("packageName").trim()
        val targetDisplayName = obj.optString("targetDisplayName").trim()
        val message = obj.optString("message").trim()
        if (
            owner.isBlank() &&
            repo.isBlank() &&
            appLabel.isBlank() &&
            packageName.isBlank() &&
            targetDisplayName.isBlank() &&
            message.isBlank()
        ) {
            return null
        }
        return GitHubShareImportResultRecord(
            status = status,
            projectUrl = obj.optString("projectUrl").trim(),
            owner = owner,
            repo = repo,
            appLabel = appLabel,
            packageName = packageName,
            targetDisplayName = targetDisplayName,
            message = message,
            completedAtMillis = completedAtMillis
        )
    }

    private fun resultToJson(record: GitHubShareImportResultRecord): JSONObject {
        return JSONObject()
            .put("status", record.status)
            .put("projectUrl", record.projectUrl)
            .put("owner", record.owner)
            .put("repo", record.repo)
            .put("appLabel", record.appLabel)
            .put("packageName", record.packageName)
            .put("targetDisplayName", record.targetDisplayName)
            .put("message", record.message)
            .put("completedAtMillis", record.completedAtMillis)
    }

    private val activeResultStatuses = setOf(
        GITHUB_SHARE_IMPORT_RESULT_STATUS_ADDED,
        GITHUB_SHARE_IMPORT_RESULT_STATUS_ALREADY_TRACKED,
        GITHUB_SHARE_IMPORT_RESULT_STATUS_FAILED,
        GITHUB_SHARE_IMPORT_RESULT_STATUS_CANCELLED
    )
}
