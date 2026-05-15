package os.kei.ui.page.main.jsonimport

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import os.kei.R
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.transfer.OsCardBundleImportPayload
import os.kei.ui.page.main.settings.support.formatBytes

internal data class KeiOSJsonImportOsDefaults(
    val system: OsGoogleSystemServiceConfig,
    val googleSettingsSample: OsGoogleSystemServiceConfig
)

internal fun buildJsonImportErrorMessage(context: Context, error: Throwable): String {
    val reason = (error as? KeiOSJsonImportException)?.reason
    return when (reason) {
        KeiOSJsonImportFailureReason.MissingSource ->
            context.getString(R.string.json_import_error_missing_source)

        KeiOSJsonImportFailureReason.EmptyFile ->
            context.getString(R.string.json_import_error_empty_file)

        KeiOSJsonImportFailureReason.FileTooLarge -> context.getString(
            R.string.json_import_error_file_too_large,
            formatBytes(KEIOS_JSON_IMPORT_MAX_BYTES)
        )

        KeiOSJsonImportFailureReason.UnsupportedFormat ->
            context.getString(R.string.json_import_error_unsupported_format)

        KeiOSJsonImportFailureReason.ReadFailed ->
            context.getString(R.string.json_import_error_read_failed)

        KeiOSJsonImportFailureReason.ParseFailed ->
            context.getString(R.string.json_import_error_parse_failed)

        KeiOSJsonImportFailureReason.ApplyFailed ->
            context.getString(R.string.json_import_error_apply_failed)

        null -> error.message ?: error.javaClass.simpleName
    }
}

internal fun buildJsonImportOsDefaults(context: Context): KeiOSJsonImportOsDefaults {
    val systemDefaults = OsGoogleSystemServiceConfig(
        title = context.getString(R.string.os_section_google_system_service_title),
        subtitle = context.getString(R.string.os_google_system_service_default_subtitle),
        appName = context.getString(R.string.os_google_system_service_default_app_name),
        intentFlags = context.getString(R.string.os_google_system_service_default_intent_flags)
    ).normalized()
    val googleSettingsSample = OsGoogleSystemServiceConfig(
        title = context.getString(R.string.os_activity_builtin_google_settings_title),
        subtitle = context.getString(R.string.os_activity_builtin_google_settings_subtitle),
        appName = context.getString(R.string.os_activity_builtin_google_settings_app_name),
        packageName = context.getString(R.string.os_activity_builtin_google_settings_package),
        className = context.getString(R.string.os_activity_builtin_google_settings_class),
        intentAction = Intent.ACTION_VIEW,
        intentFlags = context.getString(R.string.os_google_system_service_default_intent_flags)
    ).normalized(systemDefaults)
    return KeiOSJsonImportOsDefaults(systemDefaults, googleSettingsSample)
}

internal fun buildJsonImportStats(
    context: Context,
    totalCount: Int,
    validCount: Int,
    addedCount: Int,
    updatedCount: Int,
    unchangedCount: Int,
    invalidCount: Int,
    duplicateCount: Int
): List<KeiOSJsonImportStat> {
    return listOf(
        KeiOSJsonImportStat(
            context.getString(R.string.json_import_stat_total),
            totalCount.toString(),
            emphasized = true
        ),
        KeiOSJsonImportStat(
            context.getString(R.string.json_import_stat_valid),
            validCount.toString()
        ),
        KeiOSJsonImportStat(
            context.getString(R.string.json_import_stat_new),
            addedCount.toString(),
            emphasized = addedCount > 0
        ),
        KeiOSJsonImportStat(
            context.getString(R.string.json_import_stat_updated),
            updatedCount.toString(),
            emphasized = updatedCount > 0
        ),
        KeiOSJsonImportStat(
            context.getString(R.string.json_import_stat_unchanged),
            unchangedCount.toString()
        ),
        KeiOSJsonImportStat(
            context.getString(R.string.json_import_stat_invalid),
            invalidCount.toString()
        ),
        KeiOSJsonImportStat(
            context.getString(R.string.json_import_stat_duplicate),
            duplicateCount.toString()
        )
    )
}

