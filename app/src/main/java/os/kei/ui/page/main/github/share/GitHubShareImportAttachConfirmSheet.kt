@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.widget.core.MiuixInfoItem
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubShareImportAttachConfirmSheet(
    candidate: GitHubPendingShareImportAttachCandidate?,
    duplicateExists: Boolean,
    submitting: Boolean,
    submittingAndOpen: Boolean,
    allowDismiss: Boolean = true,
    onDismissRequest: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onConfirmAndOpenGitHub: (() -> Unit)? = null,
) {
    SnapshotWindowBottomSheet(
        show = candidate != null,
        title = stringResource(R.string.github_share_import_attach_dialog_title),
        onDismissRequest = onDismissRequest,
        insideMargin = shareImportSheetInsideMargin,
        allowDismiss = allowDismiss,
    ) {
        GitHubShareImportWindowBlurEffect(useBlur = candidate != null)
        val attachCandidate = candidate ?: return@SnapshotWindowBottomSheet
        SheetContentColumn(
            modifier = Modifier.shareImportSheetTags(),
            verticalSpacing = 10.dp,
        ) {
            GitHubShareImportAttachInfoCard(attachCandidate)
            if (duplicateExists) {
                SheetDescriptionText(
                    text = stringResource(R.string.github_share_import_attach_dialog_duplicate_hint),
                )
            }
            if (submitting) {
                GitHubShareImportAttachSubmittingRow(submittingAndOpen)
            }
            if (duplicateExists) {
                GitHubShareImportAttachDuplicateActions(
                    submitting = submitting,
                    onCancel = onCancel,
                )
            } else {
                GitHubShareImportAttachConfirmActions(
                    submitting = submitting,
                    submittingAndOpen = submittingAndOpen,
                    onCancel = onCancel,
                    onConfirm = onConfirm,
                    onConfirmAndOpenGitHub = onConfirmAndOpenGitHub,
                )
            }
        }
    }
}

@Composable
private fun GitHubShareImportAttachInfoCard(attachCandidate: GitHubPendingShareImportAttachCandidate) {
    SheetSectionCard(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalSpacing = 6.dp,
    ) {
        MiuixInfoItem(
            key = stringResource(R.string.github_share_import_pending_label_target),
            value = "${attachCandidate.owner}/${attachCandidate.repo}",
        )
        MiuixInfoItem(
            key = stringResource(R.string.github_share_import_attach_dialog_label_app),
            value = attachCandidate.appLabel,
        )
        MiuixInfoItem(
            key = stringResource(R.string.github_share_import_attach_dialog_label_package),
            value = attachCandidate.packageName,
        )
        val versionLabel =
            shareImportVersionLabel(
                versionName = attachCandidate.versionName,
                versionCode = attachCandidate.versionCode,
            )
        if (versionLabel.isNotBlank()) {
            MiuixInfoItem(
                key = stringResource(R.string.github_share_import_dialog_label_version),
                value = versionLabel,
            )
        }
    }
}

@Composable
private fun GitHubShareImportAttachSubmittingRow(submittingAndOpen: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidCircularProgressBar(size = 18.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text =
                if (submittingAndOpen) {
                    stringResource(R.string.github_share_import_attach_dialog_processing_open)
                } else {
                    stringResource(R.string.github_share_import_attach_dialog_processing_add)
                },
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun GitHubShareImportAttachDuplicateActions(
    submitting: Boolean,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppLiquidDialogActionButton(
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(KeiOsTestTags.GitHubShareImportAttachClose),
            text = stringResource(R.string.common_close),
            leadingIcon = appLucideCloseIcon(),
            containerColor = GitHubStatusPalette.Active,
            onClick = onCancel,
            enabled = !submitting,
        )
    }
}

@Composable
private fun GitHubShareImportAttachConfirmActions(
    submitting: Boolean,
    submittingAndOpen: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onConfirmAndOpenGitHub: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppLiquidDialogActionButton(
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(KeiOsTestTags.GitHubShareImportAttachCancel),
            text = stringResource(R.string.common_cancel),
            leadingIcon = appLucideCloseIcon(),
            containerColor = GitHubShareImportActionStyle.cancelContainerColor,
            variant = GitHubShareImportActionStyle.cancelVariant,
            onClick = onCancel,
            enabled = !submitting,
        )
        AppLiquidDialogActionButton(
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(KeiOsTestTags.GitHubShareImportAttachConfirm),
            text =
                if (submitting && !submittingAndOpen) {
                    stringResource(R.string.common_processing)
                } else {
                    stringResource(R.string.github_share_import_attach_dialog_action_confirm)
                },
            leadingIcon = appLucidePackageIcon(),
            containerColor = GitHubStatusPalette.Active,
            onClick = onConfirm,
            enabled = !submitting,
        )
    }
    if (onConfirmAndOpenGitHub != null) {
        AppLiquidDialogActionButton(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(KeiOsTestTags.GitHubShareImportAttachConfirmOpenGitHub),
            text =
                if (submitting && submittingAndOpen) {
                    stringResource(R.string.common_processing)
                } else {
                    stringResource(
                        R.string.github_share_import_attach_dialog_action_confirm_and_open_github,
                    )
                },
            leadingIcon = appLucideExternalLinkIcon(),
            containerColor = GitHubStatusPalette.Update,
            onClick = onConfirmAndOpenGitHub,
            enabled = !submitting,
        )
    }
}
