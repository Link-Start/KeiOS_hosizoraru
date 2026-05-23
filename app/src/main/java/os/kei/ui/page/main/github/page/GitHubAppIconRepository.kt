package os.kei.ui.page.main.github.page

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.AppIconCache

internal data class GitHubAppIconLoadResult(
    val bitmaps: Map<String, Bitmap>,
    val missingPackages: Set<String>,
)

internal class GitHubAppIconRepository {
    fun cachedBitmap(packageName: String): Bitmap? = AppIconCache.get(packageName.trim())

    suspend fun loadIcons(
        context: Context,
        packageNames: List<String>,
    ): GitHubAppIconLoadResult =
        withContext(AppDispatchers.githubNetwork) {
            val appContext = context.applicationContext
            val bitmaps = linkedMapOf<String, Bitmap>()
            val missingPackages = linkedSetOf<String>()
            packageNames
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { packageName ->
                    AppIconCache.get(packageName)?.let { bitmap ->
                        bitmaps[packageName] = bitmap
                        return@forEach
                    }
                    val bitmap = AppIconCache.getOrLoad(appContext, packageName)
                    if (bitmap == null) {
                        missingPackages.add(packageName)
                    } else {
                        bitmaps[packageName] = bitmap
                    }
                }
            GitHubAppIconLoadResult(
                bitmaps = bitmaps,
                missingPackages = missingPackages,
            )
        }
}
