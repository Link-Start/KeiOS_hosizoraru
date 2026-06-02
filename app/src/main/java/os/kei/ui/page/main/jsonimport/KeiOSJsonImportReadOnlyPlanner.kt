package os.kei.ui.page.main.jsonimport

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.json.optArray
import os.kei.core.json.optInt
import os.kei.core.json.parseJsonObjectOrNull

internal class KeiOSJsonImportReadOnlyPlanner(
    private val defaultDispatcher: CoroutineDispatcher
) {
    suspend fun buildPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        return withContext(defaultDispatcher) {
            val root = file.raw.parseJsonObjectOrNull()
                ?: throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.ParseFailed)
            val count = when (header.kind) {
                KeiOSJsonImportKind.McpLogs -> root.optInt(
                    "logCount",
                    root.optArray("logs")?.size ?: 0
                )

                KeiOSJsonImportKind.OsInfoCard -> root.optInt(
                    "rowCount",
                    root.optArray("rows")?.size ?: 0
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
