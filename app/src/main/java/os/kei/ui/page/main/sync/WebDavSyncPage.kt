@file:Suppress("FunctionName")

package os.kei.ui.page.main.sync

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.feature.webdav.jianguoyun.JianguoyunPreset
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideDatabaseIcon
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.settings.support.SettingsInfoItem
import os.kei.ui.page.main.settings.support.SettingsNavigationItem
import os.kei.ui.page.main.settings.support.SettingsPickerItem
import os.kei.ui.page.main.settings.support.SettingsToggleItem
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.core.AppControlRow
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.dialog.AppWindowDialogHost
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidInputField
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun WebDavSyncPage(
    onBack: () -> Unit,
    dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>,
    viewModel: WebDavSyncViewModel = viewModel(),
) {
    val state = viewModel.uiState
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    val cardColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f)

    // Populate local item counts on first composition so the per-item rows show "Local: N items"
    // immediately instead of "not measured yet". Only re-runs when the data ports identity changes
    // (which happens on locale change via the Compose factory).
    LaunchedEffect(dataPorts) {
        viewModel.refreshLocalCounts(dataPorts)
    }

    AppPageScaffold(
        title = stringResource(R.string.webdav_sync_title),
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        topBarColor = Color.Transparent,
        titleBackdrop = pageBackdrop,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onBack,
                backdrop = pageBackdrop,
            )
        },
    ) { innerPadding ->
        AppPageLazyColumn(
            innerPadding = innerPadding,
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop),
            sectionSpacing = CardLayoutRhythm.sectionGap,
        ) {
            // ── Provider & connection ────────────────────────────
            item(key = "webdav-connection", contentType = "webdav_card") {
                // Dropdown anchor + expand state lives at the page level so it survives
                // recomposition of the connection card.
                var providerExpanded by remember { mutableStateOf(false) }
                var providerAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
                WebDavConnectionCard(
                    state = state,
                    cardColor = cardColor,
                    providerExpanded = providerExpanded,
                    providerAnchorBounds = providerAnchorBounds,
                    onProviderExpandedChange = { providerExpanded = it },
                    onProviderAnchorBoundsChange = { providerAnchorBounds = it },
                    onSelectProvider = viewModel::selectProvider,
                    onUpdateServerUrl = viewModel::updateServerUrl,
                    onUpdateUsername = viewModel::updateUsername,
                    onUpdateAppPassword = viewModel::updateAppPassword,
                    onUpdateRemoteDir = viewModel::updateRemoteDir,
                    onTogglePasswordVisible = viewModel::togglePasswordVisible,
                    onTestConnection = viewModel::testConnection,
                    onSave = viewModel::saveConfig,
                    onOpenJianguoyunHelp = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, JianguoyunPreset.HELP_URL.toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            }

            // ── Sync items + auto-sync ───────────────────────────
            item(key = "webdav-sync-items", contentType = "webdav_card") {
                WebDavSyncItemsCard(
                    state = state,
                    cardColor = cardColor,
                    onToggleAutoSync = viewModel::setAutoSyncEnabled,
                    onToggleItem = viewModel::toggleItem,
                    onRunItem = { item, kind ->
                        // Per-item Upload is the only destructive single-item action
                        // (overwrites remote). Sync and Download both merge, so they run
                        // directly. Routing Upload through the confirmation flow keeps the
                        // safety story consistent with batch Upload All.
                        if (kind == WebDavBatchKind.Upload) {
                            viewModel.requestItemConfirmation(item, kind)
                        } else {
                            viewModel.runItem(item, kind, dataPorts)
                        }
                    },
                    onSyncAll = { viewModel.syncAll(dataPorts) },
                    onUploadAll = { viewModel.requestBatchConfirmation(WebDavBatchKind.Upload) },
                    onDownloadAll = { viewModel.requestBatchConfirmation(WebDavBatchKind.Download) },
                    onRefreshRemote = { viewModel.refreshRemoteSummary(dataPorts) },
                )
            }

            if (state.isConfigured) {
                item(key = "webdav-clear", contentType = "webdav_card") {
                    WebDavClearCard(
                        cardColor = cardColor,
                        onClear = viewModel::clearConfig,
                    )
                }
            }
        }
    }

    // Destructive batch actions (Upload All / Download All) require explicit confirmation so a
    // fresh device can't blindly wipe one side. Sync is merge-only so it skips this gate.
    val pendingKind = state.pendingBatchConfirmation
    AppWindowDialogHost(
        show = pendingKind != null,
        onDismissRequest = viewModel::dismissBatchConfirmation,
    ) {
        if (pendingKind != null) {
            WebDavBatchConfirmationDialog(
                kind = pendingKind,
                shrinkWarning = pendingKind == WebDavBatchKind.Upload && viewModel.uploadShrinksRemote(),
                onDismiss = viewModel::dismissBatchConfirmation,
                onConfirm = { viewModel.confirmBatchAction(dataPorts) },
            )
        }
    }

    // Per-item Upload uses the same dialog with item-specific copy so the user knows exactly
    // which dataset is about to be overwritten. Download/Sync don't go through this gate.
    val pendingItem = state.pendingItemConfirmation
    AppWindowDialogHost(
        show = pendingItem != null,
        onDismissRequest = viewModel::dismissItemConfirmation,
    ) {
        if (pendingItem != null) {
            val (item, _) = pendingItem
            WebDavItemConfirmationDialog(
                item = item,
                shrinkWarning = viewModel.itemUploadShrinksRemote(item),
                onDismiss = viewModel::dismissItemConfirmation,
                onConfirm = { viewModel.confirmItemAction(dataPorts) },
            )
        }
    }
}

