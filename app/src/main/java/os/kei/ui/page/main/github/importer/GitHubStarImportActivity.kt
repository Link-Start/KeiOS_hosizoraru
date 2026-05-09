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
import androidx.compose.runtime.Immutable
import os.kei.core.platform.PredictiveBackOemCompat
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.UiPrefs
import os.kei.feature.github.model.StarImportApplyResult
import os.kei.ui.page.main.back.ProvideBackNavigationRuntime
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.motion.LocalPredictiveBackAnimationsEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private const val EXTRA_CHANGED_COUNT = "os.kei.github.star_import.CHANGED_COUNT"
private const val EXTRA_AFFECTED_TRACK_IDS = "os.kei.github.star_import.AFFECTED_TRACK_IDS"
private const val EXTRA_REMOVED_TRACK_IDS = "os.kei.github.star_import.REMOVED_TRACK_IDS"
private const val EXTRA_AFFECTED_PACKAGES = "os.kei.github.star_import.AFFECTED_PACKAGES"

class GitHubStarImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GitHubStarImportTheme {
                GitHubStarImportPage(
                    onImported = { result ->
                        setResult(RESULT_OK, buildResultIntent(result))
                    },
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        fun buildIntent(context: Context): Intent {
            return Intent(context, GitHubStarImportActivity::class.java)
        }

        fun launch(context: Context) {
            val hostActivity = context.findGitHubStarImportHostActivity()
            val intent = buildIntent(context).apply {
                if (hostActivity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (hostActivity != null) {
                hostActivity.startActivity(intent)
            } else {
                context.startActivity(intent)
            }
        }

        internal fun parseResult(resultCode: Int, data: Intent?): GitHubStarImportActivityResult? {
            if (resultCode != RESULT_OK) return null
            data ?: return null
            val changedCount = data.getIntExtra(EXTRA_CHANGED_COUNT, 0)
            val affectedTrackIds = data.getStringArrayListExtra(EXTRA_AFFECTED_TRACK_IDS)
                .orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            val removedTrackIds = data.getStringArrayListExtra(EXTRA_REMOVED_TRACK_IDS)
                .orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            val affectedPackages = data.getStringArrayListExtra(EXTRA_AFFECTED_PACKAGES)
                .orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            if (changedCount <= 0 && affectedTrackIds.isEmpty() && removedTrackIds.isEmpty()) return null
            return GitHubStarImportActivityResult(
                changedCount = changedCount,
                affectedTrackIds = affectedTrackIds,
                removedTrackIds = removedTrackIds,
                affectedPackages = affectedPackages
            )
        }
    }
}

@Immutable
internal data class GitHubStarImportActivityResult(
    val changedCount: Int,
    val affectedTrackIds: Set<String>,
    val removedTrackIds: Set<String>,
    val affectedPackages: Set<String>
)

private fun buildResultIntent(result: StarImportApplyResult): Intent {
    return Intent()
        .putExtra(
            EXTRA_CHANGED_COUNT,
            result.changedCount
        )
        .putStringArrayListExtra(
            EXTRA_AFFECTED_TRACK_IDS,
            ArrayList(result.affectedTrackIds)
        )
        .putStringArrayListExtra(
            EXTRA_REMOVED_TRACK_IDS,
            ArrayList(result.removedTrackIds)
        )
        .putStringArrayListExtra(
            EXTRA_AFFECTED_PACKAGES,
            ArrayList(result.affectedPackages)
        )
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
