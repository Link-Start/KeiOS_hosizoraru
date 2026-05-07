package os.kei.ui.page.main.github.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import os.kei.feature.github.data.remote.GitHubShareImportResolver
import os.kei.feature.github.data.remote.GitHubShareIntentParser
import os.kei.feature.github.model.GitHubShareImportFlowMode
import os.kei.ui.page.main.github.localizedGitHubShareImportErrorMessage
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun GitHubShareImportWindowFlowHost(
    incomingGitHubShareText: String?,
    incomingGitHubShareToken: Int,
    resumeRequestToken: Int = 0,
    onIncomingGitHubShareConsumed: () -> Unit,
    onNavigateToGitHubPage: () -> Unit,
    showPendingArmedSheet: Boolean = false,
    onMinimizeActiveFlow: (() -> Unit)? = null,
    onClosePendingArmedSheet: (() -> Unit)? = null,
    onIdleWithNoPendingFlow: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = remember {
        CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    }
    DisposableEffect(Unit) {
        onDispose {
            scope.cancel()
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
    fun applyCoordinatorResult(result: ShareImportCoordinatorResult) {
        when (result) {
            ShareImportCoordinatorResult.None -> Unit
            is ShareImportCoordinatorResult.AssetReady -> {
                pendingPreview = result.preview
                pendingTrack = null
                attachCandidate = null
                phase = GitHubShareImportPhase.AssetReady
            }

            is ShareImportCoordinatorResult.Pending -> {
                pendingTrack = result.pending
                pendingPreview = null
                attachCandidate = null
                phase = GitHubShareImportPhase.WaitingInstall
            }

            is ShareImportCoordinatorResult.Detected -> {
                pendingTrack = null
                pendingPreview = null
                attachCandidate = result.candidate
                phase = GitHubShareImportPhase.InstallDetected
            }

            is ShareImportCoordinatorResult.Added,
            is ShareImportCoordinatorResult.AlreadyTracked -> {
                pendingTrack = null
                pendingPreview = null
                attachCandidate = null
                phase = GitHubShareImportPhase.Added
            }

            is ShareImportCoordinatorResult.Failed -> {
                phase = GitHubShareImportPhase.Failed
            }

            is ShareImportCoordinatorResult.Cancelled -> {
                pendingTrack = null
                pendingPreview = null
                attachCandidate = null
                phase = GitHubShareImportPhase.Idle
            }
        }
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
        resolving = true
        phase = GitHubShareImportPhase.Resolving
        notifyShareImportResolving(context, sharedText)
        onIncomingGitHubShareConsumed()
        pendingPreview = null
        withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.clearActiveFlow()
        }
        try {
            val lookupConfig = withContext(Dispatchers.IO) { GitHubTrackStore.loadLookupConfig() }
            if (!lookupConfig.shareImportLinkageEnabled) {
                phase = GitHubShareImportPhase.Idle
                return@LaunchedEffect
            }
            try {
                val parsedIncoming = GitHubShareIntentParser.parseSharedReleaseLink(sharedText)
                    ?: error(context.getString(R.string.github_share_import_error_no_valid_link))
                withContext(Dispatchers.IO) {
                    GitHubTrackStore.savePendingShareImportTrack(null)
                    GitHubShareImportFlowStore.clearActiveFlow()
                }
                GitHubTrackStoreSignals.notifyChanged()
                pendingTrack = null
                attachCandidate = null
                attachDuplicateExists = false
                val plan = withContext(Dispatchers.IO) {
                    GitHubShareImportResolver.resolve(
                        sharedText = parsedIncoming.sourceUrl,
                        lookupConfig = lookupConfig
                    ).getOrThrow()
                }
                if (plan.assets.isEmpty()) {
                    withContext(Dispatchers.IO) {
                        GitHubShareImportFlowStore.clearActiveFlow()
                    }
                    phase = GitHubShareImportPhase.Failed
                    saveShareImportResult(
                        GitHubShareImportResult(
                            kind = GitHubShareImportResultKind.Failed,
                            projectUrl = plan.parsedLink.projectUrl,
                            owner = plan.parsedLink.owner,
                            repo = plan.parsedLink.repo,
                            message = context.getString(R.string.github_toast_share_import_no_apk)
                        )
                    )
                    notifyShareImportFailed(
                        context = context,
                        reason = context.getString(R.string.github_toast_share_import_no_apk)
                    )
                    toast(context, R.string.github_toast_share_import_no_apk)
                } else {
                    phase = GitHubShareImportPhase.AssetReady
                    val preview = GitHubShareImportPreview(
                        sourceUrl = plan.parsedLink.sourceUrl,
                        projectUrl = plan.parsedLink.projectUrl,
                        owner = plan.parsedLink.owner,
                        repo = plan.parsedLink.repo,
                        releaseTag = plan.resolvedReleaseTag,
                        releaseUrl = plan.resolvedReleaseUrl,
                        strategyLabel = lookupConfig.selectedStrategy.label,
                        assets = plan.assets,
                        preferredAssetName = plan.preferredAssetName,
                        targetDisplayName = buildShareImportTargetDisplayName(
                            repo = plan.parsedLink.repo,
                            assetName = plan.preferredAssetName.ifBlank {
                                plan.assets.singleOrNull()?.name.orEmpty()
                            }
                        )
                    )
                    val directNotificationSend =
                        lookupConfig.shareImportFlowMode == GitHubShareImportFlowMode.NotificationFirst &&
                                preview.assets.size == 1
                    applyCoordinatorResult(
                        GitHubShareImportFlowCoordinator.prepareAssetReady(
                            context = context,
                            preview = preview,
                            sendInstallActionEnabled = directNotificationSend
                        )
                    )
                    if (directNotificationSend) {
                        onMinimizeActiveFlow?.invoke()
                    }
                }
            } catch (error: Throwable) {
                if (error.shouldSuppressShareImportFailureToast()) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.clearActiveFlow()
                }
                phase = GitHubShareImportPhase.Failed
                val reason = localizedGitHubShareImportErrorMessage(
                    context = context,
                    rawMessage = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                )
                saveShareImportResult(
                    GitHubShareImportResult(
                        kind = GitHubShareImportResultKind.Failed,
                        message = reason
                    )
                )
                notifyShareImportFailed(context, reason)
                toast(context, R.string.github_toast_share_import_failed, reason)
            }
        } finally {
            resolving = false
            incomingResolveRunning = false
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
        resolving = resolving,
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
                phase = GitHubShareImportPhase.Delivering
                when (
                    val delivery = GitHubShareImportFlowCoordinator.startDelivery(
                        context = context,
                        preview = preview,
                        selectedAsset = selectedAsset,
                        lookupConfig = lookupConfig
                    )
                ) {
                    is ShareImportDeliveryCoordinatorResult.Failed -> {
                        phase = GitHubShareImportPhase.Failed
                        toast(context, delivery.toastResId)
                        return@launch
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
        pending = if (
            showPendingArmedSheet &&
            pendingTrack != null &&
            pendingPreview == null &&
            !resolving &&
            attachCandidate == null
        ) {
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

private suspend fun saveShareImportResult(result: GitHubShareImportResult) {
    withContext(Dispatchers.IO) {
        GitHubShareImportFlowStore.saveActiveResult(result.toRecord())
    }
    GitHubTrackStoreSignals.notifyChanged()
}
