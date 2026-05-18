package os.kei.ui.page.main.os

import android.content.Context
import os.kei.R

private data class TopInfoLabelSpec(
    val key: String,
    val labelRes: Int,
)

private val topInfoLabelSpecs =
    listOf(
        TopInfoLabelSpec("device.identity", R.string.os_top_info_label_device_identity),
        TopInfoLabelSpec("device.model", R.string.os_top_info_label_device_model),
        TopInfoLabelSpec("device.product", R.string.os_top_info_label_device_product),
        TopInfoLabelSpec("device.board_hardware", R.string.os_top_info_label_board_hardware),
        TopInfoLabelSpec("device.first_api_level", R.string.os_top_info_label_first_api_level),
        TopInfoLabelSpec("build.release_summary", R.string.os_top_info_label_build_release_summary),
        TopInfoLabelSpec("build.fingerprint", R.string.os_top_info_label_build_fingerprint),
        TopInfoLabelSpec("runtime.cpu_summary", R.string.os_top_info_label_cpu_summary),
        TopInfoLabelSpec("runtime.abi.primary", R.string.os_top_info_label_primary_abi),
        TopInfoLabelSpec("runtime.abi.supported", R.string.os_top_info_label_supported_abis),
        TopInfoLabelSpec("runtime.abi.bitness", R.string.os_top_info_label_abi_bitness),
        TopInfoLabelSpec("runtime.vm", R.string.os_top_info_label_runtime_vm),
        TopInfoLabelSpec("runtime.zygote.mode", R.string.os_top_info_label_zygote_mode),
        TopInfoLabelSpec("runtime.sdk_extensions", R.string.os_top_info_label_sdk_extensions),
        TopInfoLabelSpec("memory.ram", R.string.os_top_info_label_ram_capacity),
        TopInfoLabelSpec("storage.data", R.string.os_top_info_label_storage_capacity),
        TopInfoLabelSpec("environment.locale_timezone", R.string.os_top_info_label_locale_timezone),
        TopInfoLabelSpec("environment.debug_state", R.string.os_top_info_label_developer_debug_state),
        TopInfoLabelSpec("partition.update_model", R.string.os_top_info_label_update_model),
        TopInfoLabelSpec("partition.slot", R.string.os_top_info_label_current_slot),
        TopInfoLabelSpec("partition.fingerprinted", R.string.os_top_info_label_fingerprinted_partitions),
        TopInfoLabelSpec("partition.dynamic", R.string.os_top_info_label_dynamic_partitions),
        TopInfoLabelSpec("treble.enabled", R.string.os_top_info_label_treble),
        TopInfoLabelSpec("gsi.vndk", R.string.os_top_info_label_gsi_vndk),
        TopInfoLabelSpec("mainline.apex_updatable", R.string.os_top_info_label_mainline),
        TopInfoLabelSpec("verified_boot.state", R.string.os_top_info_label_verified_boot),
        TopInfoLabelSpec("security.dm_verity", R.string.os_top_info_label_dm_verity),
        TopInfoLabelSpec("security.patch_levels", R.string.os_top_info_label_security_patch),
        TopInfoLabelSpec("webview.provider", R.string.os_top_info_label_webview_provider),
        TopInfoLabelSpec("kernel.page_size", R.string.os_top_info_label_page_size),
        TopInfoLabelSpec("kernel.version", R.string.os_top_info_label_kernel_version),
        TopInfoLabelSpec("graphics.opengl.es", R.string.os_top_info_label_opengl_es),
        TopInfoLabelSpec("graphics.opengl.es.decoded", R.string.os_top_info_label_opengl_es_decoded),
        TopInfoLabelSpec("graphics.vulkan.version", R.string.os_top_info_label_vulkan_version),
        TopInfoLabelSpec("graphics.vulkan.level", R.string.os_top_info_label_vulkan_level),
        TopInfoLabelSpec("graphics.gpu.driver.package", R.string.os_top_info_label_gpu_driver_package),
        TopInfoLabelSpec("graphics.gpu.driver.version", R.string.os_top_info_label_gpu_driver_version),
        TopInfoLabelSpec("feature.usb_host", R.string.os_top_info_label_usb_host),
        TopInfoLabelSpec("feature.wifi_aware", R.string.os_top_info_label_wifi_aware),
        TopInfoLabelSpec("feature.opengl.aep", R.string.os_top_info_label_opengl_aep),
        TopInfoLabelSpec("feature.vulkan.version", R.string.os_top_info_label_vulkan_feature),
        TopInfoLabelSpec("adb_enabled", R.string.os_top_info_label_adb_enabled),
        TopInfoLabelSpec("adb_wifi_enabled", R.string.os_top_info_label_adb_wifi),
        TopInfoLabelSpec("ro.adb.secure", R.string.os_top_info_label_adb_secure),
        TopInfoLabelSpec("FBO_STATE_OPEN", R.string.os_top_info_label_fbo_open),
        TopInfoLabelSpec("fbo_status", R.string.os_top_info_label_fbo_status),
        TopInfoLabelSpec("aod_mode_user_set", R.string.os_top_info_label_aod_mode),
        TopInfoLabelSpec("aod_show_style", R.string.os_top_info_label_aod_style),
        TopInfoLabelSpec("autofill_service", R.string.os_top_info_label_autofill_service),
        TopInfoLabelSpec("credential_service", R.string.os_top_info_label_credential_service),
        TopInfoLabelSpec("gsm.network.type", R.string.os_top_info_label_network_type),
        TopInfoLabelSpec("gsm.sim.state", R.string.os_top_info_label_sim_state),
        TopInfoLabelSpec("gsm.version.baseband", R.string.os_top_info_label_baseband),
        TopInfoLabelSpec("ro.build.display.id", R.string.os_top_info_label_display_build),
        TopInfoLabelSpec("ro.build.version.release", R.string.os_top_info_label_android_release),
        TopInfoLabelSpec("ro.build.version.sdk", R.string.os_top_info_label_android_sdk),
        TopInfoLabelSpec("ro.build.version.security_patch", R.string.os_top_info_label_android_security_patch),
        TopInfoLabelSpec("ro.mi.os.version.name", R.string.os_top_info_label_mios_version),
        TopInfoLabelSpec("ro.miui.ui.version.name", R.string.os_top_info_label_miui_version),
        TopInfoLabelSpec("persist.sys.locale", R.string.os_top_info_label_locale),
        TopInfoLabelSpec("persist.sys.usb.config", R.string.os_top_info_label_usb_config),
        TopInfoLabelSpec("debug.device.usb_state", R.string.os_top_info_label_usb_state),
        TopInfoLabelSpec("dalvik.vm.usejit", R.string.os_top_info_label_dalvik_jit),
        TopInfoLabelSpec("toybox --version", R.string.os_top_info_label_toybox_version),
        TopInfoLabelSpec("getenforce", R.string.os_top_info_label_selinux),
    ).associateBy { it.key }

