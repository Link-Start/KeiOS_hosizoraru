package os.kei.ui.page.main.host.pager

import org.junit.Test
import os.kei.feature.home.model.HomeOverviewCard
import os.kei.ui.page.main.model.BottomPage
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainPagerVisibilityChangeActionTest {
    @Test
    fun `hiding github tab also hides github home card`() {
        val harness = Harness(visible = setOf(BottomPage.GitHub, BottomPage.Ba))

        harness.action(BottomPage.GitHub, false)

        assertEquals(listOf(setOf(BottomPage.Ba.name)), harness.emittedBottomSets)
        assertEquals(listOf(HomeOverviewCard.GITHUB to false), harness.emittedCards)
    }

    @Test
    fun `showing github tab preserves explicit home card state`() {
        val harness = Harness(visible = setOf(BottomPage.Ba))

        harness.action(BottomPage.GitHub, true)

        assertEquals(listOf(setOf(BottomPage.Ba.name, BottomPage.GitHub.name)), harness.emittedBottomSets)
        assertTrue(harness.emittedCards.isEmpty())
    }

    @Test
    fun `hiding os tab only changes bottom tabs`() {
        val harness = Harness(visible = setOf(BottomPage.Os, BottomPage.GitHub))

        harness.action(BottomPage.Os, false)

        assertEquals(listOf(setOf(BottomPage.GitHub.name)), harness.emittedBottomSets)
        assertTrue(harness.emittedCards.isEmpty())
    }

    @Test
    fun `home tab visibility action is ignored`() {
        val harness = Harness(visible = setOf(BottomPage.GitHub))

        harness.action(BottomPage.Home, false)

        assertTrue(harness.emittedBottomSets.isEmpty())
        assertTrue(harness.emittedCards.isEmpty())
    }

    private class Harness(visible: Set<BottomPage>) {
        val emittedBottomSets = mutableListOf<Set<String>>()
        val emittedCards = mutableListOf<Pair<HomeOverviewCard, Boolean>>()
        val action = buildMainPagerVisibilityChangeAction(
            visibleBottomPageNames = visible.map { it.name }.toSet(),
            onVisibleBottomPageNamesChange = emittedBottomSets::add,
            onOverviewCardVisibilityChange = { card, isVisible ->
                emittedCards += card to isVisible
            }
        )
    }
}
