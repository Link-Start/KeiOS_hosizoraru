package os.kei.ui.page.main.os

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCommandCardStore
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCardStore
import os.kei.ui.page.main.os.shortcut.ShortcutActivityClassOption
import os.kei.ui.page.main.os.shortcut.ShortcutInstalledAppOption
import os.kei.ui.page.main.os.shortcut.loadActivityClassOptions
import os.kei.ui.page.main.os.shortcut.loadInstalledAppOptions

internal data class OsPagePersistentState(
    val uiSnapshot: OsUiSnapshot = OsUiSnapshot(),
    val activityShortcutCards: List<OsActivityShortcutCard> = emptyList(),
    val shellCommandCards: List<OsShellCommandCard> = emptyList(),
    val loaded: Boolean = false,
)

internal class OsPageRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.osOperations,
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
) {
    private val persistentState = MutableStateFlow(OsPagePersistentState())

    fun observePersistentState(): StateFlow<OsPagePersistentState> = persistentState.asStateFlow()

    suspend fun loadPersistentState(
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
        builtInShellCommandCards: List<OsShellCommandCard>,
    ) {
        val loaded =
            withContext(ioDispatcher) {
                OsPagePersistentState(
                    uiSnapshot = OsUiStateStore.loadSnapshot(),
                    activityShortcutCards =
                        OsActivityShortcutCardStore.loadCards(
                            defaults = googleSystemServiceDefaults,
                            builtInSampleDefaults =
                                builtInActivityShortcutCards.firstOrNull()?.config
                                    ?: googleSystemServiceDefaults,
                            builtInActivityShortcutCards = builtInActivityShortcutCards,
                        ),
                    shellCommandCards =
                        OsShellCommandCardStore.loadCards(
                            builtInShellCommandCards = builtInShellCommandCards,
                        ),
                    loaded = true,
                )
            }
        persistentState.value = loaded
    }

    suspend fun reloadShellCommandCards(builtInShellCommandCards: List<OsShellCommandCard> = emptyList()) {
        val cards =
            withContext(ioDispatcher) {
                OsShellCommandCardStore.loadCards(
                    builtInShellCommandCards = builtInShellCommandCards,
                )
            }
        persistentState.update { state -> state.copy(shellCommandCards = cards) }
    }

    suspend fun loadActivityShortcutPackageSuggestions(
        context: Context
    ): List<ShortcutInstalledAppOption> =
        withContext(ioDispatcher) {
            loadInstalledAppOptions(context)
        }

    suspend fun loadActivityShortcutClassSuggestions(
        context: Context,
        packageName: String
    ): List<ShortcutActivityClassOption> =
        withContext(ioDispatcher) {
            loadActivityClassOptions(
                context = context,
                packageName = packageName,
            )
        }

    suspend fun buildRowsDerivedState(input: OsPageRowsDerivationInput): OsPageRowsUiDerivedState =
        withContext(defaultDispatcher) {
            val rowsState =
                deriveOsPageRowsState(
                    queryApplied = input.queryApplied,
                    sectionStates = input.sectionStates,
                    expansionFlags = input.expansionFlags,
                )
            val groupedTopInfoRows =
                if (rowsState.query.isBlank() && !input.expansionFlags.topInfoExpanded) {
                    emptyList()
                } else {
                    groupTopInfoRows(rowsState.displayedTopInfoRows)
                }
            OsPageRowsUiDerivedState(
                input = input,
                rowsState = rowsState,
                groupedTopInfoRows = groupedTopInfoRows,
            )
        }

    fun updateVisibleCards(cards: Set<OsSectionCard>) {
        persistentState.update { state ->
            state.copy(uiSnapshot = state.uiSnapshot.copy(visibleCards = cards))
        }
    }

    suspend fun saveVisibleCards(cards: Set<OsSectionCard>) {
        updateVisibleCards(cards)
        withContext(ioDispatcher) {
            OsCardVisibilityStore.saveVisibleCards(cards)
        }
    }

    suspend fun readInfoCache(visibleSections: Set<SectionKind>): CachedSectionsSnapshot =
        withContext(ioDispatcher) {
            OsInfoCache.readSnapshot(visibleSections)
        }

    suspend fun saveExpandedStateSnapshot(snapshot: OsUiSnapshot) {
        withContext(ioDispatcher) {
            OsUiStateStore.saveExpandedStates(snapshot)
        }
    }

    fun updateActivityShortcutCards(cards: List<OsActivityShortcutCard>) {
        persistentState.update { state -> state.copy(activityShortcutCards = cards) }
    }

    fun updateShellCommandCards(cards: List<OsShellCommandCard>) {
        persistentState.update { state -> state.copy(shellCommandCards = cards) }
    }

    fun updateTopInfoExpanded(value: Boolean) =
        updateUiSnapshot {
            copy(topInfoExpanded = value)
        }

    fun updateShellRunnerExpanded(value: Boolean) =
        updateUiSnapshot {
            copy(shellRunnerExpanded = value)
        }

    fun updateSystemTableExpanded(value: Boolean) =
        updateUiSnapshot {
            copy(systemTableExpanded = value)
        }

    fun updateSecureTableExpanded(value: Boolean) =
        updateUiSnapshot {
            copy(secureTableExpanded = value)
        }

    fun updateGlobalTableExpanded(value: Boolean) =
        updateUiSnapshot {
            copy(globalTableExpanded = value)
        }

    fun updateAndroidPropsExpanded(value: Boolean) =
        updateUiSnapshot {
            copy(androidPropsExpanded = value)
        }

    fun updateJavaPropsExpanded(value: Boolean) =
        updateUiSnapshot {
            copy(javaPropsExpanded = value)
        }

    fun updateLinuxEnvExpanded(value: Boolean) =
        updateUiSnapshot {
            copy(linuxEnvExpanded = value)
        }

    private fun updateUiSnapshot(reducer: OsUiSnapshot.() -> OsUiSnapshot) {
        persistentState.update { state ->
            val nextSnapshot = state.uiSnapshot.reducer()
            state.copy(uiSnapshot = nextSnapshot)
        }
    }
}