// ── Connection card ────────────────────────────────────────────────────

@Composable
private fun WebDavConnectionCard(
    state: WebDavSyncUiState,
    cardColor: Color,
    providerExpanded: Boolean,
    providerAnchorBounds: IntRect?,
    onProviderExpandedChange: (Boolean) -> Unit,
    onProviderAnchorBoundsChange: (IntRect?) -> Unit,
    onSelectProvider: (WebDavProvider) -> Unit,
    onUpdateServerUrl: (String) -> Unit,
    onUpdateUsername: (String) -> Unit,
    onUpdateAppPassword: (String) -> Unit,
    onUpdateRemoteDir: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onOpenJianguoyunHelp: () -> Unit,
) {
    val providerEntries = remember { WebDavProvider.entries.toList() }
    val providerLabels = providerEntries.map { provider ->
        when (provider) {
            WebDavProvider.Jianguoyun -> stringResource(R.string.webdav_sync_provider_jianguoyun)
            WebDavProvider.Custom -> stringResource(R.string.webdav_sync_provider_custom)
        }
    }
    val selectedProviderIndex = providerEntries.indexOf(state.provider).coerceAtLeast(0)
    val providerSummary = when (state.provider) {
        WebDavProvider.Jianguoyun -> stringResource(R.string.webdav_sync_provider_jianguoyun_desc)
        WebDavProvider.Custom -> stringResource(R.string.webdav_sync_provider_custom_desc)
    }

    SettingsGroupCard(
        header = stringResource(R.string.webdav_sync_title),
        title = stringResource(R.string.webdav_sync_connection),
        sectionIcon = appLucideDatabaseIcon(),
        containerColor = cardColor,
    ) {
        // Provider selector — real dropdown so the available choices are discoverable.
        SettingsPickerItem(
            title = stringResource(R.string.webdav_sync_provider_label),
            summary = providerSummary,
            onClick = { onProviderExpandedChange(true) },
            trailing = {
                AppDropdownSelector(
                    selectedText = providerLabels.getOrElse(selectedProviderIndex) { state.provider.name },
                    options = providerLabels,
                    selectedIndex = selectedProviderIndex,
                    expanded = providerExpanded,
                    anchorBounds = providerAnchorBounds,
                    onExpandedChange = onProviderExpandedChange,
                    onSelectedIndexChange = { index ->
                        providerEntries.getOrNull(index)?.let(onSelectProvider)
                        onProviderExpandedChange(false)
                    },
                    onAnchorBoundsChange = onProviderAnchorBoundsChange,
                    popupMaxWidth = 220.dp,
                    popupMatchAnchorWidth = true,
                )
            },
        )

        // Server URL — locked info row for Jianguoyun, editable field for Custom
        if (state.provider.serverUrlLocked) {
            SettingsInfoItem(
                key = stringResource(R.string.webdav_sync_jianguoyun_server_label),
                value = state.provider.presetServerUrl.orEmpty(),
            )
        } else {
            WebDavFieldLabel(stringResource(R.string.webdav_sync_server_url))
            AppStandaloneLiquidInputField(
                value = state.serverUrl,
                onValueChange = onUpdateServerUrl,
                label = stringResource(R.string.webdav_sync_server_url_placeholder),
                variant = GlassVariant.SheetInput,
                singleLine = true,
            )
            urlErrorText(state.urlError)?.let { text ->
                Text(
                    text = text,
                    color = MiuixTheme.colorScheme.error,
                    fontSize = AppTypographyTokens.Caption.fontSize,
                    lineHeight = AppTypographyTokens.Caption.lineHeight,
                )
            }
        }

        // Username
        WebDavFieldLabel(stringResource(R.string.webdav_sync_username))
        AppStandaloneLiquidInputField(
            value = state.username,
            onValueChange = onUpdateUsername,
            label = stringResource(R.string.webdav_sync_username_placeholder),
            variant = GlassVariant.SheetInput,
            singleLine = true,
        )

        // App password with Show/Hide toggle
        WebDavFieldLabel(stringResource(R.string.webdav_sync_app_password))
        AppStandaloneLiquidInputField(
            value = state.appPassword,
            onValueChange = onUpdateAppPassword,
            label = stringResource(R.string.webdav_sync_password_placeholder),
            variant = GlassVariant.SheetInput,
            singleLine = true,
            visualTransformation = if (state.passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        )
        AppControlRow(
            title = stringResource(
                if (state.passwordVisible) {
                    R.string.webdav_sync_password_hide
                } else {
                    R.string.webdav_sync_password_show
                },
            ),
            summary = if (state.provider == WebDavProvider.Jianguoyun) {
                stringResource(R.string.webdav_sync_jianguoyun_password_hint)
            } else {
                null
            },
            onClick = onTogglePasswordVisible,
            minHeight = 36.dp,
        )

        // Remote directory
        WebDavFieldLabel(stringResource(R.string.webdav_sync_remote_dir))
        AppStandaloneLiquidInputField(
            value = state.remoteDir,
            onValueChange = onUpdateRemoteDir,
            label = WebDavSyncStore.DEFAULT_REMOTE_DIR,
            variant = GlassVariant.SheetInput,
            singleLine = true,
        )

        // Jianguoyun setup hint + help link
        if (state.provider == WebDavProvider.Jianguoyun) {
            Spacer(Modifier.height(CardLayoutRhythm.compactSectionGap))
            Text(
                text = stringResource(R.string.webdav_sync_jianguoyun_hint),
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f),
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
            SettingsNavigationItem(
                title = stringResource(R.string.webdav_sync_jianguoyun_help_label),
                summary = stringResource(R.string.webdav_sync_jianguoyun_help_summary),
                onClick = onOpenJianguoyunHelp,
            )
        }

        // Connection test result
        state.connectionResult?.let { outcome ->
            val text = connectionStatusText(outcome)
            val color = if (outcome.isSuccess) {
                Color(0xFF22C55E)
            } else {
                MiuixTheme.colorScheme.error
            }
            Text(
                text = text,
                color = color,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
        }

        // Test + Save buttons
        AppDualActionRow(
            first = { modifier ->
                AppStandaloneLiquidTextButton(
                    variant = GlassVariant.SheetPrimaryAction,
                    text = if (state.testing) {
                        stringResource(R.string.webdav_sync_testing)
                    } else {
                        stringResource(R.string.webdav_sync_test_connection)
                    },
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    textColor = MiuixTheme.colorScheme.primary,
                    enabled = !state.interactionLocked && state.canConnect,
                    onClick = onTestConnection,
                )
            },
            second = { modifier ->
                AppStandaloneLiquidTextButton(
                    variant = GlassVariant.SheetAction,
                    text = stringResource(R.string.webdav_sync_save),
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    textColor = MiuixTheme.colorScheme.primary,
                    enabled = !state.interactionLocked && state.canConnect,
                    onClick = onSave,
                )
            },
        )
    }
}

// ── Sync items card ────────────────────────────────────────────────────

@Composable
private fun WebDavSyncItemsCard(
    state: WebDavSyncUiState,
    cardColor: Color,
    onToggleAutoSync: (Boolean) -> Unit,
    onToggleItem: (WebDavSyncItem) -> Unit,
    onRunItem: (WebDavSyncItem, WebDavBatchKind) -> Unit,
    onSyncAll: () -> Unit,
    onUploadAll: () -> Unit,
    onDownloadAll: () -> Unit,
    onRefreshRemote: () -> Unit,
) {
    val hasEnabledItems = state.itemStates.values.any { it.enabled }
    val syncReady = state.isConfigured
    val actionEnabled = syncReady && !state.interactionLocked
    val enabledActionTextColor = MiuixTheme.colorScheme.primary
    val disabledActionTextColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.52f)
    SettingsGroupCard(
        header = stringResource(R.string.webdav_sync_title),
        title = stringResource(R.string.webdav_sync_items_title),
        sectionIcon = appLucideDatabaseIcon(),
        containerColor = cardColor,
    ) {
        // Aggregate header — gives an at-a-glance "what's local vs what's on the server" so the
        // user can decide a strategy before drilling into individual items.
        WebDavSyncTotalsHeader(state = state)
        stateSummaryMessage(state)?.let { summary ->
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.84f),
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
        }

        // Auto-sync toggle
        SettingsToggleItem(
            title = stringResource(R.string.webdav_sync_auto_sync_label),
            summary = stringResource(R.string.webdav_sync_auto_sync_summary),
            checked = state.autoSyncEnabled,
            onCheckedChange = onToggleAutoSync,
            enabled = syncReady && !state.interactionLocked,
        )

        // Refresh-remote — read-only probe so other devices can see what's on the server
        // before deciding which sync action to take.
        Spacer(Modifier.height(CardLayoutRhythm.compactSectionGap))
        AppStandaloneLiquidTextButton(
            variant = if (actionEnabled && hasEnabledItems) GlassVariant.SheetAction else GlassVariant.Content,
            text = if (state.refreshingRemote) {
                stringResource(R.string.webdav_sync_refreshing_remote)
            } else {
                stringResource(R.string.webdav_sync_refresh_remote)
            },
            modifier = Modifier.fillMaxWidth(),
            buttonModifier = Modifier.fillMaxWidth(),
            textColor = if (actionEnabled && hasEnabledItems) enabledActionTextColor else disabledActionTextColor,
            enabled = actionEnabled && hasEnabledItems,
            onClick = onRefreshRemote,
        )
        if (state.lastRemoteProbeTimeMs > 0L) {
            Text(
                text = stringResource(
                    R.string.webdav_sync_last_remote_probe,
                    formatTime(state.lastRemoteProbeTimeMs),
                ),
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.74f),
                fontSize = AppTypographyTokens.Caption.fontSize,
                lineHeight = AppTypographyTokens.Caption.lineHeight,
            )
        } else {
            Text(
                text = stringResource(R.string.webdav_sync_remote_never_probed),
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.74f),
                fontSize = AppTypographyTokens.Caption.fontSize,
                lineHeight = AppTypographyTokens.Caption.lineHeight,
            )
        }

        // Sync All / Upload All / Download All
        Spacer(Modifier.height(CardLayoutRhythm.compactSectionGap))
        AppDualActionRow(
            first = { modifier ->
                AppStandaloneLiquidTextButton(
                    variant = if (actionEnabled && hasEnabledItems) GlassVariant.SheetPrimaryAction else GlassVariant.Content,
                    text = if (state.runningKind == WebDavBatchKind.Sync) {
                        stringResource(R.string.webdav_sync_syncing)
                    } else {
                        stringResource(R.string.webdav_sync_sync_all)
                    },
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    textColor = if (actionEnabled && hasEnabledItems) enabledActionTextColor else disabledActionTextColor,
                    enabled = actionEnabled && hasEnabledItems,
                    onClick = onSyncAll,
                )
            },
            second = { modifier ->
                AppStandaloneLiquidTextButton(
                    variant = if (actionEnabled && hasEnabledItems) GlassVariant.SheetAction else GlassVariant.Content,
                    text = if (state.runningKind == WebDavBatchKind.Upload) {
                        stringResource(R.string.webdav_sync_uploading)
                    } else {
                        stringResource(R.string.webdav_sync_upload_all)
                    },
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    textColor = if (actionEnabled && hasEnabledItems) enabledActionTextColor else disabledActionTextColor,
                    enabled = actionEnabled && hasEnabledItems,
                    onClick = onUploadAll,
                )
            },
        )
        Spacer(Modifier.height(CardLayoutRhythm.compactSectionGap))
        AppStandaloneLiquidTextButton(
            variant = if (actionEnabled && hasEnabledItems) GlassVariant.SheetAction else GlassVariant.Content,
            text = if (state.runningKind == WebDavBatchKind.Download) {
                stringResource(R.string.webdav_sync_downloading)
            } else {
                stringResource(R.string.webdav_sync_download_all)
            },
            modifier = Modifier.fillMaxWidth(),
            buttonModifier = Modifier.fillMaxWidth(),
            textColor = if (actionEnabled && hasEnabledItems) enabledActionTextColor else disabledActionTextColor,
            enabled = actionEnabled && hasEnabledItems,
            onClick = onDownloadAll,
        )

        // Per-item rows — enable switch + per-item Sync / Upload / Download actions.
        // Each row is its own column block so the action buttons can sit on a dedicated line
        // beneath the title without competing for trailing space.
        WebDavSyncItem.entries.forEach { item ->
            Spacer(Modifier.height(CardLayoutRhythm.compactSectionGap))
            WebDavSyncItemRow(
                item = item,
                state = state,
                onToggleItem = onToggleItem,
                onRunItem = onRunItem,
            )
        }

        // Last full-sync info
        if (state.lastFullSyncTimeMs > 0) {
            Spacer(Modifier.height(CardLayoutRhythm.compactSectionGap))
            SettingsInfoItem(
                key = stringResource(R.string.webdav_sync_last_sync_label),
                value = formatTime(state.lastFullSyncTimeMs),
            )
        }
    }
}

