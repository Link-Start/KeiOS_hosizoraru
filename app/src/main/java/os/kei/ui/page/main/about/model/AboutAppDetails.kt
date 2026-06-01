package os.kei.ui.page.main.about.model

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import androidx.compose.runtime.Immutable
import os.kei.BuildConfig
import os.kei.R
import os.kei.ui.page.main.about.util.formatTime

@Immutable
internal data class AboutAppDetails(
    val appLabel: String = "",
    val packageName: String = "",
    val versionText: String = "",
    val buildType: String = "",
    val commitTime: String = "",
    val buildTime: String = "",
    val updatedAt: String = "",
    val debugEnabledText: String = "",
    val testOnlyEnabledText: String = "",
    val apiLevel: String = "",
    val securityPatch: String = "",
    val iconContentDescription: String = "",
)

internal fun buildAboutAppDetails(
    context: Context,
    appLabel: String,
    packageInfo: PackageInfo?,
): AboutAppDetails {
    val unknown = context.getString(R.string.common_unknown)
    val applicationInfo: ApplicationInfo? = packageInfo?.applicationInfo
    val debugEnabled = (((applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
    val testOnlyEnabled = (((applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_TEST_ONLY) != 0)
    return AboutAppDetails(
        appLabel = appLabel,
        packageName = packageInfo?.packageName ?: unknown,
        versionText =
            packageInfo?.let {
                context.getString(
                    R.string.about_value_version_format,
                    it.versionName ?: unknown,
                    it.longVersionCode,
                )
            } ?: unknown,
        buildType = BuildConfig.BUILD_TYPE,
        commitTime = BuildConfig.COMMIT_TIME_MILLIS
            .takeIf { it > 0L }
            ?.let(::formatTime)
            ?.ifBlank { unknown }
            ?: unknown,
        buildTime = BuildConfig.BUILD_TIME_MILLIS
            .takeIf { it > 0L }
            ?.let(::formatTime)
            ?.ifBlank { unknown }
            ?: unknown,
        updatedAt = packageInfo?.lastUpdateTime?.let(::formatTime)?.ifBlank { unknown } ?: unknown,
        debugEnabledText = context.getString(if (debugEnabled) R.string.about_value_yes else R.string.about_value_no),
        testOnlyEnabledText = context.getString(if (testOnlyEnabled) R.string.about_value_yes else R.string.about_value_no),
        apiLevel = Build.VERSION.SDK_INT.toString(),
        securityPatch = Build.VERSION.SECURITY_PATCH ?: unknown,
        iconContentDescription = packageInfo?.packageName ?: context.packageName,
    )
}
