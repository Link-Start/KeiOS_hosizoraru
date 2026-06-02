package os.kei.ui.page.main.student.catalog.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab

@Stable
internal data class BaGuideCatalogFilterSortSnapshot(
    val searchQuery: String = "",
    val sortMode: BaGuideCatalogSortMode = BaGuideCatalogSortMode.Default,
    val selectedFiltersRaw: String = "",
    val studentSortMode: BaGuideCatalogSortMode = BaGuideCatalogSortMode.Default,
    val npcSatelliteSortMode: BaGuideCatalogSortMode = BaGuideCatalogSortMode.Default,
    val studentSelectedFiltersRaw: String = "",
    val npcSatelliteSelectedFiltersRaw: String = "",
)

@Stable
internal class BaGuideCatalogFilterSortState(
    private val snapshot: () -> BaGuideCatalogFilterSortSnapshot,
    private val onSnapshotChange: (BaGuideCatalogFilterSortSnapshot) -> Unit,
    private val activeCatalogTab: () -> BaGuideCatalogTab?,
    private val showFilterPopupState: MutableState<Boolean>,
) {
    var searchQuery: String
        get() = snapshot().searchQuery
        set(value) {
            onSnapshotChange(snapshot().copy(searchQuery = value))
        }

    var sortMode: BaGuideCatalogSortMode
        get() = sortModeFor(activeCatalogTab() ?: BaGuideCatalogTab.Student)
        set(value) {
            val tab = activeCatalogTab() ?: BaGuideCatalogTab.Student
            val current = snapshot()
            onSnapshotChange(
                current
                    .withSortMode(
                        tab = tab,
                        mode = value,
                    ).copy(sortMode = value),
            )
        }

    var showFilterPopup: Boolean
        get() = showFilterPopupState.value
        set(value) {
            showFilterPopupState.value = value
        }

    val selectedFilterOptions: Map<Int, Set<Int>>
        get() = selectedFilterOptionsFor(activeCatalogTab() ?: BaGuideCatalogTab.Student)

    val activeFilterCount: Int
        get() = selectedFilterOptions.values.count { it.isNotEmpty() }

    fun sortModeFor(tab: BaGuideCatalogTab): BaGuideCatalogSortMode = snapshot().sortModeFor(tab)

    fun selectedFilterOptionsFor(tab: BaGuideCatalogTab): Map<Int, Set<Int>> =
        decodeSelectedFilters(snapshot().selectedFiltersRawFor(tab))

    fun selectSortMode(mode: BaGuideCatalogSortMode) {
        sortMode = mode
    }

    fun openFilterPopup() {
        showFilterPopup = true
    }

    fun toggleFilterOption(
        filterId: Int,
        optionId: Int,
    ) {
        if (filterId <= 0 || optionId <= 0) return
        val next = selectedFilterOptions.toMutableMap()
        val options = next[filterId].orEmpty().toMutableSet()
        if (optionId in options) {
            options.remove(optionId)
        } else {
            options.add(optionId)
        }
        if (options.isEmpty()) {
            next.remove(filterId)
        } else {
            next[filterId] = options.toSet()
        }
        val tab = activeCatalogTab() ?: BaGuideCatalogTab.Student
        val encoded = encodeSelectedFilters(next)
        onSnapshotChange(
            snapshot()
                .withSelectedFiltersRaw(
                    tab = tab,
                    raw = encoded,
                ).copy(selectedFiltersRaw = encoded),
        )
    }

    fun clearFilters() {
        val tab = activeCatalogTab() ?: BaGuideCatalogTab.Student
        onSnapshotChange(
            snapshot()
                .withSelectedFiltersRaw(
                    tab = tab,
                    raw = "",
                ).copy(selectedFiltersRaw = ""),
        )
        showFilterPopup = false
    }
}

@Composable
internal fun rememberBaGuideCatalogFilterSortState(
    snapshot: BaGuideCatalogFilterSortSnapshot,
    activeCatalogTab: BaGuideCatalogTab?,
    onSnapshotChange: (BaGuideCatalogFilterSortSnapshot) -> Unit,
): BaGuideCatalogFilterSortState {
    val currentSnapshot = rememberUpdatedState(snapshot)
    val currentActiveCatalogTab = rememberUpdatedState(activeCatalogTab)
    val currentOnSnapshotChange = rememberUpdatedState(onSnapshotChange)
    val showFilterPopupState = remember { mutableStateOf(false) }
    return remember(
        showFilterPopupState,
    ) {
        BaGuideCatalogFilterSortState(
            snapshot = { currentSnapshot.value },
            onSnapshotChange = { currentOnSnapshotChange.value(it) },
            activeCatalogTab = { currentActiveCatalogTab.value },
            showFilterPopupState = showFilterPopupState,
        )
    }
}

private fun BaGuideCatalogFilterSortSnapshot.sortModeFor(tab: BaGuideCatalogTab): BaGuideCatalogSortMode =
    when (tab) {
        BaGuideCatalogTab.Student -> studentSortMode
        BaGuideCatalogTab.NpcSatellite -> npcSatelliteSortMode
    }

private fun BaGuideCatalogFilterSortSnapshot.withSortMode(
    tab: BaGuideCatalogTab,
    mode: BaGuideCatalogSortMode,
): BaGuideCatalogFilterSortSnapshot =
    when (tab) {
        BaGuideCatalogTab.Student -> copy(studentSortMode = mode)
        BaGuideCatalogTab.NpcSatellite -> copy(npcSatelliteSortMode = mode)
    }

private fun BaGuideCatalogFilterSortSnapshot.selectedFiltersRawFor(tab: BaGuideCatalogTab): String =
    when (tab) {
        BaGuideCatalogTab.Student -> studentSelectedFiltersRaw
        BaGuideCatalogTab.NpcSatellite -> npcSatelliteSelectedFiltersRaw
    }

private fun BaGuideCatalogFilterSortSnapshot.withSelectedFiltersRaw(
    tab: BaGuideCatalogTab,
    raw: String,
): BaGuideCatalogFilterSortSnapshot =
    when (tab) {
        BaGuideCatalogTab.Student -> copy(studentSelectedFiltersRaw = raw)
        BaGuideCatalogTab.NpcSatellite -> copy(npcSatelliteSelectedFiltersRaw = raw)
    }

private fun encodeSelectedFilters(filters: Map<Int, Set<Int>>): String =
    filters
        .toSortedMap()
        .mapNotNull { (filterId, optionIds) ->
            val encodedOptions =
                optionIds
                    .filter { it > 0 }
                    .sorted()
                    .joinToString(",")
            if (filterId <= 0 || encodedOptions.isBlank()) {
                null
            } else {
                "$filterId:$encodedOptions"
            }
        }.joinToString(";")

private fun decodeSelectedFilters(raw: String): Map<Int, Set<Int>> {
    if (raw.isBlank()) return emptyMap()
    return raw
        .split(";")
        .mapNotNull { part ->
            val filterId = part.substringBefore(":").trim().toIntOrNull() ?: return@mapNotNull null
            val options =
                part
                    .substringAfter(":", "")
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it > 0 }
                    .toSet()
            if (filterId > 0 && options.isNotEmpty()) {
                filterId to options
            } else {
                null
            }
        }.toMap()
}
