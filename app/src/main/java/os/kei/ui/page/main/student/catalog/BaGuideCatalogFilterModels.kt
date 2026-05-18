package os.kei.ui.page.main.student.catalog

import org.json.JSONArray
import org.json.JSONObject

internal const val BA_GUIDE_FILTER_ID_RELEASE_DATE = 3774
internal const val BA_GUIDE_FILTER_ID_GLOBAL_SCORE = 11543
internal const val BA_GUIDE_FILTER_ID_CN_SCORE = 13045

private val BA_GUIDE_STUDENT_FILTER_ORDER =
    listOf(
        68,
        508,
        72,
        1608,
        167,
        177,
        183,
        188,
        199,
        204,
        212,
        11520,
        218,
        514,
        BA_GUIDE_FILTER_ID_GLOBAL_SCORE,
        BA_GUIDE_FILTER_ID_CN_SCORE,
        BA_GUIDE_FILTER_ID_RELEASE_DATE,
    )

internal data class BaGuideCatalogFilterOption(
    val id: Int,
    val name: String,
    val iconUrl: String = "",
)

internal data class BaGuideCatalogFilterDefinition(
    val id: Int,
    val name: String,
    val type: Int,
    val options: List<BaGuideCatalogFilterOption>,
) {
    val optionRankById: Map<Int, Int> =
        options
            .mapIndexed { index, option -> option.id to (options.size - index) }
            .toMap()

    fun optionLabel(optionId: Int): String = options.firstOrNull { it.id == optionId }?.name.orEmpty()
}

internal data class BaGuideCatalogEntryFilterAttributes(
    val optionIdsByFilterId: Map<Int, Set<Int>> = emptyMap(),
    val numericValueByFilterId: Map<Int, Long> = emptyMap(),
) {
    fun optionIds(filterId: Int): Set<Int> = optionIdsByFilterId[filterId].orEmpty()

    fun numericValue(filterId: Int): Long = numericValueByFilterId[filterId] ?: 0L

    fun matches(
        filterId: Int,
        selectedOptionIds: Set<Int>,
    ): Boolean {
        if (selectedOptionIds.isEmpty()) return true
        return optionIds(filterId).any { it in selectedOptionIds }
    }

    fun scoreRank(definition: BaGuideCatalogFilterDefinition?): Int {
        definition ?: return 0
        return optionIds(definition.id)
            .mapNotNull { definition.optionRankById[it] }
            .maxOrNull()
            ?: 0
    }

    fun toJson(): JSONObject =
        JSONObject().apply {
            put(
                "options",
                JSONObject().apply {
                    optionIdsByFilterId.forEach { (filterId, optionIds) ->
                        put(
                            filterId.toString(),
                            JSONArray().apply {
                                optionIds.sorted().forEach { put(it) }
                            },
                        )
                    }
                },
            )
            put(
                "numbers",
                JSONObject().apply {
                    numericValueByFilterId.forEach { (filterId, value) ->
                        put(filterId.toString(), value)
                    }
                },
            )
        }

    companion object {
        val EMPTY = BaGuideCatalogEntryFilterAttributes()

        fun fromJson(json: JSONObject?): BaGuideCatalogEntryFilterAttributes {
            json ?: return EMPTY
            val options = json.optJSONObject("options")
            val numbers = json.optJSONObject("numbers")
            return BaGuideCatalogEntryFilterAttributes(
                optionIdsByFilterId =
                    buildMap {
                        val keys = options?.keys() ?: return@buildMap
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val filterId = key.toIntOrNull() ?: continue
                            val arr = options.optJSONArray(key) ?: continue
                            val ids =
                                buildSet {
                                    for (index in 0 until arr.length()) {
                                        val id = arr.optInt(index, 0)
                                        if (id > 0) add(id)
                                    }
                                }
                            if (ids.isNotEmpty()) put(filterId, ids)
                        }
                    },
                numericValueByFilterId =
                    buildMap {
                        val keys = numbers?.keys() ?: return@buildMap
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val filterId = key.toIntOrNull() ?: continue
                            val value = numbers.optLong(key, 0L).coerceAtLeast(0L)
                            if (value > 0L) put(filterId, value)
                        }
                    },
            )
        }
    }
}

internal data class BaGuideCatalogFilterIndex(
    val definitions: List<BaGuideCatalogFilterDefinition> = emptyList(),
    val attributesByEntryId: Map<Int, BaGuideCatalogEntryFilterAttributes> = emptyMap(),
) {
    val definitionsById: Map<Int, BaGuideCatalogFilterDefinition> =
        definitions.associateBy { it.id }

    fun attributes(entryId: Int): BaGuideCatalogEntryFilterAttributes =
        attributesByEntryId[entryId] ?: BaGuideCatalogEntryFilterAttributes.EMPTY

    fun releaseDateSec(entryId: Int): Long {
        val raw = attributes(entryId).numericValue(BA_GUIDE_FILTER_ID_RELEASE_DATE)
        return when {
            raw >= 1_000_000_000_000L -> raw / 1000L
            raw > 0L -> raw
            else -> 0L
        }
    }

    companion object {
        val EMPTY = BaGuideCatalogFilterIndex()
    }
}

internal fun parseBaGuideCatalogFilterIndex(raw: String): BaGuideCatalogFilterIndex {
    val root = JSONObject(raw)
    if (root.optInt("code", -1) != 0) {
        error("catalog filter api code=${root.optInt("code", -1)}")
    }
    val data = root.optJSONObject("data") ?: return BaGuideCatalogFilterIndex.EMPTY
    val definitions = parseFilterDefinitions(data.optJSONArray("entry_filter"))
    val definitionTypes = definitions.associate { it.id to it.type }
    val attributes =
        parseFilterAttributes(
            rawAttributes = data.optJSONObject("entry_filter_attr"),
            definitionTypes = definitionTypes,
        )
    return BaGuideCatalogFilterIndex(
        definitions = definitions,
        attributesByEntryId = attributes,
    )
}

