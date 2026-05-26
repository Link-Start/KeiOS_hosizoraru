package os.kei.ui.page.main.jsonimport

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import os.kei.MainActivity
import os.kei.core.platform.PredictiveBackOemCompat
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.UiPrefs
import os.kei.ui.page.main.back.ProvideBackNavigationRuntime
import os.kei.ui.page.main.widget.motion.LocalPredictiveBackAnimationsEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class KeiOSJsonImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialIntent = intent
        setContent {
            val appThemeMode = UiPrefs.getAppThemeMode()
            val colorSchemeMode =
                when (appThemeMode) {
                    AppThemeMode.FOLLOW_SYSTEM -> ColorSchemeMode.System
                    AppThemeMode.LIGHT -> ColorSchemeMode.Light
                    AppThemeMode.DARK -> ColorSchemeMode.Dark
                }
            val transitionAnimationsEnabled = UiPrefs.isTransitionAnimationsEnabled()
            val predictiveBackPolicy =
                PredictiveBackOemCompat.currentPolicy(
                    transitionAnimationsEnabled = transitionAnimationsEnabled,
                    predictiveBackAnimationsEnabled = UiPrefs.isPredictiveBackAnimationsEnabled(),
                )
            val controller = ThemeController(colorSchemeMode)

            MiuixTheme(controller = controller) {
                ProvideBackNavigationRuntime(policy = predictiveBackPolicy) {
                    CompositionLocalProvider(
                        LocalTransitionAnimationsEnabled provides transitionAnimationsEnabled,
                        LocalPredictiveBackAnimationsEnabled provides predictiveBackPolicy.localPredictiveBackEnabled,
                    ) {
                        val viewModel: KeiOSJsonImportViewModel = viewModel()
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        val lifecycleOwner = LocalLifecycleOwner.current

                        LaunchedEffect(viewModel, initialIntent) {
                            viewModel.loadIntent(initialIntent)
                        }

                        LaunchedEffect(viewModel, lifecycleOwner) {
                            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                viewModel.events.collect { event ->
                                    when (event) {
                                        is KeiOSJsonImportEvent.OpenResult -> openResultPage(event.kind)
                                    }
                                }
                            }
                        }

                        KeiOSJsonImportPage(
                            state = uiState,
                            onConfirmImport = viewModel::confirmImport,
                            onOpenResult = viewModel::requestOpenResult,
                            onClose = { finish() },
                        )
                    }
                }
            }
        }
    }

    private fun openResultPage(kind: KeiOSJsonImportKind) {
        val targetPage =
            kind.resultTargetBottomPage() ?: run {
                finish()
                return
            }
        val targetIntent =
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE, targetPage)
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                )
            }
        runCatching { startActivity(targetIntent) }
            .onFailure {
                runCatching {
                    startActivity(
                        Intent(this, MainActivity::class.java).apply {
                            putExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE, targetPage)
                        },
                    )
                }
            }
        finish()
    }

    private fun KeiOSJsonImportKind.resultTargetBottomPage(): String? =
        when (this) {
            KeiOSJsonImportKind.GitHubTracked -> MainActivity.TARGET_BOTTOM_PAGE_GITHUB

            KeiOSJsonImportKind.OsActivityCards,
            KeiOSJsonImportKind.OsShellCards,
            KeiOSJsonImportKind.OsCardsBundle,
            KeiOSJsonImportKind.OsInfoCard,
            -> MainActivity.TARGET_BOTTOM_PAGE_OS

            KeiOSJsonImportKind.BaCatalogFavorites,
            KeiOSJsonImportKind.BaBgmFavorites,
            KeiOSJsonImportKind.BaAllFavorites,
            -> MainActivity.TARGET_BOTTOM_PAGE_BA

            KeiOSJsonImportKind.McpLogs -> MainActivity.TARGET_BOTTOM_PAGE_MCP

            KeiOSJsonImportKind.Unknown -> null
        }
}