// ── Clear-config card ──────────────────────────────────────────────────

@Composable
private fun WebDavClearCard(
    cardColor: Color,
    onClear: () -> Unit,
) {
    SettingsGroupCard(
        header = stringResource(R.string.webdav_sync_title),
        title = stringResource(R.string.webdav_sync_clear_label),
        sectionIcon = appLucideDatabaseIcon(),
        containerColor = cardColor,
    ) {
        Text(
            text = stringResource(R.string.webdav_sync_clear_summary),
            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f),
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
        )
        AppStandaloneLiquidTextButton(
            variant = GlassVariant.SheetDangerAction,
            text = stringResource(R.string.webdav_sync_clear_button),
            modifier = Modifier.fillMaxWidth(),
            buttonModifier = Modifier.fillMaxWidth(),
            textColor = MiuixTheme.colorScheme.error,
            onClick = onClear,
        )
    }
}

// ── Confirmation dialogs ───────────────────────────────────────────────

@Composable
private fun WebDavBatchConfirmationDialog(
    kind: WebDavBatchKind,
    shrinkWarning: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val title = stringResource(
        when (kind) {
            WebDavBatchKind.Upload -> R.string.webdav_sync_confirm_upload_title
            WebDavBatchKind.Download -> R.string.webdav_sync_confirm_download_title
            WebDavBatchKind.Sync -> R.string.webdav_sync_confirm_upload_title // unreachable; sync is gated out
        },
    )
    val summary = stringResource(
        when (kind) {
            WebDavBatchKind.Upload -> R.string.webdav_sync_confirm_upload_summary
            WebDavBatchKind.Download -> R.string.webdav_sync_confirm_download_summary
            WebDavBatchKind.Sync -> R.string.webdav_sync_confirm_upload_summary // unreachable
        },
    )
    WebDavConfirmationDialogContent(
        title = title,
        summary = summary,
        warning = if (shrinkWarning) stringResource(R.string.webdav_sync_confirm_upload_shrink_warning) else null,
        confirmIsDestructive = kind == WebDavBatchKind.Upload,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun WebDavItemConfirmationDialog(
    item: WebDavSyncItem,
    shrinkWarning: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val itemLabel = stringResource(item.labelRes)
    WebDavConfirmationDialogContent(
        title = stringResource(R.string.webdav_sync_confirm_item_upload_title, itemLabel),
        summary = stringResource(R.string.webdav_sync_confirm_item_upload_summary, itemLabel),
        warning = if (shrinkWarning) stringResource(R.string.webdav_sync_confirm_upload_shrink_warning) else null,
        confirmIsDestructive = true,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun WebDavConfirmationDialogContent(
    title: String,
    summary: String,
    warning: String?,
    confirmIsDestructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.compactSectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.CompactTitle.fontSize,
            lineHeight = AppTypographyTokens.CompactTitle.lineHeight,
            fontWeight = AppTypographyTokens.CompactTitle.fontWeight,
        )
        Text(
            text = summary,
            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f),
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
        )
        warning?.let {
            Text(
                text = it,
                color = MiuixTheme.colorScheme.error,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
        }
        Spacer(Modifier.height(CardLayoutRhythm.compactSectionGap))
        AppDualActionRow(
            first = { modifier ->
                AppStandaloneLiquidTextButton(
                    variant = GlassVariant.SheetAction,
                    text = stringResource(R.string.webdav_sync_confirm_cancel),
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    textColor = MiuixTheme.colorScheme.onBackgroundVariant,
                    onClick = onDismiss,
                )
            },
            second = { modifier ->
                AppStandaloneLiquidTextButton(
                    variant = if (confirmIsDestructive) {
                        GlassVariant.SheetDangerAction
                    } else {
                        GlassVariant.SheetPrimaryAction
                    },
                    text = stringResource(R.string.webdav_sync_confirm_continue),
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    textColor = if (confirmIsDestructive) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                    onClick = onConfirm,
                )
            },
        )
    }
}

// ── Per-item row ───────────────────────────────────────────────────────

@Composable
private fun WebDavSyncTotalsHeader(state: WebDavSyncUiState) {
    // Aggregate by summing the per-item caches captured in the UI state. We only count enabled
    // items so the header reflects what would actually move during a Sync All / Upload All.
    var localItems = 0
    var enabledTypes = 0
    var remoteItems = 0
    var remoteBytes = 0L
    var anyRemoteKnown = false
    state.itemStates.forEach { (_, itemState) ->
        if (!itemState.enabled) return@forEach
        enabledTypes += 1
        if (itemState.localCount >= 0) localItems += itemState.localCount
        val remote = itemState.remoteSummary ?: return@forEach
        if (remote.empty) {
            anyRemoteKnown = true
            return@forEach
        }
        if (remote.itemCount >= 0) {
            anyRemoteKnown = true
            remoteItems += remote.itemCount
        }
        if (remote.byteSize >= 0) remoteBytes += remote.byteSize
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap),
    ) {
        Text(
            text = stringResource(R.string.webdav_sync_totals_header),
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.CompactTitle.fontSize,
            lineHeight = AppTypographyTokens.CompactTitle.lineHeight,
            fontWeight = AppTypographyTokens.CompactTitle.fontWeight,
        )
        Text(
            text = stringResource(R.string.webdav_sync_totals_local_format, localItems, enabledTypes),
            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f),
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
        )
        Text(
            text = stringResource(R.string.webdav_sync_totals_contract_note),
            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.74f),
            fontSize = AppTypographyTokens.Caption.fontSize,
            lineHeight = AppTypographyTokens.Caption.lineHeight,
        )
        if (anyRemoteKnown) {
            Text(
                text = stringResource(
                    R.string.webdav_sync_totals_remote_format,
                    remoteItems,
                    formatBytes(remoteBytes),
                ),
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f),
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
        } else {
            Text(
                text = stringResource(R.string.webdav_sync_totals_remote_unknown),
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.74f),
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
        }
    }
}

