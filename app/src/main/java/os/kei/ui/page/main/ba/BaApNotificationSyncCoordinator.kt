package os.kei.ui.page.main.ba

import android.content.Context
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import kotlinx.coroutines.withTimeoutOrNull
import os.kei.ui.page.main.ba.support.BA_AP_MAX

internal data class BaApNotificationSyncRequest(
    val currentDisplay: Int,
    val limitDisplay: Int,
    val thresholdDisplay: Int,
    val notifyEnabled: Boolean,
    val lastNotifiedLevel: Int,
    val notificationId: Int = BaAccountNotificationKind.Ap.legacyId,
    val accountDisplayName: String = "",
)

internal data class BaApNotificationSyncResult(
    val lastNotifiedLevel: Int? = null,
)

internal data class BaApNotificationSyncPlan(
    val request: BaApNotificationSyncRequest,
    val shouldSendThresholdNotification: Boolean = false,
    val shouldRefreshActiveNotification: Boolean = true,
    val nextLastNotifiedLevel: Int? = null,
)

internal object BaApNotificationSyncCoordinator {
    private const val NOTIFICATION_SYNC_TIMEOUT_MS = 1_500L

    suspend fun sync(
        context: Context,
        request: BaApNotificationSyncRequest,
    ): BaApNotificationSyncResult {
        val plan = planBaApNotificationSync(request)
        var nextLastNotifiedLevel = plan.nextLastNotifiedLevel
        val thresholdNotificationSent = if (plan.shouldSendThresholdNotification) {
            sendThresholdNotification(
                context = context,
                request = plan.request,
            )
        } else {
            false
        }
        if (thresholdNotificationSent) {
            nextLastNotifiedLevel = plan.request.currentDisplay
        } else if (plan.shouldRefreshActiveNotification || plan.shouldSendThresholdNotification) {
            refreshActiveNotification(
                context = context,
                request = plan.request,
            )
        }
        return BaApNotificationSyncResult(
            lastNotifiedLevel = nextLastNotifiedLevel
        )
    }

    private suspend fun sendThresholdNotification(
        context: Context,
        request: BaApNotificationSyncRequest,
    ): Boolean {
        return withNotificationTimeout {
            BaApNotificationDispatcher.send(
                context = context,
                currentDisplay = request.currentDisplay,
                limitDisplay = request.limitDisplay,
                thresholdDisplay = request.thresholdDisplay,
                notificationId = request.notificationId,
                accountDisplayName = request.accountDisplayName,
            )
        }
    }

    private suspend fun refreshActiveNotification(
        context: Context,
        request: BaApNotificationSyncRequest,
    ) {
        withNotificationTimeout {
            BaApNotificationDispatcher.refreshIfActive(
                context = context,
                currentDisplay = request.currentDisplay,
                limitDisplay = request.limitDisplay,
                thresholdDisplay = request.thresholdDisplay,
                notificationId = request.notificationId,
                accountDisplayName = request.accountDisplayName,
            )
        }
    }

    private suspend fun withNotificationTimeout(block: () -> Boolean): Boolean {
        return withTimeoutOrNull(NOTIFICATION_SYNC_TIMEOUT_MS) {
            withContext(AppDispatchers.baFetch) { block() }
        } ?: false
    }

    internal fun BaApNotificationSyncRequest.normalized(): BaApNotificationSyncRequest {
        val normalizedLimit = limitDisplay.coerceIn(0, BA_AP_MAX)
        return copy(
            currentDisplay = currentDisplay.coerceIn(0, BA_AP_MAX),
            limitDisplay = normalizedLimit,
            thresholdDisplay = thresholdDisplay.coerceIn(0, BA_AP_MAX),
            lastNotifiedLevel = lastNotifiedLevel.coerceIn(-1, BA_AP_MAX),
        )
    }
}

internal fun planBaApNotificationSync(
    request: BaApNotificationSyncRequest,
): BaApNotificationSyncPlan {
    val normalizedRequest = with(BaApNotificationSyncCoordinator) { request.normalized() }
    val resetLastNotifiedLevel = (-1).takeIf { normalizedRequest.lastNotifiedLevel != -1 }
    if (!normalizedRequest.notifyEnabled) {
        return BaApNotificationSyncPlan(
            request = normalizedRequest,
            nextLastNotifiedLevel = resetLastNotifiedLevel,
        )
    }
    if (normalizedRequest.currentDisplay < normalizedRequest.thresholdDisplay) {
        return BaApNotificationSyncPlan(
            request = normalizedRequest,
            nextLastNotifiedLevel = resetLastNotifiedLevel,
        )
    }
    if (normalizedRequest.currentDisplay == normalizedRequest.lastNotifiedLevel) {
        return BaApNotificationSyncPlan(request = normalizedRequest)
    }
    return BaApNotificationSyncPlan(
        request = normalizedRequest,
        shouldSendThresholdNotification = true,
        shouldRefreshActiveNotification = false,
    )
}
