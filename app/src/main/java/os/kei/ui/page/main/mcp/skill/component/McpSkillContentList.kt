@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp.skill.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import os.kei.core.ext.showToast
import os.kei.mcp.server.BOOTSTRAP_PROMPT
import os.kei.mcp.server.DIAGNOSTICS_PLAN_PROMPT
import os.kei.mcp.server.SKILL_DOMAIN_TEMPLATE_URI
import os.kei.mcp.server.SKILL_RESOURCE_URI
import os.kei.mcp.server.SKILL_TOOL_TEMPLATE_URI
import os.kei.mcp.server.SUBAGENT_RESOURCE_URI
import os.kei.mcp.server.WORKFLOW_RESOURCE_URI
import os.kei.ui.page.main.mcp.skill.model.SkillSection
import os.kei.ui.page.main.mcp.skill.state.McpSkillPageContentState
import os.kei.ui.page.main.mcp.skill.state.McpSkillPageTextBundle
import os.kei.ui.page.main.mcp.util.copyToClipboard
import os.kei.ui.page.main.os.appLucideBranchIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucideWarningIcon
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var referenceExpanded by rememberSaveable { mutableStateOf(false) }

    fun copyText(
        label: String,
        text: String,
        toastText: String = textBundle.copiedToast,
    ) {
        copyToClipboard(context, label, text)
        context.showToast(toastText)
    }

    val markdown = contentState.markdown.ifBlank { textBundle.emptyMarkdown }
    val notesIcon = appLucideNotesIcon()
    val workflowIcon = appLucideBranchIcon()
    val domainIcon = appLucidePackageIcon()
    val bootstrapIcon = appLucideInfoIcon()
    val diagnosticsIcon = appLucideWarningIcon()
    val copyActions =
        remember(
            textBundle,
            markdown,
            notesIcon,
            workflowIcon,
            domainIcon,
            bootstrapIcon,
            diagnosticsIcon,
        ) {
            buildMcpSkillCopyActions(
                textBundle = textBundle,
                markdown = markdown,
                subAgentResourceUri = SUBAGENT_RESOURCE_URI,
                workflowResourceUri = WORKFLOW_RESOURCE_URI,
                domainTemplateUri = SKILL_DOMAIN_TEMPLATE_URI,
                bootstrapPrompt = BOOTSTRAP_PROMPT,
                diagnosticsPrompt = DIAGNOSTICS_PLAN_PROMPT,
                notesIcon = notesIcon,
                workflowIcon = workflowIcon,
                domainIcon = domainIcon,
                bootstrapIcon = bootstrapIcon,
                diagnosticsIcon = diagnosticsIcon,
            )
        }
    val resourceActions =
        remember(textBundle) {
            buildMcpSkillResourceActions(
                textBundle = textBundle,
                skillResourceUri = SKILL_RESOURCE_URI,
                subAgentResourceUri = SUBAGENT_RESOURCE_URI,
                workflowResourceUri = WORKFLOW_RESOURCE_URI,
                domainTemplateUri = SKILL_DOMAIN_TEMPLATE_URI,
                toolTemplateUri = SKILL_TOOL_TEMPLATE_URI,
            )
        }
    val referenceSectionRows =
        remember(contentState.sections) {
            contentState.sections.toStableMcpSkillSectionRows()
        }
    AppPageLazyColumn(
        innerPadding = innerPadding,
        state = listState,
        modifier =
            modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
        bottomExtra = AppChromeTokens.pageBottomInsetExtra,
        sectionSpacing = AppChromeTokens.pageSectionGap,
    ) {
        item(
            key = "mcp-skill-onboarding",
            contentType = "mcp_skill_onboarding",
        ) {
            McpSkillOnboardingCard(
                textBundle = textBundle,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
                onCopyClawPrompt = {
                    copyText(
                        label = "claw-skill-prompt",
                        text = textBundle.clawPrompt,
                        toastText = textBundle.clawPromptCopiedToast,
                    )
                },
                onCopyCurrentConfig = onCopyCurrentConfig,
            )
        }
        item(
            key = "mcp-skill-quick-copy",
            contentType = "mcp_skill_quick_copy",
        ) {
            McpSkillQuickCopyCard(
                textBundle = textBundle,
                actions = copyActions,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
                onCopy = { action -> copyText(action.label, action.payload) },
            )
        }
        item(
            key = "mcp-skill-resources",
            contentType = "mcp_skill_resources",
        ) {
            McpSkillResourcesCard(
                textBundle = textBundle,
                resources = resourceActions,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
                onCopy = { action -> copyText(action.title, action.payload) },
            )
        }
        item(
            key = "mcp-skill-flows",
            contentType = "mcp_skill_flows",
        ) {
            McpSkillFlowsCard(
                textBundle = textBundle,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
            )
        }
        item(
            key = "mcp-skill-reference",
            contentType = "mcp_skill_reference",
        ) {
            McpSkillReferenceCard(
                textBundle = textBundle,
                expanded = referenceExpanded,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
                onExpandedChange = { referenceExpanded = it },
            )
        }
        if (referenceExpanded) {
            items(
                items = referenceSectionRows,
                key = { row -> row.stableKey },
                contentType = { "mcp_skill_section" },
            ) { row ->
                SkillSectionCard(
                    section = row.section,
                    titleColor = titleColor,
                    subtitleColor = subtitleColor,
                    accentColor = accentColor,
                    codeColor = codeColor,
                    emptyItemText = emptyItemText,
                )
            }
        }
    }
}

internal data class StableMcpSkillSectionRow(
    val section: SkillSection,
    val stableKey: String,
)

internal fun List<SkillSection>.toStableMcpSkillSectionRows(): List<StableMcpSkillSectionRow> {
    if (isEmpty()) return emptyList()
    val occurrenceCounts = HashMap<String, Int>(size)
    return map { section ->
        val baseKey = section.stableMcpSkillSectionBaseKey()
        val occurrence = occurrenceCounts.getOrDefault(baseKey, 0)
        occurrenceCounts[baseKey] = occurrence + 1
        StableMcpSkillSectionRow(
            section = section,
            stableKey = "$baseKey#$occurrence",
        )
    }
}

private fun SkillSection.stableMcpSkillSectionBaseKey(): String =
    buildString {
        append(level)
        append(':')
        append(title.hashCode())
        append(':')
        append(items.size)
        append(':')
        append(items.hashCode())
    }
