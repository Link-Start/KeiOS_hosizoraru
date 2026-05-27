@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp.skill.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import os.kei.ui.page.main.mcp.skill.state.McpSkillPageTextBundle
import os.kei.ui.page.main.os.appLucideBranchIcon
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.os.appLucideListIcon
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideWarningIcon
import os.kei.ui.page.main.os.osLucideCopyIcon
import os.kei.ui.page.main.widget.core.AppControlRow
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Immutable
internal data class McpSkillCopyAction(
    val title: String,
    val summary: String,
    val payload: String,
    val label: String,
    val icon: ImageVector,
)

@Immutable
internal data class McpSkillResourceAction(
    val title: String,
    val summary: String,
    val payload: String,
)

@Composable
internal fun McpSkillOnboardingCard(
    textBundle: McpSkillPageTextBundle,
    titleColor: Color,
    subtitleColor: Color,
    accentColor: Color,
    onCopyClawPrompt: () -> Unit,
    onCopyCurrentConfig: () -> Unit,
) {
    AppSurfaceCard(
        contentColor = titleColor,
        showIndication = false,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(CardLayoutRhythm.cardContentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = textBundle.onboardingTitle,
                color = titleColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                lineHeight = 25.sp,
            )
            Text(
                text = textBundle.onboardingSummary,
                color = subtitleColor,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            AppDualActionRow(
                spacing = CardLayoutRhythm.infoRowGap,
                first = { modifier ->
                    SkillCopyButton(
                        text = textBundle.copyClawPromptText,
                        icon = osLucideCopyIcon(),
                        color = accentColor,
                        modifier = modifier,
                        onClick = onCopyClawPrompt,
                    )
                },
                second = { modifier ->
                    SkillCopyButton(
                        text = textBundle.copyCurrentConfigText,
                        icon = appLucideConfigIcon(),
                        color = accentColor,
                        modifier = modifier,
                        onClick = onCopyCurrentConfig,
                    )
                },
            )
            Text(
                text = "${textBundle.copyCurrentConfigText}: ${textBundle.currentConfigSummary}",
                color = subtitleColor,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
internal fun McpSkillQuickCopyCard(
    textBundle: McpSkillPageTextBundle,
    actions: List<McpSkillCopyAction>,
    titleColor: Color,
    subtitleColor: Color,
    accentColor: Color,
    onCopy: (McpSkillCopyAction) -> Unit,
) {
    AppSurfaceCard(
        contentColor = titleColor,
        showIndication = false,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(CardLayoutRhythm.cardContentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkillCardHeader(
                title = textBundle.quickCopyTitle,
                summary = textBundle.quickCopySummary,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
            )
            actions.chunked(2).forEach { rowActions ->
                AppDualActionRow(
                    spacing = CardLayoutRhythm.infoRowGap,
                    first = { modifier ->
                        SkillCopyButton(
                            text = rowActions[0].title,
                            icon = rowActions[0].icon,
                            color = accentColor,
                            modifier = modifier,
                            onClick = { onCopy(rowActions[0]) },
                        )
                    },
                    second = { modifier ->
                        rowActions.getOrNull(1)?.let { action ->
                            SkillCopyButton(
                                text = action.title,
                                icon = action.icon,
                                color = accentColor,
                                modifier = modifier,
                                onClick = { onCopy(action) },
                            )
                        }
                    },
                )
            }
            actions.take(3).forEach { action ->
                Text(
                    text = "${action.title}: ${action.summary}",
                    color = subtitleColor,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
internal fun McpSkillResourcesCard(
    textBundle: McpSkillPageTextBundle,
    resources: List<McpSkillResourceAction>,
    titleColor: Color,
    subtitleColor: Color,
    accentColor: Color,
    onCopy: (McpSkillResourceAction) -> Unit,
) {
    AppSurfaceCard(
        contentColor = titleColor,
        showIndication = false,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(CardLayoutRhythm.cardContentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkillCardHeader(
                title = textBundle.resourcesTitle,
                summary = textBundle.resourcesSummary,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
            )
            resources.forEach { resource ->
                AppControlRow(
                    title = resource.title,
                    summary = "${resource.summary}\n${resource.payload}",
                    titleColor = titleColor,
                    summaryColor = subtitleColor,
                    trailing = {
                        SkillTinyCopyButton(
                            contentDescription = resource.title,
                            accentColor = accentColor,
                            onClick = { onCopy(resource) },
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun McpSkillFlowsCard(
    textBundle: McpSkillPageTextBundle,
    titleColor: Color,
    subtitleColor: Color,
) {
    AppSurfaceCard(
        contentColor = titleColor,
        showIndication = false,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(CardLayoutRhythm.cardContentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkillCardHeader(
                title = textBundle.flowsTitle,
                summary = textBundle.flowsSummary,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
            )
            SkillFlowRow(
                icon = appLucideRefreshIcon(),
                title = textBundle.flowConnectTitle,
                summary = textBundle.flowConnectSummary,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
            )
            SkillFlowRow(
                icon = appLucideBranchIcon(),
                title = textBundle.flowWorkflowTitle,
                summary = textBundle.flowWorkflowSummary,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
            )
            SkillFlowRow(
                icon = appLucideWarningIcon(),
                title = textBundle.flowDiagnosticsTitle,
                summary = textBundle.flowDiagnosticsSummary,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
            )
        }
    }
}

@Composable
internal fun McpSkillReferenceCard(
    textBundle: McpSkillPageTextBundle,
    expanded: Boolean,
    titleColor: Color,
    subtitleColor: Color,
    accentColor: Color,
    onExpandedChange: (Boolean) -> Unit,
) {
    AppSurfaceCard(
        contentColor = titleColor,
        showIndication = true,
        onClick = { onExpandedChange(!expanded) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(CardLayoutRhythm.cardContentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkillCardHeader(
                title = textBundle.referenceTitle,
                summary = textBundle.referenceSummary,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
            )
            AppLiquidTextButton(
                backdrop = null,
                variant = GlassVariant.Compact,
                text =
                    if (expanded) {
                        textBundle.collapseReferenceText
                    } else {
                        textBundle.expandReferenceText
                    },
                textColor = accentColor,
                leadingIcon = if (expanded) appLucideListIcon() else appLucideNotesIcon(),
                onClick = { onExpandedChange(!expanded) },
            )
        }
    }
}

@Composable
private fun SkillCardHeader(
    title: String,
    summary: String,
    titleColor: Color,
    subtitleColor: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = titleColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 23.sp,
        )
        Text(
            text = summary,
            color = subtitleColor,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun SkillFlowRow(
    icon: ImageVector,
    title: String,
    summary: String,
    titleColor: Color,
    subtitleColor: Color,
) {
    AppControlRow(
        title = title,
        summary = summary,
        titleColor = titleColor,
        summaryColor = subtitleColor,
        trailing = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = subtitleColor,
            )
        },
    )
}

@Composable
private fun SkillCopyButton(
    text: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppLiquidTextButton(
        backdrop = null,
        variant = GlassVariant.Compact,
        text = text,
        textColor = color,
        leadingIcon = icon,
        modifier = modifier,
        textMaxLines = 2,
        onClick = onClick,
    )
}

@Composable
private fun SkillTinyCopyButton(
    contentDescription: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    AppLiquidIconButton(
        backdrop = null,
        variant = GlassVariant.Compact,
        icon = osLucideCopyIcon(),
        contentDescription = contentDescription,
        width = 34.dp,
        height = 30.dp,
        iconTint = accentColor,
        onClick = onClick,
    )
}

internal fun buildMcpSkillCopyActions(
    textBundle: McpSkillPageTextBundle,
    markdown: String,
    workflowResourceUri: String,
    domainTemplateUri: String,
    bootstrapPrompt: String,
    diagnosticsPrompt: String,
    notesIcon: ImageVector,
    workflowIcon: ImageVector,
    domainIcon: ImageVector,
    bootstrapIcon: ImageVector,
    diagnosticsIcon: ImageVector,
): List<McpSkillCopyAction> =
    listOf(
        McpSkillCopyAction(
            title = textBundle.copyFullSkillText,
            summary = textBundle.fullSkillSummary,
            payload = markdown,
            label = "mcp-skill-markdown",
            icon = notesIcon,
        ),
        McpSkillCopyAction(
            title = textBundle.copyWorkflowResourceText,
            summary = textBundle.workflowResourceSummary,
            payload = workflowResourceUri,
            label = "mcp-workflow-resource",
            icon = workflowIcon,
        ),
        McpSkillCopyAction(
            title = textBundle.copyDomainTemplateText,
            summary = textBundle.domainTemplateSummary,
            payload = domainTemplateUri,
            label = "mcp-domain-template",
            icon = domainIcon,
        ),
        McpSkillCopyAction(
            title = textBundle.copyBootstrapPromptText,
            summary = textBundle.bootstrapPromptSummary,
            payload = bootstrapPrompt,
            label = "mcp-bootstrap-prompt",
            icon = bootstrapIcon,
        ),
        McpSkillCopyAction(
            title = textBundle.copyDiagnosticsPromptText,
            summary = textBundle.diagnosticsPromptSummary,
            payload = diagnosticsPrompt,
            label = "mcp-diagnostics-prompt",
            icon = diagnosticsIcon,
        ),
    )

internal fun buildMcpSkillResourceActions(
    textBundle: McpSkillPageTextBundle,
    skillResourceUri: String,
    workflowResourceUri: String,
    domainTemplateUri: String,
    toolTemplateUri: String,
): List<McpSkillResourceAction> =
    listOf(
        McpSkillResourceAction(
            title = textBundle.resourceSkillTitle,
            summary = textBundle.resourceSkillSummary,
            payload = skillResourceUri,
        ),
        McpSkillResourceAction(
            title = textBundle.resourceWorkflowTitle,
            summary = textBundle.resourceWorkflowSummary,
            payload = workflowResourceUri,
        ),
        McpSkillResourceAction(
            title = textBundle.resourceDomainGithubTitle,
            summary = textBundle.resourceDomainGithubSummary,
            payload = domainTemplateUri.replace("{domain}", "github"),
        ),
        McpSkillResourceAction(
            title = textBundle.resourceDomainRuntimeTitle,
            summary = textBundle.resourceDomainRuntimeSummary,
            payload = domainTemplateUri.replace("{domain}", "runtime"),
        ),
        McpSkillResourceAction(
            title = textBundle.resourceToolTemplateTitle,
            summary = textBundle.resourceToolTemplateSummary,
            payload = toolTemplateUri,
        ),
    )
