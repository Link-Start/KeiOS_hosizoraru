package os.kei.feature.github.data.local

import com.tencent.mmkv.MMKV
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import os.kei.core.json.encodeCompact
import os.kei.core.json.jsonPrimitiveOrNull
import os.kei.core.json.optArray
import os.kei.core.json.optBoolean
import os.kei.core.json.optInt
import os.kei.core.json.optLong
import os.kei.core.json.optObject
import os.kei.core.json.optString
import os.kei.core.json.parseJsonObjectOrNull
import os.kei.core.prefs.KeiMmkv
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import java.security.MessageDigest

object GitHubReleaseAssetCacheStore {
    private const val KV_ID = "github_release_asset_cache"
    private const val KEY_INDEX = "entry_index"
    private const val IDENTICAL_SAVE_TOUCH_INTERVAL_MS = 10L * 60L * 1000L

    private val store: MMKV by lazy { KeiMmkv.byId(KV_ID) }
    private val indexLock = Any()
    private var cachedIndex: MutableSet<String>? = null

    private fun kv(): MMKV = store

    fun buildCacheKey(
        owner: String,
        repo: String,
        rawTag: String,
        releaseUrl: String,
        preferHtml: Boolean,
        aggressiveFiltering: Boolean,
        includeAllAssets: Boolean,
        hasApiToken: Boolean
    ): String {
        return listOf(
            "release-lane-v1",
            owner.trim().lowercase(),
            repo.trim().lowercase(),
            rawTag.trim().lowercase(),
            releaseUrl.trim(),
            preferHtml.toString(),
            aggressiveFiltering.toString(),
            includeAllAssets.toString(),
            hasApiToken.toString()
        ).joinToString("|")
    }

    private fun keyId(cacheKey: String): String = sha1(cacheKey)

    private fun entryStoreKey(id: String): String = "entry_$id"

    private fun loadIndex(kv: MMKV = store): MutableSet<String> =
        synchronized(indexLock) {
            cachedIndex?.toMutableSet() ?: readIndex(kv).also { cachedIndex = it.toMutableSet() }
        }

    private fun readIndex(kv: MMKV): MutableSet<String> {
        val raw = kv.decodeString(KEY_INDEX, "").orEmpty()
        if (raw.isBlank()) return mutableSetOf()
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
    }

    private fun saveIndexLocked(index: Set<String>, kv: MMKV = store) {
        val normalized = index.filter { it.isNotBlank() }.toSortedSet()
        kv.encode(KEY_INDEX, normalized.joinToString(","))
        cachedIndex = normalized.toMutableSet()
    }

    private fun addIndex(id: String, kv: MMKV = store) {
        synchronized(indexLock) {
            val index = cachedIndex ?: readIndex(kv).also { cachedIndex = it.toMutableSet() }
            if (index.add(id)) {
                saveIndexLocked(index, kv)
            }
        }
    }

    private fun removeIndex(id: String, kv: MMKV = store) {
        synchronized(indexLock) {
            val index = cachedIndex ?: readIndex(kv).also { cachedIndex = it.toMutableSet() }
            if (index.remove(id)) {
                saveIndexLocked(index, kv)
            }
        }
    }

    fun load(
        cacheKey: String,
        refreshIntervalHours: Int,
        nowMs: Long = System.currentTimeMillis()
    ): GitHubReleaseAssetBundle? {
        val normalizedKey = cacheKey.trim()
        if (normalizedKey.isBlank()) return null
        val id = keyId(normalizedKey)
        val raw = kv().decodeString(entryStoreKey(id), "").orEmpty()
        if (raw.isBlank()) return null
        val root = raw.parseJsonObjectOrNull() ?: return null
        if (root.optString("cacheKey").trim() != normalizedKey) return null
        val syncedAtMs = root.optLong("syncedAtMs", 0L).coerceAtLeast(0L)
        val intervalMs = refreshIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
        val expired = syncedAtMs <= 0L || (nowMs - syncedAtMs).coerceAtLeast(0L) >= intervalMs
        if (expired) {
            kv().removeValueForKey(entryStoreKey(id))
            removeIndex(id)
            return null
        }
        return decodeBundle(root.optObject("bundle")) ?: run {
            kv().removeValueForKey(entryStoreKey(id))
            removeIndex(id)
            null
        }
    }

    fun save(
        cacheKey: String,
        bundle: GitHubReleaseAssetBundle,
        syncedAtMs: Long = System.currentTimeMillis()
    ) {
        val normalizedKey = cacheKey.trim()
        if (normalizedKey.isBlank()) return
        val id = keyId(normalizedKey)
        val bundleJson = encodeBundle(bundle)
        val bundleHash = sha1(bundleJson.encodeCompact())
        val payload = buildJsonObject {
            put("cacheKey", normalizedKey)
            put("syncedAtMs", syncedAtMs.coerceAtLeast(0L))
            put("bundleHash", bundleHash)
            put("bundle", bundleJson)
        }.encodeCompact()
        val kv = kv()
        if (shouldSkipIdenticalSave(kv, id, bundleHash, syncedAtMs)) {
            addIndex(id, kv)
            return
        }
        kv.encode(entryStoreKey(id), payload)
        addIndex(id, kv)
    }

