package os.kei.core.log

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application-wide structured logger with level gating, lazy message overloads, and file-backed
 * log storage.
 *
 * This class has zero coupling to any app-level type (BuildConfig, Application subclass, prefs).
 * The app module calls [initialize] once during Application.onCreate to inject context and default
 * level. Prefs-based level refresh is handled by the app module calling [setLogLevel] directly.
 */
object AppLogger {
    private const val INTERNAL_TAG = "KeiLogger"
    private const val MAX_MESSAGE_LENGTH = 3200
    private const val MAX_STACK_LENGTH = 6000

    @Volatile
    @PublishedApi
    @JvmField
    internal var logLevel: AppLogLevel = AppLogLevel.Off

    /**
     * Context provider for [AppLogStore]. Must be set via [initialize] before any log calls that
     * write to disk. Defaults to a throwing stub to surface misconfiguration early.
     */
    @Volatile
    private var contextProvider: () -> Context = {
        error("AppLogger.initialize() was not called before logging")
    }

    private val lineTimeFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        }
    }

    /**
     * One-time initialization. Call from Application.onCreate.
     *
     * @param context Application context for file-backed log storage.
     * @param defaultLevelId Storage ID of the initial log level (e.g. "off", "debug").
     */
    fun initialize(context: Context, defaultLevelId: String = "off") {
        contextProvider = { context.applicationContext }
        logLevel = AppLogLevel.fromStorageId(defaultLevelId)
    }

    fun setLogLevel(level: AppLogLevel) {
        logLevel = level
    }

    fun setDebugEnabled(enabled: Boolean) {
        logLevel = if (enabled) AppLogLevel.Debug else AppLogLevel.Off
    }

    fun currentLevel(): AppLogLevel = logLevel

    fun isDebugEnabled(): Boolean = logLevel == AppLogLevel.Debug

    fun d(tag: String, message: String) {
        if (!logLevel.allows(AppLogLevel.Debug)) return
        Log.d(tag, message)
        append(level = AppLogLevel.Debug, levelLabel = "D", tag = tag, message = message)
    }

    /**
     * Lazy variant: the [message] lambda is only invoked when debug logging is enabled, avoiding
     * string concatenation and interpolation overhead on the hot path when logging is off.
     */
    inline fun d(tag: String, message: () -> String) {
        if (!logLevel.allows(AppLogLevel.Debug)) return
        val msg = message()
        Log.d(tag, msg)
        append(level = AppLogLevel.Debug, levelLabel = "D", tag = tag, message = msg)
    }

    fun i(tag: String, message: String) {
        if (!logLevel.allows(AppLogLevel.Info)) return
        Log.i(tag, message)
        append(level = AppLogLevel.Info, levelLabel = "I", tag = tag, message = message)
    }

    /**
     * Lazy variant: the [message] lambda is only invoked when info logging is enabled.
     */
    inline fun i(tag: String, message: () -> String) {
        if (!logLevel.allows(AppLogLevel.Info)) return
        val msg = message()
        Log.i(tag, msg)
        append(level = AppLogLevel.Info, levelLabel = "I", tag = tag, message = msg)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (!logLevel.allows(AppLogLevel.Warning)) return
        if (throwable == null) {
            Log.w(tag, message)
        } else {
            Log.w(tag, message, throwable)
        }
        append(
            level = AppLogLevel.Warning,
            levelLabel = "W",
            tag = tag,
            message = message,
            throwable = throwable
        )
    }

    /**
     * Lazy variant: the [message] lambda is only invoked when warning logging is enabled.
     */
    inline fun w(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (!logLevel.allows(AppLogLevel.Warning)) return
        val msg = message()
        if (throwable == null) {
            Log.w(tag, msg)
        } else {
            Log.w(tag, msg, throwable)
        }
        append(
            level = AppLogLevel.Warning,
            levelLabel = "W",
            tag = tag,
            message = msg,
            throwable = throwable
        )
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (!logLevel.allows(AppLogLevel.Error)) return
        if (throwable == null) {
            Log.e(tag, message)
        } else {
            Log.e(tag, message, throwable)
        }
        append(
            level = AppLogLevel.Error,
            levelLabel = "E",
            tag = tag,
            message = message,
            throwable = throwable
        )
    }

    /**
     * Lazy variant: the [message] lambda is only invoked when error logging is enabled.
     */
    inline fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (!logLevel.allows(AppLogLevel.Error)) return
        val msg = message()
        if (throwable == null) {
            Log.e(tag, msg)
        } else {
            Log.e(tag, msg, throwable)
        }
        append(
            level = AppLogLevel.Error,
            levelLabel = "E",
            tag = tag,
            message = msg,
            throwable = throwable
        )
    }

    @PublishedApi
    internal fun append(
        level: AppLogLevel,
        levelLabel: String,
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) {
        if (!logLevel.allows(level)) return
        val timestamp = lineTimeFormatter.get()?.format(Date()).orEmpty()
        val compactMessage = compact(message, MAX_MESSAGE_LENGTH)
        val throwablePart = throwable?.let {
            val stack = compact(Log.getStackTraceString(it), MAX_STACK_LENGTH)
            " | stack=$stack"
        }.orEmpty()
        val line = "$timestamp | $levelLabel | $tag | $compactMessage$throwablePart"
        runCatching {
            AppLogStore.appendLine(contextProvider(), line)
        }.onFailure {
            Log.w(INTERNAL_TAG, "append failed: ${it.message ?: it.javaClass.simpleName}")
        }
    }

    private fun compact(raw: String, maxLength: Int): String {
        val normalized = raw
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
            .trim()
        if (normalized.length <= maxLength) return normalized
        return normalized.take(maxLength) + "…"
    }
}
