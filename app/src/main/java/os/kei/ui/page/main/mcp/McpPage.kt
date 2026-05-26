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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import os.kei.R
import os.kei.core.ext.showLiquidToastOnly
import os.kei.core.ext.showToast
import os.kei.core.platform.LocalNetworkPermissionCompat
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.core.ui.resource.resolveString
import os.kei.mcp.server.McpServerManager
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
    val mcpPageViewModel: McpPageViewModel = viewModel()
    val uiState by mcpServerManager.uiState.collectAsStateWithLifecycle()
    val routeState by mcpPageViewModel.routeState.collectAsStateWithLifecycle()
    val runtimeNowMs by mcpPageViewModel.runtimeNowMs.collectAsStateWithLifecycle()
    val pageUiState = routeState.pageUiState
    val mcpToolBuckets = routeState.toolBuckets
    val currentUiState by rememberUpdatedState(uiState)
    val unknownText = stringResource(R.string.common_unknown)

    val isDark = isSystemInDarkTheme()
    val titleColor = MiuixTheme.colorScheme.onBackground
    val subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f)
    val runningColor = Color(0xFF2E7D32)
    val stoppedColor = Color(0xFFC62828)
    LaunchedEffect(
        mcpPageViewModel,
        uiState.running,
        uiState.runningSinceEpochMs,
        runtime.isDataActive,
    ) {
        mcpPageViewModel.requestRuntimeTicker(
            running = uiState.running,
            runningSinceEpochMs = uiState.runningSinceEpochMs,
            dataActive = runtime.isDataActive,
        )
    }
    val overviewState =
        rememberMcpPageOverviewState(
            context = context,
            uiState = uiState,
            runtimeNowMs = runtimeNowMs,
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
            distinctLayers = runtime.hasActivated,
        )
    val topBarMaterialBackdrop = rememberAppTopBarColor(enableBackdropEffects = pageBackdropEffectsEnabled)
    val mcpGlassRuntime = LocalGlassEffectRuntime.current
    LaunchedEffect(context, mcpPageViewModel) {
        mcpPageViewModel.updateLocalNetworkPermissionGranted(hasMcpLocalNetworkPermission(context))
    }

    val localNetworkPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            val localNetworkPermissionGranted = granted || hasMcpLocalNetworkPermission(context)
            context.showToast(
                context.resolveString(
                    if (localNetworkPermissionGranted) {
                        R.string.mcp_toast_local_network_permission_granted
                    } else {
                        R.string.mcp_toast_local_network_permission_denied
                    },
                ),
            )
            val shouldStartServer =
                mcpPageViewModel.consumeLocalNetworkPermissionResult(localNetworkPermissionGranted)
            if (shouldStartServer && !mcpServerManager.uiState.value.running) {
                mcpPageViewModel.requestToggleServer(mcpServerManager)
            }
        }
    val toggleServer: () -> Unit = toggleServer@{
        val localNetworkPermissionGranted = hasMcpLocalNetworkPermission(context)
        mcpPageViewModel.updateLocalNetworkPermissionGranted(localNetworkPermissionGranted)
        if (!uiState.running && pageUiState.allowExternal && !localNetworkPermissionGranted) {
            LocalNetworkPermissionCompat
                .requiredPermissionOrNull()
                ?.let { permission ->
                    mcpPageViewModel.armStartAfterLocalNetworkPermission(true)
                    runCatching { localNetworkPermissionLauncher.launch(permission) }
                }
            context.showToast(R.string.mcp_toast_local_network_permission_requested)
            return@toggleServer
        }
        mcpPageViewModel.armStartAfterLocalNetworkPermission(false)
        mcpPageViewModel.requestToggleServer(mcpServerManager)
    }
    val logsExportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            mcpPageViewModel.completeLogsExport(
                contentResolver = context.contentResolver,
                uri = uri,
                state = currentUiState,
            )
        }
    LaunchedEffect(mcpPageViewModel, logsExportLauncher, context, unknownText) {
        mcpPageViewModel.events.collect { event ->
            when (event) {
                is McpPageEvent.Toast -> {
                    context.showToast(event.messageRes)
                }

                is McpPageEvent.LiquidToast -> {
                    context.showLiquidToastOnly(event.messageRes)
                }

                is McpPageEvent.StartFailed -> {
                    context.showToast(
                        context.resolveString(
                            R.string.mcp_toast_start_failed,
                            event.reason ?: unknownText,
                        ),
                    )
                }

                is McpPageEvent.SaveFailed -> {
                    context.showToast(
                        context.resolveString(
                            R.string.common_save_failed_with_reason,
                            event.reason ?: unknownText,
                        ),
                    )
                }

                is McpPageEvent.SendFailed -> {
                    context.showToast(
                        context.resolveString(
                            R.string.common_send_failed_with_reason,
                            event.reason ?: unknownText,
                        ),
                    )
                }

                is McpPageEvent.LogsExportFailed -> {
                    context.showToast(
                        context.resolveString(
                            R.string.mcp_toast_logs_export_failed,
                            event.reason,
                        ),
                    )
                }

                is McpPageEvent.CopyText -> {
                    copyToClipboard(context, event.label, event.text)
                    context.showLiquidToastOnly(event.successRes)
                }

                is McpPageEvent.LaunchLogsExport -> {
                    runCatching {
                        logsExportLauncher.launch(event.fileName)
                    }.onFailure {
                        mcpPageViewModel.finishLogsExport()
                        context.showToast(
                            context.resolveString(
                                R.string.mcp_toast_logs_export_failed,
                                it.javaClass.simpleName,
                            ),
                        )
                    }
                }
            }
        }
    }
    val actions =
        McpPageActions(
            onToggleServer = toggleServer,
            onOpenEditSheet = { mcpPageViewModel.updateEditSheetVisible(true) },
            onControlExpandedChange = mcpPageViewModel::updateControlExpanded,
            onSendTestNotification = { mcpPageViewModel.requestSendTestNotification(mcpServerManager) },
            onShowResetConfigConfirm = { mcpPageViewModel.updateResetConfigConfirmVisible(true) },
            onCopySkillResource = mcpPageViewModel::requestCopySkillResource,
            onCopyWorkflowResource = mcpPageViewModel::requestCopyWorkflowResource,
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
            onClearLogs = { mcpPageViewModel.requestClearLogs(mcpServerManager) },
            onCopyCurrentConfig = { mcpPageViewModel.requestCopyCurrentConfig(mcpServerManager, uiState) },
            onRefreshNow = { mcpPageViewModel.requestRefreshNow(mcpServerManager) },
            onSaveServiceConfig = { mcpPageViewModel.requestSaveConfig(mcpServerManager) },
            onServerNameChange = mcpPageViewModel::updateServerName,
            onPortTextChange = mcpPageViewModel::updatePortText,
            onAllowExternalChange = mcpPageViewModel::updateAllowExternal,
            onDismissEditSheet = { mcpPageViewModel.updateEditSheetVisible(false) },
            onShowResetTokenConfirm = { mcpPageViewModel.updateResetTokenConfirmVisible(true) },
            onResetConfig = { mcpPageViewModel.requestResetConfig(mcpServerManager) },
            onDismissResetConfigConfirm = { mcpPageViewModel.updateResetConfigConfirmVisible(false) },
            onResetToken = { mcpPageViewModel.requestResetToken(mcpServerManager) },
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
                refreshRunning = pageUiState.refreshRunning,
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
