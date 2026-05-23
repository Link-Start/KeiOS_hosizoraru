@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.sheet

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.intent.SafeExternalIntents
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.page.GitHubDecisionAssistDetailRequest
import os.kei.ui.page.main.github.page.GitHubDecisionAssistDetailType
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideShareIcon
import os.kei.ui.page.main.os.osLucideCopyIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.markdown.AppMarkdownBlocksContent
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.support.LocalTextCopyExpandedOverride
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubDecisionAssistDetailSheet(
    request: GitHubDecisionAssistDetailRequest?,
    backdrop: LayerBackdrop,
    versionState: VersionCheckUi,
    assetBundle: GitHubReleaseAssetBundle?,
    releaseNotesTargets: List<GitHubReleaseNotesTarget> = emptyList(),
    selectedReleaseNotesTarget: GitHubReleaseNotesTarget? = null,
    releaseNotesApkVersion: GitHubRemoteApkVersionInfo? = null,
    releaseNotesDetailState: GitHubReleaseNotesDetailUiState = GitHubReleaseNotesDetailUiState(),
    preciseApkVersionEnabled: Boolean = false,
    assetLoading: Boolean,
    assetError: String,
    healthRefreshing: Boolean = false,
    onDismissRequest: () -> Unit,
    onRefreshHealth: (GitHubTrackedApp) -> Unit,
    onRefreshReleaseNotes: (GitHubTrackedApp, VersionCheckUi) -> Unit,
    onSelectReleaseNotesTarget: (GitHubTrackedApp, GitHubReleaseNotesTarget) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
    val detail = request ?: return
    val title =
        when (detail.type) {
            GitHubDecisionAssistDetailType.RepositoryHealth -> R.string.github_health_detail_title
            GitHubDecisionAssistDetailType.ReleaseNotes -> R.string.github_release_notes_detail_title
        }
    SnapshotWindowBottomSheet(
        show = true,
        title = stringResource(title),
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismissRequest,
            )
        },
        endAction = {
            val refreshing =
                when (detail.type) {
                    GitHubDecisionAssistDetailType.RepositoryHealth -> healthRefreshing
                    GitHubDecisionAssistDetailType.ReleaseNotes -> assetLoading
                }
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideRefreshIcon(),
                contentDescription =
                    if (refreshing) {
                        stringResource(R.string.common_loading)
                    } else {
                        stringResource(R.string.common_refresh)
                    },
                enabled = !refreshing,
                onClick = {
                    when (detail.type) {
                        GitHubDecisionAssistDetailType.RepositoryHealth -> {
                            onRefreshHealth(detail.item)
                        }

                        GitHubDecisionAssistDetailType.ReleaseNotes -> {
                            onRefreshReleaseNotes(detail.item, versionState)
                        }
                    }
                },
            )
        },
    ) {
        when (detail.type) {
            GitHubDecisionAssistDetailType.RepositoryHealth -> {
                GitHubHealthDetailContent(
                    item = detail.item,
                    state = versionState,
                    refreshing = healthRefreshing,
                )
            }

            GitHubDecisionAssistDetailType.ReleaseNotes -> {
                GitHubReleaseNotesDetailContent(
                    backdrop = backdrop,
                    item = detail.item,
                    state = versionState,
                    assetBundle = assetBundle,
                    releaseNotesTargets = releaseNotesTargets,
                    selectedReleaseNotesTarget = selectedReleaseNotesTarget,
                    releaseNotesApkVersion = releaseNotesApkVersion,
                    releaseNotesDetailState = releaseNotesDetailState,
                    preciseApkVersionEnabled = preciseApkVersionEnabled,
                    assetLoading = assetLoading,
                    assetError = assetError,
                    onSelectReleaseNotesTarget = { target ->
                        onSelectReleaseNotesTarget(detail.item, target)
                    },
                    onOpenExternalUrl = onOpenExternalUrl,
                )
            }
        }
    }
}

