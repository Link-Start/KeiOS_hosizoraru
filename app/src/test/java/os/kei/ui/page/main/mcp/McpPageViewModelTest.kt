package os.kei.ui.page.main.mcp

import androidx.lifecycle.SavedStateHandle
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpPageViewModelTest {
    @Test
    fun cardsDefaultCollapsedAndRememberExpandedState() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = McpPageViewModel(savedStateHandle)

        with(viewModel.uiState.value) {
            assertFalse(onboardingExpanded)
            assertFalse(controlExpanded)
            assertFalse(toolEntrypointsExpanded)
            assertFalse(runtimeToolsExpanded)
            assertFalse(systemToolsExpanded)
            assertFalse(githubToolsExpanded)
            assertFalse(baToolsExpanded)
            assertFalse(codexToolsExpanded)
            assertFalse(workflowToolsExpanded)
            assertFalse(advancedToolsExpanded)
            assertFalse(logsExpanded)
        }

        viewModel.updateOnboardingExpanded(true)
        viewModel.updateControlExpanded(true)
        viewModel.updateToolEntrypointsExpanded(true)
        viewModel.updateCodexToolsExpanded(true)
        viewModel.updateLogsExpanded(true)

        val recreated = McpPageViewModel(savedStateHandle)

        with(recreated.uiState.value) {
            assertTrue(onboardingExpanded)
            assertTrue(controlExpanded)
            assertTrue(toolEntrypointsExpanded)
            assertTrue(codexToolsExpanded)
            assertTrue(logsExpanded)
            assertFalse(runtimeToolsExpanded)
            assertFalse(systemToolsExpanded)
            assertFalse(githubToolsExpanded)
            assertFalse(baToolsExpanded)
            assertFalse(workflowToolsExpanded)
            assertFalse(advancedToolsExpanded)
        }
    }
}
