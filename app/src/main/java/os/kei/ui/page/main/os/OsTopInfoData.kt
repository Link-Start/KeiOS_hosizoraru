package os.kei.ui.page.main.os

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import os.kei.R

internal data class TopInfoTopic(
    val order: Int,
    @param:StringRes val titleRes: Int
)

internal enum class TopInfoRowsStatus {
    NotLoaded,
    EmptyFresh,
    Cached,
    Refreshing,
    Fresh
}

@Immutable
internal data class TopInfoRowsSnapshot(
    val rows: List<InfoRow>,
    val status: TopInfoRowsStatus
) {
    val hasRows: Boolean
        get() = rows.isNotEmpty()

    val loadedFresh: Boolean
        get() = status == TopInfoRowsStatus.Fresh || status == TopInfoRowsStatus.EmptyFresh
}

private fun String.startsWithAny(vararg prefixes: String): Boolean =
    prefixes.any { prefix -> startsWith(prefix) }

private fun String.containsAny(vararg terms: String): Boolean =
    terms.any { term -> contains(term) }

internal fun topInfoTopicOf(key: String): TopInfoTopic {
    val k = key.lowercase()
    return when {
        k.startsWith("long_press_") -> TopInfoTopic(0, R.string.os_top_info_topic_long_press)
        k.contains("fbo") || k == "key_fbo_data" -> TopInfoTopic(1, R.string.os_top_info_topic_fbo)
        k.startsWith("device.") ||
            k == "ro.product.marketname" ||
            k == "ro.product.model" ||
            k == "ro.soc.model" -> TopInfoTopic(2, R.string.os_top_info_topic_device_identity)

        k.startsWith("build.") -> TopInfoTopic(3, R.string.os_top_info_topic_version_build)

        k.startsWith("runtime.") ||
            k.containsAny("zygote", "abilist", "cpu.abi") -> TopInfoTopic(4, R.string.os_top_info_topic_runtime_architecture)
        k.startsWithAny("memory.", "storage.") -> TopInfoTopic(5, R.string.os_top_info_topic_capacity)
        k.startsWith("environment.") -> TopInfoTopic(6, R.string.os_top_info_topic_environment)
        k.startsWithAny("partition.", "treble.", "gsi.", "mainline.", "ro.apex", "ro.vndk", "ro.treble") ->
            TopInfoTopic(7, R.string.os_top_info_topic_partition_mainline)
        k.startsWithAny("verified_boot.", "security.") ||
            k.containsAny("verifiedboot", "vbmeta", "secureboot", "security_patch") ->
            TopInfoTopic(8, R.string.os_top_info_topic_security_integrity)
        k.contains("dex2oat") || k.contains("dexopt") -> TopInfoTopic(9, R.string.os_top_info_topic_dex_optimization)
        k.contains("aod") -> TopInfoTopic(10, R.string.os_top_info_topic_aod)
        k.contains("density") || k.contains("resolution") -> TopInfoTopic(11, R.string.os_top_info_topic_display_density)
        k.contains("autofill") || k.contains("credential") -> TopInfoTopic(12, R.string.os_top_info_topic_autofill_credentials)
        k.startsWith("gsm.") || k.contains("gsm") -> TopInfoTopic(13, R.string.os_top_info_topic_cellular_network)
        k.contains("level") -> TopInfoTopic(14, R.string.os_top_info_topic_level)
        k.startsWith("adb_") || k.contains("adb") -> TopInfoTopic(15, R.string.os_top_info_topic_adb_debugging)
        k.contains("usb") -> TopInfoTopic(16, R.string.os_top_info_topic_usb)
        k.startsWith("webview.") -> TopInfoTopic(17, R.string.os_top_info_topic_webview)
        k.containsAny("vulkan", "opengl", "egl", "graphics", "hwui") ->
            TopInfoTopic(18, R.string.os_top_info_topic_graphics_rendering)
        k.startsWithAny("miui_", "ro.miui", "ro.mi.") || k.contains("xiaomi") ->
            TopInfoTopic(19, R.string.os_top_info_topic_miui_xiaomi)
        k.contains("version") || k.contains("build") || k.contains("fingerprint") -> TopInfoTopic(20, R.string.os_top_info_topic_version_build)
        k.contains("time") || k.contains("timestamp") -> TopInfoTopic(21, R.string.os_top_info_topic_timestamp)
        k.startsWith("java.") || k.startsWith("android.") || k.startsWith("os.") || k.startsWith("user.") -> TopInfoTopic(22, R.string.os_top_info_topic_java_system_properties)
        k.startsWith("env.") ||
            k == "uname-a" ||
            k == "proc.version" ||
            k == "toybox --version" ||
            k == "getenforce" ||
            k.startsWith("kernel.") -> TopInfoTopic(23, R.string.os_top_info_topic_linux_environment)
        else -> TopInfoTopic(99, R.string.os_top_info_topic_other)
    }
}

