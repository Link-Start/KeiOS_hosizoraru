package os.kei.ui.page.main.about.page

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.ui.page.main.about.model.AboutAppDetails
import os.kei.ui.page.main.about.model.AboutComponentEntry
import os.kei.ui.page.main.about.model.AboutPermissionEntry
import os.kei.ui.page.main.about.model.AboutTechDetails
import os.kei.ui.page.main.about.model.buildAboutAppDetails
import os.kei.ui.page.main.about.model.buildAboutTechDetails
import os.kei.ui.page.main.about.model.buildComponentEntries
import os.kei.ui.page.main.about.model.buildPermissionEntries
import os.kei.ui.page.main.about.model.loadPackageDetailInfo

@Immutable
internal data class AboutPageDetailsState(
    val appDetails: AboutAppDetails = AboutAppDetails(),
    val permissionEntries: List<AboutPermissionEntry> = emptyList(),
    val componentEntries: List<AboutComponentEntry> = emptyList(),
    val techDetails: AboutTechDetails = AboutTechDetails(),
    val searchTargets: List<AboutSearchTarget> = emptyList(),
    val shizukuDetailMap: Map<String, String> = emptyMap(),
    val loaded: Boolean = false,
)

internal class AboutPageRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.fileIo,
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
) {
    suspend fun loadDetails(
        context: Context,
        appLabel: String,
        shizukuStatus: String,
        notificationPermissionGranted: Boolean,
        shizukuApiUtils: ShizukuApiUtils,
    ): AboutPageDetailsState {
        val details =
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
                AboutPageDetailsState(
                    appDetails = buildAboutAppDetails(context, appLabel, packageDetailInfo),
                    permissionEntries = permissionEntries,
                    componentEntries = componentEntries,
                    techDetails = buildAboutTechDetails(context),
                    shizukuDetailMap = shizukuApiUtils.detailedRows().toMap(),
                    loaded = true,
                )
            }
        val searchTargets =
            withContext(defaultDispatcher) {
                buildAboutSearchTargets(
                    context = context,
                    appLabel = appLabel,
                    shizukuStatus = shizukuStatus,
                    permissionEntries = details.permissionEntries,
                    componentEntries = details.componentEntries,
                )
            }
        return details.copy(searchTargets = searchTargets)
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
