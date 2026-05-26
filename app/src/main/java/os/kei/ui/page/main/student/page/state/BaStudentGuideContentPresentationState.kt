package os.kei.ui.page.main.student.page.state

import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.page.support.resolveGuideBottomTabs
import os.kei.ui.page.main.student.simulateRowsForDisplay
import os.kei.ui.page.main.student.tabcontent.profile.GuideNpcSatelliteProfileState
import os.kei.ui.page.main.student.tabcontent.profile.buildGuideNpcSatelliteProfileState
import os.kei.ui.page.main.student.tabcontent.render.GuideGalleryTabResolvedState
import os.kei.ui.page.main.student.tabcontent.render.GuideProfileTabHeaderState
import os.kei.ui.page.main.student.tabcontent.render.buildGuideProfileTabHeaderState
import os.kei.ui.page.main.student.tabcontent.render.resolveGuideGalleryTabState
import os.kei.ui.page.main.student.tabcontent.simulate.GuideSimulateData
import os.kei.ui.page.main.student.tabcontent.simulate.buildGuideSimulateData

internal data class BaStudentGuideContentPresentationState(
    val sourceUrl: String = "",
    val guideSyncedAtMs: Long = -1L,
    val bottomTabs: List<GuideBottomTab> = listOf(GuideBottomTab.Archive),
    val profileHeaderState: GuideProfileTabHeaderState? = null,
    val npcProfileState: GuideNpcSatelliteProfileState? = null,
    val galleryState: GuideGalleryTabResolvedState? = null,
    val simulateData: GuideSimulateData = GuideSimulateData(),
) {
    fun matches(info: BaStudentGuideInfo?): Boolean =
        info != null &&
            sourceUrl == info.sourceUrl &&
            guideSyncedAtMs == info.syncedAtMs
}

internal suspend fun deriveBaStudentGuideContentPresentationState(
    info: BaStudentGuideInfo?,
    isNpcSatelliteGuide: Boolean,
): BaStudentGuideContentPresentationState =
    withContext(AppDispatchers.uiDerivation) {
        if (info == null) {
            return@withContext BaStudentGuideContentPresentationState()
        }
        BaStudentGuideContentPresentationState(
            sourceUrl = info.sourceUrl,
            guideSyncedAtMs = info.syncedAtMs,
            bottomTabs = resolveGuideBottomTabs(info),
            profileHeaderState =
                if (isNpcSatelliteGuide) {
                    null
                } else {
                    buildGuideProfileTabHeaderState(info)
                },
            npcProfileState =
                if (isNpcSatelliteGuide) {
                    buildGuideNpcSatelliteProfileState(info)
                } else {
                    null
                },
            galleryState = resolveGuideGalleryTabState(info),
            simulateData = buildGuideSimulateData(info.simulateRowsForDisplay()),
        )
    }
