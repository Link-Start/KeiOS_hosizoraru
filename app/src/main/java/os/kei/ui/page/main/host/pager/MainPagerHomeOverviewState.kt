package os.kei.ui.page.main.host.pager

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import os.kei.feature.home.data.HomeOverviewRepository
import os.kei.feature.home.model.HomeBaOverview
import os.kei.feature.home.model.HomeGitHubOverview
import os.kei.feature.home.model.HomeMcpOverview
import os.kei.feature.home.model.HomeOverviewCard
import os.kei.feature.home.model.HomeOverviewSnapshot
import os.kei.mcp.server.McpServerManager

internal data class MainPagerHomeOverviewState(
    val homeMcpOverview: HomeMcpOverview,
    val homeGitHubOverview: HomeGitHubOverview,
    val homeBaOverview: HomeBaOverview,
    val visibleOverviewCards: Set<HomeOverviewCard>,
    val showCacheFreshnessInCards: Boolean,
    val onOverviewCardVisibilityChange: (HomeOverviewCard, Boolean) -> Unit,
    val onCacheFreshnessVisibilityChange: (Boolean) -> Unit
)

internal class MainPagerHomeOverviewViewModel(
    private val repository: HomeOverviewRepository
) : ViewModel() {
    val uiState: StateFlow<HomeOverviewSnapshot> = repository.observeOverview()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = HomeOverviewSnapshot()
        )

    fun refresh(reason: String) {
        repository.requestRefresh(reason)
    }

    fun setOverviewCardVisible(card: HomeOverviewCard, visible: Boolean) {
        viewModelScope.launch {
            repository.setOverviewCardVisible(card, visible)
        }
    }

    fun setCacheFreshnessVisibleInCards(visible: Boolean) {
        viewModelScope.launch {
            repository.setCacheFreshnessVisibleInCards(visible)
        }
    }

    companion object {
        fun factory(
            context: Context,
            mcpServerManager: McpServerManager
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MainPagerHomeOverviewViewModel::class.java)) {
                        return MainPagerHomeOverviewViewModel(
                            repository = HomeOverviewRepository(
                                context = context.applicationContext,
                                mcpUiState = mcpServerManager.uiState
                            )
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
                }
            }
        }
    }
}

@Composable
internal fun rememberMainPagerHomeOverviewState(
    mcpServerManager: McpServerManager,
    settingsReturnToken: Int
): MainPagerHomeOverviewState {
    val context = LocalContext.current
    val homeOverviewViewModel: MainPagerHomeOverviewViewModel = viewModel(
        key = "main_pager_home_overview",
        factory = remember(context, mcpServerManager) {
            MainPagerHomeOverviewViewModel.factory(
                context = context,
                mcpServerManager = mcpServerManager
            )
        }
    )
    val uiState by homeOverviewViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(settingsReturnToken) {
        if (settingsReturnToken <= 0) return@LaunchedEffect
        homeOverviewViewModel.refresh("settings_return_$settingsReturnToken")
    }
    val onOverviewCardVisibilityChange = remember(homeOverviewViewModel) {
        { card: HomeOverviewCard, visible: Boolean ->
            homeOverviewViewModel.setOverviewCardVisible(card, visible)
        }
    }
    val onCacheFreshnessVisibilityChange = remember(homeOverviewViewModel) {
        { visible: Boolean ->
            homeOverviewViewModel.setCacheFreshnessVisibleInCards(visible)
        }
    }
    return remember(uiState, onOverviewCardVisibilityChange, onCacheFreshnessVisibilityChange) {
        MainPagerHomeOverviewState(
            homeMcpOverview = uiState.mcpOverview,
            homeGitHubOverview = uiState.githubOverview,
            homeBaOverview = uiState.baOverview,
            visibleOverviewCards = uiState.visibleOverviewCards,
            showCacheFreshnessInCards = uiState.showCacheFreshnessInCards,
            onOverviewCardVisibilityChange = onOverviewCardVisibilityChange,
            onCacheFreshnessVisibilityChange = onCacheFreshnessVisibilityChange
        )
    }
}
