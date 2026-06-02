package os.kei.feature.github.data.local

import com.tencent.mmkv.MMKV
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import os.kei.core.json.encodeCompact
import os.kei.core.json.optInt
import os.kei.core.json.optLong
import os.kei.core.json.optObject
import os.kei.core.json.optString
import os.kei.core.json.parseJsonObjectOrNull
import os.kei.core.json.toMutableJsonMap
import os.kei.core.prefs.KeiMmkv
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot

object GitHubActionsRecommendedRunStore {
    private const val KV_ID = "github_actions_recommended_runs"
    private const val KEY_SNAPSHOTS = "snapshots"

    private val store: MMKV by lazy { KeiMmkv.byId(KV_ID) }

    fun load(trackId: String): GitHubActionsRecommendedRunSnapshot? {
        val normalizedTrackId = trackId.trim()
        if (normalizedTrackId.isBlank()) return null
        val root = loadRoot()
        return decodeSnapshot(root.optObject(normalizedTrackId))
    }

    fun loadAll(): Map<String, GitHubActionsRecommendedRunSnapshot> {
        val root = loadRoot()
        return buildMap {
            for (key in root.keys) {
                val trackId = key.trim()
                if (trackId.isBlank()) continue
                decodeSnapshot(root.optObject(trackId))?.let { snapshot ->
                    put(trackId, snapshot)
                }
            }
        }
    }

    fun save(snapshot: GitHubActionsRecommendedRunSnapshot) {
        if (snapshot.trackId.isBlank() || snapshot.runId <= 0L) return
        val root = loadRoot()
        val map = root.toMutableJsonMap()
        map[snapshot.trackId] = encodeSnapshot(snapshot)
        store.encode(KEY_SNAPSHOTS, JsonObject(map).encodeCompact())
    }

    fun remove(trackId: String) {
        val normalizedTrackId = trackId.trim()
        if (normalizedTrackId.isBlank()) return
        val root = loadRoot()
        val map = root.toMutableJsonMap()
        map.remove(normalizedTrackId)
        store.encode(KEY_SNAPSHOTS, JsonObject(map).encodeCompact())
    }

    fun retain(trackIds: Set<String>) {
        val keep = trackIds.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val root = loadRoot()
        val map = root.toMutableJsonMap()
        map.keys.filter { it !in keep }.forEach(map::remove)
        store.encode(KEY_SNAPSHOTS, JsonObject(map).encodeCompact())
    }

    private fun loadRoot(): JsonObject {
        val raw = store.decodeString(KEY_SNAPSHOTS).orEmpty()
        return raw.parseJsonObjectOrNull() ?: JsonObject(emptyMap())
    }

    private fun encodeSnapshot(snapshot: GitHubActionsRecommendedRunSnapshot): JsonObject {
        return buildJsonObject {
            put("trackId", snapshot.trackId)
            put("owner", snapshot.owner)
            put("repo", snapshot.repo)
            put("appLabel", snapshot.appLabel)
            put("workflowId", snapshot.workflowId)
            put("workflowName", snapshot.workflowName)
            put("workflowPath", snapshot.workflowPath)
            put("runId", snapshot.runId)
            put("runNumber", snapshot.runNumber)
            put("runAttempt", snapshot.runAttempt)
            put("runDisplayName", snapshot.runDisplayName)
            put("headBranch", snapshot.headBranch)
            put("headSha", snapshot.headSha)
            put("event", snapshot.event)
            put("status", snapshot.status)
            put("conclusion", snapshot.conclusion)
            put("htmlUrl", snapshot.htmlUrl)
            put("artifactCount", snapshot.artifactCount)
            put("androidArtifactCount", snapshot.androidArtifactCount)
            put("createdAtMillis", snapshot.createdAtMillis)
            put("updatedAtMillis", snapshot.updatedAtMillis)
            put("checkedAtMillis", snapshot.checkedAtMillis)
        }
    }

    private fun decodeSnapshot(obj: JsonObject?): GitHubActionsRecommendedRunSnapshot? {
        obj ?: return null
        val trackId = obj.optString("trackId").trim()
        val runId = obj.optLong("runId", 0L)
        if (trackId.isBlank() || runId <= 0L) return null
        return GitHubActionsRecommendedRunSnapshot(
            trackId = trackId,
            owner = obj.optString("owner").trim(),
            repo = obj.optString("repo").trim(),
            appLabel = obj.optString("appLabel").trim(),
            workflowId = obj.optLong("workflowId", 0L),
            workflowName = obj.optString("workflowName").trim(),
            workflowPath = obj.optString("workflowPath").trim(),
            runId = runId,
            runNumber = obj.optLong("runNumber", 0L),
            runAttempt = obj.optInt("runAttempt", 0),
            runDisplayName = obj.optString("runDisplayName").trim(),
            headBranch = obj.optString("headBranch").trim(),
            headSha = obj.optString("headSha").trim(),
            event = obj.optString("event").trim(),
            status = obj.optString("status").trim(),
            conclusion = obj.optString("conclusion").trim(),
            htmlUrl = obj.optString("htmlUrl").trim(),
            artifactCount = obj.optInt("artifactCount", 0),
            androidArtifactCount = obj.optInt("androidArtifactCount", 0),
            createdAtMillis = obj.optLong("createdAtMillis", 0L),
            updatedAtMillis = obj.optLong("updatedAtMillis", 0L),
            checkedAtMillis = obj.optLong("checkedAtMillis", 0L)
        )
    }
}