@Composable
private fun GitHubReleaseNotesDetailContent(
    backdrop: LayerBackdrop,
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    assetBundle: GitHubReleaseAssetBundle?,
    releaseNotesTargets: List<GitHubReleaseNotesTarget>,
    selectedReleaseNotesTarget: GitHubReleaseNotesTarget?,
    releaseNotesApkVersion: GitHubRemoteApkVersionInfo?,
    releaseNotesDetailState: GitHubReleaseNotesDetailUiState,
    preciseApkVersionEnabled: Boolean,
    assetLoading: Boolean,
    assetError: String,
    onSelectReleaseNotesTarget: (GitHubReleaseNotesTarget) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    val lines = releaseNotesDetailState.lines
    val rawMarkdown = releaseNotesDetailState.rawMarkdown
    var releaseDropdownExpanded by remember { mutableStateOf(false) }
    var releaseDropdownAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    val selectedTarget =
        selectedReleaseNotesTarget
            ?: releaseNotesTargets.firstOrNull()
    val selectedApkVersionLabel =
        releaseNotesSelectedApkVersionLabel(
            state = state,
            assetBundle = assetBundle,
            selectedTarget = selectedTarget,
            releaseNotesApkVersion = releaseNotesApkVersion,
            preciseApkVersionEnabled = preciseApkVersionEnabled,
        )
    val selectedIndex =
        releaseNotesTargets
            .indexOfFirst { it.id == selectedTarget?.id }
            .coerceAtLeast(0)
    val stableMarker = stringResource(R.string.github_release_notes_marker_stable)
    val prereleaseMarker = stringResource(R.string.github_release_notes_marker_prerelease)
    val latestMarker = stringResource(R.string.github_release_notes_marker_latest)
    val releaseOptions =
        releaseNotesTargets.map { target ->
            releaseNotesTargetDropdownLabel(
                target = target,
                stableMarker = stableMarker,
                prereleaseMarker = prereleaseMarker,
                latestMarker = latestMarker,
            )
        }
    SheetContentColumn(verticalSpacing = 10.dp) {
        SheetSummaryCard(
            title =
                selectedTarget?.releaseName?.takeIf { it.isNotBlank() }
                    ?: assetBundle?.releaseName?.takeIf { it.isNotBlank() }
                    ?: state.latestStableName.ifBlank { state.latestPreName.ifBlank { item.repo } },
            badgeLabel =
                assetBundle?.tagName?.takeIf { it.isNotBlank() }
                    ?: selectedTarget?.tagName?.takeIf { it.isNotBlank() }
                    ?: state.latestStableRawTag.ifBlank { state.latestPreRawTag.ifBlank { null } },
            badgeColor = GitHubStatusPalette.Active,
            titleMaxLines = 2,
            titleOverflow = TextOverflow.Clip,
            titleFontSize = AppTypographyTokens.Body.fontSize,
            titleLineHeight = AppTypographyTokens.Body.lineHeight,
        ) {
            DetailInfoRow(
                label = stringResource(R.string.github_release_notes_detail_release),
                value =
                    selectedTarget?.let {
                        releaseNotesTargetSheetLabel(
                            target = it,
                            stableMarker = stableMarker,
                            prereleaseMarker = prereleaseMarker,
                            latestMarker = latestMarker,
                        )
                    }
                        ?: stringResource(R.string.github_release_notes_detail_target_latest),
                valueMaxLines = Int.MAX_VALUE,
            )
            if (selectedApkVersionLabel.isNotBlank()) {
                DetailInfoRow(
                    label = stringResource(R.string.github_apk_info_label_version),
                    value = selectedApkVersionLabel,
                    valueMaxLines = Int.MAX_VALUE,
                )
            }
            DetailInfoRow(
                label = stringResource(R.string.github_release_notes_detail_repo),
                value = "${item.owner}/${item.repo}",
            )
            DetailInfoRow(
                label = stringResource(R.string.github_release_notes_detail_source),
                value = releaseNotesSourceLabel(assetBundle?.fetchSource.orEmpty()),
            )
            when {
                assetError.isNotBlank() -> {
                    GitHubDecisionDetailTextLine(assetError)
                }

                assetLoading -> {
                    GitHubDecisionDetailTextLine(
                        stringResource(R.string.github_release_notes_detail_refreshing),
                    )
                }
            }
        }
        if (releaseNotesTargets.isNotEmpty()) {
            AppDropdownSelector(
                selectedText =
                    releaseOptions.getOrElse(selectedIndex) {
                        stringResource(R.string.github_release_notes_detail_target_latest)
                    },
                options = releaseOptions,
                selectedIndex = selectedIndex,
                expanded = releaseDropdownExpanded,
                anchorBounds = releaseDropdownAnchorBounds,
                onExpandedChange = { releaseDropdownExpanded = it },
                onSelectedIndexChange = { index ->
                    releaseNotesTargets.getOrNull(index)?.let(onSelectReleaseNotesTarget)
                },
                onAnchorBoundsChange = { releaseDropdownAnchorBounds = it },
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
                anchorFillMaxWidth = true,
                anchorTextMaxLines = 3,
                anchorTextOverflow = TextOverflow.Clip,
                anchorTextSoftWrap = true,
                anchorTextSize = AppTypographyTokens.Supporting.fontSize,
                anchorTextLineHeight = AppTypographyTokens.Supporting.lineHeight,
                dropdownItemTextMaxLines = 4,
                popupMaxWidth = null,
                popupMatchAnchorWidth = true,
                alignment = PopupPositionProvider.Align.BottomStart,
            )
        }
        val translateLabel = stringResource(R.string.github_release_notes_action_translate)
        val translateFailed = stringResource(R.string.github_release_notes_translate_failed)
        val copyLabel = stringResource(R.string.common_copy)
        val copiedToast = stringResource(R.string.github_release_notes_toast_copied)
        val translatePayload =
            releaseNotesTranslationPayload(
                title =
                    selectedTarget?.releaseName?.takeIf { it.isNotBlank() }
                        ?: assetBundle?.releaseName.orEmpty(),
                tag =
                    selectedTarget?.tagName?.takeIf { it.isNotBlank() }
                        ?: assetBundle?.tagName.orEmpty(),
                rawMarkdown = rawMarkdown,
                fallbackLines = lines,
            )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetSectionTitle(
                text = stringResource(R.string.github_release_notes_detail_body_title),
                modifier = Modifier.weight(1f),
            )
            AppLiquidTextButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                text = copyLabel,
                leadingIcon = osLucideCopyIcon(),
                enabled = translatePayload.isNotBlank(),
                minHeight = 32.dp,
                horizontalPadding = 10.dp,
                verticalPadding = 4.dp,
                textSize = AppTypographyTokens.Supporting.fontSize,
                textLineHeight = AppTypographyTokens.Supporting.lineHeight,
                onClick = {
                    copyTextToClipboard(
                        context = context,
                        label = "github_release_notes_markdown",
                        text = translatePayload,
                    )
                    context.showToast(copiedToast)
                },
            )
            AppLiquidTextButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                text = translateLabel,
                leadingIcon = appLucideShareIcon(),
                enabled = translatePayload.isNotBlank(),
                minHeight = 32.dp,
                horizontalPadding = 10.dp,
                verticalPadding = 4.dp,
                textSize = AppTypographyTokens.Supporting.fontSize,
                textLineHeight = AppTypographyTokens.Supporting.lineHeight,
                onClick = {
                    val launched =
                        launchReleaseNotesTranslation(
                            context = context,
                            text = translatePayload,
                            chooserTitle = translateLabel,
                        )
                    if (!launched) {
                        context.showToast(translateFailed)
                    }
                },
            )
        }
        SheetSectionCard(
            containerColor = releaseNotesBodyContainerColor(),
            borderColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.18f),
            verticalSpacing = 10.dp,
        ) {
            if (rawMarkdown.isNotBlank()) {
                CompositionLocalProvider(LocalTextCopyExpandedOverride provides true) {
                    AppMarkdownBlocksContent(
                        blocks = releaseNotesDetailState.markdownBlocks,
                        titleColor = MiuixTheme.colorScheme.onBackground,
                        subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant,
                        accentColor = MiuixTheme.colorScheme.primary,
                        codeContainerColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.10f),
                        onOpenLink = onOpenExternalUrl,
                    )
                }
            } else if (lines.isEmpty()) {
                SheetDescriptionText(stringResource(R.string.github_release_notes_detail_empty))
            } else {
                CompositionLocalProvider(LocalTextCopyExpandedOverride provides true) {
                    lines.forEachIndexed { index, line ->
                        GitHubDecisionDetailTextLine(
                            text = if (index == 0) line else "• $line",
                            maxLines = Int.MAX_VALUE,
                            accent = index == 0,
                        )
                    }
                }
            }
        }
    }
}

