@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import os.kei.R

@Composable
internal fun shareImportVersionLabel(
    versionName: String,
    versionCode: String,
): String {
    val normalizedVersionName = versionName.trim()
    val normalizedVersionCode = versionCode.trim()
    return when {
        normalizedVersionName.isNotBlank() && normalizedVersionCode.isNotBlank() -> {
            stringResource(
                R.string.github_share_import_dialog_version_value,
                normalizedVersionName,
                normalizedVersionCode,
            )
        }

        normalizedVersionName.isNotBlank() -> {
            normalizedVersionName
        }

        normalizedVersionCode.isNotBlank() -> {
            normalizedVersionCode
        }

        else -> {
            ""
        }
    }
}
