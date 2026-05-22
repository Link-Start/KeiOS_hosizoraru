package os.kei.ui.page.main.os

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.OsActivityCardEditMode
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.ShortcutSuggestionField
import os.kei.ui.page.main.os.state.OsCardImportTarget
import os.kei.ui.page.main.os.transfer.OsCardImportPreview

@OptIn(FlowPreview::class)
internal class OsPageViewModel : ViewModel() {
    private companion object {
        const val QUERY_DEBOUNCE_MS = 180L
        const val MIN_FILTER_QUERY_LENGTH = 2
    }

    private val repository = OsPageRepository()
    private val exportRepository = OsPageExportRepository()
    private val cardRepository = OsPageCardRepository()
    private val visibilityRepository = OsPageVisibilityRepository()
    private val shellCommandRepository = OsPageShellCommandRepository()
    private val refreshRepository = OsPageRefreshRepository()
    private val sectionLoadRepository = OsPageSectionLoadRepository()
    private val activityShortcutRepository = OsPageActivityShortcutRepository()
    private val activityIconRepository = OsActivityShortcutIconRepository()
    private var activitySuggestionJob: Job? = null
    private var rowsDerivationJob: Job? = null
    private var activityIconLoadingKeys: Set<String> = emptySet()

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

    private val _activitySuggestionState = MutableStateFlow(OsActivitySuggestionUiState())
    val activitySuggestionState: StateFlow<OsActivitySuggestionUiState> =
        _activitySuggestionState.asStateFlow()

    private val _activityIconState = MutableStateFlow(OsActivityShortcutIconUiState())
    val activityIconState: StateFlow<OsActivityShortcutIconUiState> =
        _activityIconState.asStateFlow()

    private val _events = MutableSharedFlow<OsPageEvent>(replay = 0, extraBufferCapacity = 8)
    val events: SharedFlow<OsPageEvent> = _events.asSharedFlow()

    private val _rowsDerivedState = MutableStateFlow(OsPageRowsUiDerivedState.Empty)
    val rowsDerivedState: StateFlow<OsPageRowsUiDerivedState> = _rowsDerivedState.asStateFlow()

