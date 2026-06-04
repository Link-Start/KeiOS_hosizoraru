package os.kei.feature.github.data.local

import com.tencent.mmkv.MMKV
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import os.kei.core.json.encodeCompact
import os.kei.core.json.optInt
import os.kei.core.json.optLong
import os.kei.core.json.optString
import os.kei.core.json.parseJsonObjectOrNull
import os.kei.core.prefs.KeiMmkv
import os.kei.feature.github.model.GitHubActionsNotificationHistoryRecord
import java.security.MessageDigest
import java.util.Locale

object GitHubActionsNotificationHistoryStore {
    private const val KV_ID = "github_actions_notification_history"
    private const val KEY_INDEX = "entry_index"
    internal const val MAX_RECORDS = 160

    private val store: MMKV by lazy { KeiMmkv.byId(KV_ID) }

    private fun kv(): MMKV = store

    fun recordNotification(record: GitHubActionsNotificationHistoryRecord) {
        val normalized = record.normalizedForStorage()
        if (normalized.trackId.isBlank() || normalized.owner.isBlank() || normalized.repo.isBlank()) {
            return
        }
        if (normalized.runId <= 0L && normalized.runNumber <= 0L) {
            return
        }
        val kv = kv()
        val id = recordId(normalized)
        kv.encode(entryStoreKey(id), encodeRecord(normalized).encodeCompact())
        val index = loadIndex(kv)
        index.remove(id)
        index.add(id)
        trimIndex(index, kv)
        saveIndex(index, kv)
    }

    fun load(
        owner: String = "",
        repo: String = "",
    ): List<GitHubActionsNotificationHistoryRecord> {
        val normalizedOwner = owner.trim().lowercase(Locale.ROOT)
        val normalizedRepo = repo.trim().lowercase(Locale.ROOT)
        val kv = kv()
        return loadIndex(kv)
            .mapNotNull { id ->
                val raw = kv.decodeString(entryStoreKey(id)).orEmpty()
                decodeRecord(raw)?.takeIf { record ->
                    (normalizedOwner.isBlank() || record.owner.equals(normalizedOwner, ignoreCase = true)) &&
                        (normalizedRepo.isBlank() || record.repo.equals(normalizedRepo, ignoreCase = true))
                }
            }
            .sortedByDescending { it.notifiedAtMillis }
    }

    fun clear(owner: String = "", repo: String = "") {
        val normalizedOwner = owner.trim().lowercase(Locale.ROOT)
        val normalizedRepo = repo.trim().lowercase(Locale.ROOT)
        val kv = kv()
        if (normalizedOwner.isBlank() && normalizedRepo.isBlank()) {
            loadIndex(kv).forEach { id -> kv.removeValueForKey(entryStoreKey(id)) }
            kv.removeValueForKey(KEY_INDEX)
            kv.trim()
            return
        }
        val remaining = mutableSetOf<String>()
        loadIndex(kv).forEach { id ->
            val record = decodeRecord(kv.decodeString(entryStoreKey(id)).orEmpty())
            val matched =
                record != null &&
                    (normalizedOwner.isBlank() || record.owner.equals(normalizedOwner, ignoreCase = true)) &&
                    (normalizedRepo.isBlank() || record.repo.equals(normalizedRepo, ignoreCase = true))
            if (matched) {
                kv.removeValueForKey(entryStoreKey(id))
            } else {
                remaining += id
            }
        }
        saveIndex(remaining, kv)
    }

    fun pruneBefore(
        cutoffMillis: Long,
        owner: String = "",
        repo: String = "",
    ): Int {
        if (cutoffMillis <= 0L) return 0
        val normalizedOwner = owner.trim().lowercase(Locale.ROOT)
        val normalizedRepo = repo.trim().lowercase(Locale.ROOT)
        val kv = kv()
        val remaining = mutableSetOf<String>()
        var removedCount = 0
        loadIndex(kv).forEach { id ->
            val record = decodeRecord(kv.decodeString(entryStoreKey(id)).orEmpty())
            val scoped =
                record != null &&
                    (normalizedOwner.isBlank() || record.owner.equals(normalizedOwner, ignoreCase = true)) &&
                    (normalizedRepo.isBlank() || record.repo.equals(normalizedRepo, ignoreCase = true))
            if (scoped && shouldPruneBefore(record, cutoffMillis)) {
                kv.removeValueForKey(entryStoreKey(id))
                removedCount += 1
            } else {
                remaining += id
            }
        }
        saveIndex(remaining, kv)
        if (removedCount > 0) {
            kv.trim()
        }
        return removedCount
    }

    fun cachedRecordCount(): Int = loadIndex().size