@Composable
private fun WebDavSyncItemRow(
    item: WebDavSyncItem,
    state: WebDavSyncUiState,
    onToggleItem: (WebDavSyncItem) -> Unit,
    onRunItem: (WebDavSyncItem, WebDavBatchKind) -> Unit,
) {
    val itemState = state.itemStates[item]
    val enabled = itemState?.enabled ?: true
    val syncReady = state.isConfigured
    val actionEnabled = enabled && syncReady && !state.interactionLocked
    val running = itemState?.running == true
    val outcome = itemState?.lastOutcome
    val lastSync = itemState?.lastSyncTimeMs?.takeIf { it > 0 }
    val statusText = when {
        running -> stringResource(R.string.webdav_sync_item_running)
        outcome != null -> itemStatusText(outcome)
        lastSync != null -> stringResource(R.string.webdav_sync_last_sync, formatTime(lastSync))
        else -> stringResource(item.descriptionRes)
    }
    val statusColor = when {
        running -> MiuixTheme.colorScheme.primary
        outcome?.isSuccess == true -> Color(0xFF22C55E)
        outcome != null -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f)
    }
    val localCount = itemState?.localCount ?: -1
    val localLine = if (localCount >= 0) {
        stringResource(R.string.webdav_sync_local_summary_format, localCount)
    } else {
        stringResource(R.string.webdav_sync_local_summary_unknown)
    }
    val remoteLine = remoteSummaryLine(itemState?.remoteSummary)
    val remoteProbeError = itemState?.remoteProbeError
    // Disabled rows fade everything but the switch so the user sees they're skipped without
    // losing the data they already have.
    val rowAlpha = if (enabled) 1f else 0.55f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha),
        verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap),
    ) {
        AppControlRow(
            title = stringResource(item.labelRes),
            summary = itemContractSummary(item),
            trailing = {
                AppSwitch(
                    checked = enabled,
                    onCheckedChange = { onToggleItem(item) },
                    enabled = !state.interactionLocked,
                )
            },
        )
        Text(
            text = statusText,
            color = statusColor,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
        )
        Text(
            text = localLine,
            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.84f),
            fontSize = AppTypographyTokens.Caption.fontSize,
            lineHeight = AppTypographyTokens.Caption.lineHeight,
        )
        Text(
            text = remoteLine,
            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.84f),
            fontSize = AppTypographyTokens.Caption.fontSize,
            lineHeight = AppTypographyTokens.Caption.lineHeight,
        )
        remoteProbeError?.let { error ->
            Text(
                text = remoteProbeErrorLine(error),
                color = MiuixTheme.colorScheme.error,
                fontSize = AppTypographyTokens.Caption.fontSize,
                lineHeight = AppTypographyTokens.Caption.lineHeight,
            )
        }
        outcome?.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            Text(
                text = detail,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.74f),
                fontSize = AppTypographyTokens.Caption.fontSize,
                lineHeight = AppTypographyTokens.Caption.lineHeight,
            )
        }
        // Three-up action row: Sync (merge, primary), Upload (local→remote, danger), Download
        // (remote→local, secondary). All three respect the global busy flag and the per-item
        // enable switch.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.compactSectionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppStandaloneLiquidTextButton(
                variant = if (actionEnabled) GlassVariant.SheetPrimaryAction else GlassVariant.Content,
                text = stringResource(R.string.webdav_sync_item_sync_action),
                modifier = Modifier.weight(1f),
                buttonModifier = Modifier.fillMaxWidth(),
                textColor = if (actionEnabled) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.52f)
                },
                enabled = actionEnabled,
                onClick = { onRunItem(item, WebDavBatchKind.Sync) },
            )
            AppStandaloneLiquidTextButton(
                variant = if (actionEnabled) GlassVariant.SheetDangerAction else GlassVariant.Content,
                text = stringResource(R.string.webdav_sync_item_upload_action),
                modifier = Modifier.weight(1f),
                buttonModifier = Modifier.fillMaxWidth(),
                textColor = if (actionEnabled) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.52f)
                },
                enabled = actionEnabled,
                onClick = { onRunItem(item, WebDavBatchKind.Upload) },
            )
            AppStandaloneLiquidTextButton(
                variant = if (actionEnabled) GlassVariant.SheetAction else GlassVariant.Content,
                text = stringResource(R.string.webdav_sync_item_download_action),
                modifier = Modifier.weight(1f),
                buttonModifier = Modifier.fillMaxWidth(),
                textColor = if (actionEnabled) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.52f)
                },
                enabled = actionEnabled,
                onClick = { onRunItem(item, WebDavBatchKind.Download) },
            )
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────

