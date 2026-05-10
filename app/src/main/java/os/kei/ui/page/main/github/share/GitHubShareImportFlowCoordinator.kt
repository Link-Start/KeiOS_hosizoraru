package os.kei.ui.page.main.github.share

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.system.AppPackageChangedEvent
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubShareImportResolver
import os.kei.feature.github.data.remote.GitHubShareIntentParser
import os.kei.feature.github.model.GitHubApkInstallDeliveryMode
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubShareImportFlowMode
import os.kei.feature.github.notification.GitHubShareImportActionReceiver
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper
import os.kei.ui.page.main.github.install.GitHubApkInstallFlowCoordinator
import os.kei.ui.page.main.github.install.GitHubApkInstallRequestContext
import os.kei.ui.page.main.github.install.GitHubApkInstallSourceKind
import os.kei.ui.page.main.github.localizedGitHubShareImportErrorMessage

internal object GitHubShareImportFlowCoordinator {
    suspend fun startIncomingShare(
        context: Context,
        sharedText: String,
        lookupConfig: GitHubLookupConfig? = null
    ): ShareImportIncomingCoordinatorResult {
        val appContext = context.applicationContext
        val resolvedLookupConfig = lookupConfig ?: withContext(Dispatchers.IO) {
            GitHubTrackStore.loadLookupConfig()
        }
        val notificationFirst =
            resolvedLookupConfig.shareImportFlowMode == GitHubShareImportFlowMode.NotificationFirst
        if (!resolvedLookupConfig.shareImportLinkageEnabled) {
            return ShareImportIncomingCoordinatorResult(
                coordinatorResult = ShareImportCoordinatorResult.None,
                notificationFirst = notificationFirst
            )
        }
        notifyShareImportResolving(appContext, sharedText)
        return try {
            val parsedIncoming = GitHubShareIntentParser.parseSharedReleaseLink(sharedText)
                ?: error(appContext.getString(R.string.github_share_import_error_no_valid_link))
            withContext(Dispatchers.IO) {
                GitHubTrackStore.savePendingShareImportTrack(null)
                GitHubShareImportFlowStore.clearActiveFlow()
            }
            GitHubTrackStoreSignals.notifyChanged()
            val plan = withContext(Dispatchers.IO) {
                GitHubShareImportResolver.resolve(
                    sharedText = parsedIncoming.sourceUrl,
                    lookupConfig = resolvedLookupConfig
                ).getOrThrow()
            }
            if (plan.assets.isEmpty()) {
                val reason = appContext.getString(R.string.github_toast_share_import_no_apk)
                withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.clearActiveFlow()
                    GitHubShareImportFlowStore.saveActiveResult(
                        GitHubShareImportResult(
                            kind = GitHubShareImportResultKind.Failed,
                            projectUrl = plan.parsedLink.projectUrl,
                            owner = plan.parsedLink.owner,
                            repo = plan.parsedLink.repo,
                            message = reason
                        ).toRecord()
                    )
                }
                GitHubTrackStoreSignals.notifyChanged()
                notifyShareImportFailed(appContext, reason)
                return ShareImportIncomingCoordinatorResult(
                    coordinatorResult = ShareImportCoordinatorResult.Failed(reason),
                    notificationFirst = notificationFirst,
                    toastResId = R.string.github_toast_share_import_no_apk
                )
            }

            val preview = GitHubShareImportPreview(
                sourceUrl = plan.parsedLink.sourceUrl,
                projectUrl = plan.parsedLink.projectUrl,
                owner = plan.parsedLink.owner,
                repo = plan.parsedLink.repo,
                releaseTag = plan.resolvedReleaseTag,
                releaseUrl = plan.resolvedReleaseUrl,
                strategyLabel = resolvedLookupConfig.selectedStrategy.label,
                assets = plan.assets,
                preferredAssetName = plan.preferredAssetName,
                targetDisplayName = buildShareImportTargetDisplayName(
                    repo = plan.parsedLink.repo,
                    assetName = plan.preferredAssetName.ifBlank {
                        plan.assets.singleOrNull()?.name.orEmpty()
                    }
                )
            )
            val notificationFirstFlow = shouldUseNotificationFirstFlow(
                flowMode = resolvedLookupConfig.shareImportFlowMode,
                assetCount = preview.assets.size
            )
            val sendInstallActionEnabled =
                notificationFirstFlow && preview.assets.size == 1
            val coordinatorResult = prepareAssetReady(
                context = appContext,
                preview = preview,
                sendInstallActionEnabled = sendInstallActionEnabled
            )
            ShareImportIncomingCoordinatorResult(
                coordinatorResult = coordinatorResult,
                notificationFirst = notificationFirstFlow,
                sendInstallActionEnabled = sendInstallActionEnabled
            )
        } catch (error: Throwable) {
            if (error.shouldSuppressShareImportFailureToast()) {
                return ShareImportIncomingCoordinatorResult(
                    coordinatorResult = ShareImportCoordinatorResult.None,
                    notificationFirst = notificationFirst
                )
            }
            withContext(Dispatchers.IO) {
                GitHubShareImportFlowStore.clearActiveFlow()
            }
            val reason = localizedGitHubShareImportErrorMessage(
                context = appContext,
                rawMessage = error.message?.takeIf { it.isNotBlank() }
                    ?: error.javaClass.simpleName
            )
            withContext(Dispatchers.IO) {
                GitHubShareImportFlowStore.saveActiveResult(
                    GitHubShareImportResult(
                        kind = GitHubShareImportResultKind.Failed,
                        message = reason
                    ).toRecord()
                )
            }
            GitHubTrackStoreSignals.notifyChanged()
            notifyShareImportFailed(appContext, reason)
            ShareImportIncomingCoordinatorResult(
                coordinatorResult = ShareImportCoordinatorResult.Failed(reason),
                notificationFirst = notificationFirst,
                toastResId = R.string.github_toast_share_import_failed,
                toastMessage = reason
            )
        }
    }

    suspend fun prepareAssetReady(
        context: Context,
        preview: GitHubShareImportPreview,
        sendInstallActionEnabled: Boolean
    ): ShareImportCoordinatorResult.AssetReady {
        val selectedAsset = preview.selectedAssetForSend
        val readyPreview = preview.copy(
            selectedAssetName = if (sendInstallActionEnabled) {
                selectedAsset?.name.orEmpty()
            } else {
                preview.selectedAssetName
            },
            sendInstallActionEnabled = sendInstallActionEnabled && selectedAsset != null
        )
        withContext(Dispatchers.IO) {
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
            sendInstallActionEnabled = readyPreview.sendInstallActionEnabled
        )
        return ShareImportCoordinatorResult.AssetReady(
            preview = readyPreview,
            sendInstallActionEnabled = readyPreview.sendInstallActionEnabled
        )
    }

    suspend fun sendActivePreviewAssetToInstaller(
        context: Context
    ): ShareImportDeliveryCoordinatorResult {
        val appContext = context.applicationContext
        val preview = withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.loadActivePreview()?.toShareImportPreview()
        }
        if (preview == null) {
            val reason = appContext.getString(R.string.github_share_import_error_resolve_failed)
            GitHubShareImportNotificationHelper.notifyFailed(appContext, reason)
            return ShareImportDeliveryCoordinatorResult.Failed(
                R.string.github_share_import_error_resolve_failed
            )
        }
        if (!preview.sendInstallActionEnabled) {
            GitHubShareImportNotificationHelper.notifyAssetReady(
                context = appContext,
                owner = preview.owner,
                repo = preview.repo,
                releaseTag = preview.releaseTag,
                assetCount = preview.assets.size,
                sendInstallActionEnabled = false
            )
            return ShareImportDeliveryCoordinatorResult.Failed(
                R.string.github_share_import_notify_action_select_apk
            )
        }
        val selectedAsset = preview.selectedAssetForSend
        if (selectedAsset == null) {
            val reason = appContext.getString(R.string.github_share_import_error_no_usable_apk)
            GitHubShareImportNotificationHelper.notifyFailed(appContext, reason)
            return ShareImportDeliveryCoordinatorResult.Failed(
                R.string.github_share_import_error_no_usable_apk
            )
        }
        val lookupConfig = withContext(Dispatchers.IO) {
            GitHubTrackStore.loadLookupConfig()
        }
        return startDelivery(
            context = context,
            preview = preview,
            selectedAsset = selectedAsset,
            lookupConfig = lookupConfig,
            launchInNewTask = context !is Activity
        )
    }

    suspend fun startDelivery(
        context: Context,
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        launchInNewTask: Boolean = false
    ): ShareImportDeliveryCoordinatorResult = coroutineScope {
        GitHubShareImportNotificationHelper.notifyDelivering(
            context = context,
            owner = preview.owner,
            repo = preview.repo,
            assetName = selectedAsset.name,
            targetDisplayName = preview.targetDisplayName.ifBlank {
                buildShareImportTargetDisplayName(
                    repo = preview.repo,
                    assetName = selectedAsset.name
                )
            }
        )
        val scannedPackageNameDeferred = async(Dispatchers.IO) {
            scanShareImportAssetPackageName(
                asset = selectedAsset,
                lookupConfig = lookupConfig
            ).getOrDefault("")
        }
        if (lookupConfig.apkInstallDeliveryMode == GitHubApkInstallDeliveryMode.AppShizuku) {
            GitHubShareImportNotificationHelper.cancel(context)
            val installSessionId = GitHubApkInstallFlowCoordinator.beginInstallAsset(
                context = context,
                lookupConfig = lookupConfig,
                asset = selectedAsset,
                request = GitHubApkInstallRequestContext(
                    sourceKind = GitHubApkInstallSourceKind.ShareImport,
                    owner = preview.owner,
                    repo = preview.repo,
                    releaseTag = preview.releaseTag,
                    sourceLabel = preview.targetDisplayName.ifBlank {
                        buildShareImportTargetDisplayName(
                            repo = preview.repo,
                            assetName = selectedAsset.name
                        )
                    },
                    externalFileName = selectedAsset.name
                )
            )
            val scannedPackageName = scannedPackageNameDeferred.await()
            if (!GitHubApkInstallFlowCoordinator.isActiveSession(installSessionId)) {
                return@coroutineScope ShareImportDeliveryCoordinatorResult.Failed(
                    R.string.github_apk_install_cancelled
                )
            }
            GitHubApkInstallFlowCoordinator.updateExpectedPackageName(
                sessionId = installSessionId,
                packageName = scannedPackageName
            )
            val pending = buildPendingShareImportTrackRecord(
                preview = preview,
                selectedAsset = selectedAsset,
                scannedPackageName = scannedPackageName
            )
            withContext(Dispatchers.IO) {
                GitHubTrackStore.savePendingShareImportTrack(pending)
                GitHubShareImportFlowStore.clearActiveFlow()
            }
            GitHubTrackStoreSignals.notifyChanged()
            GitHubShareImportPendingScheduler.cancel(context)
            return@coroutineScope ShareImportDeliveryCoordinatorResult.WaitingInstall(
                pending = pending,
                toastResId = R.string.github_toast_share_import_wait_install,
                assetName = selectedAsset.name
            )
        }
        val deliveryResult = sendAssetToConfiguredChannel(
            context = context,
            lookupConfig = lookupConfig,
            asset = selectedAsset,
            newTask = launchInNewTask
        )
        when (deliveryResult) {
            is ShareImportDeliveryResult.Failure -> {
                scannedPackageNameDeferred.cancel()
                GitHubShareImportNotificationHelper.notifyFailed(
                    context = context,
                    reason = context.getString(deliveryResult.toastResId)
                )
                ShareImportDeliveryCoordinatorResult.Failed(deliveryResult.toastResId)
            }

            is ShareImportDeliveryResult.Success -> {
                val scannedPackageName = scannedPackageNameDeferred.await()
                val pending = buildPendingShareImportTrackRecord(
                    preview = preview,
                    selectedAsset = selectedAsset,
                    scannedPackageName = scannedPackageName
                )
                withContext(Dispatchers.IO) {
                    GitHubTrackStore.savePendingShareImportTrack(pending)
                    GitHubShareImportFlowStore.clearActiveFlow()
                }
                GitHubTrackStoreSignals.notifyChanged()
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
                GitHubShareImportPendingScheduler.scheduleNext(context)
                ShareImportDeliveryCoordinatorResult.WaitingInstall(
                    pending = pending,
                    toastResId = deliveryResult.toastResId,
                    assetName = selectedAsset.name
                )
            }
        }
    }

    private fun buildPendingShareImportTrackRecord(
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile,
        scannedPackageName: String
    ): GitHubPendingShareImportTrackRecord {
        return GitHubPendingShareImportTrackRecord(
            projectUrl = preview.projectUrl,
            owner = preview.owner,
            repo = preview.repo,
            releaseTag = preview.releaseTag,
            assetName = selectedAsset.name,
            packageName = scannedPackageName,
            targetDisplayName = buildShareImportTargetDisplayName(
                repo = preview.repo,
                assetName = selectedAsset.name,
                packageName = scannedPackageName
            ).ifBlank { preview.targetDisplayName },
            armedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun refreshPendingInstall(
        context: Context,
        event: AppPackageChangedEvent? = null
    ): ShareImportCoordinatorResult {
        val appContext = context.applicationContext
        val pending = withContext(Dispatchers.IO) {
            GitHubTrackStore.loadPendingShareImportTrack()
        } ?: return ShareImportCoordinatorResult.None
        val age = (System.currentTimeMillis() - pending.armedAtMillis).coerceAtLeast(0L)
        if (age > shareImportTrackMaxAgeMs) {
            return cancelPending(
                context = appContext,
                pending = pending,
                notify = true
            )
        }

        val reconciler = GitHubShareImportInstallReconciler(appContext)
        val currentCandidate = withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.loadActiveAttachCandidate()?.toShareImportAttachCandidate()
        }
        val reconcileResult = if (event != null) {
            reconciler.reconcilePackageEvent(
                pendingTrack = pending,
                event = event,
                currentCandidate = currentCandidate
            )
        } else {
            reconciler.reconcileRecentInstall(pending)
        }
        return applyReconcileResult(
            context = appContext,
            pending = pending,
            result = reconcileResult
        )
    }

    suspend fun confirmActiveAttachCandidate(
        context: Context,
        prefetchLatestCheck: Boolean = true
    ): ShareImportCoordinatorResult {
        val appContext = context.applicationContext
        val candidate = withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.loadActiveAttachCandidate()
                ?.toShareImportAttachCandidate()
        } ?: return refreshPendingInstall(appContext)
        GitHubShareImportNotificationHelper.notifyAddingTrack(
            context = appContext,
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
        return when (
            val result = attachCandidateToTracked(
                context = appContext,
                candidate = candidate,
                prefetchLatestCheck = prefetchLatestCheck
            )
        ) {
            ShareImportAttachResult.Duplicate -> {
                withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.clearActiveAttachCandidate()
                    GitHubShareImportFlowStore.saveActiveResult(
                        candidate.toShareImportResult(GitHubShareImportResultKind.AlreadyTracked)
                            .toRecord()
                    )
                }
                GitHubTrackStoreSignals.notifyChanged()
                GitHubShareImportNotificationHelper.notifyAlreadyTracked(
                    context = appContext,
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
                ShareImportCoordinatorResult.AlreadyTracked(candidate)
            }

            is ShareImportAttachResult.Failed -> {
                withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.saveActiveResult(
                        candidate.toShareImportResult(
                            kind = GitHubShareImportResultKind.Failed,
                            message = result.message
                        ).toRecord()
                    )
                }
                GitHubTrackStoreSignals.notifyChanged()
                GitHubShareImportNotificationHelper.notifyFailed(appContext, result.message)
                ShareImportCoordinatorResult.Failed(result.message)
            }

            is ShareImportAttachResult.Added -> {
                withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.clearActiveAttachCandidate()
                    GitHubShareImportFlowStore.saveActiveResult(
                        candidate.toShareImportResult(
                            kind = GitHubShareImportResultKind.Added,
                            appLabelOverride = result.appLabel
                        ).toRecord()
                    )
                }
                GitHubTrackStoreSignals.notifyChanged()
                GitHubShareImportNotificationHelper.notifyAdded(
                    context = appContext,
                    owner = candidate.owner,
                    repo = candidate.repo,
                    appLabel = result.appLabel.ifBlank { candidate.appLabel },
                    packageName = candidate.packageName,
                    targetDisplayName = buildShareImportTargetDisplayName(
                        appLabel = result.appLabel.ifBlank { candidate.appLabel },
                        repo = candidate.repo,
                        packageName = candidate.packageName
                    )
                )
                ShareImportCoordinatorResult.Added(candidate, result.appLabel)
            }
        }
    }

    suspend fun cancelActiveFlow(context: Context): ShareImportCoordinatorResult {
        val appContext = context.applicationContext
        val result = buildCancelledResult(appContext)
        withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.clearActiveFlow()
            GitHubTrackStore.savePendingShareImportTrack(null)
            if (result != null) {
                GitHubShareImportFlowStore.saveActiveResult(result.toRecord())
            }
        }
        GitHubShareImportPendingScheduler.cancel(appContext)
        GitHubTrackStoreSignals.notifyChanged()
        GitHubShareImportNotificationHelper.notifyCancelled(appContext)
        return ShareImportCoordinatorResult.Cancelled(result)
    }

    fun markRead(context: Context) {
        GitHubShareImportFlowStore.clearActiveResult()
        GitHubTrackStoreSignals.notifyChanged()
        GitHubShareImportNotificationHelper.cancel(context.applicationContext)
    }

    fun handlePackageChangedAsync(context: Context, event: AppPackageChangedEvent) {
        if (event.action !in shareImportAttachActions) return
        backgroundScope.launch {
            refreshPendingInstall(context.applicationContext, event)
        }
    }

    private suspend fun applyReconcileResult(
        context: Context,
        pending: GitHubPendingShareImportTrackRecord,
        result: ShareImportInstallReconcileResult
    ): ShareImportCoordinatorResult {
        return when (result) {
            ShareImportInstallReconcileResult.None -> {
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
                GitHubShareImportPendingScheduler.scheduleNext(context)
                ShareImportCoordinatorResult.Pending(pending)
            }

            ShareImportInstallReconcileResult.Expired -> cancelPending(
                context = context,
                pending = pending,
                notify = true
            )

            is ShareImportInstallReconcileResult.Duplicate -> {
                val candidate = result.candidate
                withContext(Dispatchers.IO) {
                    GitHubTrackStore.savePendingShareImportTrack(null)
                    GitHubShareImportFlowStore.clearActiveFlow()
                    GitHubShareImportFlowStore.saveActiveResult(
                        candidate.toShareImportResult(GitHubShareImportResultKind.AlreadyTracked)
                            .toRecord()
                    )
                }
                GitHubShareImportPendingScheduler.cancel(context)
                GitHubTrackStoreSignals.notifyChanged()
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
                ShareImportCoordinatorResult.AlreadyTracked(candidate)
            }

            is ShareImportInstallReconcileResult.Detected -> {
                val candidate = result.candidate
                withContext(Dispatchers.IO) {
                    GitHubTrackStore.savePendingShareImportTrack(null)
                    GitHubShareImportFlowStore.clearActivePreview()
                    GitHubShareImportFlowStore.saveActiveAttachCandidate(
                        candidate.toPendingAttachCandidateRecord()
                    )
                }
                GitHubShareImportPendingScheduler.cancel(context)
                GitHubTrackStoreSignals.notifyChanged()
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
                ShareImportCoordinatorResult.Detected(candidate)
            }
        }
    }

    private suspend fun cancelPending(
        context: Context,
        pending: GitHubPendingShareImportTrackRecord,
        notify: Boolean
    ): ShareImportCoordinatorResult.Cancelled {
        val result = pending.toShareImportResult(
            kind = GitHubShareImportResultKind.Cancelled,
            message = context.getString(R.string.github_share_import_notify_content_cancelled)
        )
        withContext(Dispatchers.IO) {
            GitHubTrackStore.savePendingShareImportTrack(null)
            GitHubShareImportFlowStore.clearActiveFlow()
            GitHubShareImportFlowStore.saveActiveResult(result.toRecord())
        }
        GitHubShareImportPendingScheduler.cancel(context)
        GitHubTrackStoreSignals.notifyChanged()
        if (notify) {
            GitHubShareImportNotificationHelper.notifyCancelled(context)
        }
        return ShareImportCoordinatorResult.Cancelled(result)
    }

    private fun buildCancelledResult(context: Context): GitHubShareImportResult? {
        GitHubShareImportFlowStore.loadActiveAttachCandidate()?.let { candidate ->
            return candidate.toShareImportAttachCandidate().toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = context.getString(R.string.github_share_import_notify_content_cancelled)
            )
        }
        GitHubTrackStore.loadPendingShareImportTrack()?.let { pending ->
            return pending.toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = context.getString(R.string.github_share_import_notify_content_cancelled)
            )
        }
        GitHubShareImportFlowStore.loadActivePreview()?.let { preview ->
            return preview.toShareImportPreview().toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = context.getString(R.string.github_share_import_notify_content_cancelled)
            )
        }
        return null
    }

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

