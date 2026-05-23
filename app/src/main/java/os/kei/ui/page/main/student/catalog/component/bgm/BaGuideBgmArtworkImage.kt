@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import os.kei.ui.page.main.student.catalog.component.LocalBaGuideCatalogImageBitmaps

@Composable
internal fun BaGuideBgmArtworkImage(
    imageUrl: String,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val bitmap = LocalBaGuideCatalogImageBitmaps.current[imageUrl.trim()]
    val rendered = bitmap ?: return
    val imageBitmap = remember(rendered) { rendered.asImageBitmap() }
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
    )
}
