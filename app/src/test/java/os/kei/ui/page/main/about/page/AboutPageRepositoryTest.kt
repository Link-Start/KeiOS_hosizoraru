package os.kei.ui.page.main.about.page

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutPageRepositoryTest {
    private val repository =
        AboutPageRepository(
            ioDispatcher = Dispatchers.Unconfined,
            defaultDispatcher = Dispatchers.Unconfined,
        )

    @Test
    fun `derive search state returns matching cards off ui path`() =
        runTest {
            val targets =
                listOf(
                    AboutSearchTarget(
                        card = AboutSearchCard.Release,
                        category = AboutCategory.Overview,
                        tokens = listOf("release notes", "version 1.8.0"),
                    ),
                    AboutSearchTarget(
                        card = AboutSearchCard.Permission,
                        category = AboutCategory.System,
                        tokens = listOf("notification permission"),
                    ),
                )

            val state =
                repository.deriveSearchState(
                    targets = targets,
                    query = "Version",
                )

            assertTrue(state.active)
            assertEquals(listOf(AboutSearchCard.Release), state.matchingTargets.map { it.card })
            assertEquals(setOf(AboutSearchCard.Release), state.matchingCards)
        }

    @Test
    fun `derive search state clears when query is blank`() =
        runTest {
            val state =
                repository.deriveSearchState(
                    targets =
                        listOf(
                            AboutSearchTarget(
                                card = AboutSearchCard.Release,
                                category = AboutCategory.Overview,
                                tokens = listOf("release"),
                            ),
                        ),
                    query = "   ",
                )

            assertFalse(state.active)
            assertTrue(state.matchingTargets.isEmpty())
            assertTrue(state.matchingCards.isEmpty())
        }
}
