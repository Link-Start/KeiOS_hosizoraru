package os.kei.ui.page.main.github.importer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import os.kei.core.platform.PredictiveBackOemCompat
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.UiPrefs
import os.kei.ui.page.main.back.ProvideBackNavigationRuntime
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.motion.LocalPredictiveBackAnimationsEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class GitHubStarImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GitHubStarImportTheme {
                GitHubStarImportPage(onClose = { finish() })
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            val hostActivity = context.findGitHubStarImportHostActivity()
            val intent = Intent(context, GitHubStarImportActivity::class.java).apply {
                if (hostActivity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (hostActivity != null) {
                hostActivity.startActivity(intent)
            } else {
                context.startActivity(intent)
            }
        }
    }
}

@Composable
private fun GitHubStarImportTheme(content: @Composable () -> Unit) {
    val transitionAnimationsEnabled = UiPrefs.isTransitionAnimationsEnabled()
    val predictiveBackPolicy = PredictiveBackOemCompat.currentPolicy(
        transitionAnimationsEnabled = transitionAnimationsEnabled,
        predictiveBackAnimationsEnabled = UiPrefs.isPredictiveBackAnimationsEnabled()
    )
    val colorSchemeMode = when (UiPrefs.getAppThemeMode()) {
        AppThemeMode.FOLLOW_SYSTEM -> ColorSchemeMode.System
        AppThemeMode.LIGHT -> ColorSchemeMode.Light
        AppThemeMode.DARK -> ColorSchemeMode.Dark
    }
    MiuixTheme(controller = ThemeController(colorSchemeMode)) {
        ProvideBackNavigationRuntime(policy = predictiveBackPolicy) {
            CompositionLocalProvider(
                LocalTransitionAnimationsEnabled provides transitionAnimationsEnabled,
                LocalPredictiveBackAnimationsEnabled provides predictiveBackPolicy.localPredictiveBackEnabled,
                LocalLiquidControlsEnabled provides UiPrefs.isLiquidSwitchEnabled()
            ) {
                content()
            }
        }
    }
}

private tailrec fun Context.findGitHubStarImportHostActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findGitHubStarImportHostActivity()
        else -> null
    }
}
