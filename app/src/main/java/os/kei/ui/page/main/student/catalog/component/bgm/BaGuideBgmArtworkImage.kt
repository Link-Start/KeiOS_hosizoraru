package os.kei.ui.page.main.student.catalog.component.bgm

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import os.kei.ui.page.main.student.GameKeeMediaImageLoader
import os.kei.ui.page.main.student.catalog.BaGuideCatalogIconCache

@Composable
internal fun BaGuideBgmArtworkImage(
    imageUrl: String,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(
        initialValue = BaGuideCatalogIconCache.get(imageUrl),
        imageUrl
    ) {
        BaGuideCatalogIconCache.get(imageUrl)?.let { cached ->
            value = cached
            return@produceState
        }
        value = GameKeeMediaImageLoader.loadCatalogIcon(context, imageUrl)
    }
    val rendered = bitmap ?: return
    val imageBitmap = remember(rendered) { rendered.asImageBitmap() }
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier
    )
}
