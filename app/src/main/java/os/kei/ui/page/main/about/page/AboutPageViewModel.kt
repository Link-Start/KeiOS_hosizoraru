package os.kei.ui.page.main.about.page

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.ui.page.main.about.model.AboutComponentEntry
import os.kei.ui.page.main.about.model.AboutPermissionEntry
import os.kei.ui.page.main.about.state.AboutPageSectionExpansionState

internal data class AboutPageChromeState(
    val selectedCategoryIndex: Int = 0,
    val bottomBarVisible: Boolean = true,
    val searchExpanded: Boolean = false,
    val searchQuery: String = "",
    val expansionState: AboutPageSectionExpansionState = AboutPageSectionExpansionState(),
) {
    val trimmedSearchQuery: String
        get() = searchQuery.trim()
}

private data class AboutPageDetailsInputs(
    val appLabel: String = "",
    val shizukuStatus: String = "",
    val notificationPermissionGranted: Boolean = false,
)

internal class AboutPageViewModel : ViewModel() {
    private val repository = AboutPageRepository()
    private var manifestJob: Job? = null
    private var shizukuJob: Job? = null
    private var searchTargetsJob: Job? = null

    private val _detailsState = MutableStateFlow(AboutPageDetailsState())
    val detailsState: StateFlow<AboutPageDetailsState> = _detailsState.asStateFlow()
    private val _chromeState = MutableStateFlow(AboutPageChromeState())
    val chromeState: StateFlow<AboutPageChromeState> = _chromeState.asStateFlow()

    private var lastInputs: AboutPageDetailsInputs? = null
    private var manifestKey: ManifestCacheKey? = null
    private var shizukuStatusKey: String? = null
    private var searchTargetsSignature: SearchTargetsSignature? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchState: StateFlow<AboutSearchUiState> =
        combine(
            _detailsState,
            _chromeState,
        ) { details, chrome ->
            details.searchTargets to chrome.searchQuery
        }.distinctUntilChanged()
            .mapLatest { (targets, query) ->
                repository.deriveSearchState(
                    targets = targets,
                    query = query,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = AboutSearchUiState(),
            )

    fun refreshDetails(
        context: Context,
        appLabel: String,
        shizukuStatus: String,
        notificationPermissionGranted: Boolean,
        shizukuApiUtils: ShizukuApiUtils,
    ) {
        val appContext = context.applicationContext
        val nextInputs =
            AboutPageDetailsInputs(
                appLabel = appLabel,
                shizukuStatus = shizukuStatus,
                notificationPermissionGranted = notificationPermissionGranted,
            )
        lastInputs = nextInputs

        // Manifest details only depend on context + notification grant.
        val manifestRequest = ManifestCacheKey(notificationPermissionGranted)
        if (manifestKey != manifestRequest) {
            manifestKey = manifestRequest
            manifestJob?.cancel()
            manifestJob =
                viewModelScope.launch {
                    val manifest =
                        repository.loadManifestDetails(
                            context = appContext,
                            notificationPermissionGranted = notificationPermissionGranted,
                        )
                    _detailsState.update { state ->
                        state.copy(
                            packageDetailInfo = manifest.packageDetailInfo,
                            permissionEntries = manifest.permissionEntries,
                            componentEntries = manifest.componentEntries,
                            loaded = true,
                        )
                    }
                    rebuildSearchTargetsIfNeeded(appContext)
                }
        }

        // Shizuku details only depend on shizuku status string + api utils.
        if (shizukuStatusKey != shizukuStatus) {
            shizukuStatusKey = shizukuStatus
            shizukuJob?.cancel()
            shizukuJob =
                viewModelScope.launch {
                    val shizukuDetailMap = repository.loadShizukuDetails(shizukuApiUtils)
                    _detailsState.update { state ->
                        if (state.shizukuDetailMap == shizukuDetailMap) {
                            state
                        } else {
                            state.copy(shizukuDetailMap = shizukuDetailMap)
                        }
                    }
                    rebuildSearchTargetsIfNeeded(appContext)
                }
        } else {
            // Status didn't change but appLabel might have — still try to rebuild targets.
            rebuildSearchTargetsIfNeeded(appContext)
        }
    }

    private fun rebuildSearchTargetsIfNeeded(context: Context) {
        val inputs = lastInputs ?: return
        val current = _detailsState.value
        if (!current.loaded) return
        val signature =
            SearchTargetsSignature(
                appLabel = inputs.appLabel,
                shizukuStatus = inputs.shizukuStatus,
                permissionEntries = current.permissionEntries,
                componentEntries = current.componentEntries,
            )
        if (searchTargetsSignature == signature) return
        searchTargetsSignature = signature
        searchTargetsJob?.cancel()
        searchTargetsJob =
            viewModelScope.launch {
                val targets =
                    repository.buildSearchTargets(
                        context = context,
                        appLabel = inputs.appLabel,
                        shizukuStatus = inputs.shizukuStatus,
                        permissionEntries = current.permissionEntries,
                        componentEntries = current.componentEntries,
                    )
                _detailsState.update { state ->
                    if (state.searchTargets === targets) state else state.copy(searchTargets = targets)
                }
            }
    }

    fun updateSelectedCategoryIndex(index: Int) {
        _chromeState.update { state ->
            state.copy(selectedCategoryIndex = index.coerceAtLeast(0))
        }
    }

    fun updateBottomBarVisible(visible: Boolean) {
        _chromeState.update { state ->
            if (state.bottomBarVisible == visible) {
                state
            } else {
                state.copy(bottomBarVisible = visible)
            }
        }
    }

    fun updateSearchExpanded(expanded: Boolean) {
        _chromeState.update { state -> state.copy(searchExpanded = expanded) }
    }

    fun updateSearchQuery(query: String) {
        val normalized = query.take(96)
        _chromeState.update { state -> state.copy(searchQuery = normalized) }
    }

    fun updateSectionExpanded(
        card: AboutSearchCard,
        expanded: Boolean,
    ) {
        _chromeState.update { state ->
            state.copy(
                expansionState = state.expansionState.withCardExpanded(card, expanded),
            )
        }
    }

    private data class ManifestCacheKey(val notificationPermissionGranted: Boolean)

    private data class SearchTargetsSignature(
        val appLabel: String,
        val shizukuStatus: String,
        val permissionEntries: List<AboutPermissionEntry>,
        val componentEntries: List<AboutComponentEntry>,
    )
}

private fun AboutPageSectionExpansionState.withCardExpanded(
    card: AboutSearchCard,
    expanded: Boolean,
): AboutPageSectionExpansionState =
    when (card) {
        AboutSearchCard.App -> copy(appExpanded = expanded)
        AboutSearchCard.Release -> copy(releaseExpanded = expanded)
        AboutSearchCard.GitHub -> copy(githubExpanded = expanded)
        AboutSearchCard.Runtime -> copy(runtimeExpanded = expanded)
        AboutSearchCard.Network -> copy(networkExpanded = expanded)
        AboutSearchCard.Media -> copy(mediaExpanded = expanded)
        AboutSearchCard.Permission -> copy(permissionExpanded = expanded)
        AboutSearchCard.Component -> copy(componentExpanded = expanded)
        AboutSearchCard.Build -> copy(buildExpanded = expanded)
        AboutSearchCard.Ui -> copy(uiFrameworkExpanded = expanded)
        AboutSearchCard.ProjectLicense -> copy(projectLicenseExpanded = expanded)
        AboutSearchCard.License -> copy(licenseExpanded = expanded)
        AboutSearchCard.Lab -> copy(componentLabExpanded = expanded)
    }
