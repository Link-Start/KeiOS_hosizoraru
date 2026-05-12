package os.kei.core.log

import android.util.Log
import os.kei.BuildConfig
import os.kei.KeiOSApp
import os.kei.core.prefs.UiPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val INTERNAL_TAG = "KeiLogger"
    private const val MAX_MESSAGE_LENGTH = 3200
    private const val MAX_STACK_LENGTH = 6000

    @Volatile
    private var logLevel: AppLogLevel = AppLogLevel.fromStorageId(BuildConfig.DEFAULT_LOG_LEVEL_ID)

    private val lineTimeFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        }
    }

    fun refreshLevelFromPrefs() {
        logLevel = UiPrefs.getLogLevel(
            defaultValue = AppLogLevel.fromStorageId(BuildConfig.DEFAULT_LOG_LEVEL_ID)
        )
    }

    fun refreshEnabledFromPrefs() {
        refreshLevelFromPrefs()
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

    fun i(tag: String, message: String) {
        if (!logLevel.allows(AppLogLevel.Info)) return
        Log.i(tag, message)
        append(level = AppLogLevel.Info, levelLabel = "I", tag = tag, message = message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(tag, message)
        } else {
            Log.w(tag, message, throwable)
        }
        if (!logLevel.allows(AppLogLevel.Warning)) return
        append(
            level = AppLogLevel.Warning,
            levelLabel = "W",
            tag = tag,
            message = message,
            throwable = throwable
        )
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e(tag, message)
        } else {
            Log.e(tag, message, throwable)
        }
        if (!logLevel.allows(AppLogLevel.Error)) return
        append(
            level = AppLogLevel.Error,
            levelLabel = "E",
            tag = tag,
            message = message,
            throwable = throwable
        )
    }

    private fun append(
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
            AppLogStore.appendLine(KeiOSApp.appContext, line)
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
