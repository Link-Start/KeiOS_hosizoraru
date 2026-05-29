package os.kei.ui.page.main.settings.page

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import os.kei.R
import os.kei.ui.page.main.widget.chrome.TabbedPageCategory
import com.composables.icons.lucide.R as LucideR

internal enum class SettingsCategory(
    override val iconRes: Int,
    override val labelRes: Int,
) : TabbedPageCategory {
    Access(LucideR.drawable.lucide_ic_shield_check, R.string.settings_category_access),
    Appearance(LucideR.drawable.lucide_ic_palette, R.string.settings_category_appearance),
    Effects(LucideR.drawable.lucide_ic_sliders_horizontal, R.string.settings_category_effects),
    Data(LucideR.drawable.lucide_ic_database, R.string.settings_category_data),
}

@Composable
internal fun SettingsCategory.label(): String {
    return stringResource(
        when (this) {
            SettingsCategory.Access -> R.string.settings_category_access
            SettingsCategory.Appearance -> R.string.settings_category_appearance
            SettingsCategory.Effects -> R.string.settings_category_effects
            SettingsCategory.Data -> R.string.settings_category_data
        }
    )
}

@Composable
internal fun SettingsCategory.icon(): ImageVector {
    val drawableRes = when (this) {
        SettingsCategory.Access -> LucideR.drawable.lucide_ic_shield_check
        SettingsCategory.Appearance -> LucideR.drawable.lucide_ic_palette
        SettingsCategory.Effects -> LucideR.drawable.lucide_ic_sliders_horizontal
        SettingsCategory.Data -> LucideR.drawable.lucide_ic_database
    }
    return ImageVector.vectorResource(drawableRes)
}
