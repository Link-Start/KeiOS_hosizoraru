package os.kei.ui.page.main.os

import androidx.compose.runtime.Immutable

@Immutable
internal data class OsPageExpansionFlags(
    val topInfoExpanded: Boolean,
    val systemTableExpanded: Boolean,
    val secureTableExpanded: Boolean,
    val globalTableExpanded: Boolean,
    val androidPropsExpanded: Boolean,
    val javaPropsExpanded: Boolean,
    val linuxEnvExpanded: Boolean
)

internal class OsPageRowsDerivationInput(
    val queryApplied: String,
    val sectionStates: Map<SectionKind, SectionState>,
    val expansionFlags: OsPageExpansionFlags,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is OsPageRowsDerivationInput &&
            queryApplied == other.queryApplied &&
            sectionStates === other.sectionStates &&
            expansionFlags == other.expansionFlags
    }

    override fun hashCode(): Int {
        var result = queryApplied.hashCode()
        result = 31 * result + System.identityHashCode(sectionStates)
        result = 31 * result + expansionFlags.hashCode()
        return result
    }
}

internal fun buildRowsDerivationInput(
    queryApplied: String,
    uiSnapshot: OsUiSnapshot,
    sectionStates: Map<SectionKind, SectionState>,
): OsPageRowsDerivationInput =
    OsPageRowsDerivationInput(
        queryApplied = queryApplied,
        sectionStates = sectionStates,
        expansionFlags =
            OsPageExpansionFlags(
                topInfoExpanded = uiSnapshot.topInfoExpanded,
                systemTableExpanded = uiSnapshot.systemTableExpanded,
                secureTableExpanded = uiSnapshot.secureTableExpanded,
                globalTableExpanded = uiSnapshot.globalTableExpanded,
                androidPropsExpanded = uiSnapshot.androidPropsExpanded,
                javaPropsExpanded = uiSnapshot.javaPropsExpanded,
                linuxEnvExpanded = uiSnapshot.linuxEnvExpanded,
            ),
    )

@Immutable
internal data class OsPageRowsUiDerivedState(
    val input: OsPageRowsDerivationInput? = null,
    val rowsState: OsPageRowsDerivedState = OsPageRowsDerivedState.Empty,
    val groupedTopInfoRows: List<TopInfoRowsGroup> = emptyList(),
    val deriving: Boolean = false,
) {
    companion object {
        val Empty = OsPageRowsUiDerivedState()
    }
}

@Immutable
internal data class OsPageRowsDerivedState(
    val query: String,
    val topInfoRows: List<InfoRow>,
    val displayedTopInfoRows: List<InfoRow>,
    val displayedSystemRows: List<InfoRow>,
    val displayedSecureRows: List<InfoRow>,
    val displayedGlobalRows: List<InfoRow>,
    val displayedAndroidRows: List<InfoRow>,
    val displayedJavaRows: List<InfoRow>,
    val displayedLinuxRows: List<InfoRow>,
    val prunedSystemRows: List<InfoRow>,
    val prunedSecureRows: List<InfoRow>,
    val prunedGlobalRows: List<InfoRow>,
    val prunedAndroidRows: List<InfoRow>,
    val prunedJavaRows: List<InfoRow>,
    val prunedLinuxRows: List<InfoRow>,
    val visibleRowsCount: Int
) {
    companion object {
        val Empty =
            OsPageRowsDerivedState(
                query = "",
                topInfoRows = emptyList(),
                displayedTopInfoRows = emptyList(),
                displayedSystemRows = emptyList(),
                displayedSecureRows = emptyList(),
                displayedGlobalRows = emptyList(),
                displayedAndroidRows = emptyList(),
                displayedJavaRows = emptyList(),
                displayedLinuxRows = emptyList(),
                prunedSystemRows = emptyList(),
                prunedSecureRows = emptyList(),
                prunedGlobalRows = emptyList(),
                prunedAndroidRows = emptyList(),
                prunedJavaRows = emptyList(),
                prunedLinuxRows = emptyList(),
                visibleRowsCount = 0,
            )
    }
}

