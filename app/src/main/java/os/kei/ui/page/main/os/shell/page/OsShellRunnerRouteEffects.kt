@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.shell.page

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import os.kei.core.ext.showLiquidToastOnly
import os.kei.core.ext.showToast
import os.kei.ui.page.main.os.shell.OsShellRunnerEvent
import os.kei.ui.page.main.os.shell.OsShellRunnerPersistentState
import os.kei.ui.page.main.os.shell.OsShellRunnerStartupBehavior
import os.kei.ui.page.main.os.shell.OsShellRunnerViewModel
import os.kei.ui.page.main.os.shell.state.OsShellRunnerTextBundle

@Composable
internal fun OsShellRunnerRouteEffects(
    context: Context,
    shellRunnerViewModel: OsShellRunnerViewModel,
    pageState: OsShellRunnerPageStateHolder,
    persistentState: OsShellRunnerPersistentState,
    textBundle: OsShellRunnerTextBundle,
) {
    LaunchedEffect(
        shellRunnerViewModel,
        textBundle.commandStoppedText,
        textBundle.outputResultLabel,
        textBundle.outputTimeLabel,
    ) {
        shellRunnerViewModel.loadPersistentState(
            commandStoppedText = textBundle.commandStoppedText,
            outputResultLabel = textBundle.outputResultLabel,
            outputTimeLabel = textBundle.outputTimeLabel,
        )
    }
    LaunchedEffect(shellRunnerViewModel) {
        shellRunnerViewModel.refreshChromePrefs()
    }
    LaunchedEffect(shellRunnerViewModel, context) {
        shellRunnerViewModel.events.collect { event ->
            when (event) {
                is OsShellRunnerEvent.Toast -> {
                    context.showToast(event.message)
                }

                is OsShellRunnerEvent.LiquidToast -> {
                    context.showLiquidToastOnly(event.message)
                }

                is OsShellRunnerEvent.OpenSaveCommandSheet -> {
                    pageState.openSaveSheet(event.suggestedSubtitle)
                }

                OsShellRunnerEvent.CloseSaveCommandSheet -> {
                    pageState.showSaveSheet = false
                }
            }
        }
    }
    BindOsShellRunnerChromePrefsRefreshEffect(shellRunnerViewModel)
    LaunchedEffect(persistentState.loaded) {
        if (
            persistentState.loaded &&
            !pageState.startupFocusApplied &&
            persistentState.settings.startupBehavior == OsShellRunnerStartupBehavior.FocusInput
        ) {
            pageState.requestStartupFocus()
        }
    }
}

@Composable
private fun BindOsShellRunnerChromePrefsRefreshEffect(shellRunnerViewModel: OsShellRunnerViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, shellRunnerViewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    shellRunnerViewModel.refreshChromePrefs()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
