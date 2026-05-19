package os.kei.ui.page.main.github.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
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
    onIdleWithNoPendingFlow: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = remember {
        CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    }
    val windowCoordinator = remember { GitHubShareImportWindowCoordinator() }
    val installFlowCoordinator = remember { GitHubShareImportInstallFlowCoordinator() }
    val latestOnActivityBackInterceptionChanged by rememberUpdatedState(
        onActivityBackInterceptionChanged
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
    var pendingPreview by remember { mutableStateOf<GitHubShareImportPreview?>(null) }
    var resolving by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(GitHubShareImportPhase.Idle) }
    var incomingResolveRunning by remember { mutableStateOf(false) }
    var pendingTrack by remember { mutableStateOf<GitHubPendingShareImportTrackRecord?>(null) }
    var attachCandidate by remember { mutableStateOf<GitHubPendingShareImportAttachCandidate?>(null) }
    var managedInstallProgress by remember {
        mutableStateOf<GitHubShareImportManagedInstallProgress?>(null)
    }
    var attachDuplicateExists by remember { mutableStateOf(false) }
    var attachSubmitting by remember { mutableStateOf(false) }
    var attachSubmittingAndOpen by remember { mutableStateOf(false) }
    var restoringActiveFlow by remember { mutableStateOf(true) }
    var notificationOnlyIncomingResolve by remember { mutableStateOf(false) }
    fun applyStoredSnapshot(snapshot: GitHubShareImportStoredFlowSnapshot) {
        pendingTrack = snapshot.pendingTrack
        pendingPreview = snapshot.preview
        managedInstallProgress = snapshot.managedInstallProgress
        attachCandidate = snapshot.attachCandidate
        phase = snapshot.phase
    }

    fun applyCoordinatorResult(result: ShareImportCoordinatorResult) {
        phase = result.toShareImportPhase()
        when (result) {
            ShareImportCoordinatorResult.None -> Unit
            is ShareImportCoordinatorResult.AssetReady -> {
                pendingPreview = result.preview
                pendingTrack = null
                attachCandidate = null
                managedInstallProgress = null
            }

            is ShareImportCoordinatorResult.Pending -> {
                pendingTrack = result.pending
                pendingPreview = null
                attachCandidate = null
                managedInstallProgress = null
            }

            is ShareImportCoordinatorResult.Detected -> {
                pendingTrack = null
                pendingPreview = null
                attachCandidate = result.candidate
                managedInstallProgress = null
            }

            is ShareImportCoordinatorResult.Added,
            is ShareImportCoordinatorResult.AlreadyTracked -> {
                pendingTrack = null
                pendingPreview = null
                attachCandidate = null
                managedInstallProgress = null
            }

            is ShareImportCoordinatorResult.Cancelled -> {
                pendingTrack = null
                pendingPreview = null
                attachCandidate = null
                managedInstallProgress = null
            }

            is ShareImportCoordinatorResult.Failed -> Unit
        }
    }

    LaunchedEffect(notificationOnlyIncomingResolve) {
        onNotificationOnlyResolveChanged?.invoke(notificationOnlyIncomingResolve)
    }
    val pendingArmedSheetVisible = showPendingArmedSheet &&
            pendingTrack != null &&
            pendingPreview == null &&
            !resolving &&
            attachCandidate == null
    LaunchedEffect(pendingArmedSheetVisible) {
        latestOnActivityBackInterceptionChanged?.invoke(pendingArmedSheetVisible)
    }

    LaunchedEffect(Unit) {
        GitHubTrackStoreSignals.version.collect {
            if (!resolving && !incomingResolveRunning && !attachSubmitting) {
                applyStoredSnapshot(windowCoordinator.loadStoredSnapshot())
            }
        }
    }

    LaunchedEffect(resumeRequestToken) {
        restoringActiveFlow = true
        try {
            if (!incomingGitHubShareText.isNullOrBlank()) {
                return@LaunchedEffect
            }
            if (resolving || incomingResolveRunning || attachCandidate != null) {
                return@LaunchedEffect
            }
            when (val restored = windowCoordinator.restoreActiveFlow(context)) {
                is GitHubShareImportRestoreResult.Snapshot -> {
                    applyStoredSnapshot(restored.snapshot)
                }

                is GitHubShareImportRestoreResult.Coordinator -> {
                    applyCoordinatorResult(restored.result)
                }
            }
        } finally {
            restoringActiveFlow = false
        }
    }

    LaunchedEffect(pendingTrack?.armedAtMillis, attachCandidate?.packageName) {
        val armedAtMillis = pendingTrack?.armedAtMillis ?: return@LaunchedEffect
        if (attachCandidate != null) return@LaunchedEffect
        when (val result = windowCoordinator.awaitPendingResolution(context, armedAtMillis)) {
            is ShareImportCoordinatorResult.Pending,
            ShareImportCoordinatorResult.None -> Unit

            else -> {
                applyCoordinatorResult(result)
                if (result is ShareImportCoordinatorResult.AlreadyTracked) {
                    toast(context, R.string.github_toast_share_import_track_exists)
                }
            }
        }
    }

    LaunchedEffect(incomingGitHubShareToken) {
        val sharedText = incomingGitHubShareText?.trim().orEmpty()
        if (sharedText.isBlank()) return@LaunchedEffect
        if (resolving || incomingResolveRunning) return@LaunchedEffect
        incomingResolveRunning = true
        onIncomingGitHubShareConsumed()
        pendingPreview = null
        pendingTrack = null
        attachCandidate = null
        managedInstallProgress = null
        attachDuplicateExists = false
        withContext(AppDispatchers.githubNetwork) {
            GitHubShareImportFlowStore.clearActiveFlow()
        }
        try {
            resolving = true
            phase = GitHubShareImportPhase.Resolving
            val incomingResult = GitHubShareImportFlowCoordinator.startIncomingShare(
                context = context,
                sharedText = sharedText
            )
            if (incomingResult.notificationFirst) {
                onMinimizeActiveFlow?.invoke()
                return@LaunchedEffect
            }
            notificationOnlyIncomingResolve = false
            applyCoordinatorResult(incomingResult.coordinatorResult)
            val toastResId = incomingResult.toastResId
            if (toastResId != null) {
                if (incomingResult.toastMessage.isBlank()) {
                    toast(context, toastResId)
                } else {
                    toast(context, toastResId, incomingResult.toastMessage)
                }
            }
        } finally {
            resolving = false
            incomingResolveRunning = false
            notificationOnlyIncomingResolve = false
        }
    }

    LaunchedEffect(pendingTrack?.armedAtMillis) {
        val armedAtMillis = pendingTrack?.armedAtMillis ?: return@LaunchedEffect
        AppPackageChangedEvents.events.collect { event ->
            val packageName = event.packageName.trim()
            if (packageName.isBlank()) return@collect
            val currentPending = pendingTrack ?: return@collect
            if (currentPending.armedAtMillis != armedAtMillis) return@collect
            if (event.action !in shareImportAttachActions) return@collect
            val expectedPackageName = currentPending.packageName.trim()
            if (expectedPackageName.isNotBlank() && packageName != expectedPackageName) {
                return@collect
            }

            when (val result =
                GitHubShareImportFlowCoordinator.refreshPendingInstall(context, event)) {
                is ShareImportCoordinatorResult.Pending,
                ShareImportCoordinatorResult.None -> Unit

                else -> {
                    applyCoordinatorResult(result)
                    if (result is ShareImportCoordinatorResult.AlreadyTracked) {
                        toast(context, R.string.github_toast_share_import_track_exists)
                    }
                }
            }
        }
    }

    LaunchedEffect(attachCandidate) {
        val candidate = attachCandidate
        if (candidate == null) {
            attachDuplicateExists = false
            attachSubmitting = false
            attachSubmittingAndOpen = false
            return@LaunchedEffect
        }
        phase = GitHubShareImportPhase.InstallDetected
        attachDuplicateExists = windowCoordinator.hasAttachDuplicate(candidate)
    }
    BindGitHubShareImportIdleCallback(
        restoringActiveFlow = restoringActiveFlow,
        resolving = resolving,
        incomingResolveRunning = incomingResolveRunning,
        pendingPreview = pendingPreview,
        pendingTrack = pendingTrack,
        attachCandidate = attachCandidate,
        incomingGitHubShareText = incomingGitHubShareText,
        onIdleWithNoPendingFlow = onIdleWithNoPendingFlow
    )

    GitHubShareImportSheet(
        preview = pendingPreview,
        resolving = resolving && !notificationOnlyIncomingResolve,
        phase = phase,
        managedInstallProgress = managedInstallProgress,
        onDismissRequest = {
            if (!resolving && pendingPreview != null) {
                onMinimizeActiveFlow?.invoke()
            }
        },
        onCancel = {
            scope.launch {
                applyCoordinatorResult(
                    GitHubShareImportFlowCoordinator.cancelActiveFlow(context)
                )
            }
            pendingPreview = null
            managedInstallProgress = null
            phase = GitHubShareImportPhase.Idle
        },
        onConfirmImport = { selectedAsset ->
            scope.launch {
                val preview = pendingPreview ?: return@launch
                phase = GitHubShareImportPhase.Delivering
                when (
                    val delivery = installFlowCoordinator.startSelectedAssetDelivery(
                        context = context,
                        preview = preview,
                        selectedAsset = selectedAsset,
                        currentManagedProgress = managedInstallProgress,
                        onManagedInstallProgress = { progress ->
                            withContext(Dispatchers.Main.immediate) {
                                managedInstallProgress = progress
                                phase = progress.phase
                            }
                        }
                    )
                ) {
                    is GitHubShareImportSelectedAssetDeliveryResult.LaunchingManagedInstall -> {
                        pendingPreview = delivery.selectedPreview
                        managedInstallProgress = delivery.progress
                        phase = delivery.progress.phase
                    }

                    is GitHubShareImportSelectedAssetDeliveryResult.CommittingManagedInstall -> {
                        managedInstallProgress = delivery.progress
                        phase = delivery.progress.phase
                    }

                    is GitHubShareImportSelectedAssetDeliveryResult.Delivered -> {
                        managedInstallProgress = null
                        when (val result = delivery.result) {
                            ShareImportDeliveryCoordinatorResult.Cancelled -> {
                                phase = GitHubShareImportPhase.Idle
                                return@launch
                            }

                            is ShareImportDeliveryCoordinatorResult.Failed -> {
                                phase = GitHubShareImportPhase.Failed
                                if (result.toastMessage.isBlank()) {
                                    toast(context, result.toastResId)
                                } else {
                                    toast(context, result.toastResId, result.toastMessage)
                                }
                                return@launch
                            }

                            is ShareImportDeliveryCoordinatorResult.InstallReady -> {
                                phase = GitHubShareImportPhase.InstallReady
                                managedInstallProgress = GitHubShareImportManagedInstallProgress(
                                    phase = GitHubShareImportPhase.InstallReady,
                                    assetName = result.assetName,
                                    targetDisplayName = preview.targetDisplayName,
                                    progressPercent = 100,
                                    totalBytes = selectedAsset.sizeBytes
                                )
                            }

                            is ShareImportDeliveryCoordinatorResult.InstallDetected -> {
                                pendingTrack = null
                                attachCandidate = result.candidate
                                pendingPreview = null
                                phase = GitHubShareImportPhase.InstallDetected
                            }

                            is ShareImportDeliveryCoordinatorResult.WaitingInstall -> {
                                pendingTrack = result.pending
                                attachCandidate = null
                                pendingPreview = null
                                phase = GitHubShareImportPhase.WaitingInstall
                                toast(
                                    context,
                                    R.string.github_toast_share_import_wait_install,
                                    result.assetName
                                )
                            }
                        }
                    }
                }
            }
        }
    )
    GitHubShareImportPendingSheet(
        pending = if (pendingArmedSheetVisible) {
            pendingTrack
        } else {
            null
        },
        onDismissRequest = {},
        onClose = {
            onClosePendingArmedSheet?.invoke()
        },
        onCancel = {
            scope.launch {
                applyCoordinatorResult(
                    GitHubShareImportFlowCoordinator.cancelActiveFlow(context)
                )
                toast(context, R.string.github_toast_share_import_pending_cancelled)
            }
        }
    )

    GitHubShareImportAttachConfirmSheet(
        candidate = attachCandidate,
        duplicateExists = attachDuplicateExists,
        submitting = attachSubmitting,
        submittingAndOpen = attachSubmittingAndOpen,
        allowDismiss = !attachSubmitting,
        onDismissRequest = {
            if (!attachSubmitting && attachCandidate != null) {
                onMinimizeActiveFlow?.invoke()
            }
        },
        onCancel = {
            if (!attachSubmitting) {
                scope.launch {
                    applyCoordinatorResult(
                        GitHubShareImportFlowCoordinator.cancelActiveFlow(context)
                    )
                }
            }
        },
        onConfirm = {
            if (attachSubmitting) return@GitHubShareImportAttachConfirmSheet
            val candidate = attachCandidate ?: return@GitHubShareImportAttachConfirmSheet
            attachSubmitting = true
            attachSubmittingAndOpen = false
            phase = GitHubShareImportPhase.AddingTrack
            scope.launch {
                try {
                    val result =
                        GitHubShareImportFlowCoordinator.confirmActiveAttachCandidate(context)
                    when (result) {
                        ShareImportCoordinatorResult.None,
                        is ShareImportCoordinatorResult.AssetReady,
                        is ShareImportCoordinatorResult.Pending,
                        is ShareImportCoordinatorResult.Detected -> Unit

                        is ShareImportCoordinatorResult.AlreadyTracked -> {
                            toast(context, R.string.github_toast_share_import_track_exists)
                        }

                        is ShareImportCoordinatorResult.Failed -> {
                            toast(context, R.string.github_toast_share_import_failed, result.message)
                        }

                        is ShareImportCoordinatorResult.Added -> {
                            toast(context, R.string.github_toast_share_import_track_added, result.appLabel)
                        }

                        is ShareImportCoordinatorResult.Cancelled -> Unit
                    }
                    applyCoordinatorResult(result)
                } finally {
                    attachSubmitting = false
                    attachSubmittingAndOpen = false
                }
            }
        },
        onConfirmAndOpenGitHub = {
            if (attachSubmitting) return@GitHubShareImportAttachConfirmSheet
            val candidate = attachCandidate ?: return@GitHubShareImportAttachConfirmSheet
            attachSubmitting = true
            attachSubmittingAndOpen = true
            phase = GitHubShareImportPhase.AddingTrack
            scope.launch {
                try {
                    val result = GitHubShareImportFlowCoordinator.confirmActiveAttachCandidate(
                        context = context,
                        prefetchLatestCheck = false
                    )
                    when (result) {
                        ShareImportCoordinatorResult.None,
                        is ShareImportCoordinatorResult.AssetReady,
                        is ShareImportCoordinatorResult.Pending,
                        is ShareImportCoordinatorResult.Detected -> Unit

                        is ShareImportCoordinatorResult.AlreadyTracked -> {
                            toast(context, R.string.github_toast_share_import_track_exists)
                        }

                        is ShareImportCoordinatorResult.Failed -> {
                            toast(context, R.string.github_toast_share_import_failed, result.message)
                        }

                        is ShareImportCoordinatorResult.Added -> {
                            toast(context, R.string.github_toast_share_import_track_added, result.appLabel)
                        }

                        is ShareImportCoordinatorResult.Cancelled -> Unit
                    }
                    applyCoordinatorResult(result)
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
                    attachSubmitting = false
                    attachSubmittingAndOpen = false
                }
            }
        }
    )
}

internal fun shouldUseNotificationFirstFlow(
    flowMode: GitHubShareImportFlowMode,
    assetCount: Int
): Boolean {
    return flowMode == GitHubShareImportFlowMode.NotificationFirst && assetCount > 0
}
