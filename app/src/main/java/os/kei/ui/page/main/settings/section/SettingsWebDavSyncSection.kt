@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.section

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.settings.support.SettingsNavigationItem

@Composable
internal fun SettingsWebDavSyncSection(
    onClick: () -> Unit,
    enabledCardColor: Color,
    disabledCardColor: Color,
) {
    val title = stringResource(R.string.webdav_sync_title)
    val summary = stringResource(R.string.webdav_sync_provider_label)
    SettingsGroupCard(
        header = title,
        title = title,
        containerColor = enabledCardColor,
    ) {
        SettingsNavigationItem(
            title = title,
            summary = summary,
            onClick = onClick,
        )
    }
}
