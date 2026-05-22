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
import os.kei.ui.page.main.os.shell.OsShellCardImportMergeResult
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.OsActivityCardImportMergeResult
import os.kei.ui.page.main.os.shortcut.OsActivityCardEditMode
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.ShortcutActivityClassOption
import os.kei.ui.page.main.os.shortcut.ShortcutInstalledAppOption
import os.kei.ui.page.main.os.shortcut.ShortcutSuggestionField
import os.kei.ui.page.main.os.state.OsCardImportTarget
import os.kei.ui.page.main.os.transfer.OsActivityCardImportPayload
import os.kei.ui.page.main.os.transfer.OsCardImportError
import os.kei.ui.page.main.os.transfer.OsCardImportException
import os.kei.ui.page.main.os.transfer.OsCardImportPreview
import os.kei.ui.page.main.os.transfer.OsShellCardImportPayload
import os.kei.ui.page.main.os.transfer.OsUnknownCardImportPayload

internal data class OsPageRuntimeState(
    val sectionStates: Map<SectionKind, SectionState> = defaultOsSectionStates(),
    val cacheLoaded: Boolean = false,
    val cachePersisted: Boolean = false,
    val uiStatePersistenceReady: Boolean = false,
    val refreshing: Boolean = false,
    val refreshProgress: Float = 0f,
    val runningShellCommandCardIds: Set<String> = emptySet(),
    val exportingCard: OsSectionCard? = null,
)