internal fun topInfoDisplayLabel(
    context: Context,
    key: String,
): String {
    topInfoLabelSpecs[key]?.let { return context.getString(it.labelRes) }
    return key
        .replace("ro.", "")
        .replace("persist.", "")
        .replace("sys.", "")
        .replace('_', ' ')
        .replace('.', ' ')
        .trim()
        .replaceFirstChar { it.titlecase() }
}

internal fun topInfoDisplayValue(
    context: Context,
    row: InfoRow,
): String {
    val value = row.value.trim()
    return when (row.key) {
        "treble.enabled" -> formatSupportValue(context, value, R.string.os_top_info_value_treble_supported)

        "mainline.apex_updatable" -> formatSupportValue(context, value, R.string.os_top_info_value_mainline_supported)

        "partition.dynamic" -> formatSupportValue(context, value, R.string.os_top_info_value_dynamic_partition_supported)

        "feature.usb_host" -> formatSupportValue(context, value, R.string.os_top_info_value_usb_host_supported)

        "feature.wifi_aware" -> formatSupportValue(context, value, R.string.os_top_info_value_wifi_aware_supported)

        "feature.opengl.aep" -> formatSupportValue(context, value, R.string.os_top_info_value_opengl_aep_supported)

        "feature.vulkan.version" -> formatSupportValue(context, value, R.string.os_top_info_value_vulkan_supported)

        "device.model",
        "device.product",
        "device.board_hardware",
        "runtime.vm",
        -> formatLabeledValues(value)

        "build.release_summary" -> formatBuildReleaseSummary(context, value)

        "device.first_api_level" -> formatFirstApiLevel(context, value)

        "runtime.abi.bitness" -> formatAbiBitness(context, value)

        "runtime.cpu_summary" -> formatCpuSummary(context, value)

        "partition.update_model" -> formatUpdateModel(context, value)

        "verified_boot.state" -> formatVerifiedBoot(context, value)

        "security.dm_verity" -> formatDmVerity(context, value)

        "memory.ram",
        "storage.data",
        -> formatCapacityValue(context, value)

        "environment.debug_state" -> formatDebugState(context, value)

        "environment.locale_timezone" -> formatLocaleTimezone(value)

        "security.patch_levels" -> value.replace(" / ", "\n")

        "runtime.sdk_extensions" -> value.ifBlank { context.getString(R.string.os_top_info_value_no_sdk_extension) }

        "partition.fingerprinted" -> value.ifBlank { context.getString(R.string.os_top_info_value_no_partition_snapshot) }

        "webview.provider" -> value.ifBlank { context.getString(R.string.os_top_info_value_webview_unknown) }

        "adb_enabled",
        "adb_wifi_enabled",
        "ro.adb.secure",
        "dalvik.vm.usejit",
        -> formatEnabledValue(context, value)

        else -> value
    }
}

