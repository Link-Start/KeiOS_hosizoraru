package os.kei.ui.page.main.os.transfer

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import os.kei.core.json.KeiJson
import os.kei.core.json.encodeCompact
import os.kei.core.json.optInt
import os.kei.core.json.optObject
import os.kei.core.json.optString
import os.kei.core.json.parseJsonObjectOrNull
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.shell.OsShellCardImportMergeResult
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCommandCardStore
import os.kei.ui.page.main.os.shortcut.OsActivityCardImportMergeResult
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCardStore
import os.kei.ui.page.main.os.state.OsCardImportTarget

internal object OsCardTransferService {
    fun buildActivityCardsExportJson(
        cards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig,
    ): String =
        OsActivityShortcutCardStore.buildCardsExportJson(
            cards = cards,
            defaults = defaults,
        )

    fun buildShellCardsExportJson(cards: List<OsShellCommandCard>): String = OsShellCommandCardStore.buildCardsExportJson(cards)

    fun buildCardsBundleExportJson(
        activityCards: List<OsActivityShortcutCard>,
        shellCards: List<OsShellCommandCard>,
        defaults: OsGoogleSystemServiceConfig,
    ): String =
        buildJsonObject {
            put("schema", OS_CARD_BUNDLE_EXPORT_SCHEMA)
            put("schemaVersion", OS_CARD_EXPORT_SCHEMA_VERSION)
            put("exportedAtMillis", System.currentTimeMillis())
            put(
                "activity",
                buildActivityCardsExportJson(activityCards, defaults).parseJsonObjectOrNull()
                    ?: error("activity card export payload is invalid"),
            )
            put(
                "shell",
                buildShellCardsExportJson(shellCards).parseJsonObjectOrNull()
                    ?: error("shell card export payload is invalid"),
            )
        }.encodeCompact(KeiJson.pretty)

    fun parseBundleImportPayload(
        raw: String,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> = emptyList(),
    ): OsCardBundleImportPayload {
        val root = raw.parseJsonObjectOrNull()
            ?: throw OsCardImportException(OsCardImportError.MissingData)
        val schemaVersion =
            root
                .optInt("schemaVersion", OS_CARD_EXPORT_SCHEMA_VERSION)
                .coerceAtLeast(OS_CARD_LEGACY_SCHEMA_VERSION)
        val activityPayload =
            root.optObject("activity")?.let { activityRoot ->
                OsActivityShortcutCardStore.parseCardsImport(
                    root = parseOsCardImportRoot(activityRoot.encodeCompact()),
                    defaults = defaults,
                    builtInSampleDefaults = builtInSampleDefaults,
                    builtInActivityShortcutCards = builtInActivityShortcutCards,
                )
            }
        val shellPayload =
            root.optObject("shell")?.let { shellRoot ->
                OsShellCommandCardStore.parseCardsImport(parseOsCardImportRoot(shellRoot.encodeCompact()))
            }
        return OsCardBundleImportPayload(
            activityPayload = activityPayload,
            shellPayload = shellPayload,
            schemaVersion = schemaVersion,
            isLegacyFormat = root.optString("schema").trim().isBlank(),
        )
    }

    fun buildBundleImportPreview(
        raw: String,
        activityShortcutCards: List<OsActivityShortcutCard>,
        shellCommandCards: List<OsShellCommandCard>,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> = emptyList(),
    ): OsCardBundleImportPreview {
        val payload =
            parseBundleImportPayload(
                raw = raw,
                defaults = defaults,
                builtInSampleDefaults = builtInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
            )
        val activityPreview =
            payload.activityPayload?.let { activityPayload ->
                buildPreview(
                    target = OsCardImportTarget.Activity,
                    payload = activityPayload,
                    validCount = activityPayload.cards.size,
                    result =
                        OsActivityShortcutCardStore.previewImportedCards(
                            payload = activityPayload,
                            existingCards = activityShortcutCards,
                            defaults = defaults,
                            builtInSampleDefaults = builtInSampleDefaults,
                            builtInActivityShortcutCards = builtInActivityShortcutCards,
                        ),
                )
            }
        val shellPreview =
            payload.shellPayload?.let { shellPayload ->
                buildPreview(
                    target = OsCardImportTarget.Shell,
                    payload = shellPayload,
                    validCount = shellPayload.cards.size,
                    result =
                        OsShellCommandCardStore.previewImportedCards(
                            payload = shellPayload,
                            existingCards = shellCommandCards,
                        ),
                )
            }
        return OsCardBundleImportPreview(
            payload = payload,
            activityPreview = activityPreview,
            shellPreview = shellPreview,
        )
    }

    fun buildImportPreview(
        raw: String,
        target: OsCardImportTarget,
        activityShortcutCards: List<OsActivityShortcutCard>,
        shellCommandCards: List<OsShellCommandCard>,
        googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
        googleSettingsBuiltInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> = emptyList(),
    ): OsCardImportPreview {
        val root = parseOsCardImportRoot(raw)
        return when (root.fileKind) {
            OsCardImportFileKind.Activity -> {
                val payload =
                    OsActivityShortcutCardStore.parseCardsImport(
                        root = root,
                        defaults = googleSystemServiceDefaults,
                        builtInSampleDefaults = googleSettingsBuiltInSampleDefaults,
                        builtInActivityShortcutCards = builtInActivityShortcutCards,
                    )
                val result =
                    if (target == OsCardImportTarget.Activity) {
                        OsActivityShortcutCardStore.previewImportedCards(
                            payload = payload,
                            existingCards = activityShortcutCards,
                            defaults = googleSystemServiceDefaults,
                            builtInSampleDefaults = googleSettingsBuiltInSampleDefaults,
                            builtInActivityShortcutCards = builtInActivityShortcutCards,
                        )
                    } else {
                        null
                    }
                buildPreview(
                    target = target,
                    payload = payload,
                    validCount = payload.cards.size,
                    result = result,
                )
            }

            OsCardImportFileKind.Shell -> {
                val payload = OsShellCommandCardStore.parseCardsImport(root)
                val result =
                    if (target == OsCardImportTarget.Shell) {
                        OsShellCommandCardStore.previewImportedCards(
                            payload = payload,
                            existingCards = shellCommandCards,
                        )
                    } else {
                        null
                    }
                buildPreview(
                    target = target,
                    payload = payload,
                    validCount = payload.cards.size,
                    result = result,
                )
            }

            OsCardImportFileKind.Unknown -> {
                val payload =
                    OsUnknownCardImportPayload(
                        sourceCount = root.sourceCount,
                        invalidCount = root.sourceCount,
                        duplicateCount = 0,
                        fileKind = root.fileKind,
                        schemaVersion = root.schemaVersion,
                        isLegacyFormat = root.isLegacyFormat,
                    )
                buildPreview(
                    target = target,
                    payload = payload,
                    validCount = 0,
                    result = null,
                )
            }
        }
    }

