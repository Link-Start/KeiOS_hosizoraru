package os.kei.ui.page.main.ba

import androidx.compose.runtime.Stable
import os.kei.ui.page.main.ba.support.BaPageSnapshot

@Stable
internal data class BaPageSettingsDraftState(
    val cafeLevel: Int,
    val mediaAdaptiveRotationEnabled: Boolean,
    val mediaSaveCustomEnabled: Boolean,
    val mediaSaveFixedTreeUri: String,
)

@Stable
internal data class BaPageNotificationDraftState(
    val apNotifyEnabled: Boolean,
    val cafeApNotifyEnabled: Boolean,
    val arenaRefreshNotifyEnabled: Boolean,
    val cafeVisitNotifyEnabled: Boolean,
    val calendarUpcomingNotifyEnabled: Boolean,
    val calendarEndingNotifyEnabled: Boolean,
    val poolUpcomingNotifyEnabled: Boolean,
    val poolEndingNotifyEnabled: Boolean,
    val calendarPoolChangeNotifyEnabled: Boolean,
    val calendarPoolNotifyLeadHours: Int,
    val apNotifyThresholdText: String,
    val cafeApNotifyThresholdText: String,
)

internal fun BaPageSnapshot.toSettingsDraftState(): BaPageSettingsDraftState =
    BaPageSettingsDraftState(
        cafeLevel = cafeLevel,
        mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
        mediaSaveCustomEnabled = mediaSaveCustomEnabled,
        mediaSaveFixedTreeUri = mediaSaveFixedTreeUri,
    )

internal fun BaPageSnapshot.toNotificationDraftState(): BaPageNotificationDraftState =
    BaPageNotificationDraftState(
        apNotifyEnabled = apNotifyEnabled,
        cafeApNotifyEnabled = cafeApNotifyEnabled,
        arenaRefreshNotifyEnabled = arenaRefreshNotifyEnabled,
        cafeVisitNotifyEnabled = cafeVisitNotifyEnabled,
        calendarUpcomingNotifyEnabled = calendarUpcomingNotifyEnabled,
        calendarEndingNotifyEnabled = calendarEndingNotifyEnabled,
        poolUpcomingNotifyEnabled = poolUpcomingNotifyEnabled,
        poolEndingNotifyEnabled = poolEndingNotifyEnabled,
        calendarPoolChangeNotifyEnabled = calendarPoolChangeNotifyEnabled,
        calendarPoolNotifyLeadHours = calendarPoolNotifyLeadHours,
        apNotifyThresholdText = apNotifyThreshold.toString(),
        cafeApNotifyThresholdText = cafeApNotifyThreshold.toString(),
    )
