package os.kei.ui.page.main.mcp.skill.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import os.kei.R

@Stable
internal data class McpSkillPageTextBundle(
    val pageTitle: String,
    val emptyMarkdown: String,
    val emptyItemText: String,
    val defaultRootTitle: String,
    val defaultOverviewTitle: String,
    val defaultContentTitle: String,
    val emptyContentText: String,
    val clawPrompt: String,
    val copyClawPromptText: String,
    val clawPromptCopiedToast: String,
    val onboardingTitle: String,
    val onboardingSummary: String,
    val quickCopyTitle: String,
    val quickCopySummary: String,
    val resourcesTitle: String,
    val resourcesSummary: String,
    val flowsTitle: String,
    val flowsSummary: String,
    val referenceTitle: String,
    val referenceSummary: String,
    val expandReferenceText: String,
    val collapseReferenceText: String,
    val copyCurrentConfigText: String,
    val copyFullSkillText: String,
    val copySubAgentResourceText: String,
    val copyWorkflowResourceText: String,
    val copyDomainTemplateText: String,
    val copyBootstrapPromptText: String,
    val copyDiagnosticsPromptText: String,
    val currentConfigSummary: String,
    val fullSkillSummary: String,
    val subAgentResourceSummary: String,
    val workflowResourceSummary: String,
    val domainTemplateSummary: String,
    val bootstrapPromptSummary: String,
    val diagnosticsPromptSummary: String,
    val resourceSkillTitle: String,
    val resourceSubAgentTitle: String,
    val resourceWorkflowTitle: String,
    val resourceDomainGithubTitle: String,
    val resourceDomainRuntimeTitle: String,
    val resourceToolTemplateTitle: String,
    val resourceSkillSummary: String,
    val resourceSubAgentSummary: String,
    val resourceWorkflowSummary: String,
    val resourceDomainGithubSummary: String,
    val resourceDomainRuntimeSummary: String,
    val resourceToolTemplateSummary: String,
    val flowConnectTitle: String,
    val flowConnectSummary: String,
    val flowWorkflowTitle: String,
    val flowWorkflowSummary: String,
    val flowDiagnosticsTitle: String,
    val flowDiagnosticsSummary: String,
    val copiedToast: String
)

