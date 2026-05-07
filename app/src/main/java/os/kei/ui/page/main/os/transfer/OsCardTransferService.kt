package os.kei.ui.page.main.os.transfer

import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCommandCardStore
import os.kei.ui.page.main.os.shell.OsShellCardImportMergeResult
import os.kei.ui.page.main.os.shortcut.OsActivityCardImportMergeResult
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCardStore
import os.kei.ui.page.main.os.state.OsCardImportTarget
import org.json.JSONObject

internal object OsCardTransferService {
    fun buildActivityCardsExportJson(
        cards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig
    ): String {
        return OsActivityShortcutCardStore.buildCardsExportJson(
            cards = cards,
            defaults = defaults
        )
    }

    fun buildShellCardsExportJson(cards: List<OsShellCommandCard>): String {
        return OsShellCommandCardStore.buildCardsExportJson(cards)
    }

    fun buildCardsBundleExportJson(
        activityCards: List<OsActivityShortcutCard>,
        shellCards: List<OsShellCommandCard>,
        defaults: OsGoogleSystemServiceConfig
    ): String {
        return JSONObject().apply {
            put("schema", "keios.os.cards.bundle.v1")
            put("exportedAtMillis", System.currentTimeMillis())
            put(
                "activity",
                JSONObject(buildActivityCardsExportJson(activityCards, defaults))
            )
            put("shell", JSONObject(buildShellCardsExportJson(shellCards)))
        }.toString(2)
    }

    fun buildImportPreview(
        raw: String,
        target: OsCardImportTarget,
        activityShortcutCards: List<OsActivityShortcutCard>,
        shellCommandCards: List<OsShellCommandCard>,
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        googleSettingsBuiltInSampleDefaults: OsGoogleSystemServiceConfig
    ): OsCardImportPreview {
        val root = parseOsCardImportRoot(raw)
        return when (root.fileKind) {
            OsCardImportFileKind.Activity -> {
                val payload = OsActivityShortcutCardStore.parseCardsImport(
                    root = root,
                    defaults = googleSystemServiceDefaults,
                    builtInSampleDefaults = googleSettingsBuiltInSampleDefaults
                )
                val result = if (target == OsCardImportTarget.Activity) {
                    OsActivityShortcutCardStore.previewImportedCards(
                        payload = payload,
                        existingCards = activityShortcutCards,
                        defaults = googleSystemServiceDefaults,
                        builtInSampleDefaults = googleSettingsBuiltInSampleDefaults
                    )
                } else {
                    null
                }
                buildPreview(
                    target = target,
                    payload = payload,
                    validCount = payload.cards.size,
                    result = result
                )
            }

            OsCardImportFileKind.Shell -> {
                val payload = OsShellCommandCardStore.parseCardsImport(root)
                val result = if (target == OsCardImportTarget.Shell) {
                    OsShellCommandCardStore.previewImportedCards(
                        payload = payload,
                        existingCards = shellCommandCards
                    )
                } else {
                    null
                }
                buildPreview(
                    target = target,
                    payload = payload,
                    validCount = payload.cards.size,
                    result = result
                )
            }

            OsCardImportFileKind.Unknown -> {
                val payload = OsUnknownCardImportPayload(
                    sourceCount = root.sourceCount,
                    invalidCount = root.sourceCount,
                    duplicateCount = 0,
                    fileKind = root.fileKind,
                    isLegacyFormat = root.isLegacyFormat
                )
                buildPreview(
                    target = target,
                    payload = payload,
                    validCount = 0,
                    result = null
                )
            }
        }
    }

    fun parseActivityImportPayload(
        raw: String,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig
    ): OsActivityCardImportPayload {
        return OsActivityShortcutCardStore.parseCardsImport(
            root = parseOsCardImportRoot(raw),
            defaults = defaults,
            builtInSampleDefaults = builtInSampleDefaults
        )
    }

