package os.kei.ui.page.main.os

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard

internal data class OsPageRuntimeState(
    val sectionStates: Map<SectionKind, SectionState> = defaultOsSectionStates(),
    val cacheLoaded: Boolean = false,
    val cachePersisted: Boolean = false,
    val uiStatePersistenceReady: Boolean = false,
    val refreshing: Boolean = false,
    val refreshProgress: Float = 0f,
    val runningShellCommandCardIds: Set<String> = emptySet(),
)

@OptIn(FlowPreview::class)
internal class OsPageViewModel : ViewModel() {
    private companion object {
        const val QUERY_DEBOUNCE_MS = 180L
        const val MIN_FILTER_QUERY_LENGTH = 2
    }

    private val repository = OsPageRepository()
    internal val sectionLoadMutex = Mutex()
    internal val sectionLoadDeferreds: MutableMap<SectionKind, Deferred<List<InfoRow>>> = mutableMapOf()

    val persistentState: StateFlow<OsPagePersistentState> =
        repository
            .observePersistentState()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = repository.observePersistentState().value,
            )

    private val _queryInput = MutableStateFlow("")
    val queryInput: StateFlow<String> = _queryInput.asStateFlow()

    private val _queryApplied = MutableStateFlow("")
    val queryApplied: StateFlow<String> = _queryApplied.asStateFlow()

    private val _runtimeState = MutableStateFlow(OsPageRuntimeState())
    val runtimeState: StateFlow<OsPageRuntimeState> = _runtimeState.asStateFlow()

    init {
        viewModelScope.launch {
            _queryInput
                .debounce(QUERY_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { q ->
                    val normalized = q.trim()
                    _queryApplied.value =
                        when {
                            normalized.isBlank() -> ""
                            normalized.length < MIN_FILTER_QUERY_LENGTH -> ""
                            else -> normalized
                        }
                }
        }
    }

    fun updateQueryInput(value: String) {
        _queryInput.value = value
    }

    fun loadPersistentState(
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    ) {
        viewModelScope.launch {
            repository.loadPersistentState(
                googleSystemServiceDefaults = googleSystemServiceDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
            )
        }
    }

    fun reloadShellCommandCards() {
        viewModelScope.launch {
            repository.reloadShellCommandCards()
        }
    }

    fun updateVisibleCards(cards: Set<OsSectionCard>) {
        repository.updateVisibleCards(cards)
    }

    suspend fun saveVisibleCards(cards: Set<OsSectionCard>) {
        repository.saveVisibleCards(cards)
    }

    suspend fun hydrateInitialCache(
        isPageActive: Boolean,
        ensureLoad: suspend (SectionKind, Boolean) -> Unit,
    ) {
        var ensuredVisibleCards = persistentState.value.uiSnapshot.visibleCards
        if (!ensuredVisibleCards.contains(OsSectionCard.GOOGLE_SYSTEM_SERVICE)) {
            ensuredVisibleCards = ensuredVisibleCards + OsSectionCard.GOOGLE_SYSTEM_SERVICE
        }
        if (!ensuredVisibleCards.contains(OsSectionCard.SHELL_RUNNER)) {
            ensuredVisibleCards = ensuredVisibleCards + OsSectionCard.SHELL_RUNNER
        }
        if (ensuredVisibleCards != persistentState.value.uiSnapshot.visibleCards) {
            repository.saveVisibleCards(ensuredVisibleCards)
        }
        val visibleSections = visibleSectionKinds(ensuredVisibleCards)
        val snapshot = repository.readInfoCache(visibleSections)
        _runtimeState.update { state ->
            state.copy(
                sectionStates =
                    mapOf(
                        SectionKind.SYSTEM to
                            SectionState(
                                rows = if (visibleSections.contains(SectionKind.SYSTEM)) snapshot.cached.system else emptyList(),
                            ),
                        SectionKind.SECURE to
                            SectionState(
                                rows = if (visibleSections.contains(SectionKind.SECURE)) snapshot.cached.secure else emptyList(),
                            ),
                        SectionKind.GLOBAL to
                            SectionState(
                                rows = if (visibleSections.contains(SectionKind.GLOBAL)) snapshot.cached.global else emptyList(),
                            ),
                        SectionKind.ANDROID to
                            SectionState(
                                rows = if (visibleSections.contains(SectionKind.ANDROID)) snapshot.cached.android else emptyList(),
                            ),
                        SectionKind.JAVA to
                            SectionState(
                                rows = if (visibleSections.contains(SectionKind.JAVA)) snapshot.cached.java else emptyList(),
                            ),
                        SectionKind.LINUX to
                            SectionState(
                                rows = if (visibleSections.contains(SectionKind.LINUX)) snapshot.cached.linux else emptyList(),
                            ),
                    ),
                cachePersisted = snapshot.hasPersistedCache,
                cacheLoaded = true,
                uiStatePersistenceReady = true,
            )
        }
        if (isPageActive) {
            visibleSections.forEach { section ->
                ensureLoad(section, false)
            }
        }
    }

    suspend fun saveExpandedStateSnapshot(snapshot: OsUiSnapshot) {
        repository.saveExpandedStateSnapshot(snapshot)
    }

    fun replaceSectionStates(sectionStates: Map<SectionKind, SectionState>) {
        _runtimeState.update { state -> state.copy(sectionStates = sectionStates) }
    }

    fun updateSection(
        section: SectionKind,
        transform: (SectionState) -> SectionState,
    ) {
        _runtimeState.update { state ->
            val updated = state.sectionStates.toMutableMap()
            val old = updated[section] ?: SectionState()
            updated[section] = transform(old)
            state.copy(sectionStates = updated)
        }
    }

    fun invalidateShizukuSections() {
        listOf(
            SectionKind.SYSTEM,
            SectionKind.SECURE,
            SectionKind.GLOBAL,
            SectionKind.LINUX,
        ).forEach { section ->
            updateSection(section) { it.copy(loadedFresh = false) }
        }
    }

    fun updateCachePersisted(value: Boolean) {
        _runtimeState.update { state -> state.copy(cachePersisted = value) }
    }

    fun updateRefreshing(value: Boolean) {
        _runtimeState.update { state -> state.copy(refreshing = value) }
    }

    fun updateRefreshProgress(value: Float) {
        _runtimeState.update { state -> state.copy(refreshProgress = value.coerceIn(0f, 1f)) }
    }

    fun updateRunningShellCommandCardIds(value: Set<String>) {
        _runtimeState.update { state -> state.copy(runningShellCommandCardIds = value) }
    }

    fun updateActivityShortcutCards(cards: List<OsActivityShortcutCard>) {
        repository.updateActivityShortcutCards(cards)
    }

    fun updateShellCommandCards(cards: List<OsShellCommandCard>) {
        repository.updateShellCommandCards(cards)
    }

    fun updateTopInfoExpanded(value: Boolean) {
        repository.updateTopInfoExpanded(value)
    }

    fun updateShellRunnerExpanded(value: Boolean) {
        repository.updateShellRunnerExpanded(value)
    }

    fun updateSystemTableExpanded(value: Boolean) {
        repository.updateSystemTableExpanded(value)
    }

    fun updateSecureTableExpanded(value: Boolean) {
        repository.updateSecureTableExpanded(value)
    }

    fun updateGlobalTableExpanded(value: Boolean) {
        repository.updateGlobalTableExpanded(value)
    }

    fun updateAndroidPropsExpanded(value: Boolean) {
        repository.updateAndroidPropsExpanded(value)
    }

    fun updateJavaPropsExpanded(value: Boolean) {
        repository.updateJavaPropsExpanded(value)
    }

    fun updateLinuxEnvExpanded(value: Boolean) {
        repository.updateLinuxEnvExpanded(value)
    }
}

private fun defaultOsSectionStates(): Map<SectionKind, SectionState> =
    mapOf(
        SectionKind.SYSTEM to SectionState(),
        SectionKind.SECURE to SectionState(),
        SectionKind.GLOBAL to SectionState(),
        SectionKind.ANDROID to SectionState(),
        SectionKind.JAVA to SectionState(),
        SectionKind.LINUX to SectionState(),
    )
