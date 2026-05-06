package os.kei.ui.page.main.github.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper
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
    val handledAtByPackage = remember { mutableStateMapOf<String, Long>() }
    val installReconciler = remember(context) {
        GitHubShareImportInstallReconciler(context.applicationContext)
    }

    LaunchedEffect(Unit) {
        GitHubTrackStoreSignals.version.collect {
            val storedTrack = withContext(Dispatchers.IO) {
                GitHubTrackStore.loadPendingShareImportTrack()
            }
            val currentTrack = pendingTrack
            if (
                currentTrack != null &&
                storedTrack?.armedAtMillis != currentTrack.armedAtMillis
            ) {
                pendingTrack = null
                if (phase == GitHubShareImportPhase.WaitingInstall) {
                    phase = GitHubShareImportPhase.Idle
                }
            }
            if (pendingPreview != null) {
                val storedPreview = withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.loadActivePreview()
                }
                if (storedPreview == null) {
                    pendingPreview = null
                    if (phase == GitHubShareImportPhase.AssetReady) {
                        phase = GitHubShareImportPhase.Idle
                    }
                }
            }
            if (attachCandidate != null) {
                val storedCandidate = withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.loadActiveAttachCandidate()
                }
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
                withContext(Dispatchers.IO) {
                    GitHubTrackStore.savePendingShareImportTrack(null)
                    GitHubShareImportFlowStore.clearActiveFlow()
                }
                saveShareImportResult(
                    loaded.toShareImportResult(
                        kind = GitHubShareImportResultKind.Cancelled,
                        message = context.getString(R.string.github_share_import_notify_content_cancelled)
                    )
                )
                pendingTrack = null
                pendingPreview = null
                GitHubShareImportNotificationHelper.cancel(context)
            } else {
                pendingPreview = null
                attachCandidate = null
                withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.clearActiveFlow()
                }
                pendingTrack = loaded
                phase = GitHubShareImportPhase.WaitingInstall
                notifyShareImportWaitingInstall(context, loaded)
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
                withContext(Dispatchers.IO) {
                    GitHubTrackStore.savePendingShareImportTrack(null)
                    GitHubShareImportFlowStore.clearActiveFlow()
                }
                saveShareImportResult(
                    current.toShareImportResult(
                        kind = GitHubShareImportResultKind.Cancelled,
                        message = context.getString(R.string.github_share_import_notify_content_cancelled)
                    )
                )
                pendingTrack = null
                pendingPreview = null
                phase = GitHubShareImportPhase.Idle
                GitHubShareImportNotificationHelper.notifyCancelled(context)
                return@LaunchedEffect
            }
            notifyShareImportWaitingInstall(context, current)
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

            when (val result = installReconciler.reconcileRecentInstall(currentPending)) {
                ShareImportInstallReconcileResult.None,
                ShareImportInstallReconcileResult.Expired -> Unit

                is ShareImportInstallReconcileResult.Duplicate -> {
                    val candidate = result.candidate
                    withContext(Dispatchers.IO) {
                        GitHubTrackStore.savePendingShareImportTrack(null)
                        GitHubShareImportFlowStore.clearActiveFlow()
                    }
                    saveShareImportResult(
                        candidate.toShareImportResult(GitHubShareImportResultKind.AlreadyTracked)
                    )
                    pendingTrack = null
                    notifyShareImportAlreadyTracked(context, candidate)
                    toast(context, R.string.github_toast_share_import_track_exists)
                    return@LaunchedEffect
                }

                is ShareImportInstallReconcileResult.Detected -> {
                    val candidate = result.candidate
                    attachCandidate = candidate
                    withContext(Dispatchers.IO) {
                        GitHubTrackStore.savePendingShareImportTrack(null)
                        GitHubShareImportFlowStore.clearActivePreview()
                        GitHubShareImportFlowStore.saveActiveAttachCandidate(
                            candidate.toPendingAttachCandidateRecord()
                        )
                    }
                    GitHubTrackStoreSignals.notifyChanged()
                    pendingTrack = null
                    phase = GitHubShareImportPhase.InstallDetected
                    notifyShareImportInstallDetected(context, candidate)
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
                handledAtByPackage.clear()
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
                        preferredAssetName = plan.preferredAssetName
                    )
                    withContext(Dispatchers.IO) {
                        GitHubShareImportFlowStore.saveActivePreview(preview.toPendingPreviewRecord())
                    }
                    GitHubTrackStoreSignals.notifyChanged()
                    pendingPreview = preview
                    notifyShareImportAssetReady(context, preview)
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

            val lastHandledAt = handledAtByPackage[packageName] ?: 0L
            if ((event.atMillis - lastHandledAt).coerceAtLeast(0L) < shareImportMinHandleIntervalMs) {
                return@collect
            }
            handledAtByPackage[packageName] = event.atMillis

            when (
                val result = installReconciler.reconcilePackageEvent(
                    pendingTrack = currentPending,
                    event = event,
                    currentCandidate = attachCandidate
                )
            ) {
                ShareImportInstallReconcileResult.None -> Unit
                ShareImportInstallReconcileResult.Expired -> {
                    withContext(Dispatchers.IO) {
                        GitHubTrackStore.savePendingShareImportTrack(null)
                        GitHubShareImportFlowStore.clearActiveFlow()
                    }
                    saveShareImportResult(
                        currentPending.toShareImportResult(
                            kind = GitHubShareImportResultKind.Cancelled,
                            message = context.getString(R.string.github_share_import_notify_content_cancelled)
                        )
                    )
                    pendingTrack = null
                    return@collect
                }

                is ShareImportInstallReconcileResult.Duplicate -> {
                    val candidate = result.candidate
                    withContext(Dispatchers.IO) {
                        GitHubTrackStore.savePendingShareImportTrack(null)
                        GitHubShareImportFlowStore.clearActiveFlow()
                    }
                    saveShareImportResult(
                        candidate.toShareImportResult(GitHubShareImportResultKind.AlreadyTracked)
                    )
                    pendingTrack = null
                    notifyShareImportAlreadyTracked(context, candidate)
                    toast(context, R.string.github_toast_share_import_track_exists)
                    return@collect
                }

                is ShareImportInstallReconcileResult.Detected -> {
                    val candidate = result.candidate
                    attachCandidate = candidate
                    withContext(Dispatchers.IO) {
                        GitHubTrackStore.savePendingShareImportTrack(null)
                        GitHubShareImportFlowStore.clearActivePreview()
                        GitHubShareImportFlowStore.saveActiveAttachCandidate(
                            candidate.toPendingAttachCandidateRecord()
                        )
                    }
                    GitHubTrackStoreSignals.notifyChanged()
                    pendingTrack = null
                    phase = GitHubShareImportPhase.InstallDetected
                    notifyShareImportInstallDetected(context, candidate)
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
            val result = pendingPreview?.toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = context.getString(R.string.github_share_import_notify_content_cancelled)
            )
            scope.launch {
                withContext(Dispatchers.IO) {
                    GitHubShareImportFlowStore.clearActiveFlow()
                }
                if (result != null) {
                    saveShareImportResult(result)
                } else {
                    GitHubTrackStoreSignals.notifyChanged()
                }
            }
            pendingPreview = null
            phase = GitHubShareImportPhase.Idle
            GitHubShareImportNotificationHelper.notifyCancelled(context)
        },
        onConfirmImport = { selectedAsset ->
            scope.launch {
                val preview = pendingPreview ?: return@launch
                val lookupConfig = withContext(Dispatchers.IO) { GitHubTrackStore.loadLookupConfig() }
                phase = GitHubShareImportPhase.Delivering
                notifyShareImportDelivering(context, preview, selectedAsset.name)
                val scannedPackageNameDeferred = scope.async {
                    scanShareImportAssetPackageName(
                        asset = selectedAsset,
                        lookupConfig = lookupConfig
                    ).getOrDefault("")
                }
                val deliveryResult = sendAssetToConfiguredChannel(
                    context = context,
                    lookupConfig = lookupConfig,
                    asset = selectedAsset
                )
                when (deliveryResult) {
                    is ShareImportDeliveryResult.Failure -> {
                        scannedPackageNameDeferred.cancel()
                        phase = GitHubShareImportPhase.Failed
                        notifyShareImportFailed(
                            context = context,
                            reason = context.getString(deliveryResult.toastResId)
                        )
                        toast(context, deliveryResult.toastResId)
                        return@launch
                    }
                    is ShareImportDeliveryResult.Success -> {
                        toast(context, deliveryResult.toastResId)
                    }
                }

                val scannedPackageName = scannedPackageNameDeferred.await()
                val pending = GitHubPendingShareImportTrackRecord(
                    projectUrl = preview.projectUrl,
                    owner = preview.owner,
                    repo = preview.repo,
                    releaseTag = preview.releaseTag,
                    assetName = selectedAsset.name,
                    packageName = scannedPackageName,
                    armedAtMillis = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    GitHubTrackStore.savePendingShareImportTrack(pending)
                    GitHubShareImportFlowStore.clearActiveFlow()
                }
                GitHubTrackStoreSignals.notifyChanged()
                pendingTrack = pending
                attachCandidate = null
                handledAtByPackage.clear()
                pendingPreview = null
                phase = GitHubShareImportPhase.WaitingInstall
                notifyShareImportWaitingInstall(context, pending)
                toast(context, R.string.github_toast_share_import_wait_install, selectedAsset.name)
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
            val result = pendingTrack?.toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = context.getString(R.string.github_share_import_notify_content_cancelled)
            )
            scope.launch {
                withContext(Dispatchers.IO) {
                    GitHubTrackStore.savePendingShareImportTrack(null)
                    GitHubShareImportFlowStore.clearActiveFlow()
                }
                if (result != null) {
                    saveShareImportResult(result)
                } else {
                    GitHubTrackStoreSignals.notifyChanged()
                }
                pendingTrack = null
                phase = GitHubShareImportPhase.Idle
                GitHubShareImportNotificationHelper.notifyCancelled(context)
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
                val result = attachCandidate?.toShareImportResult(
                    kind = GitHubShareImportResultKind.Cancelled,
                    message = context.getString(R.string.github_share_import_notify_content_cancelled)
                )
                scope.launch {
                    withContext(Dispatchers.IO) {
                        GitHubShareImportFlowStore.clearActiveAttachCandidate()
                    }
                    if (result != null) {
                        saveShareImportResult(result)
                    } else {
                        GitHubTrackStoreSignals.notifyChanged()
                    }
                }
                attachCandidate = null
                GitHubShareImportNotificationHelper.notifyCancelled(context)
            }
        },
        onConfirm = {
            if (attachSubmitting) return@GitHubShareImportAttachConfirmSheet
            val candidate = attachCandidate ?: return@GitHubShareImportAttachConfirmSheet
            attachSubmitting = true
            attachSubmittingAndOpen = false
            phase = GitHubShareImportPhase.AddingTrack
            notifyShareImportAddingTrack(context, candidate)
            scope.launch {
                try {
                    when (val result = attachCandidateToTracked(context, candidate)) {
                        ShareImportAttachResult.Duplicate -> {
                            toast(context, R.string.github_toast_share_import_track_exists)
                            notifyShareImportAlreadyTracked(context, candidate)
                            withContext(Dispatchers.IO) {
                                GitHubShareImportFlowStore.clearActiveAttachCandidate()
                            }
                            saveShareImportResult(
                                candidate.toShareImportResult(GitHubShareImportResultKind.AlreadyTracked)
                            )
                            attachCandidate = null
                            phase = GitHubShareImportPhase.Added
                        }
                        is ShareImportAttachResult.Failed -> {
                            toast(context, R.string.github_toast_share_import_failed, result.message)
                            notifyShareImportFailed(context, result.message)
                            phase = GitHubShareImportPhase.Failed
                        }
                        is ShareImportAttachResult.Added -> {
                            toast(context, R.string.github_toast_share_import_track_added, result.appLabel)
                            notifyShareImportAdded(context, candidate, result.appLabel)
                            withContext(Dispatchers.IO) {
                                GitHubShareImportFlowStore.clearActiveAttachCandidate()
                            }
                            saveShareImportResult(
                                candidate.toShareImportResult(
                                    kind = GitHubShareImportResultKind.Added,
                                    appLabelOverride = result.appLabel
                                )
                            )
                            attachCandidate = null
                            phase = GitHubShareImportPhase.Added
                        }
                    }
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
            notifyShareImportAddingTrack(context, candidate)
            scope.launch {
                try {
                    when (
                        val result = attachCandidateToTracked(
                            context = context,
                            candidate = candidate,
                            prefetchLatestCheck = false
                        )
                    ) {
                        ShareImportAttachResult.Duplicate -> {
                            toast(context, R.string.github_toast_share_import_track_exists)
                            notifyShareImportAlreadyTracked(context, candidate)
                            withContext(Dispatchers.IO) {
                                GitHubShareImportFlowStore.clearActiveAttachCandidate()
                            }
                            saveShareImportResult(
                                candidate.toShareImportResult(GitHubShareImportResultKind.AlreadyTracked)
                            )
                            attachCandidate = null
                            phase = GitHubShareImportPhase.Added
                        }
                        is ShareImportAttachResult.Failed -> {
                            toast(context, R.string.github_toast_share_import_failed, result.message)
                            notifyShareImportFailed(context, result.message)
                            phase = GitHubShareImportPhase.Failed
                        }
                        is ShareImportAttachResult.Added -> {
                            toast(context, R.string.github_toast_share_import_track_added, result.appLabel)
                            notifyShareImportAdded(context, candidate, result.appLabel)
                            withContext(Dispatchers.IO) {
                                GitHubShareImportFlowStore.clearActiveAttachCandidate()
                            }
                            saveShareImportResult(
                                candidate.toShareImportResult(
                                    kind = GitHubShareImportResultKind.Added,
                                    appLabelOverride = result.appLabel
                                )
                            )
                            attachCandidate = null
                            phase = GitHubShareImportPhase.Added
                            runCatching {
                                onNavigateToGitHubPage()
                            }.onFailure {
                                toast(context, R.string.common_open_link_failed)
                            }
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
