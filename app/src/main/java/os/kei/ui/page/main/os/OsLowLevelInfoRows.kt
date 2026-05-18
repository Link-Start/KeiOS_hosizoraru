package os.kei.ui.page.main.os

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.ext.SdkExtensions
import android.provider.Settings
import android.webkit.WebView
import java.util.Locale
import java.util.TimeZone

private fun firstKnown(vararg values: String): String =
    values
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() && !isInvalidValue(it) }
        .orEmpty()

private fun compactParts(vararg parts: String): String =
    parts
        .map { it.trim() }
        .filter { it.isNotBlank() && !isInvalidValue(it) }
        .distinct()
        .joinToString(" / ")

private fun labeled(
    label: String,
    value: String,
): String {
    val known = firstKnown(value)
    return if (known.isBlank()) "" else "$label=$known"
}

private fun prop(
    properties: Map<String, String>,
    key: String,
): String = properties[key].orEmpty()

private fun settingsGlobalString(
    context: Context,
    key: String,
): String =
    runCatching {
        Settings.Global.getString(context.contentResolver, key).orEmpty()
    }.getOrDefault("")

private fun webViewProvider(): String {
    return runCatching {
        val packageInfo = WebView.getCurrentWebViewPackage() ?: return@runCatching ""
        val packageName = packageInfo.packageName
        val versionName = packageInfo.versionName.orEmpty()
        val versionCode = packageInfo.longVersionCode
        compactParts(packageName, "$versionName ($versionCode)")
    }.getOrDefault("")
}

private fun fingerprintedPartitions(): String =
    runCatching {
        Build
            .getFingerprintedPartitions()
            .map { partition -> partition.name }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
    }.getOrDefault("")

private fun sdkExtensions(): String =
    runCatching {
        SdkExtensions
            .getAllExtensionVersions()
            .entries
            .filter { (_, version) -> version > 0 }
            .sortedBy { it.key }
            .joinToString(", ") { (sdk, version) -> "$sdk:$version" }
    }.getOrDefault("")

private fun partitionUpdateModel(properties: Map<String, String>): String {
    val abUpdate = prop(properties, "ro.build.ab_update")
    val virtualAb = prop(properties, "ro.virtual_ab.enabled")
    val virtualAbCompression = prop(properties, "ro.virtual_ab.compression.enabled")
    val slot = firstKnown(prop(properties, "ro.boot.slot_suffix"), prop(properties, "ro.boot.slot"))
    return compactParts(
        labeled("A/B", abUpdate),
        labeled("Virtual A/B", virtualAb),
        labeled("Compression", virtualAbCompression),
        if (slot.isBlank()) "" else "Slot=$slot",
    )
}

private fun verifiedBootState(properties: Map<String, String>): String =
    compactParts(
        labeled("Boot", prop(properties, "ro.boot.verifiedbootstate")),
        labeled("VBMeta", prop(properties, "ro.boot.vbmeta.device_state")),
        labeled("FlashLocked", prop(properties, "ro.boot.flash.locked")),
        labeled(
            "SecureBoot",
            firstKnown(
                prop(properties, "ro.secureboot.lockstate"),
                prop(properties, "ro.secureboot.devicelock"),
            ),
        ),
    )

private fun patchLevels(properties: Map<String, String>): String =
    compactParts(
        labeled("Build", prop(properties, "ro.build.version.security_patch")),
        labeled("Vendor", prop(properties, "ro.vendor.build.security_patch")),
    )

private fun firstApiLevel(properties: Map<String, String>): String =
    compactParts(
        labeled("Product", prop(properties, "ro.product.first_api_level")),
        labeled("Board", prop(properties, "ro.board.first_api_level")),
        labeled(
            "Vendor",
            firstKnown(
                prop(properties, "ro.vendor.api_level"),
                prop(properties, "ro.vendor.build.version.sdk"),
            ),
        ),
    )

private fun deviceIdentity(properties: Map<String, String>): String {
    val maker =
        firstKnown(
            prop(properties, "ro.product.manufacturer"),
            prop(properties, "ro.product.product.manufacturer"),
            Build.MANUFACTURER,
        )
    val marketName =
        firstKnown(
            prop(properties, "ro.product.marketname"),
            prop(properties, "ro.product.vendor.model"),
            prop(properties, "ro.product.model"),
            Build.MODEL,
        )
    val soc = compactParts(prop(properties, "ro.soc.model"), prop(properties, "ro.board.platform"))
    return compactParts(maker, marketName, soc)
}

