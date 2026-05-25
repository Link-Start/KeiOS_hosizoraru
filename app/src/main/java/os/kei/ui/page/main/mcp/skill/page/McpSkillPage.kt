@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp.skill.page

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.core.ext.showToast
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.mcp.server.McpServerManager
import os.kei.ui.page.main.mcp.skill.McpSkillPageContentRequest
import os.kei.ui.page.main.mcp.skill.McpSkillPageEvent
import os.kei.ui.page.main.mcp.skill.McpSkillPageViewModel
import os.kei.ui.page.main.mcp.skill.component.McpSkillContentList
import os.kei.ui.page.main.mcp.skill.state.rememberMcpSkillPageTextBundle
import os.kei.ui.page.main.mcp.util.copyToClipboard
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun McpSkillPage(
    mcpServerManager: McpServerManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val textBundle = rememberMcpSkillPageTextBundle()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val titleColor = MiuixTheme.colorScheme.onBackground
    val subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant
    val accentColor = MiuixTheme.colorScheme.primary
    val codeColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
    val topBarMaterialBackdrop = rememberAppTopBarColor(enableBackdropEffects = true)
    val topBarBackdrop = rememberLayerBackdrop()
    val viewModel: McpSkillPageViewModel = viewModel()
    val contentRequest =
        remember(
            textBundle.emptyMarkdown,
            textBundle.defaultRootTitle,
            textBundle.defaultOverviewTitle,
            textBundle.defaultContentTitle,
            textBundle.emptyContentText,
        ) {
            McpSkillPageContentRequest(
                emptyMarkdown = textBundle.emptyMarkdown,
                defaultRootTitle = textBundle.defaultRootTitle,
                defaultOverviewTitle = textBundle.defaultOverviewTitle,
                defaultContentTitle = textBundle.defaultContentTitle,
                emptyContentText = textBundle.emptyContentText,
            )
        }
    LaunchedEffect(mcpServerManager, contentRequest) {
        viewModel.loadContent(
            manager = mcpServerManager,
            request = contentRequest,
        )
    }
    val contentState by viewModel.contentState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { event ->
            when (event) {
                is McpSkillPageEvent.CopyText -> {
                    copyToClipboard(context, event.label, event.text)
                    context.showToast(event.successRes)
                }
            }
        }
    }

    AppPageScaffold(
        title = textBundle.pageTitle,
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarMaterialBackdrop,
        titleBackdrop = topBarBackdrop,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = MiuixIcons.Regular.Back,
                contentDescription = textBundle.pageTitle,
                onClick = onBack,
                backdrop = topBarBackdrop,
            )
        },
    ) { innerPadding ->
        McpSkillContentList(
            innerPadding = innerPadding,
            listState = listState,
            nestedScrollConnection = scrollBehavior.nestedScrollConnection,
            contentState = contentState,
            textBundle = textBundle,
            onCopyCurrentConfig = { viewModel.requestCopyCurrentConfig(mcpServerManager) },
            emptyItemText = textBundle.emptyItemText,
            titleColor = titleColor,
            subtitleColor = subtitleColor,
            accentColor = accentColor,
            codeColor = codeColor,
            modifier =
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(topBarBackdrop),
        )
    }
}
