package os.kei.ui.page.main.student.catalog.component

import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.BaStudentGuideStore
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.buildProfileMetaItems
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogStore
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl

private val BGM_SEARCH_TOKEN_SPLIT_REGEX = Regex("""\s+""")

internal class BgmFavoriteSearchContext private constructor(
    private val catalogEntryByContentId: Map<Long, BaGuideCatalogEntry>,
) {
    private val guideInfoBySourceUrl = mutableMapOf<String, BaStudentGuideInfo?>()

    fun catalogEntryFor(contentId: Long): BaGuideCatalogEntry? = catalogEntryByContentId[contentId]

    fun guideInfoFor(sourceUrl: String): BaStudentGuideInfo? =
        guideInfoBySourceUrl.getOrPut(sourceUrl) {
            BaStudentGuideStore.loadInfo(sourceUrl)
        }

    companion object {
        fun build(): BgmFavoriteSearchContext {
            val catalogEntryByContentId =
                BaGuideCatalogStore
                    .loadBundle()
                    ?.entriesByTab
                    ?.values
                    ?.asSequence()
                    ?.flatten()
                    ?.associateBy { entry -> entry.contentId }
                    .orEmpty()
            return BgmFavoriteSearchContext(catalogEntryByContentId)
        }
    }
}

internal fun bgmFavoriteMatchesSearch(
    favorite: GuideBgmFavoriteItem,
    searchQuery: String,
    context: BgmFavoriteSearchContext,
): Boolean {
    val tokens =
        searchQuery
            .trim()
            .split(BGM_SEARCH_TOKEN_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    if (tokens.isEmpty()) return true
    val contentId = extractGuideContentIdFromUrl(favorite.sourceUrl)
    val haystack = bgmFavoriteSearchFields(favorite, contentId, context)
    return tokens.all { token ->
        haystack.any { field -> field.contains(token, ignoreCase = true) }
    }
}

private fun bgmFavoriteSearchFields(
    favorite: GuideBgmFavoriteItem,
    contentId: Long?,
    context: BgmFavoriteSearchContext,
): List<String> {
    val catalogEntry = contentId?.let(context::catalogEntryFor)
    val guideInfo = context.guideInfoFor(favorite.sourceUrl)
    return buildList {
        add(favorite.title)
        add(favorite.studentTitle)
        add(favorite.note)
        add(favorite.audioUrl)
        add(favorite.sourceUrl)
        contentId?.let { add(it.toString()) }
        catalogEntry?.let { entry ->
            add(entry.name)
            add(entry.alias)
            add(entry.aliasDisplay)
            add(entry.detailUrl)
            add(entry.tab.label)
        }
        guideInfo?.let { appendGuideInfoSearchFields(it) }
    }.filter { it.isNotBlank() }
}

private fun MutableList<String>.appendGuideInfoSearchFields(info: BaStudentGuideInfo) {
    add(info.title)
    add(info.subtitle)
    add(info.description)
    add(info.summary)
    add(info.voiceCvJp)
    add(info.voiceCvCn)
    info.voiceCvByLanguage.values.forEach(::add)
    info.buildProfileMetaItems().forEach { item ->
        add(item.title)
        add(item.value)
    }
    info.stats.forEach { (key, value) ->
        add(key)
        add(value)
    }
    appendRows(info.profileRows)
    appendRows(info.skillRows)
    info.galleryItems.forEach { item ->
        add(item.title)
        add(item.note)
        add(item.memoryUnlockLevel)
    }
}

private fun MutableList<String>.appendRows(rows: List<BaGuideRow>) {
    rows.forEach { row ->
        add(row.key)
        add(row.value)
    }
}
