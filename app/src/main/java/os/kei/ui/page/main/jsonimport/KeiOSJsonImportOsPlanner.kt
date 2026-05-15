package os.kei.ui.page.main.jsonimport

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.ui.page.main.os.shell.OsShellCardImportMergeResult
import os.kei.ui.page.main.os.shell.OsShellCommandCardStore
import os.kei.ui.page.main.os.shortcut.OsActivityCardImportMergeResult
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCardStore
import os.kei.ui.page.main.os.transfer.OsCardTransferService

internal class KeiOSJsonImportOsPlanner(
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher
) {
    suspend fun buildActivityPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val defaults = buildJsonImportOsDefaults(context)
        val payload = withContext(defaultDispatcher) {
            OsCardTransferService.parseActivityImportPayload(
                raw = file.raw,
                defaults = defaults.system,
                builtInSampleDefaults = defaults.googleSettingsSample
            )
        }
        val existing = withContext(ioDispatcher) {
            OsActivityShortcutCardStore.loadCards(defaults.system, defaults.googleSettingsSample)
        }
        val result = withContext(defaultDispatcher) {
            OsCardTransferService.previewActivityImport(
                payload = payload,
                existingCards = existing,
                defaults = defaults.system,
                builtInSampleDefaults = defaults.googleSettingsSample
            )
        }
        val preview = buildJsonImportOsPreview(
            context = context,
            header = header,
            totalCount = payload.sourceCount,
            validCount = payload.cards.size,
            invalidCount = payload.invalidCount,
            duplicateCount = payload.duplicateCount,
            addedCount = result.addedCount,
            updatedCount = result.updatedCount,
            unchangedCount = result.unchangedCount,
            mergedCount = result.cards.size,
            samples = payload.cards.take(KEIOS_JSON_IMPORT_SAMPLE_LIMIT).map {
                KeiOSJsonImportSample(it.config.title, it.config.packageName)
            }
        )
        return ImportableKeiOSJsonPlan(preview) {
            withContext(ioDispatcher) {
                val current = OsActivityShortcutCardStore.loadCards(
                    defaults.system,
                    defaults.googleSettingsSample
                )
                val applyResult = OsCardTransferService.applyActivityImport(
                    payload = payload,
                    existingCards = current,
                    defaults = defaults.system,
                    builtInSampleDefaults = defaults.googleSettingsSample
                )
                applyResult.toJsonImportResult(payload.invalidCount, payload.duplicateCount)
            }
        }
    }

    suspend fun buildShellPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val payload = withContext(defaultDispatcher) {
            OsCardTransferService.parseShellImportPayload(file.raw)
        }
        val existing = withContext(ioDispatcher) { OsShellCommandCardStore.loadCards() }
        val result = withContext(defaultDispatcher) {
            OsCardTransferService.previewShellImport(payload = payload, existingCards = existing)
        }
        val preview = buildJsonImportOsPreview(
            context = context,
            header = header,
            totalCount = payload.sourceCount,
            validCount = payload.cards.size,
            invalidCount = payload.invalidCount,
            duplicateCount = payload.duplicateCount,
            addedCount = result.addedCount,
            updatedCount = result.updatedCount,
            unchangedCount = result.unchangedCount,
            mergedCount = result.cards.size,
            samples = payload.cards.take(KEIOS_JSON_IMPORT_SAMPLE_LIMIT).map {
                KeiOSJsonImportSample(it.title.ifBlank { it.command }, it.command)
            }
        )
        return ImportableKeiOSJsonPlan(preview) {
            withContext(ioDispatcher) {
                val current = OsShellCommandCardStore.loadCards()
                val applyResult = OsCardTransferService.applyShellImport(payload, current)
                applyResult.toJsonImportResult(payload.invalidCount, payload.duplicateCount)
            }
        }
    }

    suspend fun buildBundlePlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val defaults = buildJsonImportOsDefaults(context)
        val currentActivity = withContext(ioDispatcher) {
            OsActivityShortcutCardStore.loadCards(defaults.system, defaults.googleSettingsSample)
        }
        val currentShell = withContext(ioDispatcher) { OsShellCommandCardStore.loadCards() }
        val bundlePreview = withContext(defaultDispatcher) {
            OsCardTransferService.buildBundleImportPreview(
                raw = file.raw,
                activityShortcutCards = currentActivity,
                shellCommandCards = currentShell,
                defaults = defaults.system,
                builtInSampleDefaults = defaults.googleSettingsSample
            )
        }
        val preview = buildJsonImportOsPreview(
            context = context,
            header = header,
            totalCount = bundlePreview.fileItemCount,
            validCount = bundlePreview.validCount,
            invalidCount = bundlePreview.invalidCount,
            duplicateCount = bundlePreview.duplicateCount,
            addedCount = bundlePreview.newCount,
            updatedCount = bundlePreview.updatedCount,
            unchangedCount = bundlePreview.unchangedCount,
            mergedCount = bundlePreview.mergedCount,
            samples = buildJsonImportOsBundleSamples(bundlePreview.payload)
        )
        return ImportableKeiOSJsonPlan(preview) {
            withContext(ioDispatcher) {
                val activityCards = OsActivityShortcutCardStore.loadCards(
                    defaults.system,
                    defaults.googleSettingsSample
                )
                val shellCards = OsShellCommandCardStore.loadCards()
                val result = OsCardTransferService.applyBundleImport(
                    payload = bundlePreview.payload,
                    activityShortcutCards = activityCards,
                    shellCommandCards = shellCards,
                    defaults = defaults.system,
                    builtInSampleDefaults = defaults.googleSettingsSample
                )
                KeiOSJsonImportApplyResult(
                    addedCount = result.addedCount,
                    updatedCount = result.updatedCount,
                    unchangedCount = result.unchangedCount,
                    invalidCount = result.invalidCount,
                    duplicateCount = result.duplicateCount
                )
            }
        }
    }

    private fun OsActivityCardImportMergeResult.toJsonImportResult(
        invalidCount: Int,
        duplicateCount: Int
    ): KeiOSJsonImportApplyResult {
        return KeiOSJsonImportApplyResult(
            addedCount = addedCount,
            updatedCount = updatedCount,
            unchangedCount = unchangedCount,
            invalidCount = invalidCount,
            duplicateCount = duplicateCount
        )
    }

    private fun OsShellCardImportMergeResult.toJsonImportResult(
        invalidCount: Int,
        duplicateCount: Int
    ): KeiOSJsonImportApplyResult {
        return KeiOSJsonImportApplyResult(
            addedCount = addedCount,
            updatedCount = updatedCount,
            unchangedCount = unchangedCount,
            invalidCount = invalidCount,
            duplicateCount = duplicateCount
        )
    }
}
