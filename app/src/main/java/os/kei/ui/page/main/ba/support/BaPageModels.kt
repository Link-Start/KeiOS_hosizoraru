package os.kei.ui.page.main.ba.support

import os.kei.R

internal data class BaPageSnapshot(
    val serverIndex: Int = 2,
    val cafeLevel: Int = 10,
    val cafeStoredAp: Double = 0.0,
    val cafeLastHourMs: Long = 0L,
    val idNickname: String = BA_DEFAULT_NICKNAME,
    val idFriendCode: String = BA_DEFAULT_FRIEND_CODE,
    val idIndependentByServer: Boolean = false,
    val apLimit: Int = BA_AP_LIMIT_MAX,
    val apCurrent: Double = 0.0,
    val apRegenBaseMs: Long = 0L,
    val apSyncMs: Long = 0L,
    val apNotifyEnabled: Boolean = false,
    val apNotifyThreshold: Int = 120,
    val apLastNotifiedLevel: Int = -1,
    val arenaRefreshNotifyEnabled: Boolean = false,
    val arenaRefreshLastNotifiedSlotMs: Long = 0L,
    val cafeVisitNotifyEnabled: Boolean = false,
    val cafeVisitLastNotifiedSlotMs: Long = 0L,
    val calendarUpcomingNotifyEnabled: Boolean = false,
    val calendarEndingNotifyEnabled: Boolean = false,
    val poolUpcomingNotifyEnabled: Boolean = false,
    val poolEndingNotifyEnabled: Boolean = false,
    val calendarPoolChangeNotifyEnabled: Boolean = false,
    val calendarPoolNotifyLeadHours: Int = 24,
    val coffeeHeadpatMs: Long = 0L,
    val coffeeInvite1UsedMs: Long = 0L,
    val coffeeInvite2UsedMs: Long = 0L,
    val showEndedPools: Boolean = false,
    val showEndedActivities: Boolean = false,
    val showCalendarPoolImages: Boolean = true,
    val mediaAdaptiveRotationEnabled: Boolean = true,
    val mediaSaveCustomEnabled: Boolean = false,
    val mediaSaveFixedTreeUri: String = "",
    val calendarRefreshIntervalHours: Int = 12
)

internal data class BaCacheSnapshot(
    val raw: String = "",
    val syncMs: Long = 0L,
    val version: Int = 0
)

internal object BASessionState {
    var didResetScrollOnThisProcess: Boolean = false
}

internal enum class BaCalendarRefreshIntervalOption(val hours: Int, val labelRes: Int) {
    Hour1(1, R.string.ba_refresh_interval_1h),
    Hour3(3, R.string.ba_refresh_interval_3h),
    Hour6(6, R.string.ba_refresh_interval_6h),
    Hour12(12, R.string.ba_refresh_interval_12h),
    Hour24(24, R.string.ba_refresh_interval_24h);

    companion object {
        fun fromHours(hours: Int): BaCalendarRefreshIntervalOption {
            return entries.firstOrNull { it.hours == hours } ?: Hour12
        }
    }
}

internal enum class BaCalendarPoolNotifyLeadOption(val hours: Int, val labelRes: Int) {
    Hour1(1, R.string.ba_refresh_interval_1h),
    Hour3(3, R.string.ba_refresh_interval_3h),
    Hour6(6, R.string.ba_refresh_interval_6h),
    Hour12(12, R.string.ba_refresh_interval_12h),
    Hour24(24, R.string.ba_refresh_interval_24h);

    companion object {
        fun fromHours(hours: Int): BaCalendarPoolNotifyLeadOption {
            return entries.firstOrNull { it.hours == hours } ?: Hour24
        }
    }
}