    internal fun encodeRecord(record: GitHubActionsNotificationHistoryRecord): JsonObject {
        return buildJsonObject {
            put("trackId", record.trackId)
            put("owner", record.owner)
            put("repo", record.repo)
            put("appLabel", record.appLabel)
            put("workflowId", record.workflowId)
            put("workflowName", record.workflowName)
            put("workflowPath", record.workflowPath)
            put("runId", record.runId)
            put("runNumber", record.runNumber)
            put("runAttempt", record.runAttempt)
            put("runDisplayName", record.runDisplayName)
            put("headBranch", record.headBranch)
            put("headSha", record.headSha)
            put("event", record.event)
            put("status", record.status)
            put("conclusion", record.conclusion)
            put("htmlUrl", record.htmlUrl)
            put("artifactCount", record.artifactCount)
            put("androidArtifactCount", record.androidArtifactCount)
            put("checkedAtMillis", record.checkedAtMillis)
            put("notifiedAtMillis", record.notifiedAtMillis)
            put("notificationTitle", record.notificationTitle)
            put("notificationContent", record.notificationContent)
        }
    }

    internal fun decodeRecord(raw: String): GitHubActionsNotificationHistoryRecord? {
        if (raw.isBlank()) return null
        return runCatching {
            val obj = raw.parseJsonObjectOrNull() ?: return@runCatching null
            val trackId = obj.optString("trackId").trim()
            val owner = obj.optString("owner").trim()
            val repo = obj.optString("repo").trim()
            val runId = obj.optLong("runId", 0L)
            val runNumber = obj.optLong("runNumber", 0L)
            val notifiedAtMillis = obj.optLong("notifiedAtMillis", 0L)
            if (
                trackId.isBlank() ||
                owner.isBlank() ||
                repo.isBlank() ||
                (runId <= 0L && runNumber <= 0L) ||
                notifiedAtMillis <= 0L
            ) {
                return@runCatching null
            }
            GitHubActionsNotificationHistoryRecord(
                trackId = trackId,
                owner = owner,
                repo = repo,
                appLabel = obj.optString("appLabel").trim(),
                workflowId = obj.optLong("workflowId", 0L),
                workflowName = obj.optString("workflowName").trim(),
                workflowPath = obj.optString("workflowPath").trim(),
                runId = runId,
                runNumber = runNumber,
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
                checkedAtMillis = obj.optLong("checkedAtMillis", 0L),
                notifiedAtMillis = notifiedAtMillis,
                notificationTitle = obj.optString("notificationTitle").trim(),
                notificationContent = obj.optString("notificationContent").trim(),
            )
        }.getOrNull()
    }

    internal fun GitHubActionsNotificationHistoryRecord.normalizedForStorage(): GitHubActionsNotificationHistoryRecord {
        val normalizedNotifiedAt = notifiedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        return copy(
            trackId = trackId.trim(),
            owner = owner.trim().lowercase(Locale.ROOT),
            repo = repo.trim().lowercase(Locale.ROOT),
            appLabel = appLabel.trim(),
            workflowName = workflowName.trim(),
            workflowPath = workflowPath.trim(),
            runDisplayName = runDisplayName.trim(),
            headBranch = headBranch.trim(),
            headSha = headSha.trim(),
            event = event.trim(),
            status = status.trim(),
            conclusion = conclusion.trim(),
            htmlUrl = htmlUrl.trim(),
            artifactCount = artifactCount.coerceAtLeast(0),
            androidArtifactCount = androidArtifactCount.coerceAtLeast(0),
            checkedAtMillis = checkedAtMillis.coerceAtLeast(0L),
            notifiedAtMillis = normalizedNotifiedAt,
            notificationTitle = notificationTitle.trim(),
            notificationContent = notificationContent.trim(),
        )
    }

    internal fun recordId(record: GitHubActionsNotificationHistoryRecord): String {
        return sha1(
            listOf(
                record.trackId,
                record.owner,
                record.repo,
                record.workflowId.toString(),
                record.workflowPath,
                record.runId.toString(),
                record.runNumber.toString(),
            ).joinToString("|"),
        )
    }

    internal fun shouldPruneBefore(
        record: GitHubActionsNotificationHistoryRecord,
        cutoffMillis: Long,
    ): Boolean {
        return cutoffMillis > 0L && record.notifiedAtMillis in 1 until cutoffMillis
    }

    private fun trimIndex(index: MutableSet<String>, kv: MMKV) {
        if (index.size <= MAX_RECORDS) return
        val sorted =
            index
                .mapNotNull { id ->
                    val record = decodeRecord(kv.decodeString(entryStoreKey(id)).orEmpty())
                    record?.let { id to it.notifiedAtMillis }
                }
                .sortedByDescending { it.second }
        val keep = sorted.take(MAX_RECORDS).map { it.first }.toSet()
        index.filter { it !in keep }.forEach { id ->
            kv.removeValueForKey(entryStoreKey(id))
        }
        index.retainAll(keep)
    }

    private fun entryStoreKey(id: String): String = "entry_$id"

    private fun loadIndex(kv: MMKV = store): MutableSet<String> {
        val raw = kv.decodeString(KEY_INDEX).orEmpty()
        if (raw.isBlank()) return mutableSetOf()
        return raw
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
    }

    private fun saveIndex(index: Set<String>, kv: MMKV = store) {
        kv.encode(KEY_INDEX, index.filter { it.isNotBlank() }.joinToString(","))
    }

    private fun sha1(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(text.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
