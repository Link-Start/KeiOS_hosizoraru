@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.section

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.settings.support.SettingsInfoItem
import os.kei.ui.page.main.settings.support.SettingsNavigationItem
import os.kei.ui.page.main.sync.WebDavSyncStore

@Composable
internal fun SettingsWebDavSyncSection(
    onClick: () -> Unit,
    enabledCardColor: Color,
    disabledCardColor: Color,
) {
    val configured = WebDavSyncStore.hasConfig()
    val containerColor = if (configured) enabledCardColor else disabledCardColor
    SettingsGroupCard(
        header = stringResource(R.string.settings_category_data),
        title = stringResource(R.string.webdav_sync_title),
        containerColor = containerColor,
    ) {
        SettingsNavigationItem(
            title = stringResource(R.string.webdav_sync_title),
            summary = if (configured) stringResource(R.string.webdav_sync_configured_summary)
            else stringResource(R.string.webdav_sync_not_configured_summary),
            onClick = onClick,
        )
        if (configured) {
            SettingsInfoItem(
                key = stringResource(R.string.webdav_sync_status_label),
                value = stringResource(R.string.webdav_sync_status_active),
            )
        }
    }
}
