package os.kei.ui.page.main.student.catalog

import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.ba.data.remote.GameKeeRepository
import os.kei.ui.page.main.student.BaGuideDataClock
import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.BaGuideSystemDataClock
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.BaStudentGuideStore
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import os.kei.ui.page.main.student.fetch.parseGuideDetailFromContentJson
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

private const val BA_GUIDE_SECOND_PAGE_ID = 23941
private const val BA_GUIDE_STUDENT_PID = 49443
private const val BA_GUIDE_NPC_SATELLITE_PID = 107619
private const val BA_GUIDE_INDEX_REFERER_PATH = "/ba/second/$BA_GUIDE_SECOND_PAGE_ID"
private const val BA_GUIDE_FILTER_INDEX_PATH_PREFIX = "/v1/entryFilter/getEntryFilter?entry_id="
private const val BA_GUIDE_RELEASE_INDEX_MAX_NETWORK_FETCH_PER_PASS = 12
private const val BA_GUIDE_RELEASE_INDEX_NETWORK_BATCH_SIZE = 3
private const val BA_GUIDE_RELEASE_INDEX_REQUEST_THROTTLE_MS = 180L
internal const val BA_GUIDE_INDEX_URL = "https://www.gamekee.com$BA_GUIDE_INDEX_REFERER_PATH"

@Volatile
private var cachedCatalogBundle: BaGuideCatalogBundle? = null

internal enum class BaGuideCatalogTab(
    val label: String,
    @param:StringRes val labelRes: Int,
    val iconRes: Int,
) {
    Student(
        label = "实装学生",
        labelRes = R.string.ba_catalog_tab_student,
        iconRes = R.drawable.ba_tab_profile,
    ),
    NpcSatellite(
        label = "NPC及卫星",
        labelRes = R.string.ba_catalog_tab_npc_satellite,
        iconRes = R.drawable.ba_tab_skill,
    ),
}

internal data class BaGuideCatalogEntry(
    val entryId: Int,
    val pid: Int,
    val contentId: Long,
    val name: String,
    val alias: String,
    val aliasDisplay: String,
    val iconUrl: String,
    val type: Int,
    val order: Int,
    val createdAtSec: Long,
    val releaseDateSec: Long = 0L,
    val releaseDateProbeAtMs: Long = 0L,
    val detailUrl: String,
    val tab: BaGuideCatalogTab,
    val filterAttributes: BaGuideCatalogEntryFilterAttributes =
        BaGuideCatalogEntryFilterAttributes.EMPTY,
)

internal data class BaGuideCatalogBundle(
    val entriesByTab: Map<BaGuideCatalogTab, List<BaGuideCatalogEntry>>,
    val syncedAtMs: Long,
    val filterDefinitionsByTab: Map<BaGuideCatalogTab, List<BaGuideCatalogFilterDefinition>> =
        emptyMap(),
    val fullSyncedAtMs: Long = syncedAtMs,
) {
    fun entries(tab: BaGuideCatalogTab): List<BaGuideCatalogEntry> = entriesByTab[tab].orEmpty()

    fun filterDefinitions(tab: BaGuideCatalogTab): List<BaGuideCatalogFilterDefinition> = filterDefinitionsByTab[tab].orEmpty()

    companion object {
        val EMPTY =
            BaGuideCatalogBundle(
                entriesByTab = BaGuideCatalogTab.entries.associateWith { emptyList() },
                syncedAtMs = 0L,
                filterDefinitionsByTab = emptyMap(),
                fullSyncedAtMs = 0L,
            )
    }
}

private data class RawEntry(
    val entryId: Int,
    val pid: Int,
    val contentId: Long,
    val name: String,
    val alias: String,
    val iconUrl: String,
    val type: Int,
    val order: Int,
    val createdAtSec: Long,
)

private data class CatalogReleaseDatePatch(
    val releaseDateSecByContentId: Map<Long, Long> = emptyMap(),
    val probeAtMsByContentId: Map<Long, Long> = emptyMap(),
) {
    fun isEmpty(): Boolean = releaseDateSecByContentId.isEmpty() && probeAtMsByContentId.isEmpty()
}

