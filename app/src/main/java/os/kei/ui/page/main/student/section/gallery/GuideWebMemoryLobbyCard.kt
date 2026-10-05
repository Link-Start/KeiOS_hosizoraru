@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.section.gallery

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucideFullscreenIcon
import os.kei.ui.page.main.student.BaGuideGalleryItem
import os.kei.ui.page.main.student.GuideRemoteImageAdaptive
import os.kei.ui.page.main.student.GuideWebMemoryLobbyActivity
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant

@Composable
internal fun GuideWebMemoryLobbyCard(
    item: BaGuideGalleryItem,
    previewFallbackUrl: String = "",
    backdrop: Backdrop? = null,
) {
    val lobby = item.webMemoryLobby ?: return
    val context = LocalContext.current
    val openLobby = { GuideWebMemoryLobbyActivity.launch(context, lobby) }
    val title = stringResource(R.string.guide_gallery_dynamic_lobby)
    val hint = stringResource(R.string.guide_gallery_dynamic_lobby_entry_body)
    AppFeatureCard(
        title = title,
        subtitle = "",
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color(0x223B82F6),
        onClick = openLobby,
        onHeaderClick = { context.showToast(hint, duration = Toast.LENGTH_LONG) },
        headerEndActions = {
            AppLiquidIconButton(
                backdrop = backdrop,
                icon = appLucideExternalLinkIcon(),
                width = 36.dp,
                height = 36.dp,
                variant = GlassVariant.Compact,
                iconTint = Color(0xFF3B82F6),
                contentDescription = stringResource(R.string.guide_gallery_dynamic_lobby_source),
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, lobby.viewerUrl.toUri())) },
            )
            AppLiquidIconButton(
                backdrop = backdrop,
                icon = appLucideFullscreenIcon(),
                width = 36.dp,
                height = 36.dp,
                variant = GlassVariant.Compact,
                iconTint = Color(0xFF3B82F6),
                contentDescription = stringResource(R.string.guide_gallery_dynamic_lobby_open),
                onClick = openLobby,
            )
        },
    ) {
        val preview = item.imageUrl.ifBlank { previewFallbackUrl }
        if (preview.isNotBlank()) {
            GuideRemoteImageAdaptive(preview, modifier = Modifier.fillMaxWidth(), maxDecodeDimension = 960)
        }
    }
}