    fun clearAll() {
        val kv = kv()
        loadIndex(kv).forEach { id ->
            kv.removeValueForKey(entryStoreKey(id))
        }
        kv.removeValueForKey(KEY_INDEX)
        synchronized(indexLock) {
            cachedIndex = mutableSetOf()
        }
        kv.trim()
    }

    fun clear(cacheKey: String) {
        val normalizedKey = cacheKey.trim()
        if (normalizedKey.isBlank()) return
        val id = keyId(normalizedKey)
        val kv = kv()
        kv.removeValueForKey(entryStoreKey(id))
        removeIndex(id, kv)
    }

    fun cachedEntryCount(): Int = loadIndex().size

    private fun shouldSkipIdenticalSave(
        kv: MMKV,
        id: String,
        bundleHash: String,
        syncedAtMs: Long,
    ): Boolean {
        val raw = kv.decodeString(entryStoreKey(id), "").orEmpty()
        if (raw.isBlank()) return false
        val root = raw.parseJsonObjectOrNull() ?: return false
        if (root.optString("bundleHash").trim() != bundleHash) return false
        val previousSyncedAt = root.optLong("syncedAtMs", 0L).coerceAtLeast(0L)
        val touchAgeMs = (syncedAtMs.coerceAtLeast(0L) - previousSyncedAt).coerceAtLeast(0L)
        return touchAgeMs < IDENTICAL_SAVE_TOUCH_INTERVAL_MS
    }

    private fun encodeBundle(bundle: GitHubReleaseAssetBundle): JsonObject {
        return buildJsonObject {
            put("releaseName", bundle.releaseName)
            put("tagName", bundle.tagName)
            put("htmlUrl", bundle.htmlUrl)
            put("releaseUpdatedAtMillis", bundle.releaseUpdatedAtMillis ?: 0L)
            put("releaseNotesBody", bundle.releaseNotesBody)
            put("showingAllAssets", bundle.showingAllAssets)
            put("shortCommitSha", bundle.shortCommitSha)
            put("fetchSource", bundle.fetchSource)
            put("sourceConfigSignature", bundle.sourceConfigSignature)
            bundle.isPreRelease?.let { put("isPreRelease", it) }
            put(
                "assets",
                buildJsonArray {
                    bundle.assets.forEach { asset ->
                        add(
                            buildJsonObject {
                                put("name", asset.name)
                                put("downloadUrl", asset.downloadUrl)
                                put("apiAssetUrl", asset.apiAssetUrl)
                                put("sizeBytes", asset.sizeBytes)
                                put("downloadCount", asset.downloadCount)
                                put("contentType", asset.contentType)
                                put("updatedAtMillis", asset.updatedAtMillis ?: 0L)
                                put("digest", asset.digest)
                                put(
                                    "signerSha256",
                                    buildJsonArray {
                                        asset.signerSha256.forEach { signer ->
                                            add(JsonPrimitive(signer))
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }
    }

    private fun decodeBundle(obj: JsonObject?): GitHubReleaseAssetBundle? {
        obj ?: return null
        val tagName = obj.optString("tagName").trim()
        if (tagName.isBlank()) return null
        val assetsArray = obj.optArray("assets").orEmpty()
        val assets = buildList {
            for (element in assetsArray) {
                val assetObj = element as? JsonObject ?: continue
                val name = assetObj.optString("name").trim()
                val downloadUrl = assetObj.optString("downloadUrl").trim()
                if (name.isBlank() || downloadUrl.isBlank()) continue
                add(
                    GitHubReleaseAssetFile(
                        name = name,
                        downloadUrl = downloadUrl,
                        apiAssetUrl = assetObj.optString("apiAssetUrl").trim(),
                        sizeBytes = assetObj.optLong("sizeBytes", 0L),
                        downloadCount = assetObj.optInt("downloadCount", 0),
                        contentType = assetObj.optString("contentType").trim(),
                        updatedAtMillis = assetObj.optLong("updatedAtMillis", 0L)
                            .takeIf { it > 0L },
                        digest = assetObj.optString("digest").trim(),
                        signerSha256 = assetObj.optArray("signerSha256")
                            ?.mapNotNull { element ->
                                element.jsonPrimitiveOrNull()
                                    ?.contentOrNull
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                            }.orEmpty()
                    )
                )
            }
        }
        return GitHubReleaseAssetBundle(
            releaseName = obj.optString("releaseName").trim(),
            tagName = tagName,
            htmlUrl = obj.optString("htmlUrl").trim(),
            releaseUpdatedAtMillis = obj.optLong("releaseUpdatedAtMillis", 0L).takeIf { it > 0L },
            releaseNotesBody = obj.optString("releaseNotesBody").trim(),
            assets = assets,
            showingAllAssets = obj.optBoolean("showingAllAssets", false),
            shortCommitSha = obj.optString("shortCommitSha").trim(),
            fetchSource = obj.optString("fetchSource").trim(),
            sourceConfigSignature = obj.optString("sourceConfigSignature").trim(),
            isPreRelease = (obj["isPreRelease"] as? JsonPrimitive)?.booleanOrNull,
        )
    }

    private fun sha1(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(text.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