    fun parseShellImportPayload(raw: String): OsShellCardImportPayload {
        return OsShellCommandCardStore.parseCardsImport(parseOsCardImportRoot(raw))
    }

    fun previewActivityImport(
        payload: OsActivityCardImportPayload,
        existingCards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig
    ): OsActivityCardImportMergeResult {
        return OsActivityShortcutCardStore.previewImportedCards(
            payload = payload,
            existingCards = existingCards,
            defaults = defaults,
            builtInSampleDefaults = builtInSampleDefaults
        )
    }

    fun previewShellImport(
        payload: OsShellCardImportPayload,
        existingCards: List<OsShellCommandCard>
    ): OsShellCardImportMergeResult {
        return OsShellCommandCardStore.previewImportedCards(
            payload = payload,
            existingCards = existingCards
        )
    }

    fun applyActivityImport(
        payload: OsActivityCardImportPayload,
        existingCards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig
    ): OsActivityCardImportMergeResult {
        return OsActivityShortcutCardStore.applyImportedCards(
            payload = payload,
            existingCards = existingCards,
            defaults = defaults,
            builtInSampleDefaults = builtInSampleDefaults
        )
    }

    fun applyShellImport(
        payload: OsShellCardImportPayload,
        existingCards: List<OsShellCommandCard>
    ): OsShellCardImportMergeResult {
        return OsShellCommandCardStore.applyImportedCards(
            payload = payload,
            existingCards = existingCards
        )
    }

    fun buildImportSummaryText(
        target: String,
        payload: OsCardImportPayload,
        addedCount: Int,
        updatedCount: Int,
        unchangedCount: Int,
        mergedCount: Int,
        apply: Boolean,
        applied: Boolean
    ): String {
        return buildString {
            appendLine("target=$target")
            appendLine("fileKind=${payload.fileKind.name}")
            appendLine("legacyFormat=${payload.isLegacyFormat}")
            appendLine("apply=$apply")
            appendLine("applied=$applied")
            appendLine("sourceCount=${payload.sourceCount}")
            appendLine("validCount=${validCount(payload)}")
            appendLine("invalidCount=${payload.invalidCount}")
            appendLine("duplicateCount=${payload.duplicateCount}")
            appendLine("newCount=$addedCount")
            appendLine("updatedCount=$updatedCount")
            appendLine("unchangedCount=$unchangedCount")
            appendLine("mergedCount=$mergedCount")
        }.trim()
    }

    private fun validCount(payload: OsCardImportPayload): Int {
        return when (payload) {
            is OsActivityCardImportPayload -> payload.cards.size
            is OsShellCardImportPayload -> payload.cards.size
            is OsUnknownCardImportPayload -> 0
        }
    }

    private fun buildPreview(
        target: OsCardImportTarget,
        payload: OsCardImportPayload,
        validCount: Int,
        result: Any?
    ): OsCardImportPreview {
        val addedCount = when (result) {
            is OsActivityCardImportMergeResult -> result.addedCount
            is OsShellCardImportMergeResult -> result.addedCount
            else -> 0
        }
        val updatedCount = when (result) {
            is OsActivityCardImportMergeResult -> result.updatedCount
            is OsShellCardImportMergeResult -> result.updatedCount
            else -> 0
        }
        val unchangedCount = when (result) {
            is OsActivityCardImportMergeResult -> result.unchangedCount
            is OsShellCardImportMergeResult -> result.unchangedCount
            else -> 0
        }
        val mergedCount = when (result) {
            is OsActivityCardImportMergeResult -> result.cards.size
            is OsShellCardImportMergeResult -> result.cards.size
            else -> 0
        }
        return OsCardImportPreview(
            target = target,
            payload = payload,
            fileItemCount = payload.sourceCount,
            validCount = validCount,
            duplicateCount = payload.duplicateCount,
            invalidCount = payload.invalidCount,
            newCount = addedCount,
            updatedCount = updatedCount,
            unchangedCount = unchangedCount,
            mergedCount = mergedCount
        )
    }
}
