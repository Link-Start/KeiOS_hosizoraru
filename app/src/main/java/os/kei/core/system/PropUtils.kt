package os.kei.core.system

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Method

private inline fun <T> safeOf(default: T, block: () -> T): T = runCatching(block).getOrDefault(default)

private const val PROP_SNAPSHOT_TTL_NANOS = 60_000_000_000L

private val systemPropertiesGetMethod: Method? by lazy(LazyThreadSafetyMode.PUBLICATION) {
    runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java, String::class.java)
    }.getOrNull()
}

private data class TimedSnapshot<T>(
    val value: T,
    val capturedAtNanos: Long
) {
    fun isFresh(nowNanos: Long): Boolean {
        val age = nowNanos - capturedAtNanos
        return age in 0..PROP_SNAPSHOT_TTL_NANOS
    }
}

private object PropSnapshotStore {
    @Volatile
    private var systemPropertiesSnapshot: TimedSnapshot<Map<String, String>>? = null

    @Volatile
    private var javaPropertiesSnapshot: TimedSnapshot<Map<String, String>>? = null

    private val systemLock = Any()
    private val javaLock = Any()

    fun invalidate() {
        synchronized(systemLock) {
            systemPropertiesSnapshot = null
        }
        synchronized(javaLock) {
            javaPropertiesSnapshot = null
        }
    }

    fun freshSystemProperties(): Map<String, String>? {
        val now = System.nanoTime()
        return systemPropertiesSnapshot?.takeIf { it.isFresh(now) }?.value
    }

    fun freshJavaProperties(): Map<String, String>? {
        val now = System.nanoTime()
        return javaPropertiesSnapshot?.takeIf { it.isFresh(now) }?.value
    }

    fun systemProperties(forceRefresh: Boolean): Map<String, String> {
        val now = System.nanoTime()
        if (!forceRefresh) {
            systemPropertiesSnapshot?.takeIf { it.isFresh(now) }?.let { return it.value }
        }
        return synchronized(systemLock) {
            val lockedNow = System.nanoTime()
            if (!forceRefresh) {
                systemPropertiesSnapshot?.takeIf { it.isFresh(lockedNow) }?.let { return@synchronized it.value }
            }
            val loaded = readAllSystemProperties()
            systemPropertiesSnapshot = TimedSnapshot(loaded, lockedNow)
            loaded
        }
    }

    fun javaProperties(forceRefresh: Boolean): Map<String, String> {
        val now = System.nanoTime()
        if (!forceRefresh) {
            javaPropertiesSnapshot?.takeIf { it.isFresh(now) }?.let { return it.value }
        }
        return synchronized(javaLock) {
            val lockedNow = System.nanoTime()
            if (!forceRefresh) {
                javaPropertiesSnapshot?.takeIf { it.isFresh(lockedNow) }?.let { return@synchronized it.value }
            }
            val loaded = readAllJavaProperties()
            javaPropertiesSnapshot = TimedSnapshot(loaded, lockedNow)
            loaded
        }
    }
}

/**
 * 获取系统 Prop 值
 * @param key Key
 * @param default 默认值
 * @return [String]
 */
fun findPropString(key: String, default: String = ""): String {
    val normalizedKey = key.trim()
    if (normalizedKey.isBlank()) return default
    PropSnapshotStore.freshSystemProperties()?.let { snapshot ->
        if (snapshot.containsKey(normalizedKey)) {
            return snapshot[normalizedKey] ?: default
        }
    }
    return safeOf(default) {
        systemPropertiesGetMethod?.invoke(null, normalizedKey, default) as? String ?: default
    }
}

fun findJavaPropString(key: String, default: String = ""): String = safeOf(default) {
    val normalizedKey = key.trim()
    if (normalizedKey.isBlank()) return default
    PropSnapshotStore.freshJavaProperties()?.let { snapshot ->
        if (snapshot.containsKey(normalizedKey)) {
            return snapshot[normalizedKey] ?: default
        }
    }
    System.getProperties().getProperty(normalizedKey) ?: default
}

fun getAllJavaPropertiesSnapshot(forceRefresh: Boolean = false): Map<String, String> {
    return PropSnapshotStore.javaProperties(forceRefresh)
}

suspend fun getAllJavaPropertiesSnapshotAsync(
    forceRefresh: Boolean = false,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): Map<String, String> {
    return withContext(dispatcher) {
        getAllJavaPropertiesSnapshot(forceRefresh)
    }
}

val getAllJavaPropString: Map<String, String>
    get() = getAllJavaPropertiesSnapshot()

fun getAllSystemPropertiesSnapshot(forceRefresh: Boolean = false): Map<String, String> {
    return PropSnapshotStore.systemProperties(forceRefresh)
}

suspend fun getAllSystemPropertiesSnapshotAsync(
    forceRefresh: Boolean = false,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): Map<String, String> {
    return withContext(dispatcher) {
        getAllSystemPropertiesSnapshot(forceRefresh)
    }
}

val getAllSystemProperties: Map<String, String>
    get() = getAllSystemPropertiesSnapshot()

fun invalidatePropertySnapshots() {
    PropSnapshotStore.invalidate()
}

private fun readAllJavaProperties(): Map<String, String> {
    return safeOf(emptyMap()) {
        val props = System.getProperties()
        props.stringPropertyNames().associateWith { props.getProperty(it).orEmpty() }
    }
}

private fun readAllSystemProperties(): Map<String, String> {
    return safeOf(emptyMap()) {
        val raw = RuntimeCommandExecutor.execute("getprop").stdout
        parseSystemPropertiesOutput(raw)
    }
}

internal fun parseSystemPropertiesOutput(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return buildMap {
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return@forEach
            val separatorIndex = trimmed.indexOf("]: [")
            if (separatorIndex <= 1) return@forEach
            val key = trimmed.substring(1, separatorIndex)
            val value = trimmed.substring(separatorIndex + 4, trimmed.length - 1)
            put(key, value)
        }
    }
}