internal data class OsActivitySuggestionUiState(
    val packageSuggestions: List<ShortcutInstalledAppOption> = emptyList(),
    val packageSuggestionsLoading: Boolean = false,
    val classSuggestions: List<ShortcutActivityClassOption> = emptyList(),
    val classSuggestionsLoading: Boolean = false,
)

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
    private var activitySuggestionJob: Job? = null
    private var rowsDerivationJob: Job? = null

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
            when (val result = sectionLoadRepository.loadSection(
                section = section,
                forceRefresh = forceRefresh,
                visibleCardsProvider = { persistentState.value.uiSnapshot.visibleCards },
                context = context.applicationContext,
                shizukuStatus = shizukuStatus,
                shizukuApiUtils = shizukuApiUtils,
            )) {
                OsSectionLoadResult.Joined -> Unit
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
    ) {
        viewModelScope.launch {
            try {
                val updatedCards =
                    visibilityRepository.setActivityCardVisible(
                        cards = persistentState.value.activityShortcutCards,
                        cardId = cardId,
                        visible = visible,
                        defaults = defaults,
                    )
                repository.updateActivityShortcutCards(updatedCards)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.OperationFailed(error))
            }
        }
    }

    fun applyShellCommandCardVisibility(
        cardId: String,
        visible: Boolean,
    ) {
        viewModelScope.launch {
            try {
                val updatedCards =
                    visibilityRepository.setShellCommandCardVisible(
                        cardId = cardId,
                        visible = visible,
                    )
                repository.updateShellCommandCards(updatedCards)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.OperationFailed(error))
            }
        }
    }

    fun runShellCommandCard(
        card: OsShellCommandCard,
        shizukuApiUtils: ShizukuApiUtils,
        shellRunNoOutputText: String,
    ) {
        val command = card.command.trim()
        if (command.isBlank()) {
            _events.tryEmit(OsPageEvent.ShellCommandCardCommandRequired)
            return
        }
        if (runtimeState.value.runningShellCommandCardIds.contains(card.id)) return
        if (!shizukuApiUtils.canUseCommand()) {
            shizukuApiUtils.requestPermissionIfNeeded()
            _events.tryEmit(OsPageEvent.ShellCommandCardNoPermission)
            return
        }
        _runtimeState.update { state ->
            state.copy(runningShellCommandCardIds = state.runningShellCommandCardIds + card.id)
        }
        viewModelScope.launch {
            try {
                val output =
                    shellCommandRepository
                        .runCommand(
                            shizukuApiUtils = shizukuApiUtils,
                            command = command,
                        ).orEmpty()
                        .trim()
                        .ifBlank { shellRunNoOutputText }
                val updatedCards =
                    shellCommandRepository.saveRunResult(
                        cardId = card.id,
                        output = output,
                    )
                repository.updateShellCommandCards(updatedCards)
                _events.emit(OsPageEvent.ShellCommandCardRunCompleted)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.ShellCommandCardRunFailed(error))
            } finally {
                _runtimeState.update { state ->
                    state.copy(runningShellCommandCardIds = state.runningShellCommandCardIds - card.id)
                }
            }
        }
    }

    fun refreshAllSections(
        ensureLoad: suspend (SectionKind, Boolean) -> Unit,
    ) {
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

    fun prepareActivityCardsExport(
        defaults: OsGoogleSystemServiceConfig,
    ) {
        viewModelScope.launch {
            try {
                val content =
                    repository.buildActivityCardsExportJson(
                        cards = persistentState.value.activityShortcutCards,
                        defaults = defaults,
                    )
                _events.emit(
                    OsPageEvent.LaunchExportDocument(
                        fileName = "keios-os-activity-cards.json",
                        content = content,
                    ),
                )
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.OperationFailed(error))
            }
        }
    }

    fun prepareShellCardsExport() {
        viewModelScope.launch {
            try {
                val content =
                    repository.buildShellCardsExportJson(
                        cards = persistentState.value.shellCommandCards,
                    )
                _events.emit(
                    OsPageEvent.LaunchExportDocument(
                        fileName = "keios-os-shell-cards.json",
                        content = content,
                    ),
                )
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.OperationFailed(error))
            }
        }
    }

    fun prepareSectionCardExport(
        card: OsSectionCard,
        context: Context,
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        shizukuStatus: String,
        ensureLoad: suspend (SectionKind, Boolean) -> Unit,
    ) {
        if (runtimeState.value.exportingCard != null) return
        _runtimeState.update { state -> state.copy(exportingCard = card) }
        viewModelScope.launch {
            try {
                when (card) {
                    OsSectionCard.TOP_INFO -> {
                        visibleSectionKinds(persistentState.value.uiSnapshot.visibleCards).forEach { section ->
                            ensureLoad(section, false)
                        }
                    }

                    else -> {
                        sectionKindByCard(card)?.let { section ->
                            ensureLoad(section, false)
                        }
                    }
                }
                val document =
                    exportRepository.buildSectionCardExport(
                        OsPageSectionCardExportRequest(
                            card = card,
                            sectionStates = runtimeState.value.sectionStates,
                            activityShortcutCards = persistentState.value.activityShortcutCards,
                            googleSystemServiceDefaults = googleSystemServiceDefaults,
                            context = context.applicationContext,
                            shizukuStatus = shizukuStatus,
                        ),
                    )
                _events.emit(
                    OsPageEvent.LaunchExportDocument(
                        fileName = document.fileName,
                        content = document.content,
                    ),
                )
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.OperationFailed(error))
            } finally {
                _runtimeState.update { state -> state.copy(exportingCard = null) }
            }
        }
    }

    fun writeCardExportContent(
        contentResolver: ContentResolver,
        uri: Uri,
        content: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                repository.writeExportContent(
                    contentResolver = contentResolver,
                    uri = uri,
                    content = content,
                )
                onSuccess()
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                onFailure(error)
            }
        }
    }

    fun requestCardImportPreview(
        contentResolver: ContentResolver,
        uri: Uri,
        target: OsCardImportTarget,
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        googleSettingsBuiltInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
        onPreview: (OsCardImportPreview) -> Unit,
        onFailure: (Throwable) -> Unit,
        onComplete: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val raw =
                    repository.readImportContent(
                        contentResolver = contentResolver,
                        uri = uri,
                    )
                val state = persistentState.value
                val preview =
                    repository.buildCardImportPreview(
                        raw = raw,
                        target = target,
                        activityShortcutCards = state.activityShortcutCards,
                        shellCommandCards = state.shellCommandCards,
                        googleSystemServiceDefaults = googleSystemServiceDefaults,
                        googleSettingsBuiltInSampleDefaults = googleSettingsBuiltInSampleDefaults,
                        builtInActivityShortcutCards = builtInActivityShortcutCards,
                    )
                onPreview(preview)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                onFailure(error)
            } finally {
                onComplete()
            }
        }
    }

    fun confirmCardImport(
        preview: OsCardImportPreview,
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        googleSettingsBuiltInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
        onActivityImported: (OsActivityCardImportMergeResult) -> Unit,
        onShellImported: (OsShellCardImportMergeResult) -> Unit,
        onFailure: (Throwable) -> Unit,
        onComplete: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                when (val payload = preview.payload) {
                    is OsActivityCardImportPayload -> {
                        val result =
                            repository.applyActivityCardImport(
                                payload = payload,
                                existingCards = persistentState.value.activityShortcutCards,
                                defaults = googleSystemServiceDefaults,
                                builtInSampleDefaults = googleSettingsBuiltInSampleDefaults,
                                builtInActivityShortcutCards = builtInActivityShortcutCards,
                            )
                        repository.updateActivityShortcutCards(result.cards)
                        onActivityImported(result)
                    }

                    is OsShellCardImportPayload -> {
                        val result =
                            repository.applyShellCardImport(
                                payload = payload,
                                existingCards = persistentState.value.shellCommandCards,
                            )
                        repository.updateShellCommandCards(result.cards)
                        onShellImported(result)
                    }

                    is OsUnknownCardImportPayload -> throw OsCardImportException(OsCardImportError.NoImportableData)
                }
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                onFailure(error)
            } finally {
                onComplete()
            }
        }
    }

    fun saveShellCommandCardEdit(
        cardId: String,
        title: String,
        subtitle: String,
        command: String,
    ) {
        viewModelScope.launch {
            try {
                val updatedCards =
                    cardRepository.saveShellCommandCardEdit(
                        cardId = cardId,
                        title = title,
                        subtitle = subtitle,
                        command = command,
                    )
                if (updatedCards == null) {
                    _events.emit(OsPageEvent.ShellCommandCardSaveFailed)
                    return@launch
                }
                repository.updateShellCommandCards(updatedCards)
                _events.emit(OsPageEvent.ShellCommandCardSaved)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.ShellCommandCardSaveFailed)
            }
        }
    }

    fun deleteShellCommandCard(cardId: String) {
        viewModelScope.launch {
            try {
                val updatedCards = cardRepository.deleteShellCommandCard(cardId)
                repository.updateShellCommandCards(updatedCards)
                _events.emit(OsPageEvent.ShellCommandCardDeleted(cardId))
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.ExportFailed(error))
            }
        }
    }

    fun saveActivityShortcutCard(
        editMode: OsActivityCardEditMode,
        editingCardId: String?,
        draft: OsGoogleSystemServiceConfig,
        defaults: OsGoogleSystemServiceConfig,
    ) {
        viewModelScope.launch {
            try {
                val updatedCards =
                    cardRepository.saveActivityShortcutCard(
                        cards = persistentState.value.activityShortcutCards,
                        editMode = editMode,
                        editingCardId = editingCardId,
                        draft = draft,
                        defaults = defaults,
                    )
                repository.updateActivityShortcutCards(updatedCards)
                _events.emit(OsPageEvent.ActivityShortcutCardSaved)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.ExportFailed(error))
            }
        }
    }

    fun deleteActivityShortcutCard(
        cardId: String,
        defaults: OsGoogleSystemServiceConfig,
    ) {
        viewModelScope.launch {
            try {
                val updatedCards =
                    cardRepository.deleteActivityShortcutCard(
                        cards = persistentState.value.activityShortcutCards,
                        cardId = cardId,
                        defaults = defaults,
                    )
                repository.updateActivityShortcutCards(updatedCards)
                _events.emit(OsPageEvent.ActivityShortcutCardDeleted(cardId))
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(OsPageEvent.ExportFailed(error))
            }
        }
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

    private fun applyHiddenSectionUiState(
        card: OsSectionCard,
        visible: Boolean,
    ) {
        if (visible) return
        when (card) {
            OsSectionCard.TOP_INFO -> repository.updateTopInfoExpanded(false)
            OsSectionCard.SHELL_RUNNER -> repository.updateShellRunnerExpanded(false)
            OsSectionCard.GOOGLE_SYSTEM_SERVICE -> Unit
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
        super.onCleared()
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

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
