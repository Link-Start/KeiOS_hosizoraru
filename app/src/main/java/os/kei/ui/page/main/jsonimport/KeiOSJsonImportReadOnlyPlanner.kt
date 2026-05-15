package os.kei.ui.page.main.jsonimport

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject
import os.kei.R

internal class KeiOSJsonImportReadOnlyPlanner(
    private val defaultDispatcher: CoroutineDispatcher
) {
    suspend fun buildPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        return withContext(defaultDispatcher) {
            val root = JSONObject(file.raw)
            val count = when (header.kind) {
                KeiOSJsonImportKind.McpLogs -> root.optInt(
                    "logCount",
                    root.optJSONArray("logs")?.length() ?: 0
                )

                KeiOSJsonImportKind.OsInfoCard -> root.optInt(
                    "rowCount",
                    root.optJSONArray("rows")?.length() ?: 0
                )

                else -> 0
            }
            ReadOnlyKeiOSJsonPlan(
                KeiOSJsonImportPreview(
                    kind = header.kind,
                    marker = header.marker,
                    version = header.version,
                    highVersion = header.highVersion,
                    readOnly = true,
                    legacyFormat = header.legacyFormat,
                    canImport = false,
                    totalCount = count,
                    validCount = count,
                    stats = listOf(
                        KeiOSJsonImportStat(
                            context.getString(R.string.json_import_stat_total),
                            count.toString()
                        ),
                        KeiOSJsonImportStat(
                            context.getString(R.string.json_import_stat_mode),
                            context.getString(R.string.json_import_mode_read_only)
                        )
                    ),
                    samples = buildJsonImportReadOnlySamples(root, header.kind)
                )
            )
        }
    }
}