@Composable
private fun WebDavFieldLabel(text: String) {
    Spacer(Modifier.height(CardLayoutRhythm.denseSectionGap))
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.74f),
        fontSize = AppTypographyTokens.Caption.fontSize,
        lineHeight = AppTypographyTokens.Caption.lineHeight,
    )
}

@Composable
private fun itemContractSummary(item: WebDavSyncItem): String = when (item) {
    WebDavSyncItem.GitHubTracked -> stringResource(R.string.webdav_sync_item_github_tracked_contract)
    WebDavSyncItem.BaCatalogFavorites -> stringResource(item.descriptionRes)
    WebDavSyncItem.BaBgmFavorites -> stringResource(item.descriptionRes)
    WebDavSyncItem.OsActivityCards -> stringResource(R.string.webdav_sync_item_os_activity_contract)
    WebDavSyncItem.OsShellCards -> stringResource(R.string.webdav_sync_item_os_shell_contract)
}

@Composable
private fun stateSummaryMessage(state: WebDavSyncUiState): String? {
    if (!state.isConfigured || state.missingConfig) {
        return stringResource(R.string.webdav_sync_missing_config_summary)
    }
    val enabledCount = state.itemStates.values.count { it.enabled }
    if (enabledCount == 0) return stringResource(R.string.webdav_sync_no_enabled_items_summary)
    return stringResource(
        R.string.webdav_sync_enabled_items_summary,
        enabledCount,
        state.itemStates.size,
    )
}

