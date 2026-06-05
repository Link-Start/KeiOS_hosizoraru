package os.kei.ui.page.main.mcp

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.mcp.server.McpServerManager
import os.kei.mcp.server.McpServerUiState
import os.kei.mcp.server.SKILL_RESOURCE_URI
import os.kei.mcp.server.SUBAGENT_RESOURCE_URI
import os.kei.mcp.server.WORKFLOW_RESOURCE_URI
import os.kei.ui.page.main.mcp.state.McpToolBucketInput
import os.kei.ui.page.main.mcp.state.McpToolBuckets

internal data class McpLogsExportRequest(
    val generatedAt: String,
    val fileName: String,
)

@Immutable
internal data class McpPageUiState(
    val portText: String = "38888",
    val allowExternal: Boolean = false,
    val serverName: String = "KeiOS MCP",
    val showEditSheet: Boolean = false,
    val onboardingExpanded: Boolean = false,
    val controlExpanded: Boolean = false,
    val toolEntrypointsExpanded: Boolean = false,
    val runtimeToolsExpanded: Boolean = false,
    val systemToolsExpanded: Boolean = false,
    val githubToolsExpanded: Boolean = false,
    val baToolsExpanded: Boolean = false,
    val codexToolsExpanded: Boolean = false,
    val workflowToolsExpanded: Boolean = false,
    val advancedToolsExpanded: Boolean = false,
    val toolsSearchQuery: String = "",
    val logsExpanded: Boolean = false,
    val logsExporting: Boolean = false,
    val refreshRunning: Boolean = false,
    val pendingLogsExport: McpLogsExportRequest? = null,
    val localNetworkPermissionGranted: Boolean = false,
    val startAfterLocalNetworkPermission: Boolean = false,
    val showResetTokenConfirm: Boolean = false,
    val showResetConfigConfirm: Boolean = false,
) {
    val serviceDraft: McpServiceDraft
        get() =
            McpServiceDraft(
                serverName = serverName,
                portText = portText,
                allowExternal = allowExternal,
            )
}

@Immutable
internal data class McpPageRouteState(
    val pageUiState: McpPageUiState = McpPageUiState(),
    val toolBuckets: McpToolBuckets = McpToolBuckets.Empty,
)

internal sealed interface McpPageEvent {
    data class Toast(
        @param:StringRes val messageRes: Int,
    ) : McpPageEvent

    data class LiquidToast(
        @param:StringRes val messageRes: Int,
    ) : McpPageEvent

    data class StartFailed(
        val reason: String?,
    ) : McpPageEvent

    data class SaveFailed(
        val reason: String?,
    ) : McpPageEvent

    data class SendFailed(
        val reason: String?,
    ) : McpPageEvent

    data class LogsExportFailed(
        val reason: String,
    ) : McpPageEvent

    data class CopyText(
        val label: String,
        val text: String,
        @param:StringRes val successRes: Int,
    ) : McpPageEvent

    data class LaunchLogsExport(
        val fileName: String,
    ) : McpPageEvent
}