    private val queryState: StateFlow<OsPageQueryState> =
        combine(queryInput, queryApplied) { input, applied ->
            OsPageQueryState(
                input = input,
                applied = applied,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = OsPageQueryState(),
        )

    private val coreUiState: StateFlow<OsPageCoreUiState> =
        combine(
            persistentState,
            runtimeState,
            activitySuggestionState,
            queryState,
        ) { persistent, runtime, activitySuggestion, query ->
            OsPageCoreUiState(
                persistentState = persistent,
                runtimeState = runtime,
                activitySuggestionState = activitySuggestion,
                queryState = query,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = OsPageCoreUiState(),
        )

    val uiState: StateFlow<OsPageUiState> =
        combine(coreUiState, rowsDerivedState) { core, rows ->
            OsPageUiState(
                persistentState = core.persistentState,
                runtimeState = core.runtimeState,
                activitySuggestionState = core.activitySuggestionState,
                queryInput = core.queryState.input,
                queryApplied = core.queryState.applied,
                rowsDerivedState = rows,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = OsPageUiState(),
        )
    private val transferCoordinator =
        OsPageTransferCoordinator(
            scope = viewModelScope,
            repository = repository,
            exportRepository = exportRepository,
            persistentState = persistentState,
            runtimeState = runtimeState,
            runtimeMutableState = _runtimeState,
            events = _events,
        )
    private val cardCoordinator =
        OsPageCardCoordinator(
            scope = viewModelScope,
            repository = repository,
            cardRepository = cardRepository,
            visibilityRepository = visibilityRepository,
            shellCommandRepository = shellCommandRepository,
            persistentState = persistentState,
            runtimeState = runtimeState,
            runtimeMutableState = _runtimeState,
            events = _events,
        )

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
        viewModelScope.launch {
            combine(_queryApplied, persistentState, _runtimeState) { query, persistent, runtime ->
                buildRowsDerivationInput(
                    queryApplied = query,
                    uiSnapshot = persistent.uiSnapshot,
                    sectionStates = runtime.sectionStates,
                )
            }.distinctUntilChanged()
                .collectLatest { input ->
                    applyRowsDerivedState(input)
                }
        }
    }

    fun updateQueryInput(value: String) {
        _queryInput.value = value
    }

    fun loadPersistentState(
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
        builtInShellCommandCards: List<OsShellCommandCard>,
    ) {
        viewModelScope.launch {
            repository.loadPersistentState(
                googleSystemServiceDefaults = googleSystemServiceDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
                builtInShellCommandCards = builtInShellCommandCards,
            )
        }
    }

    fun reloadShellCommandCards(builtInShellCommandCards: List<OsShellCommandCard> = emptyList()) {
        viewModelScope.launch {
            repository.reloadShellCommandCards(
                builtInShellCommandCards = builtInShellCommandCards,
            )
        }
    }

    fun openActivityShortcutCard(
        card: OsActivityShortcutCard,
        defaults: OsGoogleSystemServiceConfig,
    ) {
        viewModelScope.launch {
            try {
                val normalized =
                    activityShortcutRepository.normalizeForOpen(
                        card = card,
                        defaults = defaults,
                    )
                if (normalized.packageName.isBlank()) {
                    _events.emit(OsPageEvent.ActivityShortcutInvalidTarget)
                    return@launch
                }
                _events.emit(OsPageEvent.LaunchActivityShortcut(normalized))
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.OperationFailed(error))
            }
        }
    }

    fun openActivityShortcutCardEditor(
        card: OsActivityShortcutCard,
        defaults: OsGoogleSystemServiceConfig,
    ) {
        viewModelScope.launch {
            try {
                val request =
                    activityShortcutRepository.buildEditRequest(
                        card = card,
                        defaults = defaults,
                    )
                _events.emit(OsPageEvent.ShowActivityShortcutEditor(request))
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.OperationFailed(error))
            }
        }
    }

