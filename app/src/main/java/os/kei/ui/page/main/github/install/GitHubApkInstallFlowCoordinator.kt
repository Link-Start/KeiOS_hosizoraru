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
import os.kei.core.install.ShizukuDualInstallBackend
import os.kei.core.install.ShizukuSessionInstallBackend
import os.kei.core.install.ShizukuShellInstallBackend
import os.kei.core.intent.SafeExternalIntents
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.domain.ApkTrustEvaluationInput
import os.kei.feature.github.domain.ApkTrustEvaluator
import os.kei.feature.github.model.GitHubApkInstallUiMode
import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.notification.GitHubApkInstallNotificationHelper
import os.kei.ui.page.main.github.share.GitHubShareImportFlowCoordinator
import os.kei.ui.page.main.github.share.resolvePreferredAssetUrl
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

internal object GitHubApkInstallFlowCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionIds = AtomicLong(1L)
    private val downloader = GitHubApkInstallFileDownloader()
    private val extractor = GitHubApkInstallArchiveExtractor()
    private val _state = MutableStateFlow(GitHubApkInstallFlowState())
    val state: StateFlow<GitHubApkInstallFlowState> = _state.asStateFlow()

    private var activeJob: Job? = null
    private var activeWork: ActiveInstallWork? = null
    private var lastRequest: LastInstallRequest? = null

    fun beginInstallAsset(
        context: Context,
        lookupConfig: GitHubLookupConfig,
        asset: GitHubReleaseAssetFile,
        request: GitHubApkInstallRequestContext
    ) {
        val appContext = context.applicationContext
        val snapshot = LastInstallRequest.Asset(appContext, lookupConfig, asset, request)
        lastRequest = snapshot
        activeJob?.cancel()
        val sessionId = sessionIds.incrementAndGet()
        val notificationFirst =
            lookupConfig.apkInstallUiMode == GitHubApkInstallUiMode.NotificationFirst
        updateState(
            GitHubApkInstallFlowState(
                sessionId = sessionId,
                phase = GitHubApkInstallPhase.Downloading,
                request = request,
                asset = asset,
                sheetVisible = !notificationFirst,
                notificationFirst = notificationFirst
            )
        )
        activeJob = scope.launch {
            try {
                val resolvedUrl = resolvePreferredAssetUrl(lookupConfig, asset)
                activeWork = ActiveInstallWork(
                    appContext = appContext,
                    lookupConfig = lookupConfig,
                    asset = asset,
                    request = request.copy(
                        externalUrl = resolvedUrl,
                        externalFileName = asset.name
                    ),
                    sessionId = sessionId,
                    notificationFirst = notificationFirst
                )
                downloadAndPrepare(activeWork!!)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                fail(appContext, error.localizedInstallMessage(appContext))
            }
        }
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
        activeJob?.cancel()
        activeJob = null
        activeWork?.cleanup()
        activeWork = null
        updateState(
            _state.value.copy(
                phase = GitHubApkInstallPhase.Cancelled,
                progress = 0f,
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
                phase = GitHubApkInstallPhase.Downloading,
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
                    progress = 0.44f,
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
                progress = 0.52f,
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
        val trust = ApkTrustEvaluator.evaluate(
            ApkTrustEvaluationInput(
                asset = work.asset.copy(name = candidate.name, sizeBytes = candidate.sizeBytes),
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                installedInfo = installedInfo,
                expectedPackageName = work.request.expectedPackageName,
                localArchiveInfo = archiveInfo
            )
        )
        updateState(
            _state.value.copy(
                localArchiveInfo = archiveInfo,
                installedPackageInfo = installedInfo,
                trustSignal = trust,
                selectedCandidateName = candidate.name,
                selectedCandidateSizeBytes = candidate.sizeBytes,
                phase = GitHubApkInstallPhase.ReadyToInstall,
                progress = 0.58f,
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
                    phase = GitHubApkInstallPhase.Installing,
                    bytesDone = progress.bytesWritten,
                    totalBytes = progress.totalBytes
                )
            } else if (progress is ApkInstallProgress.Committing) {
                updateState(_state.value.copy(progress = 0.86f))
            }
        }
        when (result) {
            is ApkInstallResult.Success -> {
                updateState(
                    _state.value.copy(
                        phase = GitHubApkInstallPhase.Success,
                        backendId = result.backendId,
                        progress = 1f,
                        message = result.message
                    )
                )
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
                        progress = 0.88f,
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
                        progress = 0f,
                        sheetVisible = true,
                        message = result.message.ifBlank {
                            work.appContext.getString(R.string.github_apk_install_error_failed)
                        }
                    )
                )
            }
        }
    }

    private fun installBackend(context: Context): ApkInstallBackend {
        return ShizukuDualInstallBackend(
            sessionBackend = ShizukuSessionInstallBackend(context.applicationContext),
            shellBackend = ShizukuShellInstallBackend()
        )
    }

    private fun updateProgress(
        phase: GitHubApkInstallPhase,
        bytesDone: Long,
        totalBytes: Long
    ) {
        val fraction = if (totalBytes > 0L) {
            bytesDone.toFloat() / totalBytes.toFloat()
        } else {
            0f
        }.coerceIn(0f, 1f)
        updateState(
            _state.value.copy(
                phase = phase,
                bytesDone = bytesDone,
                totalBytes = totalBytes,
                progress = fraction
            )
        )
    }

    private fun fail(context: Context, message: String) {
        updateState(
            _state.value.copy(
                phase = GitHubApkInstallPhase.Failed,
                progress = 0f,
                sheetVisible = true,
                message = message
            )
        )
        GitHubApkInstallNotificationHelper.notify(context, _state.value)
    }

    private fun updateState(next: GitHubApkInstallFlowState) {
        _state.value = next
        if (next.active) {
            GitHubApkInstallNotificationHelper.notify(next.requestContext(), next)
        }
    }

    private fun GitHubApkInstallFlowState.requestContext(): Context {
        return activeWork?.appContext ?: lastRequest?.context
        ?: error("install context unavailable")
    }

    private fun Throwable.localizedInstallMessage(context: Context): String {
        return message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.github_apk_install_error_failed)
    }

    private fun ActiveInstallWork.cleanup() {
        downloadedFiles.forEach { file -> runCatching { file.file.delete() } }
        candidates.forEach { file -> runCatching { file.file.delete() } }
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
        }.recoverCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                normalized,
                PackageManager.GET_SIGNATURES
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
            signatureSha256 = info.signatureSha256List()
        )
    }

    private fun PackageInfo.signatureSha256List(): List<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = signingInfo ?: return emptyList()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            signatures
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
