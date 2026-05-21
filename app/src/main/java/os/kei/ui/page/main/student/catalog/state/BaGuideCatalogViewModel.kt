package os.kei.ui.page.main.student.catalog.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import kotlin.time.Duration.Companion.milliseconds

internal data class BaGuideCatalogDataUiState(
    val catalog: BaGuideCatalogBundle = BaGuideCatalogBundle.EMPTY,
    val loading: Boolean = false,
    val error: String? = null
)

private data class BaGuideCatalogBinding(
    val transitionAnimationsEnabled: Boolean,
    val initialFetchDelayMs: Int,
    val loadFailedText: String,
    val refreshFailedKeepCacheText: String
)

internal class BaGuideCatalogViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = BaGuideCatalogRepository()
    private var binding: BaGuideCatalogBinding? = null
    private var loadJob: Job? = null
    private var hydrateJob: Job? = null
    private var hydratedSyncedAtMs: Long = -1L
    private val listDerivationJobs = mutableMapOf<BaGuideCatalogTab, Job>()
    private val listDerivationInputs = mutableMapOf<BaGuideCatalogTab, BaGuideCatalogListInput>()
    private var studentBgmListDerivationJob: Job? = null
    private var studentBgmListDerivationInput: BaGuideStudentBgmListInput? = null
    private var favoriteBgmListDerivationJob: Job? = null
    private var favoriteBgmListDerivationInput: BaGuideFavoriteBgmListInput? = null
    private var studentBgmDisplayedDerivationJob: Job? = null
    private var studentBgmDisplayedDerivationInput: BaGuideStudentBgmDisplayedInput? = null

    private val _dataState = MutableStateFlow(BaGuideCatalogDataUiState())
    val dataState: StateFlow<BaGuideCatalogDataUiState> = _dataState.asStateFlow()
    val favoriteBgms: StateFlow<List<GuideBgmFavoriteItem>> =
        repository
            .bgmFavoritesFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = repository.bgmFavoritesSnapshot(),
            )
    private val _nativeBgmMediaNotificationEnabled =
        MutableStateFlow(repository.loadNativeBgmMediaNotificationEnabled())
    val nativeBgmMediaNotificationEnabled: StateFlow<Boolean> =
        _nativeBgmMediaNotificationEnabled.asStateFlow()

    private val _catalogListDerivedStates =
        MutableStateFlow<Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>>(emptyMap())
    val catalogListDerivedStates: StateFlow<Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>> =
        _catalogListDerivedStates.asStateFlow()

    private val _studentBgmListDerivedState =
        MutableStateFlow(BaGuideStudentBgmListDerivedState.Empty)
    val studentBgmListDerivedState: StateFlow<BaGuideStudentBgmListDerivedState> =
        _studentBgmListDerivedState.asStateFlow()

    private val _favoriteBgmListDerivedState =
        MutableStateFlow(BaGuideFavoriteBgmListDerivedState.Empty)
    val favoriteBgmListDerivedState: StateFlow<BaGuideFavoriteBgmListDerivedState> =
        _favoriteBgmListDerivedState.asStateFlow()

    private val _studentBgmDisplayedDerivedState =
        MutableStateFlow(BaGuideStudentBgmDisplayedDerivedState.Empty)
    val studentBgmDisplayedDerivedState: StateFlow<BaGuideStudentBgmDisplayedDerivedState> =
        _studentBgmDisplayedDerivedState.asStateFlow()

    fun bind(
        transitionAnimationsEnabled: Boolean,
        initialFetchDelayMs: Int,
        loadFailedText: String,
        refreshFailedKeepCacheText: String
    ) {
        val next = BaGuideCatalogBinding(
            transitionAnimationsEnabled = transitionAnimationsEnabled,
            initialFetchDelayMs = initialFetchDelayMs,
            loadFailedText = loadFailedText,
            refreshFailedKeepCacheText = refreshFailedKeepCacheText
        )
        if (binding == next && _dataState.value.catalog.entriesByTab.values.any { it.isNotEmpty() }) return
        binding = next
        loadCatalog(manualRefresh = false, allowInitialDelay = true)
    }

    fun requestRefresh() {
        loadCatalog(manualRefresh = true, allowInitialDelay = false)
    }

    suspend fun toggleBgmFavorite(item: GuideBgmFavoriteItem): Boolean =
        repository.toggleBgmFavorite(item)

    suspend fun removeBgmFavorite(audioUrl: String) {
        repository.removeBgmFavorite(audioUrl)
    }

    fun setNativeBgmMediaNotificationEnabled(enabled: Boolean) {
        if (_nativeBgmMediaNotificationEnabled.value == enabled) return
        _nativeBgmMediaNotificationEnabled.value = enabled
        viewModelScope.launch {
            repository.saveNativeBgmMediaNotificationEnabled(enabled)
        }
    }

    fun requestCatalogListDerivedState(input: BaGuideCatalogListInput) {
        val previousInput = listDerivationInputs[input.tab]
        val hasDerivedState = _catalogListDerivedStates.value.containsKey(input.tab)
        if (previousInput == input && hasDerivedState) return

        listDerivationInputs[input.tab] = input
        listDerivationJobs[input.tab]?.cancel()
        _catalogListDerivedStates.update { states ->
            val current = states[input.tab] ?: BaGuideCatalogListDerivedState.Empty
            states + (input.tab to current.copy(deriving = true))
        }
        listDerivationJobs[input.tab] =
            viewModelScope.launch {
                val derivedState = repository.deriveCatalogListState(input)
                if (listDerivationInputs[input.tab] != input) return@launch
                _catalogListDerivedStates.update { states ->
                    states + (input.tab to derivedState)
                }
            }
    }

    fun requestStudentBgmListDerivedState(input: BaGuideStudentBgmListInput) {
        val previousInput = studentBgmListDerivationInput
        if (previousInput == input && _studentBgmListDerivedState.value !== BaGuideStudentBgmListDerivedState.Empty) return

        studentBgmListDerivationInput = input
        studentBgmListDerivationJob?.cancel()
        _studentBgmListDerivedState.update { state ->
            state.copy(deriving = true)
        }
        studentBgmListDerivationJob =
            viewModelScope.launch {
                val derivedState = repository.deriveStudentBgmListState(input)
                if (studentBgmListDerivationInput != input) return@launch
                _studentBgmListDerivedState.value = derivedState
            }
    }

    fun requestFavoriteBgmListDerivedState(input: BaGuideFavoriteBgmListInput) {
        val previousInput = favoriteBgmListDerivationInput
        if (previousInput == input && _favoriteBgmListDerivedState.value !== BaGuideFavoriteBgmListDerivedState.Empty) return

        favoriteBgmListDerivationInput = input
        favoriteBgmListDerivationJob?.cancel()
        _favoriteBgmListDerivedState.update { state ->
            state.copy(deriving = true)
        }
        favoriteBgmListDerivationJob =
            viewModelScope.launch {
                val derivedState = repository.deriveFavoriteBgmListState(input)
                if (favoriteBgmListDerivationInput != input) return@launch
                _favoriteBgmListDerivedState.value = derivedState
            }
    }

    fun requestStudentBgmDisplayedDerivedState(input: BaGuideStudentBgmDisplayedInput) {
        val previousInput = studentBgmDisplayedDerivationInput
        if (previousInput == input && _studentBgmDisplayedDerivedState.value.input == input) return

        studentBgmDisplayedDerivationInput = input
        studentBgmDisplayedDerivationJob?.cancel()
        _studentBgmDisplayedDerivedState.update { state ->
            state.copy(
                input = input,
                deriving = true,
            )
        }
        studentBgmDisplayedDerivationJob =
            viewModelScope.launch {
                val derivedState = repository.deriveStudentBgmDisplayedState(input)
                if (studentBgmDisplayedDerivationInput != input) return@launch
                _studentBgmDisplayedDerivedState.value = derivedState
            }
    }

    private fun loadCatalog(
        manualRefresh: Boolean,
        allowInitialDelay: Boolean
    ) {
        val currentBinding = binding ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (
                allowInitialDelay &&
                currentBinding.transitionAnimationsEnabled &&
                currentBinding.initialFetchDelayMs > 0
            ) {
                delay(currentBinding.initialFetchDelayMs.milliseconds)
            }
            _dataState.update { state -> state.copy(loading = true) }
            val result = repository.loadCatalog(
                context = appContext,
                currentCatalog = _dataState.value.catalog,
                manualRefresh = manualRefresh,
                loadFailedText = currentBinding.loadFailedText,
                refreshFailedKeepCacheText = currentBinding.refreshFailedKeepCacheText
            )
            _dataState.value = BaGuideCatalogDataUiState(
                catalog = result.catalog,
                loading = false,
                error = result.error
            )
            hydrateReleaseDatesIfNeeded(result.catalog)
        }
    }

    private fun hydrateReleaseDatesIfNeeded(catalog: BaGuideCatalogBundle) {
        if (catalog.entriesByTab.values.all { it.isEmpty() }) return
        if (catalog.syncedAtMs <= 0L || catalog.syncedAtMs == hydratedSyncedAtMs) return
        hydratedSyncedAtMs = catalog.syncedAtMs
        hydrateJob?.cancel()
        hydrateJob = viewModelScope.launch {
            repository.hydrateReleaseDateIndex(
                source = catalog,
                onBundleUpdated = { updated ->
                    _dataState.update { state ->
                        if (state.catalog.syncedAtMs == catalog.syncedAtMs) {
                            state.copy(catalog = updated)
                        } else {
                            state
                        }
                    }
                }
            )
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        hydrateJob?.cancel()
        listDerivationJobs.values.forEach { job -> job.cancel() }
        studentBgmListDerivationJob?.cancel()
        favoriteBgmListDerivationJob?.cancel()
        studentBgmDisplayedDerivationJob?.cancel()
        super.onCleared()
    }
}
