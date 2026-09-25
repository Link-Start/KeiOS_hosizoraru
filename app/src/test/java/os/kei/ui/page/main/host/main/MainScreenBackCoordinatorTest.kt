package os.kei.ui.page.main.host.main

import org.junit.Test
import os.kei.ui.navigation.KeiosRoute
import kotlin.test.assertEquals

class MainScreenBackCoordinatorTest {
    @Test
    fun `route back pops a child route, restores the BA tab under the catalog, and does nothing at the root`() {
        listOf<Triple<String, Pair<Int, KeiosRoute?>, MainRouteBackAction>>(
            Triple("main root", 1 to KeiosRoute.Main, MainRouteBackAction.None),
            Triple("empty route stack", 0 to null, MainRouteBackAction.None),
            Triple("standard child route", 2 to KeiosRoute.Settings, MainRouteBackAction.PopRoute),
            Triple("ba guide catalog", 2 to KeiosRoute.BaGuideCatalog(), MainRouteBackAction.PopBaGuideCatalog),
        ).forEach { (name, stack, expected) ->
            val (backStackSize, topRoute) = stack
            assertEquals(expected, resolveMainRouteBackAction(backStackSize = backStackSize, topRoute = topRoute), name)
        }
    }
}