private fun deviceProduct(properties: Map<String, String>): String =
    compactParts(
        labeled("Brand", firstKnown(prop(properties, "ro.product.brand"), Build.BRAND)),
        labeled("Device", firstKnown(prop(properties, "ro.product.device"), Build.DEVICE)),
        labeled("Product", firstKnown(prop(properties, "ro.product.name"), Build.PRODUCT)),
    )

private fun deviceModel(properties: Map<String, String>): String =
    compactParts(
        labeled("Model", firstKnown(prop(properties, "ro.product.model"), Build.MODEL)),
        labeled("Product", firstKnown(prop(properties, "ro.product.name"), Build.PRODUCT)),
        labeled("Device", firstKnown(prop(properties, "ro.product.device"), Build.DEVICE)),
        labeled("Manufacturer", firstKnown(prop(properties, "ro.product.manufacturer"), Build.MANUFACTURER)),
    )

private fun boardHardware(properties: Map<String, String>): String =
    compactParts(
        labeled("Board", firstKnown(prop(properties, "ro.product.board"), Build.BOARD)),
        labeled("Hardware", firstKnown(prop(properties, "ro.hardware"), prop(properties, "ro.boot.hardware"), Build.HARDWARE)),
        labeled("Platform", prop(properties, "ro.board.platform")),
    )

private fun buildReleaseSummary(properties: Map<String, String>): String =
    compactParts(
        labeled("Android", firstKnown(prop(properties, "ro.build.version.release"), Build.VERSION.RELEASE)),
        labeled("API", firstKnown(prop(properties, "ro.build.version.sdk"), Build.VERSION.SDK_INT.toString())),
        labeled("Build", firstKnown(prop(properties, "ro.build.id"), Build.ID)),
        labeled("Type", firstKnown(prop(properties, "ro.build.type"), Build.TYPE)),
        labeled("Tags", firstKnown(prop(properties, "ro.build.tags"), Build.TAGS)),
    )

private fun abiBitness(): String =
    compactParts(
        if (Build.SUPPORTED_64_BIT_ABIS.isEmpty()) {
            ""
        } else {
            "64-bit=${Build.SUPPORTED_64_BIT_ABIS.joinToString(",")}"
        },
        if (Build.SUPPORTED_32_BIT_ABIS.isEmpty()) {
            ""
        } else {
            "32-bit=${Build.SUPPORTED_32_BIT_ABIS.joinToString(",")}"
        },
    )

private fun cpuSummary(properties: Map<String, String>): String =
    compactParts(
        labeled("Cores", Runtime.getRuntime().availableProcessors().toString()),
        labeled("Arch", System.getProperty("os.arch").orEmpty()),
        labeled(
            "ABI",
            firstKnown(
                Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                prop(properties, "ro.product.cpu.abi"),
            ),
        ),
        labeled(
            "Hardware",
            firstKnown(
                prop(properties, "ro.product.board"),
                prop(properties, "ro.hardware"),
                prop(properties, "ro.boot.hardware"),
            ),
        ),
    )

private fun runtimeSummary(): String =
    compactParts(
        labeled("VM", compactParts(System.getProperty("java.vm.name").orEmpty(), System.getProperty("java.vm.version").orEmpty())),
        labeled("Runtime", System.getProperty("java.runtime.version").orEmpty()),
        labeled("OpenSSL", System.getProperty("android.openssl.version").orEmpty()),
    )

private fun zygoteMode(properties: Map<String, String>): String =
    compactParts(
        prop(properties, "ro.zygote"),
        labeled("MediaServer64", prop(properties, "ro.mediaserver.64b.enable")),
        labeled("VendorLazy32", prop(properties, "ro.vendor.mi_support_zygote32_lazyload")),
    )

private fun gsiAndVndk(properties: Map<String, String>): String =
    compactParts(
        labeled("VNDK", prop(properties, "ro.vndk.version")),
        labeled("LLNDK", prop(properties, "ro.llndk.api_level")),
        labeled("BoardAPI", prop(properties, "ro.board.api_level")),
    )

private fun localeTimezone(properties: Map<String, String>): String =
    compactParts(
        labeled("Locale", firstKnown(prop(properties, "persist.sys.locale"), Locale.getDefault().toLanguageTag())),
        labeled("TimeZone", TimeZone.getDefault().id),
    )

