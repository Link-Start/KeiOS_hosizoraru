package os.kei.ui.page.main.student

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import os.kei.core.json.KeiJson
import os.kei.core.json.decodeFromStringOrNull
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

private const val ROOT_DIR_NAME = "ba_student_guide_detail_cache"
private const val VERSION_DIR_NAME = "v1"
private const val INDEX_FILE_NAME = "index.json"
private const val META_FILE_NAME = "meta.json"

@Serializable
private data class BaGuideStudentDetailCacheIndex(
    val sourceUrls: List<String> = emptyList(),
)

internal data class BaGuideStudentDetailCacheStats(
    val totalCount: Int = 0,
    val studentCount: Int = 0,
    val hotUpdateCount: Int = 0,
    val longTermCount: Int = 0,
    val archivedCount: Int = 0,
    val latestCachedAtMs: Long = 0L,
    val latestValidatedAtMs: Long = 0L,
    val nextAutoRefreshAtMs: Long = 0L,
) {
    companion object {
        val Empty = BaGuideStudentDetailCacheStats()
    }
}

internal class BaGuideStudentDetailFileCacheStore(
    private val rootDir: File,
) {
    private val lock = Any()

    fun loadMeta(sourceUrl: String): BaGuideStudentDetailCacheMeta? =
        synchronized(lock) {
            val source = normalizeStudentGuideSourceUrl(sourceUrl)
            if (source.isBlank()) return@synchronized null
            readMetaLocked(source)
        }

    fun loadMetaByContentId(contentId: Long): BaGuideStudentDetailCacheMeta? {
        if (contentId <= 0L) return null
        return allMetas()
            .asSequence()
            .filter { meta -> meta.tab == BaGuideCatalogTab.Student && meta.contentId == contentId }
            .maxByOrNull { meta -> maxOf(meta.lastValidatedAtMs, meta.cachedAtMs) }
    }

    fun saveMeta(meta: BaGuideStudentDetailCacheMeta) {
        synchronized(lock) {
            val source = normalizeStudentGuideSourceUrl(meta.sourceUrl)
            if (source.isBlank()) return
            val normalized = meta.copy(sourceUrl = source)
            val dir = entryDir(source).apply { mkdirs() }
            writeTextAtomic(
                target = File(dir, META_FILE_NAME),
                text = KeiJson.lenient.encodeToString(normalized),
            )
            val index = readIndexLocked().toMutableSet()
            index += source
            writeIndexLocked(index)
        }
    }

    fun remove(sourceUrl: String) {
        synchronized(lock) {
            val source = normalizeStudentGuideSourceUrl(sourceUrl)
            if (source.isBlank()) return
            runCatching { entryDir(source).deleteRecursively() }
            val index = readIndexLocked().toMutableSet()
            if (index.remove(source)) {
                writeIndexLocked(index)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            runCatching { cacheRootDir().deleteRecursively() }
        }
    }

    fun storageBytes(): Long =
        synchronized(lock) {
            cacheRootDir().totalFileBytes()
        }

    fun allMetas(): List<BaGuideStudentDetailCacheMeta> =
        synchronized(lock) {
            allMetasLocked()
        }

    fun removeOrphanStudentMetas(activeStudentSourceUrlsByContentId: Map<Long, Set<String>>): List<BaGuideStudentDetailCacheMeta> =
        synchronized(lock) {
            if (activeStudentSourceUrlsByContentId.isEmpty()) return@synchronized emptyList()
            val activeSourcesByContentId =
                activeStudentSourceUrlsByContentId
                    .mapValues { (_, urls) ->
                        urls
                            .asSequence()
                            .map(::normalizeStudentGuideSourceUrl)
                            .filter { it.isNotBlank() }
                            .toSet()
                    }.filter { (contentId, urls) -> contentId > 0L && urls.isNotEmpty() }
            if (activeSourcesByContentId.isEmpty()) return@synchronized emptyList()
            val removed =
                allMetasLocked()
                    .filter { meta ->
                        meta.tab == BaGuideCatalogTab.Student &&
                            activeSourcesByContentId[meta.contentId]?.contains(meta.sourceUrl) != true
                    }
            if (removed.isEmpty()) return@synchronized emptyList()
            val index = readIndexLocked().toMutableSet()
            removed.forEach { meta ->
                runCatching { entryDir(meta.sourceUrl).deleteRecursively() }
                index.remove(meta.sourceUrl)
            }
            writeIndexLocked(index)
            removed
        }

    fun stats(): BaGuideStudentDetailCacheStats {
        val metas = allMetas()
        if (metas.isEmpty()) return BaGuideStudentDetailCacheStats.Empty
        val studentMetas = metas.filter { it.tab == BaGuideCatalogTab.Student }
        return BaGuideStudentDetailCacheStats(
            totalCount = metas.size,
            studentCount = studentMetas.size,
            hotUpdateCount = studentMetas.count { it.freshnessTier == BaGuideStudentDetailFreshnessTier.HotUpdate },
            longTermCount = studentMetas.count { it.freshnessTier == BaGuideStudentDetailFreshnessTier.LongTerm },
            archivedCount = studentMetas.count { it.freshnessTier == BaGuideStudentDetailFreshnessTier.Archived },
            latestCachedAtMs = metas.maxOfOrNull { it.cachedAtMs } ?: 0L,
            latestValidatedAtMs = metas.maxOfOrNull { it.lastValidatedAtMs } ?: 0L,
            nextAutoRefreshAtMs =
                studentMetas
                    .map { it.nextAutoRefreshAtMs }
                    .filter { it > 0L }
                    .minOrNull()
                    ?: 0L,
        )
    }

    private fun readMetaLocked(sourceUrl: String): BaGuideStudentDetailCacheMeta? {
        val raw = File(entryDir(sourceUrl), META_FILE_NAME).readTextOrNull().orEmpty()
        val meta = KeiJson.lenient.decodeFromStringOrNull<BaGuideStudentDetailCacheMeta>(raw) ?: return null
        return meta.takeIf { normalizeStudentGuideSourceUrl(it.sourceUrl) == sourceUrl }
    }

    private fun allMetasLocked(): List<BaGuideStudentDetailCacheMeta> =
        readIndexLocked()
            .mapNotNull(::readMetaLocked)
            .ifEmpty {
                cacheRootDir()
                    .listFiles()
                    .orEmpty()
                    .asSequence()
                    .filter { file -> file.isDirectory }
                    .mapNotNull { dir ->
                        val raw = File(dir, META_FILE_NAME).readTextOrNull()
                        KeiJson.lenient.decodeFromStringOrNull<BaGuideStudentDetailCacheMeta>(raw.orEmpty())
                    }.toList()
            }

    private fun readIndexLocked(): Set<String> {
        val raw = File(cacheRootDir(), INDEX_FILE_NAME).readTextOrNull().orEmpty()
        val index = KeiJson.lenient.decodeFromStringOrNull<BaGuideStudentDetailCacheIndex>(raw)
        return index
            ?.sourceUrls
            .orEmpty()
            .asSequence()
            .map(::normalizeStudentGuideSourceUrl)
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun writeIndexLocked(index: Set<String>) {
        val root = cacheRootDir().apply { mkdirs() }
        val normalized =
            index
                .map(::normalizeStudentGuideSourceUrl)
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        if (normalized.isEmpty()) {
            File(root, INDEX_FILE_NAME).delete()
            return
        }
        writeTextAtomic(
            target = File(root, INDEX_FILE_NAME),
            text = KeiJson.lenient.encodeToString(BaGuideStudentDetailCacheIndex(normalized)),
        )
    }

    private fun cacheRootDir(): File = File(rootDir, "$ROOT_DIR_NAME/$VERSION_DIR_NAME")

    private fun entryDir(sourceUrl: String): File = File(cacheRootDir(), baGuideStudentDetailCacheId(sourceUrl))
}

internal fun baGuideStudentDetailCacheStore(context: Context): BaGuideStudentDetailFileCacheStore =
    BaGuideStudentDetailFileCacheStore(context.filesDir)

internal fun buildBaGuideStudentDetailCacheMetaFromInfo(
    info: BaStudentGuideInfo,
    contentId: Long,
    tab: BaGuideCatalogTab,
    catalogCreatedAtSec: Long,
    releaseDateSec: Long,
    previous: BaGuideStudentDetailCacheMeta?,
    nowMs: Long = System.currentTimeMillis(),
): BaGuideStudentDetailCacheMeta? {
    val source = normalizeStudentGuideSourceUrl(info.sourceUrl)
    if (source.isBlank() || contentId <= 0L) return null
    val hasRemoteAgeSignal = catalogCreatedAtSec > 0L || releaseDateSec > 0L
    val firstSeenAtMs =
        previous?.firstSeenAtMs?.takeIf { it > 0L }
            ?: if (hasRemoteAgeSignal) {
                0L
            } else {
                nowMs.coerceAtLeast(1L)
            }
    val contentHash = baGuideStudentDetailContentHash(info)
    val unchangedPrevious = previous?.takeIf { it.contentHash == contentHash }
    val unchangedValidationCount =
        if (unchangedPrevious != null) {
            unchangedPrevious.unchangedValidationCount + 1
        } else {
            0
        }
    val tier =
        resolveBaGuideStudentDetailFreshnessTier(
            tab = tab,
            catalogCreatedAtSec = catalogCreatedAtSec,
            releaseDateSec = releaseDateSec,
            firstSeenAtMs = firstSeenAtMs,
            unchangedValidationCount = unchangedValidationCount,
            nowMs = nowMs,
        ) ?: return null
    val validatedAtMs = nowMs.coerceAtLeast(info.syncedAtMs.coerceAtLeast(1L))
    val cachedAtMs =
        if (unchangedPrevious != null) {
            unchangedPrevious.cachedAtMs
        } else {
            info.syncedAtMs.takeIf { it > 0L } ?: validatedAtMs
        }
    return BaGuideStudentDetailCacheMeta(
        sourceUrl = source,
        contentId = contentId,
        tab = tab,
        catalogCreatedAtSec = catalogCreatedAtSec.coerceAtLeast(0L),
        releaseDateSec = releaseDateSec.coerceAtLeast(0L),
        firstSeenAtMs = firstSeenAtMs,
        cachedAtMs = cachedAtMs,
        lastValidatedAtMs = validatedAtMs,
        lastChangedAtMs = unchangedPrevious?.lastChangedAtMs ?: cachedAtMs,
        nextAutoRefreshAtMs = validatedAtMs + tier.validationIntervalMs,
        freshnessTier = tier,
        contentHash = contentHash,
        unchangedValidationCount = unchangedValidationCount,
        failureCount = 0,
        lastFailureAtMs = 0L,
        nextRetryAtMs = 0L,
    )
}

internal fun alignBaGuideStudentDetailCacheMetaWithCatalog(
    meta: BaGuideStudentDetailCacheMeta,
    sourceUrl: String,
    catalogCreatedAtSec: Long,
    releaseDateSec: Long,
    nowMs: Long = System.currentTimeMillis(),
): BaGuideStudentDetailCacheMeta {
    if (meta.tab != BaGuideCatalogTab.Student) return meta
    val source = normalizeStudentGuideSourceUrl(sourceUrl).ifBlank { meta.sourceUrl }
    val alignedCatalogCreatedAtSec =
        catalogCreatedAtSec
            .takeIf { it > 0L }
            ?: meta.catalogCreatedAtSec.coerceAtLeast(0L)
    val alignedReleaseDateSec =
        releaseDateSec
            .takeIf { it > 0L }
            ?: meta.releaseDateSec.coerceAtLeast(0L)
    val tier =
        resolveBaGuideStudentDetailFreshnessTier(
            tab = meta.tab,
            catalogCreatedAtSec = alignedCatalogCreatedAtSec,
            releaseDateSec = alignedReleaseDateSec,
            firstSeenAtMs = meta.firstSeenAtMs,
            unchangedValidationCount = meta.unchangedValidationCount,
            nowMs = nowMs,
        ) ?: meta.freshnessTier
    val nextAutoRefreshAtMs =
        if (meta.lastValidatedAtMs > 0L) {
            meta.lastValidatedAtMs + tier.validationIntervalMs
        } else {
            meta.nextAutoRefreshAtMs
        }
    return meta.copy(
        sourceUrl = source,
        catalogCreatedAtSec = alignedCatalogCreatedAtSec,
        releaseDateSec = alignedReleaseDateSec,
        nextAutoRefreshAtMs = nextAutoRefreshAtMs,
        freshnessTier = tier,
    )
}

internal fun baGuideStudentDetailContentHash(info: BaStudentGuideInfo): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateString(info.title)
    digest.updateString(info.subtitle)
    digest.updateString(info.description)
    digest.updateString(info.imageUrl)
    digest.updateString(info.summary)
    info.stats.forEach { (key, value) ->
        digest.updateString(key)
        digest.updateString(value)
    }
    info.skillRows.forEach { digest.updateGuideRow(it) }
    info.profileRows.forEach { digest.updateGuideRow(it) }
    info.galleryItems.forEach { item ->
        digest.updateString(item.title)
        digest.updateString(item.imageUrl)
        digest.updateString(item.mediaType)
        digest.updateString(item.mediaUrl)
        digest.updateString(item.memoryUnlockLevel)
        digest.updateString(item.note)
        digest.updateString(item.webMemoryLobby?.toJson()?.toString().orEmpty())
    }
    info.growthRows.forEach { digest.updateGuideRow(it) }
    info.simulateRows.forEach { digest.updateGuideRow(it) }
    info.voiceRows.forEach { digest.updateGuideRow(it) }
    digest.updateString(info.voiceCvJp)
    digest.updateString(info.voiceCvCn)
    info.voiceCvByLanguage.toSortedMap().forEach { (key, value) ->
        digest.updateString(key)
        digest.updateString(value)
    }
    info.voiceLanguageHeaders.forEach(digest::updateString)
    info.voiceEntries.forEach { entry ->
        digest.updateString(entry.section)
        digest.updateString(entry.title)
        entry.lineHeaders.forEach(digest::updateString)
        entry.lines.forEach(digest::updateString)
        entry.audioUrls.forEach(digest::updateString)
        digest.updateString(entry.audioUrl)
    }
    digest.updateString(info.tabSkillIconUrl)
    digest.updateString(info.tabProfileIconUrl)
    digest.updateString(info.tabVoiceIconUrl)
    digest.updateString(info.tabGalleryIconUrl)
    digest.updateString(info.tabSimulateIconUrl)
    return digest.digest().joinToString(separator = "") { byte ->
        String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
    }
}

private fun baGuideStudentDetailCacheId(sourceUrl: String): String {
    val digest = MessageDigest.getInstance("SHA-1")
    return digest.digest(sourceUrl.toByteArray(StandardCharsets.UTF_8)).joinToString(separator = "") { byte ->
        String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
    }
}

private fun MessageDigest.updateGuideRow(row: BaGuideRow) {
    updateString(row.key)
    updateString(row.value)
    updateString(row.imageUrl)
    row.imageUrls.forEach(::updateString)
}

private fun MessageDigest.updateString(value: String) {
    update(value.toByteArray(StandardCharsets.UTF_8))
    update(0)
}

private fun File.readTextOrNull(): String? =
    runCatching {
        if (isFile) readText(Charsets.UTF_8) else null
    }.getOrNull()

private fun File.totalFileBytes(): Long =
    runCatching {
        if (!exists()) {
            0L
        } else {
            walkTopDown()
                .filter { file -> file.isFile }
                .sumOf { file -> file.length().coerceAtLeast(0L) }
        }
    }.getOrDefault(0L)

private fun writeTextAtomic(
    target: File,
    text: String,
) {
    val parent = target.parentFile ?: return
    parent.mkdirs()
    val temp = File(parent, "${target.name}.tmp")
    temp.writeText(text, Charsets.UTF_8)
    if (!temp.renameTo(target)) {
        temp.copyTo(target, overwrite = true)
        temp.delete()
    }
}