    fun parseActivityImportPayload(
        raw: String,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> = emptyList(),
    ): OsActivityCardImportPayload =
        OsActivityShortcutCardStore.parseCardsImport(
            root = parseOsCardImportRoot(raw),
            defaults = defaults,
            builtInSampleDefaults = builtInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
        )

    fun parseShellImportPayload(raw: String): OsShellCardImportPayload =
        OsShellCommandCardStore.parseCardsImport(parseOsCardImportRoot(raw))

    fun previewActivityImport(
        payload: OsActivityCardImportPayload,
        existingCards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> = emptyList(),
    ): OsActivityCardImportMergeResult =
        OsActivityShortcutCardStore.previewImportedCards(
            payload = payload,
            existingCards = existingCards,
            defaults = defaults,
            builtInSampleDefaults = builtInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
        )

    fun previewShellImport(
        payload: OsShellCardImportPayload,
        existingCards: List<OsShellCommandCard>,
    ): OsShellCardImportMergeResult =
        OsShellCommandCardStore.previewImportedCards(
            payload = payload,
            existingCards = existingCards,
        )

    fun applyActivityImport(
        payload: OsActivityCardImportPayload,
        existingCards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> = emptyList(),
    ): OsActivityCardImportMergeResult =
        OsActivityShortcutCardStore.applyImportedCards(
            payload = payload,
            existingCards = existingCards,
            defaults = defaults,
            builtInSampleDefaults = builtInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
        )

    fun applyShellImport(
        payload: OsShellCardImportPayload,
        existingCards: List<OsShellCommandCard>,
    ): OsShellCardImportMergeResult =
        OsShellCommandCardStore.applyImportedCards(
            payload = payload,
            existingCards = existingCards,
        )

    fun applyBundleImport(
        payload: OsCardBundleImportPayload,
        activityShortcutCards: List<OsActivityShortcutCard>,
        shellCommandCards: List<OsShellCommandCard>,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> = emptyList(),
    ): OsCardBundleImportApplyResult {
        val activityResult =
            payload.activityPayload
                ?.takeIf { it.cards.isNotEmpty() }
                ?.let { activityPayload ->
                    applyActivityImport(
                        payload = activityPayload,
                        existingCards = activityShortcutCards,
                        defaults = defaults,
                        builtInSampleDefaults = builtInSampleDefaults,
                        builtInActivityShortcutCards = builtInActivityShortcutCards,
                    )
                }
        val shellResult =
            payload.shellPayload
                ?.takeIf { it.cards.isNotEmpty() }
                ?.let { shellPayload ->
                    applyShellImport(
                        payload = shellPayload,
                        existingCards = shellCommandCards,
                    )
                }
        return OsCardBundleImportApplyResult(
            addedCount = (activityResult?.addedCount ?: 0) + (shellResult?.addedCount ?: 0),
            updatedCount = (activityResult?.updatedCount ?: 0) + (shellResult?.updatedCount ?: 0),
            unchangedCount =
                (activityResult?.unchangedCount ?: 0) + (
                    shellResult?.unchangedCount
                        ?: 0
                ),
            invalidCount = payload.invalidCount,
            duplicateCount = payload.duplicateCount,
            mergedCount = (activityResult?.cards?.size ?: 0) + (shellResult?.cards?.size ?: 0),
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
        applied: Boolean,
    ): String =
        buildString {
            appendLine("target=$target")
            appendLine("fileKind=${payload.fileKind.name}")
            appendLine("schemaVersion=${payload.schemaVersion}")
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

    private fun validCount(payload: OsCardImportPayload): Int =
        when (payload) {
            is OsActivityCardImportPayload -> payload.cards.size
            is OsShellCardImportPayload -> payload.cards.size
            is OsUnknownCardImportPayload -> 0
        }

    private fun buildPreview(
        target: OsCardImportTarget,
        payload: OsCardImportPayload,
        validCount: Int,
        result: Any?,
    ): OsCardImportPreview {
        val addedCount =
            when (result) {
                is OsActivityCardImportMergeResult -> result.addedCount
                is OsShellCardImportMergeResult -> result.addedCount
                else -> 0
            }
        val updatedCount =
            when (result) {
                is OsActivityCardImportMergeResult -> result.updatedCount
                is OsShellCardImportMergeResult -> result.updatedCount
                else -> 0
            }
        val unchangedCount =
            when (result) {
                is OsActivityCardImportMergeResult -> result.unchangedCount
                is OsShellCardImportMergeResult -> result.unchangedCount
                else -> 0
            }
        val mergedCount =
            when (result) {
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
            mergedCount = mergedCount,
        )
    }
}
