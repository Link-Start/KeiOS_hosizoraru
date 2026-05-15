package os.kei.ui.page.main.os

import android.net.Uri
import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv

internal object OsInfoCache {
    private const val KV_ID = "os_info_cache"
    private const val LEGACY_KV_ID = "system_info_cache"

    private const val KEY_OS_SYSTEM = "section_os_system_table"
    private const val KEY_OS_SECURE = "section_os_secure_table"
    private const val KEY_OS_GLOBAL = "section_os_global_table"
    private const val KEY_OS_ANDROID = "section_os_android_properties"
    private const val KEY_OS_JAVA = "section_os_java_properties"
    private const val KEY_OS_LINUX = "section_os_linux_environment"

    private const val LEGACY_KEY_SYSTEM = "section_system_table"
    private const val LEGACY_KEY_SECURE = "section_secure_table"
    private const val LEGACY_KEY_GLOBAL = "section_global_table"
    private const val LEGACY_KEY_ANDROID = "section_android_properties"
    private const val LEGACY_KEY_JAVA = "section_java_properties"
    private const val LEGACY_KEY_LINUX = "section_linux_environment"
    private val store: MMKV by lazy { KeiMmkv.byId(KV_ID) }
    private val legacyStore: MMKV by lazy { KeiMmkv.byId(LEGACY_KV_ID) }

    private fun encodeRows(rows: List<InfoRow>): String {
        return rows.joinToString("\n") { row ->
            "${Uri.encode(row.key)}\t${Uri.encode(row.value)}"
        }
    }