private data class CatalogReleaseDateProbeResult(
    val contentId: Long,
    val probeAtMs: Long,
    val releaseDateSec: Long,
)

internal fun loadCachedBaGuideCatalogBundle(): BaGuideCatalogBundle? {
    val memory = cachedCatalogBundle
    if (memory != null) return memory
    val persisted = BaGuideCatalogStore.loadBundle() ?: return null
    cachedCatalogBundle = persisted
    return persisted
}

internal fun isBaGuideCatalogBundleComplete(bundle: BaGuideCatalogBundle?): Boolean {
    bundle ?: return false
    return BaGuideCatalogTab.entries.all { tab ->
        val entries = bundle.entries(tab)
        val filterDefinitions = bundle.filterDefinitions(tab)
        entries.isNotEmpty() &&
            filterDefinitions.isNotEmpty() &&
            entries.all { entry ->
                entry.contentId > 0L &&
                    entry.name.isNotBlank() &&
                    entry.detailUrl.isNotBlank()
            }
    }
}

internal fun clearBaGuideCatalogCache(context: Context? = null) {
    cachedCatalogBundle = null
    BaGuideCatalogStore.clearCache()
    BaGuideCatalogIconCache.clear(context)
}

internal suspend fun fetchBaGuideCatalogBundle(
    forceRefresh: Boolean = false,
    networkDispatcher: CoroutineDispatcher = AppDispatchers.baFetch,
    parseDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
    clock: BaGuideDataClock = BaGuideSystemDataClock,
    refreshMode: BaGuideCatalogRefreshMode = BaGuideCatalogRefreshMode.Full,
): BaGuideCatalogBundle {
    if (!forceRefresh) {
        val cached =
            withContext(networkDispatcher) {
                loadCachedBaGuideCatalogBundle()
            }
        if (cached != null && isBaGuideCatalogBundleComplete(cached)) {
            return cached
        }
    }
    val now = clock.nowMs()
    val releaseDateIndex =
        withContext(networkDispatcher) {
            BaGuideCatalogStore.loadReleaseDateIndexSnapshot()
        }
    val cachedFilterBundle =
        withContext(networkDispatcher) {
            loadCachedBaGuideCatalogBundle()
        }

    val (studentRaw, npcSatelliteRaw) =
        coroutineScope {
            val studentDeferred = async(networkDispatcher) { fetchRawEntriesByPid(BA_GUIDE_STUDENT_PID) }
            val npcSatelliteDeferred =
                async(networkDispatcher) {
                    fetchRawEntriesByPid(BA_GUIDE_NPC_SATELLITE_PID)
                }
            studentDeferred.await() to npcSatelliteDeferred.await()
        }
    val rawByTab =
        mapOf(
            BaGuideCatalogTab.Student to studentRaw,
            BaGuideCatalogTab.NpcSatellite to npcSatelliteRaw,
        )
    val fullRefresh = refreshMode == BaGuideCatalogRefreshMode.Full || !isBaGuideCatalogBundleComplete(cachedFilterBundle)
    val changedTabs =
        BaGuideCatalogTab.entries.filter { tab ->
            fullRefresh || cachedFilterBundle.rawChanged(tab = tab, rawEntries = rawByTab.getValue(tab))
        }
    if (!fullRefresh && changedTabs.isEmpty() && cachedFilterBundle != null) {
        val bundle = cachedFilterBundle.copy(syncedAtMs = now)
        cachedCatalogBundle = bundle
        withContext(networkDispatcher) {
            BaGuideCatalogStore.saveBundle(bundle)
        }
        return bundle
    }

    val filterIndexByTab = fetchFilterIndices(changedTabs, networkDispatcher)

    val entriesByTab =
        withContext(parseDispatcher) {
            BaGuideCatalogTab.entries.associateWith { tab ->
                if (!changedTabs.contains(tab)) {
                    cachedFilterBundle?.entries(tab).orEmpty()
                } else {
                    val filterIndex = filterIndexByTab[tab] ?: BaGuideCatalogFilterIndex.EMPTY
                    val filterFallback = cachedFilterBundle.filterFallback(tab)
                    rawByTab.getValue(tab).map { raw ->
                        val filterAttributes =
                            filterIndex
                                .attributes(raw.entryId)
                                .ifEmpty { filterFallback.attributes(raw) }
                        val filterReleaseDateSec =
                            filterIndex.releaseDateSec(raw.entryId).takeIf { it > 0L }
                                ?: filterAttributes.releaseDateSec()
                        raw.toCatalogEntry(
                            tab = tab,
                            releaseDateSec = releaseDateIndex[raw.contentId] ?: filterReleaseDateSec,
                            filterAttributes = filterAttributes,
                        )
                    }
                }
            }
        }

    val bundle =
        BaGuideCatalogBundle(
            entriesByTab = entriesByTab,
            syncedAtMs = now,
            filterDefinitionsByTab =
                BaGuideCatalogTab.entries.associateWith { tab ->
                    filterIndexByTab[tab]?.definitions.orEmpty().ifEmpty {
                        cachedFilterBundle?.filterDefinitions(tab).orEmpty()
                    }
                },
            fullSyncedAtMs =
                if (refreshMode == BaGuideCatalogRefreshMode.Full) {
                    now
                } else {
                    cachedFilterBundle?.fullSyncedAtMs?.takeIf { it > 0L }
                        ?: cachedFilterBundle?.syncedAtMs?.takeIf { it > 0L }
                        ?: now
                },
        )
    cachedCatalogBundle = bundle
    withContext(networkDispatcher) {
        BaGuideCatalogStore.saveBundle(bundle)
    }
    return bundle
}

