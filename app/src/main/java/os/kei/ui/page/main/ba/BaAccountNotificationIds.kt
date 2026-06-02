package os.kei.ui.page.main.ba

import os.kei.mcp.notification.McpNotificationHelper
import os.kei.ui.page.main.ba.support.BaAccountId

internal enum class BaAccountNotificationKind(
    val legacyId: Int,
    val offset: Int,
) {
    Ap(
        legacyId = McpNotificationHelper.BA_AP_NOTIFICATION_ID,
        offset = 0,
    ),
    CafeAp(
        legacyId = McpNotificationHelper.BA_CAFE_AP_NOTIFICATION_ID,
        offset = 1,
    ),
    CafeVisit(
        legacyId = McpNotificationHelper.BA_CAFE_VISIT_NOTIFICATION_ID,
        offset = 2,
    ),
    ArenaRefresh(
        legacyId = McpNotificationHelper.BA_ARENA_REFRESH_NOTIFICATION_ID,
        offset = 3,
    );

    fun notificationId(accountId: BaAccountId): Int =
        BaAccountNotificationIds.notificationId(
            accountId = accountId,
            kind = this,
        )
}

internal object BaAccountNotificationIds {
    private const val ACCOUNT_NOTIFICATION_BASE_ID = 43_000
    private const val ACCOUNT_NOTIFICATION_BUCKET_COUNT = 100_000
    private const val KIND_STRIDE = 4

    fun notificationId(
        accountId: BaAccountId,
        kind: BaAccountNotificationKind,
    ): Int {
        val bucket =
            Math.floorMod(
                accountId.value.hashCode(),
                ACCOUNT_NOTIFICATION_BUCKET_COUNT,
            )
        return ACCOUNT_NOTIFICATION_BASE_ID + bucket * KIND_STRIDE + kind.offset
    }
}