    private fun decodeRows(raw: String?): List<InfoRow> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val index = line.indexOf('\t')
            if (index <= 0) return@mapNotNull null
            val key = Uri.decode(line.substring(0, index))
            val value = Uri.decode(line.substring(index + 1))
            InfoRow(key, value)
        }.toList()
    }

    private fun readRaw(newKv: MMKV, legacyKv: MMKV, newKey: String, legacyKey: String): String? {
        val newRaw = newKv.decodeString(newKey)
        if (!newRaw.isNullOrBlank()) return newRaw
        return legacyKv.decodeString(legacyKey)
    }

    private fun hasAllKeys(kv: MMKV, keys: List<String>): Boolean {
        return keys.all { kv.containsKey(it) }
    }

    fun read(visibleSections: Set<SectionKind> = SectionKind.entries.toSet()): CachedSections {
        val kv = store
        val legacyKv = legacyStore
        return CachedSections(
            system = if (visibleSections.contains(SectionKind.SYSTEM)) decodeRows(
                readRaw(
                    kv,
                    legacyKv,
                    KEY_OS_SYSTEM,
                    LEGACY_KEY_SYSTEM
                )
            ) else emptyList(),
            secure = if (visibleSections.contains(SectionKind.SECURE)) decodeRows(
                readRaw(
                    kv,
                    legacyKv,
                    KEY_OS_SECURE,
                    LEGACY_KEY_SECURE
                )
            ) else emptyList(),
            global = if (visibleSections.contains(SectionKind.GLOBAL)) decodeRows(
                readRaw(
                    kv,
                    legacyKv,
                    KEY_OS_GLOBAL,
                    LEGACY_KEY_GLOBAL
                )
            ) else emptyList(),
            android = if (visibleSections.contains(SectionKind.ANDROID)) decodeRows(
                readRaw(
                    kv,
                    legacyKv,
                    KEY_OS_ANDROID,
                    LEGACY_KEY_ANDROID
                )
            ) else emptyList(),
            java = if (visibleSections.contains(SectionKind.JAVA)) decodeRows(
                readRaw(
                    kv,
                    legacyKv,
                    KEY_OS_JAVA,
                    LEGACY_KEY_JAVA
                )
            ) else emptyList(),
            linux = if (visibleSections.contains(SectionKind.LINUX)) decodeRows(
                readRaw(
                    kv,
                    legacyKv,
                    KEY_OS_LINUX,
                    LEGACY_KEY_LINUX
                )
            ) else emptyList()
        )
    }

    fun write(section: SectionKind, rows: List<InfoRow>) {
        val kv = store
        when (section) {
            SectionKind.SYSTEM -> kv.encode(KEY_OS_SYSTEM, encodeRows(rows))
            SectionKind.SECURE -> kv.encode(KEY_OS_SECURE, encodeRows(rows))
            SectionKind.GLOBAL -> kv.encode(KEY_OS_GLOBAL, encodeRows(rows))
            SectionKind.ANDROID -> kv.encode(KEY_OS_ANDROID, encodeRows(rows))
            SectionKind.JAVA -> kv.encode(KEY_OS_JAVA, encodeRows(rows))
            SectionKind.LINUX -> kv.encode(KEY_OS_LINUX, encodeRows(rows))
        }
    }

    fun clear(section: SectionKind) {
        val kv = store
        val legacyKv = legacyStore
        when (section) {
            SectionKind.SYSTEM -> {
                kv.removeValueForKey(KEY_OS_SYSTEM)
                legacyKv.removeValueForKey(LEGACY_KEY_SYSTEM)
            }
            SectionKind.SECURE -> {
                kv.removeValueForKey(KEY_OS_SECURE)
                legacyKv.removeValueForKey(LEGACY_KEY_SECURE)
            }
            SectionKind.GLOBAL -> {
                kv.removeValueForKey(KEY_OS_GLOBAL)
                legacyKv.removeValueForKey(LEGACY_KEY_GLOBAL)
            }
            SectionKind.ANDROID -> {
                kv.removeValueForKey(KEY_OS_ANDROID)
                legacyKv.removeValueForKey(LEGACY_KEY_ANDROID)
            }
            SectionKind.JAVA -> {
                kv.removeValueForKey(KEY_OS_JAVA)
                legacyKv.removeValueForKey(LEGACY_KEY_JAVA)
            }
            SectionKind.LINUX -> {
                kv.removeValueForKey(KEY_OS_LINUX)
                legacyKv.removeValueForKey(LEGACY_KEY_LINUX)
            }
        }
    }

    fun readSnapshot(visibleSections: Set<SectionKind> = SectionKind.entries.toSet()): CachedSectionsSnapshot {
        if (visibleSections.isEmpty()) return CachedSectionsSnapshot()
        val cached = read(visibleSections)
        val hasPersistedCache = visibleSections.any { section ->
            when (section) {
                SectionKind.SYSTEM -> cached.system.isNotEmpty()
                SectionKind.SECURE -> cached.secure.isNotEmpty()
                SectionKind.GLOBAL -> cached.global.isNotEmpty()
                SectionKind.ANDROID -> cached.android.isNotEmpty()
                SectionKind.JAVA -> cached.java.isNotEmpty()
                SectionKind.LINUX -> cached.linux.isNotEmpty()
            }
        }
        return CachedSectionsSnapshot(
            cached = cached,
            hasPersistedCache = hasPersistedCache
        )
    }

    fun hasPersistedCache(visibleSections: Set<SectionKind>): Boolean {
        return readSnapshot(visibleSections).hasPersistedCache
    }

    fun clearAll() {
        SectionKind.entries.forEach(::clear)
        store.trim()
        legacyStore.trim()
    }

    fun storageFootprintBytes(): Long = store.totalSize() + legacyStore.totalSize()

    fun actualDataBytes(): Long = store.actualSize() + legacyStore.actualSize()

    fun cacheBytesEstimated(): Long {
        val snapshot = read()
        return snapshot.system.sumOf { (it.key.length + it.value.length).toLong() * 2 + 8L } +
            snapshot.secure.sumOf { (it.key.length + it.value.length).toLong() * 2 + 8L } +
            snapshot.global.sumOf { (it.key.length + it.value.length).toLong() * 2 + 8L } +
            snapshot.android.sumOf { (it.key.length + it.value.length).toLong() * 2 + 8L } +
            snapshot.java.sumOf { (it.key.length + it.value.length).toLong() * 2 + 8L } +
            snapshot.linux.sumOf { (it.key.length + it.value.length).toLong() * 2 + 8L }
    }

    fun cachedSectionCount(visibleCards: Set<OsSectionCard>): Int {
        val visibleSections = buildSet {
            if (visibleCards.contains(OsSectionCard.SYSTEM)) add(SectionKind.SYSTEM)
            if (visibleCards.contains(OsSectionCard.SECURE)) add(SectionKind.SECURE)
            if (visibleCards.contains(OsSectionCard.GLOBAL)) add(SectionKind.GLOBAL)
            if (visibleCards.contains(OsSectionCard.ANDROID)) add(SectionKind.ANDROID)
            if (visibleCards.contains(OsSectionCard.JAVA)) add(SectionKind.JAVA)
            if (visibleCards.contains(OsSectionCard.LINUX)) add(SectionKind.LINUX)
        }
        val cached = read(visibleSections)
        var count = 0
        if (cached.system.isNotEmpty()) count++
        if (cached.secure.isNotEmpty()) count++
        if (cached.global.isNotEmpty()) count++
        if (cached.android.isNotEmpty()) count++
        if (cached.java.isNotEmpty()) count++
        if (cached.linux.isNotEmpty()) count++
        return count
    }

    fun hasPersistedCache(): Boolean {
        val kv = store
        val legacyKv = legacyStore
        val hasNew = hasAllKeys(
            kv,
            listOf(KEY_OS_SYSTEM, KEY_OS_SECURE, KEY_OS_GLOBAL, KEY_OS_ANDROID, KEY_OS_JAVA, KEY_OS_LINUX)
        )
        val hasLegacy = hasAllKeys(
            legacyKv,
            listOf(
                LEGACY_KEY_SYSTEM,
                LEGACY_KEY_SECURE,
                LEGACY_KEY_GLOBAL,
                LEGACY_KEY_ANDROID,
                LEGACY_KEY_JAVA,
                LEGACY_KEY_LINUX
            )
        )
        return hasNew || hasLegacy
    }
}
