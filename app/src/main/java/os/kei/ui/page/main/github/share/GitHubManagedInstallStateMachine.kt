package os.kei.ui.page.main.github.share

import os.kei.feature.github.data.local.GitHubPendingShareImportManagedInstallRecord
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.install.GitHubApkInstallProgress
import os.kei.feature.github.install.GitHubApkInstallRequest
import os.kei.feature.github.install.GitHubApkInstallResult
import os.kei.feature.github.install.GitHubApkInstallStage
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig

internal data class GitHubManagedInstallStartState(
    val requestId: String,
    val targetDisplayName: String,
    val initialProgress: GitHubShareImportManagedInstallProgress,
)

internal fun buildManagedInstallStartState(
    preview: GitHubShareImportPreview,
    selectedAsset: GitHubReleaseAssetFile,
    requestId: String,
): GitHubManagedInstallStartState {
    val targetDisplayName =
        preview.targetDisplayName.ifBlank {
            buildShareImportTargetDisplayName(
                repo = preview.repo,
                assetName = selectedAsset.name,
            )
        }
    return GitHubManagedInstallStartState(
        requestId = requestId,
        targetDisplayName = targetDisplayName,
        initialProgress =
            GitHubShareImportManagedInstallProgress(
                phase = GitHubShareImportPhase.Installing,
                assetName = selectedAsset.name,
                targetDisplayName = targetDisplayName,
                progressPercent = 0,
                totalBytes = selectedAsset.sizeBytes,
            ),
    )
}

internal fun buildManagedInstallInitialRecord(
    preview: GitHubShareImportPreview,
    selectedAsset: GitHubReleaseAssetFile,
    startState: GitHubManagedInstallStartState,
): GitHubPendingShareImportManagedInstallRecord =
    GitHubPendingShareImportManagedInstallRecord(
        requestId = startState.requestId,
        projectUrl = preview.projectUrl,
        owner = preview.owner,
        repo = preview.repo,
        releaseTag = preview.releaseTag,
        assetName = selectedAsset.name,
        targetDisplayName = startState.targetDisplayName,
        progressPhase = startState.initialProgress.phase.name,
        progressPercent = startState.initialProgress.boundedProgressPercent,
        downloadedBytes = startState.initialProgress.downloadedBytes,
        totalBytes = startState.initialProgress.totalBytes,
    )

internal fun buildManagedInstallRequest(
    preview: GitHubShareImportPreview,
    selectedAsset: GitHubReleaseAssetFile,
    lookupConfig: GitHubLookupConfig,
    startState: GitHubManagedInstallStartState,
    manifestInfo: GitHubApkManifestInfo,
    resolvedDownloadUrl: String,
): GitHubApkInstallRequest =
    GitHubApkInstallRequest(
        owner = preview.owner,
        repo = preview.repo,
        releaseTag = preview.releaseTag,
        projectUrl = preview.projectUrl,
        asset = selectedAsset,
        lookupConfig = lookupConfig,
        targetDisplayName = startState.targetDisplayName,
        scannedAppLabel = manifestInfo.appLabel.trim(),
        scannedPackageName = manifestInfo.packageName.trim(),
        scannedVersionName = manifestInfo.versionName.trim(),
        scannedVersionCode = manifestInfo.versionCode.trim(),
        scannedMinSdk = manifestInfo.minSdk.trim(),
        scannedTargetSdk = manifestInfo.targetSdk.trim(),
        scannedNativeAbis = manifestInfo.normalizedNativeAbis(),
        resolvedDownloadUrl = resolvedDownloadUrl,
        requestId = startState.requestId,
    )

internal fun buildManagedInstallProgress(
    request: GitHubApkInstallRequest,
    progress: GitHubApkInstallProgress,
): GitHubShareImportManagedInstallProgress =
    GitHubShareImportManagedInstallProgress(
        phase = progress.stage.toShareImportPhase(),
        assetName = request.asset.name,
        appLabel = progress.appLabel.ifBlank { request.scannedAppLabel },
        packageName = progress.packageName.ifBlank { request.scannedPackageName },
        versionName = progress.versionName.ifBlank { request.scannedVersionName },
        versionCode = progress.versionCode.ifBlank { request.scannedVersionCode },
        minSdk = progress.minSdk.ifBlank { request.scannedMinSdk },
        targetSdk = progress.targetSdk.ifBlank { request.scannedTargetSdk },
        nativeAbis = request.scannedNativeAbis,
        targetDisplayName = request.targetDisplayName,
        progressPercent = progress.boundedProgressPercent,
        downloadedBytes = progress.downloadedBytes,
        totalBytes = progress.totalBytes,
    )