private suspend fun fetchFilterIndices(
    tabs: List<BaGuideCatalogTab>,
    networkDispatcher: CoroutineDispatcher,
): Map<BaGuideCatalogTab, BaGuideCatalogFilterIndex> {
    if (tabs.isEmpty()) return emptyMap()
    return coroutineScope {
        tabs
            .distinct()
            .associateWith { tab ->
                async(networkDispatcher) { fetchFilterIndex(tab.pid) }
            }.mapValues { (_, deferred) -> deferred.await() }
    }
}

private data class CatalogFilterFallback(
    val byEntryId: Map<Int, BaGuideCatalogEntryFilterAttributes>,
    val byContentId: Map<Long, BaGuideCatalogEntryFilterAttributes>,
) {
    fun attributes(raw: RawEntry): BaGuideCatalogEntryFilterAttributes =
        byEntryId[raw.entryId]
            ?: byContentId[raw.contentId]
            ?: BaGuideCatalogEntryFilterAttributes.EMPTY
}

private fun BaGuideCatalogBundle?.filterFallback(tab: BaGuideCatalogTab): CatalogFilterFallback {
    val entries = this?.entries(tab).orEmpty()
    return CatalogFilterFallback(
        byEntryId =
            entries
                .filter { entry -> entry.entryId > 0 && !entry.filterAttributes.isEmpty() }
                .associate { entry -> entry.entryId to entry.filterAttributes },
        byContentId =
            entries
                .filter { entry -> entry.contentId > 0L && !entry.filterAttributes.isEmpty() }
                .associate { entry -> entry.contentId to entry.filterAttributes },
    )
}

private val BaGuideCatalogTab.pid: Int
    get() =
        when (this) {
            BaGuideCatalogTab.Student -> BA_GUIDE_STUDENT_PID
            BaGuideCatalogTab.NpcSatellite -> BA_GUIDE_NPC_SATELLITE_PID
        }

private fun BaGuideCatalogBundle?.rawChanged(
    tab: BaGuideCatalogTab,
    rawEntries: List<RawEntry>,
): Boolean {
    val currentEntries = this?.entries(tab).orEmpty()
    if (currentEntries.size != rawEntries.size) return true
    return currentEntries.zip(rawEntries).any { (entry, raw) -> entry.rawChanged(raw) }
}

