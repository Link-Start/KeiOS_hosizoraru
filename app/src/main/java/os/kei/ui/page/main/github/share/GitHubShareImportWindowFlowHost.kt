@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.system.AppPackageChangedEvents
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.model.GitHubShareImportFlowMode

@Composable
internal fun GitHubShareImportWindowFlowHost(
    incomingGitHubShareText: String?,
    incomingGitHubShareToken: Int,
    resumeRequestToken: Int = 0,
    onIncomingGitHubShareConsumed: () -> Unit,
    onNavigateToGitHubPage: () -> Unit,
    showPendingArmedSheet: Boolean = false,
    onNotificationOnlyResolveChanged: ((Boolean) -> Unit)? = null,
    onActivityBackInterceptionChanged: ((Boolean) -> Unit)? = null,
    onMinimizeActiveFlow: (() -> Unit)? = null,
    onClosePendingArmedSheet: (() -> Unit)? = null,
    onIdleWithNoPendingFlow: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope =
        remember {
            CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        }
    val windowCoordinator = remember { GitHubShareImportWindowCoordinator() }
    val installFlowCoordinator = remember { GitHubShareImportInstallFlowCoordinator() }
    val latestOnActivityBackInterceptionChanged by rememberUpdatedState(
        onActivityBackInterceptionChanged,
    )
    DisposableEffect(Unit) {
        onDispose {
            scope.cancel()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            latestOnActivityBackInterceptionChanged?.invoke(false)
        }
    }
    val flowState = rememberGitHubShareImportWindowFlowStateHolder()
    val snapshot = flowState.snapshot

    LaunchedEffect(snapshot.notificationOnlyIncomingResolve) {
        onNotificationOnlyResolveChanged?.invoke(snapshot.notificationOnlyIncomingResolve)
    }
    val pendingArmedSheetVisible = flowState.pendingArmedSheetVisible(showPendingArmedSheet)
    LaunchedEffect(pendingArmedSheetVisible) {
        latestOnActivityBackInterceptionChanged?.invoke(pendingArmedSheetVisible)
    }

    LaunchedEffect(Unit) {
        GitHubTrackStoreSignals.version.collect {
            if (!flowState.resolving && !flowState.incomingResolveRunning && !flowState.attachSubmitting) {
                flowState.applyStoredSnapshot(windowCoordinator.loadStoredSnapshot())
            }
        }
    }

    LaunchedEffect(resumeRequestToken) {
        flowState.updateRestoringActiveFlow(true)
        try {
            if (!incomingGitHubShareText.isNullOrBlank()) {
                return@LaunchedEffect
            }
            if (flowState.resolving || flowState.incomingResolveRunning || flowState.attachCandidate != null) {
                return@LaunchedEffect
            }
            when (val restored = windowCoordinator.restoreActiveFlow(context)) {
                is GitHubShareImportRestoreResult.Snapshot -> {
                    flowState.applyStoredSnapshot(restored.snapshot)
                }

                is GitHubShareImportRestoreResult.Coordinator -> {
                    flowState.applyCoordinatorResult(restored.result)
                }
            }
        } finally {
            flowState.updateRestoringActiveFlow(false)
        }
    }

    LaunchedEffect(flowState.pendingTrack?.armedAtMillis, flowState.attachCandidate?.packageName) {
        val armedAtMillis = flowState.pendingTrack?.armedAtMillis ?: return@LaunchedEffect
        if (flowState.attachCandidate != null) return@LaunchedEffect
        when (val result = windowCoordinator.awaitPendingResolution(context, armedAtMillis)) {
            is ShareImportCoordinatorResult.Pending,
            ShareImportCoordinatorResult.None,
            -> {
                Unit
            }

            else -> {
                flowState.applyCoordinatorResult(result)
                if (result is ShareImportCoordinatorResult.AlreadyTracked) {
                    toast(context, R.string.github_toast_share_import_track_exists)
                }
            }
        }
    }

    LaunchedEffect(incomingGitHubShareToken) {
        val sharedText = incomingGitHubShareText?.trim().orEmpty()
        if (sharedText.isBlank()) return@LaunchedEffect
        if (flowState.resolving || flowState.incomingResolveRunning) return@LaunchedEffect
        flowState.updateIncomingResolveRunning(true)
        onIncomingGitHubShareConsumed()
        flowState.clearActiveFlowForIncomingResolve()
        withContext(AppDispatchers.githubNetwork) {
            GitHubShareImportFlowStore.clearActiveFlow()
        }
        try {
            flowState.updateResolving(true)
            flowState.updatePhase(GitHubShareImportPhase.Resolving)
            val incomingResult =
                GitHubShareImportFlowCoordinator.startIncomingShare(
                    context = context,
                    sharedText = sharedText,
                )
            if (incomingResult.notificationFirst) {
                onMinimizeActiveFlow?.invoke()
                return@LaunchedEffect
            }
            flowState.updateNotificationOnlyIncomingResolve(false)
            flowState.applyCoordinatorResult(incomingResult.coordinatorResult)
            val toastResId = incomingResult.toastResId
            if (toastResId != null) {
                if (incomingResult.toastMessage.isBlank()) {
                    toast(context, toastResId)
                } else {
                    toast(context, toastResId, incomingResult.toastMessage)
                }
            }
        } finally {
            flowState.updateResolving(false)
            flowState.updateIncomingResolveRunning(false)
            flowState.updateNotificationOnlyIncomingResolve(false)
        }
    }

    LaunchedEffect(flowState.pendingTrack?.armedAtMillis) {
        val armedAtMillis = flowState.pendingTrack?.armedAtMillis ?: return@LaunchedEffect
        AppPackageChangedEvents.events.collect { event ->
            val packageName = event.packageName.trim()
            if (packageName.isBlank()) return@collect
            val currentPending = flowState.pendingTrack ?: return@collect
            if (currentPending.armedAtMillis != armedAtMillis) return@collect
            if (event.action !in shareImportAttachActions) return@collect
            val expectedPackageName = currentPending.packageName.trim()
            if (expectedPackageName.isNotBlank() && packageName != expectedPackageName) {
                return@collect
            }

            when (
                val result =
                    GitHubShareImportFlowCoordinator.refreshPendingInstall(context, event)
            ) {
                is ShareImportCoordinatorResult.Pending,
                ShareImportCoordinatorResult.None,
                -> {
                    Unit
                }

                else -> {
                    flowState.applyCoordinatorResult(result)
                    if (result is ShareImportCoordinatorResult.AlreadyTracked) {
                        toast(context, R.string.github_toast_share_import_track_exists)
                    }
                }
            }
        }
    }

    LaunchedEffect(flowState.attachCandidate) {
        val candidate = flowState.attachCandidate
        if (candidate == null) {
            flowState.updateAttachDuplicateExists(false)
            flowState.updateAttachSubmitting(submitting = false, confirmAndOpen = false)
            return@LaunchedEffect
        }
        flowState.updatePhase(GitHubShareImportPhase.InstallDetected)
        flowState.updateAttachDuplicateExists(windowCoordinator.hasAttachDuplicate(candidate))
    }
    BindGitHubShareImportIdleCallback(
        restoringActiveFlow = flowState.restoringActiveFlow,
        resolving = flowState.resolving,
        incomingResolveRunning = flowState.incomingResolveRunning,
        pendingPreview = flowState.pendingPreview,
        pendingTrack = flowState.pendingTrack,
        attachCandidate = flowState.attachCandidate,
        incomingGitHubShareText = incomingGitHubShareText,
        onIdleWithNoPendingFlow = onIdleWithNoPendingFlow,
    )

    GitHubShareImportWindowSheetHost(
        snapshot = snapshot,
        pendingArmedSheetVisible = pendingArmedSheetVisible,
        onMinimizeActiveFlow = { onMinimizeActiveFlow?.invoke() },
        onCancelPreview = {
            scope.launch {
                flowState.applyCoordinatorResult(
                    GitHubShareImportFlowCoordinator.cancelActiveFlow(context),
                )
            }
            flowState.clearAfterPreviewCancel()
        },
        onConfirmImport = { selectedAsset ->
            scope.launch {
                val preview = flowState.pendingPreview ?: return@launch
                flowState.updatePhase(GitHubShareImportPhase.Delivering)
                when (
                    val delivery =
                        installFlowCoordinator.startSelectedAssetDelivery(
                            context = context,
                            preview = preview,
                            selectedAsset = selectedAsset,
                            currentManagedProgress = flowState.managedInstallProgress,
                            onManagedInstallProgress = { progress ->
                                withContext(Dispatchers.Main.immediate) {
                                    flowState.updateManagedInstallProgress(progress)
                                }
                            },
                        )
                ) {
                    is GitHubShareImportSelectedAssetDeliveryResult.LaunchingManagedInstall -> {
                        flowState.applyCoordinatorResult(
                            ShareImportCoordinatorResult.AssetReady(
                                preview = delivery.selectedPreview,
                                sendInstallActionEnabled = delivery.selectedPreview.sendInstallActionEnabled,
                            ),
                        )
                        flowState.updateManagedInstallProgress(delivery.progress)
                    }

                    is GitHubShareImportSelectedAssetDeliveryResult.CommittingManagedInstall -> {
                        flowState.updateManagedInstallProgress(delivery.progress)
                    }

                    is GitHubShareImportSelectedAssetDeliveryResult.Delivered -> {
                        val result = delivery.result
                        flowState.applyDeliveryResult(
                            delivery = result,
                            preview = preview,
                            selectedAssetSizeBytes = selectedAsset.sizeBytes,
                        )
                        if (result is ShareImportDeliveryCoordinatorResult.Failed) {
                            if (result.toastMessage.isBlank()) {
                                toast(context, result.toastResId)
                            } else {
                                toast(context, result.toastResId, result.toastMessage)
                            }
                            return@launch
                        }
                        if (result is ShareImportDeliveryCoordinatorResult.WaitingInstall) {
                            toast(
                                context,
                                R.string.github_toast_share_import_wait_install,
                                result.assetName,
                            )
                        }
                    }
                }
            }
        },
        onClosePendingArmedSheet = {
            onClosePendingArmedSheet?.invoke()
        },
        onCancelPending = {
            scope.launch {
                flowState.applyCoordinatorResult(
                    GitHubShareImportFlowCoordinator.cancelActiveFlow(context),
                )
                toast(context, R.string.github_toast_share_import_pending_cancelled)
            }
        },
        onCancelAttach = {
            scope.launch {
                flowState.applyCoordinatorResult(
                    GitHubShareImportFlowCoordinator.cancelActiveFlow(context),
                )
            }
        },
        onConfirmAttach = {
            flowState.updateAttachSubmitting(submitting = true, confirmAndOpen = false)
            flowState.updatePhase(GitHubShareImportPhase.AddingTrack)
            scope.launch {
                try {
                    val result =
                        GitHubShareImportFlowCoordinator.confirmActiveAttachCandidate(context)
                    toastAttachConfirmResult(context, result)
                    flowState.applyCoordinatorResult(result)
                } finally {
                    flowState.updateAttachSubmitting(submitting = false, confirmAndOpen = false)
                }
            }
        },
        onConfirmAttachAndOpenGitHub = {
            flowState.updateAttachSubmitting(submitting = true, confirmAndOpen = true)
            flowState.updatePhase(GitHubShareImportPhase.AddingTrack)
            scope.launch {
                try {
                    val result =
                        GitHubShareImportFlowCoordinator.confirmActiveAttachCandidate(
                            context = context,
                            prefetchLatestCheck = false,
                        )
                    toastAttachConfirmResult(context, result)
                    flowState.applyCoordinatorResult(result)
                    if (
                        result is ShareImportCoordinatorResult.Added ||
                        result is ShareImportCoordinatorResult.AlreadyTracked
                    ) {
                        runCatching {
                            onNavigateToGitHubPage()
                        }.onFailure {
                            toast(context, R.string.common_open_link_failed)
                        }
                    }
                } finally {
                    flowState.updateAttachSubmitting(submitting = false, confirmAndOpen = false)
                }
            }
        },
    )
}

internal fun shouldUseNotificationFirstFlow(
    flowMode: GitHubShareImportFlowMode,
    assetCount: Int,
): Boolean = flowMode == GitHubShareImportFlowMode.NotificationFirst && assetCount > 0

private fun toastAttachConfirmResult(
    context: Context,
    result: ShareImportCoordinatorResult,
) {
    when (result) {
        ShareImportCoordinatorResult.None,
        is ShareImportCoordinatorResult.AssetReady,
        is ShareImportCoordinatorResult.Pending,
        is ShareImportCoordinatorResult.Detected,
        is ShareImportCoordinatorResult.Cancelled,
        -> {
            Unit
        }

        is ShareImportCoordinatorResult.AlreadyTracked -> {
            toast(context, R.string.github_toast_share_import_track_exists)
        }

        is ShareImportCoordinatorResult.Failed -> {
            toast(context, R.string.github_toast_share_import_failed, result.message)
        }

        is ShareImportCoordinatorResult.Added -> {
            toast(context, R.string.github_toast_share_import_track_added, result.appLabel)
        }
    }
}
