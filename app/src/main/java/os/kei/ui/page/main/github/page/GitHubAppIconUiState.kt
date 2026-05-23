package os.kei.ui.page.main.github.page

import android.graphics.Bitmap

internal data class GitHubAppIconUiState(
    val bitmaps: Map<String, Bitmap> = emptyMap(),
    val missingPackages: Set<String> = emptySet(),
)