internal sealed interface ShareImportDeliveryCoordinatorResult {
    data class WaitingInstall(
        val pending: GitHubPendingShareImportTrackRecord,
        val toastResId: Int,
        val assetName: String
    ) : ShareImportDeliveryCoordinatorResult

    data class Failed(
        val toastResId: Int
    ) : ShareImportDeliveryCoordinatorResult
}

internal sealed interface ShareImportCoordinatorResult {
    data object None : ShareImportCoordinatorResult
    data class AssetReady(
        val preview: GitHubShareImportPreview,
        val sendInstallActionEnabled: Boolean
    ) : ShareImportCoordinatorResult

    data class Pending(val pending: GitHubPendingShareImportTrackRecord) :
        ShareImportCoordinatorResult

    data class Detected(val candidate: GitHubPendingShareImportAttachCandidate) :
        ShareImportCoordinatorResult

    data class Added(
        val candidate: GitHubPendingShareImportAttachCandidate,
        val appLabel: String
    ) : ShareImportCoordinatorResult

    data class AlreadyTracked(
        val candidate: GitHubPendingShareImportAttachCandidate
    ) : ShareImportCoordinatorResult

    data class Failed(val message: String) : ShareImportCoordinatorResult
    data class Cancelled(val result: GitHubShareImportResult?) : ShareImportCoordinatorResult
}

