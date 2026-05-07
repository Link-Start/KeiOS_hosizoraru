package os.kei.ui.page.main.host.pager

import org.junit.Test
import os.kei.feature.home.model.HomeOverviewCard
import os.kei.ui.page.main.model.BottomPage
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainPagerVisibilityChangeActionTest {
    @Test
    fun `hiding github tab also hides github home card`() {
        val emittedBottomSets = mutableListOf<Set<String>>()
        val emittedCards = mutableListOf<Pair<HomeOverviewCard, Boolean>>()
        val action = buildMainPagerVisibilityChangeAction(
            visibleBottomPageNames = setOf(BottomPage.GitHub.name, BottomPage.Ba.name),
            onVisibleBottomPageNamesChange = emittedBottomSets::add,
            onOverviewCardVisibilityChange = { card, visible ->
                emittedCards += card to visible
            }
        )

        action(BottomPage.GitHub, false)

        assertEquals(listOf(setOf(BottomPage.Ba.name)), emittedBottomSets)
        assertEquals(listOf(HomeOverviewCard.GITHUB to false), emittedCards)
    }

    @Test
    fun `showing github tab preserves explicit home card state`() {
        val emittedBottomSets = mutableListOf<Set<String>>()
        val emittedCards = mutableListOf<Pair<HomeOverviewCard, Boolean>>()
        val action = buildMainPagerVisibilityChangeAction(
            visibleBottomPageNames = setOf(BottomPage.Ba.name),
            onVisibleBottomPageNamesChange = emittedBottomSets::add,
            onOverviewCardVisibilityChange = { card, visible ->
                emittedCards += card to visible
            }
        )

        action(BottomPage.GitHub, true)

        assertEquals(listOf(setOf(BottomPage.Ba.name, BottomPage.GitHub.name)), emittedBottomSets)
        assertTrue(emittedCards.isEmpty())
    }

    @Test
    fun `hiding os tab only changes bottom tabs`() {
        val emittedBottomSets = mutableListOf<Set<String>>()
        val emittedCards = mutableListOf<Pair<HomeOverviewCard, Boolean>>()
        val action = buildMainPagerVisibilityChangeAction(
            visibleBottomPageNames = setOf(BottomPage.Os.name, BottomPage.GitHub.name),
            onVisibleBottomPageNamesChange = emittedBottomSets::add,
            onOverviewCardVisibilityChange = { card, visible ->
                emittedCards += card to visible
            }
        )

        action(BottomPage.Os, false)

        assertEquals(listOf(setOf(BottomPage.GitHub.name)), emittedBottomSets)
        assertTrue(emittedCards.isEmpty())
    }

    @Test
    fun `home tab visibility action is ignored`() {
        val emittedBottomSets = mutableListOf<Set<String>>()
        val emittedCards = mutableListOf<Pair<HomeOverviewCard, Boolean>>()
        val action = buildMainPagerVisibilityChangeAction(
            visibleBottomPageNames = setOf(BottomPage.GitHub.name),
            onVisibleBottomPageNamesChange = emittedBottomSets::add,
            onOverviewCardVisibilityChange = { card, visible ->
                emittedCards += card to visible
            }
        )

        action(BottomPage.Home, false)

        assertTrue(emittedBottomSets.isEmpty())
        assertTrue(emittedCards.isEmpty())
    }
}
