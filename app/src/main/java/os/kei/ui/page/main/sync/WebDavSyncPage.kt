@file:Suppress("FunctionName")

package os.kei.ui.page.main.sync

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.shape.appSquircleBackground
import os.kei.ui.page.main.widget.shape.appSquircleBorder
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
) {
    val scope = rememberCoroutineScope()
    val viewModel = WebDavSyncViewModel(scope)
    val state = viewModel.uiState
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    val cardColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f)

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
                    // Provider selector — cycles through providers
                    val providerSummary = when (state.selectedProvider) {
                        WebDavProvider.Jianguoyun -> stringResource(R.string.webdav_sync_provider_jianguoyun_desc)
                        WebDavProvider.Custom -> stringResource(R.string.webdav_sync_provider_custom_desc)
                    }
                    SettingsNavigationItem(
                        title = stringResource(R.string.webdav_sync_provider_label),
                        summary = "${state.selectedProvider.displayName} · $providerSummary",
                        onClick = {
                            val next = WebDavProvider.entries.let { entries ->
                                val idx = (entries.indexOf(state.selectedProvider) + 1) % entries.size
                                entries[idx]
                            }
                            viewModel.selectProvider(next)
                        },
                    )

                    // Jianguoyun: show recommended server URL as read-only info
                    if (state.selectedProvider == WebDavProvider.Jianguoyun) {
                        SettingsInfoItem(
                            key = stringResource(R.string.webdav_sync_jianguoyun_server_label),
                            value = JianguoyunPreset.SERVER_URL,
                        )
                    }

                    // Server URL — only editable for Custom provider
                    if (state.selectedProvider == WebDavProvider.Custom) {
                        WebDavFieldLabel(text = stringResource(R.string.webdav_sync_server_url))
                        WebDavLiquidTextField(
                            value = state.serverUrl,
                            onValueChange = viewModel::updateServerUrl,
                            placeholder = stringResource(R.string.webdav_sync_server_url_placeholder),
                            minHeight = 44.dp,
                            singleLine = true,
                        )
                    }

                    // Username
                    WebDavFieldLabel(text = stringResource(R.string.webdav_sync_username))
                    WebDavLiquidTextField(
                        value = state.username,
                        onValueChange = viewModel::updateUsername,
                        placeholder = stringResource(R.string.webdav_sync_username_placeholder),
                        minHeight = 44.dp,
                        singleLine = true,
                    )

                    // App password
                    WebDavFieldLabel(text = stringResource(R.string.webdav_sync_app_password))
                    WebDavLiquidTextField(
                        value = state.appPassword,
                        onValueChange = viewModel::updateAppPassword,
                        placeholder = stringResource(R.string.webdav_sync_password_placeholder),
                        minHeight = 44.dp,
                        singleLine = true,
                    )
                    // Jianguoyun password format hint
                    if (state.selectedProvider == WebDavProvider.Jianguoyun) {
                        Text(
                            text = stringResource(R.string.webdav_sync_jianguoyun_password_hint),
                            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.74f),
                            fontSize = AppTypographyTokens.Caption.fontSize,
                            lineHeight = AppTypographyTokens.Caption.lineHeight,
                        )
                    }

                    // Remote directory
                    WebDavFieldLabel(text = stringResource(R.string.webdav_sync_remote_dir))
                    WebDavLiquidTextField(
                        value = state.remoteDir,
                        onValueChange = viewModel::updateRemoteDir,
                        placeholder = "KeiOS/",
                        minHeight = 44.dp,
                        singleLine = true,
                    )

                    // Jianguoyun full setup hint
                    if (state.selectedProvider == WebDavProvider.Jianguoyun) {
                        Spacer(Modifier.height(CardLayoutRhythm.compactSectionGap))
                        Text(
                            text = stringResource(R.string.webdav_sync_jianguoyun_hint),
                            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f),
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

// ── Text field components (matching project's FeedbackLiquidTextField pattern) ──

@Composable
private fun WebDavFieldLabel(text: String) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.74f),
        fontSize = AppTypographyTokens.Caption.fontSize,
        lineHeight = AppTypographyTokens.Caption.lineHeight,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun WebDavLiquidTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minHeight: Dp,
    singleLine: Boolean = false,
) {
    val textStyle = TextStyle(
        color = MiuixTheme.colorScheme.onBackground,
        fontSize = AppTypographyTokens.Body.fontSize,
        lineHeight = AppTypographyTokens.Body.lineHeight,
        textAlign = TextAlign.Start,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val placeholderStyle = textStyle.copy(color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.50f))
    WebDavLiquidPanel(minHeight = minHeight) {
        val fieldHeight = minHeight - 24.dp
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = true,
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = fieldHeight),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopStart,
                ) {
                    if (value.isBlank()) {
                        BasicText(
                            text = placeholder,
                            style = placeholderStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        innerTextField()
                    }
                }
            },
        )
    }
}

@Composable
private fun WebDavLiquidPanel(
    minHeight: Dp,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val borderColor = if (isDark) {
        Color(0xFF8ABEFF).copy(alpha = 0.24f)
    } else {
        Color(0xFFB5D7FF).copy(alpha = 0.82f)
    }
    val panelColor = if (isDark) {
        Color(0xFF121A24).copy(alpha = 0.78f)
    } else {
        Color.White.copy(alpha = 0.88f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .appSquircleBackground(panelColor, 18.dp)
            .appSquircleBorder(width = 1.dp, color = borderColor, cornerRadius = 18.dp)
            .padding(contentPadding),
        content = content,
    )
}

private fun formatTime(timeMs: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timeMs))
}
