package os.kei.core.tile

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import os.kei.R
import os.kei.core.log.AppLogger
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BA_DAILY_TILE_SLOTS
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.accountIdAt
import os.kei.ui.page.main.ba.support.firstFreeSlot
import os.kei.ui.page.main.ba.support.retainingExistingAccounts
import os.kei.ui.page.main.ba.support.slotOf
import os.kei.ui.page.main.ba.support.withSlot

/** What came back from asking the system to add a tile. */
internal enum class BaDailyTileAddResult {
    Added,
    AlreadyAdded,

    /** The teacher said no, or walked away from the dialog. Either way the tile is not on the panel. */
    Declined,

    /** The system refused outright — wrong user, no status bar, or the request quota is spent. */
    Unavailable,
}

/**
 * Whether the tile is on the panel once the request has settled.
 *
 * The only reason to leave a component enabled, and to leave a pool slot claimed. Claiming has to
 * happen *before* the request, so every other outcome has to undo it.
 */
internal val BaDailyTileAddResult.keepsTile: Boolean
    get() = this == BaDailyTileAddResult.Added || this == BaDailyTileAddResult.AlreadyAdded

/**
 * First error code, from `StatusBarManager`: *"Values greater or equal to this value indicate an error
 * in the request."*
 *
 * The platform keeps the threshold itself private, so the boundary is restated here rather than
 * enumerating the six error constants. Splitting on it — instead of listing codes — is what makes a
 * result code the platform adds later land in the right bucket on its own.
 */
private const val TILE_ADD_FIRST_ERROR_CODE = 1000

/**
 * Maps a `requestAddTileService` result code onto something the teacher can be told.
 *
 * The distinction that matters is decline vs. unavailable, because they say different things: one is
 * "you chose not to", the other is "this device will not". Anything below
 * [TILE_ADD_FIRST_ERROR_CODE] is a *result* — the flow ran and the tile simply is not on the panel —
 * so it reads as a decline. That deliberately catches `TILE_ADD_REQUEST_RESULT_DIALOG_DISMISSED`
 * (`3`), which is `@hide` and so cannot be named here, but still reaches the callback when the dialog
 * is swiped away. Reporting that as unavailable would tell the teacher their device does not support
 * a tile they had just been offered.
 */
internal fun baDailyTileAddResultOf(code: Int): BaDailyTileAddResult =
    when {
        code == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> BaDailyTileAddResult.Added
        code == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> BaDailyTileAddResult.AlreadyAdded
        code >= TILE_ADD_FIRST_ERROR_CODE -> BaDailyTileAddResult.Unavailable
        // TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED, the hidden dismissed code, and any future result.
        else -> BaDailyTileAddResult.Declined
    }

/** Which daily-done trigger a quick-settings component is, for a long-press that has to be told apart. */
internal sealed interface BaDailyTileKind {
    data object AllAccounts : BaDailyTileKind

    data class AccountSlot(val slot: Int) : BaDailyTileKind
}

/**
 * Claims, releases and keeps the daily-done tiles in step with the account list.
 *
 * Every tile component is declared `android:enabled="false"`, so nothing shows up in the quick-settings
 * editor until it is claimed here. Enabling is therefore part of claiming, and it has to happen *before*
 * the add request: an add for a disabled component comes back
 * `TILE_ADD_REQUEST_ERROR_BAD_COMPONENT`.
 *
 * The add request is rate limited by the platform **per component**, and its javadoc is explicit that the
 * system "can choose to auto-deny a request if the user has denied that specific request (user,
 * ComponentName) enough times before". So it is only ever fired from an explicit tap, never on a sheet
 * opening or an account change, and a slot's component keeps its identity for as long as possible rather
 * than being recycled between accounts.
 */
internal object BaDailyTileManager {
    private const val TAG = "BaDailyTile"

    private val accountTileClasses =
        listOf(
            BaDailyDoneAccountTileService1::class.java,
            BaDailyDoneAccountTileService2::class.java,
            BaDailyDoneAccountTileService3::class.java,
        )

    private fun allComponent(context: Context): ComponentName =
        ComponentName(context, BaDailyDoneAllTileService::class.java)

    private fun accountComponent(context: Context, slot: Int): ComponentName =
        ComponentName(context, accountTileClasses[slot.coerceIn(0, BA_DAILY_TILE_SLOTS - 1)])

