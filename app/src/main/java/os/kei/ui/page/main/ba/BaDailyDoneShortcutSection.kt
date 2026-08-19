@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.shortcut.BaDailyShortcutSync
import os.kei.core.tile.BaDailyTileAddResult
import os.kei.core.tile.BaDailyTileManager
import os.kei.ui.page.main.ba.support.BA_DAILY_TILE_SLOTS
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaDailyTileMode
import os.kei.ui.page.main.ba.support.slotOf
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetChoiceCard
import os.kei.ui.page.main.widget.sheet.SheetChoiceCardDensity
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionHeader

/**
 * Manages the daily-done quick-settings tiles and launcher shortcuts.
 *
 * Deliberately *not* hoisted into the sheet's draft/save model like the rest of the account sheet. Every
 * action here is an immediate system side effect — enabling a manifest component, asking the platform to
 * add a tile — none of which can be staged and replayed on save. Hoisting them would also mean threading
 * six callbacks through four files to reach a button whose result comes back asynchronously from the
 * system.
 *
 * The add request is fired only from these taps. The platform rate limits it per component and can
 * auto-deny permanently after repeated refusals, so nothing here requests on composition or on an
 * account change.
 */
@Composable
internal fun BaDailyDoneShortcutSection(
    backdrop: Backdrop?,
    accounts: List<BaOfficeAccountCardUiState>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Bumped after every action so the enabled flags below are re-read from the package manager.
    var revision by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(BASettingsStore.loadDailyTileState().mode) }

    val tileState = remember(revision) { BASettingsStore.loadDailyTileState() }
    val allTileEnabled = remember(revision) { BaDailyTileManager.isAllAccountsTileEnabled(context) }

    fun report(result: BaDailyTileAddResult) {
        val message =
            when (result) {
                BaDailyTileAddResult.Added -> R.string.ba_daily_done_tile_added
                BaDailyTileAddResult.AlreadyAdded -> R.string.ba_daily_done_tile_already_added
                BaDailyTileAddResult.Declined -> R.string.ba_daily_done_tile_declined
                BaDailyTileAddResult.Unavailable -> R.string.ba_daily_done_tile_unavailable
            }
        context.showToast(context.getString(message))
        revision++
    }

    fun applyMode(next: BaDailyTileMode) {
        if (next == mode) return
        mode = next
        BASettingsStore.saveDailyTileState(BASettingsStore.loadDailyTileState().copy(mode = next))
        // The shortcut set is derived from the mode, so it has to be rebuilt now rather than waiting for
        // an account change to move the fingerprint.
        scope.launch { BaDailyShortcutSync.resync(context) }
        revision++
    }

    SheetSectionHeader(
        text = stringResource(R.string.ba_daily_done_section_title),
        summary = stringResource(R.string.ba_daily_done_section_summary),
    )

    SheetSectionCard(verticalSpacing = 10.dp) {
        // The same editor a tile long-press opens, reachable for a teacher who has not added a tile —
        // and the only way to reach it at all when the shortcut is the trigger they use.
        SheetControlRow(
            label = stringResource(R.string.ba_daily_template_row_label),
            summary = stringResource(R.string.ba_daily_template_scope_summary),
        ) {
            AppLiquidTextButton(
                backdrop = backdrop,
                text = stringResource(R.string.ba_daily_template_edit),
                variant = GlassVariant.SheetAction,
                onClick = { BaDailyDoneTemplateActivity.launch(context) },
            )
        }
    }

    SheetSectionCard(verticalSpacing = 10.dp) {
        SheetChoiceCard(
            title = stringResource(R.string.ba_daily_done_mode_all),
            summary = stringResource(R.string.ba_daily_done_tile_subtitle),
            selected = mode == BaDailyTileMode.AllAccounts,
            density = SheetChoiceCardDensity.Compact,
            onSelect = { applyMode(BaDailyTileMode.AllAccounts) },
        )
        SheetChoiceCard(
            title = stringResource(R.string.ba_daily_done_mode_per_account),
            summary =
                stringResource(
                    R.string.ba_daily_done_mode_per_account_summary,
                    BA_DAILY_TILE_SLOTS,
                ),
            selected = mode == BaDailyTileMode.PerAccount,
            density = SheetChoiceCardDensity.Compact,
            onSelect = { applyMode(BaDailyTileMode.PerAccount) },
        )
    }

    SheetSectionCard(verticalSpacing = 10.dp) {
        SheetControlRow(label = stringResource(R.string.ba_daily_done_tile_label_all)) {
            AppLiquidTextButton(
                backdrop = backdrop,
                text =
                    stringResource(
                        if (allTileEnabled) {
                            R.string.ba_daily_done_remove_tile
                        } else {
                            R.string.ba_daily_done_add_tile
                        },
                    ),
                variant = GlassVariant.SheetAction,
                onClick = {
                    if (allTileEnabled) {
                        BaDailyTileManager.releaseAllAccountsTile(context)
                        revision++
                    } else {
                        BaDailyTileManager.requestAllAccountsTile(context, ::report)
                    }
                },
            )
        }

        if (mode == BaDailyTileMode.PerAccount) {
            accounts.forEach { account ->
                val bound = tileState.slotOf(BaAccountId(account.id.value)) != null
                SheetControlRow(
                    label =
                        stringResource(
                            R.string.ba_daily_done_tile_label_account_format,
                            account.displayName,
                        ),
                ) {
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        text =
                            stringResource(
                                if (bound) {
                                    R.string.ba_daily_done_remove_tile
                                } else {
                                    R.string.ba_daily_done_add_tile
                                },
                            ),
                        variant = GlassVariant.SheetAction,
                        onClick = {
                            if (bound) {
                                BaDailyTileManager.releaseAccountTile(context, account.id)
                                revision++
                            } else {
                                val claimed =
                                    BaDailyTileManager.requestAccountTile(
                                        context = context,
                                        accountId = account.id,
                                        accountDisplayName = account.displayName,
                                        onResult = ::report,
                                    )
                                if (!claimed) {
                                    // Every slot is taken. A tile is a manifest component, so the pool
                                    // cannot grow at runtime — say so instead of failing silently.
                                    context.showToast(
                                        context.getString(
                                            R.string.ba_daily_done_tile_pool_full,
                                            BA_DAILY_TILE_SLOTS,
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
