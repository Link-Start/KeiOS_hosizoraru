package os.kei.ui.page.main.host.pager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import os.kei.feature.home.model.HomeOverviewCard
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.model.toHomeOverviewCardOrNull

internal data class MainPagerBackgroundState(
    val hasNonHomeBackground: Boolean,
    val effectiveNonHomeBackgroundUri: String
)

@Composable
internal fun rememberMainPagerBackgroundState(
    nonHomeBackgroundEnabled: Boolean,
    nonHomeBackgroundUri: String
): MainPagerBackgroundState {
    val effectiveNonHomeBackgroundUri = remember(nonHomeBackgroundEnabled, nonHomeBackgroundUri) {
        if (nonHomeBackgroundEnabled) nonHomeBackgroundUri.trim() else ""
    }
    val hasNonHomeBackground = remember(effectiveNonHomeBackgroundUri) {
        effectiveNonHomeBackgroundUri.isNotBlank()
    }
    return remember(hasNonHomeBackground, effectiveNonHomeBackgroundUri) {
        MainPagerBackgroundState(
            hasNonHomeBackground = hasNonHomeBackground,
            effectiveNonHomeBackgroundUri = effectiveNonHomeBackgroundUri
        )
    }
}

internal data class MainPagerTabsState(
    val tabs: List<BottomPage>,
    val initialPageIndex: Int,
    val visibleTabsSnapshot: Set<BottomPage>
)

@Composable
internal fun rememberMainPagerTabsState(
    visibleBottomPageNames: Set<String>,
    requestedBottomPage: String?,
    requestedBottomPageToken: Int
): MainPagerTabsState {
    val tabs = remember(visibleBottomPageNames) {
        BottomPage.entries.filter { page ->
            page == BottomPage.Home || visibleBottomPageNames.contains(page.name)
        }
    }
    val initialPageIndex = remember(tabs, requestedBottomPage, requestedBottomPageToken) {
        val target = requestedBottomPage?.trim().orEmpty()
        tabs.indexOfFirst { it.name == target }
            .takeIf { it >= 0 }
            ?: 0
    }
    val visibleTabsSnapshot = remember(tabs) { tabs.toSet() }
    return remember(tabs, initialPageIndex, visibleTabsSnapshot) {
        MainPagerTabsState(
            tabs = tabs,
            initialPageIndex = initialPageIndex,
            visibleTabsSnapshot = visibleTabsSnapshot
        )
    }
}

internal fun buildMainPagerVisibilityChangeAction(
    visibleBottomPageNames: Set<String>,
    onVisibleBottomPageNamesChange: (Set<String>) -> Unit,
    onOverviewCardVisibilityChange: (HomeOverviewCard, Boolean) -> Unit
): (BottomPage, Boolean) -> Unit {
    return { page, visible ->
        if (page != BottomPage.Home) {
            val updated = visibleBottomPageNames
                .toMutableSet()
                .apply {
                    if (visible) add(page.name) else remove(page.name)
                }
                .toSet()
            onVisibleBottomPageNamesChange(updated)
            if (!visible) {
                page.toHomeOverviewCardOrNull()?.let { card ->
                    onOverviewCardVisibilityChange(card, false)
                }
            }
        }
    }
}
