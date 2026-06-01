@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.section

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.os.appLucideDatabaseIcon
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
    val presentation = deriveWebDavSyncPresentation(configured)
    val autoSyncEnabled = WebDavSyncStore.isAutoSyncEnabled()
    val lastFullSyncTimeMs = WebDavSyncStore.getLastFullSyncTime()
    SettingsGroupCard(
        header = stringResource(R.string.settings_category_data),
        title = stringResource(R.string.webdav_sync_title),
        sectionIcon = appLucideDatabaseIcon(),
        containerColor = settingsSectionContainerColor(presentation, enabledCardColor, disabledCardColor),
    ) {
        SettingsNavigationItem(
            title = stringResource(R.string.webdav_sync_title),
            summary =
                if (configured) {
                    stringResource(R.string.webdav_sync_configured_summary)
                } else {
                    stringResource(R.string.webdav_sync_not_configured_summary)
                },
            onClick = onClick,
        )
        if (configured) {
            val config = WebDavSyncStore.loadConfig()
            SettingsInfoItem(
                key = stringResource(R.string.webdav_sync_status_label),
                value = stringResource(R.string.webdav_sync_status_active),
            )
            if (config != null) {
                SettingsInfoItem(
                    key = stringResource(R.string.webdav_sync_username),
                    value = config.username,
                )
            }
            SettingsInfoItem(
                key = stringResource(R.string.webdav_sync_auto_sync_label),
                value = stringResource(
                    if (autoSyncEnabled) {
                        R.string.webdav_sync_status_enabled
                    } else {
                        R.string.webdav_sync_status_disabled
                    },
                ),
            )
            SettingsInfoItem(
                key = stringResource(R.string.webdav_sync_last_sync_label),
                value = if (lastFullSyncTimeMs > 0L) {
                    webDavSettingsTime(lastFullSyncTimeMs)
                } else {
                    stringResource(R.string.webdav_sync_last_sync_never)
                },
            )
        }
    }
}

private fun webDavSettingsTime(timeMs: Long): String {
    val formatter = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(timeMs))
}
