package os.kei.mcp.server

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class McpLogEntry(
    val time: String,
    val level: String,
    val message: String
)

internal class McpRuntimeLogStore(
    private val maxEntries: Int = 120,
    private val onChanged: (List<McpLogEntry>) -> Unit
) {
    private var entries: List<McpLogEntry> = emptyList()

    fun snapshot(): List<McpLogEntry> = entries

    fun append(level: String, message: String) {
        val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        entries = (entries + McpLogEntry(time = now, level = level, message = message))
            .takeLast(maxEntries)
        onChanged(entries)
    }

    fun clear() {
        entries = emptyList()
        onChanged(entries)
    }
}
