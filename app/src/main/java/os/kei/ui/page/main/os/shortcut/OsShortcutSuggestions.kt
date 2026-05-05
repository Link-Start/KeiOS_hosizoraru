package os.kei.ui.page.main.os.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.Locale

internal enum class ShortcutSuggestionField {
    PackageName,
    ClassName,
    IntentAction,
    IntentCategory,
    IntentFlags,
    IntentUriData,
    IntentMimeType
}

internal data class ShortcutSuggestionItem(
    val label: String,
    val value: String,
    val summary: String,
    val append: Boolean = false,
    val relatedAppName: String = "",
    val classItemExported: Boolean = false
)

internal data class ShortcutInstalledAppOption(
    val appName: String,
    val packageName: String
)

internal data class ShortcutActivityClassOption(
    val className: String,
    val activityName: String,
    val isExported: Boolean
)

internal fun loadInstalledAppOptions(context: Context): List<ShortcutInstalledAppOption> {
    val pm = context.packageManager
    val packageInfos =
        pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
    val overlayFlagMask = runCatching {
        ApplicationInfo::class.java.getField("FLAG_IS_RESOURCE_OVERLAY").getInt(null)
    }.getOrDefault(0)
    return packageInfos.mapNotNull { info ->
        val packageName = info.packageName.trim()
        if (packageName.isBlank()) return@mapNotNull null
        val appInfo = info.applicationInfo ?: runCatching {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        }.getOrNull() ?: return@mapNotNull null
        if (
            shouldIgnoreInstalledAppForShortcut(
                packageName = packageName,
                appInfo = appInfo,
                hasAnyEnabledActivity = info.activities?.any { it.enabled } == true,
                overlayFlagMask = overlayFlagMask
            )
        ) {
            return@mapNotNull null
        }

        val appName = runCatching {
            pm.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName).trim().ifBlank { packageName }

        ShortcutInstalledAppOption(
            appName = appName,
            packageName = packageName
        )
    }.distinctBy { it.packageName }
        .sortedWith(
            compareBy<ShortcutInstalledAppOption> { it.appName.lowercase(Locale.ROOT) }
                .thenBy { it.packageName.lowercase(Locale.ROOT) }
        )
}

internal fun loadActivityClassOptions(
    context: Context,
    packageName: String
): List<ShortcutActivityClassOption> {
    val normalizedPackageName = packageName.trim()
    if (normalizedPackageName.isBlank()) return emptyList()
    val pm = context.packageManager
    val packageInfo = runCatching {
        pm.getPackageInfo(
            normalizedPackageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
        )
    }.getOrNull() ?: return emptyList()

    return packageInfo.activities.orEmpty()
        .asSequence()
        .filter { it.enabled || it.exported }
        .mapNotNull { info ->
            val raw = info.name.trim()
            if (raw.isBlank()) return@mapNotNull null
            val normalized = if (raw.startsWith(".")) {
                "$normalizedPackageName$raw"
            } else {
                raw
            }
            val activityName = runCatching {
                info.loadLabel(pm).toString()
            }.getOrNull()?.trim().orEmpty().ifBlank {
                normalized.substringAfterLast('.')
            }
            ShortcutActivityClassOption(
                className = normalized,
                activityName = activityName,
                isExported = info.exported
            )
        }
        .groupBy { it.className }
        .values
        .mapNotNull { options ->
            options.maxWithOrNull(
                compareBy<ShortcutActivityClassOption> { it.isExported }
                    .thenBy { it.activityName.length }
            )
        }
        .sortedWith(
            compareBy<ShortcutActivityClassOption> { it.activityName.lowercase(Locale.ROOT) }
                .thenBy { it.className.lowercase(Locale.ROOT) }
        )
        .toList()
}

private fun shouldIgnoreInstalledAppForShortcut(
    packageName: String,
    appInfo: ApplicationInfo,
    hasAnyEnabledActivity: Boolean,
    overlayFlagMask: Int
): Boolean {
    if (!hasAnyEnabledActivity) return true
    if (!appInfo.enabled) return true
    if ((appInfo.flags and ApplicationInfo.FLAG_INSTALLED) == 0) return true
    if ((appInfo.flags and ApplicationInfo.FLAG_HAS_CODE) == 0) return true
    if ((appInfo.flags and ApplicationInfo.FLAG_TEST_ONLY) != 0) return true
    if (overlayFlagMask != 0 && (appInfo.flags and overlayFlagMask) != 0) return true
    val normalizedPackageName = packageName.lowercase(Locale.ROOT)
    if (normalizedPackageName.contains(".overlay")) return true
    if (normalizedPackageName.startsWith("overlay.")) return true
    return false
}

