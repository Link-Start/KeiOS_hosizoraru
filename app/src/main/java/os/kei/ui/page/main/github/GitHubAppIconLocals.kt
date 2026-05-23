package os.kei.ui.page.main.github

import android.graphics.Bitmap
import androidx.compose.runtime.compositionLocalOf

internal val LocalGitHubAppIconBitmaps =
    compositionLocalOf<Map<String, Bitmap>> { emptyMap() }
