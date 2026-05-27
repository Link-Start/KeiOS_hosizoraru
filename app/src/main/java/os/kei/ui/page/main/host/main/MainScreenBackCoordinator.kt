package os.kei.ui.page.main.host.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import os.kei.ui.navigation.KeiosRoute
import os.kei.ui.navigation.Navigator

internal enum class MainRouteBackAction {
    None,
    PopRoute,
    PopBaGuideCatalog,
}

internal fun resolveMainRouteBackAction(
    backStackSize: Int,
    topRoute: NavKey?,
): MainRouteBackAction {
    if (backStackSize <= 1) return MainRouteBackAction.None
    return when (topRoute) {
        is KeiosRoute.BaGuideCatalog -> MainRouteBackAction.PopBaGuideCatalog
        null -> MainRouteBackAction.None
        else -> MainRouteBackAction.PopRoute
    }
}

@Stable
internal class MainScreenBackCoordinator(
    private val backStack: List<NavKey>,
    private val navigator: Navigator,
    private val pagerCoordinator: MainScreenPagerCoordinator,
) {
    fun onRouteBack() {
        when (resolveMainRouteBackAction(backStack.size, backStack.lastOrNull())) {
            MainRouteBackAction.None -> {
                Unit
            }

            MainRouteBackAction.PopRoute -> {
                navigator.pop()
            }

            MainRouteBackAction.PopBaGuideCatalog -> {
                pagerCoordinator.onBaGuideCatalogBack()
                navigator.pop()
            }
        }
    }
}

@Composable
internal fun rememberMainScreenBackCoordinator(
    backStack: List<NavKey>,
    navigator: Navigator,
    pagerCoordinator: MainScreenPagerCoordinator,
): MainScreenBackCoordinator =
    remember(backStack, navigator, pagerCoordinator) {
        MainScreenBackCoordinator(
            backStack = backStack,
            navigator = navigator,
            pagerCoordinator = pagerCoordinator,
        )
    }
