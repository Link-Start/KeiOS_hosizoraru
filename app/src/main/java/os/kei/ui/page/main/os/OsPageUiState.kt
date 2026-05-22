package os.kei.ui.page.main.os

import androidx.compose.runtime.Immutable

@Immutable
internal data class OsPageQueryState(
    val input: String = "",
    val applied: String = "",
)

@Immutable
internal data class OsPageCoreUiState(
    val persistentState: OsPagePersistentState = OsPagePersistentState(),
    val runtimeState: OsPageRuntimeState = OsPageRuntimeState(),
    val activitySuggestionState: OsActivitySuggestionUiState = OsActivitySuggestionUiState(),
    val queryState: OsPageQueryState = OsPageQueryState(),
)

@Immutable
internal data class OsPageUiState(
    val persistentState: OsPagePersistentState = OsPagePersistentState(),
    val runtimeState: OsPageRuntimeState = OsPageRuntimeState(),
    val activitySuggestionState: OsActivitySuggestionUiState = OsActivitySuggestionUiState(),
    val queryInput: String = "",
    val queryApplied: String = "",
    val rowsDerivedState: OsPageRowsUiDerivedState = OsPageRowsUiDerivedState.Empty,
)
