package os.kei.ui.page.main.os

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.io.DEFAULT_BOUNDED_TEXT_READ_MAX_BYTES
import os.kei.core.io.readTextFromUriLimited
import os.kei.core.system.RuntimeCommandExecutor
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCommandCardStore
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCardStore
import os.kei.ui.page.main.os.shortcut.ShortcutActivityClassOption
import os.kei.ui.page.main.os.shortcut.ShortcutInstalledAppOption
import os.kei.ui.page.main.os.shortcut.loadActivityClassOptions
import os.kei.ui.page.main.os.shortcut.loadInstalledAppOptions
import os.kei.ui.page.main.os.state.OsCardImportTarget
import os.kei.ui.page.main.os.transfer.OsActivityCardImportPayload
import os.kei.ui.page.main.os.transfer.OsCardImportPreview
import os.kei.ui.page.main.os.transfer.OsCardTransferService
import os.kei.ui.page.main.os.transfer.OsShellCardImportPayload

internal data class OsPagePersistentState(
    val uiSnapshot: OsUiSnapshot = OsUiSnapshot(),
    val activityShortcutCards: List<OsActivityShortcutCard> = emptyList(),
    val shellCommandCards: List<OsShellCommandCard> = emptyList(),
    val loaded: Boolean = false,
)

internal class OsPageRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.osOperations,
    private val fileIoDispatcher: CoroutineDispatcher = AppDispatchers.fileIo,
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
) {
    private val persistentState = MutableStateFlow(OsPagePersistentState())

    fun observePersistentState(): StateFlow<OsPagePersistentState> = persistentState.asStateFlow()

    suspend fun loadPersistentState(
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
        builtInShellCommandCards: List<OsShellCommandCard>,
    ) {
        if (persistentState.value.loaded) return
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
        persistentState.update { current ->
            if (current.loaded) current else loaded
        }
    }

    fun closePersistentShell() {
        RuntimeCommandExecutor.closePersistentShell()
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

    suspend fun writeExportContent(
        contentResolver: ContentResolver,
        uri: Uri,
        content: String,
    ) {
        withContext(fileIoDispatcher) {
            contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                checkNotNull(writer) { "openOutputStream returned null" }
                writer.write(content)
            }
        }
    }

    suspend fun readImportContent(
        contentResolver: ContentResolver,
        uri: Uri,
    ): String =
        withContext(fileIoDispatcher) {
            contentResolver
                .readTextFromUriLimited(
                    uri = uri,
                    maxBytes = DEFAULT_BOUNDED_TEXT_READ_MAX_BYTES,
                ).text
        }

    suspend fun buildActivityCardsExportJson(
        cards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig,
    ): String =
        withContext(defaultDispatcher) {
            OsCardTransferService.buildActivityCardsExportJson(
                cards = cards,
                defaults = defaults,
            )
        }

    suspend fun buildShellCardsExportJson(cards: List<OsShellCommandCard>): String =
        withContext(defaultDispatcher) {
            OsCardTransferService.buildShellCardsExportJson(cards)
        }

    suspend fun buildCardImportPreview(
        raw: String,
        target: OsCardImportTarget,
        activityShortcutCards: List<OsActivityShortcutCard>,
        shellCommandCards: List<OsShellCommandCard>,
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        googleSettingsBuiltInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    ): OsCardImportPreview =
        withContext(defaultDispatcher) {
            OsCardTransferService.buildImportPreview(
                raw = raw,
                target = target,
                activityShortcutCards = activityShortcutCards,
                shellCommandCards = shellCommandCards,
                googleSystemServiceDefaults = googleSystemServiceDefaults,
                googleSettingsBuiltInSampleDefaults = googleSettingsBuiltInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
            )
        }

    suspend fun applyActivityCardImport(
        payload: OsActivityCardImportPayload,
        existingCards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    ) = withContext(defaultDispatcher) {
        OsCardTransferService.applyActivityImport(
            payload = payload,
            existingCards = existingCards,
            defaults = defaults,
            builtInSampleDefaults = builtInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
        )
    }

    suspend fun applyShellCardImport(
        payload: OsShellCardImportPayload,
        existingCards: List<OsShellCommandCard>,
    ) = withContext(defaultDispatcher) {
        OsCardTransferService.applyShellImport(
            payload = payload,
            existingCards = existingCards,
        )
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