internal fun mergeManagedInstallProgressRecord(
    activeRecord: GitHubPendingShareImportManagedInstallRecord,
    request: GitHubApkInstallRequest,
    progress: GitHubApkInstallProgress,
): GitHubPendingShareImportManagedInstallRecord {
    val uiProgress = buildManagedInstallProgress(request, progress)
    return activeRecord.copy(
        sessionId = if (progress.sessionId > 0) progress.sessionId else activeRecord.sessionId,
        appLabel = uiProgress.appLabel.ifBlank { activeRecord.appLabel },
        packageName = uiProgress.packageName.ifBlank { activeRecord.packageName },
        versionName = uiProgress.versionName.ifBlank { activeRecord.versionName },
        versionCode = uiProgress.versionCode.ifBlank { activeRecord.versionCode },
        minSdk = uiProgress.minSdk.ifBlank { activeRecord.minSdk },
        targetSdk = uiProgress.targetSdk.ifBlank { activeRecord.targetSdk },
        nativeAbis = uiProgress.nativeAbis.ifEmpty { activeRecord.nativeAbis },
        targetDisplayName = uiProgress.targetDisplayName.ifBlank { activeRecord.targetDisplayName },
        progressPhase = uiProgress.phase.name,
        progressPercent = uiProgress.boundedProgressPercent,
        downloadedBytes = uiProgress.downloadedBytes,
        totalBytes = uiProgress.totalBytes,
    )
}

internal fun buildStagedManagedInstallRecord(
    activeRecord: GitHubPendingShareImportManagedInstallRecord,
    request: GitHubApkInstallRequest,
    result: GitHubApkInstallResult.Staged,
): GitHubPendingShareImportManagedInstallRecord =
    activeRecord.copy(
        sessionId = result.sessionId,
        appLabel =
            result.appLabel
                .ifBlank { request.scannedAppLabel }
                .ifBlank { activeRecord.appLabel },
        packageName = result.packageName.ifBlank { activeRecord.packageName },
        versionName =
            result.versionName
                .ifBlank { request.scannedVersionName }
                .ifBlank { activeRecord.versionName },
        versionCode =
            result.versionCode
                .ifBlank { request.scannedVersionCode }
                .ifBlank { activeRecord.versionCode },
        minSdk =
            result.minSdk
                .ifBlank { request.scannedMinSdk }
                .ifBlank { activeRecord.minSdk },
        targetSdk =
            result.targetSdk
                .ifBlank { request.scannedTargetSdk }
                .ifBlank { activeRecord.targetSdk },
        nativeAbis = request.scannedNativeAbis.ifEmpty { activeRecord.nativeAbis },
        progressPhase = GitHubShareImportPhase.InstallReady.name,
        progressPercent = 100,
        downloadedBytes = result.downloadedBytes,
        totalBytes = result.totalBytes,
    )

internal fun buildManagedInstallAttachCandidate(
    preview: GitHubShareImportPreview,
    request: GitHubApkInstallRequest,
    result: GitHubApkInstallResult.Succeeded,
    snapshot: ShareImportInstalledPackageSnapshot?,
    eventAction: String,
    detectedAtMillis: Long,
): GitHubPendingShareImportAttachCandidate =
    GitHubPendingShareImportAttachCandidate(
        projectUrl = preview.projectUrl,
        owner = preview.owner,
        repo = preview.repo,
        packageName = result.packageName.trim(),
        appLabel =
            snapshot
                ?.appLabel
                .orEmpty()
                .ifBlank { result.appLabel }
                .ifBlank { request.scannedAppLabel }
                .ifBlank { request.targetDisplayName }
                .ifBlank { result.packageName.trim() },
        versionName =
            snapshot
                ?.versionName
                .orEmpty()
                .ifBlank { request.scannedVersionName },
        versionCode =
            snapshot
                ?.versionCode
                .orEmpty()
                .ifBlank { request.scannedVersionCode },
        eventAction = eventAction,
        detectedAtMillis = detectedAtMillis,
        firstInstallTimeMs = snapshot?.firstInstallTimeMs ?: result.firstInstallTimeMs,
    )

private fun GitHubApkInstallStage.toShareImportPhase(): GitHubShareImportPhase =
    when (this) {
        GitHubApkInstallStage.Downloading -> GitHubShareImportPhase.InstallDownloading

        GitHubApkInstallStage.ReadyToCommit -> GitHubShareImportPhase.InstallReady

        GitHubApkInstallStage.Committing -> GitHubShareImportPhase.InstallCommitting

        GitHubApkInstallStage.Preparing,
        GitHubApkInstallStage.Staging,
        -> GitHubShareImportPhase.Installing

        GitHubApkInstallStage.Succeeded,
        GitHubApkInstallStage.Failed,
        GitHubApkInstallStage.Cancelled,
        -> GitHubShareImportPhase.Idle
    }

private fun GitHubApkManifestInfo.normalizedNativeAbis(): List<String> =
    nativeAbis
        .map { abi -> abi.trim() }
        .filter { abi -> abi.isNotBlank() }
