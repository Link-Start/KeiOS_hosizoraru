package os.kei.ui.page.main.ba

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.ext.showToast
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BaAccountId

/**
 * Applies the daily-done template and reports the result as a toast.
 *
 * Shared by the launcher shortcut and the tile's long-press editor, which are the two triggers with a
 * window on screen to toast into. The quick-settings tile deliberately does *not* use this: a tile click
 * is not foreground, Android 12 and up drop a background app's toast, and that path reports through
 * [BaDailyDoneNotificationDispatcher] instead.
 *
 * A `null` [accountId] is the all-accounts trigger, which is exactly the store's null filter. A non-null
 * one that no longer resolves is reported rather than silently doing nothing — a tile or shortcut outlives
 * the account it was bound to.
 */
internal object BaDailyDoneRunner {
    suspend fun applyAndToast(
        context: Context,
        accountId: BaAccountId?,
    ) {
        val targets = accountId?.let(::listOf)
        val outcomes =
            withContext(AppDispatchers.baFetch) {
                BASettingsStore.applyDailyDone(accountIds = targets)
            }
        when {
            outcomes.isEmpty() -> context.toast(R.string.ba_daily_done_toast_no_target)
            outcomes.none { it.value.changedAnything } ->
                context.toast(R.string.ba_daily_done_toast_already_done)

            else ->
                context.toast(
                    R.string.ba_daily_done_toast_applied_format,
                    outcomes.count { it.value.changedAnything },
                    outcomes.values.sumOf { it.craftSlotsStarted },
                )
        }
    }

    private suspend fun Context.toast(
        resId: Int,
        vararg args: Any,
    ) {
        withContext(Dispatchers.Main.immediate) {
            showToast(if (args.isEmpty()) getString(resId) else getString(resId, *args))
        }
    }
}