@Composable
internal fun rememberMcpSkillPageTextBundle(): McpSkillPageTextBundle {
    val pageTitle = stringResource(R.string.mcp_skill_page_title)
    val emptyMarkdown = stringResource(R.string.mcp_skill_markdown_empty)
    val emptyItemText = stringResource(R.string.mcp_skill_section_empty_items)
    val defaultRootTitle = stringResource(R.string.mcp_skill_section_default_root)
    val defaultOverviewTitle = stringResource(R.string.mcp_skill_section_default_overview)
    val defaultContentTitle = stringResource(R.string.mcp_skill_section_default_content)
    val emptyContentText = stringResource(R.string.mcp_skill_section_empty_content)
    val clawPrompt = stringResource(R.string.mcp_skill_claw_prompt)
    val copyClawPromptText = stringResource(R.string.mcp_skill_action_copy_claw_prompt)
    val clawPromptCopiedToast = stringResource(R.string.mcp_skill_toast_claw_prompt_copied)
    val onboardingTitle = stringResource(R.string.mcp_skill_onboarding_title)
    val onboardingSummary = stringResource(R.string.mcp_skill_onboarding_summary)
    val quickCopyTitle = stringResource(R.string.mcp_skill_quick_copy_title)
    val quickCopySummary = stringResource(R.string.mcp_skill_quick_copy_summary)
    val resourcesTitle = stringResource(R.string.mcp_skill_resources_title)
    val resourcesSummary = stringResource(R.string.mcp_skill_resources_summary)
    val flowsTitle = stringResource(R.string.mcp_skill_flows_title)
    val flowsSummary = stringResource(R.string.mcp_skill_flows_summary)
    val referenceTitle = stringResource(R.string.mcp_skill_reference_title)
    val referenceSummary = stringResource(R.string.mcp_skill_reference_summary)
    val expandReferenceText = stringResource(R.string.mcp_skill_action_expand_reference)
    val collapseReferenceText = stringResource(R.string.mcp_skill_action_collapse_reference)
    val copyCurrentConfigText = stringResource(R.string.mcp_skill_action_copy_current_config)
    val copyFullSkillText = stringResource(R.string.mcp_skill_action_copy_full_skill)
    val copySubAgentResourceText = stringResource(R.string.mcp_skill_action_copy_subagent_resource)
    val copyWorkflowResourceText = stringResource(R.string.mcp_skill_action_copy_workflow_resource)
    val copyDomainTemplateText = stringResource(R.string.mcp_skill_action_copy_domain_template)
    val copyBootstrapPromptText = stringResource(R.string.mcp_skill_action_copy_bootstrap_prompt)
    val copyDiagnosticsPromptText =
        stringResource(R.string.mcp_skill_action_copy_diagnostics_prompt)
    val currentConfigSummary = stringResource(R.string.mcp_skill_copy_current_config_summary)
    val fullSkillSummary = stringResource(R.string.mcp_skill_copy_full_skill_summary)
    val subAgentResourceSummary = stringResource(R.string.mcp_skill_copy_subagent_resource_summary)
    val workflowResourceSummary = stringResource(R.string.mcp_skill_copy_workflow_resource_summary)
    val domainTemplateSummary = stringResource(R.string.mcp_skill_copy_domain_template_summary)
    val bootstrapPromptSummary = stringResource(R.string.mcp_skill_copy_bootstrap_prompt_summary)
    val diagnosticsPromptSummary =
        stringResource(R.string.mcp_skill_copy_diagnostics_prompt_summary)
    val resourceSkillTitle = stringResource(R.string.mcp_skill_resource_skill_title)
    val resourceSubAgentTitle = stringResource(R.string.mcp_skill_resource_subagent_title)
    val resourceWorkflowTitle = stringResource(R.string.mcp_skill_resource_workflow_title)
    val resourceDomainGithubTitle = stringResource(R.string.mcp_skill_resource_domain_github_title)
    val resourceDomainRuntimeTitle =
        stringResource(R.string.mcp_skill_resource_domain_runtime_title)
    val resourceToolTemplateTitle = stringResource(R.string.mcp_skill_resource_tool_template_title)
    val resourceSkillSummary = stringResource(R.string.mcp_skill_resource_skill_summary)
    val resourceSubAgentSummary = stringResource(R.string.mcp_skill_resource_subagent_summary)
    val resourceWorkflowSummary = stringResource(R.string.mcp_skill_resource_workflow_summary)
    val resourceDomainGithubSummary =
        stringResource(R.string.mcp_skill_resource_domain_github_summary)
    val resourceDomainRuntimeSummary =
        stringResource(R.string.mcp_skill_resource_domain_runtime_summary)
    val resourceToolTemplateSummary =
        stringResource(R.string.mcp_skill_resource_tool_template_summary)
    val flowConnectTitle = stringResource(R.string.mcp_skill_flow_connect_title)
    val flowConnectSummary = stringResource(R.string.mcp_skill_flow_connect_summary)
    val flowWorkflowTitle = stringResource(R.string.mcp_skill_flow_workflow_title)
    val flowWorkflowSummary = stringResource(R.string.mcp_skill_flow_workflow_summary)
    val flowDiagnosticsTitle = stringResource(R.string.mcp_skill_flow_diagnostics_title)
    val flowDiagnosticsSummary = stringResource(R.string.mcp_skill_flow_diagnostics_summary)
    val copiedToast = stringResource(R.string.mcp_skill_toast_copied)
    return remember(
        pageTitle,
        emptyMarkdown,
        emptyItemText,
        defaultRootTitle,
        defaultOverviewTitle,
        defaultContentTitle,
        emptyContentText,
        clawPrompt,
        copyClawPromptText,
        clawPromptCopiedToast,
        onboardingTitle,
        onboardingSummary,
        quickCopyTitle,
        quickCopySummary,
        resourcesTitle,
        resourcesSummary,
        flowsTitle,
        flowsSummary,
        referenceTitle,
        referenceSummary,
        expandReferenceText,
        collapseReferenceText,
        copyCurrentConfigText,
        copyFullSkillText,
        copySubAgentResourceText,
        copyWorkflowResourceText,
        copyDomainTemplateText,
        copyBootstrapPromptText,
        copyDiagnosticsPromptText,
        currentConfigSummary,
        fullSkillSummary,
        subAgentResourceSummary,
        workflowResourceSummary,
        domainTemplateSummary,
        bootstrapPromptSummary,
        diagnosticsPromptSummary,
        resourceSkillTitle,
        resourceSubAgentTitle,
        resourceWorkflowTitle,
        resourceDomainGithubTitle,
        resourceDomainRuntimeTitle,
        resourceToolTemplateTitle,
        resourceSkillSummary,
        resourceSubAgentSummary,
        resourceWorkflowSummary,
        resourceDomainGithubSummary,
        resourceDomainRuntimeSummary,
        resourceToolTemplateSummary,
        flowConnectTitle,
        flowConnectSummary,
        flowWorkflowTitle,
        flowWorkflowSummary,
        flowDiagnosticsTitle,
        flowDiagnosticsSummary,
        copiedToast
    ) {
        McpSkillPageTextBundle(
            pageTitle = pageTitle,
            emptyMarkdown = emptyMarkdown,
            emptyItemText = emptyItemText,
            defaultRootTitle = defaultRootTitle,
            defaultOverviewTitle = defaultOverviewTitle,
            defaultContentTitle = defaultContentTitle,
            emptyContentText = emptyContentText,
            clawPrompt = clawPrompt,
            copyClawPromptText = copyClawPromptText,
            clawPromptCopiedToast = clawPromptCopiedToast,
            onboardingTitle = onboardingTitle,
            onboardingSummary = onboardingSummary,
            quickCopyTitle = quickCopyTitle,
            quickCopySummary = quickCopySummary,
            resourcesTitle = resourcesTitle,
            resourcesSummary = resourcesSummary,
            flowsTitle = flowsTitle,
            flowsSummary = flowsSummary,
            referenceTitle = referenceTitle,
            referenceSummary = referenceSummary,
            expandReferenceText = expandReferenceText,
            collapseReferenceText = collapseReferenceText,
            copyCurrentConfigText = copyCurrentConfigText,
            copyFullSkillText = copyFullSkillText,
            copySubAgentResourceText = copySubAgentResourceText,
            copyWorkflowResourceText = copyWorkflowResourceText,
            copyDomainTemplateText = copyDomainTemplateText,
            copyBootstrapPromptText = copyBootstrapPromptText,
            copyDiagnosticsPromptText = copyDiagnosticsPromptText,
            currentConfigSummary = currentConfigSummary,
            fullSkillSummary = fullSkillSummary,
            subAgentResourceSummary = subAgentResourceSummary,
            workflowResourceSummary = workflowResourceSummary,
            domainTemplateSummary = domainTemplateSummary,
            bootstrapPromptSummary = bootstrapPromptSummary,
            diagnosticsPromptSummary = diagnosticsPromptSummary,
            resourceSkillTitle = resourceSkillTitle,
            resourceSubAgentTitle = resourceSubAgentTitle,
            resourceWorkflowTitle = resourceWorkflowTitle,
            resourceDomainGithubTitle = resourceDomainGithubTitle,
            resourceDomainRuntimeTitle = resourceDomainRuntimeTitle,
            resourceToolTemplateTitle = resourceToolTemplateTitle,
            resourceSkillSummary = resourceSkillSummary,
            resourceSubAgentSummary = resourceSubAgentSummary,
            resourceWorkflowSummary = resourceWorkflowSummary,
            resourceDomainGithubSummary = resourceDomainGithubSummary,
            resourceDomainRuntimeSummary = resourceDomainRuntimeSummary,
            resourceToolTemplateSummary = resourceToolTemplateSummary,
            flowConnectTitle = flowConnectTitle,
            flowConnectSummary = flowConnectSummary,
            flowWorkflowTitle = flowWorkflowTitle,
            flowWorkflowSummary = flowWorkflowSummary,
            flowDiagnosticsTitle = flowDiagnosticsTitle,
            flowDiagnosticsSummary = flowDiagnosticsSummary,
            copiedToast = copiedToast
        )
    }
}
