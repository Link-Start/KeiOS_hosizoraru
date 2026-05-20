package os.kei.ui.page.main.os

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.system.RuntimeCommandExecutor
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.core.system.getAllJavaPropertiesSnapshot
import os.kei.core.system.getAllJavaPropertiesSnapshotAsync
import os.kei.core.system.getAllSystemPropertiesSnapshot
import os.kei.core.system.getAllSystemPropertiesSnapshotAsync

internal fun execRuntimeCommand(command: String): String? {
    return RuntimeCommandExecutor.execute(command).stdout.ifBlank { null }
}

internal suspend fun execRuntimeCommandAsync(command: String): String? {
    return RuntimeCommandExecutor.executeAsync(command).stdout.ifBlank { null }
}

internal fun parseKeyValueLines(raw: String?): List<InfoRow> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && it.contains("=") }
        .mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) return@mapNotNull null
            InfoRow(
                key = line.substring(0, index).trim(),
                value = line.substring(index + 1).trim()
            )
        }
        .toList()
}

internal fun commandRows(command: String, shizukuApiUtils: ShizukuApiUtils): List<InfoRow> {
    val shizuku = shizukuApiUtils.execCommand(command)
    val runtime = if (shizuku.isNullOrBlank()) execRuntimeCommand(command) else null
    return parseKeyValueLines(shizuku ?: runtime)
}

internal suspend fun commandRowsAsync(
    command: String,
    shizukuApiUtils: ShizukuApiUtils,
    dispatcher: CoroutineDispatcher = AppDispatchers.osOperations
): List<InfoRow> {
    val shizuku = shizukuApiUtils.execCommandCancellable(command)
    val runtime = if (shizuku.isNullOrBlank()) execRuntimeCommandAsync(command) else null
    return withContext(dispatcher) {
        parseKeyValueLines(shizuku ?: runtime)
    }
}

internal data class OsSettingsSectionSnapshot(
    val rowsBySection: Map<SectionKind, List<InfoRow>>
) {
    val hasRows: Boolean
        get() = rowsBySection.values.any { it.isNotEmpty() }

    fun rowsFor(section: SectionKind): List<InfoRow>? = rowsBySection[section]
}

internal data class OsLinuxCommandSnapshot(
    val uname: String = "",
    val getenforce: String = "",
    val procVersion: String = "",
    val toyboxVersion: String = ""
)

internal data class OsLinuxProbeSnapshot(
    val runtime: OsLinuxCommandSnapshot,
    val shizuku: OsLinuxCommandSnapshot
)

internal data class OsPageDataSnapshot(
    val settingsSections: OsSettingsSectionSnapshot? = null,
    val systemProperties: Map<String, String>? = null,
    val javaProperties: Map<String, String>? = null,
    val linuxProbe: OsLinuxProbeSnapshot? = null
) {
    companion object {
        fun loadForExport(shizukuApiUtils: ShizukuApiUtils): OsPageDataSnapshot {
            return OsPageDataSnapshot(
                settingsSections = loadSettingsSectionSnapshot(shizukuApiUtils),
                systemProperties = getAllSystemPropertiesSnapshot(forceRefresh = true),
                javaProperties = getAllJavaPropertiesSnapshot(forceRefresh = true),
                linuxProbe = loadLinuxProbeSnapshot(shizukuApiUtils)
            )
        }

        suspend fun loadForExportAsync(
            shizukuApiUtils: ShizukuApiUtils,
            dispatcher: CoroutineDispatcher = AppDispatchers.osOperations
        ): OsPageDataSnapshot = coroutineScope {
            val systemProperties = async {
                getAllSystemPropertiesSnapshotAsync(forceRefresh = true, dispatcher = dispatcher)
            }
            val javaProperties = async {
                getAllJavaPropertiesSnapshotAsync(forceRefresh = true, dispatcher = dispatcher)
            }
            val settingsSections = async {
                loadSettingsSectionSnapshotAsync(shizukuApiUtils, dispatcher)
            }
            val linuxProbe = async {
                loadLinuxProbeSnapshotAsync(shizukuApiUtils, dispatcher)
            }
            OsPageDataSnapshot(
                settingsSections = settingsSections.await(),
                systemProperties = systemProperties.await(),
                javaProperties = javaProperties.await(),
                linuxProbe = linuxProbe.await()
            )
        }
    }
}

internal fun settingsRowsForSection(
    section: SectionKind,
    shizukuApiUtils: ShizukuApiUtils
): List<InfoRow> {
    return commandRows(settingsListCommand(section), shizukuApiUtils)
}

internal suspend fun settingsRowsForSectionAsync(
    section: SectionKind,
    shizukuApiUtils: ShizukuApiUtils,
    dispatcher: CoroutineDispatcher = AppDispatchers.osOperations
): List<InfoRow> {
    return commandRowsAsync(settingsListCommand(section), shizukuApiUtils, dispatcher)
}

