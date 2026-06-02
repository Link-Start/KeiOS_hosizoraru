package os.kei.ui.page.main.student.catalog.component

import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl

internal fun GuideBgmFavoriteItem.resolveStudentArtworkImageUrl(
    catalog: BaGuideCatalogBundle
): String {
    return sequenceOf(
        studentImageUrl,
        resolveCatalogEntry(catalog)?.iconUrl.orEmpty(),
        imageUrl
    )
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}

internal fun GuideBgmFavoriteItem.withResolvedCatalogStudentMetadata(
    catalog: BaGuideCatalogBundle
): GuideBgmFavoriteItem = withCatalogEntryStudentMetadata(resolveCatalogEntry(catalog))

internal fun GuideBgmFavoriteItem.withCatalogEntryStudentMetadata(
    entry: BaGuideCatalogEntry?
): GuideBgmFavoriteItem {
    if (entry == null) return this
    val entryName = entry.name.trim()
    val entryIconUrl = entry.iconUrl.trim()
    val entryDetailUrl = entry.detailUrl.trim()
    if (
        entryName.isBlank() &&
        entryIconUrl.isBlank() &&
        entryDetailUrl.isBlank()
    ) {
        return this
    }
    val resolvedStudentImageUrl = studentImageUrl.ifBlank { entryIconUrl }
    val resolvedImageUrl = imageUrl.ifBlank { resolvedStudentImageUrl.ifBlank { entryIconUrl } }
    val resolvedStudentTitle = studentTitle.ifBlank { entryName }
    val resolvedSourceUrl = sourceUrl.ifBlank { entryDetailUrl }
    if (
        resolvedStudentImageUrl == studentImageUrl &&
        resolvedImageUrl == imageUrl &&
        resolvedStudentTitle == studentTitle &&
        resolvedSourceUrl == sourceUrl
    ) {
        return this
    }
    return copy(
        studentTitle = resolvedStudentTitle,
        studentImageUrl = resolvedStudentImageUrl,
        imageUrl = resolvedImageUrl,
        sourceUrl = resolvedSourceUrl,
    )
}

internal fun GuideBgmFavoriteItem.resolvePlaybackArtworkImageUrl(): String {
    return sequenceOf(studentImageUrl, imageUrl)
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}

private fun GuideBgmFavoriteItem.resolveCatalogEntry(
    catalog: BaGuideCatalogBundle
): BaGuideCatalogEntry? {
    val contentId = extractGuideContentIdFromUrl(sourceUrl)
    return catalog.entriesByTab.values
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