private fun releaseNotesSelectedApkVersionLabel(
    state: VersionCheckUi,
    assetBundle: GitHubReleaseAssetBundle?,
    selectedTarget: GitHubReleaseNotesTarget?,
    releaseNotesApkVersion: GitHubRemoteApkVersionInfo?,
    preciseApkVersionEnabled: Boolean,
): String {
    if (!preciseApkVersionEnabled) return ""
    releaseNotesApkVersion?.versionLabel()?.takeIf { it.isNotBlank() }?.let { return it }
    val selectedTag =
        selectedTarget
            ?.tagName
            ?.trim()
            .orEmpty()
            .ifBlank { assetBundle?.tagName?.trim().orEmpty() }
    val selectedUrl =
        selectedTarget
            ?.htmlUrl
            ?.trim()
            .orEmpty()
            .ifBlank { assetBundle?.htmlUrl?.trim().orEmpty() }
    val selectedIsPreRelease =
        selectedTarget?.prerelease
            ?: selectedTag.equals(state.latestPreRawTag, ignoreCase = true)
    val versionInfo =
        when {
            selectedIsPreRelease &&
                releaseNotesTargetMatches(
                    selectedTag = selectedTag,
                    selectedUrl = selectedUrl,
                    rawTag = state.latestPreRawTag,
                    releaseUrl = state.latestPreUrl,
                )
            -> state.latestPreApkVersion

            !selectedIsPreRelease &&
                releaseNotesTargetMatches(
                    selectedTag = selectedTag,
                    selectedUrl = selectedUrl,
                    rawTag = state.latestStableRawTag.ifBlank { state.latestTag },
                    releaseUrl = state.latestStableUrl,
                )
            -> state.latestStableApkVersion

            else -> null
        }
    return versionInfo?.versionLabel().orEmpty()
}

