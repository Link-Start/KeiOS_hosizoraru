package os.kei.ui.page.main.github.share

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.system.AppPackageChangedEvent
import os.kei.core.system.getInstalledPackageInfosSafely
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord

internal data class ShareImportInstalledPackageSnapshot(
    val packageName: String,
    val appLabel: String,
    val versionName: String = "",
    val versionCode: String = "",
    val lastUpdateTimeMs: Long,
    val firstInstallTimeMs: Long,
)

internal fun GitHubPendingShareImportTrackRecord.toAttachCandidate(
    packageSnapshot: ShareImportInstalledPackageSnapshot,
    eventAction: String,
    detectedAtMillis: Long,
): GitHubPendingShareImportAttachCandidate =
    GitHubPendingShareImportAttachCandidate(
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        packageName = packageSnapshot.packageName,
        appLabel = packageSnapshot.appLabel.ifBlank { packageSnapshot.packageName },
        versionName = packageSnapshot.versionName,
        versionCode = packageSnapshot.versionCode,
        eventAction = eventAction,
        detectedAtMillis = detectedAtMillis,
        firstInstallTimeMs = packageSnapshot.firstInstallTimeMs,
    )

internal suspend fun loadInstalledPackageSnapshot(
    context: Context,
    packageName: String,
): ShareImportInstalledPackageSnapshot? =
    withContext(AppDispatchers.githubNetwork) {
        loadInstalledPackageSnapshotBlocking(context, packageName)
    }

private fun loadInstalledPackageSnapshotBlocking(
    context: Context,
    packageName: String,
): ShareImportInstalledPackageSnapshot? {
    val normalizedPackageName = packageName.trim()
    if (normalizedPackageName.isBlank()) return null
    val pm = context.packageManager
    val info =
        runCatching {
            pm.getPackageInfo(normalizedPackageName, PackageManager.PackageInfoFlags.of(0))
        }.getOrNull() ?: return null

    val label =
        runCatching {
            val appInfo =
                info.applicationInfo ?: pm.getApplicationInfo(
                    normalizedPackageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                )
            pm.getApplicationLabel(appInfo).toString().trim()
        }.getOrDefault("")

    return ShareImportInstalledPackageSnapshot(
        packageName = normalizedPackageName,
        appLabel = label,
        versionName = info.versionName?.trim().orEmpty(),
        versionCode =
            info.longVersionCode
                .takeIf { it > 0L }
                ?.toString()
                .orEmpty(),
        lastUpdateTimeMs = info.lastUpdateTime,
        firstInstallTimeMs = info.firstInstallTime,
    )
}

internal fun findRecentInstalledCandidateForPendingTrack(
    context: Context,
    pendingTrack: GitHubPendingShareImportTrackRecord,
): ShareImportInstalledPackageSnapshot? {
    val expectedPackageName = pendingTrack.packageName.trim()
    if (expectedPackageName.isNotBlank()) {
        return loadInstalledPackageSnapshotBlocking(context, expectedPackageName)
            ?.takeIf { snapshot ->
                isShareImportExactPackageMatch(
                    pendingTrack = pendingTrack,
                    packageSnapshot = snapshot,
                )
            }
    }

    val pm = context.packageManager
    val installed = pm.getInstalledPackageInfosSafely()
    val candidates =
        installed
            .asSequence()
            .filter { info ->
                val packageName = info.packageName.trim()
                packageName.isNotBlank() &&
                    packageName != context.packageName &&
                    pm.getLaunchIntentForPackage(packageName) != null
            }.mapNotNull { info ->
                val packageName = info.packageName.trim()
                val updateTime = info.lastUpdateTime
                if (updateTime <= 0L) return@mapNotNull null
                if (updateTime < pendingTrack.armedAtMillis - shareImportTrackUpdateToleranceMs) {
                    return@mapNotNull null
                }
                val appLabel =
                    runCatching {
                        val appInfo =
                            info.applicationInfo ?: pm.getApplicationInfo(
                                packageName,
                                PackageManager.ApplicationInfoFlags.of(0),
                            )
                        pm.getApplicationLabel(appInfo).toString().trim()
                    }.getOrDefault("")

                ShareImportInstalledPackageSnapshot(
                    packageName = packageName,
                    appLabel = appLabel,
                    versionCode =
                        info.longVersionCode
                            .takeIf { it > 0L }
                            ?.toString()
                            .orEmpty(),
                    lastUpdateTimeMs = updateTime,
                    firstInstallTimeMs = info.firstInstallTime,
                )
            }.toList()

    return selectRecentInstalledCandidateForPendingTrack(
        pendingTrack = pendingTrack,
        candidates = candidates,
    )
}

