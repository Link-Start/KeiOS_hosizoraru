package os.kei.ui.page.main.os

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class OsPageRowsStateLoaderTest {
    @Test
    fun `failed rows derivation clears deriving state and keeps previous rows`() =
        runTest {
            val input =
                OsPageRowsDerivationInput(
                    queryApplied = "flag",
                    sectionStates =
                        mapOf(
                            SectionKind.SYSTEM to
                                SectionState(
                                    rows = listOf(InfoRow("custom.flag", "true")),
                                ),
                        ),
                    expansionFlags = collapsedFlags(),
                )
            val loader =
                OsPageRowsStateLoader(
                    scope = this,
                    repository = OsPageRepository(defaultDispatcher = Dispatchers.Unconfined),
                    buildRowsDerivedState = { error("boom") },
                )

            loader.request(input)
            advanceUntilIdle()

            val state = loader.state.value
            assertEquals(input, state.input)
            assertFalse(state.deriving)
            assertEquals(OsPageRowsDerivedState.Empty, state.rowsState)
        }

    private fun collapsedFlags(): OsPageExpansionFlags =
        OsPageExpansionFlags(
            topInfoExpanded = false,
            systemTableExpanded = false,
            secureTableExpanded = false,
            globalTableExpanded = false,
            androidPropsExpanded = false,
            javaPropsExpanded = false,
            linuxEnvExpanded = false,
        )
}
