package os.kei.ui.page.main.github.page.action

import android.content.pm.ApplicationInfo
import java.io.File

internal fun ApplicationInfo?.installedApkSizeBytes(): Long {
    this ?: return -1L
    val paths = buildList {
        sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
        splitSourceDirs?.forEach { path ->
            path.takeIf { it.isNotBlank() }?.let(::add)
        }
    }.distinct()
    val total = paths.sumOf { path ->
        File(path).takeIf { it.isFile }?.length() ?: 0L
    }
    return total.takeIf { it > 0L } ?: -1L
}
