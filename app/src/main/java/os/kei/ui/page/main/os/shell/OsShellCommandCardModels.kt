package os.kei.ui.page.main.os.shell

import androidx.compose.runtime.Immutable
import java.util.Locale
import java.util.UUID

@Immutable
internal data class OsShellCardImportMergeResult(
    val cards: List<OsShellCommandCard>,
    val addedCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
)

@Immutable
internal data class OsShellCommandCard(
    val id: String,
    val visible: Boolean = true,
    val title: String,
    val subtitle: String = "",
    val command: String,
    val runOutput: String = "",
    val lastRunAtMillis: Long = 0L,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

internal fun newOsShellCommandCardId(): String {
    val compactUuid =
        UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .take(12)
    return "shell-${compactUuid.lowercase(Locale.ROOT)}"
}

internal fun defaultOsShellCommandCardTitle(command: String): String {
    val normalized = command.trim().replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return ""
    return if (normalized.length <= 36) normalized else "${normalized.take(35)}…"
}

internal fun createDefaultShellCommandCardDraft(command: String = ""): OsShellCommandCard {
    val normalizedCommand = command.trim()
    return OsShellCommandCard(
        id = "",
        visible = true,
        title = defaultOsShellCommandCardTitle(normalizedCommand),
        subtitle = "",
        command = normalizedCommand,
        runOutput = "",
        lastRunAtMillis = 0L,
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )
}