internal class McpPageViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val repository = McpPageRepository()
    private val toolBucketLoader = McpToolBucketLoader(viewModelScope, repository)
    private val runtimeTicker = McpRuntimeTicker(viewModelScope)
    private val _uiState = MutableStateFlow(savedStateHandle.toInitialMcpPageUiState())
    val uiState: StateFlow<McpPageUiState> = _uiState.asStateFlow()
    val toolBuckets: StateFlow<McpToolBuckets> = toolBucketLoader.buckets
    val runtimeNowMs: StateFlow<Long> = runtimeTicker.nowMs
    private val _events = MutableSharedFlow<McpPageEvent>(replay = 0, extraBufferCapacity = 8)
    val events: SharedFlow<McpPageEvent> = _events.asSharedFlow()

    val routeState: StateFlow<McpPageRouteState> =
        combine(uiState, toolBuckets) { pageState, buckets ->
            McpPageRouteState(
                pageUiState = pageState,
                toolBuckets = buckets,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = McpPageRouteState(),
        )

    fun syncServiceDraft(
        serverState: McpServerUiState,
        force: Boolean = false,
    ) {
        updateUiStateIfChanged { state ->
            if (state.showEditSheet && !force) {
                state
            } else {
                state.copy(
                    portText = serverState.port.toString(),
                    allowExternal = serverState.allowExternal,
                    serverName = serverState.serverName,
                )
            }
        }
    }

    fun updatePortText(value: String) {
        val portText = value.filter(Char::isDigit).take(5)
        updateUiStateIfChanged { state -> state.copy(portText = portText) }
    }

    fun updateAllowExternal(value: Boolean) {
        updateUiStateIfChanged { state -> state.copy(allowExternal = value) }
    }

    fun updateServerName(value: String) {
        updateUiStateIfChanged { state -> state.copy(serverName = value) }
    }

    fun updateEditSheetVisible(value: Boolean) {
        updateUiStateIfChanged { state -> state.copy(showEditSheet = value) }
    }

    fun updateOnboardingExpanded(value: Boolean) {
        updatePersistentBoolean(
            key = MCP_STATE_ONBOARDING_EXPANDED,
            value = value,
            current = McpPageUiState::onboardingExpanded,
        ) { state -> state.copy(onboardingExpanded = value) }
    }

    fun updateControlExpanded(value: Boolean) {
        updatePersistentBoolean(
            key = MCP_STATE_CONTROL_EXPANDED,
            value = value,
            current = McpPageUiState::controlExpanded,
        ) { state -> state.copy(controlExpanded = value) }
    }

    fun updateToolEntrypointsExpanded(value: Boolean) {
        updatePersistentBoolean(
            key = MCP_STATE_TOOL_ENTRYPOINTS_EXPANDED,
            value = value,
            current = McpPageUiState::toolEntrypointsExpanded,
        ) { state -> state.copy(toolEntrypointsExpanded = value) }
    }

    fun updateRuntimeToolsExpanded(value: Boolean) {
        updatePersistentBoolean(
            key = MCP_STATE_RUNTIME_TOOLS_EXPANDED,
            value = value,
            current = McpPageUiState::runtimeToolsExpanded,
        ) { state -> state.copy(runtimeToolsExpanded = value) }
    }

    fun updateSystemToolsExpanded(value: Boolean) {
        updatePersistentBoolean(
            key = MCP_STATE_SYSTEM_TOOLS_EXPANDED,
            value = value,
            current = McpPageUiState::systemToolsExpanded,
        ) { state -> state.copy(systemToolsExpanded = value) }
    }

    fun updateGithubToolsExpanded(value: Boolean) {
        updatePersistentBoolean(
            key = MCP_STATE_GITHUB_TOOLS_EXPANDED,
            value = value,
            current = McpPageUiState::githubToolsExpanded,
        ) { state -> state.copy(githubToolsExpanded = value) }
    }

    fun updateBaToolsExpanded(value: Boolean) {
        updatePersistentBoolean(
            key = MCP_STATE_BA_TOOLS_EXPANDED,
            value = value,
            current = McpPageUiState::baToolsExpanded,
        ) { state -> state.copy(baToolsExpanded = value) }
    }

    fun updateCodexToolsExpanded(value: Boolean) {
        updatePersistentBoolean(
            key = MCP_STATE_CODEX_TOOLS_EXPANDED,
            value = value,
            current = McpPageUiState::codexToolsExpanded,
        ) { state -> state.copy(codexToolsExpanded = value) }
    }

    fun updateWorkflowToolsExpanded(value: Boolean) {
        updatePersistentBoolean(
            key = MCP_STATE_WORKFLOW_TOOLS_EXPANDED,
            value = value,
            current = McpPageUiState::workflowToolsExpanded,
        ) { state -> state.copy(workflowToolsExpanded = value) }
    }

    fun updateAdvancedToolsExpanded(value: Boolean) {
        updatePersistentBoolean(
            key = MCP_STATE_ADVANCED_TOOLS_EXPANDED,
            value = value,
            current = McpPageUiState::advancedToolsExpanded,
        ) { state -> state.copy(advancedToolsExpanded = value) }
    }

    fun updateToolsSearchQuery(value: String) {
        val searchQuery = value.take(80)
        updateUiStateIfChanged { state -> state.copy(toolsSearchQuery = searchQuery) }
    }

    fun requestToolBuckets(input: McpToolBucketInput) {
        toolBucketLoader.request(input)
    }

    fun requestRuntimeTicker(
        running: Boolean,
        runningSinceEpochMs: Long,
        dataActive: Boolean,
    ) {
        runtimeTicker.request(
            running = running,
            runningSinceEpochMs = runningSinceEpochMs,
            dataActive = dataActive,
        )
    }

    fun updateLogsExpanded(value: Boolean) {
        updatePersistentBoolean(
            key = MCP_STATE_LOGS_EXPANDED,
            value = value,
            current = McpPageUiState::logsExpanded,
        ) { state -> state.copy(logsExpanded = value) }
    }

    fun updateLocalNetworkPermissionGranted(granted: Boolean) {
        updateUiStateIfChanged { state -> state.copy(localNetworkPermissionGranted = granted) }
    }

    fun armStartAfterLocalNetworkPermission(armed: Boolean) {
        updateUiStateIfChanged { state -> state.copy(startAfterLocalNetworkPermission = armed) }
    }

    fun consumeLocalNetworkPermissionResult(granted: Boolean): Boolean {
        var shouldStart = false
        _uiState.update { state ->
            shouldStart = state.startAfterLocalNetworkPermission && granted
            state.copy(
                localNetworkPermissionGranted = granted,
                startAfterLocalNetworkPermission = false,
            )
        }
        return shouldStart
    }

    fun updateResetTokenConfirmVisible(value: Boolean) {
        updateUiStateIfChanged { state -> state.copy(showResetTokenConfirm = value) }
    }

    fun updateResetConfigConfirmVisible(value: Boolean) {
        updateUiStateIfChanged { state -> state.copy(showResetConfigConfirm = value) }
    }

    fun beginLogsExport(
        generatedAt: String,
        fileName: String,
    ) {
        _uiState.update { state ->
            state.copy(
                logsExporting = true,
                pendingLogsExport =
                    McpLogsExportRequest(
                        generatedAt = generatedAt,
                        fileName = fileName,
                ),
            )
        }
        viewModelScope.launch {
            _events.emit(McpPageEvent.LaunchLogsExport(fileName))
        }
    }

    private fun consumePendingLogsExport(): McpLogsExportRequest? {
        val request = _uiState.value.pendingLogsExport
        _uiState.update { state -> state.copy(pendingLogsExport = null) }
        return request
    }

    fun finishLogsExport() {
        updateUiStateIfChanged { state ->
            state.copy(
                logsExporting = false,
                pendingLogsExport = null,
            )
        }
    }

    fun requestToggleServer(manager: McpServerManager) {
        viewModelScope.launch {
            when (val result = toggleServer(manager)) {
                McpToggleServerResult.InvalidPort -> _events.emit(McpPageEvent.Toast(R.string.common_port_invalid))
                is McpToggleServerResult.Failed -> _events.emit(McpPageEvent.StartFailed(result.reason))
                McpToggleServerResult.Started -> _events.emit(McpPageEvent.LiquidToast(R.string.mcp_toast_service_started))
                McpToggleServerResult.Stopped -> _events.emit(McpPageEvent.LiquidToast(R.string.mcp_toast_service_stopped))
            }
        }
    }

    fun requestCopyCurrentConfig(
        manager: McpServerManager,
        serverState: McpServerUiState,
    ) {
        viewModelScope.launch {
            val json =
                buildConfigJson(
                    manager = manager,
                    serverState = serverState,
                )
            _events.emit(
                McpPageEvent.CopyText(
                    label = "mcp-config",
                    text = json,
                    successRes = R.string.mcp_toast_config_copied,
                ),
            )
        }
    }

    fun requestCopySkillResource() {
        _events.tryEmit(
            McpPageEvent.CopyText(
                label = "mcp-skill-resource",
                text = SKILL_RESOURCE_URI,
                successRes = R.string.mcp_toast_resource_copied,
            ),
        )
    }

    fun requestCopySubAgentResource() {
        _events.tryEmit(
            McpPageEvent.CopyText(
                label = "mcp-subagent-resource",
                text = SUBAGENT_RESOURCE_URI,
                successRes = R.string.mcp_toast_resource_copied,
            ),
        )
    }

    fun requestCopyWorkflowResource() {
        _events.tryEmit(
            McpPageEvent.CopyText(
                label = "mcp-workflow-resource",
                text = WORKFLOW_RESOURCE_URI,
                successRes = R.string.mcp_toast_resource_copied,
            ),
        )
    }

    fun requestCopyClawSetupPrompt(
        manager: McpServerManager,
        serverState: McpServerUiState,
        promptTemplate: String,
    ) {
        viewModelScope.launch {
            val prompt =
                repository.buildClawSetupPrompt(
                    manager = manager,
                    serverState = serverState,
                    draft = _uiState.value.serviceDraft,
                    promptTemplate = promptTemplate,
                )
            _events.emit(
                McpPageEvent.CopyText(
                    label = "mcp-claw-setup-prompt",
                    text = prompt,
                    successRes = R.string.mcp_toast_claw_setup_prompt_copied,
                ),
            )
        }
    }

    fun requestRefreshNow(manager: McpServerManager) {
        if (_uiState.value.refreshRunning) return
        _uiState.update { state -> state.copy(refreshRunning = true) }
        viewModelScope.launch {
            try {
                refreshNow(manager)
                _events.emit(McpPageEvent.LiquidToast(R.string.common_refreshed))
            } finally {
                _uiState.update { state -> state.copy(refreshRunning = false) }
            }
        }
    }

    fun requestSaveConfig(manager: McpServerManager) {
        viewModelScope.launch {
            when (val result = saveConfig(manager)) {
                McpSaveConfigResult.InvalidPort -> {
                    _events.emit(McpPageEvent.Toast(R.string.common_port_invalid))
                }

                is McpSaveConfigResult.Failed -> {
                    _events.emit(McpPageEvent.SaveFailed(result.reason))
                }

                McpSaveConfigResult.Success -> {
                    _events.emit(McpPageEvent.Toast(R.string.mcp_toast_saved_requires_restart))
                    updateEditSheetVisible(false)
                    syncServiceDraft(manager.uiState.value, force = true)
                }
            }
        }
    }

    fun requestSendTestNotification(manager: McpServerManager) {
        viewModelScope.launch {
            sendTestNotification(manager)
                .onSuccess {
                    _events.emit(McpPageEvent.Toast(R.string.mcp_toast_test_notification_sent))
                }.onFailure {
                    _events.emit(McpPageEvent.SendFailed(it.message))
                }
        }
    }

    fun requestResetConfig(manager: McpServerManager) {
        viewModelScope.launch {
            val requiresRestart = resetConfigPreservingToken(manager)
            _events.emit(
                McpPageEvent.Toast(
                    if (requiresRestart) {
                        R.string.mcp_toast_config_reset_requires_restart
                    } else {
                        R.string.mcp_toast_config_reset
                    },
                ),
            )
            updateResetConfigConfirmVisible(false)
        }
    }

    fun requestResetToken(manager: McpServerManager) {
        viewModelScope.launch {
            resetToken(manager)
            _events.emit(McpPageEvent.Toast(R.string.mcp_toast_token_reset_reconnect))
            updateResetTokenConfirmVisible(false)
        }
    }

    fun requestClearLogs(manager: McpServerManager) {
        viewModelScope.launch {
            clearLogs(manager)
        }
    }

    fun completeLogsExport(
        contentResolver: ContentResolver,
        uri: Uri?,
        state: McpServerUiState,
    ) {
        val request = consumePendingLogsExport()
        if (uri == null || request == null) {
            finishLogsExport()
            return
        }
        viewModelScope.launch {
            val result =
                runCatching {
                    exportLogs(
                        contentResolver = contentResolver,
                        uri = uri,
                        request = request,
                        state = state,
                    )
                }
            finishLogsExport()
            result
                .onSuccess {
                    _events.emit(McpPageEvent.LiquidToast(R.string.mcp_toast_logs_exported))
                }.onFailure {
                    _events.emit(McpPageEvent.LogsExportFailed(it.javaClass.simpleName))
                }
        }
    }

    private suspend fun toggleServer(manager: McpServerManager): McpToggleServerResult =
        repository.toggleServer(
            manager = manager,
            draft = _uiState.value.serviceDraft,
        )

    private suspend fun saveConfig(manager: McpServerManager): McpSaveConfigResult =
        repository.saveConfig(
            manager = manager,
            draft = _uiState.value.serviceDraft,
        )

    private suspend fun resetConfigPreservingToken(manager: McpServerManager): Boolean = repository.resetConfigPreservingToken(manager)

    private suspend fun resetToken(manager: McpServerManager) {
        repository.resetToken(manager)
    }

    private suspend fun sendTestNotification(manager: McpServerManager): Result<Unit> = repository.sendTestNotification(manager)

    private suspend fun refreshNow(manager: McpServerManager) {
        repository.refreshNow(manager)
    }

    private suspend fun clearLogs(manager: McpServerManager) {
        repository.clearLogs(manager)
    }

    private suspend fun buildConfigJson(
        manager: McpServerManager,
        serverState: McpServerUiState,
    ): String =
        repository.buildConfigJson(
            manager = manager,
            serverState = serverState,
            draft = _uiState.value.serviceDraft,
        )

    private suspend fun exportLogs(
        contentResolver: ContentResolver,
        uri: Uri,
        request: McpLogsExportRequest,
        state: McpServerUiState,
    ) {
        repository.exportLogs(
            contentResolver = contentResolver,
            uri = uri,
            generatedAt = request.generatedAt,
            state = state,
        )
    }

    private inline fun updateUiStateIfChanged(
        crossinline transform: (McpPageUiState) -> McpPageUiState,
    ): Boolean {
        var changed = false
        _uiState.update { state ->
            val next = transform(state)
            changed = next != state
            if (changed) next else state
        }
        return changed
    }

    private inline fun updatePersistentBoolean(
        key: String,
        value: Boolean,
        crossinline current: (McpPageUiState) -> Boolean,
        crossinline transform: (McpPageUiState) -> McpPageUiState,
    ) {
        val changed =
            updateUiStateIfChanged { state ->
                if (current(state) == value) state else transform(state)
            }
        if (changed) {
            savedStateHandle[key] = value
        }
    }
}

