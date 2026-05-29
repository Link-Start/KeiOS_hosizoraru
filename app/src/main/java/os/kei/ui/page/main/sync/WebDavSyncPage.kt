@file:Suppress("FunctionName")

package os.kei.ui.page.main.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.settings.support.SettingsInfoItem
import os.kei.ui.page.main.settings.support.SettingsNavigationItem
import os.kei.ui.page.main.settings.support.SettingsToggleItem
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.os.appLucideBackIcon
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
            // ── Provider selector ────────────────────────────────
            item(key = "webdav-provider", contentType = "webdav_card") {
                SettingsGroupCard(
                    header = stringResource(R.string.webdav_sync_title),
                    title = stringResource(R.string.webdav_sync_provider_label),
                    containerColor = cardColor,
                ) {
                    WebDavProvider.entries.forEach { provider ->
                        SettingsNavigationItem(
                            title = provider.displayName,
                            summary = if (provider == state.selectedProvider) "✓" else "",
                            onClick = { viewModel.selectProvider(provider) },
                        )
                    }
                }
            }

            // ── Connection config ────────────────────────────────
            item(key = "webdav-connection", contentType = "webdav_card") {
                SettingsGroupCard(
                    header = stringResource(R.string.webdav_sync_title),
                    title = stringResource(R.string.webdav_sync_connection),
                    containerColor = cardColor,
                ) {
                    if (state.selectedProvider == WebDavProvider.Jianguoyun) {
                        Text(
                            text = stringResource(R.string.webdav_sync_jianguoyun_hint),
                            color = subtitleColor,
                            fontSize = AppTypographyTokens.Supporting.fontSize,
                            lineHeight = AppTypographyTokens.Supporting.lineHeight,
                        )
                        Spacer(Modifier.height(CardLayoutRhythm.compactSectionGap))
                    }

                    if (state.selectedProvider == WebDavProvider.Custom) {
                        WebDavTextField(
                            label = stringResource(R.string.webdav_sync_server_url),
                            value = state.serverUrl,
                            onValueChange = viewModel::updateServerUrl,
                            placeholder = "https://dav.example.com/",
                        )
                    }

                    WebDavTextField(
                        label = stringResource(R.string.webdav_sync_username),
                        value = state.username,
                        onValueChange = viewModel::updateUsername,
                        placeholder = stringResource(R.string.webdav_sync_username_placeholder),
                    )

                    WebDavTextField(
                        label = stringResource(R.string.webdav_sync_app_password),
                        value = state.appPassword,
                        onValueChange = viewModel::updateAppPassword,
                        placeholder = stringResource(R.string.webdav_sync_password_placeholder),
                        isPassword = true,
                    )

                    WebDavTextField(
                        label = stringResource(R.string.webdav_sync_remote_dir),
                        value = state.remoteDir,
                        onValueChange = viewModel::updateRemoteDir,
                        placeholder = "KeiOS/",
                    )

                    Spacer(Modifier.height(CardLayoutRhythm.compactSectionGap))

                    AppStandaloneLiquidTextButton(
                        variant = GlassVariant.SheetPrimaryAction,
                        text = if (state.testing) stringResource(R.string.webdav_sync_testing)
                        else stringResource(R.string.webdav_sync_test_connection),
                        modifier = Modifier.fillMaxWidth(),
                        buttonModifier = Modifier.fillMaxWidth(),
                        textColor = MiuixTheme.colorScheme.primary,
                        enabled = !state.testing && state.username.isNotBlank() && state.appPassword.isNotBlank(),
                        onClick = { viewModel.testConnection() },
                    )

                    AppStandaloneLiquidTextButton(
                        variant = GlassVariant.SheetAction,
                        text = stringResource(R.string.webdav_sync_save),
                        modifier = Modifier.fillMaxWidth(),
                        buttonModifier = Modifier.fillMaxWidth(),
                        textColor = MiuixTheme.colorScheme.primary,
                        enabled = state.username.isNotBlank() && state.appPassword.isNotBlank(),
                        onClick = { viewModel.saveConfig() },
                    )

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
                }
            }

            // ── Sync items ───────────────────────────────────────
            if (state.isConfigured) {
                item(key = "webdav-sync-items", contentType = "webdav_card") {
                    SettingsGroupCard(
                        header = stringResource(R.string.webdav_sync_title),
                        title = stringResource(R.string.webdav_sync_items_title),
                        containerColor = cardColor,
                    ) {
                        WebDavSyncItem.entries.forEach { item ->
                            val itemState = state.itemStates[item]
                            val lastSync = itemState?.lastSyncTimeMs?.takeIf { it > 0 }
                            SettingsToggleItem(
                                title = stringResource(item.labelRes),
                                summary = if (lastSync != null) {
                                    stringResource(R.string.webdav_sync_last_sync, formatTime(lastSync))
                                } else {
                                    stringResource(item.descriptionRes)
                                },
                                checked = itemState?.enabled ?: true,
                                onCheckedChange = { viewModel.toggleItem(item) },
                            )
                        }

                        Spacer(Modifier.height(CardLayoutRhythm.compactSectionGap))

                        AppDualActionRow(
                            first = { modifier ->
                                AppStandaloneLiquidTextButton(
                                    variant = GlassVariant.SheetPrimaryAction,
                                    text = if (state.syncing && state.syncProgress?.contains("Uploading") == true) {
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
                                    text = if (state.syncing && state.syncProgress?.contains("Downloading") == true) {
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

                        val syncError = state.lastSyncError
                        if (syncError != null) {
                            Text(
                                text = syncError,
                                color = MiuixTheme.colorScheme.error,
                                fontSize = AppTypographyTokens.Supporting.fontSize,
                                lineHeight = AppTypographyTokens.Supporting.lineHeight,
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
    Column {
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
    }
}

@Composable
private fun AppDualActionRow(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap),
    ) {
        first(Modifier.weight(1f))
        second(Modifier.weight(1f))
    }
}

private fun formatTime(timeMs: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timeMs))
}
