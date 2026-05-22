package os.kei.ui.page.main.student.catalog.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

@Stable
internal class BaGuideCatalogFilterSortState(
    private val searchQueryState: MutableState<String>,
    private val sortModeState: MutableState<BaGuideCatalogSortMode>,
    private val showSortPopupState: MutableState<Boolean>,
    private val showFilterPopupState: MutableState<Boolean>,
    private val selectedFiltersRawState: MutableState<String>,
) {
    var searchQuery: String
        get() = searchQueryState.value
        set(value) {
            searchQueryState.value = value
        }

    var sortMode: BaGuideCatalogSortMode
        get() = sortModeState.value
        set(value) {
            sortModeState.value = value
        }

    var showSortPopup: Boolean
        get() = showSortPopupState.value
        set(value) {
            showSortPopupState.value = value
        }

    var showFilterPopup: Boolean
        get() = showFilterPopupState.value
        set(value) {
            showFilterPopupState.value = value
        }

    val selectedFilterOptions: Map<Int, Set<Int>>
        get() = decodeSelectedFilters(selectedFiltersRawState.value)

    val activeFilterCount: Int
        get() = selectedFilterOptions.values.count { it.isNotEmpty() }

    fun selectSortMode(mode: BaGuideCatalogSortMode) {
        sortMode = mode
        showSortPopup = false
    }

    fun openSortPopup() {
        showFilterPopup = false
        showSortPopup = true
    }

    fun openFilterPopup() {
        showSortPopup = false
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
        selectedFiltersRawState.value = encodeSelectedFilters(next)
    }

    fun clearFilters() {
        selectedFiltersRawState.value = ""
        showFilterPopup = false
    }
}

@Composable
internal fun rememberBaGuideCatalogFilterSortState(): BaGuideCatalogFilterSortState {
    val searchQueryState = rememberSaveable { mutableStateOf("") }
    val sortModeState = rememberSaveable { mutableStateOf(BaGuideCatalogSortMode.Default) }
    val showSortPopupState = remember { mutableStateOf(false) }
    val showFilterPopupState = remember { mutableStateOf(false) }
    val selectedFiltersRawState = rememberSaveable { mutableStateOf("") }
    return remember(
        searchQueryState,
        sortModeState,
        showSortPopupState,
        showFilterPopupState,
        selectedFiltersRawState,
    ) {
        BaGuideCatalogFilterSortState(
            searchQueryState = searchQueryState,
            sortModeState = sortModeState,
            showSortPopupState = showSortPopupState,
            showFilterPopupState = showFilterPopupState,
            selectedFiltersRawState = selectedFiltersRawState,
        )
    }
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
