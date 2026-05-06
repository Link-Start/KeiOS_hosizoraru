package os.kei.ui.page.main.github.section

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.github.GitHubCompactInfoRow
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.share.GitHubPendingShareImportAttachCandidate
import os.kei.ui.page.main.github.share.GitHubPendingShareImportTrack
import os.kei.ui.page.main.github.share.GitHubShareImportPreview
import os.kei.ui.page.main.github.share.shareImportRemainingMinutes
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubPendingShareImportCard(
    pending: GitHubPendingShareImportTrack,
    repoOverlapCount: Int,
    onOpen: () -> Unit,
    onCancel: () -> Unit
) {
    val nowMillis = System.currentTimeMillis()
    val ageMinutes = ((nowMillis - pending.armedAtMillis).coerceAtLeast(0L) / 60_000L).toInt()
    val remainingMinutes = shareImportRemainingMinutes(pending.armedAtMillis, nowMillis)
    AppSurfaceCard(
        containerColor = GitHubStatusPalette.tonedSurface(
            GitHubStatusPalette.Active,
            isDark = isSystemInDarkTheme()
        ).copy(alpha = 0.26f),
        borderColor = GitHubStatusPalette.Active.copy(alpha = 0.24f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = CardLayoutRhythm.cardHorizontalPadding,
                    vertical = CardLayoutRhythm.cardVerticalPadding
                ),
            verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.controlRowGap),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.github_share_import_pending_title),
                    color = GitHubStatusPalette.Active,
                    fontSize = AppTypographyTokens.CompactTitle.fontSize,
                    lineHeight = AppTypographyTokens.CompactTitle.lineHeight,
                    fontWeight = AppTypographyTokens.CompactTitle.fontWeight,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    label = stringResource(
                        R.string.github_share_import_pending_remaining_minutes,
                        remainingMinutes
                    ),
                    color = GitHubStatusPalette.Active,
                    size = AppStatusPillSize.Compact
                )
                StatusPill(
                    label = stringResource(
                        R.string.github_share_import_pending_armed_minutes,
                        ageMinutes.coerceAtLeast(0)
                    ),
                    color = GitHubStatusPalette.PreRelease,
                    size = AppStatusPillSize.Compact
                )
            }
            GitHubCompactInfoRow(
                label = stringResource(R.string.github_share_import_pending_label_target),
                value = "${pending.owner}/${pending.repo}",
                valueColor = MiuixTheme.colorScheme.onBackground
            )
            if (pending.releaseTag.isNotBlank()) {
                GitHubCompactInfoRow(
                    label = stringResource(R.string.github_share_import_pending_label_release),
                    value = pending.releaseTag,
                    valueColor = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }
            if (pending.assetName.isNotBlank()) {
                GitHubCompactInfoRow(
                    label = stringResource(R.string.github_share_import_pending_label_asset),
                    value = pending.assetName,
                    valueColor = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }
            if (repoOverlapCount > 0) {
                Text(
                    text = stringResource(
                        R.string.github_share_import_pending_repo_overlap_hint,
                        repoOverlapCount
                    ),
                    color = GitHubStatusPalette.PreRelease,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.controlRowGap)
            ) {
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.github_share_import_notify_action_view_status),
                    leadingIcon = appLucideExternalLinkIcon(),
                    containerColor = GitHubStatusPalette.Active,
                    onClick = onOpen
                )
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.github_share_import_pending_action_cancel),
                    leadingIcon = appLucideCloseIcon(),
                    onClick = onCancel
                )
            }
        }
    }
}

@Composable
internal fun GitHubShareImportPreviewCard(
    preview: GitHubShareImportPreview,
    onOpen: () -> Unit,
    onCancel: () -> Unit
) {
    GitHubShareImportFlowCard(
        title = stringResource(R.string.github_share_import_preview_card_title),
        status = stringResource(R.string.github_share_import_notify_action_select_apk),
        statusColor = GitHubStatusPalette.Update,
        onOpen = onOpen,
        onCancel = onCancel
    ) {
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_share_import_pending_label_target),
            value = "${preview.owner}/${preview.repo}",
            valueColor = MiuixTheme.colorScheme.onBackground
        )
        if (preview.releaseTag.isNotBlank()) {
            GitHubCompactInfoRow(
                label = stringResource(R.string.github_share_import_pending_label_release),
                value = preview.releaseTag,
                valueColor = MiuixTheme.colorScheme.onBackgroundVariant
            )
        }
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_share_import_dialog_label_assets),
            value = stringResource(
                R.string.github_share_import_preview_card_asset_count,
                preview.assets.size
            ),
            valueColor = GitHubStatusPalette.Update
        )
    }
}

@Composable
internal fun GitHubShareImportAttachCandidateCard(
    candidate: GitHubPendingShareImportAttachCandidate,
    onOpen: () -> Unit,
    onCancel: () -> Unit
) {
    GitHubShareImportFlowCard(
        title = stringResource(R.string.github_share_import_attach_card_title),
        status = stringResource(R.string.github_share_import_phase_install_detected),
        statusColor = GitHubStatusPalette.Update,
        onOpen = onOpen,
        onCancel = onCancel
    ) {
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_share_import_pending_label_target),
            value = "${candidate.owner}/${candidate.repo}",
            valueColor = MiuixTheme.colorScheme.onBackground
        )
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_share_import_attach_dialog_label_app),
            value = candidate.appLabel.ifBlank { candidate.packageName },
            valueColor = MiuixTheme.colorScheme.onBackground
        )
        GitHubCompactInfoRow(
            label = stringResource(R.string.github_share_import_attach_dialog_label_package),
            value = candidate.packageName,
            valueColor = MiuixTheme.colorScheme.onBackgroundVariant
        )
    }
}

@Composable
private fun GitHubShareImportFlowCard(
    title: String,
    status: String,
    statusColor: androidx.compose.ui.graphics.Color,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AppSurfaceCard(
        containerColor = GitHubStatusPalette.tonedSurface(
            statusColor,
            isDark = isSystemInDarkTheme()
        ).copy(alpha = 0.26f),
        borderColor = statusColor.copy(alpha = 0.24f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = CardLayoutRhythm.cardHorizontalPadding,
                    vertical = CardLayoutRhythm.cardVerticalPadding
                ),
            verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.controlRowGap),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = statusColor,
                    fontSize = AppTypographyTokens.CompactTitle.fontSize,
                    lineHeight = AppTypographyTokens.CompactTitle.lineHeight,
                    fontWeight = AppTypographyTokens.CompactTitle.fontWeight,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    label = status,
                    color = statusColor,
                    size = AppStatusPillSize.Compact
                )
            }
            content()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.controlRowGap)
            ) {
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.github_share_import_card_action_continue),
                    leadingIcon = appLucideExternalLinkIcon(),
                    containerColor = statusColor,
                    onClick = onOpen
                )
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.github_share_import_pending_action_cancel),
                    leadingIcon = appLucideCloseIcon(),
                    onClick = onCancel
                )
            }
        }
    }
}
