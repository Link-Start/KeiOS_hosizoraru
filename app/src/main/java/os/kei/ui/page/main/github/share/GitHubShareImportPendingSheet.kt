@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.widget.core.MiuixInfoItem
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.testing.KeiOsTestTags

@Composable
internal fun GitHubShareImportPendingSheet(
    pending: GitHubPendingShareImportTrackRecord?,
    onDismissRequest: () -> Unit,
    onClose: () -> Unit,
    onCancel: () -> Unit,
) {
    SnapshotWindowBottomSheet(
        show = pending != null,
        title = stringResource(R.string.github_share_import_pending_title),
        onDismissRequest = onDismissRequest,
        insideMargin = shareImportSheetInsideMargin,
        allowDismiss = false,
    ) {
        GitHubShareImportWindowBlurEffect(useBlur = pending != null)
        val pendingTrack = pending ?: return@SnapshotWindowBottomSheet
        val remainingMinutes = shareImportRemainingMinutes(pendingTrack.armedAtMillis)
        SheetContentColumn(
            modifier = Modifier.shareImportSheetTags(),
            verticalSpacing = 10.dp,
        ) {
            SheetDescriptionText(
                text =
                    stringResource(
                        if (pendingTrack.packageName.isNotBlank()) {
                            R.string.github_share_import_pending_dialog_summary_exact
                        } else {
                            R.string.github_share_import_pending_dialog_summary
                        },
                    ),
            )
            SheetSectionCard(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalSpacing = 6.dp,
            ) {
                StatusPill(
                    label =
                        stringResource(
                            R.string.github_share_import_pending_remaining_minutes,
                            remainingMinutes,
                        ),
                    color = GitHubStatusPalette.PreRelease,
                )
                MiuixInfoItem(
                    key = stringResource(R.string.github_share_import_pending_label_target),
                    value = "${pendingTrack.owner}/${pendingTrack.repo}",
                )
                if (pendingTrack.releaseTag.isNotBlank()) {
                    MiuixInfoItem(
                        key = stringResource(R.string.github_share_import_pending_label_release),
                        value = pendingTrack.releaseTag,
                    )
                }
                if (pendingTrack.assetName.isNotBlank()) {
                    MiuixInfoItem(
                        key = stringResource(R.string.github_share_import_pending_label_asset),
                        value = pendingTrack.assetName,
                    )
                }
                if (pendingTrack.packageName.isNotBlank()) {
                    MiuixInfoItem(
                        key = stringResource(R.string.github_share_import_attach_dialog_label_package),
                        value = pendingTrack.packageName,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppLiquidDialogActionButton(
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(KeiOsTestTags.GitHubShareImportPendingClose),
                    text = stringResource(R.string.common_close),
                    leadingIcon = appLucideCloseIcon(),
                    onClick = onClose,
                )
                AppLiquidDialogActionButton(
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(KeiOsTestTags.GitHubShareImportPendingCancel),
                    text = stringResource(R.string.github_share_import_pending_action_cancel),
                    leadingIcon = appLucideCloseIcon(),
                    containerColor = GitHubShareImportActionStyle.cancelContainerColor,
                    variant = GitHubShareImportActionStyle.cancelVariant,
                    onClick = onCancel,
                )
            }
        }
    }
}
