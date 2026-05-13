package os.kei.ui.page.main.github.page

import org.junit.Test
import os.kei.feature.github.model.KEI_OS_RELEASE_PACKAGE_NAME
import os.kei.feature.github.model.defaultKeiOsTrackedApp
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GitHubActionsDebugNotificationTargetTest {
    @Test
    fun `debug notification queries release package and routes to current track`() {
        val currentItem = defaultKeiOsTrackedApp().copy(packageName = "os.kei.debug")

        val target = assertNotNull(
            selectKeiOsActionsDebugNotificationTarget(listOf(currentItem))
        )

        assertEquals(currentItem.id, target.uiItem.id)
        assertEquals(KEI_OS_RELEASE_PACKAGE_NAME, target.lookupItem.packageName)
        assertEquals("hosizoraru/KeiOS|os.kei", target.lookupItem.id)
    }

    @Test
    fun `release package track is preferred for debug notification route`() {
        val currentItem = defaultKeiOsTrackedApp().copy(packageName = "os.kei.debug")
        val releaseItem = currentItem.copy(packageName = KEI_OS_RELEASE_PACKAGE_NAME)

        val target = assertNotNull(
            selectKeiOsActionsDebugNotificationTarget(listOf(currentItem, releaseItem))
        )

        assertEquals(releaseItem.id, target.uiItem.id)
        assertEquals(KEI_OS_RELEASE_PACKAGE_NAME, target.lookupItem.packageName)
    }
}
