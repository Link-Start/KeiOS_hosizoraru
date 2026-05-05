package os.kei.ui.page.main.ba

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.UiPrefs
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
internal fun BaStandaloneActivityTheme(content: @Composable () -> Unit) {
    val colorSchemeMode = when (UiPrefs.getAppThemeMode()) {
        AppThemeMode.FOLLOW_SYSTEM -> ColorSchemeMode.System
        AppThemeMode.LIGHT -> ColorSchemeMode.Light
        AppThemeMode.DARK -> ColorSchemeMode.Dark
    }

    MiuixTheme(controller = ThemeController(colorSchemeMode)) {
        content()
    }
}

internal tailrec fun Context.findBaHostActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findBaHostActivity()
        else -> null
    }
}
