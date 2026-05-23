package os.kei.ui.page.main.os

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.core.shizuku.ShizukuApiUtils

internal class OsPageSectionController(
    private val scope: CoroutineScope,
    private val repository: OsPageRepository,
    private val visibilityRepository: OsPageVisibilityRepository,
    private val refreshRepository: OsPageRefreshRepository,
    private val sectionLoadRepository: OsPageSectionLoadRepository,
    private val persistentState: StateFlow<OsPagePersistentState>,
    private val runtimeState: StateFlow<OsPageRuntimeState>,
    private val runtimeMutableState: MutableStateFlow<OsPageRuntimeState>,
    private val events: MutableSharedFlow<OsPageEvent>,
) {
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
        runtimeMutableState.update { state ->
            state.copy(
                sectionStates = hydratedSectionStates(visibleSections, snapshot),
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

    fun replaceSectionStates(sectionStates: Map<SectionKind, SectionState>) {
        runtimeMutableState.update { state -> state.copy(sectionStates = sectionStates) }
    }

    fun updateSection(
        section: SectionKind,
        transform: (SectionState) -> SectionState,
    ) {
        runtimeMutableState.update { state ->
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
                OsSectionLoadResult.Joined -> Unit
                is OsSectionLoadResult.Loaded -> applyLoadedSection(section, result)
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
        scope.launch {
            try {
                val cachePersisted =
                    visibilityRepository.persistSectionCardVisibility(
                        card = card,
                        visible = visible,
                        visibleCards = updatedVisibleCards,
                    )
                runtimeMutableState.update { state -> state.copy(cachePersisted = cachePersisted) }
                if (visible) {
                    sectionKindByCard(card)?.let { section ->
                        ensureLoad(section, true)
                    }
                }
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                events.emit(OsPageEvent.OperationFailed(error))
            }
        }
    }

    fun refreshAllSections(ensureLoad: suspend (SectionKind, Boolean) -> Unit) {
        if (runtimeState.value.refreshing) return
        scope.launch {
            runtimeMutableState.update { state ->
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
                    runtimeMutableState.update { state ->
                        state.copy(refreshProgress = (index + 1).toFloat() / sectionCount.toFloat())
                    }
                }
                events.emit(OsPageEvent.RefreshCompleted(refreshed = targets.isNotEmpty()))
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                events.emit(OsPageEvent.OperationFailed(error))
            } finally {
                runtimeMutableState.update { state -> state.copy(refreshing = false) }
            }
        }
    }

    private fun applyLoadedSection(
        section: SectionKind,
        result: OsSectionLoadResult.Loaded,
    ) {
        updateSection(section) {
            it.copy(
                rows = result.rows,
                loading = false,
                loadedFresh = true,
                loadFailed = false,
            )
        }
        runtimeMutableState.update { state -> state.copy(cachePersisted = result.cachePersisted) }
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
}

private fun hydratedSectionStates(
    visibleSections: Set<SectionKind>,
    snapshot: CachedSectionsSnapshot,
): Map<SectionKind, SectionState> =
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
    )

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