private fun releaseNotesTranslationPayload(
    title: String,
    tag: String,
    rawMarkdown: String,
    fallbackLines: List<String>,
): String {
    val body =
        rawMarkdown.trim().ifBlank {
            fallbackLines.joinToString("\n").trim()
        }
    if (body.isBlank()) return ""
    val header =
        buildList {
            title.trim().takeIf { it.isNotBlank() }?.let(::add)
            tag.trim().takeIf { it.isNotBlank() && it != title.trim() }?.let(::add)
        }.joinToString(" · ")
    return listOf(header, body)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}

private fun launchReleaseNotesTranslation(
    context: Context,
    text: String,
    chooserTitle: String,
): Boolean {
    val payload = text.trim()
    if (payload.isBlank()) return false
    val translateIntent =
        Intent(Intent.ACTION_TRANSLATE).apply {
            putExtra(Intent.EXTRA_TEXT, payload)
        }
    if (startActivitySafely(context, translateIntent)) return true

    val processTextIntent =
        Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, payload)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        }
    if (startActivitySafely(context, processTextIntent)) return true

    val shareIntent =
        Intent.createChooser(
            SafeExternalIntents.textShareIntent(
                text = payload,
                subject = chooserTitle,
            ),
            chooserTitle,
        )
    return startActivitySafely(context, shareIntent)
}

