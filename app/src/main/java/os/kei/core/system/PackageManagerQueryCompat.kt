package os.kei.core.system

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.BadParcelableException
import android.os.DeadObjectException
import android.os.TransactionTooLargeException

internal fun PackageManager.getInstalledPackageInfosSafely(
    flags: PackageManager.PackageInfoFlags = PackageManager.PackageInfoFlags.of(0),
    launcherFallback: Boolean = true
): List<PackageInfo> {
    val primary = runCatching {
        getInstalledPackages(flags)
    }
    primary.getOrNull()?.let { return it }

    val error = primary.exceptionOrNull()
    if (error?.isPackageManagerBulkQueryFailure() != true) {
        throw error ?: IllegalStateException("Installed package query failed")
    }
    if (!launcherFallback) return emptyList()

    return runCatching {
        queryLaunchablePackageInfos(flags)
    }.getOrDefault(emptyList())
}

internal fun PackageManager.queryLaunchablePackageInfos(
    flags: PackageManager.PackageInfoFlags = PackageManager.PackageInfoFlags.of(0)
): List<PackageInfo> {
    val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return queryIntentActivities(launchIntent, PackageManager.ResolveInfoFlags.of(0))
        .asSequence()
        .mapNotNull { it.activityInfo?.packageName?.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .mapNotNull { packageName -> getPackageInfoOrNull(packageName, flags) }
        .toList()
}

internal fun Throwable.isPackageManagerBulkQueryFailure(): Boolean {
    return this is BadParcelableException ||
            this is DeadObjectException ||
            this is TransactionTooLargeException ||
            cause?.isPackageManagerBulkQueryFailure() == true
}

private fun PackageManager.getPackageInfoOrNull(
    packageName: String,
    flags: PackageManager.PackageInfoFlags
): PackageInfo? {
    return runCatching {
        getPackageInfo(packageName, flags)
    }.getOrNull()
}