@Composable
private fun connectionStatusText(outcome: WebDavConnectionOutcome): String {
    val base = when (outcome.status) {
        WebDavConnectionStatus.Success -> stringResource(R.string.webdav_sync_test_success)
        WebDavConnectionStatus.SuccessDirCreated -> stringResource(R.string.webdav_sync_test_success_dir_created)
        WebDavConnectionStatus.AuthFailed -> stringResource(R.string.webdav_sync_status_auth_failed)
        WebDavConnectionStatus.PermissionDenied -> stringResource(R.string.webdav_sync_status_permission_denied)
        WebDavConnectionStatus.NetworkError -> stringResource(R.string.webdav_sync_status_network_error)
        WebDavConnectionStatus.InvalidUrl -> stringResource(R.string.webdav_sync_status_invalid_url)
        WebDavConnectionStatus.Unknown -> stringResource(R.string.webdav_sync_status_unknown)
    }
    val detail = outcome.detail?.takeIf { it.isNotBlank() }
    return if (detail != null && !outcome.isSuccess) "$base · $detail" else base
}

@Composable
private fun itemStatusText(outcome: WebDavItemOutcome): String = when (outcome.status) {
    WebDavItemStatus.Uploaded -> stringResource(R.string.webdav_sync_status_uploaded)
    WebDavItemStatus.Downloaded -> stringResource(R.string.webdav_sync_status_downloaded)
    WebDavItemStatus.Merged -> stringResource(R.string.webdav_sync_status_merged)
    WebDavItemStatus.UpToDate -> stringResource(R.string.webdav_sync_status_up_to_date)
    WebDavItemStatus.RemoteEmpty -> stringResource(R.string.webdav_sync_status_remote_empty)
    WebDavItemStatus.AuthFailed -> stringResource(R.string.webdav_sync_status_auth_failed)
    WebDavItemStatus.PermissionDenied -> stringResource(R.string.webdav_sync_status_permission_denied)
    WebDavItemStatus.NetworkError -> stringResource(R.string.webdav_sync_status_network_error)
    WebDavItemStatus.ConflictUnresolved -> stringResource(R.string.webdav_sync_status_conflict)
    WebDavItemStatus.Error -> stringResource(R.string.webdav_sync_status_error)
}

