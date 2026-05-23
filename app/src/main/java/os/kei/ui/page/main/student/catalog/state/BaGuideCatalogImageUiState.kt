package os.kei.ui.page.main.student.catalog.state

import android.graphics.Bitmap

internal data class BaGuideCatalogImageUiState(
    val bitmaps: Map<String, Bitmap> = emptyMap(),
    val missingUrls: Set<String> = emptySet(),
)
