@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideCatalogEntryAvatar(
    imageUrl: String,
    fallbackRes: Int,
    loadEnabled: Boolean = true,
    size: Dp = 56.dp,
) {
    if (imageUrl.isBlank()) {
        BaGuideCatalogEntryAvatarFallback(iconRes = fallbackRes, size = size)
    } else {
        BaGuideCatalogEntryAvatarImage(
            imageUrl = imageUrl,
            fallbackRes = fallbackRes,
            loadEnabled = loadEnabled,
            size = size,
        )
    }
}

@Composable
private fun BaGuideCatalogEntryAvatarImage(
    imageUrl: String,
    fallbackRes: Int,
    loadEnabled: Boolean,
    size: Dp,
) {
    val rendered = if (loadEnabled) LocalBaGuideCatalogImageBitmaps.current[imageUrl.trim()] else null
    if (rendered == null) {
        BaGuideCatalogEntryAvatarFallback(iconRes = fallbackRes, size = size)
        return
    }
    val imageBitmap = remember(rendered) { rendered.asImageBitmap() }
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp)),
    )
}

@Composable
private fun BaGuideCatalogEntryAvatarFallback(
    iconRes: Int,
    size: Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
