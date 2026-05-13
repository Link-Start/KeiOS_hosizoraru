package os.kei.ui.page.main.mcp.skill.component

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import os.kei.mcp.server.BOOTSTRAP_PROMPT
import os.kei.mcp.server.DIAGNOSTICS_PLAN_PROMPT
import os.kei.mcp.server.SKILL_DOMAIN_TEMPLATE_URI
import os.kei.mcp.server.SKILL_RESOURCE_URI
import os.kei.mcp.server.SKILL_TOOL_TEMPLATE_URI
import os.kei.mcp.server.WORKFLOW_RESOURCE_URI
import os.kei.ui.page.main.mcp.skill.state.McpSkillPageContentState
import os.kei.ui.page.main.mcp.skill.state.McpSkillPageTextBundle
import os.kei.ui.page.main.mcp.util.copyToClipboard
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn

@Composable
internal fun McpSkillContentList(
    innerPadding: PaddingValues,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    contentState: McpSkillPageContentState,
    textBundle: McpSkillPageTextBundle,
    onCopyCurrentConfig: () -> Unit,
    emptyItemText: String,
    titleColor: Color,
    subtitleColor: Color,
    accentColor: Color,
    codeColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var referenceExpanded by rememberSaveable { mutableStateOf(false) }
    fun copyText(label: String, text: String, toastText: String = textBundle.copiedToast) {
        copyToClipboard(context, label, text)
        Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
    }

    val copyActions = buildMcpSkillCopyActions(
        textBundle = textBundle,
        markdown = contentState.markdown.ifBlank { textBundle.emptyMarkdown },
        workflowResourceUri = WORKFLOW_RESOURCE_URI,
        domainTemplateUri = SKILL_DOMAIN_TEMPLATE_URI,
        bootstrapPrompt = BOOTSTRAP_PROMPT,
        diagnosticsPrompt = DIAGNOSTICS_PLAN_PROMPT
    )
    val resourceActions = buildMcpSkillResourceActions(
        textBundle = textBundle,
        skillResourceUri = SKILL_RESOURCE_URI,
        workflowResourceUri = WORKFLOW_RESOURCE_URI,
        domainTemplateUri = SKILL_DOMAIN_TEMPLATE_URI,
        toolTemplateUri = SKILL_TOOL_TEMPLATE_URI
    )
    AppPageLazyColumn(
        innerPadding = innerPadding,
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        bottomExtra = AppChromeTokens.pageBottomInsetExtra,
        sectionSpacing = AppChromeTokens.pageSectionGap
    ) {
        item {
            McpSkillOnboardingCard(
                textBundle = textBundle,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
                onCopyClawPrompt = {
                    copyText(
                        label = "claw-skill-prompt",
                        text = textBundle.clawPrompt,
                        toastText = textBundle.clawPromptCopiedToast
                    )
                },
                onCopyCurrentConfig = onCopyCurrentConfig
            )
        }
        item {
            McpSkillQuickCopyCard(
                textBundle = textBundle,
                actions = copyActions,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
                onCopy = { action -> copyText(action.label, action.payload) }
            )
        }
        item {
            McpSkillResourcesCard(
                textBundle = textBundle,
                resources = resourceActions,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
                onCopy = { action -> copyText(action.title, action.payload) }
            )
        }
        item {
            McpSkillFlowsCard(
                textBundle = textBundle,
                titleColor = titleColor,
                subtitleColor = subtitleColor
            )
        }
        item {
            McpSkillReferenceCard(
                textBundle = textBundle,
                expanded = referenceExpanded,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
                onExpandedChange = { referenceExpanded = it }
            )
        }
        if (referenceExpanded) {
            itemsIndexed(
                items = contentState.sections,
                key = { index, section -> "${section.level}:${section.title}:$index" },
                contentType = { _, _ -> "mcp_skill_section" }
            ) { _, section ->
                SkillSectionCard(
                    section = section,
                    titleColor = titleColor,
                    subtitleColor = subtitleColor,
                    accentColor = accentColor,
                    codeColor = codeColor,
                    emptyItemText = emptyItemText
                )
            }
        }
    }
}
