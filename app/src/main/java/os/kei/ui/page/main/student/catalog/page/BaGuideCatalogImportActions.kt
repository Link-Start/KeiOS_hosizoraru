package os.kei.ui.page.main.student.catalog.page

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.ui.page.main.student.GuideBgmFavoriteStore
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogFilterSortState

internal data class BaGuideCatalogImportActions(
    val importStudentFavoritesLauncher: ActivityResultLauncher<Array<String>>,
    val importBgmFavoritesLauncher: ActivityResultLauncher<Array<String>>,
    val importAllFavoritesLauncher: ActivityResultLauncher<Array<String>>,
    val confirmFavoritesImport: (BaGuideCatalogImportPreviewState) -> Unit
)

@Composable
internal fun rememberBaGuideCatalogImportActions(
    context: Context,
    pageScope: CoroutineScope,
    filterSortState: BaGuideCatalogFilterSortState,
    onPreviewStateChange: (BaGuideCatalogImportPreviewState?) -> Unit
): BaGuideCatalogImportActions {
    val importStudentFavoritesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pageScope.launch {
            val preview = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                    BaGuideCatalogImportPreviewState(
                        kind = BaGuideCatalogImportKind.Student,
                        raw = raw,
                        studentPreview = previewCatalogFavoritesImport(
                            raw = raw,
                            currentFavorites = filterSortState.favoriteCatalogEntries
                        ),
                        bgmPreview = GuideBgmFavoriteStore.previewFavoritesJsonImport("")
                    )
                }.getOrNull()
            }
            if (preview == null || !preview.hasImportableData) {
                Toast.makeText(
                    context,
                    context.getString(R.string.ba_catalog_transfer_import_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                onPreviewStateChange(preview)
            }
        }
    }
    val importBgmFavoritesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pageScope.launch {
            val preview = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                    BaGuideCatalogImportPreviewState(
                        kind = BaGuideCatalogImportKind.Bgm,
                        raw = raw,
                        studentPreview = CatalogFavoritesImportPreview(0, 0, 0),
                        bgmPreview = GuideBgmFavoriteStore.previewFavoritesJsonImport(raw)
                    )
                }.getOrNull()
            }
            if (preview == null || !preview.hasImportableData) {
                Toast.makeText(
                    context,
                    context.getString(R.string.ba_catalog_bgm_import_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                onPreviewStateChange(preview)
            }
        }
    }
    val importAllFavoritesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pageScope.launch {
            val preview = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                    BaGuideCatalogImportPreviewState(
                        kind = BaGuideCatalogImportKind.All,
                        raw = raw,
                        studentPreview = previewCatalogFavoritesImport(
                            raw = raw,
                            currentFavorites = filterSortState.favoriteCatalogEntries
                        ),
                        bgmPreview = GuideBgmFavoriteStore.previewFavoritesJsonImport(raw)
                    )
                }.getOrNull()
            }
            if (preview == null || !preview.hasImportableData) {
                Toast.makeText(
                    context,
                    context.getString(R.string.ba_catalog_transfer_import_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                onPreviewStateChange(preview)
            }
        }
    }
    return BaGuideCatalogImportActions(
        importStudentFavoritesLauncher = importStudentFavoritesLauncher,
        importBgmFavoritesLauncher = importBgmFavoritesLauncher,
        importAllFavoritesLauncher = importAllFavoritesLauncher,
        confirmFavoritesImport = { preview ->
            confirmFavoritesImport(
                context = context,
                pageScope = pageScope,
                filterSortState = filterSortState,
                preview = preview,
                onPreviewStateChange = onPreviewStateChange
            )
        }
    )
}

private fun confirmFavoritesImport(
    context: Context,
    pageScope: CoroutineScope,
    filterSortState: BaGuideCatalogFilterSortState,
    preview: BaGuideCatalogImportPreviewState,
    onPreviewStateChange: (BaGuideCatalogImportPreviewState?) -> Unit
) {
    pageScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val studentFavorites = if (
                    preview.kind == BaGuideCatalogImportKind.All ||
                    preview.kind == BaGuideCatalogImportKind.Student
                ) {
                    parseCatalogFavoritesExport(preview.raw)
                } else {
                    emptyMap()
                }
                val bgmResult = if (
                    preview.kind == BaGuideCatalogImportKind.All ||
                    preview.kind == BaGuideCatalogImportKind.Bgm
                ) {
                    GuideBgmFavoriteStore.importFavoritesJsonMerged(preview.raw)
                } else {
                    null
                }
                studentFavorites to bgmResult
            }
        }
        result
            .onSuccess { (studentFavorites, bgmResult) ->
                if (studentFavorites.isNotEmpty()) {
                    filterSortState.replaceFavorites(
                        filterSortState.favoriteCatalogEntries + studentFavorites
                    )
                }
                val message = when (preview.kind) {
                    BaGuideCatalogImportKind.All -> context.getString(
                        R.string.ba_catalog_transfer_all_import_success,
                        studentFavorites.size,
                        bgmResult?.addedCount ?: 0,
                        bgmResult?.updatedCount ?: 0
                    )

                    BaGuideCatalogImportKind.Student -> context.getString(
                        R.string.ba_catalog_transfer_student_import_success,
                        studentFavorites.size
                    )

                    BaGuideCatalogImportKind.Bgm -> context.getString(
                        R.string.ba_catalog_bgm_import_success,
                        bgmResult?.addedCount ?: 0,
                        bgmResult?.updatedCount ?: 0
                    )
                }
                onPreviewStateChange(null)
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.ba_catalog_transfer_import_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}
