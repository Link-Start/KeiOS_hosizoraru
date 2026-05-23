package os.kei.ui.page.main.host.pager

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.feature.home.data.HomeOverviewRepository
import os.kei.feature.home.model.HomeAppOverview
import os.kei.feature.home.model.HomeBaOverview
import os.kei.feature.home.model.HomeGitHubOverview
import os.kei.feature.home.model.HomeMcpOverview
import os.kei.feature.home.model.HomeOverviewCard
import os.kei.feature.home.model.HomeOverviewSnapshot
import os.kei.mcp.server.McpServerManager

@Immutable
internal data class MainPagerHomeOverviewState(
    val homeAppOverview: HomeAppOverview,
    val homeMcpOverview: HomeMcpOverview,
    val homeGitHubOverview: HomeGitHubOverview,
    val homeBaOverview: HomeBaOverview,
    val visibleOverviewCards: Set<HomeOverviewCard>,
    val showCacheFreshnessInCards: Boolean,
    val actionBarSelectedIndex: Int,
    val showBottomPageEditor: Boolean,
    val runtimeNowMs: Long,
    val onOverviewCardVisibilityChange: (HomeOverviewCard, Boolean) -> Unit,
    val onCacheFreshnessVisibilityChange: (Boolean) -> Unit,
    val onActionBarSelectedIndexChange: (Int) -> Unit,
    val onBottomPageEditorVisibleChange: (Boolean) -> Unit,
)

@Immutable
internal data class MainPagerHomeChromeUiState(
    val actionBarSelectedIndex: Int = 1,
    val showBottomPageEditor: Boolean = false,
)

internal class MainPagerHomeOverviewViewModel(
    private val repository: HomeOverviewRepository,
) : ViewModel() {
    private val runtimeTicker = MainPagerHomeRuntimeTicker(viewModelScope)
    private val _chromeUiState = MutableStateFlow(MainPagerHomeChromeUiState())
    val chromeUiState: StateFlow<MainPagerHomeChromeUiState> = _chromeUiState.asStateFlow()

    val uiState: StateFlow<HomeOverviewSnapshot> =
        repository
            .observeOverview()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = HomeOverviewSnapshot(),
            )
    val runtimeNowMs: StateFlow<Long> = runtimeTicker.nowMs

    fun refresh(reason: String) {
        repository.requestRefresh(reason)
    }

    fun setOverviewCardVisible(
        card: HomeOverviewCard,
        visible: Boolean,
    ) {
        viewModelScope.launch {
            repository.setOverviewCardVisible(card, visible)
        }
    }

    fun setCacheFreshnessVisibleInCards(visible: Boolean) {
        viewModelScope.launch {
            repository.setCacheFreshnessVisibleInCards(visible)
        }
    }

    fun setActionBarSelectedIndex(index: Int) {
        _chromeUiState.update { state ->
            if (state.actionBarSelectedIndex == index) {
                state
            } else {
                state.copy(actionBarSelectedIndex = index)
            }
        }
    }

    fun setBottomPageEditorVisible(visible: Boolean) {
        _chromeUiState.update { state ->
            if (state.showBottomPageEditor == visible) {
                state
            } else {
                state.copy(showBottomPageEditor = visible)
            }
        }
    }

    fun openBottomPageEditor() {
        _chromeUiState.update { state ->
            state.copy(
                actionBarSelectedIndex = 0,
                showBottomPageEditor = true,
            )
        }
    }

    fun requestRuntimeTicker(
        mcpOverview: HomeMcpOverview,
        runtime: MainPageRuntime,
    ) {
        runtimeTicker.request(
            mcpOverview = mcpOverview,
            runtime = runtime,
        )
    }

    companion object {
        fun factory(
            context: Context,
            mcpServerManager: McpServerManager,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MainPagerHomeOverviewViewModel::class.java)) {
                        return MainPagerHomeOverviewViewModel(
                            repository =
                                HomeOverviewRepository(
                                    context = context.applicationContext,
                                    mcpUiState = mcpServerManager.uiState,
                                ),
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
    settingsReturnToken: Int,
    homeRuntime: MainPageRuntime,
): MainPagerHomeOverviewState {
    val context = LocalContext.current
    val homeOverviewViewModel: MainPagerHomeOverviewViewModel =
        viewModel(
            key = "main_pager_home_overview",
            factory =
                remember(context, mcpServerManager) {
                    MainPagerHomeOverviewViewModel.factory(
                        context = context,
                        mcpServerManager = mcpServerManager,
                    )
                },
        )
    val uiState by homeOverviewViewModel.uiState.collectAsStateWithLifecycle()
    val chromeUiState by homeOverviewViewModel.chromeUiState.collectAsStateWithLifecycle()
    val runtimeNowMs by homeOverviewViewModel.runtimeNowMs.collectAsStateWithLifecycle()
    LaunchedEffect(settingsReturnToken) {
        if (settingsReturnToken <= 0) return@LaunchedEffect
        homeOverviewViewModel.refresh("settings_return_$settingsReturnToken")
    }
    LaunchedEffect(uiState.mcpOverview, homeRuntime) {
        homeOverviewViewModel.requestRuntimeTicker(
            mcpOverview = uiState.mcpOverview,
            runtime = homeRuntime,
        )
    }
    val onOverviewCardVisibilityChange =
        remember(homeOverviewViewModel) {
            { card: HomeOverviewCard, visible: Boolean ->
                homeOverviewViewModel.setOverviewCardVisible(card, visible)
            }
        }
    val onCacheFreshnessVisibilityChange =
        remember(homeOverviewViewModel) {
            { visible: Boolean ->
                homeOverviewViewModel.setCacheFreshnessVisibleInCards(visible)
            }
        }
    val onActionBarSelectedIndexChange =
        remember(homeOverviewViewModel) {
            { index: Int ->
                homeOverviewViewModel.setActionBarSelectedIndex(index)
            }
        }
    val onBottomPageEditorVisibleChange =
        remember(homeOverviewViewModel) {
            { visible: Boolean ->
                if (visible) {
                    homeOverviewViewModel.openBottomPageEditor()
                } else {
                    homeOverviewViewModel.setBottomPageEditorVisible(false)
                }
            }
        }
    return remember(
        uiState,
        chromeUiState,
        runtimeNowMs,
        onOverviewCardVisibilityChange,
        onCacheFreshnessVisibilityChange,
        onActionBarSelectedIndexChange,
        onBottomPageEditorVisibleChange,
    ) {
        MainPagerHomeOverviewState(
            homeMcpOverview = uiState.mcpOverview,
            homeAppOverview = uiState.appOverview,
            homeGitHubOverview = uiState.githubOverview,
            homeBaOverview = uiState.baOverview,
            visibleOverviewCards = uiState.visibleOverviewCards,
            showCacheFreshnessInCards = uiState.showCacheFreshnessInCards,
            actionBarSelectedIndex = chromeUiState.actionBarSelectedIndex,
            showBottomPageEditor = chromeUiState.showBottomPageEditor,
            runtimeNowMs = runtimeNowMs,
            onOverviewCardVisibilityChange = onOverviewCardVisibilityChange,
            onCacheFreshnessVisibilityChange = onCacheFreshnessVisibilityChange,
            onActionBarSelectedIndexChange = onActionBarSelectedIndexChange,
            onBottomPageEditorVisibleChange = onBottomPageEditorVisibleChange,
        )
    }
}
