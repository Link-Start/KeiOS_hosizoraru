package os.kei.ui.page.main.student.catalog.state

import androidx.annotation.StringRes
import os.kei.R
import os.kei.ui.page.main.student.catalog.BA_GUIDE_FILTER_ID_CN_SCORE
import os.kei.ui.page.main.student.catalog.BA_GUIDE_FILTER_ID_GLOBAL_SCORE
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogFilterDefinition
import os.kei.ui.page.main.student.catalog.scoreRankForSort

internal enum class BaGuideCatalogSortMode(
    @get:StringRes val labelRes: Int,
) {
    Default(R.string.ba_catalog_sort_default),
    ReleaseDateDesc(R.string.ba_catalog_sort_release_date_desc),
    ReleaseDateAsc(R.string.ba_catalog_sort_release_date_asc),
    GlobalScoreDesc(R.string.ba_catalog_sort_global_score_desc),
    GlobalScoreAsc(R.string.ba_catalog_sort_global_score_asc),
    CnScoreDesc(R.string.ba_catalog_sort_cn_score_desc),
    CnScoreAsc(R.string.ba_catalog_sort_cn_score_asc),
}

internal fun List<BaGuideCatalogEntry>.sortedByMode(
    mode: BaGuideCatalogSortMode,
    favoriteCatalogEntries: Map<Long, Long>,
    filterDefinitions: List<BaGuideCatalogFilterDefinition> = emptyList(),
): List<BaGuideCatalogEntry> {
    val definitionsById = filterDefinitions.associateBy { it.id }
    val sortedBase =
        when (mode) {
            BaGuideCatalogSortMode.Default -> {
                sortedBy { it.order }
            }

            BaGuideCatalogSortMode.ReleaseDateDesc -> {
                sortedWith(
                    compareByDescending<BaGuideCatalogEntry> {
                        when {
                            it.releaseDateSec > 0L -> it.releaseDateSec
                            it.createdAtSec > 0L -> it.createdAtSec
                            else -> Long.MIN_VALUE
                        }
                    }.thenBy { it.order },
                )
            }

            BaGuideCatalogSortMode.ReleaseDateAsc -> {
                sortedWith(
                    compareBy<BaGuideCatalogEntry> {
                        when {
                            it.releaseDateSec > 0L -> it.releaseDateSec
                            it.createdAtSec > 0L -> it.createdAtSec
                            else -> Long.MAX_VALUE
                        }
                    }.thenBy { it.order },
                )
            }

            BaGuideCatalogSortMode.GlobalScoreDesc -> {
                sortedWith(
                    compareByDescending<BaGuideCatalogEntry> {
                        scoreRankForSort(it, BA_GUIDE_FILTER_ID_GLOBAL_SCORE, definitionsById)
                    }.thenBy { it.order },
                )
            }

            BaGuideCatalogSortMode.GlobalScoreAsc -> {
                sortedWith(
                    compareBy<BaGuideCatalogEntry> {
                        val rank = scoreRankForSort(it, BA_GUIDE_FILTER_ID_GLOBAL_SCORE, definitionsById)
                        if (rank > 0) rank else Int.MAX_VALUE
                    }.thenBy { it.order },
                )
            }

            BaGuideCatalogSortMode.CnScoreDesc -> {
                sortedWith(
                    compareByDescending<BaGuideCatalogEntry> {
                        scoreRankForSort(it, BA_GUIDE_FILTER_ID_CN_SCORE, definitionsById)
                    }.thenBy { it.order },
                )
            }

            BaGuideCatalogSortMode.CnScoreAsc -> {
                sortedWith(
                    compareBy<BaGuideCatalogEntry> {
                        val rank = scoreRankForSort(it, BA_GUIDE_FILTER_ID_CN_SCORE, definitionsById)
                        if (rank > 0) rank else Int.MAX_VALUE
                    }.thenBy { it.order },
                )
            }
        }
    if (favoriteCatalogEntries.isEmpty()) return sortedBase
    val pinnedFavorites =
        sortedBase
            .filter { entry -> favoriteCatalogEntries.containsKey(entry.contentId) }
            .sortedWith(
                compareBy<BaGuideCatalogEntry> {
                    favoriteCatalogEntries[it.contentId] ?: Long.MAX_VALUE
                }.thenBy { it.order },
            )
    val regularEntries =
        sortedBase.filterNot { entry ->
            favoriteCatalogEntries.containsKey(entry.contentId)
        }
    return pinnedFavorites + regularEntries
}