internal fun selectRecentInstalledCandidateForPendingTrack(
    pendingTrack: GitHubPendingShareImportTrackRecord,
    candidates: List<ShareImportInstalledPackageSnapshot>,
): ShareImportInstalledPackageSnapshot? {
    val expectedPackageName = pendingTrack.packageName.trim()
    if (expectedPackageName.isNotBlank()) {
        return candidates.firstOrNull { candidate ->
            isShareImportExactPackageMatch(
                pendingTrack = pendingTrack,
                packageSnapshot = candidate,
            )
        }
    }

    val eligible =
        topRecentEligibleInstalledCandidates(
            pendingTrack = pendingTrack,
            candidates = candidates,
        )

    if (eligible.isEmpty()) return null
    if (eligible.size >= 2) {
        val first = eligible[0]
        val second = eligible[1]
        val updateGap = (first.lastUpdateTimeMs - second.lastUpdateTimeMs).coerceAtLeast(0L)
        if (updateGap < 90_000L) return null
    }
    return eligible.first()
}

private fun topRecentEligibleInstalledCandidates(
    pendingTrack: GitHubPendingShareImportTrackRecord,
    candidates: List<ShareImportInstalledPackageSnapshot>,
): List<ShareImportInstalledPackageSnapshot> {
    var first: ShareImportInstalledPackageSnapshot? = null
    var second: ShareImportInstalledPackageSnapshot? = null
    var third: ShareImportInstalledPackageSnapshot? = null

    candidates.forEach { candidate ->
        if (candidate.packageName.isBlank()) return@forEach
        // Polling reconciliation must only pick packages updated after this share was armed.
        if (candidate.lastUpdateTimeMs < pendingTrack.armedAtMillis) return@forEach
        when {
            first == null || candidate.lastUpdateTimeMs > first.lastUpdateTimeMs -> {
                third = second
                second = first
                first = candidate
            }

            second == null || candidate.lastUpdateTimeMs > second.lastUpdateTimeMs -> {
                third = second
                second = candidate
            }

            third == null || candidate.lastUpdateTimeMs > third.lastUpdateTimeMs -> {
                third = candidate
            }
        }
    }

    return listOfNotNull(first, second, third)
}

internal fun isShareImportExactPackageMatch(
    pendingTrack: GitHubPendingShareImportTrackRecord,
    packageSnapshot: ShareImportInstalledPackageSnapshot,
): Boolean {
    val expectedPackageName = pendingTrack.packageName.trim()
    if (expectedPackageName.isBlank()) return false
    if (packageSnapshot.packageName.trim() != expectedPackageName) return false
    val threshold = pendingTrack.armedAtMillis - shareImportTrackUpdateToleranceMs
    return packageSnapshot.lastUpdateTimeMs >= threshold
}

internal fun isShareImportAttachEventValid(
    event: AppPackageChangedEvent,
    armedAtMillis: Long,
    packageLastUpdateTimeMs: Long,
): Boolean {
    if (event.action == Intent.ACTION_PACKAGE_ADDED || event.action == Intent.ACTION_PACKAGE_REPLACED) {
        return true
    }
    if (event.action != Intent.ACTION_PACKAGE_CHANGED) return false
    if (packageLastUpdateTimeMs <= 0L) return false
    return packageLastUpdateTimeMs >= armedAtMillis - shareImportTrackUpdateToleranceMs
}
