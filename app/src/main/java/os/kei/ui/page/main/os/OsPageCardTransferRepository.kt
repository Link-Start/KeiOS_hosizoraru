package os.kei.ui.page.main.os

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.io.DEFAULT_BOUNDED_TEXT_READ_MAX_BYTES
import os.kei.core.io.readTextFromUriLimited
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.state.OsCardImportTarget
import os.kei.ui.page.main.os.transfer.OsActivityCardImportPayload
import os.kei.ui.page.main.os.transfer.OsCardImportPreview
import os.kei.ui.page.main.os.transfer.OsCardTransferService
import os.kei.ui.page.main.os.transfer.OsShellCardImportPayload

internal class OsPageCardTransferRepository(
    private val fileIoDispatcher: CoroutineDispatcher = AppDispatchers.fileIo,
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
) {
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
}