    private fun setComponentEnabled(
        context: Context,
        component: ComponentName,
        enabled: Boolean,
    ) {
        val target =
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                component,
                target,
                PackageManager.DONT_KILL_APP,
            )
        }.onFailure { throwable ->
            AppLogger.e(TAG, "failed to set $component enabled=$enabled", throwable)
        }
    }

    internal fun isComponentEnabled(
        context: Context,
        component: ComponentName,
    ): Boolean =
        context.packageManager.getComponentEnabledSetting(component) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun isAllAccountsTileEnabled(context: Context): Boolean =
        isComponentEnabled(context, allComponent(context))

    fun isAccountTileEnabled(context: Context, slot: Int): Boolean =
        isComponentEnabled(context, accountComponent(context, slot))

    /**
     * Enables the all-accounts tile and asks the system to add it.
     *
     * Must be called while the app is in the foreground — an add request from the background returns
     * `TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND`.
     *
     * The enable is rolled back when the request does not end with the tile on the panel. Leaving it
     * enabled would put the component in the quick-settings editor the teacher had just declined it
     * from, and would make the settings row read "Remove tile" for a tile that is not there — the row
     * derives from the component state, which is the only thing that survives a process death.
     */
    fun requestAllAccountsTile(
        context: Context,
        onResult: (BaDailyTileAddResult) -> Unit,
    ) {
        val component = allComponent(context)
        val wasEnabled = isComponentEnabled(context, component)
        setComponentEnabled(context, component, enabled = true)
        request(
            context = context,
            component = component,
            label = context.getString(R.string.ba_daily_done_tile_label_all),
        ) { result ->
            if (!result.keepsTile && !wasEnabled) {
                setComponentEnabled(context, component, enabled = false)
                AppLogger.i(TAG) { "rolled back the all-accounts tile after $result" }
            }
            onResult(result)
        }
    }

    /**
     * Binds [accountId] to a free pool slot, enables that slot's component and asks to add it.
     *
     * Reuses the slot the account already holds when there is one, so a teacher who removes and re-adds
     * the tile does not burn a second component's request quota.
     *
     * A slot claimed for this request is released again if the tile does not end up on the panel. The
     * pool is only [BA_DAILY_TILE_SLOTS] deep, so a declined request must not consume one of them.
     */
    fun requestAccountTile(
        context: Context,
        accountId: BaAccountId,
        accountDisplayName: String,
        onResult: (BaDailyTileAddResult) -> Unit,
    ): Boolean {
        val state = BASettingsStore.loadDailyTileState()
        val heldSlotBefore = state.slotOf(accountId)
        val slot = heldSlotBefore ?: state.firstFreeSlot() ?: return false
        BASettingsStore.saveDailyTileState(state.withSlot(slot, accountId))
        val component = accountComponent(context, slot)
        val wasEnabled = isComponentEnabled(context, component)
        setComponentEnabled(context, component, enabled = true)
        request(
            context = context,
            component = component,
            label =
                context.getString(
                    R.string.ba_daily_done_tile_label_account_format,
                    accountDisplayName,
                ),
        ) { result ->
            if (!result.keepsTile) {
                if (heldSlotBefore == null) {
                    // Re-read rather than reusing the captured state: this runs on the main executor
                    // after the dialog closes, and a WebDAV merge could have rewritten the bindings.
                    BASettingsStore.saveDailyTileState(
                        BASettingsStore.loadDailyTileState().withSlot(slot, null),
                    )
                }
                if (!wasEnabled) setComponentEnabled(context, component, enabled = false)
                AppLogger.i(TAG) { "rolled back tile slot=$slot after $result" }
            }
            onResult(result)
        }
        return true
    }

    /** Releases an account's slot and hides its component again. */
    fun releaseAccountTile(
        context: Context,
        accountId: BaAccountId,
    ) {
        val state = BASettingsStore.loadDailyTileState()
        val slot = state.slotOf(accountId) ?: return
        BASettingsStore.saveDailyTileState(state.withSlot(slot, null))
        setComponentEnabled(context, accountComponent(context, slot), enabled = false)
    }

    fun releaseAllAccountsTile(context: Context) {
        setComponentEnabled(context, allComponent(context), enabled = false)
    }

    /**
     * Drops bindings for accounts that no longer exist and hides the freed components.
     *
     * Safe to call after any account mutation; it writes only when something actually changed, because the
     * only available change signal fires on every BA write including each AP tick.
     */
    fun syncWithAccounts(
        context: Context,
        existingAccountIds: Collection<BaAccountId>,
    ) {
        val state = BASettingsStore.loadDailyTileState()
        val synced = state.retainingExistingAccounts(existingAccountIds)
        if (synced == state) return
        BASettingsStore.saveDailyTileState(synced)
        (0 until BA_DAILY_TILE_SLOTS).forEach { slot ->
            if (state.accountIdAt(slot) != null && synced.accountIdAt(slot) == null) {
                setComponentEnabled(context, accountComponent(context, slot), enabled = false)
                AppLogger.i(TAG) { "released tile slot=$slot after its account was deleted" }
            }
        }
    }

    /**
     * Which of the four declared tiles a component name refers to, or `null` when it is none of them.
     *
     * The component arrives as an extra on the platform's `QS_TILE_PREFERENCES` intent, i.e. from outside
     * the app, so the package is checked rather than assumed: the preferences activity has to be exported
     * for the system to launch it, which means anyone can send it a component name. Resolving to `null`
     * is the safe answer, not a per-account guess.
     */
    fun kindOf(
        context: Context,
        component: ComponentName?,
    ): BaDailyTileKind? {
        val target = component ?: return null
        if (target.packageName != context.packageName) return null
        if (target.className == BaDailyDoneAllTileService::class.java.name) {
            return BaDailyTileKind.AllAccounts
        }
        val slot = accountTileClasses.indexOfFirst { it.name == target.className }
        return if (slot >= 0) BaDailyTileKind.AccountSlot(slot) else null
    }

    private fun request(
        context: Context,
        component: ComponentName,
        label: String,
        onResult: (BaDailyTileAddResult) -> Unit,
    ) {
        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            onResult(BaDailyTileAddResult.Unavailable)
            return
        }
        runCatching {
            statusBarManager.requestAddTileService(
                component,
                label,
                // The same icon the tile itself declares: this dialog is the system previewing the tile,
                // so showing anything else would preview a tile that does not exist.
                Icon.createWithResource(context, R.drawable.ic_ba_daily_done_mono),
                context.mainExecutor,
            ) { code ->
                val result = baDailyTileAddResultOf(code)
                if (result == BaDailyTileAddResult.Unavailable) {
                    // The 1000-series: mismatched package, a request already in flight, a disabled
                    // component, the wrong user, the app in the background, or no status bar service.
                    // None of these are actionable in the UI, so the code only survives in the log.
                    AppLogger.w(TAG) { "tile add request refused with code $code" }
                }
                onResult(result)
            }
        }.onFailure { throwable ->
            AppLogger.e(TAG, "requestAddTileService threw for $component", throwable)
            onResult(BaDailyTileAddResult.Unavailable)
        }
    }
}
