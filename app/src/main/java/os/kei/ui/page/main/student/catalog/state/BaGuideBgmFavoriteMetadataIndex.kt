package os.kei.ui.page.main.student.catalog.state

import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.BaStudentGuideStore
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.buildProfileMetaItems
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogStore
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl

internal data class BaGuideBgmFavoriteMetadata(
    val searchFields: List<String> = emptyList(),
    val academy: String = "",
    val catalogTab: BaGuideCatalogTab? = null,
)

internal class BaGuideBgmFavoriteMetadataIndex private constructor(
    private val metadataBySourceUrl: Map<String, BaGuideBgmFavoriteMetadata>,
) {
    fun metadataFor(favorite: GuideBgmFavoriteItem): BaGuideBgmFavoriteMetadata =
        metadataBySourceUrl[favorite.sourceUrl] ?: BaGuideBgmFavoriteMetadata()

    companion object {
        val Empty = BaGuideBgmFavoriteMetadataIndex(emptyMap())

        fun build(favorites: List<GuideBgmFavoriteItem>): BaGuideBgmFavoriteMetadataIndex {
            if (favorites.isEmpty()) return Empty
            val bundle = BaGuideCatalogStore.loadBundle()
            val catalogEntryByContentId =
                bundle
                    ?.entriesByTab
                    ?.values
                    ?.asSequence()
                    ?.flatten()
                    ?.associateBy { entry -> entry.contentId }
                    .orEmpty()
            val catalogTabByContentId =
                buildMap {
                    bundle?.entriesByTab?.forEach { (tab, entries) ->
                        entries.forEach { entry ->
                            put(entry.contentId, tab)
                        }
                    }
                }
            val metadataBySourceUrl =
                favorites
                    .asSequence()
                    .map { favorite -> favorite.sourceUrl }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .associateWith { sourceUrl ->
                        val contentId = extractGuideContentIdFromUrl(sourceUrl)
                        val catalogEntry = contentId?.let(catalogEntryByContentId::get)
                        val guideInfo = BaStudentGuideStore.loadInfo(sourceUrl)
                        BaGuideBgmFavoriteMetadata(
                            searchFields =
                                buildFavoriteSearchFields(
                                    sourceUrl = sourceUrl,
                                    contentId = contentId,
                                    catalogEntry = catalogEntry,
                                    guideInfo = guideInfo,
                                ),
                            academy = guideInfo?.academyForBgmFavorite().orEmpty(),
                            catalogTab = contentId?.let(catalogTabByContentId::get),
                        )
                    }
            return BaGuideBgmFavoriteMetadataIndex(metadataBySourceUrl)
        }
    }
}

private fun buildFavoriteSearchFields(
    sourceUrl: String,
    contentId: Long?,
    catalogEntry: BaGuideCatalogEntry?,
    guideInfo: BaStudentGuideInfo?,
): List<String> =
    buildList {
        add(sourceUrl)
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

private fun BaStudentGuideInfo.academyForBgmFavorite(): String =
    buildProfileMetaItems()
        .firstOrNull { item -> item.title == "学院" }
        ?.value
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "-" }
        .orEmpty()

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