    fun openAddActivityShortcutCardEditor(defaults: OsGoogleSystemServiceConfig) {
        viewModelScope.launch {
            try {
                val request = activityShortcutRepository.buildAddRequest(defaults)
                _events.emit(OsPageEvent.ShowActivityShortcutEditor(request))
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.OperationFailed(error))
            }
        }
    }

    fun openShellCommandCardEditor(card: OsShellCommandCard) {
        _events.tryEmit(OsPageEvent.ShowShellCommandCardEditor(card))
    }

    fun requestActivitySuggestions(
        context: Context,
        show: Boolean,
        target: ShortcutSuggestionField,
        packageName: String,
    ) {
        activitySuggestionJob?.cancel()
        if (!show) {
            _activitySuggestionState.update { state ->
                state.copy(
                    packageSuggestionsLoading = false,
                    classSuggestionsLoading = false,
                )
            }
            return
        }
        val appContext = context.applicationContext
        activitySuggestionJob =
            viewModelScope.launch {
                when (target) {
                    ShortcutSuggestionField.PackageName -> {
                        _activitySuggestionState.update { state ->
                            state.copy(packageSuggestionsLoading = true)
                        }
                        val suggestions =
                            runCatching {
                                repository.loadActivityShortcutPackageSuggestions(appContext)
                            }.getOrDefault(emptyList())
                        _activitySuggestionState.update { state ->
                            state.copy(
                                packageSuggestions = suggestions,
                                packageSuggestionsLoading = false,
                            )
                        }
                    }

                    ShortcutSuggestionField.ClassName -> {
                        val normalizedPackageName = packageName.trim()
                        if (normalizedPackageName.isBlank()) {
                            _activitySuggestionState.update { state ->
                                state.copy(
                                    classSuggestions = emptyList(),
                                    classSuggestionsLoading = false,
                                )
                            }
                            return@launch
                        }
                        _activitySuggestionState.update { state ->
                            state.copy(classSuggestionsLoading = true)
                        }
                        val suggestions =
                            runCatching {
                                repository.loadActivityShortcutClassSuggestions(
                                    context = appContext,
                                    packageName = normalizedPackageName,
                                )
                            }.getOrDefault(emptyList())
                        _activitySuggestionState.update { state ->
                            state.copy(
                                classSuggestions = suggestions,
                                classSuggestionsLoading = false,
                            )
                        }
                    }

                    else -> {
                        _activitySuggestionState.update { state ->
                            state.copy(
                                packageSuggestionsLoading = false,
                                classSuggestionsLoading = false,
                            )
                        }
                    }
                }
            }
    }

    fun requestRowsDerivedState(input: OsPageRowsDerivationInput) {
        rowsDerivationJob?.cancel()
        rowsDerivationJob =
            viewModelScope.launch {
                applyRowsDerivedState(input)
            }
    }

    fun requestActivityShortcutIcons(
        context: Context,
        requests: List<OsActivityShortcutIconRequest>,
    ) {
        val normalizedRequests =
            requests
                .filter { request ->
                    request.packageName.isNotBlank() && request.className.isNotBlank()
                }.distinctBy { request ->
                    osActivityShortcutIconKey(
                        packageName = request.packageName,
                        className = request.className,
                    )
                }
        if (normalizedRequests.isEmpty()) return

        val currentState = _activityIconState.value
        val missingRequests =
            normalizedRequests.filter { request ->
                val key =
                    osActivityShortcutIconKey(
                        packageName = request.packageName,
                        className = request.className,
                    )
                !currentState.bitmaps.containsKey(key) &&
                    !currentState.missingKeys.contains(key) &&
                    !activityIconLoadingKeys.contains(key) &&
                    !activityIconRepository.isMissing(key)
            }
        if (missingRequests.isEmpty()) return

        val requestedKeys =
            missingRequests
                .map { request ->
                    osActivityShortcutIconKey(
                        packageName = request.packageName,
                        className = request.className,
                    )
                }.toSet()
        activityIconLoadingKeys += requestedKeys
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val result =
                    activityIconRepository.loadActivityIcons(
                        context = appContext,
                        requests = missingRequests,
                    )
                _activityIconState.update { state ->
                    state.copy(
                        bitmaps = state.bitmaps + result.bitmaps,
                        missingKeys = state.missingKeys + result.missingKeys,
                    )
                }
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _activityIconState.update { state ->
                    state.copy(missingKeys = state.missingKeys + requestedKeys)
                }
            } finally {
                activityIconLoadingKeys -= requestedKeys
            }
        }
    }

    private suspend fun applyRowsDerivedState(input: OsPageRowsDerivationInput) {
        val current = _rowsDerivedState.value
        if (current.input == input && !current.deriving) return
        _rowsDerivedState.update { state ->
            state.copy(
                input = input,
                deriving = true,
            )
        }
        _rowsDerivedState.value = repository.buildRowsDerivedState(input)
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

    suspend fun ensureSectionLoaded(
        section: SectionKind,
        forceRefresh: Boolean,
        context: Context,
        shizukuStatus: String,
        shizukuApiUtils: ShizukuApiUtils,
    ) {
        val currentState = runtimeState.value.sectionStates[section] ?: SectionState()
        val visibleCards = persistentState.value.uiSnapshot.visibleCards
        if (!sectionLoadRepository.shouldLoad(section, forceRefresh, visibleCards, currentState)) return
        updateSection(section) { it.copy(loading = true, loadFailed = false) }
        try {
            when (
                val result =
                    sectionLoadRepository.loadSection(
                        section = section,
                        forceRefresh = forceRefresh,
                        visibleCardsProvider = { persistentState.value.uiSnapshot.visibleCards },
                        context = context.applicationContext,
                        shizukuStatus = shizukuStatus,
                        shizukuApiUtils = shizukuApiUtils,
                    )
            ) {
                OsSectionLoadResult.Joined -> {
                    Unit
                }

                is OsSectionLoadResult.Loaded -> {
                    updateSection(section) {
                        it.copy(
                            rows = result.rows,
                            loading = false,
                            loadedFresh = true,
                            loadFailed = false,
                        )
                    }
                    _runtimeState.update { state -> state.copy(cachePersisted = result.cachePersisted) }
                }
            }
        } catch (error: Throwable) {
            error.rethrowIfCancellation()
            updateSection(section) { it.copy(loading = false, loadFailed = true) }
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

    fun applySectionCardVisibility(
        card: OsSectionCard,
        visible: Boolean,
        ensureLoad: suspend (SectionKind, Boolean) -> Unit,
    ) {
        val updatedVisibleCards =
            visibilityRepository.updatedVisibleCards(
                currentVisibleCards = persistentState.value.uiSnapshot.visibleCards,
                card = card,
                visible = visible,
            )
        repository.updateVisibleCards(updatedVisibleCards)
        applyHiddenSectionUiState(card = card, visible = visible)
        viewModelScope.launch {
            try {
                val cachePersisted =
                    visibilityRepository.persistSectionCardVisibility(
                        card = card,
                        visible = visible,
                        visibleCards = updatedVisibleCards,
                    )
                _runtimeState.update { state -> state.copy(cachePersisted = cachePersisted) }
                if (visible) {
                    sectionKindByCard(card)?.let { section ->
                        ensureLoad(section, true)
                    }
                }
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.OperationFailed(error))
            }
        }
    }

    fun applyActivityCardVisibility(
        cardId: String,
        visible: Boolean,
        defaults: OsGoogleSystemServiceConfig,
    ) = cardCoordinator.applyActivityCardVisibility(
        cardId = cardId,
        visible = visible,
        defaults = defaults,
    )

    fun applyShellCommandCardVisibility(
        cardId: String,
        visible: Boolean,
    ) = cardCoordinator.applyShellCommandCardVisibility(
        cardId = cardId,
        visible = visible,
    )

    fun runShellCommandCard(
        card: OsShellCommandCard,
        shizukuApiUtils: ShizukuApiUtils,
        shellRunNoOutputText: String,
    ) = cardCoordinator.runShellCommandCard(
        card = card,
        shizukuApiUtils = shizukuApiUtils,
        shellRunNoOutputText = shellRunNoOutputText,
    )

    fun refreshAllSections(ensureLoad: suspend (SectionKind, Boolean) -> Unit) {
        if (runtimeState.value.refreshing) return
        viewModelScope.launch {
            _runtimeState.update { state ->
                state.copy(
                    refreshing = true,
                    refreshProgress = 0f,
                )
            }
            try {
                val targets =
                    refreshRepository.refreshableSections(
                        persistentState.value.uiSnapshot.visibleCards,
                    )
                val sectionCount = targets.size.coerceAtLeast(1)
                targets.forEachIndexed { index, section ->
                    ensureLoad(section, true)
                    _runtimeState.update { state ->
                        state.copy(refreshProgress = (index + 1).toFloat() / sectionCount.toFloat())
                    }
                }
                _events.emit(OsPageEvent.RefreshCompleted(refreshed = targets.isNotEmpty()))
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.OperationFailed(error))
            } finally {
                _runtimeState.update { state -> state.copy(refreshing = false) }
            }
        }
    }

    fun prepareActivityCardsExport(defaults: OsGoogleSystemServiceConfig) = transferCoordinator.prepareActivityCardsExport(defaults)

    fun prepareShellCardsExport() = transferCoordinator.prepareShellCardsExport()

    fun prepareSectionCardExport(
        card: OsSectionCard,
        context: Context,
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        shizukuStatus: String,
        ensureLoad: suspend (SectionKind, Boolean) -> Unit,
    ) = transferCoordinator.prepareSectionCardExport(
        card = card,
        context = context,
        googleSystemServiceDefaults = googleSystemServiceDefaults,
        shizukuStatus = shizukuStatus,
        ensureLoad = ensureLoad,
    )

    fun writeCardExportContent(
        contentResolver: ContentResolver,
        uri: Uri,
        content: String,
    ) = transferCoordinator.writeCardExportContent(
        contentResolver = contentResolver,
        uri = uri,
        content = content,
    )

    fun requestCardImportPreview(
        contentResolver: ContentResolver,
        uri: Uri,
        target: OsCardImportTarget,
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        googleSettingsBuiltInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    ) = transferCoordinator.requestCardImportPreview(
        contentResolver = contentResolver,
        uri = uri,
        target = target,
        googleSystemServiceDefaults = googleSystemServiceDefaults,
        googleSettingsBuiltInSampleDefaults = googleSettingsBuiltInSampleDefaults,
        builtInActivityShortcutCards = builtInActivityShortcutCards,
    )

    fun confirmCardImport(
        preview: OsCardImportPreview,
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        googleSettingsBuiltInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    ) = transferCoordinator.confirmCardImport(
        preview = preview,
        googleSystemServiceDefaults = googleSystemServiceDefaults,
        googleSettingsBuiltInSampleDefaults = googleSettingsBuiltInSampleDefaults,
        builtInActivityShortcutCards = builtInActivityShortcutCards,
    )

    fun saveShellCommandCardEdit(
        cardId: String,
        title: String,
        subtitle: String,
        command: String,
    ) = cardCoordinator.saveShellCommandCardEdit(
        cardId = cardId,
        title = title,
        subtitle = subtitle,
        command = command,
    )

    fun deleteShellCommandCard(cardId: String) = cardCoordinator.deleteShellCommandCard(cardId)

    fun saveActivityShortcutCard(
        editMode: OsActivityCardEditMode,
        editingCardId: String?,
        draft: OsGoogleSystemServiceConfig,
        defaults: OsGoogleSystemServiceConfig,
    ) = cardCoordinator.saveActivityShortcutCard(
        editMode = editMode,
        editingCardId = editingCardId,
        draft = draft,
        defaults = defaults,
    )

    fun deleteActivityShortcutCard(
        cardId: String,
        defaults: OsGoogleSystemServiceConfig,
    ) = cardCoordinator.deleteActivityShortcutCard(
        cardId = cardId,
        defaults = defaults,
    )

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

    private fun applyHiddenSectionUiState(
        card: OsSectionCard,
        visible: Boolean,
    ) {
        if (visible) return
        when (card) {
            OsSectionCard.TOP_INFO -> {
                repository.updateTopInfoExpanded(false)
            }

            OsSectionCard.SHELL_RUNNER -> {
                repository.updateShellRunnerExpanded(false)
            }

            OsSectionCard.GOOGLE_SYSTEM_SERVICE -> {
                Unit
            }

            OsSectionCard.SYSTEM -> {
                repository.updateSystemTableExpanded(false)
                updateSection(SectionKind.SYSTEM) { SectionState() }
            }

            OsSectionCard.SECURE -> {
                repository.updateSecureTableExpanded(false)
                updateSection(SectionKind.SECURE) { SectionState() }
            }

            OsSectionCard.GLOBAL -> {
                repository.updateGlobalTableExpanded(false)
                updateSection(SectionKind.GLOBAL) { SectionState() }
            }

            OsSectionCard.ANDROID -> {
                repository.updateAndroidPropsExpanded(false)
                updateSection(SectionKind.ANDROID) { SectionState() }
            }

            OsSectionCard.JAVA -> {
                repository.updateJavaPropsExpanded(false)
                updateSection(SectionKind.JAVA) { SectionState() }
            }

            OsSectionCard.LINUX -> {
                repository.updateLinuxEnvExpanded(false)
                updateSection(SectionKind.LINUX) { SectionState() }
            }
        }
    }

    override fun onCleared() {
        activitySuggestionJob?.cancel()
        rowsDerivationJob?.cancel()
        activityIconLoadingKeys = emptySet()
        super.onCleared()
    }
}

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