private fun labeledParts(value: String): Map<String, String> =
    value
        .split(" / ")
        .mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) return@mapNotNull null
            part.take(index) to part.drop(index + 1)
        }.toMap()

private fun formatSupportValue(
    context: Context,
    value: String,
    supportedRes: Int,
): String =
    when (value.lowercase()) {
        "true", "1", "yes", "y" -> context.getString(supportedRes)
        "false", "0", "no", "n" -> context.getString(R.string.os_top_info_value_not_supported)
        else -> value
    }

private fun formatEnabledValue(
    context: Context,
    value: String,
): String =
    when (value.lowercase()) {
        "true", "1", "yes", "y", "enabled" -> context.getString(R.string.os_top_info_value_enabled)
        "false", "0", "no", "n", "disabled" -> context.getString(R.string.os_top_info_value_disabled)
        else -> value
    }

private fun formatAbiBitness(
    context: Context,
    value: String,
): String {
    val has64 = value.contains("64-bit=")
    val has32 = value.contains("32-bit=")
    return when {
        has64 && has32 -> context.getString(R.string.os_top_info_value_abi_64_with_32, value)
        has64 -> context.getString(R.string.os_top_info_value_abi_64_only, value)
        has32 -> context.getString(R.string.os_top_info_value_abi_32_only, value)
        else -> value
    }
}

private fun formatLabeledValues(value: String): String =
    labeledParts(value)
        .values
        .filter { it.isNotBlank() }
        .joinToString(" / ")
        .ifBlank { value }

private fun formatFirstApiLevel(
    context: Context,
    value: String,
): String {
    val parts = labeledParts(value)
    val product = parts["Product"].orEmpty()
    val vendor = parts["Vendor"].orEmpty()
    return if (product.isBlank() && vendor.isBlank()) {
        value
    } else {
        context.getString(R.string.os_top_info_value_first_api_level, product, vendor)
    }
}

private fun formatBuildReleaseSummary(
    context: Context,
    value: String,
): String {
    val parts = labeledParts(value)
    val release = parts["Android"].orEmpty()
    val api = parts["API"].orEmpty()
    val build = parts["Build"].orEmpty()
    val type = parts["Type"].orEmpty()
    val tags = parts["Tags"].orEmpty()
    return if (release.isBlank() && api.isBlank()) {
        value
    } else {
        context.getString(R.string.os_top_info_value_build_release_summary, release, api, build, type, tags)
    }
}

