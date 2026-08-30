@file:Suppress("FunctionName", "ktlint:standard:property-naming")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.component.bgm.BaGuideBgmDockTab
import os.kei.ui.testing.KeiOsTestTags

@Composable
internal fun rememberBaGuideCatalogChromeTabs(): List<BaGuideBgmDockTab> {
    val studentLabel = stringResource(R.string.ba_catalog_tab_students_short)
    val memoryLobbyLabel = stringResource(R.string.ba_catalog_tab_memory_lobby_short)
    val studentBgmLabel = stringResource(R.string.ba_catalog_tab_student_bgm_short)
    val bgmLabel = stringResource(R.string.ba_catalog_tab_bgm)
    val studentIcon = ImageVector.vectorResource(R.drawable.ba_tab_profile_vector)
    val memoryLobbyIcon = ImageVector.vectorResource(R.drawable.ba_tab_skill_vector)
    val musicIcon = ImageVector.vectorResource(R.drawable.ba_tab_bgm)
    val playbackIcon = ImageVector.vectorResource(R.drawable.ba_tab_play)
    return remember(
        studentLabel,
        memoryLobbyLabel,
        studentBgmLabel,
        bgmLabel,
        studentIcon,
        memoryLobbyIcon,
        musicIcon,
        playbackIcon,
    ) {
        listOf(
            BaGuideBgmDockTab(
                CatalogStudentKey,
                studentIcon,
                studentLabel,
                KeiOsTestTags.BaGuideCatalogDockStudent,
            ),
            BaGuideBgmDockTab(
                CatalogMemoryLobbyKey,
                memoryLobbyIcon,
                memoryLobbyLabel,
                KeiOsTestTags.BaGuideCatalogDockMemoryLobby,
            ),
            BaGuideBgmDockTab(
                CatalogStudentBgmKey,
                musicIcon,
                studentBgmLabel,
                KeiOsTestTags.BaGuideCatalogDockStudentBgm,
            ),
            BaGuideBgmDockTab(
                CatalogFavoriteBgmKey,
                playbackIcon,
                bgmLabel,
                KeiOsTestTags.BaGuideCatalogDockFavoriteBgm,
            ),
        )
    }
}

internal fun catalogPagerSwitchDurationMillis(distance: Int): Int = (100 * distance.coerceAtLeast(1) + 100).coerceIn(180, 420)

internal fun Map<String, String>.catalogSearchQueryFor(tab: BaGuideCatalogTab): String =
    get(tab.catalogSearchQueryKey()).orEmpty()

internal fun BaGuideCatalogTab.catalogSearchQueryKey(): String =
    when (this) {
        BaGuideCatalogTab.Student -> CatalogStudentKey
        BaGuideCatalogTab.NpcSatellite -> CatalogNpcSatelliteKey
    }

internal const val CATALOG_PAGER_TARGET_WARM_DATA_DISTANCE = 0.75f

internal val CATALOG_MUSIC_CONTENT_TOP_PADDING = 136.dp
internal val CATALOG_MUSIC_CONTENT_BOTTOM_PADDING = 224.dp

private const val CatalogStudentKey = "Student"
private const val CatalogNpcSatelliteKey = "NpcSatellite"
private const val CatalogMemoryLobbyKey = "MemoryLobby"
private const val CatalogStudentBgmKey = "StudentBgm"
private const val CatalogFavoriteBgmKey = "Bgm"
