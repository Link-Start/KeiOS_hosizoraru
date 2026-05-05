package os.kei.ui.page.main.student.catalog.component

import os.kei.ui.page.main.student.BaStudentGuideStore
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl

internal fun GuideBgmFavoriteItem.resolveStudentArtworkImageUrl(
    catalog: BaGuideCatalogBundle
): String {
    return sequenceOf(
        studentImageUrl,
        resolveCatalogEntryArtworkImageUrl(catalog),
        imageUrl
    )
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}

internal fun GuideBgmFavoriteItem.resolvePlaybackArtworkImageUrl(): String {
    val guideOverviewImageUrl = BaStudentGuideStore.loadInfoSnapshot(sourceUrl)
        .info
        ?.imageUrl
        .orEmpty()
    return sequenceOf(guideOverviewImageUrl, studentImageUrl, imageUrl)
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}

private fun GuideBgmFavoriteItem.resolveCatalogEntryArtworkImageUrl(
    catalog: BaGuideCatalogBundle
): String {
    val contentId = extractGuideContentIdFromUrl(sourceUrl)
    val matchedEntry = catalog.entriesByTab.values
        .asSequence()
        .flatten()
        .firstOrNull { entry ->
            when {
                contentId != null -> entry.contentId == contentId
                sourceUrl.isNotBlank() -> entry.detailUrl == sourceUrl
                else -> false
            }
        }
        ?: catalog.entriesByTab.values
            .asSequence()
            .flatten()
            .firstOrNull { entry -> entry.matchesFavoriteStudentName(this) }
    return matchedEntry?.iconUrl.orEmpty()
}

private fun BaGuideCatalogEntry.matchesFavoriteStudentName(
    favorite: GuideBgmFavoriteItem
): Boolean {
    val target = favorite.studentTitle.ifBlank { favorite.title }.catalogNameKey()
    if (target.isBlank()) return false
    return sequenceOf(name, alias, aliasDisplay)
        .map { it.catalogNameKey() }
        .any { key -> key == target || key.contains(target) || target.contains(key) }
}

private fun String.catalogNameKey(): String {
    return trim()
        .lowercase()
        .replace("（", "(")
        .replace("）", ")")
        .replace(" ", "")
        .replace("・", "·")
}
