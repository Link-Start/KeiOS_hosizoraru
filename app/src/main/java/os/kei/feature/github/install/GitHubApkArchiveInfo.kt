package os.kei.feature.github.install

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File

internal data class GitHubApkArchiveInfo(
    val packageName: String = "",
    val appLabel: String = "",
    val versionName: String = "",
    val versionCode: String = "",
    val minSdk: String = "",
    val targetSdk: String = "",
)

internal fun readGitHubApkArchiveInfo(
    context: Context,
    apkFile: File,
): GitHubApkArchiveInfo {
    val pm = context.packageManager
    val packageInfo =
        runCatching {
            pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(0),
            )
        }.getOrNull() ?: return GitHubApkArchiveInfo()
    val appInfo = packageInfo.applicationInfo?.applyArchiveSource(apkFile)
    val label =
        appInfo
            ?.let { info ->
                runCatching { info.loadLabel(pm).toString().trim() }.getOrDefault("")
            }.orEmpty()
    return GitHubApkArchiveInfo(
        packageName = packageInfo.packageName,
        appLabel = label,
        versionName = packageInfo.versionName?.trim().orEmpty(),
        versionCode =
            packageInfo.longVersionCode
                .takeIf { it >= 0L }
                ?.toString()
                .orEmpty(),
        minSdk =
            appInfo
                ?.minSdkVersion
                ?.takeIf { it >= 0 }
                ?.toString()
                .orEmpty(),
        targetSdk =
            appInfo
                ?.targetSdkVersion
                ?.takeIf { it >= 0 }
                ?.toString()
                .orEmpty(),
    )
}

private fun ApplicationInfo.applyArchiveSource(apkFile: File): ApplicationInfo {
    sourceDir = apkFile.absolutePath
    publicSourceDir = apkFile.absolutePath
    return this
}
