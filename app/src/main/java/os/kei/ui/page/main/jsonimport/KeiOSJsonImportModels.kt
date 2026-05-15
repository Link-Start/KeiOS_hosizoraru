package os.kei.ui.page.main.jsonimport

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import os.kei.R
import os.kei.core.io.DEFAULT_BOUNDED_TEXT_READ_MAX_BYTES

internal const val KEIOS_JSON_IMPORT_MAX_BYTES: Long = DEFAULT_BOUNDED_TEXT_READ_MAX_BYTES
internal const val KEIOS_JSON_IMPORT_SAMPLE_LIMIT = 6

internal enum class KeiOSJsonImportStage {
    Idle,
    Reading,
    Detecting,
    Parsing,
    PreviewReady,
    Importing,
    Done,
    Failed
}

internal enum class KeiOSJsonImportKind(@param:StringRes val titleRes: Int) {
    GitHubTracked(R.string.json_import_kind_github_tracked),
    OsActivityCards(R.string.json_import_kind_os_activity_cards),
    OsShellCards(R.string.json_import_kind_os_shell_cards),
    OsCardsBundle(R.string.json_import_kind_os_cards_bundle),
    BaCatalogFavorites(R.string.json_import_kind_ba_catalog_favorites),
    BaBgmFavorites(R.string.json_import_kind_ba_bgm_favorites),
    BaAllFavorites(R.string.json_import_kind_ba_all_favorites),
    McpLogs(R.string.json_import_kind_mcp_logs),
    OsInfoCard(R.string.json_import_kind_os_info_card),
    Unknown(R.string.json_import_kind_unknown)
}

internal data class KeiOSJsonImportIntentSource(
    val uri: Uri? = null,
    val inlineText: String? = null,
    val displayName: String = "",
    val mimeType: String = "",
    val declaredSizeBytes: Long = -1L
) {
    val isInlineText: Boolean
        get() = uri == null && inlineText != null
}

internal data class KeiOSJsonImportFile(
    val raw: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sourceDescription: String
)

internal data class KeiOSJsonImportHeader(
    val kind: KeiOSJsonImportKind,
    val marker: String = "",
    val version: Int = 0,
    val highVersion: Boolean = false,
    val readOnly: Boolean = false,
    val legacyFormat: Boolean = false
)

@Immutable
internal data class KeiOSJsonImportStat(
    val label: String,
    val value: String,
    val emphasized: Boolean = false
)

@Immutable
internal data class KeiOSJsonImportSample(
    val title: String,
    val subtitle: String = ""
)

@Immutable
internal data class KeiOSJsonImportPreview(
    val kind: KeiOSJsonImportKind,
    val marker: String,
    val version: Int,
    val highVersion: Boolean,
    val readOnly: Boolean,
    val legacyFormat: Boolean,
    val canImport: Boolean,
    val totalCount: Int,
    val validCount: Int,
    val newCount: Int = 0,
    val updatedCount: Int = 0,
    val unchangedCount: Int = 0,
    val duplicateCount: Int = 0,
    val invalidCount: Int = 0,
    val stats: List<KeiOSJsonImportStat> = emptyList(),
    val samples: List<KeiOSJsonImportSample> = emptyList()
)

internal sealed interface KeiOSJsonImportPlan {
    val preview: KeiOSJsonImportPreview
}

internal data class ImportableKeiOSJsonPlan(
    override val preview: KeiOSJsonImportPreview,
    val apply: suspend () -> KeiOSJsonImportApplyResult
) : KeiOSJsonImportPlan

internal data class ReadOnlyKeiOSJsonPlan(
    override val preview: KeiOSJsonImportPreview
) : KeiOSJsonImportPlan

@Immutable
internal data class KeiOSJsonImportApplyResult(
    val addedCount: Int = 0,
    val updatedCount: Int = 0,
    val unchangedCount: Int = 0,
    val invalidCount: Int = 0,
    val duplicateCount: Int = 0,
    val message: String = ""
)

@Immutable
internal data class KeiOSJsonImportUiState(
    val stage: KeiOSJsonImportStage = KeiOSJsonImportStage.Idle,
    val sourceName: String = "",
    val sourceDescription: String = "",
    val sourceSizeBytes: Long = -1L,
    val preview: KeiOSJsonImportPreview? = null,
    val applyResult: KeiOSJsonImportApplyResult? = null,
    val errorMessage: String = "",
    val busy: Boolean = false
) {
    val canConfirmImport: Boolean
        get() = stage == KeiOSJsonImportStage.PreviewReady &&
                preview?.canImport == true &&
                preview.readOnly == false &&
                !busy
}

internal class KeiOSJsonImportException(
    val reason: KeiOSJsonImportFailureReason,
    message: String = reason.name,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

internal enum class KeiOSJsonImportFailureReason {
    MissingSource,
    EmptyFile,
    FileTooLarge,
    UnsupportedFormat,
    ReadFailed,
    ParseFailed,
    ApplyFailed
}
