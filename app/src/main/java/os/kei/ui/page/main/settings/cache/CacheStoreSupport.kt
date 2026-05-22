package os.kei.ui.page.main.settings.cache

import android.content.Context
import com.tencent.mmkv.MMKV
import os.kei.R
import os.kei.core.prefs.KeiMmkv
import os.kei.core.system.AppBuildEnv
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

private const val CACHE_EVENT_KV_ID = "cache_events"

internal fun formatActivity(
    context: Context,
    updatedAtMs: Long,
    clearedAtMs: Long,
): String {
    val updated = formatTimestamp(context, updatedAtMs)
    val cleared = formatTimestamp(context, clearedAtMs)
    return context.getString(R.string.settings_cache_activity, updated, cleared)
}

internal fun formatTimestamp(
    context: Context,
    epochMs: Long,
): String {
    if (epochMs <= 0L) return context.getString(R.string.settings_cache_no_record)
    return SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
}

internal fun clearDebugUiDump(context: Context) {
    val dir = AppBuildEnv.uiDumpDirectory(context)
    if (dir.exists()) {
        dir.deleteRecursively()
    }
    dir.mkdirs()
}

internal data class DirectoryStats(
    val fileCount: Int,
    val totalBytes: Long,
    val latestModifiedAtMs: Long,
)

internal fun collectDirectoryStats(root: File): DirectoryStats {
    if (!root.exists()) {
        return DirectoryStats(
            fileCount = 0,
            totalBytes = 0L,
            latestModifiedAtMs = 0L,
        )
    }
    val queue = ArrayDeque<File>()
    queue.add(root)
    var fileCount = 0
    var totalBytes = 0L
    var latestModifiedAtMs = 0L
    while (queue.isNotEmpty()) {
        val current = queue.removeLast()
        val modifiedAt = current.lastModified()
        if (modifiedAt > latestModifiedAtMs) {
            latestModifiedAtMs = modifiedAt
        }
        if (current.isFile) {
            fileCount += 1
            totalBytes += current.length().coerceAtLeast(0L)
            continue
        }
        current.listFiles().orEmpty().forEach(queue::add)
    }
    return DirectoryStats(
        fileCount = fileCount,
        totalBytes = totalBytes,
        latestModifiedAtMs = latestModifiedAtMs,
    )
}

internal fun mmkvLastModified(
    context: Context,
    id: String,
): Long {
    val root = File(context.filesDir, "mmkv")
    if (!root.exists()) return 0L
    return root
        .listFiles()
        .orEmpty()
        .filter { file -> file.name == id || file.name.startsWith("$id.") }
        .maxOfOrNull(File::lastModified)
        ?: 0L
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return if (digitGroups == 0) {
        "$bytes ${units[digitGroups]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }
}

internal object CacheEventStore {
    private val store: MMKV by lazy { KeiMmkv.byId(CACHE_EVENT_KV_ID) }

    fun loadClearedAt(id: String): Long = store.decodeLong("cleared_$id", 0L)

    fun markCleared(
        id: String,
        epochMs: Long = System.currentTimeMillis(),
    ) {
        store.encode("cleared_$id", epochMs.coerceAtLeast(0L))
    }
}
