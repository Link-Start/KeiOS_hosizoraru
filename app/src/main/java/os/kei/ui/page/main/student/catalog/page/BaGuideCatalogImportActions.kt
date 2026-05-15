package os.kei.ui.page.main.student.catalog.page

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.R
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
    fun requestImportPreview(
        uri: Uri?,
        kind: BaGuideCatalogImportKind,
        failureMessageRes: Int
    ) {
        if (uri == null) return
        pageScope.launch {
            try {
                val preview = buildBaGuideCatalogImportPreviewAsync(
                    context = context,
                    uri = uri,
                    kind = kind,
                    currentFavorites = filterSortState.favoriteCatalogEntries
                )
                if (preview.hasImportableData) {
                    onPreviewStateChange(preview)
                } else {
                    Toast.makeText(
                        context,
                        context.getString(failureMessageRes),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                Toast.makeText(
                    context,
                    context.getString(failureMessageRes),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importStudentFavoritesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        requestImportPreview(
            uri = uri,
            kind = BaGuideCatalogImportKind.Student,
            failureMessageRes = R.string.ba_catalog_transfer_import_failed
        )
    }
    val importBgmFavoritesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        requestImportPreview(
            uri = uri,
            kind = BaGuideCatalogImportKind.Bgm,
            failureMessageRes = R.string.ba_catalog_bgm_import_failed
        )
    }
    val importAllFavoritesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        requestImportPreview(
            uri = uri,
            kind = BaGuideCatalogImportKind.All,
            failureMessageRes = R.string.ba_catalog_transfer_import_failed
        )
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
        try {
            val importResult = applyBaGuideCatalogFavoritesImportAsync(preview)
            val studentFavorites = importResult.studentFavorites
            val bgmResult = importResult.bgmResult
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
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            Toast.makeText(
                context,
                context.getString(R.string.ba_catalog_transfer_import_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
