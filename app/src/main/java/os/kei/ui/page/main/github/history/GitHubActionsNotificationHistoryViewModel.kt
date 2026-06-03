package os.kei.ui.page.main.github.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.feature.github.model.GitHubActionsNotificationHistoryRecord

internal data class GitHubActionsNotificationHistoryUiState(
    val loading: Boolean = true,
    val records: List<GitHubActionsNotificationHistoryRecord> = emptyList(),
    val errorMessage: String = "",
)

internal class GitHubActionsNotificationHistoryViewModel(
    private val repository: GitHubActionsNotificationHistoryRepository =
        GitHubActionsNotificationHistoryRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(GitHubActionsNotificationHistoryUiState())
    val uiState: StateFlow<GitHubActionsNotificationHistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    loading = true,
                    errorMessage = "",
                )
            }
            val result = runCatching { repository.loadHistory() }
            _uiState.value =
                GitHubActionsNotificationHistoryUiState(
                    loading = false,
                    records = result.getOrDefault(emptyList()),
                    errorMessage = result.exceptionOrNull()?.message.orEmpty(),
                )
        }
    }
}
