package os.kei.ui.page.main.student.tabcontent.render

import android.content.Context
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.widget.glass.LiquidInfoBlock

internal fun LazyListScope.renderGuideGalleryTabContent(
    tabLabel: String,
    info: BaStudentGuideInfo?,
    error: String?,
    backdrop: Backdrop,
    accent: Color,
    context: Context,
    sourceUrl: String,
    galleryCacheRevision: Int,
    bgmFavoriteAudioUrls: Set<String>,
    mediaAdaptiveRotationEnabled: Boolean,
    galleryState: GuideGalleryTabResolvedState?,
    onOpenExternal: (String) -> Unit,
    onSaveMedia: (url: String, title: String) -> Unit,
    onSaveMediaPack: (items: List<Pair<String, String>>, packTitle: String) -> Unit,
    onToggleBgmFavorite: (GuideBgmFavoriteItem) -> Unit,
) {
    val guide = info
    if (guide == null) {
        item {
            LiquidInfoBlock(
                backdrop = backdrop,
                title = tabLabel,
                subtitle = info?.subtitle?.ifBlank { "GameKee" } ?: "GameKee",
                accent = accent,
            )
        }
        return
    }

    val resolvedGalleryState = galleryState ?: resolveGuideGalleryTabState(guide)
    item(key = "guide-gallery-model-3d", contentType = "guide_gallery_model_3d") {
        os.kei.ui.page.main.student.model3d.GuideModel3dEntryRoute(guide, context, backdrop)
    }
    renderGuideGalleryStateContent(
        state = resolvedGalleryState,
        error = error,
        backdrop = backdrop,
        context = context,
        sourceUrl = sourceUrl,
        studentTitle = guide.title,
        studentImageUrl = guide.imageUrl,
        galleryCacheRevision = galleryCacheRevision,
        bgmFavoriteAudioUrls = bgmFavoriteAudioUrls,
        mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
        onOpenExternal = onOpenExternal,
        onSaveMedia = onSaveMedia,
        onSaveMediaPack = onSaveMediaPack,
        onToggleBgmFavorite = onToggleBgmFavorite,
    )
}