private fun BaGuideCatalogEntry.rawChanged(raw: RawEntry): Boolean =
    entryId != raw.entryId ||
        pid != raw.pid ||
        contentId != raw.contentId ||
        name != raw.name ||
        alias != raw.alias ||
        iconUrl != raw.iconUrl ||
        type != raw.type ||
        order != raw.order ||
        createdAtSec != raw.createdAtSec

private fun BaGuideCatalogEntryFilterAttributes.ifEmpty(
    fallback: () -> BaGuideCatalogEntryFilterAttributes,
): BaGuideCatalogEntryFilterAttributes =
    if (isEmpty()) fallback() else this

private fun BaGuideCatalogEntryFilterAttributes.isEmpty(): Boolean =
    optionIdsByFilterId.isEmpty() && numericValueByFilterId.isEmpty()

private fun BaGuideCatalogEntryFilterAttributes.releaseDateSec(): Long {
    val raw = numericValue(BA_GUIDE_FILTER_ID_RELEASE_DATE)
    return when {
        raw >= 1_000_000_000_000L -> raw / 1000L
        raw > 0L -> raw
        else -> 0L
    }
}

internal suspend fun hydrateBaGuideCatalogReleaseDateIndex(
    source: BaGuideCatalogBundle,
    maxNetworkFetchPerPass: Int = BA_GUIDE_RELEASE_INDEX_MAX_NETWORK_FETCH_PER_PASS,
    networkBatchSize: Int = BA_GUIDE_RELEASE_INDEX_NETWORK_BATCH_SIZE,
    requestThrottleMs: Long = BA_GUIDE_RELEASE_INDEX_REQUEST_THROTTLE_MS,
    networkDispatcher: CoroutineDispatcher = AppDispatchers.baFetch,
    parseDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
    clock: BaGuideDataClock = BaGuideSystemDataClock,
    onBundleUpdated: (BaGuideCatalogBundle) -> Unit = {},
): BaGuideCatalogBundle {
    if (source.entriesByTab.values.all { it.isEmpty() }) return source
    var working = source

    val localPatch =
        withContext(networkDispatcher) {
            collectReleaseDatePatchFromGuideCache(working)
        }
    if (!localPatch.isEmpty()) {
        val updated =
            withContext(networkDispatcher) {
                applyAndPersistReleaseDatePatch(working, localPatch)
            }
        if (updated !== working) {
            working = updated
            onBundleUpdated(working)
        }
    }

    var remainingFetch = maxNetworkFetchPerPass.coerceAtLeast(0)
    val batchLimit = networkBatchSize.coerceAtLeast(1)
    while (remainingFetch > 0) {
        val candidates =
            working.pendingReleaseDateProbeCandidates(
                limit = minOf(batchLimit, remainingFetch),
            )
        if (candidates.isEmpty()) break
        val networkPatch =
            collectReleaseDatePatchFromNetwork(
                entries = candidates,
                requestThrottleMs = requestThrottleMs,
                networkDispatcher = networkDispatcher,
                parseDispatcher = parseDispatcher,
                clock = clock,
            )
        remainingFetch -= candidates.size
        if (networkPatch.isEmpty()) continue
        val hasReleaseDateUpdates = networkPatch.releaseDateSecByContentId.isNotEmpty()
        val updated =
            withContext(networkDispatcher) {
                applyAndPersistReleaseDatePatch(working, networkPatch)
            }
        if (updated !== working) {
            working = updated
            if (hasReleaseDateUpdates) {
                onBundleUpdated(working)
            }
        }
    }

    return working
}

internal fun List<BaGuideCatalogEntry>.filterByQuery(query: String): List<BaGuideCatalogEntry> {
    val keyword = query.trim().lowercase(Locale.ROOT)
    if (keyword.isBlank()) return this
    return filter { entry ->
        entry.name.lowercase(Locale.ROOT).contains(keyword) ||
            entry.alias.lowercase(Locale.ROOT).contains(keyword) ||
            entry.contentId.toString().contains(keyword)
    }
}

