package os.kei.ui.page.main.feedback

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.intent.SafeExternalIntents
import os.kei.core.platform.PredictiveBackOemCompat
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.UiPrefs
import os.kei.ui.page.main.back.ProvideBackNavigationRuntime
import os.kei.ui.page.main.widget.motion.LocalPredictiveBackAnimationsEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class FeedbackIssueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialThemeMode = UiPrefs.getAppThemeMode()
        val initialTransitionAnimationsEnabled = UiPrefs.isTransitionAnimationsEnabled()
        val initialPredictiveBackPolicy =
            PredictiveBackOemCompat.currentPolicy(
                transitionAnimationsEnabled = initialTransitionAnimationsEnabled,
                predictiveBackAnimationsEnabled = UiPrefs.isPredictiveBackAnimationsEnabled(),
            )
        val initialColorSchemeMode =
            when (initialThemeMode) {
                AppThemeMode.FOLLOW_SYSTEM -> ColorSchemeMode.System
                AppThemeMode.LIGHT -> ColorSchemeMode.Light
                AppThemeMode.DARK -> ColorSchemeMode.Dark
            }

        setContent {
            val controller =
                androidx.compose.runtime.remember(initialColorSchemeMode) {
                    ThemeController(initialColorSchemeMode)
                }

            MiuixTheme(controller = controller) {
                ProvideBackNavigationRuntime(policy = initialPredictiveBackPolicy) {
                    CompositionLocalProvider(
                        LocalTransitionAnimationsEnabled provides initialTransitionAnimationsEnabled,
                        LocalPredictiveBackAnimationsEnabled provides initialPredictiveBackPolicy.localPredictiveBackEnabled,
                    ) {
                        val viewModel: FeedbackIssueViewModel = viewModel()
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        val lifecycleOwner = LocalLifecycleOwner.current
                        val exportLauncher =
                            rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.CreateDocument("application/zip"),
                            ) { uri ->
                                if (uri != null) {
                                    viewModel.exportZip(uri)
                                }
                            }

                        LaunchedEffect(viewModel, lifecycleOwner, exportLauncher) {
                            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                viewModel.events.collect { event ->
                                    when (event) {
                                        is FeedbackIssueEvent.LaunchLogExport -> {
                                            exportLauncher.launch(event.fileName)
                                        }

                                        is FeedbackIssueEvent.OpenUrl -> {
                                            val opened =
                                                SafeExternalIntents.startBrowsableUrl(
                                                    context = this@FeedbackIssueActivity,
                                                    url = event.url,
                                                )
                                            if (!opened) {
                                                showToast(getString(R.string.common_open_link_failed))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        FeedbackIssuePage(
                            state = uiState,
                            onTitleChange = viewModel::updateTitle,
                            onBodyChange = viewModel::updateBody,
                            onRefresh = viewModel::refresh,
                            onExportZip = viewModel::requestLogExport,
                            onClearLogs = viewModel::clearLogs,
                            onRequestSubmit = viewModel::requestSubmit,
                            onDismissSubmit = viewModel::dismissSubmitConfirmation,
                            onConfirmBrowserSubmit = viewModel::submitViaBrowser,
                            onConfirmApiSubmit = viewModel::submitViaApi,
                            onClose = { finish() },
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            val hostActivity = context.findHostActivity()
            val intent =
                Intent(context, FeedbackIssueActivity::class.java).apply {
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

private tailrec fun Context.findHostActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findHostActivity()
        else -> null
    }
