@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.page

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import os.kei.R
import os.kei.core.ext.showLiquidToastOnly
import os.kei.core.ext.showToast
import os.kei.core.ui.resource.resolveString
import os.kei.ui.page.main.settings.state.SettingsPageEvent
import os.kei.ui.page.main.settings.state.SettingsPageViewModel

@Composable
internal fun BindSettingsLogExportAction(
    context: Context,
    settingsPageViewModel: SettingsPageViewModel,
) {
    val logExportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/zip"),
        ) { uri ->
            settingsPageViewModel.completeLogExport(context, uri)
        }
    LaunchedEffect(settingsPageViewModel, logExportLauncher, context) {
        settingsPageViewModel.events.collect { event ->
            when (event) {
                is SettingsPageEvent.Toast -> {
                    context.showToast(event.messageRes)
                }

                is SettingsPageEvent.LiquidToast -> {
                    context.showLiquidToastOnly(event.messageRes)
                }

                is SettingsPageEvent.FailureToast -> {
                    val reason =
                        event.reason.ifBlank {
                            context.resolveString(R.string.common_unknown)
                        }
                    context.showToast(context.resolveString(event.messageRes, reason))
                }

                is SettingsPageEvent.LaunchLogExport -> {
                    runCatching {
                        logExportLauncher.launch(event.fileName)
                    }.onFailure {
                        settingsPageViewModel.finishLogExport()
                        context.showToast(
                            context.resolveString(
                                R.string.settings_log_toast_export_failed,
                                it.javaClass.simpleName,
                            ),
                        )
                    }
                }
            }
        }
    }
}
