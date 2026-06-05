package os.kei.mcp.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val minPublishIntervalMs: Long = 0L,
    private val publishScope: CoroutineScope? = null,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val onChanged: (List<McpLogEntry>) -> Unit
) {
    private val entries = ArrayDeque<McpLogEntry>(maxEntries)
    private var publishJob: Job? = null
    private var lastPublishedAtMs: Long = Long.MIN_VALUE
    private var formatterLocale: Locale? = null
    private var formatter: SimpleDateFormat? = null

    @Synchronized
    fun snapshot(): List<McpLogEntry> = entries.toList()

    fun append(level: String, message: String) {
        val snapshot =
            synchronized(this) {
                val now = formattedNow()
                if (entries.size >= maxEntries) {
                    entries.removeFirst()
                }
                entries.addLast(McpLogEntry(time = now, level = level, message = message))
                nextPublishSnapshotLocked()
            }
        snapshot?.let(onChanged)
    }

    fun clear() {
        synchronized(this) {
            publishJob?.cancel()
            publishJob = null
            lastPublishedAtMs = Long.MIN_VALUE
            entries.clear()
        }
        onChanged(emptyList())
    }

    private fun formattedNow(): String {
        val locale = Locale.getDefault()
        val activeFormatter =
            formatter?.takeIf { formatterLocale == locale }
                ?: SimpleDateFormat("HH:mm:ss", locale).also {
                    formatterLocale = locale
                    formatter = it
                }
        return activeFormatter.format(Date())
    }

    private fun nextPublishSnapshotLocked(): List<McpLogEntry>? {
        val interval = minPublishIntervalMs.coerceAtLeast(0L)
        val scope = publishScope
        val now = nowMs()
        if (interval == 0L || scope == null || lastPublishedAtMs == Long.MIN_VALUE) {
            publishJob?.cancel()
            publishJob = null
            lastPublishedAtMs = now
            return entries.toList()
        }

        val elapsed = now - lastPublishedAtMs
        if (elapsed >= interval) {
            publishJob?.cancel()
            publishJob = null
            lastPublishedAtMs = now
            return entries.toList()
        }

        if (publishJob?.isActive != true) {
            val delayMs = interval - elapsed
            publishJob =
                scope.launch {
                    delay(delayMs)
                    flushPendingPublish()
                }
        }
        return null
    }

    private fun flushPendingPublish() {
        val snapshot =
            synchronized(this) {
                publishJob = null
                lastPublishedAtMs = nowMs()
                entries.toList()
            }
        onChanged(snapshot)
    }
}
