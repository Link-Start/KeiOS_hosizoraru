package os.kei.core.telemetry

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tencent.mmkv.MMKV
import org.json.JSONArray
import org.json.JSONObject
import os.kei.BuildConfig
import os.kei.core.prefs.UiPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FirebaseTelemetryRecordKind {
    BasicStats,
    ErrorLog
}

data class FirebaseTelemetryRecord(
    val timestampMs: Long,
    val kind: FirebaseTelemetryRecordKind,
    val title: String,
    val detail: String,
    val fields: List<String>,
    val appVersion: String = "",
    val androidSdk: Int = 0,
    val manufacturer: String = "",
    val model: String = ""
)

data class FirebaseTelemetrySnapshot(
    val basicStatsEnabled: Boolean,
    val errorLogsEnabled: Boolean,
    val recentRecords: List<FirebaseTelemetryRecord>
)

object FirebaseTelemetry {
    const val PREVIOUS_CRASH_DETAIL = "__previous_fatal_crash__"

    private const val TAG = "FirebaseTelemetry"
    private const val KV_ID = "firebase_telemetry"
    private const val KEY_RECENT_RECORDS = "recent_records"
    private const val MAX_RECENT_RECORDS = 12
    private const val MAX_DETAIL_LENGTH = 420
    private val store: MMKV by lazy { MMKV.mmkvWithID(KV_ID) }