internal fun deriveOsPageRowsState(
    queryApplied: String,
    sectionStates: Map<SectionKind, SectionState>,
    expansionFlags: OsPageExpansionFlags
): OsPageRowsDerivedState {
    val systemRows = sectionStates[SectionKind.SYSTEM]?.rows ?: emptyList()
    val secureRows = sectionStates[SectionKind.SECURE]?.rows ?: emptyList()
    val globalRows = sectionStates[SectionKind.GLOBAL]?.rows ?: emptyList()
    val androidRows = sectionStates[SectionKind.ANDROID]?.rows ?: emptyList()
    val javaRows = sectionStates[SectionKind.JAVA]?.rows ?: emptyList()
    val linuxRows = sectionStates[SectionKind.LINUX]?.rows ?: emptyList()
    val topInfoRows = buildTopInfoRowsSnapshot(sectionStates).rows
    val prunedSystemRows = removeTopInfoRows(SectionKind.SYSTEM, systemRows)
    val prunedSecureRows = removeTopInfoRows(SectionKind.SECURE, secureRows)
    val prunedGlobalRows = removeTopInfoRows(SectionKind.GLOBAL, globalRows)
    val prunedAndroidRows = removeTopInfoRows(SectionKind.ANDROID, androidRows)
    val prunedJavaRows = removeTopInfoRows(SectionKind.JAVA, javaRows)
    val prunedLinuxRows = removeTopInfoRows(SectionKind.LINUX, linuxRows)
    val query = queryApplied.trim()
    val displayedTopInfoRows = deriveDisplayedRows(
        rows = topInfoRows,
        query = query,
        expanded = expansionFlags.topInfoExpanded
    )
    val displayedSystemRows = deriveDisplayedRows(
        rows = prunedSystemRows,
        query = query,
        expanded = expansionFlags.systemTableExpanded
    )
    val displayedSecureRows = deriveDisplayedRows(
        rows = prunedSecureRows,
        query = query,
        expanded = expansionFlags.secureTableExpanded
    )
    val displayedGlobalRows = deriveDisplayedRows(
        rows = prunedGlobalRows,
        query = query,
        expanded = expansionFlags.globalTableExpanded
    )
    val displayedAndroidRows = deriveDisplayedRows(
        rows = prunedAndroidRows,
        query = query,
        expanded = expansionFlags.androidPropsExpanded
    )
    val displayedJavaRows = deriveDisplayedRows(
        rows = prunedJavaRows,
        query = query,
        expanded = expansionFlags.javaPropsExpanded
    )
    val displayedLinuxRows = deriveDisplayedRows(
        rows = prunedLinuxRows,
        query = query,
        expanded = expansionFlags.linuxEnvExpanded
    )
    val visibleRowsCount = displayedTopInfoRows.size +
            displayedSystemRows.size +
            displayedSecureRows.size +
            displayedGlobalRows.size +
            displayedAndroidRows.size +
            displayedJavaRows.size +
            displayedLinuxRows.size
    return OsPageRowsDerivedState(
        query = query,
        topInfoRows = topInfoRows,
        displayedTopInfoRows = displayedTopInfoRows,
        displayedSystemRows = displayedSystemRows,
        displayedSecureRows = displayedSecureRows,
        displayedGlobalRows = displayedGlobalRows,
        displayedAndroidRows = displayedAndroidRows,
        displayedJavaRows = displayedJavaRows,
        displayedLinuxRows = displayedLinuxRows,
        prunedSystemRows = prunedSystemRows,
        prunedSecureRows = prunedSecureRows,
        prunedGlobalRows = prunedGlobalRows,
        prunedAndroidRows = prunedAndroidRows,
        prunedJavaRows = prunedJavaRows,
        prunedLinuxRows = prunedLinuxRows,
        visibleRowsCount = visibleRowsCount
    )
}

private fun deriveDisplayedRows(
    rows: List<InfoRow>,
    query: String,
    expanded: Boolean
): List<InfoRow> {
    return if (query.isBlank() && !expanded) {
        rows
    } else {
        sortRowsByType(filterRows(rows, query))
    }
}
