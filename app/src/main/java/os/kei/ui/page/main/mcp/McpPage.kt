package os.kei.ui.page.main.mcp

import android.content.Context
import os.kei.core.ext.showToast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.platform.LocalNetworkPermissionCompat
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.core.ui.resource.resolveString
import os.kei.mcp.server.McpServerManager
import os.kei.mcp.server.SKILL_RESOURCE_URI
import os.kei.mcp.server.WORKFLOW_RESOURCE_URI
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.host.pager.rememberMainPageBackdropSet
import os.kei.ui.page.main.mcp.dialog.McpResetConfigDialog
import os.kei.ui.page.main.mcp.dialog.McpResetTokenDialog
import os.kei.ui.page.main.mcp.section.McpLogsSection
import os.kei.ui.page.main.mcp.section.McpOverviewCardSection
import os.kei.ui.page.main.mcp.section.McpServiceControlSection
import os.kei.ui.page.main.mcp.section.McpToolsSection
import os.kei.ui.page.main.mcp.sheet.McpEditServiceSheet
import os.kei.ui.page.main.mcp.state.rememberMcpPageOverviewState
import os.kei.ui.page.main.mcp.util.copyToClipboard
import os.kei.ui.page.main.os.appLucideEditIcon
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.os.appLucidePauseIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.osLucideCopyIcon
import os.kei.ui.page.main.os.osLucideRunIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.LiquidActionBar
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import os.kei.ui.page.main.widget.chrome.appPageBottomPaddingWithFloatingOverlay
import os.kei.ui.page.main.widget.glass.AppFloatingDockAction
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.AppFloatingVerticalActionDock
import os.kei.ui.page.main.widget.glass.LocalGlassEffectRuntime
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun McpPage(
    mcpServerManager: McpServerManager,
    runtime: MainPageRuntime = MainPageRuntime(contentBottomPadding = 72.dp),
    liquidActionBarLayeredStyleEnabled: Boolean = true,
    onOpenSkill: () -> Unit = {},
    onActionBarInteractingChanged: (Boolean) -> Unit = {}
) {
    val mcpTitle = stringResource(R.string.page_mcp_title)
    val editServiceParamsContentDescription = stringResource(R.string.mcp_action_edit_service_params)
    val openSkillContentDescription = stringResource(R.string.mcp_action_open_skill_md)
    val copyConfigContentDescription = stringResource(R.string.mcp_action_copy_current_config)
    val resourceCopiedText = stringResource(R.string.mcp_toast_resource_copied)
    val refreshContentDescription = stringResource(R.string.common_refresh)
    val unknownText = stringResource(R.string.common_unknown)
    val runtimePendingText = stringResource(R.string.mcp_runtime_pending)
    val titleColor = MiuixTheme.colorScheme.onBackground
    val subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f)
    val runningColor = Color(0xFF2E7D32)
    val stoppedColor = Color(0xFFC62828)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mcpPageViewModel: McpPageViewModel = viewModel()
    val uiState by mcpServerManager.uiState.collectAsStateWithLifecycle()
    val pageUiState by mcpPageViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(
        mcpServerManager,
        uiState.port,
        uiState.allowExternal,
        uiState.serverName,
        pageUiState.showEditSheet
    ) {
        mcpPageViewModel.syncServiceDraft(uiState)
    }
    val isDark = isSystemInDarkTheme()
    val overviewState = rememberMcpPageOverviewState(
        context = context,
        uiState = uiState,
        runtime = runtime,
        isDark = isDark,
        titleColor = titleColor,
        subtitleColor = subtitleColor,
        runningColor = runningColor,
        stoppedColor = stoppedColor,
        runtimePendingText = runtimePendingText
    )
    val portText = pageUiState.portText
    val allowExternal = pageUiState.allowExternal
    val serverName = pageUiState.serverName
    val serviceDraftChanged = serverName.trim() != uiState.serverName.trim() ||
            portText.trim() != uiState.port.toString() ||
            allowExternal != uiState.allowExternal
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val currentUiState by rememberUpdatedState(uiState)
    val serverNameHint = context.resolveString(R.string.mcp_input_service_name_hint)
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
                    context.showToast(context.resolveString(
                        R.string.mcp_toast_start_failed,
                        result.reason ?: unknownText
                    ))
                }

                McpToggleServerResult.Started -> {
                    context.showToast(R.string.mcp_toast_service_started)
                }

                McpToggleServerResult.Stopped -> {
                    context.showToast(R.string.mcp_toast_service_stopped)
                }
            }
        }
    }
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        localNetworkPermissionGranted = granted || hasMcpLocalNetworkPermission(context)
        context.showToast(context.resolveString(
            if (localNetworkPermissionGranted) {
                R.string.mcp_toast_local_network_permission_granted
            } else {
                R.string.mcp_toast_local_network_permission_denied
            }
        ))
        val shouldStartServer = startAfterLocalNetworkPermission && localNetworkPermissionGranted
        startAfterLocalNetworkPermission = false
        if (shouldStartServer && !mcpServerManager.uiState.value.running) {
            launchServerToggle()
        }
    }
    val serverNameFieldWidth = remember(serverName, serverNameHint) {
        val visibleChars = serverName.trim().ifBlank { serverNameHint }.length.coerceIn(6, 18)
        (visibleChars * 11 + 36).dp
    }
    val portFieldWidth = remember(portText) {
        val visibleChars = portText.trim().ifBlank { "38888" }.length.coerceIn(4, 6)
        (visibleChars * 14 + 28).dp
    }
    val pageBackdropEffectsEnabled = runtime.isPageActive &&
        !runtime.isPagerScrollInProgress
    val fullBackdropEffectsEnabled = pageBackdropEffectsEnabled
    val mcpGlassRuntime = LocalGlassEffectRuntime.current
    val toggleServer: () -> Unit = toggleServer@{
        localNetworkPermissionGranted = hasMcpLocalNetworkPermission(context)
        if (!uiState.running && allowExternal && !localNetworkPermissionGranted) {
            LocalNetworkPermissionCompat.requiredPermissionOrNull()
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
    val dockAlignment = if (runtime.floatingDockSide == AppFloatingDockSide.Start) {
        Alignment.BottomStart
    } else {
        Alignment.BottomEnd
    }
    val dockStartPadding = if (runtime.floatingDockSide == AppFloatingDockSide.Start) 14.dp else 0.dp
    val dockEndPadding = if (runtime.floatingDockSide == AppFloatingDockSide.End) 14.dp else 0.dp
    val bottomBarOffset = if (runtime.bottomBarVisible) 0.dp else AppChromeTokens.floatingBottomBarOuterHeight
    val floatingDockBottom by animateDpAsState(
        targetValue = runtime.contentBottomPadding - 24.dp - bottomBarOffset,
        label = "mcp_floating_action_dock_bottom"
    )
    val copyCurrentConfig: () -> Unit = {
        scope.launch {
            val json = mcpPageViewModel.buildConfigJson(
                manager = mcpServerManager,
                serverState = uiState
            )
            copyToClipboard(context, "mcp-config", json)
            context.showToast(R.string.mcp_toast_config_copied)
        }
    }
    val copySkillResource: () -> Unit = {
        copyToClipboard(context, "mcp-skill-resource", SKILL_RESOURCE_URI)
        context.showToast(resourceCopiedText)
    }
    val copyWorkflowResource: () -> Unit = {
        copyToClipboard(context, "mcp-workflow-resource", WORKFLOW_RESOURCE_URI)
        context.showToast(resourceCopiedText)
    }
    val refreshMcpNow: () -> Unit = {
        if (!refreshRunning) {
            scope.launch {
                refreshRunning = true
                try {
                    mcpPageViewModel.refreshNow(mcpServerManager)
                    context.showToast(R.string.common_refreshed)
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
                    context.showToast(context.resolveString(
                        R.string.common_save_failed_with_reason,
                        result.reason ?: unknownText
                    ))
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
            mcpPageViewModel.sendTestNotification(mcpServerManager)
                .onSuccess {
                    context.showToast(R.string.mcp_toast_test_notification_sent)
                }
                .onFailure {
                    context.showToast(context.resolveString(
                        R.string.common_send_failed_with_reason,
                        it.message ?: unknownText
                    ))
                }
        }
    }
    val resetConfig: () -> Unit = {
        scope.launch {
            val requiresRestart = mcpPageViewModel.resetConfigPreservingToken(mcpServerManager)
            context.showToast(context.resolveString(
                if (requiresRestart) {
                    R.string.mcp_toast_config_reset_requires_restart
                } else {
                    R.string.mcp_toast_config_reset
                }
            ))
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
    val backdrops = rememberMainPageBackdropSet(
        keyPrefix = "mcp",
        refreshOnCompositionEnter = true,
        distinctLayers = fullBackdropEffectsEnabled
    )
    val topBarMaterialBackdrop = rememberAppTopBarColor(enableBackdropEffects = pageBackdropEffectsEnabled)
    DisposableEffect(Unit) {
        onDispose { onActionBarInteractingChanged(false) }
    }
    LaunchedEffect(runtime.scrollToTopSignal, runtime.isPageActive) {
        if (runtime.isPageActive && runtime.scrollToTopSignal > 0) listState.animateScrollToItem(0)
    }
    val logsExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val request = mcpPageViewModel.consumePendingLogsExport()
        if (uri == null || request == null) {
            mcpPageViewModel.finishLogsExport()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = runCatching {
                mcpPageViewModel.exportLogs(
                    contentResolver = context.contentResolver,
                    uri = uri,
                    request = request,
                    state = currentUiState
                )
            }
            mcpPageViewModel.finishLogsExport()
            result.onSuccess {
                context.showToast(R.string.mcp_toast_logs_exported)
            }.onFailure {
                context.showToast(context.resolveString(
                    R.string.mcp_toast_logs_export_failed,
                    it.javaClass.simpleName
                ))
            }
        }
    }
    val onOpenSkillState = rememberUpdatedState(onOpenSkill)
    val editIcon = appLucideEditIcon()
    val notesIcon = appLucideNotesIcon()
    val copyIcon = osLucideCopyIcon()
    val refreshIcon = appLucideRefreshIcon()
    val toggleIcon = if (uiState.running) appLucidePauseIcon() else osLucideRunIcon()
    val toggleContentDescription = if (uiState.running) {
        stringResource(R.string.mcp_action_stop_service)
    } else {
        stringResource(R.string.mcp_action_start_service)
    }
    val dockActions = listOf(
        AppFloatingDockAction(
            icon = copyIcon,
            contentDescription = copyConfigContentDescription,
            iconTint = MiuixTheme.colorScheme.primary,
            onClick = copyCurrentConfig
        ),
        AppFloatingDockAction(
            icon = refreshIcon,
            contentDescription = refreshContentDescription,
            iconTint = MiuixTheme.colorScheme.primary,
            enabled = !refreshRunning,
            rotating = refreshRunning,
            onClick = refreshMcpNow
        ),
        AppFloatingDockAction(
            icon = toggleIcon,
            contentDescription = toggleContentDescription,
            iconTint = if (uiState.running) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
            onClick = toggleServer
        )
    )
    val actionItems = remember(
        editServiceParamsContentDescription,
        openSkillContentDescription
    ) {
        listOf(
            LiquidActionItem(
                icon = editIcon,
                contentDescription = editServiceParamsContentDescription,
                onClick = { mcpPageViewModel.updateEditSheetVisible(true) }
            ),
            LiquidActionItem(
                icon = notesIcon,
                contentDescription = openSkillContentDescription,
                onClick = { onOpenSkillState.value() }
            )
        )
    }

    CompositionLocalProvider(LocalGlassEffectRuntime provides mcpGlassRuntime) {
    AppPageScaffold(
        title = "",
        largeTitle = mcpTitle,
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarMaterialBackdrop,
        titleBackdrop = backdrops.topBar,
        reserveTopEndActionSpace = true,
        actions = {
            LiquidActionBar(
                backdrop = backdrops.topBar,
                layeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                items = actionItems,
                onInteractionChanged = onActionBarInteractingChanged
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            AppPageLazyColumn(
                innerPadding = innerPadding,
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                bottomExtra = appPageBottomPaddingWithFloatingOverlay(runtime.contentBottomPadding),
                sectionSpacing = 12.dp
            ) {
                item(key = "mcp-overview", contentType = "mcp_overview_section") {
                    McpOverviewCardSection(
                        backdrop = backdrops.content,
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        overviewCardColor = overviewState.overviewCardColor,
                        overviewBorderColor = overviewState.overviewBorderColor,
                        overviewAccentColor = overviewState.overviewAccentColor,
                        runtimeText = overviewState.runtimeText,
                        isDark = isDark,
                        running = uiState.running,
                        overviewMetrics = overviewState.overviewMetrics,
                        onToggleServer = toggleServer,
                        onOpenEditSheet = { mcpPageViewModel.updateEditSheetVisible(true) }
                    )
                }
                item(key = "mcp-service-control", contentType = "mcp_service_control_section") {
                    McpServiceControlSection(
                        backdrop = backdrops.content,
                        expanded = pageUiState.controlExpanded,
                        onExpandedChange = mcpPageViewModel::updateControlExpanded,
                        onSendTestNotification = sendTestNotification,
                        onShowResetConfigConfirm = {
                            mcpPageViewModel.updateResetConfigConfirmVisible(
                                true
                            )
                        },
                        onCopySkillResource = copySkillResource,
                        onCopyWorkflowResource = copyWorkflowResource
                    )
                }
                item(key = "mcp-tools", contentType = "mcp_tools_section") {
                    McpToolsSection(
                        backdrop = backdrops.content,
                        expanded = pageUiState.configExpanded,
                        onExpandedChange = mcpPageViewModel::updateConfigExpanded,
                        uiState = uiState,
                        searchQuery = pageUiState.toolsSearchQuery,
                        onSearchQueryChange = mcpPageViewModel::updateToolsSearchQuery,
                        advancedExpanded = pageUiState.advancedToolsExpanded,
                        onAdvancedExpandedChange = mcpPageViewModel::updateAdvancedToolsExpanded
                    )
                }
                item(key = "mcp-logs", contentType = "mcp_logs_section") {
                    McpLogsSection(
                        backdrop = backdrops.content,
                        expanded = pageUiState.logsExpanded,
                        onExpandedChange = mcpPageViewModel::updateLogsExpanded,
                        uiState = uiState,
                        logsExporting = pageUiState.logsExporting,
                        onExportLogs = { generatedAt, fileName ->
                            mcpPageViewModel.beginLogsExport(generatedAt, fileName)
                            runCatching {
                                logsExportLauncher.launch(fileName)
                            }.onFailure {
                                mcpPageViewModel.finishLogsExport()
                                context.showToast(context.resolveString(
                                    R.string.mcp_toast_logs_export_failed,
                                    it.javaClass.simpleName
                                ))
                            }
                        },
                        onClearLogs = {
                            scope.launch {
                                mcpPageViewModel.clearLogs(mcpServerManager)
                            }
                        },
                        subtitleColor = subtitleColor
                    )
                }
            }

            AppFloatingVerticalActionDock(
                backdrop = backdrops.content,
                actions = dockActions,
                modifier = Modifier
                    .align(dockAlignment)
                    .padding(
                        start = dockStartPadding,
                        end = dockEndPadding,
                        bottom = floatingDockBottom
                    )
                )
        }
    }

    McpEditServiceSheet(
        show = pageUiState.showEditSheet,
        backdrop = backdrops.sheet,
        serverName = serverName,
        onServerNameChange = mcpPageViewModel::updateServerName,
        serverNameFieldWidth = serverNameFieldWidth,
        portText = portText,
        onPortTextChange = mcpPageViewModel::updatePortText,
        portFieldWidth = portFieldWidth,
        allowExternal = allowExternal,
        hasUnsavedChanges = serviceDraftChanged,
        onAllowExternalChange = mcpPageViewModel::updateAllowExternal,
        onSave = saveServiceConfig,
        onDismissRequest = { mcpPageViewModel.updateEditSheetVisible(false) },
        onShowResetTokenConfirm = { mcpPageViewModel.updateResetTokenConfirmVisible(true) }
    )

    McpResetConfigDialog(
        show = pageUiState.showResetConfigConfirm,
        onConfirm = resetConfig,
        onDismissRequest = { mcpPageViewModel.updateResetConfigConfirmVisible(false) }
    )

    McpResetTokenDialog(
        show = pageUiState.showResetTokenConfirm,
        onConfirm = resetToken,
        onDismissRequest = { mcpPageViewModel.updateResetTokenConfirmVisible(false) }
    )
    }
}

private fun hasMcpLocalNetworkPermission(context: Context): Boolean {
    return LocalNetworkPermissionCompat.hasPermission(context)
}