    @Volatile
    private var previousCrashChecked = false
    private val timeFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        }
    }

    fun applyCollectionPrefs(context: Context) {
        val appContext = context.applicationContext
        val basicEnabled = UiPrefs.isFirebaseBasicStatsEnabled()
        val errorEnabled = UiPrefs.isFirebaseErrorLogsEnabled()
        setAnalyticsCollection(appContext, basicEnabled)
        setCrashlyticsCollection(false)
        updateCrashlyticsKeys()
        if (errorEnabled) {
            recordPreviousCrashIfEnabled()
            sendUnsentErrorReports()
        }
    }

    fun recordAppOpenIfEnabled(context: Context) {
        if (!UiPrefs.isFirebaseBasicStatsEnabled()) return
        recordBasicStats(context.applicationContext)
    }

    fun recordPreviousCrashIfEnabled() {
        if (previousCrashChecked || !UiPrefs.isFirebaseErrorLogsEnabled()) return
        previousCrashChecked = true
        val crashlytics = FirebaseCrashlytics.getInstance()
        val crashed = runCatching {
            crashlytics.didCrashOnPreviousExecution()
        }.getOrDefault(false)
        if (!crashed) return
        addRecentRecord(
            kind = FirebaseTelemetryRecordKind.ErrorLog,
            title = FirebaseTelemetryRecordKind.ErrorLog.name,
            detail = PREVIOUS_CRASH_DETAIL,
            fields = emptyList()
        )
    }

    private fun recordBasicStats(appContext: Context) {
        val params = Bundle().apply {
            putString("app_version", BuildConfig.VERSION_NAME)
            putLong("version_code", BuildConfig.VERSION_CODE.toLong())
            putLong("android_sdk", Build.VERSION.SDK_INT.toLong())
            putString("manufacturer", Build.MANUFACTURER.orEmpty().take(36))
            putString("model", Build.MODEL.orEmpty().take(36))
            putString("build_type", BuildConfig.BUILD_TYPE)
        }
        runCatching {
            FirebaseAnalytics.getInstance(appContext).logEvent("keios_app_open", params)
        }.onFailure { error ->
            Log.w(TAG, "log app open failed: ${error.message ?: error.javaClass.simpleName}")
        }
        addRecentRecord(
            kind = FirebaseTelemetryRecordKind.BasicStats,
            title = FirebaseTelemetryRecordKind.BasicStats.name,
            detail = "",
            fields = emptyList()
        )
    }

    fun setBasicStatsEnabled(
        context: Context,
        enabled: Boolean
    ) {
        val appContext = context.applicationContext
        setAnalyticsCollection(appContext, enabled)
        if (enabled) {
            recordBasicStats(appContext)
        } else {
            resetAnalyticsData(appContext)
        }
    }

    fun setErrorLogsEnabled(enabled: Boolean) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        if (enabled) {
            // Old reports captured while the user opted out stay local-only.
            runCatching { crashlytics.deleteUnsentReports() }
            updateCrashlyticsKeys()
        } else {
            runCatching { crashlytics.deleteUnsentReports() }
        }
        setCrashlyticsCollection(false)
    }

    fun recordErrorLog(
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        if (!UiPrefs.isFirebaseErrorLogsEnabled()) return
        val crashlytics = FirebaseCrashlytics.getInstance()
        val compactMessage = compact(message)
        val safeTag = tag.ifBlank { "KeiOS" }.take(48)
        val exception = throwable ?: IllegalStateException("KeiOS error log: $safeTag")
        runCatching {
            updateCrashlyticsKeys()
            crashlytics.setCustomKey("keios_log_tag", safeTag)
            crashlytics.log("$safeTag: $compactMessage")
            crashlytics.recordException(exception)
            crashlytics.sendUnsentReports()
        }.onFailure { error ->
            Log.w(TAG, "record error failed: ${error.message ?: error.javaClass.simpleName}")
        }
        addRecentRecord(
            kind = FirebaseTelemetryRecordKind.ErrorLog,
            title = FirebaseTelemetryRecordKind.ErrorLog.name,
            detail = buildString {
                append(safeTag)
                append(" · ")
                append(exception.javaClass.simpleName)
                exception.message?.takeIf { it.isNotBlank() }?.let {
                    append(" · ")
                    append(compact(it, 160))
                }
            },
            fields = emptyList()
        )
    }

    fun sendUnsentErrorReports() {
        if (!UiPrefs.isFirebaseErrorLogsEnabled()) return
        runCatching {
            FirebaseCrashlytics.getInstance().sendUnsentReports()
        }
    }

    fun deleteUnsentErrorReports() {
        runCatching {
            FirebaseCrashlytics.getInstance().deleteUnsentReports()
        }
    }

    fun loadSnapshot(): FirebaseTelemetrySnapshot {
        return FirebaseTelemetrySnapshot(
            basicStatsEnabled = UiPrefs.isFirebaseBasicStatsEnabled(),
            errorLogsEnabled = UiPrefs.isFirebaseErrorLogsEnabled(),
            recentRecords = loadRecentRecords()
        )
    }

    fun clearRecentRecords() {
        store.removeValueForKey(KEY_RECENT_RECORDS)
    }

    fun formatRecordTime(timestampMs: Long): String {
        if (timestampMs <= 0L) return ""
        return runCatching {
            timeFormatter.get()?.format(Date(timestampMs)).orEmpty()
        }.getOrDefault("")
    }

    private fun setAnalyticsCollection(
        context: Context,
        enabled: Boolean
    ) {
        runCatching {
            val analytics = FirebaseAnalytics.getInstance(context)
            analytics.setAnalyticsCollectionEnabled(enabled)
            if (enabled) {
                analytics.setUserProperty("keios_build_type", BuildConfig.BUILD_TYPE)
                analytics.setUserProperty("keios_app_version", BuildConfig.VERSION_NAME.take(36))
            }
        }.onFailure { error ->
            Log.w(TAG, "set analytics failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun resetAnalyticsData(context: Context) {
        runCatching {
            FirebaseAnalytics.getInstance(context).resetAnalyticsData()
        }.onFailure { error ->
            Log.w(TAG, "reset analytics failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun setCrashlyticsCollection(enabled: Boolean) {
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        }.onFailure { error ->
            Log.w(TAG, "set crashlytics failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun updateCrashlyticsKeys() {
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("keios_app_version", BuildConfig.VERSION_NAME)
                setCustomKey("keios_version_code", BuildConfig.VERSION_CODE)
                setCustomKey("keios_build_type", BuildConfig.BUILD_TYPE)
                setCustomKey("android_sdk", Build.VERSION.SDK_INT)
                setCustomKey("manufacturer", Build.MANUFACTURER.orEmpty())
                setCustomKey("model", Build.MODEL.orEmpty())
            }
        }
    }

    private fun addRecentRecord(
        kind: FirebaseTelemetryRecordKind,
        title: String,
        detail: String,
        fields: List<String>
    ) {
        val next = listOf(
            FirebaseTelemetryRecord(
                timestampMs = System.currentTimeMillis(),
                kind = kind,
                title = title,
                detail = compact(detail),
                fields = fields,
                appVersion = BuildConfig.VERSION_NAME,
                androidSdk = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty()
            )
        ) + loadRecentRecords()
        saveRecentRecords(next.take(MAX_RECENT_RECORDS))
    }

    private fun loadRecentRecords(): List<FirebaseTelemetryRecord> {
        val raw = store.decodeString(KEY_RECENT_RECORDS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val kind = runCatching {
                        FirebaseTelemetryRecordKind.valueOf(item.optString("kind"))
                    }.getOrDefault(FirebaseTelemetryRecordKind.BasicStats)
                    val fieldsArray = item.optJSONArray("fields") ?: JSONArray()
                    add(
                        FirebaseTelemetryRecord(
                            timestampMs = item.optLong("timestampMs"),
                            kind = kind,
                            title = item.optString("title"),
                            detail = item.optString("detail"),
                            fields = buildList {
                                for (fieldIndex in 0 until fieldsArray.length()) {
                                    add(fieldsArray.optString(fieldIndex))
                                }
                            },
                            appVersion = item.optString("appVersion"),
                            androidSdk = item.optInt("androidSdk"),
                            manufacturer = item.optString("manufacturer"),
                            model = item.optString("model")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveRecentRecords(records: List<FirebaseTelemetryRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("timestampMs", record.timestampMs)
                    .put("kind", record.kind.name)
                    .put("title", record.title)
                    .put("detail", record.detail)
                    .put("fields", JSONArray(record.fields))
                    .put("appVersion", record.appVersion)
                    .put("androidSdk", record.androidSdk)
                    .put("manufacturer", record.manufacturer)
                    .put("model", record.model)
            )
        }
        store.encode(KEY_RECENT_RECORDS, array.toString())
    }

    private fun compact(
        raw: String,
        maxLength: Int = MAX_DETAIL_LENGTH
    ): String {
        val normalized = raw
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
            .trim()
        if (normalized.length <= maxLength) return normalized
        return normalized.take(maxLength) + "..."
    }
}
