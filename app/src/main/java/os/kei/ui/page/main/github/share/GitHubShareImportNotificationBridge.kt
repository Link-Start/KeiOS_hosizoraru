package os.kei.ui.page.main.github.share

import android.content.Context
import os.kei.R
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper

internal fun notifyShareImportResolving(
    context: Context,
    sharedText: String
) {
    GitHubShareImportNotificationHelper.notifyResolving(
        context = context,
        sourceLabel = sharedText
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?.take(96)
            .orEmpty()
    )
}

internal fun notifyShareImportAssetReady(
    context: Context,
    preview: GitHubShareImportPreview
) {
    GitHubShareImportNotificationHelper.notifyAssetReady(
        context = context,
        owner = preview.owner,
        repo = preview.repo,
        releaseTag = preview.releaseTag,
        assetCount = preview.assets.size
    )
}

internal fun notifyShareImportDelivering(
    context: Context,
    preview: GitHubShareImportPreview,
    assetName: String
) {
    GitHubShareImportNotificationHelper.notifyDelivering(
        context = context,
        owner = preview.owner,
        repo = preview.repo,
        assetName = assetName,
        targetDisplayName = preview.targetDisplayName.ifBlank {
            buildShareImportTargetDisplayName(
                repo = preview.repo,
                assetName = assetName
            )
        }
    )
}

internal fun notifyShareImportWaitingInstall(
    context: Context,
    pending: GitHubPendingShareImportTrackRecord
) {
    GitHubShareImportNotificationHelper.notifyWaitingInstall(
        context = context,
        owner = pending.owner,
        repo = pending.repo,
        releaseTag = pending.releaseTag,
        assetName = pending.assetName,
        packageName = pending.packageName,
        remainingMinutes = shareImportRemainingMinutes(pending.armedAtMillis),
        targetDisplayName = pending.targetDisplayName
    )
}

internal fun notifyShareImportInstallDetected(
    context: Context,
    candidate: GitHubPendingShareImportAttachCandidate
) {
    GitHubShareImportNotificationHelper.notifyInstallDetected(
        context = context,
        owner = candidate.owner,
        repo = candidate.repo,
        appLabel = candidate.appLabel,
        packageName = candidate.packageName,
        targetDisplayName = buildShareImportTargetDisplayName(
            appLabel = candidate.appLabel,
            repo = candidate.repo,
            packageName = candidate.packageName
        )
    )
}

internal fun notifyShareImportAddingTrack(
    context: Context,
    candidate: GitHubPendingShareImportAttachCandidate
) {
    GitHubShareImportNotificationHelper.notifyAddingTrack(
        context = context,
        owner = candidate.owner,
        repo = candidate.repo,
        appLabel = candidate.appLabel,
        packageName = candidate.packageName,
        targetDisplayName = buildShareImportTargetDisplayName(
            appLabel = candidate.appLabel,
            repo = candidate.repo,
            packageName = candidate.packageName
        )
    )
}

internal fun notifyShareImportAdded(
    context: Context,
    candidate: GitHubPendingShareImportAttachCandidate,
    appLabel: String
) {
    GitHubShareImportNotificationHelper.notifyAdded(
        context = context,
        owner = candidate.owner,
        repo = candidate.repo,
        appLabel = appLabel.ifBlank { candidate.appLabel },
        packageName = candidate.packageName,
        targetDisplayName = buildShareImportTargetDisplayName(
            appLabel = appLabel.ifBlank { candidate.appLabel },
            repo = candidate.repo,
            packageName = candidate.packageName
        )
    )
}

internal fun notifyShareImportAlreadyTracked(
    context: Context,
    candidate: GitHubPendingShareImportAttachCandidate
) {
    GitHubShareImportNotificationHelper.notifyAlreadyTracked(
        context = context,
        owner = candidate.owner,
        repo = candidate.repo,
        appLabel = candidate.appLabel,
        packageName = candidate.packageName,
        targetDisplayName = buildShareImportTargetDisplayName(
            appLabel = candidate.appLabel,
            repo = candidate.repo,
            packageName = candidate.packageName
        )
    )
}

internal fun notifyShareImportFailed(
    context: Context,
    reason: String
) {
    GitHubShareImportNotificationHelper.notifyFailed(
        context = context,
        reason = reason.ifBlank {
            context.getString(R.string.github_share_import_error_resolve_failed)
        }
    )
}