internal data class ShareImportIncomingCoordinatorResult(
    val coordinatorResult: ShareImportCoordinatorResult,
    val notificationFirst: Boolean,
    val sendInstallActionEnabled: Boolean = false,
    @param:StringRes val toastResId: Int? = null,
    val toastMessage: String = ""
)

internal object GitHubShareImportPendingScheduler {
    private const val REQUEST_CODE_SHARE_IMPORT_TICK = 42003
    private const val FIRST_TICK_DELAY_MS = 15_000L
    private const val REFRESH_TICK_DELAY_MS = 60_000L

    fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        val pending = GitHubTrackStore.loadPendingShareImportTrack()
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = tickPendingIntent(appContext)
        if (pending == null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            return
        }
        val now = System.currentTimeMillis()
        val age = (now - pending.armedAtMillis).coerceAtLeast(0L)
        val expiresAt = pending.armedAtMillis + shareImportTrackMaxAgeMs
        val delay = if (age < FIRST_TICK_DELAY_MS) FIRST_TICK_DELAY_MS else REFRESH_TICK_DELAY_MS
        val triggerAt =
            (now + delay).coerceAtMost(expiresAt).coerceAtLeast(now + FIRST_TICK_DELAY_MS)
        alarmManager.cancel(pendingIntent)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = tickPendingIntent(appContext)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun tickPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GitHubShareImportActionReceiver::class.java).apply {
            action = GitHubShareImportActionReceiver.ACTION_REFRESH_SHARE_IMPORT
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SHARE_IMPORT_TICK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