private fun fetchRawEntriesByPid(pid: Int): List<RawEntry> {
    val body =
        GameKeeRepository.fetchBaCatalogTreeJson(
            pid = pid,
            refererPath = BA_GUIDE_INDEX_REFERER_PATH,
        )
    val root = JSONObject(body)
    if (root.optInt("code", -1) != 0) {
        error("catalog api code=${root.optInt("code", -1)} pid=$pid")
    }
    val data = root.optJSONArray("data") ?: return emptyList()
    val out = mutableListOf<RawEntry>()
    for (index in 0 until data.length()) {
        val item = data.optJSONObject(index) ?: continue
        val contentId = item.optLong("content_id", 0L)
        if (contentId <= 0L) continue
        val name =
            item
                .optString("name")
                .trim()
                .ifBlank { item.optString("title").trim() }
        if (name.isBlank()) continue
        out +=
            RawEntry(
                entryId = item.optInt("id", 0),
                pid = item.optInt("pid", pid),
                contentId = contentId,
                name = name,
                alias = item.optString("name_alias").trim(),
                iconUrl = normalizeCatalogImageUrl(item.optString("icon")),
                type = item.optInt("type", 0),
                order = index,
                createdAtSec = item.optLong("created_at", 0L),
            )
    }
    return out
}

private fun fetchFilterIndex(entryId: Int): BaGuideCatalogFilterIndex =
    runCatching {
        val body =
            GameKeeRepository.fetchBaApiJson(
                pathOrUrl = "$BA_GUIDE_FILTER_INDEX_PATH_PREFIX$entryId",
                refererPath = BA_GUIDE_INDEX_REFERER_PATH,
            )
        parseBaGuideCatalogFilterIndex(body)
    }.getOrElse {
        BaGuideCatalogFilterIndex.EMPTY
    }

private fun RawEntry.toCatalogEntry(
    tab: BaGuideCatalogTab,
    releaseDateSec: Long,
    filterAttributes: BaGuideCatalogEntryFilterAttributes,
): BaGuideCatalogEntry =
    BaGuideCatalogEntry(
        entryId = entryId,
        pid = pid,
        contentId = contentId,
        name = name,
        alias = alias,
        aliasDisplay = formatAliasDisplay(alias),
        iconUrl = iconUrl,
        type = type,
        order = order,
        createdAtSec = createdAtSec,
        releaseDateSec = releaseDateSec.coerceAtLeast(0L),
        releaseDateProbeAtMs = 0L,
        detailUrl = "https://www.gamekee.com/ba/tj/$contentId.html",
        tab = tab,
        filterAttributes = filterAttributes,
    )

private fun BaGuideCatalogBundle.pendingReleaseDateProbeCandidates(limit: Int): List<BaGuideCatalogEntry> {
    if (limit <= 0) return emptyList()
    return BaGuideCatalogTab.entries
        .asSequence()
        .flatMap { tab -> entries(tab).asSequence() }
        .filter { entry ->
            entry.contentId > 0L &&
                entry.detailUrl.isNotBlank() &&
                entry.releaseDateSec <= 0L &&
                entry.releaseDateProbeAtMs <= 0L
        }.take(limit)
        .toList()
}

private fun collectReleaseDatePatchFromGuideCache(bundle: BaGuideCatalogBundle): CatalogReleaseDatePatch {
    val cachedSourceUrls = BaStudentGuideStore.cachedSourceUrls()
    if (cachedSourceUrls.isEmpty()) return CatalogReleaseDatePatch()
    val normalizedCached =
        cachedSourceUrls
            .asSequence()
            .map { normalizeGuideUrl(it).trim() }
            .filter { it.isNotBlank() }
            .toSet()
    if (normalizedCached.isEmpty()) return CatalogReleaseDatePatch()

    val releaseDateUpdates = mutableMapOf<Long, Long>()
    val targets =
        bundle.entriesByTab.values
            .asSequence()
            .flatten()
            .filter { it.releaseDateSec <= 0L && it.contentId > 0L && it.detailUrl.isNotBlank() }
            .toList()

    targets.forEach { entry ->
        val normalizedUrl = normalizeGuideUrl(entry.detailUrl).trim()
        if (!normalizedCached.contains(normalizedUrl)) return@forEach
        val snapshot = BaStudentGuideStore.loadInfoSnapshot(normalizedUrl)
        val info = snapshot.info ?: return@forEach
        val releaseDateSec = extractReleaseDateSec(info)
        if (releaseDateSec > 0L) {
            releaseDateUpdates[entry.contentId] = releaseDateSec
        }
    }
    return CatalogReleaseDatePatch(releaseDateSecByContentId = releaseDateUpdates)
}

