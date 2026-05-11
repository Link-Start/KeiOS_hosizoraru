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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.system.AppPackageChangedEvents
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.model.GitHubShareImportFlowMode
import kotlin.time.Duration.Companion.milliseconds

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
    var attachDuplicateExists by remember { mutableStateOf(false) }
    var attachSubmitting by remember { mutableStateOf(false) }
    var attachSubmittingAndOpen by remember { mutableStateOf(false) }
    var restoringActiveFlow by remember { mutableStateOf(true) }
    var notificationOnlyIncomingResolve by remember { mutableStateOf(false) }
    fun applyCoordinatorResult(result: ShareImportCoordinatorResult) {
        phase = result.toShareImportPhase()
        when (result) {
            ShareImportCoordinatorResult.None -> Unit
            is ShareImportCoordinatorResult.AssetReady -> {
                pendingPreview = result.preview
                pendingTrack = null
                attachCandidate = null
            }

            is ShareImportCoordinatorResult.Pending -> {
                pendingTrack = result.pending
                pendingPreview = null
                attachCandidate = null
            }

            is ShareImportCoordinatorResult.Detected -> {
                pendingTrack = null
                pendingPreview = null
                attachCandidate = result.candidate
            }

            is ShareImportCoordinatorResult.Added,
            is ShareImportCoordinatorResult.AlreadyTracked -> {
                pendingTrack = null
                pendingPreview = null
                attachCandidate = null
            }

            is ShareImportCoordinatorResult.Cancelled -> {
                pendingTrack = null
                pendingPreview = null
                attachCandidate = null
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
            val storedTrack = withContext(Dispatchers.IO) {
                GitHubTrackStore.loadPendingShareImportTrack()
            }
            val currentTrack = pendingTrack
            if (storedTrack != null && storedTrack.armedAtMillis != currentTrack?.armedAtMillis) {
                pendingTrack = storedTrack
                pendingPreview = null
                attachCandidate = null
                phase = GitHubShareImportPhase.WaitingInstall
                return@collect
            }
            if (currentTrack != null && storedTrack == null) {
                pendingTrack = null
                if (phase == GitHubShareImportPhase.WaitingInstall) {
                    phase = GitHubShareImportPhase.Idle
                }
            }
            val storedPreview = withContext(Dispatchers.IO) {
                GitHubShareImportFlowStore.loadActivePreview()
            }?.toShareImportPreview()
            if (storedTrack == null && storedPreview != null) {
                if (pendingPreview?.sourceUrl != storedPreview.sourceUrl) {
                    pendingPreview = storedPreview
                    attachCandidate = null
                    phase = GitHubShareImportPhase.AssetReady
                }
            } else if (pendingPreview != null) {
                if (storedPreview == null) {
                    pendingPreview = null
                    if (phase == GitHubShareImportPhase.AssetReady) {
                        phase = GitHubShareImportPhase.Idle
                    }
                }
            }
            val storedCandidate = withContext(Dispatchers.IO) {
                GitHubShareImportFlowStore.loadActiveAttachCandidate()
            }?.toShareImportAttachCandidate()
            if (storedTrack == null && storedPreview == null && storedCandidate != null) {
                if (attachCandidate?.packageName != storedCandidate.packageName) {
                    pendingPreview = null
                    attachCandidate = storedCandidate
                    phase = GitHubShareImportPhase.InstallDetected
                }
            } else if (attachCandidate != null) {
                if (storedCandidate == null) {
                    attachCandidate = null
                    if (phase == GitHubShareImportPhase.InstallDetected) {
                        phase = GitHubShareImportPhase.Idle
                    }
                }
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
            val loaded = withContext(Dispatchers.IO) {
                GitHubTrackStore.loadPendingShareImportTrack()
            }
            if (loaded == null) {
                pendingTrack = null
                val restoredPreview = withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.loadActivePreview()
                }?.toShareImportPreview()
                if (restoredPreview != null) {
                    pendingPreview = restoredPreview
                    attachCandidate = null
                    phase = GitHubShareImportPhase.AssetReady
                    notifyShareImportAssetReady(context, restoredPreview)
                    return@LaunchedEffect
                }
                val restoredCandidate = withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.loadActiveAttachCandidate()
                }?.toShareImportAttachCandidate()
                if (restoredCandidate != null) {
                    pendingPreview = null
                    attachCandidate = restoredCandidate
                    phase = GitHubShareImportPhase.InstallDetected
                    notifyShareImportInstallDetected(context, restoredCandidate)
                }
                return@LaunchedEffect
            }
            val age = (System.currentTimeMillis() - loaded.armedAtMillis).coerceAtLeast(0L)
            if (age > shareImportTrackMaxAgeMs) {
                applyCoordinatorResult(
                    GitHubShareImportFlowCoordinator.cancelActiveFlow(context)
                )
            } else {
                pendingPreview = null
                attachCandidate = null
                withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.clearActiveFlow()
                }
                applyCoordinatorResult(
                    GitHubShareImportFlowCoordinator.refreshPendingInstall(context)
                )
            }
        } finally {
            restoringActiveFlow = false
        }
    }

    LaunchedEffect(pendingTrack?.armedAtMillis) {
        while (true) {
            val current = pendingTrack ?: return@LaunchedEffect
            val age = (System.currentTimeMillis() - current.armedAtMillis).coerceAtLeast(0L)
            if (age > shareImportTrackMaxAgeMs) {
                applyCoordinatorResult(
                    GitHubShareImportFlowCoordinator.cancelActiveFlow(context)
                )
                return@LaunchedEffect
            }
            val result = GitHubShareImportFlowCoordinator.refreshPendingInstall(context)
            applyCoordinatorResult(result)
            if (result !is ShareImportCoordinatorResult.Pending) return@LaunchedEffect
            val remainingMs = (shareImportTrackMaxAgeMs - age).coerceAtLeast(0L)
            delay(remainingMs.coerceAtMost(60_000L).coerceAtLeast(1_000L).milliseconds)
        }
    }
    LaunchedEffect(pendingTrack?.armedAtMillis, attachCandidate?.packageName) {
        val armedAtMillis = pendingTrack?.armedAtMillis ?: return@LaunchedEffect
        if (attachCandidate != null) return@LaunchedEffect
        while (true) {
            val currentPending = pendingTrack ?: return@LaunchedEffect
            if (currentPending.armedAtMillis != armedAtMillis) return@LaunchedEffect
            if (attachCandidate != null) return@LaunchedEffect

            when (val result = GitHubShareImportFlowCoordinator.refreshPendingInstall(context)) {
                is ShareImportCoordinatorResult.Pending,
                ShareImportCoordinatorResult.None -> Unit

                else -> {
                    applyCoordinatorResult(result)
                    if (result is ShareImportCoordinatorResult.AlreadyTracked) {
                        toast(context, R.string.github_toast_share_import_track_exists)
                    }
                    return@LaunchedEffect
                }
            }

            val pendingAge = (System.currentTimeMillis() - currentPending.armedAtMillis).coerceAtLeast(0L)
            if (pendingAge > shareImportTrackMaxAgeMs) return@LaunchedEffect
            delay(2_500.milliseconds)
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
        attachDuplicateExists = false
        withContext(Dispatchers.IO) {
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
        val candidateId = "${candidate.owner}/${candidate.repo}|${candidate.packageName}"
        attachDuplicateExists = withContext(Dispatchers.IO) {
            GitHubTrackStore.load().any { it.id == candidateId }
        }
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
            phase = GitHubShareImportPhase.Idle
        },
        onConfirmImport = { selectedAsset ->
            scope.launch {
                val preview = pendingPreview ?: return@launch
                val lookupConfig = withContext(Dispatchers.IO) { GitHubTrackStore.loadLookupConfig() }
                phase = if (lookupConfig.appManagedShareInstallEnabled) {
                    GitHubShareImportPhase.Installing
                } else {
                    GitHubShareImportPhase.Delivering
                }
                when (
                    val delivery = GitHubShareImportFlowCoordinator.startDelivery(
                        context = context,
                        preview = preview,
                        selectedAsset = selectedAsset,
                        lookupConfig = lookupConfig
                    )
                ) {
                    ShareImportDeliveryCoordinatorResult.Cancelled -> {
                        phase = GitHubShareImportPhase.Idle
                        return@launch
                    }

                    is ShareImportDeliveryCoordinatorResult.Failed -> {
                        phase = GitHubShareImportPhase.Failed
                        if (delivery.toastMessage.isBlank()) {
                            toast(context, delivery.toastResId)
                        } else {
                            toast(context, delivery.toastResId, delivery.toastMessage)
                        }
                        return@launch
                    }

                    is ShareImportDeliveryCoordinatorResult.InstallDetected -> {
                        pendingTrack = null
                        attachCandidate = delivery.candidate
                        pendingPreview = null
                        phase = GitHubShareImportPhase.InstallDetected
                    }

                    is ShareImportDeliveryCoordinatorResult.WaitingInstall -> {
                        pendingTrack = delivery.pending
                        attachCandidate = null
                        pendingPreview = null
                        phase = GitHubShareImportPhase.WaitingInstall
                        toast(
                            context,
                            R.string.github_toast_share_import_wait_install,
                            delivery.assetName
                        )
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
