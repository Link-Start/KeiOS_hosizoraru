package os.kei.ui.page.main.host.main

import org.junit.Test
import os.kei.ui.navigation.KeiosRoute
import kotlin.test.assertEquals

class MainScreenBackCoordinatorTest {
    @Test
    fun `main root has no route back action`() {
        val action =
            resolveMainRouteBackAction(
                backStackSize = 1,
                topRoute = KeiosRoute.Main,
            )

        assertEquals(MainRouteBackAction.None, action)
    }

    @Test
    fun `standard child route pops route`() {
        val action =
            resolveMainRouteBackAction(
                backStackSize = 2,
                topRoute = KeiosRoute.Settings,
            )

        assertEquals(MainRouteBackAction.PopRoute, action)
    }

    @Test
    fun `ba guide catalog route restores ba tab before pop`() {
        val action =
            resolveMainRouteBackAction(
                backStackSize = 2,
                topRoute = KeiosRoute.BaGuideCatalog(),
            )

        assertEquals(MainRouteBackAction.PopBaGuideCatalog, action)
    }

    @Test
    fun `empty route stack has no route back action`() {
        val action =
            resolveMainRouteBackAction(
                backStackSize = 0,
                topRoute = null,
            )

        assertEquals(MainRouteBackAction.None, action)
    }
}
