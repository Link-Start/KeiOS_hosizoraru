package os.kei.ui.page.main.os

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.state.OsCardImportTarget
import os.kei.ui.page.main.os.transfer.OsActivityCardImportPayload
import os.kei.ui.page.main.os.transfer.OsCardImportError
import os.kei.ui.page.main.os.transfer.OsCardImportException
import os.kei.ui.page.main.os.transfer.OsCardImportPreview
import os.kei.ui.page.main.os.transfer.OsShellCardImportPayload
import os.kei.ui.page.main.os.transfer.OsUnknownCardImportPayload

internal class OsPageTransferCoordinator(
    private val scope: CoroutineScope,
    private val repository: OsPageRepository,
    private val exportRepository: OsPageExportRepository,
    private val persistentState: StateFlow<OsPagePersistentState>,
    private val runtimeState: StateFlow<OsPageRuntimeState>,
    private val runtimeMutableState: MutableStateFlow<OsPageRuntimeState>,
    private val events: MutableSharedFlow<OsPageEvent>,
) {
    fun prepareActivityCardsExport(
        defaults: OsGoogleSystemServiceConfig,
    ) {
        scope.launch {
            try {
                val content =
                    repository.buildActivityCardsExportJson(
                        cards = persistentState.value.activityShortcutCards,
                        defaults = defaults,
                    )
                events.emit(
                    OsPageEvent.LaunchExportDocument(
                        fileName = "keios-os-activity-cards.json",
                        content = content,
                    ),
                )
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                events.emit(OsPageEvent.OperationFailed(error))
            }
        }
    }

    fun prepareShellCardsExport() {
        scope.launch {
            try {
                val content =
                    repository.buildShellCardsExportJson(
                        cards = persistentState.value.shellCommandCards,
                    )
                events.emit(
                    OsPageEvent.LaunchExportDocument(
                        fileName = "keios-os-shell-cards.json",
                        content = content,
                    ),
                )
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                events.emit(OsPageEvent.OperationFailed(error))
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
        runtimeMutableState.update { state -> state.copy(exportingCard = card) }
        scope.launch {
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
                events.emit(
                    OsPageEvent.LaunchExportDocument(
                        fileName = document.fileName,
                        content = document.content,
                    ),
                )
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                events.emit(OsPageEvent.OperationFailed(error))
            } finally {
                runtimeMutableState.update { state -> state.copy(exportingCard = null) }
            }
        }
    }

    fun writeCardExportContent(
        contentResolver: ContentResolver,
        uri: Uri,
        content: String,
    ) {
        scope.launch {
            try {
                repository.writeExportContent(
                    contentResolver = contentResolver,
                    uri = uri,
                    content = content,
                )
                events.emit(OsPageEvent.CardExportWritten)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                events.emit(OsPageEvent.CardExportWriteFailed(error))
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
    ) {
        scope.launch {
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
                events.emit(OsPageEvent.CardImportPreviewReady(preview))
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                events.emit(OsPageEvent.CardImportFailed(error))
            } finally {
                events.emit(OsPageEvent.CardTransferCompleted)
            }
        }
    }

    fun confirmCardImport(
        preview: OsCardImportPreview,
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        googleSettingsBuiltInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    ) {
        scope.launch {
            try {
                when (val payload = preview.payload) {
                    is OsActivityCardImportPayload -> importActivityCards(
                        payload = payload,
                        googleSystemServiceDefaults = googleSystemServiceDefaults,
                        googleSettingsBuiltInSampleDefaults = googleSettingsBuiltInSampleDefaults,
                        builtInActivityShortcutCards = builtInActivityShortcutCards,
                    )

                    is OsShellCardImportPayload -> importShellCards(payload)
                    is OsUnknownCardImportPayload -> throw OsCardImportException(OsCardImportError.NoImportableData)
                }
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                events.emit(OsPageEvent.CardImportFailed(error))
            } finally {
                events.emit(OsPageEvent.CardTransferCompleted)
            }
        }
    }

    private suspend fun importActivityCards(
        payload: OsActivityCardImportPayload,
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        googleSettingsBuiltInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    ) {
        val result =
            repository.applyActivityCardImport(
                payload = payload,
                existingCards = persistentState.value.activityShortcutCards,
                defaults = googleSystemServiceDefaults,
                builtInSampleDefaults = googleSettingsBuiltInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
            )
        repository.updateActivityShortcutCards(result.cards)
        events.emit(OsPageEvent.ActivityCardsImported(result))
    }

    private suspend fun importShellCards(payload: OsShellCardImportPayload) {
        val result =
            repository.applyShellCardImport(
                payload = payload,
                existingCards = persistentState.value.shellCommandCards,
            )
        repository.updateShellCommandCards(result.cards)
        events.emit(OsPageEvent.ShellCardsImported(result))
    }
}

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
