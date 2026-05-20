package os.kei.feature.github.data.local

import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv
import org.json.JSONArray
import org.json.JSONObject
import os.kei.feature.github.domain.GitHubStarImportApkVerificationCache
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubStarImportApkVerification
import os.kei.feature.github.model.GitHubStarImportApkVerificationStatus
import java.security.MessageDigest

object GitHubStarImportApkVerificationCacheStore : GitHubStarImportApkVerificationCache {
    private const val KV_ID = "github_star_import_apk_verification_cache"
    private const val KEY_INDEX = "entry_index"
    private const val MAX_ENTRIES = 200

    private val store: MMKV by lazy { KeiMmkv.byId(KV_ID) }

    override fun load(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig,
        refreshIntervalHours: Int,
        nowMillis: Long
    ): GitHubStarImportApkVerification? {
        val cacheKey = buildCacheKey(owner, repo, lookupConfig)
        val id = keyId(cacheKey)
        val raw = store.decodeString(entryStoreKey(id), "").orEmpty()
        if (raw.isBlank()) return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return clearAndNull(id)
        if (root.optString("cacheKey").trim() != cacheKey) return clearAndNull(id)
        val verification = decode(root.optJSONObject("verification")) ?: return clearAndNull(id)
        val checkedAt = verification.checkedAtMillis.coerceAtLeast(0L)
        val intervalMs = refreshIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
        val expired = checkedAt <= 0L || (nowMillis - checkedAt).coerceAtLeast(0L) >= intervalMs
        return if (expired) {
            clearAndNull(id)
        } else {
            verification
        }
    }

    override fun save(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig,
        verification: GitHubStarImportApkVerification
    ) {
        save(buildCacheKey(owner, repo, lookupConfig), verification)
    }

    fun clearAll() {
        loadIndex().keys.forEach { id ->
            store.removeValueForKey(entryStoreKey(id))
        }
        store.removeValueForKey(KEY_INDEX)
    }

    private fun save(
        cacheKey: String,
        verification: GitHubStarImportApkVerification
    ) {
        val id = keyId(cacheKey)
        val payload = JSONObject()
            .put("cacheKey", cacheKey)
            .put("verification", encode(verification))
            .toString()
        store.encode(entryStoreKey(id), payload)
        addIndex(id, verification.checkedAtMillis)
        trimToMaxEntries()
    }

    private fun buildCacheKey(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig
    ): String {
        return listOf(
            owner.trim().lowercase(),
            repo.trim().lowercase(),
            lookupConfig.selectedStrategy.storageId,
            lookupConfig.aggressiveApkFiltering.toString(),
            lookupConfig.apiToken.trim().takeIf { it.isNotBlank() }?.let(::keyId).orEmpty()
        ).joinToString("|")
    }

    private fun clearAndNull(id: String): GitHubStarImportApkVerification? {
        store.removeValueForKey(entryStoreKey(id))
        removeIndex(id)
        return null
    }

    private fun addIndex(id: String, checkedAtMillis: Long) {
        val index = loadIndex().toMutableMap()
        index[id] = checkedAtMillis.coerceAtLeast(0L)
        saveIndex(index)
    }

    private fun removeIndex(id: String) {
        val index = loadIndex().toMutableMap()
        index.remove(id)
        saveIndex(index)
    }

    private fun trimToMaxEntries() {
        val index = loadIndex()
        if (index.size <= MAX_ENTRIES) return
        val trimmed = index.entries
            .sortedByDescending { it.value }
            .take(MAX_ENTRIES)
            .associate { it.key to it.value }
        val keep = trimmed.keys
        index.keys
            .filterNot { it in keep }
            .forEach { id -> store.removeValueForKey(entryStoreKey(id)) }
        saveIndex(trimmed)
    }

    private fun loadIndex(): Map<String, Long> {
        val raw = store.decodeString(KEY_INDEX, "").orEmpty()
        if (raw.isBlank()) return emptyMap()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyMap()
        return buildMap {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val id = obj.optString("id").trim()
                if (id.isNotBlank()) {
                    put(id, obj.optLong("checkedAtMillis", 0L))
                }
            }
        }
    }

    private fun saveIndex(index: Map<String, Long>) {
        val array = JSONArray()
        index.entries
            .sortedByDescending { it.value }
            .forEach { (id, checkedAt) ->
                array.put(
                    JSONObject()
                        .put("id", id)
                        .put("checkedAtMillis", checkedAt.coerceAtLeast(0L))
                )
            }
        store.encode(KEY_INDEX, array.toString())
    }

    private fun encode(verification: GitHubStarImportApkVerification): JSONObject {
        return JSONObject()
            .put("owner", verification.owner)
            .put("repo", verification.repo)
            .put("status", verification.status.name)
            .put("releaseTag", verification.releaseTag)
            .put("releaseUrl", verification.releaseUrl)
            .put("apkAssetCount", verification.apkAssetCount)
            .put("sampleAssetName", verification.sampleAssetName)
            .put("packageName", verification.packageName)
            .put("checkedAtMillis", verification.checkedAtMillis)
            .put("errorMessage", verification.errorMessage)
    }

    private fun decode(obj: JSONObject?): GitHubStarImportApkVerification? {
        obj ?: return null
        val owner = obj.optString("owner").trim()
        val repo = obj.optString("repo").trim()
        if (owner.isBlank() || repo.isBlank()) return null
        val status = runCatching {
            GitHubStarImportApkVerificationStatus.valueOf(obj.optString("status"))
        }.getOrNull() ?: return null
        return GitHubStarImportApkVerification(
            owner = owner,
            repo = repo,
            status = status,
            releaseTag = obj.optString("releaseTag").trim(),
            releaseUrl = obj.optString("releaseUrl").trim(),
            apkAssetCount = obj.optInt("apkAssetCount", 0).coerceAtLeast(0),
            sampleAssetName = obj.optString("sampleAssetName").trim(),
            packageName = obj.optString("packageName").trim(),
            checkedAtMillis = obj.optLong("checkedAtMillis", 0L).coerceAtLeast(0L),
            errorMessage = obj.optString("errorMessage").trim()
        )
    }

    private fun keyId(cacheKey: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(cacheKey.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun entryStoreKey(id: String): String = "entry_$id"
}
