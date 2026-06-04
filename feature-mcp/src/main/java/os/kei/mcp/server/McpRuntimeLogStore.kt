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
    private val entries = ArrayDeque<McpLogEntry>(maxEntries)

    @Synchronized
    fun snapshot(): List<McpLogEntry> = entries.toList()

    @Synchronized
    fun append(level: String, message: String) {
        val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        if (entries.size >= maxEntries) {
            entries.removeFirst()
        }
        entries.addLast(McpLogEntry(time = now, level = level, message = message))
        onChanged(entries.toList())
    }

    @Synchronized
    fun clear() {
        entries.clear()
        onChanged(emptyList())
    }
}
