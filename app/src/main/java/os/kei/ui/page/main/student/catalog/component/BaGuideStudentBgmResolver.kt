package os.kei.ui.page.main.student.catalog.component

import os.kei.ui.page.main.student.GuideBgmFavoriteItem

internal data class BaGuideStudentBgmResolvedItem(
    val favorite: GuideBgmFavoriteItem,
    val fromCache: Boolean,
    val fromFavorite: Boolean = false
)
