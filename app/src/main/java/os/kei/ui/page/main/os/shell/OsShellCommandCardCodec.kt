package os.kei.ui.page.main.os.shell

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import os.kei.core.json.encodeCompact
import os.kei.core.json.optBoolean
import os.kei.core.json.optLong
import os.kei.core.json.optString
import os.kei.core.json.parseJsonArrayOrNull

internal object OsShellCommandCardCodec {
    private const val MAX_OUTPUT_LENGTH = 24_000

    fun normalizeCard(
        card: OsShellCommandCard,
        nowMs: Long = OsShellCommandCardSystemClock.nowMs(),
    ): OsShellCommandCard? {
        val normalizedCommand = card.command.trim()
        if (normalizedCommand.isBlank()) return null
        val createdAt = card.createdAtMillis.takeIf { it > 0L } ?: nowMs
        val updatedAt = card.updatedAtMillis.takeIf { it > 0L } ?: createdAt
        val lastRunAt = card.lastRunAtMillis.takeIf { it > 0L } ?: 0L
        return card.copy(
            id = card.id.trim().ifBlank { newOsShellCommandCardId() },
            title = card.title.trim().ifBlank { defaultOsShellCommandCardTitle(normalizedCommand) },
            subtitle = card.subtitle.trim(),
            command = normalizedCommand,
            runOutput = normalizeRunOutput(card.runOutput),
            lastRunAtMillis = lastRunAt,
            createdAtMillis = createdAt,
            updatedAtMillis = updatedAt,
        )
    }

    fun encodeCards(cards: List<OsShellCommandCard>): String {
        val array = buildJsonArray {
            cards.forEach { card ->
                add(
                    buildJsonObject {
                    put(OS_SHELL_CARD_KEY_ID, card.id)
                    put(OS_SHELL_CARD_KEY_VISIBLE, card.visible)
                    put(OS_SHELL_CARD_KEY_TITLE, card.title)
                    put(OS_SHELL_CARD_KEY_SUBTITLE, card.subtitle)
                    put(OS_SHELL_CARD_KEY_COMMAND, card.command)
                    put(OS_SHELL_CARD_KEY_RUN_OUTPUT, card.runOutput)
                    put(OS_SHELL_CARD_KEY_LAST_RUN_AT, card.lastRunAtMillis)
                    put(OS_SHELL_CARD_KEY_CREATED_AT, card.createdAtMillis)
                    put(OS_SHELL_CARD_KEY_UPDATED_AT, card.updatedAtMillis)
                },
            )
            }
        }
        return array.encodeCompact()
    }

    fun decodeCards(
        raw: String,
        nowMs: Long = OsShellCommandCardSystemClock.nowMs(),
    ): List<OsShellCommandCard> =
        runCatching {
            val array = raw.parseJsonArrayOrNull() ?: return@runCatching emptyList()
            buildList {
                for (element in array) {
                    val item = element as? JsonObject ?: continue
                    decodeCard(item, nowMs)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())

    fun decodeCard(
        item: JsonObject,
        nowMs: Long = OsShellCommandCardSystemClock.nowMs(),
    ): OsShellCommandCard? {
        if (!item.containsKey(OS_SHELL_CARD_KEY_COMMAND)) return null
        return normalizeCard(
            card =
                OsShellCommandCard(
                    id = item.optString(OS_SHELL_CARD_KEY_ID),
                    visible = item.optBoolean(OS_SHELL_CARD_KEY_VISIBLE, true),
                    title = item.optString(OS_SHELL_CARD_KEY_TITLE),
                    subtitle = item.optString(OS_SHELL_CARD_KEY_SUBTITLE),
                    command = item.optString(OS_SHELL_CARD_KEY_COMMAND),
                    runOutput = item.optString(OS_SHELL_CARD_KEY_RUN_OUTPUT),
                    lastRunAtMillis = item.optLong(OS_SHELL_CARD_KEY_LAST_RUN_AT, 0L),
                    createdAtMillis = item.optLong(OS_SHELL_CARD_KEY_CREATED_AT, 0L),
                    updatedAtMillis = item.optLong(OS_SHELL_CARD_KEY_UPDATED_AT, 0L),
                ),
            nowMs = nowMs,
        )
    }

    fun mergeKeyFor(card: OsShellCommandCard): String = card.command.trim().replace(Regex("\\s+"), " ")

    fun cardsEquivalent(
        old: OsShellCommandCard,
        new: OsShellCommandCard,
    ): Boolean =
        old.visible == new.visible &&
            old.title == new.title &&
            old.subtitle == new.subtitle &&
            old.command == new.command &&
            old.runOutput == new.runOutput &&
            old.lastRunAtMillis == new.lastRunAtMillis

    private fun normalizeRunOutput(output: String): String {
        val normalized =
            output
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim()
        if (normalized.length <= MAX_OUTPUT_LENGTH) return normalized
        return normalized.takeLast(MAX_OUTPUT_LENGTH)
    }
}
