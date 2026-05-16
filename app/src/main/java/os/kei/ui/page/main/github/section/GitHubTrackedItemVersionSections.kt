package os.kei.ui.page.main.github.section

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.formatApkVersionValue
import os.kei.ui.page.main.github.formatReleaseMetaValue
import os.kei.ui.page.main.github.githubPreReleaseLinkUrl
import os.kei.ui.page.main.github.githubStableReleaseLinkUrl
import os.kei.ui.page.main.github.isLocalAppUninstalled
import os.kei.ui.page.main.github.stableVersionColor
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.motion.appExpandIn
import os.kei.ui.page.main.widget.motion.appExpandOut
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Suppress("FunctionName")
@Composable
internal fun GitHubTrackedItemVersionSections(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    itemLookupConfig: GitHubLookupConfig,
    appUpdatedAtLabel: String,
    checkState: GitHubTrackedItemsCheckState,
    expansionState: GitHubTrackedItemsExpansionState,
    actions: GitHubTrackedItemsActions,
    context: Context,
) {
    GitHubTrackedItemLocalVersionSection(
        item = item,
        state = state,
        appUpdatedAtLabel = appUpdatedAtLabel,
        checkState = checkState,
        expansionState = expansionState,
        actions = actions,
        context = context,
    )
    GitHubTrackedItemStableVersionSection(
        item = item,
        state = state,
        itemLookupConfig = itemLookupConfig,
        expansionState = expansionState,
        actions = actions,
    )
    GitHubTrackedItemPreReleaseVersionSection(
        item = item,
        state = state,
        itemLookupConfig = itemLookupConfig,
        expansionState = expansionState,
        actions = actions,
    )
}

