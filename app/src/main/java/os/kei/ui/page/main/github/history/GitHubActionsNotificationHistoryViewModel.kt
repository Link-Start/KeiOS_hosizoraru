package os.kei.ui.page.main.github.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.ui.page.main.github.page.GitHubAppIconLoader
import os.kei.ui.page.main.github.page.GitHubAppIconUiState

internal data class GitHubActionsNotificationHistoryUiState(
    val loading: Boolean = true,
    val records: List<GitHubActionsNotificationHistoryUiRecord> = emptyList(),
    val totalRecordCount: Int = 0,
    val errorMessage: String = "",
    val filterMode: GitHubActionsHistoryFilterMode = GitHubActionsHistoryFilterMode.All,
    val sortMode: GitHubActionsHistorySortMode = GitHubActionsHistorySortMode.NotifiedAt,
    val sortDirection: GitHubActionsHistorySortDirection = GitHubActionsHistorySortDirection.Descending,
    val lastCleanupRemovedCount: Int? = null,
)

internal class GitHubActionsNotificationHistoryViewModel(
    private val repository: GitHubActionsNotificationHistoryRepository =
        GitHubActionsNotificationHistoryRepository(),
) : ViewModel() {
    private val appIconLoader = GitHubAppIconLoader(viewModelScope)
    private val historyOperationMutex = Mutex()
    private val _uiState = MutableStateFlow(GitHubActionsNotificationHistoryUiState())
    val uiState: StateFlow<GitHubActionsNotificationHistoryUiState> = _uiState.asStateFlow()
    val appIconState: StateFlow<GitHubAppIconUiState> = appIconLoader.state
    private var allRecords: List<GitHubActionsNotificationHistoryUiRecord> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            historyOperationMutex.withLock {
                _uiState.update { state ->
                    state.copy(
                        loading = true,
                        errorMessage = "",
                    )
                }
                val result = runCatching { repository.loadHistory() }
                result
                    .onSuccess { records ->
                        allRecords = records
                        updateDisplayRecords {
                            copy(
                                loading = false,
                                totalRecordCount = records.size,
                                errorMessage = "",
                                lastCleanupRemovedCount = null,
                            )
                        }
                    }.onFailure { error ->
                        _uiState.update { state ->
                            state.copy(
                                loading = false,
                                records = emptyList(),
                                totalRecordCount = allRecords.size,
                                errorMessage = error.message.orEmpty(),
                            )
                        }
                    }
            }
        }
    }

    fun requestAppIcons(
        context: Context,
        packageNames: List<String>,
    ) {
        appIconLoader.requestIcons(
            context = context.applicationContext,
            packageNames = packageNames,
        )
    }

    fun setFilterMode(value: GitHubActionsHistoryFilterMode) {
        updateDisplayRecords { copy(filterMode = value) }
    }

    fun setSortMode(value: GitHubActionsHistorySortMode) {
        updateDisplayRecords { copy(sortMode = value) }
    }

    fun setSortDirection(value: GitHubActionsHistorySortDirection) {
        updateDisplayRecords { copy(sortDirection = value) }
    }

    fun pruneOlderThan(age: GitHubActionsHistoryCleanupAge) {
        viewModelScope.launch {
            historyOperationMutex.withLock {
                _uiState.update { state ->
                    state.copy(
                        loading = true,
                        errorMessage = "",
                    )
                }
                val result = runCatching { repository.pruneOlderThanDays(age.days) }
                result
                    .onSuccess { removedCount ->
                        val records = repository.loadHistory()
                        allRecords = records
                        updateDisplayRecords {
                            copy(
                                loading = false,
                                totalRecordCount = records.size,
                                errorMessage = "",
                                lastCleanupRemovedCount = removedCount,
                            )
                        }
                    }.onFailure { error ->
                        _uiState.update { state ->
                            state.copy(
                                loading = false,
                                errorMessage = error.message.orEmpty(),
                            )
                        }
                    }
            }
        }
    }

    private fun updateDisplayRecords(
        transform: GitHubActionsNotificationHistoryUiState.() -> GitHubActionsNotificationHistoryUiState,
    ) {
        _uiState.update { previous ->
            val next = previous.transform()
            next.copy(
                records =
                    buildGitHubActionsHistoryDisplayRecords(
                        records = allRecords,
                        filterMode = next.filterMode,
                        sortMode = next.sortMode,
                        sortDirection = next.sortDirection,
                    ),
                totalRecordCount = allRecords.size,
            )
        }
    }
}
