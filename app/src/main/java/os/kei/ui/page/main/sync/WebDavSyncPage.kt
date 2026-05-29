@file:Suppress("FunctionName")

package os.kei.ui.page.main.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import os.kei.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WebDAV sync configuration and control page.
 *
 * @param dataPorts export/import lambdas for each sync item.
 *   The caller provides these so the page has no direct dependency on domain stores.
 */
@Composable
internal fun WebDavSyncPage(
    onBack: () -> Unit,
    dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>,
) {
    val scope = rememberCoroutineScope()
    val viewModel = WebDavSyncViewModel(scope)
    val state = viewModel.uiState

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState()),
        ) {
            // Title bar with back
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onBack) {
                    Text(text = "←")
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.webdav_sync_title),
                    style = MiuixTheme.textStyles.headline1,
                )
            }

            // Provider selector
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.webdav_sync_provider_label),
                        style = MiuixTheme.textStyles.headline2,
                    )
                    Spacer(Modifier.height(8.dp))
                    WebDavProvider.entries.forEach { provider ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectProvider(provider) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = provider.displayName,
                                style = MiuixTheme.textStyles.body1,
                            )
                            if (provider == state.selectedProvider) {
                                Spacer(Modifier.width(8.dp))
                                Text(text = "✓", color = MiuixTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Connection config
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.webdav_sync_connection),
                        style = MiuixTheme.textStyles.headline2,
                    )
                    Spacer(Modifier.height(12.dp))

                    if (state.selectedProvider == WebDavProvider.Jianguoyun) {
                        Text(
                            text = stringResource(R.string.webdav_sync_jianguoyun_hint),
                            style = MiuixTheme.textStyles.body2,
                            color = Color.Gray,
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (state.selectedProvider == WebDavProvider.Custom) {
                        LabeledField(
                            label = stringResource(R.string.webdav_sync_server_url),
                            value = state.serverUrl,
                            onValueChange = viewModel::updateServerUrl,
                            placeholder = "https://dav.example.com/",
                        )
                    }

                    LabeledField(
                        label = stringResource(R.string.webdav_sync_username),
                        value = state.username,
                        onValueChange = viewModel::updateUsername,
                        placeholder = stringResource(R.string.webdav_sync_username_placeholder),
                    )

                    LabeledField(
                        label = stringResource(R.string.webdav_sync_app_password),
                        value = state.appPassword,
                        onValueChange = viewModel::updateAppPassword,
                        placeholder = stringResource(R.string.webdav_sync_password_placeholder),
                        isPassword = true,
                    )

                    LabeledField(
                        label = stringResource(R.string.webdav_sync_remote_dir),
                        value = state.remoteDir,
                        onValueChange = viewModel::updateRemoteDir,
                        placeholder = "KeiOS/",
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = { viewModel.testConnection() },
                            enabled = !state.testing && state.username.isNotBlank() && state.appPassword.isNotBlank(),
                        ) {
                            Text(
                                text = if (state.testing) stringResource(R.string.webdav_sync_testing)
                                else stringResource(R.string.webdav_sync_test_connection),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.saveConfig() },
                            enabled = state.username.isNotBlank() && state.appPassword.isNotBlank(),
                        ) {
                            Text(text = stringResource(R.string.webdav_sync_save))
                        }
                    }

                    AnimatedVisibility(visible = state.testResult != null) {
                        val result = state.testResult
                        val color = when (result) {
                            is WebDavTestUiResult.Success -> Color(0xFF22C55E)
                            is WebDavTestUiResult.Failure -> Color(0xFFEF4444)
                            null -> Color.Transparent
                        }
                        val text = when (result) {
                            is WebDavTestUiResult.Success -> stringResource(R.string.webdav_sync_test_success)
                            is WebDavTestUiResult.Failure -> stringResource(R.string.webdav_sync_test_failed, result.message)
                            null -> ""
                        }
                        if (text.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(text = text, color = color, style = MiuixTheme.textStyles.body2)
                        }
                    }
                }
            }

            // Sync items
            if (state.isConfigured) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.webdav_sync_items_title),
                            style = MiuixTheme.textStyles.headline2,
                        )
                        Spacer(Modifier.height(8.dp))

                        WebDavSyncItem.entries.forEach { item ->
                            val itemState = state.itemStates[item]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleItem(item) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(item.labelRes),
                                        style = MiuixTheme.textStyles.body1,
                                    )
                                    Text(
                                        text = stringResource(item.descriptionRes),
                                        style = MiuixTheme.textStyles.body2,
                                        color = Color.Gray,
                                    )
                                    if (itemState != null && itemState.lastSyncTimeMs > 0) {
                                        Text(
                                            text = stringResource(
                                                R.string.webdav_sync_last_sync,
                                                formatTime(itemState.lastSyncTimeMs),
                                            ),
                                            style = MiuixTheme.textStyles.body2,
                                            color = Color.Gray,
                                        )
                                    }
                                }
                                if (itemState != null) {
                                    Text(
                                        text = if (itemState.enabled) "✓" else "—",
                                        color = if (itemState.enabled) MiuixTheme.colorScheme.primary else Color.Gray,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                onClick = { viewModel.uploadAll(dataPorts) },
                                enabled = !state.syncing,
                            ) {
                                Text(
                                    text = if (state.syncing && state.syncProgress?.contains("Uploading") == true) state.syncProgress.orEmpty()
                                    else stringResource(R.string.webdav_sync_upload),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.downloadAll(dataPorts) },
                                enabled = !state.syncing,
                            ) {
                                Text(
                                    text = if (state.syncing && state.syncProgress?.contains("Downloading") == true) state.syncProgress.orEmpty()
                                    else stringResource(R.string.webdav_sync_download),
                                )
                            }
                        }

                        val syncError = state.lastSyncError
                        if (syncError != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = syncError,
                                color = Color(0xFFEF4444),
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                }
            }

            Spacer(
                Modifier
                    .height(32.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MiuixTheme.textStyles.body2)
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

private fun formatTime(timeMs: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timeMs))
}
