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

internal class GitHubShareImportEntryCoordinator(
    private val previewCoordinator: GitHubShareImportPreviewCoordinator,
    private val resultWriter: GitHubShareImportResultWriter = GitHubShareImportResultWriter(),
    private val clock: GitHubShareImportClock = GitHubSystemShareImportClock,
) {
    private val failureHandler =
        GitHubShareImportEntryFailureHandler(
            resultWriter = resultWriter,
            clock = clock,
        )

    suspend fun startIncomingShare(
        context: Context,
        sharedText: String,
        lookupConfig: GitHubLookupConfig? = null,
    ): ShareImportIncomingCoordinatorResult {
        val appContext = context.applicationContext
        val resolvedLookupConfig =
            lookupConfig ?: withContext(AppDispatchers.githubLocal) {
                GitHubTrackStore.loadLookupConfig()
            }
        val notificationFirst =
            resolvedLookupConfig.shareImportFlowMode == GitHubShareImportFlowMode.NotificationFirst
        notifyShareImportResolving(appContext, sharedText)
        return try {
            val parsedIncoming =
                GitHubShareIntentParser.parseSharedReleaseLink(sharedText)
                    ?: error(appContext.getString(R.string.github_share_import_error_no_valid_link))
            withContext(AppDispatchers.githubLocal) {
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
                return failureHandler.handleEmptyAssets(
                    context = appContext,
                    notificationFirst = notificationFirst,
                    plan = plan,
                )
            }

            val preview =
                GitHubShareImportPreviewBuilder.build(
                    plan = plan,
                    lookupConfig = resolvedLookupConfig,
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
            failureHandler.handleIncomingFailure(
                context = appContext,
                error = error,
                notificationFirst = notificationFirst,
            )
        }
    }
}
