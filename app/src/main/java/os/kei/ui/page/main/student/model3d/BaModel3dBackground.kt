package os.kei.ui.page.main.student.model3d

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import os.kei.core.prefs.KeiMmkv
import java.util.Locale

/** A small theme tint separates white clothing from the viewport without baking lighting into the model. */
internal fun model3dDefaultBackground(background: Color, primary: Color, dark: Boolean): Color =
    lerp(background, primary, if (dark) 0.08f else 0.025f).copy(alpha = 1f)

/** WebGL and the native backdrop always receive the same opaque result, including the picker's alpha control. */
internal fun model3dBackground(customArgb: Int?, themeBackground: Color): Color =
    customArgb?.let { Color(it).compositeOver(themeBackground) } ?: themeBackground

internal fun model3dUsesDarkContent(background: Color): Boolean =
    (background.luminance() + 0.05f) / 0.05f >= 1.05f / (background.luminance() + 0.05f)

internal fun model3dBackgroundHex(color: Color): String =
    "#" + (color.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase(Locale.ROOT)

internal fun parseModel3dColor(raw: String): Int? {
    val value = raw.trim().removePrefix("#")
    if (!value.matches(Regex("(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})"))) return null
    return value.toLong(16).let { if (value.length == 6) it or 0xFF000000L else it }.toInt()
}

/** This preference is shared across student viewers; a palette gesture never writes the store per frame. */
internal object BaModel3dAppearanceStore {
    private val store get() = KeiMmkv.byId("ba_model3d_appearance")
    fun loadBackground(): Int? = store.let { if (it.containsKey("background_argb")) it.decodeInt("background_argb") else null }
    fun saveBackground(color: Int?) {
        if (color == null) store.removeValueForKey("background_argb") else store.encode("background_argb", color)
    }
}
