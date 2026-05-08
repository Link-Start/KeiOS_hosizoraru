package os.kei.ui.page.main.os

import org.junit.Test
import kotlin.test.assertEquals

class OsPageRowsDeriverTest {
    @Test
    fun `query filters section rows off composable path`() {
        val state = deriveOsPageRowsState(
            queryApplied = " flag ",
            sectionStates = mapOf(
                SectionKind.SYSTEM to SectionState(
                    rows = listOf(
                        InfoRow("custom.flag", "true"),
                        InfoRow("custom.version", "1")
                    )
                ),
                SectionKind.JAVA to SectionState(
                    rows = listOf(InfoRow("java.home", "/demo"))
                )
            ),
            expansionFlags = collapsedFlags()
        )

        assertEquals("flag", state.query)
        assertEquals(listOf(InfoRow("custom.flag", "true")), state.displayedSystemRows)
        assertEquals(emptyList(), state.displayedJavaRows)
        assertEquals(1, state.visibleRowsCount)
    }

    @Test
    fun `blank query keeps collapsed rows in original order`() {
        val rows = listOf(
            InfoRow("custom.version", "1"),
            InfoRow("custom.flag", "true")
        )

        val state = deriveOsPageRowsState(
            queryApplied = "",
            sectionStates = mapOf(SectionKind.SYSTEM to SectionState(rows = rows)),
            expansionFlags = collapsedFlags()
        )

        assertEquals(rows, state.displayedSystemRows)
        assertEquals(rows, state.prunedSystemRows)
        assertEquals(2, state.visibleRowsCount)
    }

    private fun collapsedFlags(): OsPageExpansionFlags {
        return OsPageExpansionFlags(
            topInfoExpanded = false,
            systemTableExpanded = false,
            secureTableExpanded = false,
            globalTableExpanded = false,
            androidPropsExpanded = false,
            javaPropsExpanded = false,
            linuxEnvExpanded = false
        )
    }
}
