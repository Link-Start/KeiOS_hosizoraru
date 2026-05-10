package os.kei.ui.page.main.github.install

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.install.ApkArchiveInspector
import os.kei.core.install.ApkInstallBackend
import os.kei.core.install.ApkInstallEntry
import os.kei.core.install.ApkInstallProgress
import os.kei.core.install.ApkInstallRequest
import os.kei.core.install.ApkInstallResult
import os.kei.core.install.LocalApkArchiveInfo
import os.kei.core.install.ShizukuSessionInstallBackend
import os.kei.core.intent.SafeExternalIntents
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.domain.ApkTrustEvaluationInput
import os.kei.feature.github.domain.ApkTrustEvaluator
import os.kei.feature.github.model.GitHubApkInstallUiMode
import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.notification.GitHubApkInstallNotificationHelper
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper
import os.kei.ui.page.main.github.share.GitHubShareImportFlowCoordinator
import os.kei.ui.page.main.github.share.resolvePreferredAssetUrl
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

internal object GitHubApkInstallFlowCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionIds = AtomicLong(1L)
    private val downloader = GitHubApkInstallFileDownloader()
    private val extractor = GitHubApkInstallArchiveExtractor()
    private val apkInfoRepository = GitHubApkInfoRepository()
    private val _state = MutableStateFlow(GitHubApkInstallFlowState())
    val state: StateFlow<GitHubApkInstallFlowState> = _state.asStateFlow()

    private var activeJob: Job? = null
    private var activeWork: ActiveInstallWork? = null
    private var lastRequest: LastInstallRequest? = null

    fun beginInstallAsset(
        context: Context,
        lookupConfig: GitHubLookupConfig,
        asset: GitHubReleaseAssetFile,
        request: GitHubApkInstallRequestContext,
        initialInstalledInfo: GitHubInstalledPackageInfo? = null
    ) {
        val appContext = context.applicationContext
        val snapshot = LastInstallRequest.Asset(appContext, lookupConfig, asset, request)
        lastRequest = snapshot
        val notificationFirst =
            lookupConfig.apkInstallUiMode == GitHubApkInstallUiMode.NotificationFirst
        val current = _state.value
        if (
            activeWork != null &&
            current.active &&
            !current.phase.isTerminalForAsyncUpdates() &&
            current.sameInstallTarget(asset, request)
        ) {
            updateState(
                current.copy(
                    sheetVisible = current.sheetVisible || !notificationFirst,
                    notificationFirst = notificationFirst
                )
            )
            return
        }
        activeJob?.cancel()
        activeWork?.cleanup()
        activeWork = null
        val sessionId = sessionIds.incrementAndGet()
        if (request.sourceKind == GitHubApkInstallSourceKind.ShareImport) {
            GitHubShareImportNotificationHelper.cancel(appContext)
        }
        updateState(
            GitHubApkInstallFlowState(
                sessionId = sessionId,
                phase = GitHubApkInstallPhase.Downloading,
                request = request,
                asset = asset,
                selectedCandidateName = asset.name,
                selectedCandidateSizeBytes = asset.sizeBytes,
                remoteManifestInfo = request.remoteManifestInfo,
                installedPackageInfo = initialInstalledInfo,
                trustSignal = evaluateTrust(
                    asset = asset,
                    request = request,
                    installedInfo = initialInstalledInfo,
                    archiveInfo = null
                ),
                progressKind = GitHubApkInstallProgressKind.Download,
                stageProgress = 0f,
                progress = 0f,
                overallProgress = installOverallProgress(
                    phase = GitHubApkInstallPhase.Downloading,
                    stageProgress = 0f
                ),
                sheetVisible = !notificationFirst,
                notificationFirst = notificationFirst
            )
        )
        activeJob = scope.launch {
            try {
                val resolvedUrl = resolvePreferredAssetUrl(lookupConfig, asset)
                val preparedRequest = request.copy(
                    externalUrl = resolvedUrl,
                    externalFileName = asset.name
                )
                activeWork = ActiveInstallWork(
                    appContext = appContext,
                    lookupConfig = lookupConfig,
                    asset = asset,
                    request = preparedRequest,
                    sessionId = sessionId,
                    notificationFirst = notificationFirst
                )
                val installedInfoProbe = launch(Dispatchers.IO) {
                    preheatInstalledInfo(activeWork!!)
                }
                val remoteManifestProbe = launch(Dispatchers.IO) {
                    preheatRemoteManifest(activeWork!!)
                }
                downloadAndPrepare(activeWork!!)
                installedInfoProbe.cancel()
                remoteManifestProbe.cancel()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failActive(
                    context = appContext,
                    sessionId = sessionId,
                    message = error.localizedInstallMessage(appContext),
                    rawMessage = error.message.orEmpty()
                )
            }
        }
    }

    fun beginPreparingInstall(
        context: Context,
        lookupConfig: GitHubLookupConfig,
        asset: GitHubReleaseAssetFile,
        request: GitHubApkInstallRequestContext
    ): Long {
        val appContext = context.applicationContext
        val snapshot = LastInstallRequest.Asset(appContext, lookupConfig, asset, request)
        lastRequest = snapshot
        activeJob?.cancel()
        activeWork?.cleanup()
        activeWork = null
        val sessionId = sessionIds.incrementAndGet()
        val notificationFirst =
            lookupConfig.apkInstallUiMode == GitHubApkInstallUiMode.NotificationFirst
        updateState(
            GitHubApkInstallFlowState(
                sessionId = sessionId,
                phase = GitHubApkInstallPhase.Downloading,
                request = request,
                asset = asset,
                selectedCandidateName = asset.name,
                selectedCandidateSizeBytes = asset.sizeBytes,
                remoteManifestInfo = request.remoteManifestInfo,
                trustSignal = evaluateTrust(
                    asset = asset,
                    request = request,
                    installedInfo = null,
                    archiveInfo = null
                ),
                progressKind = GitHubApkInstallProgressKind.Waiting,
                stageProgress = 0f,
                progress = 0f,
                overallProgress = 0.02f,
                sheetVisible = !notificationFirst,
                notificationFirst = notificationFirst,
                message = appContext.getString(R.string.github_apk_install_preparing_download)
            )
        )
        return sessionId
    }

    fun isActiveSession(sessionId: Long): Boolean {
        val current = _state.value
        return sessionId > 0L &&
                current.sessionId == sessionId &&
                current.active &&
                !current.phase.isTerminalForAsyncUpdates()
    }

    fun failPreparingInstall(
        context: Context,
        sessionId: Long,
        message: String,
        rawMessage: String = message
    ) {
        failActive(
            context = context.applicationContext,
            sessionId = sessionId,
            message = message,
            rawMessage = rawMessage
        )
    }

    fun beginInstallResolvedUrl(
        context: Context,
        lookupConfig: GitHubLookupConfig,
        resolvedUrl: String,
        fileName: String,
        sizeBytes: Long,
        request: GitHubApkInstallRequestContext
    ) {
        val asset = GitHubReleaseAssetFile(
            name = fileName,
            downloadUrl = resolvedUrl,
            sizeBytes = sizeBytes,
            downloadCount = 0
        )
        beginInstallAsset(
            context = context,
            lookupConfig = lookupConfig,
            asset = asset,
            request = request.copy(
                externalUrl = resolvedUrl,
                externalFileName = fileName
            )
        )
    }

    fun showSheet() {
        _state.update { current ->
            if (current.active) current.copy(sheetVisible = true) else current
        }
    }

    fun hideSheet() {
        _state.update { current -> current.copy(sheetVisible = false) }
    }

    fun selectCandidate(index: Int) {
        val work = activeWork ?: return
        val candidate = work.candidates.getOrNull(index) ?: return
        activeJob?.cancel()
        activeJob = scope.launch {
            inspectAndPrepareConfirmation(work, candidate)
        }
    }

    fun confirmInstall() {
        val work = activeWork ?: return
        val candidate = work.selectedCandidate ?: return
        activeJob?.cancel()
        activeJob = scope.launch {
            installCandidate(work, candidate)
        }
    }

    fun launchPendingUserAction(context: Context) {
        val intent = activeWork?.pendingUserAction ?: return
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun retry(context: Context) {
        when (val request = lastRequest) {
            is LastInstallRequest.Asset -> beginInstallAsset(
                context = context,
                lookupConfig = request.lookupConfig,
                asset = request.asset,
                request = request.request
            )

            null -> Unit
        }
    }

    fun cancel(context: Context) {
        val current = _state.value
        if (current.phase == GitHubApkInstallPhase.Idle) return
        val work = activeWork
        activeJob?.cancel()
        activeJob = null
        work?.cleanup()
        activeWork = null
        updateState(
            current.copy(
                phase = GitHubApkInstallPhase.Cancelled,
                progressKind = GitHubApkInstallProgressKind.None,
                progress = 0f,
                stageProgress = 0f,
                overallProgress = current.overallProgress.coerceIn(0f, 1f),
                message = context.getString(R.string.github_apk_install_cancelled),
                sheetVisible = false
            )
        )
    }

    fun markRead(context: Context) {
        activeJob?.cancel()
        activeJob = null
        activeWork?.cleanup()
        activeWork = null
        _state.value = GitHubApkInstallFlowState()
        GitHubApkInstallNotificationHelper.cancel(context.applicationContext)
    }

    fun openExternalCurrent(context: Context): Boolean {
        val current = activeWork?.request ?: _state.value.request
        val url = current.externalUrl.ifBlank { _state.value.asset?.downloadUrl.orEmpty() }
        return SafeExternalIntents.startBrowsableUrl(context, url)
    }

    private suspend fun downloadAndPrepare(work: ActiveInstallWork) {
        val resolvedUrl = work.request.externalUrl.ifBlank {
            resolvePreferredAssetUrl(work.lookupConfig, work.asset)
        }
        val downloaded = downloader.download(
            context = work.appContext,
            url = resolvedUrl,
            fileName = work.asset.name
        ) { done, total ->
            updateProgress(
                sessionId = work.sessionId,
                phase = GitHubApkInstallPhase.Downloading,
                kind = GitHubApkInstallProgressKind.Download,
                bytesDone = done,
                totalBytes = total
            )
        }
        work.downloadedFiles += downloaded
        val candidates = extractor.extractCandidates(work.appContext, downloaded)
        if (candidates.isEmpty()) {
            fail(
                context = work.appContext,
                message = work.appContext.getString(R.string.github_apk_install_error_no_apk)
            )
            return
        }
        work.candidates = candidates
        if (candidates.size > 1) {
            updateState(
                _state.value.copy(
                    phase = GitHubApkInstallPhase.SelectingApk,
                    candidates = candidates.mapIndexed { index, file ->
                        GitHubApkInstallCandidate(index, file.name, file.sizeBytes)
                    },
                    progressKind = GitHubApkInstallProgressKind.Waiting,
                    stageProgress = 1f,
                    progress = 0.44f,
                    overallProgress = installOverallProgress(
                        phase = GitHubApkInstallPhase.SelectingApk,
                        stageProgress = 1f
                    ),
                    sheetVisible = true,
                    message = work.appContext.getString(R.string.github_apk_install_select_apk)
                )
            )
            return
        }
        inspectAndPrepareConfirmation(work, candidates.first())
    }

    private suspend fun inspectAndPrepareConfirmation(
        work: ActiveInstallWork,
        candidate: GitHubApkInstallDownloadedFile
    ) {
        work.selectedCandidate = candidate
        updateState(
            _state.value.copy(
                phase = GitHubApkInstallPhase.Inspecting,
                selectedCandidateName = candidate.name,
                selectedCandidateSizeBytes = candidate.sizeBytes,
                progressKind = GitHubApkInstallProgressKind.Inspect,
                stageProgress = 0f,
                progress = 0f,
                overallProgress = installOverallProgress(
                    phase = GitHubApkInstallPhase.Inspecting,
                    stageProgress = 0f
                ),
                message = work.appContext.getString(R.string.github_apk_install_inspecting)
            )
        )
        val archiveInfo = withContext(Dispatchers.IO) {
            ApkArchiveInspector(work.appContext).inspect(candidate.file).getOrThrow()
        }
        val installedInfo = loadInstalledPackageInfo(
            context = work.appContext,
            packageName = work.request.expectedPackageName.ifBlank { archiveInfo.packageName }
        )
        val resolvedInstalledInfo = installedInfo ?: _state.value.installedPackageInfo
        val trustRequest = work.request.copy(
            remoteManifestInfo = _state.value.remoteManifestInfo ?: work.request.remoteManifestInfo
        )
        val trust = evaluateTrust(
            asset = work.asset.copy(name = candidate.name, sizeBytes = candidate.sizeBytes),
            request = trustRequest,
            installedInfo = resolvedInstalledInfo,
            archiveInfo = archiveInfo
        )
        updateState(
            _state.value.copy(
                remoteManifestInfo = trustRequest.remoteManifestInfo,
                localArchiveInfo = archiveInfo,
                installedPackageInfo = resolvedInstalledInfo,
                trustSignal = trust,
                selectedCandidateName = candidate.name,
                selectedCandidateSizeBytes = candidate.sizeBytes,
                phase = GitHubApkInstallPhase.ReadyToInstall,
                progressKind = GitHubApkInstallProgressKind.Waiting,
                stageProgress = 1f,
                progress = 1f,
                overallProgress = installOverallProgress(
                    phase = GitHubApkInstallPhase.ReadyToInstall,
                    stageProgress = 1f
                ),
                sheetVisible = true,
                message = work.appContext.getString(
                    when (trust.level) {
                        GitHubDecisionLevel.Good -> R.string.github_apk_install_ready_good
                        GitHubDecisionLevel.Review -> R.string.github_apk_install_ready_review
                        GitHubDecisionLevel.Risk -> R.string.github_apk_install_ready_risk
                    }
                )
            )
        )
    }

    private suspend fun installCandidate(
        work: ActiveInstallWork,
        candidate: GitHubApkInstallDownloadedFile
    ) {
        val archiveInfo = _state.value.localArchiveInfo ?: withContext(Dispatchers.IO) {
            ApkArchiveInspector(work.appContext).inspect(candidate.file).getOrThrow()
        }
        updateState(
            _state.value.copy(
                phase = GitHubApkInstallPhase.Installing,
                localArchiveInfo = archiveInfo,
                selectedCandidateName = candidate.name,
                selectedCandidateSizeBytes = candidate.sizeBytes,
                progress = 0.62f,
                stageProgress = 0f,
                overallProgress = installOverallProgress(
                    phase = GitHubApkInstallPhase.Installing,
                    stageProgress = 0f
                ),
                progressKind = GitHubApkInstallProgressKind.Staging,
                message = work.appContext.getString(R.string.github_apk_install_installing)
            )
        )
        val result = installBackend(work.appContext).install(
            request = ApkInstallRequest(
                packageName = archiveInfo.packageName,
                sourceLabel = work.request.displayLabel,
                entries = listOf(
                    ApkInstallEntry(
                        name = candidate.name,
                        sizeBytes = candidate.sizeBytes
                    ) { candidate.file.inputStream().buffered() }
                )
            )
        ) { progress ->
            if (progress is ApkInstallProgress.Staging) {
                updateProgress(
                    sessionId = work.sessionId,
                    phase = GitHubApkInstallPhase.Installing,
                    kind = GitHubApkInstallProgressKind.Staging,
                    bytesDone = progress.bytesWritten,
                    totalBytes = progress.totalBytes
                )
            } else if (progress is ApkInstallProgress.Committing) {
                updateActiveState(work.sessionId) { current ->
                    current.copy(
                        progressKind = GitHubApkInstallProgressKind.Commit,
                        stageProgress = 1f,
                        progress = 1f,
                        overallProgress = installOverallProgress(
                            phase = GitHubApkInstallPhase.Installing,
                            stageProgress = 1f
                        ),
                        message = work.appContext.getString(R.string.github_apk_install_installing)
                    )
                }
            }
        }
        when (result) {
            is ApkInstallResult.Success -> {
                val refreshedTrackIds = withContext(Dispatchers.IO) {
                    refreshTrackedItemsAfterInstall(work, archiveInfo)
                }
                updateState(
                    _state.value.copy(
                        phase = GitHubApkInstallPhase.Success,
                        backendId = result.backendId,
                        progressKind = GitHubApkInstallProgressKind.None,
                        stageProgress = 1f,
                        progress = 1f,
                        overallProgress = 1f,
                        message = result.message
                    )
                )
                work.cleanup()
                if (activeWork === work) {
                    activeWork = null
                }
                refreshedTrackIds.forEach { trackId ->
                    GitHubTrackStoreSignals.requestTrackRefresh(
                        trackId = trackId,
                        notifyChangeSignal = false
                    )
                }
                GitHubTrackStoreSignals.notifyChanged()
                if (work.request.sourceKind == GitHubApkInstallSourceKind.ShareImport) {
                    GitHubShareImportFlowCoordinator.refreshPendingInstall(work.appContext)
                }
            }

            is ApkInstallResult.PendingUserAction -> {
                work.pendingUserAction = result.intent
                updateState(
                    _state.value.copy(
                        phase = GitHubApkInstallPhase.PendingUserAction,
                        backendId = result.backendId,
                        progressKind = GitHubApkInstallProgressKind.Waiting,
                        stageProgress = 1f,
                        progress = 1f,
                        overallProgress = installOverallProgress(
                            phase = GitHubApkInstallPhase.PendingUserAction,
                            stageProgress = 1f
                        ),
                        sheetVisible = true,
                        message = result.message
                    )
                )
            }

            is ApkInstallResult.Failure -> {
                updateState(
                    _state.value.copy(
                        phase = GitHubApkInstallPhase.Failed,
                        backendId = result.backendId,
                        failureReason = result.reason,
                        progressKind = GitHubApkInstallProgressKind.None,
                        stageProgress = 0f,
                        progress = 0f,
                        overallProgress = _state.value.overallProgress.coerceIn(0f, 1f),
                        sheetVisible = true,
                        message = result.userFacingMessage(work.appContext),
                        rawMessage = result.message.ifBlank {
                            work.appContext.getString(R.string.github_apk_install_error_failed)
                        }
                    )
                )
            }
        }
    }

    private fun installBackend(context: Context): ApkInstallBackend {
        return ShizukuSessionInstallBackend(context.applicationContext)
    }

    private fun preheatInstalledInfo(work: ActiveInstallWork) {
        val packageName = work.request.expectedPackageName
            .ifBlank { work.request.remoteManifestInfo?.packageName.orEmpty() }
        val installedInfo = loadInstalledPackageInfo(work.appContext, packageName)
        updateActiveState(work.sessionId) { current ->
            val request = work.request.copy(
                remoteManifestInfo = current.remoteManifestInfo ?: work.request.remoteManifestInfo
            )
            current.copy(
                installedPackageInfo = installedInfo ?: current.installedPackageInfo,
                trustSignal = evaluateTrust(
                    asset = work.asset.copy(
                        name = current.selectedCandidateName.ifBlank { work.asset.name },
                        sizeBytes = current.selectedCandidateSizeBytes.takeIf { it > 0L }
                            ?: work.asset.sizeBytes
                    ),
                    request = request,
                    installedInfo = installedInfo ?: current.installedPackageInfo,
                    archiveInfo = current.localArchiveInfo
                ),
                remoteManifestInfo = request.remoteManifestInfo
            )
        }
    }

    private suspend fun preheatRemoteManifest(work: ActiveInstallWork) {
        if (work.request.remoteManifestInfo != null) return
        if (!work.asset.name.endsWith(".apk", ignoreCase = true)) return
        val manifest = apkInfoRepository.inspectAsync(
            asset = work.asset,
            lookupConfig = work.lookupConfig
        ).getOrNull() ?: return
        val installedInfo = loadInstalledPackageInfo(
            context = work.appContext,
            packageName = work.request.expectedPackageName.ifBlank { manifest.packageName }
        )
        updateActiveState(work.sessionId) { current ->
            val request = work.request.copy(remoteManifestInfo = manifest)
            current.copy(
                remoteManifestInfo = manifest,
                installedPackageInfo = installedInfo ?: current.installedPackageInfo,
                trustSignal = evaluateTrust(
                    asset = work.asset.copy(
                        name = current.selectedCandidateName.ifBlank { work.asset.name },
                        sizeBytes = current.selectedCandidateSizeBytes.takeIf { it > 0L }
                            ?: work.asset.sizeBytes
                    ),
                    request = request,
                    installedInfo = installedInfo ?: current.installedPackageInfo,
                    archiveInfo = current.localArchiveInfo
                )
            )
        }
    }

    private fun evaluateTrust(
        asset: GitHubReleaseAssetFile,
        request: GitHubApkInstallRequestContext,
        installedInfo: GitHubInstalledPackageInfo?,
        archiveInfo: LocalApkArchiveInfo?
    ) = ApkTrustEvaluator.evaluate(
        ApkTrustEvaluationInput(
            asset = asset,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            manifestInfo = request.remoteManifestInfo,
            installedInfo = installedInfo,
            expectedPackageName = request.expectedPackageName.ifBlank {
                request.remoteManifestInfo?.packageName.orEmpty()
            },
            localArchiveInfo = archiveInfo
        )
    )

    private fun updateActiveState(
        sessionId: Long,
        transform: (GitHubApkInstallFlowState) -> GitHubApkInstallFlowState
    ) {
        val current = _state.value
        if (current.sessionId != sessionId || current.phase.isTerminalForAsyncUpdates()) return
        updateState(transform(current))
    }

    private fun updateProgress(
        sessionId: Long,
        phase: GitHubApkInstallPhase,
        kind: GitHubApkInstallProgressKind,
        bytesDone: Long,
        totalBytes: Long
    ) {
        val fraction = if (totalBytes > 0L) {
            bytesDone.toFloat() / totalBytes.toFloat()
        } else {
            0f
        }.coerceIn(0f, 1f)
        updateActiveState(sessionId) { current ->
            current.copy(
                phase = phase,
                progressKind = kind,
                bytesDone = bytesDone,
                totalBytes = totalBytes,
                stageProgress = fraction,
                progress = fraction,
                overallProgress = installOverallProgress(
                    phase = phase,
                    stageProgress = fraction
                )
            )
        }
    }

    private fun fail(context: Context, message: String) {
        updateState(
            _state.value.copy(
                phase = GitHubApkInstallPhase.Failed,
                progressKind = GitHubApkInstallProgressKind.None,
                stageProgress = 0f,
                progress = 0f,
                overallProgress = _state.value.overallProgress.coerceIn(0f, 1f),
                sheetVisible = true,
                message = message
            )
        )
        GitHubApkInstallNotificationHelper.notify(context, _state.value)
    }

    private fun failActive(
        context: Context,
        sessionId: Long,
        message: String,
        rawMessage: String = ""
    ) {
        val current = _state.value
        if (current.sessionId != sessionId || current.phase.isTerminalForAsyncUpdates()) return
        updateState(
            current.copy(
                phase = GitHubApkInstallPhase.Failed,
                progressKind = GitHubApkInstallProgressKind.None,
                stageProgress = 0f,
                progress = 0f,
                overallProgress = current.overallProgress.coerceIn(0f, 1f),
                sheetVisible = true,
                message = sanitizeInstallFailureMessage(context, message),
                rawMessage = rawMessage
            )
        )
    }

    private fun updateState(next: GitHubApkInstallFlowState) {
        _state.value = next
        if (next.phase != GitHubApkInstallPhase.Idle) {
            GitHubApkInstallNotificationHelper.notify(next.requestContext(), next)
        }
    }

    private fun GitHubApkInstallFlowState.requestContext(): Context {
        return activeWork?.appContext ?: lastRequest?.context
        ?: error("install context unavailable")
    }

    private fun Throwable.localizedInstallMessage(context: Context): String {
        return sanitizeInstallFailureMessage(
            context = context,
            raw = message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.github_apk_install_error_failed)
        )
    }

    private fun ActiveInstallWork.cleanup() {
        val installCacheDir = File(appContext.cacheDir, "github_apk_install")
        val files = (downloadedFiles + candidates)
            .map { downloaded -> downloaded.file }
            .distinctBy { file -> file.absolutePath }
        files.forEach { file -> runCatching { file.delete() } }
        files
            .mapNotNull { file -> file.parentFile }
            .distinctBy { dir -> dir.absolutePath }
            .filter { dir -> dir.name.startsWith("extracted-") }
            .forEach { dir -> runCatching { dir.deleteRecursively() } }
        installCacheDir
            .listFiles()
            .orEmpty()
            .filter { file -> file.isDirectory && file.name.startsWith("extracted-") }
            .filter { dir -> dir.listFiles().orEmpty().isEmpty() }
            .forEach { dir -> runCatching { dir.deleteRecursively() } }
    }

    private fun loadInstalledPackageInfo(
        context: Context,
        packageName: String
    ): GitHubInstalledPackageInfo? {
        val normalized = packageName.trim()
        if (normalized.isBlank()) return null
        val info = runCatching {
            context.packageManager.getPackageInfo(
                normalized,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        }.getOrNull() ?: return null
        val appInfo = info.applicationInfo
        return GitHubInstalledPackageInfo(
            packageName = normalized,
            appLabel = appInfo?.loadLabel(context.packageManager)?.toString().orEmpty(),
            versionName = info.versionName.orEmpty(),
            versionCode = info.longVersionCode,
            minSdk = appInfo?.minSdkVersion ?: -1,
            targetSdk = appInfo?.targetSdkVersion ?: -1,
            signatureSha256 = info.signatureSha256List(),
            sourceSizeBytes = appInfo.sourceApkSizeBytes()
        )
    }

    private suspend fun refreshTrackedItemsAfterInstall(
        work: ActiveInstallWork,
        archiveInfo: LocalApkArchiveInfo
    ): Set<String> {
        val packageName = archiveInfo.packageName
            .ifBlank { work.request.expectedPackageName }
            .trim()
        if (packageName.isBlank()) return emptySet()
        val installedInfo = awaitInstalledPackageInfo(
            context = work.appContext,
            packageName = packageName
        )
        val appLabel = installedInfo?.appLabel.orEmpty().ifBlank { archiveInfo.appLabel }
        val items = GitHubTrackStore.load()
        val matchedIds = items
            .filter { item -> item.packageName.equals(packageName, ignoreCase = true) }
            .map { item -> item.id }
            .toSet()
        if (matchedIds.isEmpty()) return emptySet()
        if (appLabel.isNotBlank()) {
            val updated = items.map { item ->
                if (
                    item.id in matchedIds &&
                    !item.appLabel.equals(appLabel, ignoreCase = false)
                ) {
                    item.copy(appLabel = appLabel)
                } else {
                    item
                }
            }
            if (updated != items) {
                GitHubTrackStore.save(updated)
            }
        }
        return matchedIds
    }

    private suspend fun awaitInstalledPackageInfo(
        context: Context,
        packageName: String
    ): GitHubInstalledPackageInfo? {
        repeat(INSTALL_SUCCESS_PACKAGE_INFO_RETRY_COUNT) { index ->
            val installed = loadInstalledPackageInfo(context, packageName)
            if (installed != null) return installed
            if (index < INSTALL_SUCCESS_PACKAGE_INFO_RETRY_COUNT - 1) {
                delay(INSTALL_SUCCESS_PACKAGE_INFO_RETRY_DELAY_MS)
            }
        }
        return null
    }

    private fun android.content.pm.ApplicationInfo?.sourceApkSizeBytes(): Long {
        if (this == null) return 0L
        val files = buildList {
            sourceDir?.let { add(it) }
            splitSourceDirs.orEmpty().forEach(::add)
        }
        return files.sumOf { path -> File(path).takeIf { it.isFile }?.length() ?: 0L }
    }

    private fun PackageInfo.signatureSha256List(): List<String> {
        val signingInfo = signingInfo ?: return emptyList()
        val signatures = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        } ?: return emptyList()
        return signatures
            .map { signature -> signature.toByteArray().sha256Hex() }
            .distinct()
    }

    private fun ByteArray.sha256Hex(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun installOverallProgress(
        phase: GitHubApkInstallPhase,
        stageProgress: Float
    ): Float {
        val stage = stageProgress.coerceIn(0f, 1f)
        return when (phase) {
            GitHubApkInstallPhase.Downloading -> 0.06f + stage * 0.44f
            GitHubApkInstallPhase.SelectingApk -> 0.52f
            GitHubApkInstallPhase.Inspecting -> 0.54f + stage * 0.04f
            GitHubApkInstallPhase.ReadyToInstall -> 0.60f
            GitHubApkInstallPhase.Installing -> 0.62f + stage * 0.26f
            GitHubApkInstallPhase.PendingUserAction -> 0.90f
            GitHubApkInstallPhase.Success -> 1f
            GitHubApkInstallPhase.Failed,
            GitHubApkInstallPhase.Cancelled,
            GitHubApkInstallPhase.Idle -> 0f
        }.coerceIn(0f, 1f)
    }

    private fun GitHubApkInstallPhase.isTerminalForAsyncUpdates(): Boolean {
        return this == GitHubApkInstallPhase.Idle ||
                this == GitHubApkInstallPhase.Success ||
                this == GitHubApkInstallPhase.Failed ||
                this == GitHubApkInstallPhase.Cancelled
    }

    private fun GitHubApkInstallFlowState.sameInstallTarget(
        asset: GitHubReleaseAssetFile,
        request: GitHubApkInstallRequestContext
    ): Boolean {
        val currentAsset = this.asset ?: return false
        return currentAsset.name == asset.name &&
                currentAsset.downloadUrl == asset.downloadUrl &&
                this.request.sourceKind == request.sourceKind &&
                this.request.owner == request.owner &&
                this.request.repo == request.repo &&
                this.request.releaseTag == request.releaseTag &&
                this.request.externalUrl == request.externalUrl &&
                this.request.externalFileName == request.externalFileName
    }

    private fun ApkInstallResult.Failure.userFacingMessage(context: Context): String {
        return sanitizeInstallFailureMessage(
            context = context,
            raw = message.ifBlank { context.getString(R.string.github_apk_install_error_failed) }
        )
    }

    private fun sanitizeInstallFailureMessage(context: Context, raw: String): String {
        val normalized = raw.trim()
        if (normalized.isBlank()) return context.getString(R.string.github_apk_install_error_failed)
        val lower = normalized.lowercase()
        return when {
            "timed out" in lower || "timeout" in lower ->
                context.getString(R.string.github_apk_install_error_timeout)

            else -> normalized
        }
    }

    private const val INSTALL_SUCCESS_PACKAGE_INFO_RETRY_COUNT = 3
    private const val INSTALL_SUCCESS_PACKAGE_INFO_RETRY_DELAY_MS = 250L

    private data class ActiveInstallWork(
        val appContext: Context,
        val lookupConfig: GitHubLookupConfig,
        val asset: GitHubReleaseAssetFile,
        val request: GitHubApkInstallRequestContext,
        val sessionId: Long,
        val notificationFirst: Boolean,
        val downloadedFiles: MutableList<GitHubApkInstallDownloadedFile> = mutableListOf(),
        var candidates: List<GitHubApkInstallDownloadedFile> = emptyList(),
        var selectedCandidate: GitHubApkInstallDownloadedFile? = null,
        var pendingUserAction: Intent? = null
    )

    private sealed interface LastInstallRequest {
        val context: Context

        data class Asset(
            override val context: Context,
            val lookupConfig: GitHubLookupConfig,
            val asset: GitHubReleaseAssetFile,
            val request: GitHubApkInstallRequestContext
        ) : LastInstallRequest
    }
}