internal fun sortTopInfoRows(rows: List<InfoRow>): List<InfoRow> {
    return rows.sortedWith(
        compareBy<InfoRow>(
            { topInfoTopicOf(it.key).order },
            { detectValueType(it.value).rank },
            { it.key.lowercase() }
        )
    )
}

internal fun groupTopInfoRows(context: Context, rows: List<InfoRow>): List<Pair<String, List<InfoRow>>> {
    val grouped = rows.groupBy { topInfoTopicOf(it.key) }
    return grouped.entries
        .sortedBy { it.key.order }
        .map { entry -> context.getString(entry.key.titleRes) to entry.value }
}

internal fun removeTopInfoRows(section: SectionKind, rows: List<InfoRow>): List<InfoRow> {
    val keySet = when (section) {
        SectionKind.SYSTEM -> TopInfoKeys.system
        SectionKind.SECURE -> TopInfoKeys.secure
        SectionKind.GLOBAL -> TopInfoKeys.global
        SectionKind.ANDROID -> TopInfoKeys.android
        SectionKind.JAVA -> TopInfoKeys.java
        SectionKind.LINUX -> TopInfoKeys.linux
    }
    return rows.filterNot { keySet.contains(it.key) }
}

private fun mapRows(rows: List<InfoRow>): Map<String, String> = rows.associate { it.key to it.value }

internal fun buildTopInfoRows(
    systemRows: List<InfoRow>,
    secureRows: List<InfoRow>,
    globalRows: List<InfoRow>,
    androidRows: List<InfoRow>,
    javaRows: List<InfoRow>,
    linuxRows: List<InfoRow>
): List<InfoRow> {
    val systemMap = mapRows(systemRows)
    val secureMap = mapRows(secureRows)
    val globalMap = mapRows(globalRows)
    val androidMap = mapRows(androidRows)
    val javaMap = mapRows(javaRows)
    val linuxMap = mapRows(linuxRows)

    val rows = mutableListOf<InfoRow>()
    TopInfoKeys.system.forEach { key -> systemMap[key]?.let { rows += InfoRow(key, it) } }
    TopInfoKeys.secure.forEach { key -> secureMap[key]?.let { rows += InfoRow(key, it) } }
    TopInfoKeys.global.forEach { key -> globalMap[key]?.let { rows += InfoRow(key, it) } }
    TopInfoKeys.android.forEach { key -> androidMap[key]?.let { rows += InfoRow(key, it) } }
    TopInfoKeys.java.forEach { key -> javaMap[key]?.let { rows += InfoRow(key, it) } }
    TopInfoKeys.linux.forEach { key -> linuxMap[key]?.let { rows += InfoRow(key, it) } }
    return sortTopInfoRows(cleanRows(rows))
}

internal fun buildTopInfoRowsSnapshot(
    sectionStates: Map<SectionKind, SectionState>
): TopInfoRowsSnapshot {
    val systemRows = sectionStates[SectionKind.SYSTEM]?.rows ?: emptyList()
    val secureRows = sectionStates[SectionKind.SECURE]?.rows ?: emptyList()
    val globalRows = sectionStates[SectionKind.GLOBAL]?.rows ?: emptyList()
    val androidRows = sectionStates[SectionKind.ANDROID]?.rows ?: emptyList()
    val javaRows = sectionStates[SectionKind.JAVA]?.rows ?: emptyList()
    val linuxRows = sectionStates[SectionKind.LINUX]?.rows ?: emptyList()
    val rows = buildTopInfoRows(
        systemRows = systemRows,
        secureRows = secureRows,
        globalRows = globalRows,
        androidRows = androidRows,
        javaRows = javaRows,
        linuxRows = linuxRows
    )
    val states = SectionKind.entries.map { kind -> sectionStates[kind] ?: SectionState() }
    if (states.any { it.loading }) {
        return TopInfoRowsSnapshot(rows = rows, status = TopInfoRowsStatus.Refreshing)
    }
    if (rows.isEmpty()) {
        val loadedFresh = states.any { it.loadedFresh }
        return TopInfoRowsSnapshot(
            rows = rows,
            status = if (loadedFresh) TopInfoRowsStatus.EmptyFresh else TopInfoRowsStatus.NotLoaded
        )
    }

    val contributingFresh = listOf(
        SectionKind.SYSTEM to systemRows.any { it.key in TopInfoKeys.system },
        SectionKind.SECURE to secureRows.any { it.key in TopInfoKeys.secure },
        SectionKind.GLOBAL to globalRows.any { it.key in TopInfoKeys.global },
        SectionKind.ANDROID to androidRows.any { it.key in TopInfoKeys.android },
        SectionKind.JAVA to javaRows.any { it.key in TopInfoKeys.java },
        SectionKind.LINUX to linuxRows.any { it.key in TopInfoKeys.linux }
    )
        .filter { (_, contributes) -> contributes }
        .all { (kind, _) -> sectionStates[kind]?.loadedFresh == true }
    return TopInfoRowsSnapshot(
        rows = rows,
        status = if (contributingFresh) TopInfoRowsStatus.Fresh else TopInfoRowsStatus.Cached
    )
}
