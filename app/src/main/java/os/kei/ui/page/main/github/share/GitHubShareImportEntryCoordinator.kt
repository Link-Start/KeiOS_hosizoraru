package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubShareImportResolver
import os.kei.feature.github.data.remote.GitHubShareIntentParser
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubShareImportFlowMode
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper
import os.kei.ui.page.main.github.localizedGitHubShareImportErrorMessage

internal class GitHubShareImportEntryCoordinator(
    private val previewCoordinator: GitHubShareImportPreviewCoordinator,
) {
    suspend fun startIncomingShare(
        context: Context,
        sharedText: String,
        lookupConfig: GitHubLookupConfig? = null,
    ): ShareImportIncomingCoordinatorResult {
        val appContext = context.applicationContext
        val resolvedLookupConfig =
            lookupConfig ?: withContext(AppDispatchers.githubNetwork) {
                GitHubTrackStore.loadLookupConfig()
            }
        val notificationFirst =
            resolvedLookupConfig.shareImportFlowMode == GitHubShareImportFlowMode.NotificationFirst
        notifyShareImportResolving(appContext, sharedText)
        return try {
            val parsedIncoming =
                GitHubShareIntentParser.parseSharedReleaseLink(sharedText)
                    ?: error(appContext.getString(R.string.github_share_import_error_no_valid_link))
            withContext(AppDispatchers.githubNetwork) {
                GitHubTrackStore.savePendingShareImportTrack(null)
                GitHubShareImportFlowStore.clearActiveFlow()
            }
            GitHubTrackStoreSignals.notifyChanged()
            val plan =
                GitHubShareImportResolver
                    .resolve(
                        sharedText = parsedIncoming.sourceUrl,
                        lookupConfig = resolvedLookupConfig,
                    ).getOrThrow()
            if (plan.assets.isEmpty()) {
                return handleEmptyAssets(
                    context = appContext,
                    notificationFirst = notificationFirst,
                    projectUrl = plan.parsedLink.projectUrl,
                    owner = plan.parsedLink.owner,
                    repo = plan.parsedLink.repo,
                )
            }

            val preview =
                GitHubShareImportPreview(
                    sourceUrl = plan.parsedLink.sourceUrl,
                    projectUrl = plan.parsedLink.projectUrl,
                    owner = plan.parsedLink.owner,
                    repo = plan.parsedLink.repo,
                    releaseTag = plan.resolvedReleaseTag,
                    releaseUrl = plan.resolvedReleaseUrl,
                    strategyLabel = resolvedLookupConfig.selectedStrategy.label,
                    assets = plan.assets,
                    preferredAssetName = plan.preferredAssetName,
                    targetDisplayName =
                        buildShareImportTargetDisplayName(
                            repo = plan.parsedLink.repo,
                            assetName =
                                plan.preferredAssetName.ifBlank {
                                    plan.assets
                                        .singleOrNull()
                                        ?.name
                                        .orEmpty()
                                },
                        ),
                )
            val notificationFirstFlow =
                shouldUseNotificationFirstFlow(
                    flowMode = resolvedLookupConfig.shareImportFlowMode,
                    assetCount = preview.assets.size,
                )
            val sendInstallActionEnabled =
                notificationFirstFlow && preview.assets.size == 1
            val coordinatorResult =
                previewCoordinator.prepareAssetReady(
                    context = appContext,
                    preview = preview,
                    sendInstallActionEnabled = sendInstallActionEnabled,
                )
            ShareImportIncomingCoordinatorResult(
                coordinatorResult = coordinatorResult,
                notificationFirst = notificationFirstFlow,
                sendInstallActionEnabled = sendInstallActionEnabled,
            )
        } catch (error: Throwable) {
            handleIncomingFailure(
                context = appContext,
                error = error,
                notificationFirst = notificationFirst,
            )
        }
    }

    private suspend fun handleEmptyAssets(
        context: Context,
        notificationFirst: Boolean,
        projectUrl: String,
        owner: String,
        repo: String,
    ): ShareImportIncomingCoordinatorResult {
        val reason = context.getString(R.string.github_toast_share_import_no_apk)
        withContext(AppDispatchers.githubNetwork) {
            GitHubShareImportFlowStore.clearActiveFlow()
            GitHubShareImportFlowStore.saveActiveResult(
                GitHubShareImportResult(
                    kind = GitHubShareImportResultKind.Failed,
                    projectUrl = projectUrl,
                    owner = owner,
                    repo = repo,
                    message = reason,
                ).toRecord(),
            )
        }
        GitHubTrackStoreSignals.notifyChanged()
        notifyShareImportFailed(context, reason)
        return ShareImportIncomingCoordinatorResult(
            coordinatorResult = ShareImportCoordinatorResult.Failed(reason),
            notificationFirst = notificationFirst,
            toastResId = R.string.github_toast_share_import_no_apk,
        )
    }

    private suspend fun handleIncomingFailure(
        context: Context,
        error: Throwable,
        notificationFirst: Boolean,
    ): ShareImportIncomingCoordinatorResult {
        if (error.shouldSuppressShareImportFailureToast()) {
            return ShareImportIncomingCoordinatorResult(
                coordinatorResult = ShareImportCoordinatorResult.None,
                notificationFirst = notificationFirst,
            )
        }
        withContext(AppDispatchers.githubNetwork) {
            GitHubShareImportFlowStore.clearActiveFlow()
        }
        val reason =
            localizedGitHubShareImportErrorMessage(
                context = context,
                rawMessage =
                    error.message?.takeIf { it.isNotBlank() }
                        ?: error.javaClass.simpleName,
            )
        withContext(AppDispatchers.githubNetwork) {
            GitHubShareImportFlowStore.saveActiveResult(
                GitHubShareImportResult(
                    kind = GitHubShareImportResultKind.Failed,
                    message = reason,
                ).toRecord(),
            )
        }
        GitHubTrackStoreSignals.notifyChanged()
        notifyShareImportFailed(context, reason)
        return ShareImportIncomingCoordinatorResult(
            coordinatorResult = ShareImportCoordinatorResult.Failed(reason),
            notificationFirst = notificationFirst,
            toastResId = R.string.github_toast_share_import_failed,
            toastMessage = reason,
        )
    }
}