private suspend fun collectReleaseDatePatchFromNetwork(
    entries: List<BaGuideCatalogEntry>,
    requestThrottleMs: Long,
    networkDispatcher: CoroutineDispatcher,
    parseDispatcher: CoroutineDispatcher,
    clock: BaGuideDataClock,
): CatalogReleaseDatePatch {
    if (entries.isEmpty()) return CatalogReleaseDatePatch()
    val results =
        coroutineScope {
            entries
                .mapIndexed { index, entry ->
                    async {
                        if (requestThrottleMs > 0L && index > 0) {
                            delay((requestThrottleMs * index).milliseconds)
                        }
                        probeReleaseDateFromNetwork(
                            entry = entry,
                            networkDispatcher = networkDispatcher,
                            parseDispatcher = parseDispatcher,
                            clock = clock,
                        )
                    }
                }.awaitAll()
        }
    val releaseDateUpdates = mutableMapOf<Long, Long>()
    val probeUpdates = mutableMapOf<Long, Long>()
    results.forEach { result ->
        if (result.contentId <= 0L) return@forEach
        if (result.probeAtMs > 0L) {
            probeUpdates[result.contentId] = result.probeAtMs
        }
        if (result.releaseDateSec > 0L) {
            releaseDateUpdates[result.contentId] = result.releaseDateSec
        }
    }
    return CatalogReleaseDatePatch(
        releaseDateSecByContentId = releaseDateUpdates,
        probeAtMsByContentId = probeUpdates,
    )
}

