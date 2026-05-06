package os.kei.ui.page.main.settings.page

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.ui.page.main.settings.state.SettingsPageViewModel

@Composable
internal fun BindSettingsLogExportAction(
    context: Context,
    scope: CoroutineScope,
    settingsPageViewModel: SettingsPageViewModel,
    pendingExportFileName: String?
) {
    val logExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) {
            settingsPageViewModel.finishLogExport()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = settingsPageViewModel.exportLogZip(context, uri)
            settingsPageViewModel.finishLogExport()
            if (result.isSuccess) {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_log_toast_exported),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val reason = result.exceptionOrNull()?.javaClass?.simpleName
                    ?: context.getString(R.string.common_unknown)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_log_toast_export_failed, reason),
                    Toast.LENGTH_SHORT
                ).show()
            }
            settingsPageViewModel.reloadLogStats(context)
        }
    }
    LaunchedEffect(pendingExportFileName) {
        settingsPageViewModel.consumePendingExportFileName()
            ?.let(logExportLauncher::launch)
    }
}
