@file:Suppress("FunctionName")

package os.kei.ui.page.main.sync

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import os.kei.ui.page.main.settings.support.SettingsToggleItem
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.core.AppControlRow
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidInputField
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
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

    AppPageScaffold(
        title = stringResource(R.string.webdav_sync_title_short),
        largeTitle = stringResource(R.string.webdav_sync_title),
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
                WebDavConnectionCard(
                    state = state,
                    cardColor = cardColor,
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
            if (state.isConfigured) {
                item(key = "webdav-sync-items", contentType = "webdav_card") {
                    WebDavSyncItemsCard(
                        state = state,
                        cardColor = cardColor,
                        onToggleAutoSync = viewModel::setAutoSyncEnabled,
                        onToggleItem = viewModel::toggleItem,
                        onRunItem = { item, kind -> viewModel.runItem(item, kind, dataPorts) },
                        onSyncAll = { viewModel.syncAll(dataPorts) },
                        onUploadAll = { viewModel.uploadAll(dataPorts) },
                        onDownloadAll = { viewModel.downloadAll(dataPorts) },
                    )
                }

                item(key = "webdav-clear", contentType = "webdav_card") {
                    WebDavClearCard(
                        cardColor = cardColor,
                        onClear = viewModel::clearConfig,
                    )
                }
            }
        }
    }
}

// ── Connection card ────────────────────────────────────────────────────

@Composable
private fun WebDavConnectionCard(
    state: WebDavSyncUiState,
    cardColor: Color,
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
    val providerSummary = when (state.provider) {
        WebDavProvider.Jianguoyun -> stringResource(R.string.webdav_sync_provider_jianguoyun_desc)
        WebDavProvider.Custom -> stringResource(R.string.webdav_sync_provider_custom_desc)
    }
    val providerName = when (state.provider) {
        WebDavProvider.Jianguoyun -> stringResource(R.string.webdav_sync_provider_jianguoyun)
        WebDavProvider.Custom -> stringResource(R.string.webdav_sync_provider_custom)
    }

    SettingsGroupCard(
        header = stringResource(R.string.webdav_sync_title),
        title = stringResource(R.string.webdav_sync_connection),
        sectionIcon = appLucideDatabaseIcon(),
        containerColor = cardColor,
    ) {
        // Provider selector — tap to cycle through providers
        SettingsNavigationItem(
            title = stringResource(R.string.webdav_sync_provider_label),
            summary = "$providerName · $providerSummary",
            onClick = {
                val entries = WebDavProvider.entries
                val next = entries[(entries.indexOf(state.provider) + 1) % entries.size]
                onSelectProvider(next)
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
                    enabled = !state.testing && state.canConnect,
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
                    enabled = state.canConnect,
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
) {
    SettingsGroupCard(
        header = stringResource(R.string.webdav_sync_title),
        title = stringResource(R.string.webdav_sync_items_title),
        sectionIcon = appLucideDatabaseIcon(),
        containerColor = cardColor,
    ) {
        // Auto-sync toggle
        SettingsToggleItem(
            title = stringResource(R.string.webdav_sync_auto_sync_label),
            summary = stringResource(R.string.webdav_sync_auto_sync_summary),
            checked = state.autoSyncEnabled,
            onCheckedChange = onToggleAutoSync,
        )

        // Sync All / Upload All / Download All
        Spacer(Modifier.height(CardLayoutRhythm.compactSectionGap))
        AppDualActionRow(
            first = { modifier ->
                AppStandaloneLiquidTextButton(
                    variant = GlassVariant.SheetPrimaryAction,
                    text = if (state.runningKind == WebDavBatchKind.Sync) {
                        stringResource(R.string.webdav_sync_syncing)
                    } else {
                        stringResource(R.string.webdav_sync_sync_all)
                    },
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    textColor = MiuixTheme.colorScheme.primary,
                    enabled = !state.busy,
                    onClick = onSyncAll,
                )
            },
            second = { modifier ->
                AppStandaloneLiquidTextButton(
                    variant = GlassVariant.SheetAction,
                    text = if (state.runningKind == WebDavBatchKind.Upload) {
                        stringResource(R.string.webdav_sync_uploading)
                    } else {
                        stringResource(R.string.webdav_sync_upload_all)
                    },
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    textColor = MiuixTheme.colorScheme.primary,
                    enabled = !state.busy,
                    onClick = onUploadAll,
                )
            },
        )
        Spacer(Modifier.height(CardLayoutRhythm.compactSectionGap))
        AppStandaloneLiquidTextButton(
            variant = GlassVariant.SheetAction,
            text = if (state.runningKind == WebDavBatchKind.Download) {
                stringResource(R.string.webdav_sync_downloading)
            } else {
                stringResource(R.string.webdav_sync_download_all)
            },
            modifier = Modifier.fillMaxWidth(),
            buttonModifier = Modifier.fillMaxWidth(),
            textColor = MiuixTheme.colorScheme.primary,
            enabled = !state.busy,
            onClick = onDownloadAll,
        )

        // Per-item rows — enable toggle + per-item Sync action
        WebDavSyncItem.entries.forEach { item ->
            val itemState = state.itemStates[item]
            val enabled = itemState?.enabled ?: true
            val running = itemState?.running == true
            val lastSync = itemState?.lastSyncTimeMs?.takeIf { it > 0 }
            val outcome = itemState?.lastOutcome
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

            Spacer(Modifier.height(CardLayoutRhythm.denseSectionGap))
            AppControlRow(
                title = stringResource(item.labelRes),
                summary = null,
                trailing = {
                    AppStandaloneLiquidTextButton(
                        variant = GlassVariant.Compact,
                        text = stringResource(R.string.webdav_sync_item_sync_action),
                        textColor = MiuixTheme.colorScheme.primary,
                        enabled = enabled && !state.busy,
                        onClick = { onRunItem(item, WebDavBatchKind.Sync) },
                    )
                    AppStandaloneLiquidTextButton(
                        variant = GlassVariant.Compact,
                        text = stringResource(
                            if (enabled) R.string.webdav_sync_item_disable else R.string.webdav_sync_item_enable,
                        ),
                        textColor = if (enabled) {
                            MiuixTheme.colorScheme.onBackgroundVariant
                        } else {
                            MiuixTheme.colorScheme.primary
                        },
                        enabled = !state.busy,
                        onClick = { onToggleItem(item) },
                    )
                },
            )
            Text(
                text = statusText,
                color = statusColor,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
            outcome?.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    text = detail,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.74f),
                    fontSize = AppTypographyTokens.Caption.fontSize,
                    lineHeight = AppTypographyTokens.Caption.lineHeight,
                )
            }
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
private fun urlErrorText(error: WebDavUrlError?): String? = when (error) {
    null -> null
    WebDavUrlError.Empty -> stringResource(R.string.webdav_sync_url_error_empty)
    WebDavUrlError.Scheme -> stringResource(R.string.webdav_sync_url_error_scheme)
}

private fun formatTime(timeMs: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timeMs))
}