private fun startActivitySafely(
    context: Context,
    intent: Intent,
): Boolean =
    runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)

private fun releaseNotesTargetMatches(
    selectedTag: String,
    selectedUrl: String,
    rawTag: String,
    releaseUrl: String,
): Boolean {
    val tag = rawTag.trim()
    val url = releaseUrl.trim()
    return (
        selectedTag.isNotBlank() && tag.isNotBlank() &&
            selectedTag.equals(
                tag,
                ignoreCase = true,
            )
    ) ||
        (
            selectedUrl.isNotBlank() && url.isNotBlank() &&
                selectedUrl.equals(
                    url,
                    ignoreCase = true,
                )
        )
}

@Composable
private fun releaseNotesBodyContainerColor(): Color = MiuixTheme.colorScheme.surface.copy(alpha = 0.96f)

private fun releaseNotesTargetDropdownLabel(
    target: GitHubReleaseNotesTarget,
    stableMarker: String,
    prereleaseMarker: String,
    latestMarker: String,
): String {
    val markers =
        releaseNotesTargetMarkers(
            target = target,
            stableMarker = stableMarker,
            prereleaseMarker = prereleaseMarker,
            latestMarker = latestMarker,
        )
    val name = target.releaseName.ifBlank { target.tagName }
    val tagLine =
        buildList {
            target.tagName.takeIf { it.isNotBlank() && it != name }?.let(::add)
            markers.takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString(" · ")
    return if (tagLine.isBlank()) {
        name
    } else {
        "$name\n$tagLine"
    }
}

private fun releaseNotesTargetSheetLabel(
    target: GitHubReleaseNotesTarget,
    stableMarker: String,
    prereleaseMarker: String,
    latestMarker: String,
): String {
    val markers =
        releaseNotesTargetMarkers(
            target = target,
            stableMarker = stableMarker,
            prereleaseMarker = prereleaseMarker,
            latestMarker = latestMarker,
        )
    return buildList {
        add(target.tagName)
        markers.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ")
}

private fun releaseNotesTargetMarkers(
    target: GitHubReleaseNotesTarget,
    stableMarker: String,
    prereleaseMarker: String,
    latestMarker: String,
): String =
    buildList {
        add(
            if (target.prerelease) {
                prereleaseMarker
            } else {
                stableMarker
            },
        )
        if (target.latestInChannel) {
            add(latestMarker)
        }
    }.joinToString(" · ")

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
    valueMaxLines: Int = 2,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.28f),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.72f),
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = valueMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun releaseNotesSourceLabel(source: String): String =
    when (source) {
        "api" -> stringResource(R.string.github_release_notes_source_api)
        "html" -> stringResource(R.string.github_release_notes_source_atom)
        "subscription" -> stringResource(R.string.github_release_notes_source_subscription)
        else -> stringResource(R.string.common_unknown)
    }

internal fun releaseNotesMarkdownSourceKey(
    item: GitHubTrackedApp,
    bundle: GitHubReleaseAssetBundle?,
    body: String,
): String =
    buildString {
        append("github-release-notes|")
        append(item.id)
        append('|')
        append(item.owner)
        append('/')
        append(item.repo)
        append('|')
        append(bundle?.tagName.orEmpty())
        append('|')
        append(bundle?.htmlUrl.orEmpty())
        append('|')
        append(bundle?.releaseUpdatedAtMillis ?: 0L)
        append('|')
        append(bundle?.fetchSource.orEmpty())
        append('|')
        append(bundle?.sourceConfigSignature.orEmpty())
        append('|')
        append(body.length)
        append(':')
        append(body.hashCode())
    }
