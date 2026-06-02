package os.kei.ui.page.main.github.share

import android.content.Context
import os.kei.R
import os.kei.feature.github.data.local.GitHubPendingShareImportManagedInstallRecord
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.install.GitHubApkInstallFailureReason
import os.kei.feature.github.install.GitHubApkInstallRequest
import os.kei.feature.github.install.GitHubApkInstallResult
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.ui.page.main.github.localizedGitHubPageErrorMessage

internal fun GitHubApkInstallRequest.toManagedInstallRecord(sessionId: Int): GitHubPendingShareImportManagedInstallRecord =
    GitHubPendingShareImportManagedInstallRecord(
        requestId = requestId,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        releaseTag = releaseTag,
        assetName = asset.name,
        appLabel = scannedAppLabel,
        packageName = scannedPackageName,
        versionName = scannedVersionName,
        versionCode = scannedVersionCode,
        minSdk = scannedMinSdk,
        targetSdk = scannedTargetSdk,
        nativeAbis = scannedNativeAbis,
        targetDisplayName = targetDisplayName,
        sessionId = sessionId,
        progressPhase = GitHubShareImportPhase.Installing.name,
        progressPercent = 0,
        downloadedBytes = 0L,
        totalBytes = asset.sizeBytes,
        startedAtMillis = startedAtMillis,
    )

internal fun GitHubPendingShareImportManagedInstallRecord.toInstallRequest(
    asset: GitHubReleaseAssetFile,
    lookupConfig: GitHubLookupConfig,
    fallbackTargetDisplayName: String,
): GitHubApkInstallRequest =
    GitHubApkInstallRequest(
        owner = owner,
        repo = repo,
        releaseTag = releaseTag,
        projectUrl = projectUrl,
        asset = asset,
        lookupConfig = lookupConfig,
        targetDisplayName = targetDisplayName.ifBlank { fallbackTargetDisplayName },
        scannedAppLabel = appLabel,
        scannedPackageName = packageName,
        scannedVersionName = versionName,
        scannedVersionCode = versionCode,
        scannedMinSdk = minSdk,
        scannedTargetSdk = targetSdk,
        scannedNativeAbis = nativeAbis,
        requestId = requestId,
        startedAtMillis = startedAtMillis,
    )

internal fun managedInstallFailureMessage(
    context: Context,
    result: GitHubApkInstallResult.Failed,
): String =
    when (result.reason) {
        GitHubApkInstallFailureReason.ShizukuUnavailable -> {
            context.getString(R.string.github_share_import_error_shizuku_unavailable)
        }

        GitHubApkInstallFailureReason.ShizukuPermissionMissing -> {
            context.getString(R.string.github_share_import_error_shizuku_permission_missing)
        }

        GitHubApkInstallFailureReason.RemoteInstallPermissionMissing -> {
            context.getString(R.string.github_share_import_error_shizuku_install_permission_missing)
        }

        GitHubApkInstallFailureReason.PackageNameMissing -> {
            context.getString(R.string.github_share_import_error_app_managed_package_missing)
        }

        else -> {
            result.message.ifBlank {
                context.getString(R.string.github_share_import_error_app_managed_install_failed)
            }.let { message ->
                localizedGitHubPageErrorMessage(
                    context = context,
                    rawMessage = message,
                    fallbackMessage = context.getString(R.string.github_share_import_error_app_managed_install_failed),
                )
            }
        }
    }

internal fun managedInstallAction(context: Context): String = "${context.packageName}.github.share_import.action.MANAGED_INSTALL"
