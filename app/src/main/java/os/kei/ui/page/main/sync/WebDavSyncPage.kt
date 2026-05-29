@file:Suppress("FunctionName")

package os.kei.ui.page.main.sync

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideDatabaseIcon
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.settings.support.SettingsInfoItem
import os.kei.ui.page.main.settings.support.SettingsNavigationItem
import os.kei.ui.page.main.settings.support.SettingsToggleItem
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun WebDavSyncPage(
    onBack: () -> Unit,
    dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>,
) {
    val scope = rememberCoroutineScope()
    val viewModel = WebDavSyncViewModel(scope)
    val state = viewModel.uiState
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    val cardColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f)
    val subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f)

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
                SettingsGroupCard(
                    header = stringResource(R.string.webdav_sync_title),
                    title = stringResource(R.string.webdav_sync_connection),
                    sectionIcon = appLucideDatabaseIcon(),
                    containerColor = cardColor,
                ) {
                    // Provider selector
                    SettingsNavigationItem(
                        title = stringResource(R.string.webdav_sync_provider_label),
                        summary = state.selectedProvider.displayName,
                        onClick = {
                            val next = WebDavProvider.entries.let { entries ->
                                val idx = (entries.indexOf(state.selectedProvider) + 1) % entries.size
                                entries[idx]
                            }
                            viewModel.selectProvider(next)
                        },
                    )

                    // Server URL (custom only)
                    if (state.selectedProvider == WebDavProvider.Custom) {
                        WebDavTextField(
                            label = stringResource(R.string.webdav_sync_server_url),
                            value = state.serverUrl,
                            onValueChange = viewModel::updateServerUrl,
                            placeholder = "https://dav.example.com/",
                        )
                    }

                    // Username
                    WebDavTextField(
                        label = stringResource(R.string.webdav_sync_username),
                        value = state.username,
                        onValueChange = viewModel::updateUsername,
                        placeholder = stringResource(R.string.webdav_sync_username_placeholder),
                    )

                    // App password
                    WebDavTextField(
                        label = stringResource(R.string.webdav_sync_app_password),
                        value = state.appPassword,
                        onValueChange = viewModel::updateAppPassword,
                        placeholder = stringResource(R.string.webdav_sync_password_placeholder),
                        isPassword = true,
                    )

                    // Remote directory
                    WebDavTextField(
                        label = stringResource(R.string.webdav_sync_remote_dir),
                        value = state.remoteDir,
                        onValueChange = viewModel::updateRemoteDir,
                        placeholder = "KeiOS/",
                    )

                    // Jianguoyun hint
                    if (state.selectedProvider == WebDavProvider.Jianguoyun) {
                        Text(
                            text = stringResource(R.string.webdav_sync_jianguoyun_hint),
                            color = subtitleColor,
                            fontSize = AppTypographyTokens.Supporting.fontSize,
                            lineHeight = AppTypographyTokens.Supporting.lineHeight,
                        )
                    }

                    // Test result
                    val testResult = state.testResult
                    if (testResult != null) {
                        val color = when (testResult) {
                            is WebDavTestUiResult.Success -> Color(0xFF22C55E)
                            is WebDavTestUiResult.Failure -> MiuixTheme.colorScheme.error
                        }
                        val text = when (testResult) {
                            is WebDavTestUiResult.Success -> stringResource(R.string.webdav_sync_test_success)
                            is WebDavTestUiResult.Failure -> stringResource(R.string.webdav_sync_test_failed, testResult.message)
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
                                text =
                                    if (state.testing) {
                                        stringResource(R.string.webdav_sync_testing)
                                    } else {
                                        stringResource(R.string.webdav_sync_test_connection)
                                    },
                                modifier = modifier,
                                buttonModifier = Modifier.fillMaxWidth(),
                                textColor = MiuixTheme.colorScheme.primary,
                                enabled = !state.testing && state.username.isNotBlank() && state.appPassword.isNotBlank(),
                                onClick = { viewModel.testConnection() },
                            )
                        },
                        second = { modifier ->
                            AppStandaloneLiquidTextButton(
                                variant = GlassVariant.SheetAction,
                                text = stringResource(R.string.webdav_sync_save),
                                modifier = modifier,
                                buttonModifier = Modifier.fillMaxWidth(),
                                textColor = MiuixTheme.colorScheme.primary,
                                enabled = state.username.isNotBlank() && state.appPassword.isNotBlank(),
                                onClick = { viewModel.saveConfig() },
                            )
                        },
                    )
                }
            }

            // ── Sync items ───────────────────────────────────────
            if (state.isConfigured) {
                item(key = "webdav-sync-items", contentType = "webdav_card") {
                    SettingsGroupCard(
                        header = stringResource(R.string.webdav_sync_title),
                        title = stringResource(R.string.webdav_sync_items_title),
                        sectionIcon = appLucideDatabaseIcon(),
                        containerColor = cardColor,
                    ) {
                        WebDavSyncItem.entries.forEach { item ->
                            val itemState = state.itemStates[item]
                            val lastSync = itemState?.lastSyncTimeMs?.takeIf { it > 0 }
                            SettingsToggleItem(
                                title = stringResource(item.labelRes),
                                summary =
                                    if (lastSync != null) {
                                        stringResource(R.string.webdav_sync_last_sync, formatTime(lastSync))
                                    } else {
                                        stringResource(item.descriptionRes)
                                    },
                                checked = itemState?.enabled ?: true,
                                onCheckedChange = { viewModel.toggleItem(item) },
                            )
                        }

                        // Sync error
                        val syncError = state.lastSyncError
                        if (syncError != null) {
                            Text(
                                text = syncError,
                                color = MiuixTheme.colorScheme.error,
                                fontSize = AppTypographyTokens.Supporting.fontSize,
                                lineHeight = AppTypographyTokens.Supporting.lineHeight,
                            )
                        }

                        // Upload + Download
                        AppDualActionRow(
                            first = { modifier ->
                                AppStandaloneLiquidTextButton(
                                    variant = GlassVariant.SheetPrimaryAction,
                                    text =
                                        if (state.syncing && state.syncProgress?.contains("Uploading") == true) {
                                            state.syncProgress.orEmpty()
                                        } else {
                                            stringResource(R.string.webdav_sync_upload)
                                        },
                                    modifier = modifier,
                                    buttonModifier = Modifier.fillMaxWidth(),
                                    textColor = MiuixTheme.colorScheme.primary,
                                    enabled = !state.syncing,
                                    onClick = { viewModel.uploadAll(dataPorts) },
                                )
                            },
                            second = { modifier ->
                                AppStandaloneLiquidTextButton(
                                    variant = GlassVariant.SheetAction,
                                    text =
                                        if (state.syncing && state.syncProgress?.contains("Downloading") == true) {
                                            state.syncProgress.orEmpty()
                                        } else {
                                            stringResource(R.string.webdav_sync_download)
                                        },
                                    modifier = modifier,
                                    buttonModifier = Modifier.fillMaxWidth(),
                                    textColor = MiuixTheme.colorScheme.primary,
                                    enabled = !state.syncing,
                                    onClick = { viewModel.downloadAll(dataPorts) },
                                )
                            },
                        )

                        // Last full sync info
                        val lastFullSync = WebDavSyncStore.getLastFullSyncTime()
                        if (lastFullSync > 0) {
                            SettingsInfoItem(
                                key = stringResource(R.string.webdav_sync_last_sync_label),
                                value = formatTime(lastFullSync),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebDavTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
) {
    Text(
        text = label,
        fontSize = AppTypographyTokens.Supporting.fontSize,
        lineHeight = AppTypographyTokens.Supporting.lineHeight,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
    )
    Spacer(Modifier.height(4.dp))
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = placeholder,
        useLabelAsPlaceholder = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
        ),
        singleLine = true,
    )
    Spacer(Modifier.height(CardLayoutRhythm.compactSectionGap))
}

private fun formatTime(timeMs: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timeMs))
}