internal fun loadSettingsSectionSnapshot(shizukuApiUtils: ShizukuApiUtils): OsSettingsSectionSnapshot {
    val shizuku = shizukuApiUtils.execCommand(settingsProbeCommand, timeoutMs = SETTINGS_PROBE_TIMEOUT_MS)
    val shizukuSnapshot = parseSettingsSectionSnapshot(shizuku)
    if (shizukuSnapshot.hasRows) return shizukuSnapshot
    val runtime = execRuntimeCommand(settingsProbeCommand)
    return parseSettingsSectionSnapshot(runtime)
}

internal suspend fun loadSettingsSectionSnapshotAsync(
    shizukuApiUtils: ShizukuApiUtils,
    dispatcher: CoroutineDispatcher = AppDispatchers.osOperations
): OsSettingsSectionSnapshot {
    val shizuku = shizukuApiUtils.execCommandCancellable(
        command = settingsProbeCommand,
        timeoutMs = SETTINGS_PROBE_TIMEOUT_MS
    )
    val shizukuSnapshot = withContext(dispatcher) {
        parseSettingsSectionSnapshot(shizuku)
    }
    if (shizukuSnapshot.hasRows) return shizukuSnapshot
    val runtime = execRuntimeCommandAsync(settingsProbeCommand)
    return withContext(dispatcher) {
        parseSettingsSectionSnapshot(runtime)
    }
}

internal fun parseSettingsSectionSnapshot(raw: String?): OsSettingsSectionSnapshot {
    if (raw.isNullOrBlank()) return OsSettingsSectionSnapshot(emptyMap())
    val rowsBySection = linkedMapOf<SectionKind, MutableList<InfoRow>>()
    var activeSection: SectionKind? = null
    raw.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@forEach
        val index = trimmed.indexOf('=')
        if (index <= 0) return@forEach
        val key = trimmed.substring(0, index).trim()
        val value = trimmed.substring(index + 1).trim()
        if (key == SETTINGS_SECTION_MARKER) {
            activeSection = settingsSectionIdMap[value]
            return@forEach
        }
        val section = activeSection ?: return@forEach
        rowsBySection.getOrPut(section) { mutableListOf() } += InfoRow(key, value)
    }
    return OsSettingsSectionSnapshot(rowsBySection)
}

private fun settingsListCommand(section: SectionKind): String {
    return settingsSectionCommands[section] ?: error("Unsupported settings section: $section")
}

private fun parseLinuxCommandSnapshot(raw: String?): OsLinuxCommandSnapshot {
    val rows = parseKeyValueLines(raw).associate { it.key to it.value }
    return OsLinuxCommandSnapshot(
        uname = rows["uname-a"].orEmpty(),
        getenforce = rows["getenforce"].orEmpty(),
        procVersion = rows["proc.version"].orEmpty(),
        toyboxVersion = rows["toybox --version"].orEmpty()
    )
}

internal fun loadLinuxProbeSnapshot(shizukuApiUtils: ShizukuApiUtils): OsLinuxProbeSnapshot {
    return OsLinuxProbeSnapshot(
        runtime = parseLinuxCommandSnapshot(execRuntimeCommand(linuxProbeCommand)),
        shizuku = parseLinuxCommandSnapshot(shizukuApiUtils.execCommand(linuxProbeCommand))
    )
}

internal suspend fun loadLinuxProbeSnapshotAsync(
    shizukuApiUtils: ShizukuApiUtils,
    dispatcher: CoroutineDispatcher = AppDispatchers.osOperations
): OsLinuxProbeSnapshot = coroutineScope {
    val runtime = async {
        execRuntimeCommandAsync(linuxProbeCommand)
    }
    val shizuku = async {
        shizukuApiUtils.execCommandCancellable(linuxProbeCommand)
    }
    withContext(dispatcher) {
        OsLinuxProbeSnapshot(
            runtime = parseLinuxCommandSnapshot(runtime.await()),
            shizuku = parseLinuxCommandSnapshot(shizuku.await())
        )
    }
}

internal fun OsLinuxProbeSnapshot.preferredValue(
    selector: OsLinuxCommandSnapshot.() -> String
): String {
    return shizuku.selector().ifBlank { runtime.selector() }
}

private val settingsSectionCommands = linkedMapOf(
    SectionKind.SYSTEM to "settings list system",
    SectionKind.SECURE to "settings list secure",
    SectionKind.GLOBAL to "settings list global"
)

private val settingsSectionIdMap = settingsSectionCommands.keys.associateBy { it.name.lowercase() }

private val settingsProbeCommand = settingsSectionCommands.entries.joinToString(separator = "; ") { (section, command) ->
    "printf '$SETTINGS_SECTION_MARKER=${section.name.lowercase()}\\n'; $command 2>/dev/null"
}

private val linuxProbeCommand = listOf(
    "printf 'uname-a=%s\\n' \"\$(uname -a 2>/dev/null | head -n 1)\"",
    "printf 'getenforce=%s\\n' \"\$(getenforce 2>/dev/null | head -n 1)\"",
    "printf 'proc.version=%s\\n' \"\$(cat /proc/version 2>/dev/null | head -n 1)\"",
    "printf 'toybox --version=%s\\n' \"\$(toybox --version 2>/dev/null | head -n 1)\""
).joinToString(separator = "; ")

private const val SETTINGS_SECTION_MARKER = "__keios_settings_section"
private const val SETTINGS_PROBE_TIMEOUT_MS = 5_000L
