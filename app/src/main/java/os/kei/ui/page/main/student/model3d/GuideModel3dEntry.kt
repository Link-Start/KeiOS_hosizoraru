@file:Suppress("FunctionName")
package os.kei.ui.page.main.student.model3d

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.ext.showToast
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucideFullscreenIcon
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.GuideRemoteImageAdaptive
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant

/** No WebGL context or model download is created while browsing the encyclopedia. */
@Composable
internal fun GuideModel3dEntryRoute(guide: BaStudentGuideInfo, context: Context, backdrop: Backdrop) {
    val resource by produceState<BaModel3dResource?>(null, guide.sourceUrl, guide.profileRows, guide.stats) {
        value = withContext(AppDispatchers.fileIo) { BaModel3dCatalog.load(context.applicationContext).resolve(guide) }
    }
    val selected = resource ?: return
    val hint = stringResource(R.string.guide_model_3d_hint)
    GuideModel3dEntryCard(guide.imageUrl, backdrop,
        onOpen = { GuideModel3dActivity.launch(context, selected) },
        onSource = { context.startActivity(Intent(Intent.ACTION_VIEW, selected.sourceUrl.toUri())) },
        onHint = { context.showToast(hint, duration = Toast.LENGTH_LONG) },
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun GuideModel3dEntryCard(preview: String, backdrop: Backdrop, onOpen: () -> Unit, onSource: () -> Unit, onHint: () -> Unit) {
    AppFeatureCard(
        title = stringResource(R.string.guide_model_3d_title), subtitle = "", modifier = Modifier.fillMaxWidth(),
        containerColor = Color(0x223B82F6), onClick = onOpen, onHeaderClick = onHint,
        headerEndActions = {
            AppLiquidIconButton(backdrop, appLucideExternalLinkIcon(), width = 36.dp, height = 36.dp,
                variant = GlassVariant.Compact, iconTint = Color(0xFF3B82F6),
                contentDescription = stringResource(R.string.guide_gallery_dynamic_lobby_source), onClick = onSource)
            AppLiquidIconButton(backdrop, appLucideFullscreenIcon(), width = 36.dp, height = 36.dp,
                variant = GlassVariant.Compact, iconTint = Color(0xFF3B82F6),
                contentDescription = stringResource(R.string.guide_model_3d_open), onClick = onOpen)
        },
    ) {
        if (preview.isNotBlank()) GuideRemoteImageAdaptive(preview, modifier = Modifier.fillMaxWidth(), maxDecodeDimension = 960)
    }
}
