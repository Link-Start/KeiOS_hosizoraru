@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ext.showLiquidToastOnly
import os.kei.core.ext.showToast
import os.kei.core.platform.LocalNetworkPermissionCompat
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.core.ui.resource.resolveString
import os.kei.mcp.server.McpServerManager
import os.kei.mcp.server.SKILL_RESOURCE_URI
import os.kei.mcp.server.WORKFLOW_RESOURCE_URI
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.host.pager.rememberMainPageBackdropSet
import os.kei.ui.page.main.mcp.state.rememberMcpPageOverviewState
import os.kei.ui.page.main.mcp.util.copyToClipboard
import os.kei.ui.page.main.os.appLucideEditIcon
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import os.kei.ui.page.main.widget.glass.LocalGlassEffectRuntime
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun McpPage(
    mcpServerManager: McpServerManager,
    runtime: MainPageRuntime = MainPageRuntime(contentBottomPadding = 72.dp),
    liquidActionBarLayeredStyleEnabled: Boolean = true,
    onShowBottomBar: () -> Unit = {},
    onOpenSkill: () -> Unit = {},
    onActionBarInteractingChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mcpPageViewModel: McpPageViewModel = viewModel()
    val uiState by mcpServerManager.uiState.collectAsStateWithLifecycle()
    val pageUiState by mcpPageViewModel.uiState.collectAsStateWithLifecycle()
    val mcpToolBuckets by mcpPageViewModel.toolBuckets.collectAsStateWithLifecycle()
    val currentUiState by rememberUpdatedState(uiState)
    val unknownText = stringResource(R.string.common_unknown)
    val resourceCopiedText = stringResource(R.string.mcp_toast_resource_copied)

    val isDark = isSystemInDarkTheme()
    val titleColor = MiuixTheme.colorScheme.onBackground
    val subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f)
    val runningColor = Color(0xFF2E7D32)
    val stoppedColor = Color(0xFFC62828)
    val overviewState =
        rememberMcpPageOverviewState(
            context = context,
            uiState = uiState,
            runtime = runtime,
            isDark = isDark,
            titleColor = titleColor,
            subtitleColor = subtitleColor,
            runningColor = runningColor,
            stoppedColor = stoppedColor,
            runtimePendingText = stringResource(R.string.mcp_runtime_pending),
        )

    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdropEffectsEnabled = runtime.isPageActive && !runtime.isPagerScrollInProgress
    val backdrops =
        rememberMainPageBackdropSet(
            keyPrefix = "mcp",
            refreshOnCompositionEnter = true,
            distinctLayers = pageBackdropEffectsEnabled,
        )
    val topBarMaterialBackdrop = rememberAppTopBarColor(enableBackdropEffects = pageBackdropEffectsEnabled)
    val mcpGlassRuntime = LocalGlassEffectRuntime.current
    var localNetworkPermissionGranted by remember {
        mutableStateOf(hasMcpLocalNetworkPermission(context))
    }
    var startAfterLocalNetworkPermission by remember { mutableStateOf(false) }
    var refreshRunning by remember { mutableStateOf(false) }

    fun launchServerToggle() {
        scope.launch {
            when (val result = mcpPageViewModel.toggleServer(mcpServerManager)) {
                McpToggleServerResult.InvalidPort -> {
                    context.showToast(R.string.common_port_invalid)
                }

                is McpToggleServerResult.Failed -> {
                    context.showToast(
                        context.resolveString(
                            R.string.mcp_toast_start_failed,
                            result.reason ?: unknownText,
                        ),
                    )
                }

                McpToggleServerResult.Started -> {
                    context.showLiquidToastOnly(R.string.mcp_toast_service_started)
                }

                McpToggleServerResult.Stopped -> {
                    context.showLiquidToastOnly(R.string.mcp_toast_service_stopped)
                }
            }
        }
    }

    val localNetworkPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            localNetworkPermissionGranted = granted || hasMcpLocalNetworkPermission(context)
            context.showToast(
                context.resolveString(
                    if (localNetworkPermissionGranted) {
                        R.string.mcp_toast_local_network_permission_granted
                    } else {
                        R.string.mcp_toast_local_network_permission_denied
                    },
                ),
            )
            val shouldStartServer = startAfterLocalNetworkPermission && localNetworkPermissionGranted
            startAfterLocalNetworkPermission = false
            if (shouldStartServer && !mcpServerManager.uiState.value.running) {
                launchServerToggle()
            }
        }
    val toggleServer: () -> Unit = toggleServer@{
        localNetworkPermissionGranted = hasMcpLocalNetworkPermission(context)
        if (!uiState.running && pageUiState.allowExternal && !localNetworkPermissionGranted) {
            LocalNetworkPermissionCompat
                .requiredPermissionOrNull()
                ?.let { permission ->
                    startAfterLocalNetworkPermission = true
                    runCatching { localNetworkPermissionLauncher.launch(permission) }
                }
            context.showToast(R.string.mcp_toast_local_network_permission_requested)
            return@toggleServer
        }
        startAfterLocalNetworkPermission = false
        launchServerToggle()
    }
    val copyCurrentConfig: () -> Unit = {
        scope.launch {
            val json =
                mcpPageViewModel.buildConfigJson(
                    manager = mcpServerManager,
                    serverState = uiState,
                )
            copyToClipboard(context, "mcp-config", json)
            context.showLiquidToastOnly(R.string.mcp_toast_config_copied)
        }
    }
    val copySkillResource: () -> Unit = {
        copyToClipboard(context, "mcp-skill-resource", SKILL_RESOURCE_URI)
        context.showLiquidToastOnly(resourceCopiedText)
    }
    val copyWorkflowResource: () -> Unit = {
        copyToClipboard(context, "mcp-workflow-resource", WORKFLOW_RESOURCE_URI)
        context.showLiquidToastOnly(resourceCopiedText)
    }
    val refreshMcpNow: () -> Unit = {
        if (!refreshRunning) {
            scope.launch {
                refreshRunning = true
                try {
                    mcpPageViewModel.refreshNow(mcpServerManager)
                    context.showLiquidToastOnly(R.string.common_refreshed)
                } finally {
                    refreshRunning = false
                }
            }
        }
    }
    val saveServiceConfig: () -> Unit = {
        scope.launch {
            when (val result = mcpPageViewModel.saveConfig(mcpServerManager)) {
                McpSaveConfigResult.InvalidPort -> {
                    context.showToast(R.string.common_port_invalid)
                }

                is McpSaveConfigResult.Failed -> {
                    context.showToast(
                        context.resolveString(
                            R.string.common_save_failed_with_reason,
                            result.reason ?: unknownText,
                        ),
                    )
                }

                McpSaveConfigResult.Success -> {
                    context.showToast(R.string.mcp_toast_saved_requires_restart)
                    mcpPageViewModel.updateEditSheetVisible(false)
                    mcpPageViewModel.syncServiceDraft(mcpServerManager.uiState.value, force = true)
                }
            }
        }
    }
    val sendTestNotification: () -> Unit = {
        scope.launch {
            mcpPageViewModel
                .sendTestNotification(mcpServerManager)
                .onSuccess {
                    context.showToast(R.string.mcp_toast_test_notification_sent)
                }.onFailure {
                    context.showToast(
                        context.resolveString(
                            R.string.common_send_failed_with_reason,
                            it.message ?: unknownText,
                        ),
                    )
                }
        }
    }
    val resetConfig: () -> Unit = {
        scope.launch {
            val requiresRestart = mcpPageViewModel.resetConfigPreservingToken(mcpServerManager)
            context.showToast(
                context.resolveString(
                    if (requiresRestart) {
                        R.string.mcp_toast_config_reset_requires_restart
                    } else {
                        R.string.mcp_toast_config_reset
                    },
                ),
            )
            mcpPageViewModel.updateResetConfigConfirmVisible(false)
        }
    }
    val resetToken: () -> Unit = {
        scope.launch {
            mcpPageViewModel.resetToken(mcpServerManager)
            context.showToast(R.string.mcp_toast_token_reset_reconnect)
            mcpPageViewModel.updateResetTokenConfirmVisible(false)
        }
    }
    val logsExportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            val request = mcpPageViewModel.consumePendingLogsExport()
            if (uri == null || request == null) {
                mcpPageViewModel.finishLogsExport()
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val result =
                    runCatching {
                        mcpPageViewModel.exportLogs(
                            contentResolver = context.contentResolver,
                            uri = uri,
                            request = request,
                            state = currentUiState,
                        )
                    }
                mcpPageViewModel.finishLogsExport()
                result
                    .onSuccess {
                        context.showLiquidToastOnly(R.string.mcp_toast_logs_exported)
                    }.onFailure {
                        context.showToast(
                            context.resolveString(
                                R.string.mcp_toast_logs_export_failed,
                                it.javaClass.simpleName,
                            ),
                        )
                    }
            }
        }
    val actions =
        McpPageActions(
            onToggleServer = toggleServer,
            onOpenEditSheet = { mcpPageViewModel.updateEditSheetVisible(true) },
            onControlExpandedChange = mcpPageViewModel::updateControlExpanded,
            onSendTestNotification = sendTestNotification,
            onShowResetConfigConfirm = { mcpPageViewModel.updateResetConfigConfirmVisible(true) },
            onCopySkillResource = copySkillResource,
            onCopyWorkflowResource = copyWorkflowResource,
            onToolsSearchQueryChange = mcpPageViewModel::updateToolsSearchQuery,
            onToolEntrypointsExpandedChange = mcpPageViewModel::updateToolEntrypointsExpanded,
            onRuntimeToolsExpandedChange = mcpPageViewModel::updateRuntimeToolsExpanded,
            onSystemToolsExpandedChange = mcpPageViewModel::updateSystemToolsExpanded,
            onGithubToolsExpandedChange = mcpPageViewModel::updateGithubToolsExpanded,
            onBaToolsExpandedChange = mcpPageViewModel::updateBaToolsExpanded,
            onCodexToolsExpandedChange = mcpPageViewModel::updateCodexToolsExpanded,
            onWorkflowToolsExpandedChange = mcpPageViewModel::updateWorkflowToolsExpanded,
            onAdvancedToolsExpandedChange = mcpPageViewModel::updateAdvancedToolsExpanded,
            onLogsExpandedChange = mcpPageViewModel::updateLogsExpanded,
            onExportLogs = { generatedAt, fileName ->
                mcpPageViewModel.beginLogsExport(generatedAt, fileName)
                runCatching {
                    logsExportLauncher.launch(fileName)
                }.onFailure {
                    mcpPageViewModel.finishLogsExport()
                    context.showToast(
                        context.resolveString(
                            R.string.mcp_toast_logs_export_failed,
                            it.javaClass.simpleName,
                        ),
                    )
                }
            },
            onClearLogs = {
                scope.launch {
                    mcpPageViewModel.clearLogs(mcpServerManager)
                }
            },
            onCopyCurrentConfig = copyCurrentConfig,
            onRefreshNow = refreshMcpNow,
            onSaveServiceConfig = saveServiceConfig,
            onServerNameChange = mcpPageViewModel::updateServerName,
            onPortTextChange = mcpPageViewModel::updatePortText,
            onAllowExternalChange = mcpPageViewModel::updateAllowExternal,
            onDismissEditSheet = { mcpPageViewModel.updateEditSheetVisible(false) },
            onShowResetTokenConfirm = { mcpPageViewModel.updateResetTokenConfirmVisible(true) },
            onResetConfig = resetConfig,
            onDismissResetConfigConfirm = { mcpPageViewModel.updateResetConfigConfirmVisible(false) },
            onResetToken = resetToken,
            onDismissResetTokenConfirm = { mcpPageViewModel.updateResetTokenConfirmVisible(false) },
        )
    val serverNameHint = context.resolveString(R.string.mcp_input_service_name_hint)
    val serverNameFieldWidth =
        remember(pageUiState.serverName, serverNameHint) {
            val visibleChars =
                pageUiState.serverName
                    .trim()
                    .ifBlank { serverNameHint }
                    .length
                    .coerceIn(6, 18)
            (visibleChars * 11 + 36).dp
        }
    val portFieldWidth =
        remember(pageUiState.portText) {
            val visibleChars =
                pageUiState.portText
                    .trim()
                    .ifBlank { "38888" }
                    .length
                    .coerceIn(4, 6)
            (visibleChars * 14 + 28).dp
        }
    val serviceDraftChanged =
        pageUiState.serverName.trim() != uiState.serverName.trim() ||
            pageUiState.portText.trim() != uiState.port.toString() ||
            pageUiState.allowExternal != uiState.allowExternal

    BindMcpPageEffects(
        mcpServerManager = mcpServerManager,
        mcpPageViewModel = mcpPageViewModel,
        uiState = uiState,
        pageUiState = pageUiState,
        runtime = runtime,
        listState = listState,
        onActionBarInteractingChanged = onActionBarInteractingChanged,
    )

    CompositionLocalProvider(LocalGlassEffectRuntime provides mcpGlassRuntime) {
        AppPageScaffold(
            title = "",
            largeTitle = stringResource(R.string.page_mcp_title),
            modifier = Modifier.fillMaxSize(),
            scrollBehavior = scrollBehavior,
            topBarColor = topBarMaterialBackdrop,
            titleBackdrop = backdrops.topBar,
            reserveTopEndActionSpace = true,
            onTitleClick = onShowBottomBar,
            actions = {
                LiquidActionBar(
                    backdrop = backdrops.topBar,
                    layeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                    items =
                        rememberMcpPageActionItems(
                            onOpenEditSheet = actions.onOpenEditSheet,
                            onOpenSkill = onOpenSkill,
                        ),
                    onInteractionChanged = onActionBarInteractingChanged,
                )
            },
        ) { innerPadding ->
            McpPageContent(
                uiState = uiState,
                pageUiState = pageUiState,
                toolBuckets = mcpToolBuckets,
                overviewState = overviewState,
                runtime = runtime,
                innerPadding = innerPadding,
                listState = listState,
                nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                backdrops = backdrops,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                isDark = isDark,
                refreshRunning = refreshRunning,
                actions = actions,
            )
        }
        McpPageSheets(
            pageUiState = pageUiState,
            backdrops = backdrops,
            serverNameFieldWidth = serverNameFieldWidth,
            portFieldWidth = portFieldWidth,
            serviceDraftChanged = serviceDraftChanged,
            actions = actions,
        )
    }
}

@Composable
private fun rememberMcpPageActionItems(
    onOpenEditSheet: () -> Unit,
    onOpenSkill: () -> Unit,
): List<LiquidActionItem> {
    val editIcon = appLucideEditIcon()
    val notesIcon = appLucideNotesIcon()
    val editServiceParamsContentDescription = stringResource(R.string.mcp_action_edit_service_params)
    val openSkillContentDescription = stringResource(R.string.mcp_action_open_skill_md)
    return remember(
        editIcon,
        notesIcon,
        editServiceParamsContentDescription,
        openSkillContentDescription,
        onOpenEditSheet,
        onOpenSkill,
    ) {
        listOf(
            LiquidActionItem(
                icon = editIcon,
                contentDescription = editServiceParamsContentDescription,
                onClick = onOpenEditSheet,
            ),
            LiquidActionItem(
                icon = notesIcon,
                contentDescription = openSkillContentDescription,
                onClick = onOpenSkill,
            ),
        )
    }
}

private fun hasMcpLocalNetworkPermission(context: Context): Boolean = LocalNetworkPermissionCompat.hasPermission(context)
