package os.kei.ui.page.main.mcp

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import os.kei.mcp.server.McpServerManager
import os.kei.mcp.server.McpServerUiState

internal data class McpLogsExportRequest(
    val generatedAt: String,
    val fileName: String,
)

internal data class McpPageUiState(
    val portText: String = "38888",
    val allowExternal: Boolean = false,
    val serverName: String = "KeiOS MCP",
    val showEditSheet: Boolean = false,
    val controlExpanded: Boolean = true,
    val toolEntrypointsExpanded: Boolean = true,
    val runtimeToolsExpanded: Boolean = false,
    val systemToolsExpanded: Boolean = false,
    val githubToolsExpanded: Boolean = false,
    val baToolsExpanded: Boolean = false,
    val codexToolsExpanded: Boolean = true,
    val workflowToolsExpanded: Boolean = false,
    val advancedToolsExpanded: Boolean = false,
    val toolsSearchQuery: String = "",
    val logsExpanded: Boolean = false,
    val logsExporting: Boolean = false,
    val pendingLogsExport: McpLogsExportRequest? = null,
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

internal class McpPageViewModel : ViewModel() {
    private val repository = McpPageRepository()
    private val _uiState = MutableStateFlow(McpPageUiState())
    val uiState: StateFlow<McpPageUiState> = _uiState.asStateFlow()

    fun syncServiceDraft(
        serverState: McpServerUiState,
        force: Boolean = false,
    ) {
        _uiState.update { state ->
            if (state.showEditSheet && !force) return@update state
            state.copy(
                portText = serverState.port.toString(),
                allowExternal = serverState.allowExternal,
                serverName = serverState.serverName,
            )
        }
    }

    fun updatePortText(value: String) {
        _uiState.update { state -> state.copy(portText = value.filter(Char::isDigit).take(5)) }
    }

    fun updateAllowExternal(value: Boolean) {
        _uiState.update { state -> state.copy(allowExternal = value) }
    }

    fun updateServerName(value: String) {
        _uiState.update { state -> state.copy(serverName = value) }
    }

    fun updateEditSheetVisible(value: Boolean) {
        _uiState.update { state -> state.copy(showEditSheet = value) }
    }

    fun updateControlExpanded(value: Boolean) {
        _uiState.update { state -> state.copy(controlExpanded = value) }
    }

    fun updateToolEntrypointsExpanded(value: Boolean) {
        _uiState.update { state -> state.copy(toolEntrypointsExpanded = value) }
    }

    fun updateRuntimeToolsExpanded(value: Boolean) {
        _uiState.update { state -> state.copy(runtimeToolsExpanded = value) }
    }

    fun updateSystemToolsExpanded(value: Boolean) {
        _uiState.update { state -> state.copy(systemToolsExpanded = value) }
    }

    fun updateGithubToolsExpanded(value: Boolean) {
        _uiState.update { state -> state.copy(githubToolsExpanded = value) }
    }

    fun updateBaToolsExpanded(value: Boolean) {
        _uiState.update { state -> state.copy(baToolsExpanded = value) }
    }

    fun updateCodexToolsExpanded(value: Boolean) {
        _uiState.update { state -> state.copy(codexToolsExpanded = value) }
    }

    fun updateWorkflowToolsExpanded(value: Boolean) {
        _uiState.update { state -> state.copy(workflowToolsExpanded = value) }
    }

    fun updateAdvancedToolsExpanded(value: Boolean) {
        _uiState.update { state -> state.copy(advancedToolsExpanded = value) }
    }

    fun updateToolsSearchQuery(value: String) {
        _uiState.update { state -> state.copy(toolsSearchQuery = value.take(80)) }
    }

    fun updateLogsExpanded(value: Boolean) {
        _uiState.update { state -> state.copy(logsExpanded = value) }
    }

    fun updateResetTokenConfirmVisible(value: Boolean) {
        _uiState.update { state -> state.copy(showResetTokenConfirm = value) }
    }

    fun updateResetConfigConfirmVisible(value: Boolean) {
        _uiState.update { state -> state.copy(showResetConfigConfirm = value) }
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
    }

    fun consumePendingLogsExport(): McpLogsExportRequest? {
        val request = _uiState.value.pendingLogsExport
        _uiState.update { state -> state.copy(pendingLogsExport = null) }
        return request
    }

    fun finishLogsExport() {
        _uiState.update { state ->
            state.copy(
                logsExporting = false,
                pendingLogsExport = null,
            )
        }
    }

    suspend fun toggleServer(manager: McpServerManager): McpToggleServerResult =
        repository.toggleServer(
            manager = manager,
            draft = _uiState.value.serviceDraft,
        )

    suspend fun saveConfig(manager: McpServerManager): McpSaveConfigResult =
        repository.saveConfig(
            manager = manager,
            draft = _uiState.value.serviceDraft,
        )

    suspend fun resetConfigPreservingToken(manager: McpServerManager): Boolean = repository.resetConfigPreservingToken(manager)

    suspend fun resetToken(manager: McpServerManager) {
        repository.resetToken(manager)
    }

    suspend fun sendTestNotification(manager: McpServerManager): Result<Unit> = repository.sendTestNotification(manager)

    suspend fun refreshNow(manager: McpServerManager) {
        repository.refreshNow(manager)
    }

    suspend fun clearLogs(manager: McpServerManager) {
        repository.clearLogs(manager)
    }

    suspend fun buildConfigJson(
        manager: McpServerManager,
        serverState: McpServerUiState,
    ): String =
        repository.buildConfigJson(
            manager = manager,
            serverState = serverState,
            draft = _uiState.value.serviceDraft,
        )

    suspend fun exportLogs(
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
}