@Suppress("FunctionName")
@Composable
private fun GitHubTrackedItemLocalVersionSection(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    appUpdatedAtLabel: String,
    checkState: GitHubTrackedItemsCheckState,
    expansionState: GitHubTrackedItemsExpansionState,
    actions: GitHubTrackedItemsActions,
    context: Context,
) {
    val localText = formatLocalVersionText(context, checkState.checkStates[item.id])
    if (localText == null) return
    val localVersionColor =
        if (state.isLocalAppUninstalled()) {
            MiuixTheme.colorScheme.onBackgroundVariant
        } else {
            MiuixTheme.colorScheme.primary
        }
    val localVersionExpanded = expansionState.trackedLocalVersionExpanded[item.id] == true
    GitHubReleaseVersionCard(
        label = stringResource(R.string.github_item_label_local_version),
        value = localText,
        textColor = localVersionColor,
        expanded = localVersionExpanded,
        emphasized = !state.isLocalAppUninstalled(),
        valueMaxLines = 2,
        onExpandedChange = { expanded ->
            actions.onLocalVersionExpandedChange(item.id, expanded)
        },
    )
    AnimatedVisibility(
        visible = localVersionExpanded,
        enter = appExpandIn(),
        exit = appExpandOut(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap),
        ) {
            GitHubLinkedInfoCard(
                label = stringResource(R.string.github_item_label_updated_at),
                value = appUpdatedAtLabel,
                valueColor = GitHubStatusPalette.Active,
                onClick = {
                    actions.onLocalVersionExpandedChange(item.id, false)
                },
            )
            if (item.checkActionsUpdates) {
                GitHubLinkedInfoCard(
                    label = stringResource(R.string.github_item_label_actions_run),
                    value = formatActionsRunSnapshotValue(checkState.actionsRecommendedRunSnapshots[item.id]),
                    valueColor = GitHubStatusPalette.Cache,
                    valueMaxLines = 2,
                    onClick = {
                        actions.onOpenActionsSheet(item)
                    },
                )
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun GitHubTrackedItemStableVersionSection(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    itemLookupConfig: GitHubLookupConfig,
    expansionState: GitHubTrackedItemsExpansionState,
    actions: GitHubTrackedItemsActions,
) {
    val showStableRelease =
        state.hasStableRelease &&
            (
                state.latestStableName.isNotBlank() ||
                    state.latestStableRawTag.isNotBlank() ||
                    state.latestTag.isNotBlank()
            )
    if (!showStableRelease) return
    val latestColor =
        state.stableVersionColor(
            neutralColor = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    val stableReleaseMeta =
        formatReleaseMetaValue(
            preciseInfo =
                state.latestStableApkVersion.takeIf {
                    itemLookupConfig.preciseApkVersionEnabled
                },
            releaseName = state.latestStableName.ifBlank { state.latestTag },
            rawTag = state.latestStableRawTag,
        )
    val stableExpanded = expansionState.trackedStableVersionExpanded[item.id] == true
    GitHubReleaseVersionCard(
        label =
            stringResource(
                if (item.isDirectApkTrack()) {
                    R.string.github_item_label_remote_stable_version
                } else {
                    R.string.github_item_label_stable_version
                },
            ),
        value =
            formatApkVersionValue(
                preciseInfo =
                    state.latestStableApkVersion.takeIf {
                        itemLookupConfig.preciseApkVersionEnabled
                    },
                releaseName = state.latestStableName.ifBlank { state.latestTag },
                rawTag = state.latestStableRawTag,
            ),
        textColor = latestColor,
        expanded = stableExpanded,
        emphasized = state.hasUpdate == true && !state.recommendsPreRelease,
        valueMaxLines = 2,
        onExpandedChange = { expanded ->
            actions.onStableVersionExpandedChange(item.id, expanded)
        },
    )
    AnimatedVisibility(
        visible = stableExpanded && stableReleaseMeta.isNotBlank(),
        enter = appExpandIn(),
        exit = appExpandOut(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap),
        ) {
            GitHubLinkedInfoCard(
                label =
                    stringResource(
                        if (item.isDirectApkTrack()) {
                            R.string.github_item_label_remote_stable_release
                        } else {
                            R.string.github_item_label_stable_release
                        },
                    ),
                value = stableReleaseMeta,
                valueColor = MiuixTheme.colorScheme.primary,
                valueMaxLines = 2,
                onClick = {
                    actions.onOpenExternalUrl(
                        state.githubStableReleaseLinkUrl(
                            item.owner,
                            item.repo,
                        ),
                    )
                },
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun GitHubTrackedItemPreReleaseVersionSection(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    itemLookupConfig: GitHubLookupConfig,
    expansionState: GitHubTrackedItemsExpansionState,
    actions: GitHubTrackedItemsActions,
) {
    val showPreRelease =
        state.showPreReleaseInfo &&
            (
                state.latestPreName.isNotBlank() ||
                    state.latestPreRawTag.isNotBlank() ||
                    state.preReleaseInfo.isNotBlank()
            )
    if (!showPreRelease) return
    val preReleaseCardTextColor = gitHubPreReleaseCardTextColor()
    val preReleaseMeta =
        formatReleaseMetaValue(
            preciseInfo =
                state.latestPreApkVersion.takeIf {
                    itemLookupConfig.preciseApkVersionEnabled
                },
            releaseName = state.latestPreName.ifBlank { state.preReleaseInfo },
            rawTag = state.latestPreRawTag,
        )
    val preExpanded = expansionState.trackedPreReleaseVersionExpanded[item.id] == true
    GitHubReleaseVersionCard(
        label =
            stringResource(
                if (item.isDirectApkTrack()) {
                    R.string.github_item_label_remote_prerelease_version
                } else {
                    R.string.github_item_label_prerelease_version
                },
            ),
        value =
            formatApkVersionValue(
                preciseInfo =
                    state.latestPreApkVersion.takeIf {
                        itemLookupConfig.preciseApkVersionEnabled
                    },
                releaseName = state.latestPreName.ifBlank { state.preReleaseInfo },
                rawTag = state.latestPreRawTag,
            ),
        textColor = preReleaseCardTextColor,
        expanded = preExpanded,
        emphasized = state.recommendsPreRelease || state.hasPreReleaseUpdate,
        valueMaxLines = 2,
        onExpandedChange = { expanded ->
            actions.onPreReleaseVersionExpandedChange(item.id, expanded)
        },
    )
    AnimatedVisibility(
        visible = preExpanded && preReleaseMeta.isNotBlank(),
        enter = appExpandIn(),
        exit = appExpandOut(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap),
        ) {
            GitHubLinkedInfoCard(
                label =
                    stringResource(
                        if (item.isDirectApkTrack()) {
                            R.string.github_item_label_remote_prerelease_release
                        } else {
                            R.string.github_item_label_prerelease_release
                        },
                    ),
                value = preReleaseMeta,
                labelColor = preReleaseCardTextColor,
                valueColor = preReleaseCardTextColor,
                valueMaxLines = 2,
                onClick = {
                    actions.onOpenExternalUrl(
                        state.githubPreReleaseLinkUrl(item.owner, item.repo),
                    )
                },
            )
        }
    }
}