internal class GitHubShareImportPreviewCoordinator {
    suspend fun prepareAssetReady(
        context: Context,
        preview: GitHubShareImportPreview,
        sendInstallActionEnabled: Boolean,
    ): ShareImportCoordinatorResult.AssetReady {
        val selectedAsset = preview.selectedAssetForSend
        val readyPreview =
            preview.copy(
                selectedAssetName =
                    if (sendInstallActionEnabled) {
                        selectedAsset?.name.orEmpty()
                    } else {
                        preview.selectedAssetName
                    },
                sendInstallActionEnabled = sendInstallActionEnabled && selectedAsset != null,
            )
        withContext(AppDispatchers.githubNetwork) {
            GitHubTrackStore.savePendingShareImportTrack(null)
            GitHubShareImportFlowStore.saveActivePreview(readyPreview.toPendingPreviewRecord())
        }
        GitHubShareImportPendingScheduler.cancel(context)
        GitHubTrackStoreSignals.notifyChanged()
        GitHubShareImportNotificationHelper.notifyAssetReady(
            context = context.applicationContext,
            owner = readyPreview.owner,
            repo = readyPreview.repo,
            releaseTag = readyPreview.releaseTag,
            assetCount = readyPreview.assets.size,
            sendInstallActionEnabled = readyPreview.sendInstallActionEnabled,
        )
        return ShareImportCoordinatorResult.AssetReady(
            preview = readyPreview,
            sendInstallActionEnabled = readyPreview.sendInstallActionEnabled,
        )
    }
}