internal fun parseIntentCategories(raw: String): List<String> {
    return parseIntentTokenText(raw)
}

internal fun parseIntentTokenText(raw: String): List<String> {
    return raw.split(',', ';', '|', '\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

internal fun mergeIntentTokenText(
    current: String,
    incoming: String,
    append: Boolean
): String {
    val normalizedIncoming = incoming.trim()
    if (!append) return normalizedIncoming
    if (normalizedIncoming.isBlank()) return ""

    val mergedTokens = linkedSetOf<String>()
    parseIntentTokenText(current).forEach { mergedTokens.add(it) }
    parseIntentTokenText(normalizedIncoming).forEach { token ->
        val duplicated = mergedTokens.any { it.equals(token, ignoreCase = true) }
        if (!duplicated) {
            mergedTokens.add(token)
        }
    }
    return mergedTokens.joinToString(", ")
}

internal fun parseIntentFlags(raw: String): Int {
    if (raw.isBlank()) return 0
    return raw.split(',', ';', '|', '\n', ' ')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .fold(0) { current, token ->
            current or parseSingleIntentFlag(token)
        }
}

private fun parseSingleIntentFlag(token: String): Int {
    val normalized = token.trim()
    if (normalized.isBlank()) return 0
    normalized.toIntOrNull()?.let { return it }
    normalized.removePrefix("0x").removePrefix("0X")
        .toLongOrNull(16)
        ?.toInt()
        ?.let { return it }

    val compact = normalized
        .removePrefix("Intent.")
        .removePrefix("android.content.Intent.")
        .uppercase(Locale.ROOT)
    return intentFlagNameValues[compact]
        ?: intentFlagNameValues["FLAG_ACTIVITY_$compact"]
        ?: 0
}

internal fun Int.ifBlankUse(fallback: Int): Int {
    return if (this == 0) fallback else this
}

private val intentFlagNameValues: Map<String, Int> = mapOf(
    "FLAG_ACTIVITY_NEW_TASK" to Intent.FLAG_ACTIVITY_NEW_TASK,
    "FLAG_ACTIVITY_SINGLE_TOP" to Intent.FLAG_ACTIVITY_SINGLE_TOP,
    "FLAG_ACTIVITY_CLEAR_TOP" to Intent.FLAG_ACTIVITY_CLEAR_TOP,
    "FLAG_ACTIVITY_FORWARD_RESULT" to Intent.FLAG_ACTIVITY_FORWARD_RESULT,
    "FLAG_ACTIVITY_PREVIOUS_IS_TOP" to Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP,
    "FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS" to Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
    "FLAG_ACTIVITY_BROUGHT_TO_FRONT" to Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT,
    "FLAG_ACTIVITY_RESET_TASK_IF_NEEDED" to Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
    "FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY" to Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY,
    "FLAG_ACTIVITY_MULTIPLE_TASK" to Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
    "FLAG_ACTIVITY_NO_USER_ACTION" to Intent.FLAG_ACTIVITY_NO_USER_ACTION,
    "FLAG_ACTIVITY_REORDER_TO_FRONT" to Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
    "FLAG_ACTIVITY_NO_ANIMATION" to Intent.FLAG_ACTIVITY_NO_ANIMATION,
    "FLAG_ACTIVITY_CLEAR_TASK" to Intent.FLAG_ACTIVITY_CLEAR_TASK,
    "FLAG_ACTIVITY_TASK_ON_HOME" to Intent.FLAG_ACTIVITY_TASK_ON_HOME,
    "FLAG_ACTIVITY_RETAIN_IN_RECENTS" to Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS,
    "FLAG_ACTIVITY_REQUIRE_NON_BROWSER" to Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER,
    "FLAG_ACTIVITY_REQUIRE_DEFAULT" to Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT,
    "FLAG_ACTIVITY_MATCH_EXTERNAL" to Intent.FLAG_ACTIVITY_MATCH_EXTERNAL
)
