package os.kei.ui.page.main.ba

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.tile.BaDailyTileKind
import os.kei.core.tile.BaDailyTileManager
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.accountIdAt
import os.kei.ui.page.main.widget.sheet.SceneBackdropHost
import android.graphics.Color as AndroidColor

/**
 * The daily quick-settings tiles' long-press destination.
 *
 * Android reserves a tile's long-press for `TileService.ACTION_QS_TILE_PREFERENCES` and falls back to the
 * system app-info screen when no activity handles it — which is what these tiles used to do, telling the
 * teacher about storage and permissions instead of about the tile they were holding. SystemUI attaches the
 * pressed component as `Intent.EXTRA_COMPONENT_NAME`, so one activity serves all four tiles and reads its
 * target off the intent.
 *
 * A window rather than a page: it is launched from the shade, so it has to stand on its own. It stays
 * translucent and lets the sheet draw its own scrim, so what recedes behind the sheet is whatever the
 * launch left on screen rather than a second dim of our own.
 *
 * Being exported is what lets the platform launch it, not a decision about trust, so nothing here acts on
 * the intent beyond choosing which name to show: the template is written only by an explicit tap, and an
 * unrecognised component falls back to the all-accounts scope rather than guessing at an account.
 */
class BaDailyDoneTemplateActivity : ComponentActivity() {
    private var target by mutableStateOf(Target(BaDailyDoneTemplateScope.AllAccounts, accountId = null))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureTranslucentWindow()
        target = resolveTarget(intent)

        setContent {
            var show by remember { mutableStateOf(true) }
            var applying by remember { mutableStateOf(false) }
            var config by remember { mutableStateOf(BASettingsStore.loadDailyDoneConfig()) }
            val currentTarget = target

            BaStandaloneActivityTheme {
                // Sheets only get real glass where a scene backdrop exists to sample. Without this the
                // sheet still works, but falls back to an opaque fill.
                SceneBackdropHost {
                    BaDailyDoneTemplateSheet(
                        show = show,
                        scope = currentTarget.scope,
                        config = config,
                        applying = applying,
                        onSave = { draft ->
                            BASettingsStore.saveDailyDoneConfig(draft)
                            config = draft
                            showToast(getString(R.string.ba_daily_template_saved))
                            show = false
                        },
                        onApply = { draft ->
                            if (!applying) {
                                applying = true
                                // Saved before applied, so what just ran is also what a later plain tap
                                // will run. The sheet is dismissed only once the run has reported back:
                                // finishing first would cancel the lifecycle scope mid-write.
                                BASettingsStore.saveDailyDoneConfig(draft)
                                config = draft
                                lifecycleScope.launch {
                                    BaDailyDoneRunner.applyAndToast(
                                        context = this@BaDailyDoneTemplateActivity,
                                        accountId = currentTarget.accountId,
                                    )
                                    applying = false
                                    show = false
                                }
                            }
                        },
                        onDismissRequest = { show = false },
                        onDismissFinished = ::finishSafely,
                    )
                }
            }
        }
    }

    /**
     * Re-resolves the target rather than keeping the one this instance opened with.
     *
     * `singleTop` means a long-press on a *different* daily tile while the editor is up arrives here
     * instead of creating a second instance, and the account name on screen has to follow it.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        target = resolveTarget(intent)
    }

    /**
     * Resolves the pressed tile into a scope to show and an account to apply to.
     *
     * The two are separate because a per-account tile outlives its account: the scope then says so and the
     * editor stays open for the template, which is shared and still worth fixing from here, while there is
     * no account left to apply it to.
     */
    private fun resolveTarget(source: Intent?): Target {
        val component =
            source?.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        return when (val kind = BaDailyTileManager.kindOf(this, component)) {
            null,
            BaDailyTileKind.AllAccounts,
            -> Target(BaDailyDoneTemplateScope.AllAccounts, accountId = null)

            is BaDailyTileKind.AccountSlot -> {
                val account =
                    BASettingsStore
                        .loadDailyTileState()
                        .accountIdAt(kind.slot)
                        ?.let { id ->
                            BASettingsStore.loadAccountState().accounts.firstOrNull { it.profile.id == id }
                        }
                if (account == null) {
                    Target(BaDailyDoneTemplateScope.Unbound, accountId = null)
                } else {
                    Target(
                        scope = BaDailyDoneTemplateScope.Account(account.profile.displayName),
                        accountId = account.profile.id,
                    )
                }
            }
        }
    }

    private fun configureTranslucentWindow() {
        enableEdgeToEdge()
        window.setBackgroundDrawable(AndroidColor.TRANSPARENT.toDrawable())
        window.isNavigationBarContrastEnforced = false
        // The sheet already dims what is behind it; a window dim on top of that would double up.
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0f }
    }

    private fun finishSafely() {
        if (!isFinishing) finish()
    }

    private data class Target(
        val scope: BaDailyDoneTemplateScope,
        val accountId: BaAccountId?,
    )

    companion object {
        /**
         * Opens the editor for the all-accounts template, for the in-app entry point.
         *
         * A tile's own long-press does not come through here: the platform launches the activity itself,
         * with the pressed component attached.
         */
        fun launch(context: Context) {
            // Same shape as the debug activities' launcher: a Compose `LocalContext` is normally the host
            // activity, but it is not guaranteed to be, and startActivity on an application context
            // without FLAG_ACTIVITY_NEW_TASK throws.
            val host = context.findBaHostActivity()
            val intent =
                Intent(context, BaDailyDoneTemplateActivity::class.java).apply {
                    if (host == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            (host ?: context).startActivity(intent)
        }
    }
}
