package os.kei.ui.page.main.os.shell.util

import kotlinx.serialization.json.JsonElement
import os.kei.core.json.KeiJson
import os.kei.core.json.parseJsonElementOrNull
import os.kei.ui.page.main.os.shell.ShellOutputDisplayEntry

private val shellAnsiEscapeRegex = Regex("""\u001B\[[;\d]*[ -/]*[@-~]""")
private val shellKeyValueRegex = Regex("""\b[^\s=]+=[^\s=]+\b""")

internal fun buildShellOutputHistoryText(
    entries: List<ShellOutputDisplayEntry>,
    maxChars: Int
): String {
    if (entries.isEmpty()) return ""
    val raw = entries.joinToString(separator = "\n\n") { entry ->
        buildString {
            append("$ ")
            append(entry.command.trim())
            appendLine()
            appendLine()
            append(entry.result.trimEnd())
            val timeLabel = entry.timeLabel.trim()
            if (timeLabel.isNotBlank()) {
                appendLine()
                appendLine()
                append(timeLabel)
            }
        }.trimEnd()
    }
    return trimShellOutputHistory(raw = raw, maxChars = maxChars)
}

internal fun formatShellResultForReadability(raw: String): String {
    val normalized = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    val jsonPretty = tryFormatShellOutputAsJson(normalized)
    if (jsonPretty != null) return jsonPretty

    val noAnsi = shellAnsiEscapeRegex.replace(normalized, "")
    val lines = mutableListOf<String>()
    var previousBlank = true

    noAnsi.lines().forEach { source ->
        val reflowed = reflowShellVerboseLine(
            source.replace("\t", "    ").trimEnd()
        )
        reflowed.lines().forEach { lineRaw ->
            val line = lineRaw.trimEnd()
            if (line.isBlank()) {
                if (!previousBlank) {
                    lines += ""
                    previousBlank = true
                }
            } else {
                if (lineLooksLikeSectionHeading(line) && lines.isNotEmpty() && lines.last().isNotBlank()) {
                    lines += ""
                }
                lines += line
                previousBlank = false
            }
        }
    }
    return lines.joinToString("\n").trim()
}

internal fun trimShellOutputHistory(raw: String, maxChars: Int): String {
    val normalized = raw.trimEnd()
    if (normalized.length <= maxChars) return normalized
    return normalized
        .takeLast(maxChars)
        .trimStart()
}

private fun tryFormatShellOutputAsJson(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    return runCatching {
        when {
            trimmed.startsWith("{") && trimmed.endsWith("}") ->
                trimmed.parseJsonElementOrNull()?.let { element ->
                    KeiJson.pretty.encodeToString(JsonElement.serializer(), element)
                }
            trimmed.startsWith("[") && trimmed.endsWith("]") ->
                trimmed.parseJsonElementOrNull()?.let { element ->
                    KeiJson.pretty.encodeToString(JsonElement.serializer(), element)
                }
            else -> null
        }
    }.getOrNull()
}

private fun reflowShellVerboseLine(line: String): String {
    if (line.length <= 120) return line
    val keyValues = shellKeyValueRegex.findAll(line).map { it.value }.toList()
    if (keyValues.size >= 6) {
        return keyValues.joinToString(separator = "\n") { token -> "  $token" }
    }
    if (line.contains(", ") && line.count { it == ',' } >= 5) {
        return line.replace(", ", ",\n  ")
    }
    if (line.contains("; ") && line.count { it == ';' } >= 4) {
        return line.replace("; ", ";\n  ")
    }
    return line
}

private fun lineLooksLikeSectionHeading(line: String): Boolean {
    if (line.length !in 2..80) return false
    if (line.startsWith(" ") || line.startsWith("\t")) return false
    return line.endsWith(":")
}