@Composable
private fun remoteProbeErrorLine(outcome: WebDavItemOutcome): String {
    val base = itemStatusText(outcome)
    val detail = outcome.detail?.takeIf { it.isNotBlank() } ?: return base
    return "$base · $detail"
}

@Composable
private fun urlErrorText(error: WebDavUrlError?): String? = when (error) {
    null -> null
    WebDavUrlError.Empty -> stringResource(R.string.webdav_sync_url_error_empty)
    WebDavUrlError.Scheme -> stringResource(R.string.webdav_sync_url_error_scheme)
}

private fun formatTime(timeMs: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timeMs))
}

@Composable
private fun remoteSummaryLine(summary: WebDavRemoteSummary?): String {
    if (summary == null) return stringResource(R.string.webdav_sync_remote_summary_unknown)
    if (summary.empty) return stringResource(R.string.webdav_sync_remote_summary_empty)
    return stringResource(
        R.string.webdav_sync_remote_summary_format,
        summary.itemCount.coerceAtLeast(0),
        formatBytes(summary.byteSize),
        formatTime(summary.probedAtMs),
    )
}

@Composable
private fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe >= 1024L * 1024L -> stringResource(R.string.webdav_sync_size_mb, safe / 1024.0 / 1024.0)
        safe >= 1024L -> stringResource(R.string.webdav_sync_size_kb, safe / 1024.0)
        else -> stringResource(R.string.webdav_sync_size_bytes, safe)
    }
}