private suspend fun probeReleaseDateFromNetwork(
    entry: BaGuideCatalogEntry,
    networkDispatcher: CoroutineDispatcher,
    parseDispatcher: CoroutineDispatcher,
    clock: BaGuideDataClock,
): CatalogReleaseDateProbeResult {
    val contentId = entry.contentId
    if (contentId <= 0L || entry.detailUrl.isBlank()) {
        return CatalogReleaseDateProbeResult(
            contentId = contentId,
            probeAtMs = 0L,
            releaseDateSec = 0L,
        )
    }
    val probedAtMs = clock.nowMs().coerceAtLeast(1L)
    return try {
        val contentDetail =
            withContext(networkDispatcher) {
                GameKeeRepository.fetchBaContentDetail(
                    contentId = contentId,
                    refererPath = "/ba/tj/$contentId.html",
                )
            }
        val releaseDateSec =
            withContext(parseDispatcher) {
                val detail =
                    parseGuideDetailFromContentJson(
                        raw = contentDetail.resolvedContentJson,
                        sourceUrl = entry.detailUrl,
                    )
                extractReleaseDateSec(
                    profileRows = detail.profileRows,
                    stats = detail.stats,
                )
            }
        CatalogReleaseDateProbeResult(
            contentId = contentId,
            probeAtMs = probedAtMs,
            releaseDateSec = releaseDateSec.coerceAtLeast(0L),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        CatalogReleaseDateProbeResult(
            contentId = contentId,
            probeAtMs = probedAtMs,
            releaseDateSec = 0L,
        )
    }
}

private fun applyAndPersistReleaseDatePatch(
    bundle: BaGuideCatalogBundle,
    patch: CatalogReleaseDatePatch,
): BaGuideCatalogBundle {
    val (updatedBundle, changed) = bundle.applyReleaseDatePatch(patch)
    if (!changed) return bundle
    if (patch.releaseDateSecByContentId.isNotEmpty()) {
        BaGuideCatalogStore.upsertReleaseDateIndex(patch.releaseDateSecByContentId)
    }
    cachedCatalogBundle = updatedBundle
    BaGuideCatalogStore.saveBundle(updatedBundle)
    return updatedBundle
}

private fun BaGuideCatalogBundle.applyReleaseDatePatch(patch: CatalogReleaseDatePatch): Pair<BaGuideCatalogBundle, Boolean> {
    if (patch.isEmpty()) return this to false
    var changed = false
    val updatedEntriesByTab =
        entriesByTab.mapValues { (_, entries) ->
            entries.map { entry ->
                val releaseDateSec = patch.releaseDateSecByContentId[entry.contentId] ?: entry.releaseDateSec
                val probeAtMs = patch.probeAtMsByContentId[entry.contentId] ?: entry.releaseDateProbeAtMs
                if (releaseDateSec != entry.releaseDateSec || probeAtMs != entry.releaseDateProbeAtMs) {
                    changed = true
                    entry.copy(
                        releaseDateSec = releaseDateSec.coerceAtLeast(0L),
                        releaseDateProbeAtMs = probeAtMs.coerceAtLeast(0L),
                    )
                } else {
                    entry
                }
            }
        }
    if (!changed) return this to false
    return copy(entriesByTab = updatedEntriesByTab) to true
}

private fun extractReleaseDateSec(info: BaStudentGuideInfo): Long =
    extractReleaseDateSec(
        profileRows = info.profileRows,
        stats = info.stats,
    )

private fun extractReleaseDateSec(
    profileRows: List<BaGuideRow>,
    stats: List<Pair<String, String>>,
): Long {
    val candidates =
        sequence {
            profileRows.forEach { row ->
                if (row.key.contains("实装日期", ignoreCase = true)) {
                    yield(row.value)
                }
            }
            stats.forEach { (key, value) ->
                if (key.contains("实装日期", ignoreCase = true)) {
                    yield(value)
                }
            }
        }
    return candidates
        .map { parseReleaseDateSec(it) }
        .firstOrNull { it > 0L }
        ?: 0L
}

private fun parseReleaseDateSec(raw: String): Long {
    if (raw.isBlank()) return 0L
    val compact =
        raw
            .substringBefore("<-")
            .substringBefore("←")
            .trim()
    if (compact.isBlank()) return 0L

    val classic = RELEASE_DATE_CLASSIC_REGEX.find(compact)
    if (classic != null) {
        return releaseDateToEpochSecond(
            year = classic.groupValues.getOrNull(1)?.toIntOrNull(),
            month = classic.groupValues.getOrNull(2)?.toIntOrNull(),
            day = classic.groupValues.getOrNull(3)?.toIntOrNull(),
        )
    }

    val packed = RELEASE_DATE_PACKED_REGEX.find(compact)
    if (packed != null) {
        return releaseDateToEpochSecond(
            year = packed.groupValues.getOrNull(1)?.toIntOrNull(),
            month = packed.groupValues.getOrNull(2)?.toIntOrNull(),
            day = packed.groupValues.getOrNull(3)?.toIntOrNull(),
        )
    }
    return 0L
}

private fun releaseDateToEpochSecond(
    year: Int?,
    month: Int?,
    day: Int?,
): Long {
    if (year == null || month == null || day == null) return 0L
    if (year < 2000 || month !in 1..12 || day !in 1..31) return 0L
    return runCatching {
        LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
    }.getOrDefault(0L)
}

private val RELEASE_DATE_CLASSIC_REGEX = Regex("""(20\d{2})[^\d]{1,4}(\d{1,2})[^\d]{1,4}(\d{1,2})""")
private val RELEASE_DATE_PACKED_REGEX = Regex("""(20\d{2})(\d{2})(\d{2})""")

private fun formatAliasDisplay(alias: String): String =
    alias
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" · ")

private fun normalizeCatalogImageUrl(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    if (value.startsWith("http://", ignoreCase = true)) return value.upgradeHttpSchemeToHttps()
    if (value.startsWith("https://", ignoreCase = true)) return value
    if (value.startsWith("//")) return "https:$value"
    return if (value.startsWith("/")) {
        "https://www.gamekee.com$value"
    } else {
        "https://www.gamekee.com/$value"
    }
}

private fun String.upgradeHttpSchemeToHttps(): String = replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