private const val MCP_STATE_CONTROL_EXPANDED = "mcp.controlExpanded"
private const val MCP_STATE_ONBOARDING_EXPANDED = "mcp.onboardingExpanded"
private const val MCP_STATE_TOOL_ENTRYPOINTS_EXPANDED = "mcp.toolEntrypointsExpanded"
private const val MCP_STATE_RUNTIME_TOOLS_EXPANDED = "mcp.runtimeToolsExpanded"
private const val MCP_STATE_SYSTEM_TOOLS_EXPANDED = "mcp.systemToolsExpanded"
private const val MCP_STATE_GITHUB_TOOLS_EXPANDED = "mcp.githubToolsExpanded"
private const val MCP_STATE_BA_TOOLS_EXPANDED = "mcp.baToolsExpanded"
private const val MCP_STATE_CODEX_TOOLS_EXPANDED = "mcp.codexToolsExpanded"
private const val MCP_STATE_WORKFLOW_TOOLS_EXPANDED = "mcp.workflowToolsExpanded"
private const val MCP_STATE_ADVANCED_TOOLS_EXPANDED = "mcp.advancedToolsExpanded"
private const val MCP_STATE_LOGS_EXPANDED = "mcp.logsExpanded"

private fun SavedStateHandle.toInitialMcpPageUiState(): McpPageUiState =
    McpPageUiState(
        onboardingExpanded = get<Boolean>(MCP_STATE_ONBOARDING_EXPANDED) ?: false,
        controlExpanded = get<Boolean>(MCP_STATE_CONTROL_EXPANDED) ?: false,
        toolEntrypointsExpanded = get<Boolean>(MCP_STATE_TOOL_ENTRYPOINTS_EXPANDED) ?: false,
        runtimeToolsExpanded = get<Boolean>(MCP_STATE_RUNTIME_TOOLS_EXPANDED) ?: false,
        systemToolsExpanded = get<Boolean>(MCP_STATE_SYSTEM_TOOLS_EXPANDED) ?: false,
        githubToolsExpanded = get<Boolean>(MCP_STATE_GITHUB_TOOLS_EXPANDED) ?: false,
        baToolsExpanded = get<Boolean>(MCP_STATE_BA_TOOLS_EXPANDED) ?: false,
        codexToolsExpanded = get<Boolean>(MCP_STATE_CODEX_TOOLS_EXPANDED) ?: false,
        workflowToolsExpanded = get<Boolean>(MCP_STATE_WORKFLOW_TOOLS_EXPANDED) ?: false,
        advancedToolsExpanded = get<Boolean>(MCP_STATE_ADVANCED_TOOLS_EXPANDED) ?: false,
        logsExpanded = get<Boolean>(MCP_STATE_LOGS_EXPANDED) ?: false,
    )
