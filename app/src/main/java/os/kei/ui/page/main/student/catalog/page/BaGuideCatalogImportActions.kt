package os.kei.ui.page.main.student.catalog.page

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

internal data class BaGuideCatalogImportActions(
    val importStudentFavoritesLauncher: ActivityResultLauncher<Array<String>>,
    val importBgmFavoritesLauncher: ActivityResultLauncher<Array<String>>,
    val importAllFavoritesLauncher: ActivityResultLauncher<Array<String>>,
    val confirmFavoritesImport: (BaGuideCatalogImportPreviewState) -> Unit,
)

@Composable
internal fun rememberBaGuideCatalogImportActions(
    onRequestImportPreview: (Uri?, BaGuideCatalogImportKind) -> Unit,
    onConfirmFavoritesImport: (BaGuideCatalogImportPreviewState) -> Unit,
): BaGuideCatalogImportActions {
    val importStudentFavoritesLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            onRequestImportPreview(
                uri,
                BaGuideCatalogImportKind.Student,
            )
        }
    val importBgmFavoritesLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            onRequestImportPreview(
                uri,
                BaGuideCatalogImportKind.Bgm,
            )
        }
    val importAllFavoritesLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            onRequestImportPreview(
                uri,
                BaGuideCatalogImportKind.All,
            )
        }
    return BaGuideCatalogImportActions(
        importStudentFavoritesLauncher = importStudentFavoritesLauncher,
        importBgmFavoritesLauncher = importBgmFavoritesLauncher,
        importAllFavoritesLauncher = importAllFavoritesLauncher,
        confirmFavoritesImport = onConfirmFavoritesImport,
    )
}
