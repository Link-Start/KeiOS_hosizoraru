package os.kei.ui.page.main.student.catalog.page

import os.kei.R
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab

internal enum class BaGuideCatalogPageTab(
    val labelRes: Int,
    val compactLabelRes: Int,
    val catalogTab: BaGuideCatalogTab?,
    val specialTab: BaGuideCatalogSpecialTab? = null,
) {
    Student(
        labelRes = R.string.ba_catalog_tab_student,
        compactLabelRes = R.string.ba_catalog_tab_student_short,
        catalogTab = BaGuideCatalogTab.Student,
    ),
    NpcSatellite(
        labelRes = R.string.ba_catalog_tab_npc_satellite,
        compactLabelRes = R.string.ba_catalog_tab_npc_satellite_short,
        catalogTab = BaGuideCatalogTab.NpcSatellite,
    ),
    StudentBgm(
        labelRes = R.string.ba_catalog_tab_student_bgm,
        compactLabelRes = R.string.ba_catalog_tab_student_bgm_short,
        catalogTab = null,
        specialTab = BaGuideCatalogSpecialTab.StudentBgm,
    ),
    Bgm(
        labelRes = R.string.ba_catalog_tab_bgm,
        compactLabelRes = R.string.ba_catalog_tab_bgm,
        catalogTab = null,
        specialTab = BaGuideCatalogSpecialTab.FavoriteBgm,
    ),
}

internal enum class BaGuideCatalogSpecialTab {
    StudentBgm,
    FavoriteBgm,
}

internal val BaGuideCatalogPageTab.searchPlaceholderRes: Int
    get() =
        when (this) {
            BaGuideCatalogPageTab.Student -> R.string.ba_catalog_search_placeholder_student
            BaGuideCatalogPageTab.NpcSatellite -> R.string.ba_catalog_search_placeholder_npc_satellite
            BaGuideCatalogPageTab.StudentBgm -> R.string.ba_catalog_search_placeholder_music
            BaGuideCatalogPageTab.Bgm -> R.string.ba_catalog_search_placeholder_playback
        }
