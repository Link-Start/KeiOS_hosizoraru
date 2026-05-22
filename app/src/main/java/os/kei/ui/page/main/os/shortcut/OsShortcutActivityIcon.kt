@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.shortcut

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.kyant.capsule.ContinuousCapsule
import os.kei.R
import os.kei.ui.page.main.github.AppIcon
import os.kei.ui.page.main.os.osActivityShortcutIconKey
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ShortcutActivityIcon(
    packageName: String,
    className: String,
    size: Dp,
    bitmap: Bitmap?,
    fallbackToPackageIcon: Boolean = true,
) {
    val normalizedPackageName = packageName.trim()
    val normalizedClassName = className.trim()
    val iconCacheKey = osActivityShortcutIconKey(normalizedPackageName, normalizedClassName)

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = iconCacheKey,
            modifier =
                Modifier
                    .width(size)
                    .height(size)
                    .clip(ContinuousCapsule),
        )
    } else if (fallbackToPackageIcon && normalizedPackageName.isNotBlank()) {
        AppIcon(packageName = normalizedPackageName, size = size)
    } else {
        Box(
            modifier =
                Modifier
                    .width(size)
                    .height(size)
                    .clip(ContinuousCapsule),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.github_strategy_app_fallback),
                color = MiuixTheme.colorScheme.primary,
                fontSize = AppTypographyTokens.Caption.fontSize,
                lineHeight = AppTypographyTokens.Caption.lineHeight,
            )
        }
    }
}