internal fun List<BaGuideCatalogEntry>.filterByCatalogFilters(selectedOptionIdsByFilterId: Map<Int, Set<Int>>): List<BaGuideCatalogEntry> {
    val activeFilters =
        selectedOptionIdsByFilterId
            .filterValues { it.isNotEmpty() }
            .filterKeys { it != BA_GUIDE_FILTER_ID_RELEASE_DATE }
    if (activeFilters.isEmpty()) return this
    return filter { entry ->
        activeFilters.all { (filterId, selectedOptionIds) ->
            entry.filterAttributes.matches(filterId, selectedOptionIds)
        }
    }
}

internal fun scoreRankForSort(
    entry: BaGuideCatalogEntry,
    filterId: Int,
    definitionsById: Map<Int, BaGuideCatalogFilterDefinition>,
): Int = entry.filterAttributes.scoreRank(definitionsById[filterId])

private fun parseFilterDefinitions(rawFilters: JSONArray?): List<BaGuideCatalogFilterDefinition> {
    rawFilters ?: return emptyList()
    val orderRank =
        BA_GUIDE_STUDENT_FILTER_ORDER
            .mapIndexed { index, id -> id to index }
            .toMap()
    return buildList {
        for (index in 0 until rawFilters.length()) {
            val item = rawFilters.optJSONObject(index) ?: continue
            val id = item.optInt("id", 0)
            if (id <= 0 || id !in orderRank) continue
            val name = item.optString("name").trim()
            if (name.isBlank()) continue
            add(
                BaGuideCatalogFilterDefinition(
                    id = id,
                    name = name,
                    type = item.optInt("type", 0),
                    options = parseFilterOptions(item.optJSONArray("children")),
                ),
            )
        }
    }.sortedBy { orderRank[it.id] ?: Int.MAX_VALUE }
}

private fun parseFilterOptions(rawOptions: JSONArray?): List<BaGuideCatalogFilterOption> {
    rawOptions ?: return emptyList()
    return buildList {
        for (index in 0 until rawOptions.length()) {
            val item = rawOptions.optJSONObject(index) ?: continue
            val id = item.optInt("id", 0)
            val name = item.optString("name").trim()
            if (id <= 0 || name.isBlank()) continue
            add(
                BaGuideCatalogFilterOption(
                    id = id,
                    name = name,
                    iconUrl = normalizeCatalogFilterIconUrl(item.optString("icon")),
                ),
            )
        }
    }
}

private fun parseFilterAttributes(
    rawAttributes: JSONObject?,
    definitionTypes: Map<Int, Int>,
): Map<Int, BaGuideCatalogEntryFilterAttributes> {
    rawAttributes ?: return emptyMap()
    return buildMap {
        val keys = rawAttributes.keys()
        while (keys.hasNext()) {
            val key = keys.next().trim()
            val entryId = key.toIntOrNull() ?: continue
            if (entryId <= 0) continue
            val arr = rawAttributes.optJSONArray(key) ?: continue
            val optionIdsByFilterId = mutableMapOf<Int, Set<Int>>()
            val numericValueByFilterId = mutableMapOf<Int, Long>()
            for (index in 0 until arr.length()) {
                val item = arr.optJSONObject(index) ?: continue
                val filterId = item.optInt("input_id", 0)
                if (filterId <= 0 || filterId !in definitionTypes) continue
                when (definitionTypes[filterId]) {
                    0 -> {
                        val ids = item.opt("value").toIntSetValue()
                        if (ids.isNotEmpty()) optionIdsByFilterId[filterId] = ids
                    }

                    1 -> {
                        val value = item.opt("value").toLongValue()
                        if (value > 0L) numericValueByFilterId[filterId] = value
                    }
                }
            }
            put(
                entryId,
                BaGuideCatalogEntryFilterAttributes(
                    optionIdsByFilterId = optionIdsByFilterId,
                    numericValueByFilterId = numericValueByFilterId,
                ),
            )
        }
    }
}

private fun Any?.toIntSetValue(): Set<Int> =
    when (this) {
        is JSONArray -> toIntSet()
        is Number -> setOf(toInt()).filterPositiveIds()
        is String -> split(",").mapNotNull { it.trim().toIntOrNull() }.filterPositiveIds()
        else -> emptySet()
    }

private fun JSONArray?.toIntSet(): Set<Int> {
    this ?: return emptySet()
    return buildSet {
        for (index in 0 until length()) {
            when (val value = opt(index)) {
                is Number -> value.toInt()
                is String -> value.trim().toIntOrNull() ?: 0
                else -> 0
            }.takeIf { it > 0 }?.let(::add)
        }
    }
}

private fun Iterable<Int>.filterPositiveIds(): Set<Int> = filter { it > 0 }.toSet()

private fun Any?.toLongValue(): Long =
    when (this) {
        is Number -> toLong()
        is String -> trim().toLongOrNull() ?: 0L
        else -> 0L
    }.coerceAtLeast(0L)

private fun normalizeCatalogFilterIconUrl(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    if (value.startsWith("http://", ignoreCase = true)) {
        return value.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
    }
    if (value.startsWith("https://", ignoreCase = true)) return value
    if (value.startsWith("//")) return "https:$value"
    return if (value.startsWith("/")) {
        "https://www.gamekee.com$value"
    } else {
        "https://www.gamekee.com/$value"
    }
}
