@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.section.gallery

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.os.appLucideFullscreenIcon
import os.kei.ui.page.main.student.BaGuideGalleryItem
import os.kei.ui.page.main.student.GuideRemoteImageAdaptive
import os.kei.ui.page.main.student.GuideWebMemoryLobbyActivity
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppFeatureCard

@Composable
internal fun GuideWebMemoryLobbyCard(
    item: BaGuideGalleryItem,
    previewFallbackUrl: String = "",
) {
    val lobby = item.webMemoryLobby ?: return
    val context = LocalContext.current
    val openLobby = { GuideWebMemoryLobbyActivity.launch(context, lobby) }
    val title = stringResource(R.string.guide_gallery_dynamic_lobby)
    AppFeatureCard(
        title = title,
        subtitle = stringResource(R.string.guide_gallery_dynamic_lobby_entry_body),
        onClick = openLobby,
        headerEndActions = {
            AppCompactIconAction(
                icon = appLucideFullscreenIcon(),
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
