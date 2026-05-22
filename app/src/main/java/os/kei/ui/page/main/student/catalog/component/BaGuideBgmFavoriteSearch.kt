package os.kei.ui.page.main.student.catalog.component

import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.state.BaGuideBgmFavoriteMetadataIndex
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl

private val BGM_SEARCH_TOKEN_SPLIT_REGEX = Regex("""\s+""")

internal fun bgmFavoriteMatchesSearch(
    favorite: GuideBgmFavoriteItem,
    searchQuery: String,
    metadataIndex: BaGuideBgmFavoriteMetadataIndex,
): Boolean {
    val tokens =
        searchQuery
            .trim()
            .split(BGM_SEARCH_TOKEN_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    if (tokens.isEmpty()) return true
    val contentId = extractGuideContentIdFromUrl(favorite.sourceUrl)
    val metadata = metadataIndex.metadataFor(favorite)
    val haystack = bgmFavoriteSearchFields(favorite, contentId, metadata.searchFields)
    return tokens.all { token ->
        haystack.any { field -> field.contains(token, ignoreCase = true) }
    }
}

private fun bgmFavoriteSearchFields(
    favorite: GuideBgmFavoriteItem,
    contentId: Long?,
    metadataSearchFields: List<String>,
): List<String> {
    return buildList {
        add(favorite.title)
        add(favorite.studentTitle)
        add(favorite.note)
        add(favorite.audioUrl)
        add(favorite.sourceUrl)
        contentId?.let { add(it.toString()) }
        addAll(metadataSearchFields)
    }.filter { it.isNotBlank() }
}
