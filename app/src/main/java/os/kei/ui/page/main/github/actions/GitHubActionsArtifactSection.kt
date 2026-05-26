@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.actions

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.model.GitHubActionsArtifactMatch
import os.kei.feature.github.model.GitHubActionsLookupStrategyOption
import os.kei.feature.github.model.GitHubActionsRunMatch
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.supportsManagedApkInstall
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.page.GitHubActionsArtifactFilter
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubActionsArtifactsSection(
    lookupConfig: GitHubLookupConfig,
    expanded: Boolean,
    selectedArtifactFilter: GitHubActionsArtifactFilter,
    downloadingArtifactId: Long?,
    sharingArtifactId: Long?,
    selectedRun: GitHubActionsRunMatch?,
    selectedRunArtifactsLoading: Boolean,
    canResolveArtifacts: Boolean,
    visibleArtifactMatches: List<GitHubActionsVisibleArtifactMatch>,
    recommendedArtifactCount: Int,
    alternativesArtifactCount: Int,
    isDark: Boolean,
    backdrop: LayerBackdrop,
    onExpandedChange: (Boolean) -> Unit,
    onArtifactFilterChange: (GitHubActionsArtifactFilter) -> Unit,
    onInstallArtifact: (Long, Long) -> Unit,
    onDownloadArtifact: (Long, Long) -> Unit,
    onShareArtifact: (Long, Long) -> Unit,
    onOpenArtifactDetail: (GitHubActionsRunMatch, GitHubActionsArtifactMatch, Boolean) -> Unit,
    context: Context,
) {
    val nightlyLink = lookupConfig.actionsStrategy == GitHubActionsLookupStrategyOption.NightlyLink
    val emptyArtifactsText =
        if (nightlyLink) {
            stringResource(R.string.github_actions_empty_artifacts_nightly)
        } else {
            stringResource(R.string.github_actions_empty_artifacts)
        }
    GitHubActionsCollapsibleSection(
        title = stringResource(R.string.github_actions_section_artifacts),
        summary = artifactSectionSummary(selectedRun),
        countLabel =
            stringResource(
                R.string.github_actions_value_count,
                selectedRun?.artifactMatches?.size ?: 0,
            ),
        expanded = expanded,
        isDark = isDark,
        onExpandedChange = onExpandedChange,
    ) {
        when {
            selectedRun == null -> {
                GitHubActionsNoticeCard(
                    text = emptyArtifactsText,
                    accent = MiuixTheme.colorScheme.onBackgroundVariant,
                    isDark = isDark,
                )
            }

            selectedRun.traits.inProgress -> {
                GitHubActionsNoticeCard(
                    text = stringResource(R.string.github_actions_hint_run_in_progress),
                    accent = GitHubStatusPalette.Active,
                    isDark = isDark,
                )
            }

            selectedRunArtifactsLoading -> {
                GitHubActionsLoadingCard(
                    text = stringResource(R.string.github_actions_loading_artifacts),
                )
            }

            selectedRun.artifactMatches.isEmpty() -> {
                GitHubActionsNoticeCard(
                    text = emptyArtifactsText,
                    accent = MiuixTheme.colorScheme.onBackgroundVariant,
                    isDark = isDark,
                )
            }

            else -> {
                val relativeTimeNowMillis =
                    remember(selectedRun.runArtifacts, visibleArtifactMatches) {
                        System.currentTimeMillis()
                    }
                if (!canResolveArtifacts) {
                    GitHubActionsArtifactHintText(
                        text = stringResource(R.string.github_actions_hint_token_required),
                    )
                }
                GitHubActionsArtifactFilterRow(
                    selected = selectedArtifactFilter,
                    recommendedCount = recommendedArtifactCount,
                    alternativesCount = alternativesArtifactCount,
                    onSelectedChange = onArtifactFilterChange,
                )
                if (visibleArtifactMatches.isEmpty()) {
                    GitHubActionsNoticeCard(
                        text = stringResource(R.string.github_actions_empty_artifacts_filter),
                        accent = MiuixTheme.colorScheme.onBackgroundVariant,
                        isDark = isDark,
                    )
                }
                visibleArtifactMatches.forEach { visibleMatch ->
                    val artifactMatch = visibleMatch.match
                    GitHubActionsArtifactCard(
                        runMatch = selectedRun,
                        artifactMatch = artifactMatch,
                        recommended = visibleMatch.recommended,
                        canShareArtifact = canResolveArtifacts,
                        managedInstallEnabled = artifactMatch.supportsManagedApkInstall(lookupConfig),
                        relativeTimeNowMillis = relativeTimeNowMillis,
                        downloading = downloadingArtifactId == artifactMatch.artifact.id,
                        sharing = sharingArtifactId == artifactMatch.artifact.id,
                        context = context,
                        isDark = isDark,
                        backdrop = backdrop,
                        onInstall = {
                            onInstallArtifact(
                                selectedRun.runArtifacts.run.id,
                                artifactMatch.artifact.id,
                            )
                        },
                        onDownload = {
                            onDownloadArtifact(
                                selectedRun.runArtifacts.run.id,
                                artifactMatch.artifact.id,
                            )
                        },
                        onShare = {
                            onShareArtifact(
                                selectedRun.runArtifacts.run.id,
                                artifactMatch.artifact.id,
                            )
                        },
                        onOpenDetail = {
                            onOpenArtifactDetail(
                                selectedRun,
                                artifactMatch,
                                visibleMatch.recommended,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GitHubActionsArtifactFilterRow(
    selected: GitHubActionsArtifactFilter,
    recommendedCount: Int,
    alternativesCount: Int,
    onSelectedChange: (GitHubActionsArtifactFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GitHubActionsArtifactFilter.entries.forEach { filter ->
            AppLiquidTextButton(
                backdrop = null,
                variant = if (selected == filter) GlassVariant.SheetAction else GlassVariant.Content,
                text =
                    when (filter) {
                        GitHubActionsArtifactFilter.Recommended -> {
                            stringResource(
                                R.string.github_actions_artifact_filter_recommended,
                                recommendedCount,
                            )
                        }

                        GitHubActionsArtifactFilter.Alternatives -> {
                            stringResource(
                                R.string.github_actions_artifact_filter_alternatives,
                                alternativesCount,
                            )
                        }

                        GitHubActionsArtifactFilter.All -> {
                            stringResource(
                                R.string.github_actions_artifact_filter_all,
                                recommendedCount + alternativesCount,
                            )
                        }
                    },
                onClick = { onSelectedChange(filter) },
                modifier = Modifier.weight(1f),
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis,
            )
        }
    }
}
