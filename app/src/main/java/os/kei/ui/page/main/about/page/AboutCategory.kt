package os.kei.ui.page.main.about.page

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import os.kei.R
import com.composables.icons.lucide.R as LucideR

internal enum class AboutCategory {
    Overview,
    System,
    Tech,
    Lab
}

@Composable
internal fun AboutCategory.label(): String {
    return stringResource(
        when (this) {
            AboutCategory.Overview -> R.string.about_category_overview
            AboutCategory.System -> R.string.about_category_system
            AboutCategory.Tech -> R.string.about_category_tech
            AboutCategory.Lab -> R.string.about_category_lab
        }
    )
}

@Composable
internal fun AboutCategory.icon(): ImageVector {
    val drawableRes = when (this) {
        AboutCategory.Overview -> LucideR.drawable.lucide_ic_info
        AboutCategory.System -> LucideR.drawable.lucide_ic_shield_check
        AboutCategory.Tech -> LucideR.drawable.lucide_ic_layers_2
        AboutCategory.Lab -> LucideR.drawable.lucide_ic_flask_conical
    }
    return ImageVector.vectorResource(drawableRes)
}
