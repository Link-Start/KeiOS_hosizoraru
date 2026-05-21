@file:Suppress("PropertyName")

package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.os.appLucideGridIcon
import os.kei.ui.page.main.os.appLucideHomeIcon
import os.kei.ui.page.main.os.appLucideLibraryIcon
import os.kei.ui.page.main.os.appLucideRadioIcon

@Composable
internal fun rememberBaGuideBgmDockTabs(): List<BaGuideBgmDockTab> {
    val homeLabel = stringResource(R.string.ba_catalog_bgm_nav_home)
    val discoverLabel = stringResource(R.string.ba_catalog_bgm_nav_discover)
    val radioLabel = stringResource(R.string.ba_catalog_bgm_nav_radio)
    val libraryLabel = stringResource(R.string.ba_catalog_bgm_nav_library)
    val homeIcon = appLucideHomeIcon()
    val discoverIcon = appLucideGridIcon()
    val radioIcon = appLucideRadioIcon()
    val libraryIcon = appLucideLibraryIcon()
    return remember(
        homeLabel,
        discoverLabel,
        radioLabel,
        libraryLabel,
        homeIcon,
        discoverIcon,
        radioIcon,
        libraryIcon,
    ) {
        listOf(
            BaGuideBgmDockTab(BaGuideBgmDockKeys.Home, homeIcon, homeLabel),
            BaGuideBgmDockTab(BaGuideBgmDockKeys.Discover, discoverIcon, discoverLabel),
            BaGuideBgmDockTab(BaGuideBgmDockKeys.Radio, radioIcon, radioLabel),
            BaGuideBgmDockTab(BaGuideBgmDockKeys.Library, libraryIcon, libraryLabel),
        )
    }
}

internal object BaGuideBgmDockKeys {
    const val Home = "home"
    const val Discover = "discover"
    const val Radio = "radio"
    const val Library = "library"
}

internal data class BaGuideBgmDockTab(
    val key: String,
    val icon: ImageVector,
    val label: String,
)