private fun formatCpuSummary(
    context: Context,
    value: String,
): String {
    val parts = labeledParts(value)
    val cores = parts["Cores"].orEmpty()
    val arch = parts["Arch"].orEmpty()
    val abi = parts["ABI"].orEmpty()
    val hardware = parts["Hardware"].orEmpty()
    return if (cores.isBlank()) {
        value
    } else {
        context.getString(R.string.os_top_info_value_cpu_summary, cores, arch, abi, hardware)
    }
}

private fun formatCapacityValue(
    context: Context,
    value: String,
): String {
    val parts = labeledParts(value)
    val total = parts["TotalBytes"]?.toLongOrNull()
    val available = parts["AvailableBytes"]?.toLongOrNull()
    val used = parts["UsedBytes"]?.toLongOrNull()
    return when {
        total != null && available != null && used != null -> {
            context.getString(
                R.string.os_top_info_value_capacity_used_available,
                formatBytes(used),
                formatBytes(available),
                formatBytes(total),
            )
        }

        total != null && available != null -> {
            context.getString(
                R.string.os_top_info_value_capacity_available,
                formatBytes(available),
                formatBytes(total),
            )
        }

        else -> {
            value
        }
    }
}

private fun formatDebugState(
    context: Context,
    value: String,
): String {
    val parts = labeledParts(value)
    val developerOptions =
        formatEnabledValue(context, parts["DeveloperOptions"].orEmpty())
    val adb =
        formatEnabledValue(context, parts["ADB"].orEmpty())
    return context.getString(R.string.os_top_info_value_debug_state, developerOptions, adb)
}

private fun formatLocaleTimezone(value: String): String =
    labeledParts(value)
        .values
        .filter { it.isNotBlank() }
        .joinToString(" / ")
        .ifBlank { value }

private fun formatDmVerity(
    context: Context,
    value: String,
): String =
    when (value.lowercase()) {
        "enforcing" -> context.getString(R.string.os_top_info_value_dm_verity_enforcing)
        "eio" -> context.getString(R.string.os_top_info_value_dm_verity_eio)
        "logging" -> context.getString(R.string.os_top_info_value_dm_verity_logging)
        "disabled" -> context.getString(R.string.os_top_info_value_dm_verity_disabled)
        else -> value
    }

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "$bytes ${units[unitIndex]}"
    } else {
        String.format(java.util.Locale.US, "%.1f %s", value, units[unitIndex])
    }
}

private fun formatUpdateModel(
    context: Context,
    value: String,
): String {
    val lower = value.lowercase()
    val hasAb = lower.contains("a/b=true")
    val hasVirtualAb = lower.contains("virtual a/b=true")
    val hasCompression = lower.contains("compression=true")
    val slot =
        value
            .split(" / ")
            .firstOrNull { it.startsWith("Slot=") }
            ?.removePrefix("Slot=")
            .orEmpty()
    val mode =
        when {
            hasVirtualAb && hasCompression -> context.getString(R.string.os_top_info_value_update_virtual_ab_compressed)
            hasVirtualAb -> context.getString(R.string.os_top_info_value_update_virtual_ab)
            hasAb -> context.getString(R.string.os_top_info_value_update_ab)
            else -> context.getString(R.string.os_top_info_value_update_basic)
        }
    return if (slot.isBlank()) mode else context.getString(R.string.os_top_info_value_update_with_slot, mode, slot)
}

private fun formatVerifiedBoot(
    context: Context,
    value: String,
): String {
    val boot =
        value
            .split(" / ")
            .firstOrNull { it.startsWith("Boot=") }
            ?.removePrefix("Boot=")
            .orEmpty()
    val vbMeta =
        value
            .split(" / ")
            .firstOrNull { it.startsWith("VBMeta=") }
            ?.removePrefix("VBMeta=")
            .orEmpty()
    val bootText =
        when (boot.lowercase()) {
            "green" -> context.getString(R.string.os_top_info_value_verified_boot_green)
            "yellow" -> context.getString(R.string.os_top_info_value_verified_boot_yellow)
            "orange" -> context.getString(R.string.os_top_info_value_verified_boot_orange)
            "red" -> context.getString(R.string.os_top_info_value_verified_boot_red)
            else -> value
        }
    return if (vbMeta.isBlank()) bootText else context.getString(R.string.os_top_info_value_verified_boot_with_state, bootText, vbMeta)
}
