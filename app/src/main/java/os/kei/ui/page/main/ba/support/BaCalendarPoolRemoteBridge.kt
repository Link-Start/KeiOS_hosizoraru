package os.kei.ui.page.main.ba.support

import org.json.JSONObject
import os.kei.feature.ba.data.remote.GameKeeRepository

internal enum class BaCalendarPoolRemoteSource {
    CalendarApi,
    PoolAllApi
}

internal data class BaCalendarPoolRemoteResult<T>(
    val entries: List<T>,
    val source: BaCalendarPoolRemoteSource,
    val fetchedAtMs: Long,
    val sourceErrors: List<String> = emptyList()
) {
    val errorSummary: String
        get() = sourceErrors.joinToString(" | ").trim()
}

internal fun fetchBaCalendarRemoteResult(
    serverIndex: Int,
    nowMs: Long = System.currentTimeMillis(),
    fetchJson: (pathOrUrl: String, refererPath: String) -> String = { pathOrUrl, refererPath ->
        GameKeeRepository.fetchBaApiJson(
            pathOrUrl = pathOrUrl,
            refererPath = refererPath
        )
    }
): BaCalendarPoolRemoteResult<BaCalendarEntry> {
    val serverId = gameKeeServerId(serverIndex)
    val endpointPath =
        "$BA_CALENDAR_ENDPOINT?importance=0&sort=-1&keyword=&limit=999&page_no=1&serverId=$serverId&status=0"
    val body = runWithRetry {
        fetchJson(endpointPath, "/ba/huodong/$serverId")
    }
    return BaCalendarPoolRemoteResult(
        entries = parseBaCalendarEntriesFromApiBody(body, nowMs),
        source = BaCalendarPoolRemoteSource.CalendarApi,
        fetchedAtMs = nowMs
    )
}

internal fun fetchBaPoolRemoteResult(
    serverIndex: Int,
    nowMs: Long = System.currentTimeMillis(),
    fetchJson: (pathOrUrl: String, refererPath: String) -> String = { pathOrUrl, refererPath ->
        GameKeeRepository.fetchBaApiJson(
            pathOrUrl = pathOrUrl,
            refererPath = refererPath
        )
    }
): BaCalendarPoolRemoteResult<BaPoolEntry> {
    val merged = mutableMapOf<Int, BaPoolEntry>()
    val sourceErrors = mutableListOf<String>()
    runCatching {
        fetchBaPoolEntriesFromAllRemote(
            serverIndex = serverIndex,
            nowMs = nowMs,
            fetchJson = fetchJson
        )
    }.fold(
        onSuccess = { result ->
            result.entries.forEach { entry ->
                val existing = merged[entry.id]
                if (
                    existing == null ||
                    (existing.endAtMs <= nowMs && entry.endAtMs > nowMs) ||
                    entry.endAtMs > existing.endAtMs ||
                    entry.startAtMs > existing.startAtMs
                ) {
                    merged[entry.id] = entry
                }
            }
        },
        onFailure = { error ->
            sourceErrors += error.message.orEmpty().ifBlank { error.javaClass.simpleName }
        }
    )
    if (merged.isEmpty() && sourceErrors.isNotEmpty()) {
        error("pool all sources failed: ${sourceErrors.joinToString(" | ")}")
    }
    return BaCalendarPoolRemoteResult(
        entries = normalizeBaPoolEntries(merged.values.toList(), nowMs),
        source = BaCalendarPoolRemoteSource.PoolAllApi,
        fetchedAtMs = nowMs,
        sourceErrors = sourceErrors
    )
}

