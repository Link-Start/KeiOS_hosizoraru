package os.kei.ui.page.main.ba

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import os.kei.ui.page.main.ba.support.BA_AP_MAX

internal data class BaApNotificationSyncRequest(
    val currentDisplay: Int,
    val limitDisplay: Int,
    val thresholdDisplay: Int,
    val notifyEnabled: Boolean,
)

internal object BaApNotificationSyncCoordinator {
    private const val NOTIFICATION_SYNC_TIMEOUT_MS = 1_500L

    suspend fun sync(
        context: Context,
        office: BaOfficeController,
        request: BaApNotificationSyncRequest,
    ) {
        val normalizedRequest = request.normalized()
        val thresholdNotificationSent = maybeSendThresholdNotification(
            context = context,
            office = office,
            request = normalizedRequest,
        )
        if (!thresholdNotificationSent) {
            refreshActiveNotification(
                context = context,
                request = normalizedRequest,
            )
        }
    }

    private suspend fun maybeSendThresholdNotification(
        context: Context,
        office: BaOfficeController,
        request: BaApNotificationSyncRequest,
    ): Boolean {
        if (!request.notifyEnabled) {
            office.updateApLastNotifiedLevelIfChanged(-1)
            return false
        }
        if (request.currentDisplay < request.thresholdDisplay) {
            office.updateApLastNotifiedLevelIfChanged(-1)
            return false
        }
        if (request.currentDisplay == office.apLastNotifiedLevel) return false

        val sent = withNotificationTimeout {
            BaApNotificationDispatcher.send(
                context = context,
                currentDisplay = request.currentDisplay,
                limitDisplay = request.limitDisplay,
                thresholdDisplay = request.thresholdDisplay,
            )
        }
        if (sent) {
            office.updateApLastNotifiedLevelIfChanged(request.currentDisplay)
        }
        return sent
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
            )
        }
    }

    private suspend fun withNotificationTimeout(block: () -> Boolean): Boolean {
        return withTimeoutOrNull(NOTIFICATION_SYNC_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { block() }
        } ?: false
    }

    private fun BaApNotificationSyncRequest.normalized(): BaApNotificationSyncRequest {
        val normalizedLimit = limitDisplay.coerceIn(0, BA_AP_MAX)
        return copy(
            currentDisplay = currentDisplay.coerceIn(0, BA_AP_MAX),
            limitDisplay = normalizedLimit,
            thresholdDisplay = thresholdDisplay.coerceIn(0, BA_AP_MAX),
        )
    }
}
