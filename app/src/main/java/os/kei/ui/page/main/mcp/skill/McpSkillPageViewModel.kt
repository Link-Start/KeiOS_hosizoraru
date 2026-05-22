package os.kei.ui.page.main.mcp.skill

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.mcp.server.McpServerManager
import os.kei.ui.page.main.mcp.skill.state.McpSkillPageContentState

internal sealed interface McpSkillPageEvent {
    data class CopyText(
        val label: String,
        val text: String,
        @param:StringRes val successRes: Int,
    ) : McpSkillPageEvent
}

internal class McpSkillPageViewModel : ViewModel() {
    private val repository = McpSkillPageRepository()
    private val _contentState = MutableStateFlow(McpSkillPageContentState())
    val contentState: StateFlow<McpSkillPageContentState> = _contentState.asStateFlow()
    private val _events = MutableSharedFlow<McpSkillPageEvent>(replay = 0, extraBufferCapacity = 4)
    val events: SharedFlow<McpSkillPageEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null
    private var lastRequest: McpSkillPageContentRequest? = null

    fun loadContent(
        manager: McpServerManager,
        request: McpSkillPageContentRequest,
    ) {
        if (lastRequest == request && _contentState.value.markdown.isNotBlank()) return
        if (lastRequest == request && loadJob?.isActive == true) return
        lastRequest = request
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _contentState.value = repository.loadContent(manager, request)
            }
    }

    fun requestCopyCurrentConfig(manager: McpServerManager) {
        viewModelScope.launch {
            _events.emit(
                McpSkillPageEvent.CopyText(
                    label = "mcp-config",
                    text = repository.buildConfigJson(manager),
                    successRes = R.string.mcp_toast_config_copied,
                ),
            )
        }
    }
}