internal fun fetchBaPoolEntriesFromAllRemote(
    serverIndex: Int,
    nowMs: Long,
    fetchJson: (pathOrUrl: String, refererPath: String) -> String = { pathOrUrl, refererPath ->
        GameKeeRepository.fetchBaApiJson(
            pathOrUrl = pathOrUrl,
            refererPath = refererPath
        )
    }
): BaCalendarPoolRemoteResult<BaPoolEntry> {
    val serverId = gameKeeServerId(serverIndex)
    val endpointPath =
        "$BA_POOL_ENDPOINT?order_by=-1&card_tag_id=&keyword=&kind_id=6&status=0&serverId=$serverId"
    val body = runWithRetry {
        fetchJson(endpointPath, "/ba/kachi/$serverId")
    }
    return BaCalendarPoolRemoteResult(
        entries = parseBaPoolEntriesFromAllApiBody(body, nowMs),
        source = BaCalendarPoolRemoteSource.PoolAllApi,
        fetchedAtMs = nowMs
    )
}

internal fun parseBaCalendarEntriesFromApiBody(
    body: String,
    nowMs: Long = System.currentTimeMillis()
): List<BaCalendarEntry> {
    val root = JSONObject(body)
    if (root.optInt("code", -1) != 0) {
        error("calendar api code=${root.optInt("code", -1)}")
    }
    val data = root.optJSONArray("data") ?: return emptyList()
    val entries = mutableListOf<BaCalendarEntry>()
    for (index in 0 until data.length()) {
        val item = data.optJSONObject(index) ?: continue
        val title = item.optString("title").trim()
        if (title.isBlank()) continue

        val beginSec = item.optLong("begin_at", 0L)
        val endSec = item.optLong("end_at", 0L)
        if (beginSec <= 0L || endSec <= 0L) continue
        val beginAtMs = beginSec * 1000L
        val endAtMs = endSec * 1000L

        entries += BaCalendarEntry(
            id = item.optInt("id", 0),
            title = title,
            kindId = item.optInt("activity_kind_id", 31),
            kindName = normalizeBaCalendarKindFallback(item.optString("activity_kind_name")),
            beginAtMs = beginAtMs,
            endAtMs = endAtMs,
            linkUrl = normalizeGameKeeLink(item.optString("link_url")),
            imageUrl = extractGameKeeImageLink(item),
            isRunning = nowMs in beginAtMs until endAtMs
        )
    }
    return normalizeBaCalendarEntries(entries, nowMs)
}

internal fun parseBaPoolEntriesFromAllApiBody(
    body: String,
    nowMs: Long = System.currentTimeMillis()
): List<BaPoolEntry> {
    val root = JSONObject(body)
    if (root.optInt("code", -1) != 0) {
        error("pool api code=${root.optInt("code", -1)}")
    }
    val data = root.optJSONArray("data") ?: return emptyList()
    val entries = mutableListOf<BaPoolEntry>()
    for (index in 0 until data.length()) {
        val item = data.optJSONObject(index) ?: continue
        val name = item.optString("name").trim()
        if (name.isBlank()) continue

        val startSec = item.optLong("start_at", 0L)
        val endSec = item.optLong("end_at", 0L)
        if (startSec <= 0L || endSec <= 0L) continue
        val startAtMs = startSec * 1000L
        val endAtMs = endSec * 1000L
        val isRunning = nowMs in startAtMs until endAtMs
        val isUpcoming = startAtMs > nowMs

        val knownTagId = parsePoolTagIds(item.optString("tag_id"))
            .firstOrNull { it in BA_POOL_TAG_ID_SET }
        val normalizedTagId = when {
            knownTagId != null -> knownTagId
            isRunning || isUpcoming -> BA_POOL_FALLBACK_ACTIVE_TAG_ID
            else -> null
        } ?: continue
        entries += BaPoolEntry(
            id = item.optInt("id", 0),
            name = name,
            tagId = normalizedTagId,
            tagName = "",
            startAtMs = startAtMs,
            endAtMs = endAtMs,
            linkUrl = normalizeGameKeeLink(item.optString("link_url")),
            imageUrl = extractGameKeeImageLink(item),
            isRunning = isRunning
        )
    }
    return entries
}