internal fun buildJsonImportOsPreview(
    context: Context,
    header: KeiOSJsonImportHeader,
    totalCount: Int,
    validCount: Int,
    invalidCount: Int,
    duplicateCount: Int,
    addedCount: Int,
    updatedCount: Int,
    unchangedCount: Int,
    mergedCount: Int,
    samples: List<KeiOSJsonImportSample>
): KeiOSJsonImportPreview {
    return KeiOSJsonImportPreview(
        kind = header.kind,
        marker = header.marker,
        version = header.version,
        highVersion = header.highVersion,
        readOnly = false,
        legacyFormat = header.legacyFormat,
        canImport = validCount > 0,
        totalCount = totalCount,
        validCount = validCount,
        newCount = addedCount,
        updatedCount = updatedCount,
        unchangedCount = unchangedCount,
        duplicateCount = duplicateCount,
        invalidCount = invalidCount,
        stats = buildJsonImportStats(
            context,
            totalCount,
            validCount,
            addedCount,
            updatedCount,
            unchangedCount,
            invalidCount,
            duplicateCount
        ) + KeiOSJsonImportStat(
            context.getString(R.string.json_import_stat_merged),
            mergedCount.toString()
        ),
        samples = samples
    )
}

internal fun buildJsonImportBaPreview(
    context: Context,
    header: KeiOSJsonImportHeader,
    totalCount: Int,
    validCount: Int,
    addedCount: Int,
    updatedCount: Int,
    samples: List<KeiOSJsonImportSample>
): KeiOSJsonImportPreview {
    val unchangedCount = (validCount - addedCount - updatedCount).coerceAtLeast(0)
    return KeiOSJsonImportPreview(
        kind = header.kind,
        marker = header.marker,
        version = header.version,
        highVersion = header.highVersion,
        readOnly = false,
        legacyFormat = header.legacyFormat,
        canImport = validCount > 0,
        totalCount = totalCount,
        validCount = validCount,
        newCount = addedCount,
        updatedCount = updatedCount,
        unchangedCount = unchangedCount,
        stats = buildJsonImportStats(
            context,
            totalCount,
            validCount,
            addedCount,
            updatedCount,
            unchangedCount,
            invalidCount = 0,
            duplicateCount = 0
        ),
        samples = samples
    )
}

internal fun buildJsonImportOsBundleSamples(
    payload: OsCardBundleImportPayload
): List<KeiOSJsonImportSample> {
    val activitySamples = payload.activityPayload?.cards.orEmpty()
        .take(KEIOS_JSON_IMPORT_SAMPLE_LIMIT)
        .map { KeiOSJsonImportSample(it.config.title, it.config.packageName) }
    val remaining = (KEIOS_JSON_IMPORT_SAMPLE_LIMIT - activitySamples.size).coerceAtLeast(0)
    val shellSamples = payload.shellPayload?.cards.orEmpty()
        .take(remaining)
        .map { KeiOSJsonImportSample(it.title.ifBlank { it.command }, it.command) }
    return activitySamples + shellSamples
}

internal fun buildJsonImportReadOnlySamples(
    root: JSONObject,
    kind: KeiOSJsonImportKind
): List<KeiOSJsonImportSample> {
    val array = when (kind) {
        KeiOSJsonImportKind.McpLogs -> root.optJSONArray("logs")
        KeiOSJsonImportKind.OsInfoCard -> root.optJSONArray("rows")
        else -> null
    } ?: return emptyList()
    return buildList {
        repeat(minOf(array.length(), KEIOS_JSON_IMPORT_SAMPLE_LIMIT)) { index ->
            val item = array.optJSONObject(index) ?: return@repeat
            add(
                when (kind) {
                    KeiOSJsonImportKind.McpLogs -> KeiOSJsonImportSample(
                        title = item.optString("message"),
                        subtitle = listOf(item.optString("time"), item.optString("level"))
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                    )

                    else -> KeiOSJsonImportSample(
                        title = item.optString("key"),
                        subtitle = item.optString("value")
                    )
                }
            )
        }
    }
}

internal fun buildJsonImportBgmSamples(raw: String): List<KeiOSJsonImportSample> {
    return runCatching {
        val trimmed = raw.trim()
        val array = if (trimmed.startsWith("[")) {
            org.json.JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            root.optJSONArray("favorites")
                ?: root.optJSONArray("bgmFavorites")
                ?: org.json.JSONArray()
        }
        buildList {
            repeat(minOf(array.length(), KEIOS_JSON_IMPORT_SAMPLE_LIMIT)) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
                add(
                    KeiOSJsonImportSample(
                        title = item.optString("title").ifBlank { item.optString("audioUrl") },
                        subtitle = item.optString("studentTitle")
                            .ifBlank { item.optString("audioUrl") }
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal const val JSON_IMPORT_YIELD_EVERY_ITEMS = 64
