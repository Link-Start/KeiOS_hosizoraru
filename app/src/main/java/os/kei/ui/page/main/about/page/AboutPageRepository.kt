package os.kei.ui.page.main.about.page

import android.content.Context
import android.content.pm.PackageInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.ui.page.main.about.model.AboutComponentEntry
import os.kei.ui.page.main.about.model.AboutPermissionEntry
import os.kei.ui.page.main.about.model.buildComponentEntries
import os.kei.ui.page.main.about.model.buildPermissionEntries
import os.kei.ui.page.main.about.model.loadPackageDetailInfo

internal data class AboutPageDetailsState(
    val packageDetailInfo: PackageInfo? = null,
    val permissionEntries: List<AboutPermissionEntry> = emptyList(),
    val componentEntries: List<AboutComponentEntry> = emptyList(),
    val searchTargets: List<AboutSearchTarget> = emptyList(),
    val shizukuDetailMap: Map<String, String> = emptyMap(),
    val loaded: Boolean = false,
)

internal data class AboutPageManifestDetails(
    val packageDetailInfo: PackageInfo?,
    val permissionEntries: List<AboutPermissionEntry>,
    val componentEntries: List<AboutComponentEntry>,
)

internal class AboutPageRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.fileIo,
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
) {
    suspend fun loadManifestDetails(
        context: Context,
        notificationPermissionGranted: Boolean,
    ): AboutPageManifestDetails =
        withContext(ioDispatcher) {
            val packageDetailInfo = loadPackageDetailInfo(context)
            val permissionEntries =
                buildPermissionEntries(
                    context = context,
                    packageInfo = packageDetailInfo,
                    notificationPermissionGranted = notificationPermissionGranted,
                )
            val componentEntries =
                buildComponentEntries(
                    context = context,
                    packageInfo = packageDetailInfo,
                )
            AboutPageManifestDetails(
                packageDetailInfo = packageDetailInfo,
                permissionEntries = permissionEntries,
                componentEntries = componentEntries,
            )
        }

    suspend fun loadShizukuDetails(shizukuApiUtils: ShizukuApiUtils): Map<String, String> =
        withContext(ioDispatcher) {
            shizukuApiUtils.detailedRows().toMap()
        }

    suspend fun buildSearchTargets(
        context: Context,
        appLabel: String,
        shizukuStatus: String,
        permissionEntries: List<AboutPermissionEntry>,
        componentEntries: List<AboutComponentEntry>,
    ): List<AboutSearchTarget> =
        withContext(defaultDispatcher) {
            buildAboutSearchTargets(
                context = context,
                appLabel = appLabel,
                shizukuStatus = shizukuStatus,
                permissionEntries = permissionEntries,
                componentEntries = componentEntries,
            )
        }

    suspend fun deriveSearchState(
        targets: List<AboutSearchTarget>,
        query: String,
    ): AboutSearchUiState =
        withContext(defaultDispatcher) {
            val trimmedQuery = query.trim()
            if (trimmedQuery.isBlank()) {
                AboutSearchUiState()
            } else {
                val matchingTargets = targets.filter { it.matches(trimmedQuery) }
                AboutSearchUiState(
                    active = true,
                    matchingTargets = matchingTargets,
                    matchingCards = matchingTargets.map { it.card }.toSet(),
                )
            }
        }
}