private fun debugState(context: Context): String =
    compactParts(
        labeled("DeveloperOptions", settingsGlobalString(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)),
        labeled("ADB", settingsGlobalString(context, Settings.Global.ADB_ENABLED)),
    )

private fun ramCapacity(context: Context): String {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return ""
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    return compactParts(
        labeled("TotalBytes", info.totalMem.toString()),
        labeled("AvailableBytes", info.availMem.toString()),
        labeled("LowMemory", info.lowMemory.toString()),
    )
}

private fun dataStorageCapacity(): String =
    runCatching {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.blockSizeLong * stat.blockCountLong
        val available = stat.blockSizeLong * stat.availableBlocksLong
        val used = (total - available).coerceAtLeast(0L)
        compactParts(
            labeled("UsedBytes", used.toString()),
            labeled("AvailableBytes", available.toString()),
            labeled("TotalBytes", total.toString()),
        )
    }.getOrDefault("")

internal fun lowLevelInfoRows(
    context: Context,
    systemProperties: Map<String, String>,
): List<InfoRow> =
    listOf(
        InfoRow("device.identity", deviceIdentity(systemProperties)),
        InfoRow("device.model", deviceModel(systemProperties)),
        InfoRow("device.product", deviceProduct(systemProperties)),
        InfoRow("device.board_hardware", boardHardware(systemProperties)),
        InfoRow("device.first_api_level", firstApiLevel(systemProperties)),
        InfoRow("build.release_summary", buildReleaseSummary(systemProperties)),
        InfoRow("build.fingerprint", firstKnown(prop(systemProperties, "ro.build.fingerprint"), Build.FINGERPRINT)),
        InfoRow("runtime.cpu_summary", cpuSummary(systemProperties)),
        InfoRow(
            "runtime.abi.primary",
            firstKnown(
                Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                prop(systemProperties, "ro.product.cpu.abi"),
            ),
        ),
        InfoRow("runtime.abi.supported", Build.SUPPORTED_ABIS.joinToString(", ")),
        InfoRow("runtime.abi.bitness", abiBitness()),
        InfoRow("runtime.vm", runtimeSummary()),
        InfoRow("runtime.zygote.mode", zygoteMode(systemProperties)),
        InfoRow("runtime.sdk_extensions", sdkExtensions()),
        InfoRow("memory.ram", ramCapacity(context)),
        InfoRow("storage.data", dataStorageCapacity()),
        InfoRow("environment.locale_timezone", localeTimezone(systemProperties)),
        InfoRow("environment.debug_state", debugState(context)),
        InfoRow("partition.update_model", partitionUpdateModel(systemProperties)),
        InfoRow(
            "partition.slot",
            firstKnown(
                prop(systemProperties, "ro.boot.slot_suffix"),
                prop(systemProperties, "ro.boot.slot"),
            ),
        ),
        InfoRow("partition.fingerprinted", fingerprintedPartitions()),
        InfoRow(
            "partition.dynamic",
            firstKnown(
                prop(systemProperties, "ro.boot.dynamic_partitions"),
                prop(systemProperties, "ro.boot.dynamic_partitions_retrofit"),
            ),
        ),
        InfoRow("treble.enabled", prop(systemProperties, "ro.treble.enabled")),
        InfoRow("gsi.vndk", gsiAndVndk(systemProperties)),
        InfoRow("mainline.apex_updatable", prop(systemProperties, "ro.apex.updatable")),
        InfoRow("verified_boot.state", verifiedBootState(systemProperties)),
        InfoRow("security.dm_verity", prop(systemProperties, "ro.boot.veritymode")),
        InfoRow("security.patch_levels", patchLevels(systemProperties)),
        InfoRow("webview.provider", webViewProvider()),
        InfoRow(
            "kernel.page_size",
            firstKnown(
                prop(systemProperties, "ro.boot.hardware.cpu.pagesize"),
                prop(systemProperties, "ro.product.cpu.pagesize.max"),
            ),
        ),
        InfoRow("kernel.version", firstKnown(prop(systemProperties, "ro.kernel.version"), System.getProperty("os.version").orEmpty())),
        boolFeatureRow(context.packageManager, PackageManager.FEATURE_USB_HOST, "feature.usb_host"),
    )
