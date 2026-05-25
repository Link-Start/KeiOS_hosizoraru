package os.kei.ui.page.main.ba

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import os.kei.core.platform.PredictiveBackOemCompat
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.UiPrefs
import os.kei.ui.page.main.back.ProvideBackNavigationRuntime
import os.kei.ui.page.main.widget.motion.LocalPredictiveBackAnimationsEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
internal fun BaStandaloneActivityTheme(content: @Composable () -> Unit) {
    val transitionAnimationsEnabled = remember { UiPrefs.isTransitionAnimationsEnabled() }
    val predictiveBackPolicy =
        remember(transitionAnimationsEnabled) {
            PredictiveBackOemCompat.currentPolicy(
                transitionAnimationsEnabled = transitionAnimationsEnabled,
                predictiveBackAnimationsEnabled = UiPrefs.isPredictiveBackAnimationsEnabled(),
            )
        }
    val colorSchemeMode =
        remember {
            when (UiPrefs.getAppThemeMode()) {
                AppThemeMode.FOLLOW_SYSTEM -> ColorSchemeMode.System
                AppThemeMode.LIGHT -> ColorSchemeMode.Light
                AppThemeMode.DARK -> ColorSchemeMode.Dark
            }
        }
    val controller = remember(colorSchemeMode) { ThemeController(colorSchemeMode) }

    MiuixTheme(controller = controller) {
        ProvideBackNavigationRuntime(policy = predictiveBackPolicy) {
            CompositionLocalProvider(
                LocalTransitionAnimationsEnabled provides transitionAnimationsEnabled,
                LocalPredictiveBackAnimationsEnabled provides predictiveBackPolicy.localPredictiveBackEnabled
            ) {
                content()
            }
        }
    }
}

internal tailrec fun Context.findBaHostActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findBaHostActivity()
        else -> null
    }
}
