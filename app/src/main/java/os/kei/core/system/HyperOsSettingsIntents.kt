package os.kei.core.system

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

internal object HyperOsSettingsIntents {
    private const val SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
    private const val POWER_DETAIL_ACTIVITY =
        "com.miui.powercenter.legacypowerrank.PowerDetailActivity"
    private const val APP_PERMISSION_EDITOR_ACTION = "miui.intent.action.APP_PERM_EDITOR"
    private const val APP_PERMISSIONS_SETTINGS_ACTION = "android.settings.APP_PERMISSIONS_SETTINGS"
    private const val HYPER_OS_VERSION_PREFIX = "OS3"

    fun isHyperOs3Device(): Boolean {
        return readSystemProperty("ro.mi.os.version.name")
            ?.startsWith(HYPER_OS_VERSION_PREFIX, ignoreCase = true) == true
    }

    @SuppressLint("BatteryLife")
    fun buildBatteryOptimizationIntent(context: Context, alreadyIgnored: Boolean): Intent? {
        val packageManager = context.packageManager
        val packageUri = "package:${context.packageName}".toUri()
        val candidateIntents = buildList {
            if (isHyperOs3Device()) {
                add(buildPowerDetailIntent(context))
            }
            if (!alreadyIgnored) {
                add(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri))
                add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
        }
        return candidateIntents.firstOrNull { intent ->
            intent.resolveActivity(packageManager) != null
        }?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildAppListPermissionIntent(context: Context): Intent? {
        val packageManager = context.packageManager
        val packageUri = "package:${context.packageName}".toUri()
        val candidateIntents = buildList {
            if (isHyperOs3Device()) {
                add(buildHyperOsAppPermissionEditorIntent(context))
            }
            add(
                Intent(APP_PERMISSIONS_SETTINGS_ACTION).apply {
                    putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
                }
            )
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
        }
        return candidateIntents.firstOrNull { intent ->
            intent.resolveActivity(packageManager) != null
        }?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun buildPowerDetailIntent(context: Context): Intent {
        return Intent().setComponent(
            ComponentName(SECURITY_CENTER_PACKAGE, POWER_DETAIL_ACTIVITY)
        ).apply {
            data = "package:${context.packageName}".toUri()
            putExtra("iconPackage", context.packageName)
            putExtra("package_name", context.packageName)
            putExtra("uid", context.applicationInfo.uid)
            putExtra("showMenus", false)
            putExtra("UserId", resolveAndroidUserId(context.applicationInfo.uid))
        }
    }

    private fun buildHyperOsAppPermissionEditorIntent(context: Context): Intent {
        return Intent(APP_PERMISSION_EDITOR_ACTION).apply {
            setPackage(SECURITY_CENTER_PACKAGE)
            putExtra("extra_pkgname", context.packageName)
        }
    }

    private fun readSystemProperty(key: String): String? {
        return findPropString(key).trim().takeIf { it.isNotBlank() }
    }

    private fun resolveAndroidUserId(uid: Int): Int {
        return uid / 100000
    }
}
