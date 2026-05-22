@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.os.appLucideFolderIcon
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class BaGuideCatalogPlaybackSettingsState(
    val nativeBgmMediaNotificationEnabled: Boolean,
    val notificationPermissionGranted: Boolean,
    val onNativeBgmMediaNotificationChange: (Boolean) -> Unit,
)

@Composable
internal fun BaGuideCatalogPlaybackSettingsGroup(state: BaGuideCatalogPlaybackSettingsState) {
    SheetSummaryCard(
        title = stringResource(R.string.ba_catalog_playback_settings_title),
        badgeLabel = stringResource(R.string.ba_catalog_transfer_hero_settings),
        accentColor = Color(0xFF38BDF8),
        verticalSpacing = 10.dp,
    ) {
        SheetControlRow(
            label = stringResource(R.string.ba_catalog_bgm_native_media_notification),
            summary =
                if (state.nativeBgmMediaNotificationEnabled && !state.notificationPermissionGranted) {
                    stringResource(R.string.ba_catalog_bgm_native_media_notification_permission_summary)
                } else {
                    stringResource(R.string.ba_catalog_bgm_native_media_notification_summary)
                },
        ) {
            AppSwitch(
                checked = state.nativeBgmMediaNotificationEnabled,
                onCheckedChange = state.onNativeBgmMediaNotificationChange,
            )
        }
    }
}

@Composable
internal fun BaGuideCatalogTransferSaveLocationGroup(
    mediaSaveCustomEnabled: Boolean,
    mediaSaveFixedTreeUri: String,
    onMediaSaveCustomEnabledChange: (Boolean) -> Unit,
    onPickMediaSaveLocation: () -> Unit,
) {
    SheetSummaryCard(
        title = stringResource(R.string.ba_catalog_transfer_save_location),
        badgeLabel =
            if (mediaSaveCustomEnabled) {
                stringResource(R.string.ba_catalog_transfer_save_location_fixed)
            } else {
                stringResource(R.string.ba_catalog_transfer_save_location_saf_badge)
            },
        accentColor = MiuixTheme.colorScheme.primary,
        verticalSpacing = 10.dp,
    ) {
        SheetControlRow(
            label = stringResource(R.string.ba_catalog_transfer_save_location_fixed),
            summary =
                when {
                    !mediaSaveCustomEnabled -> stringResource(R.string.ba_catalog_transfer_save_location_saf)
                    mediaSaveFixedTreeUri.isBlank() -> stringResource(R.string.ba_catalog_transfer_save_location_fixed_empty)
                    else -> stringResource(R.string.ba_catalog_transfer_save_location_fixed_ready)
                },
        ) {
            AppSwitch(
                checked = mediaSaveCustomEnabled,
                onCheckedChange = onMediaSaveCustomEnabledChange,
            )
        }
        if (mediaSaveCustomEnabled) {
            AppStandaloneLiquidTextButton(
                text = stringResource(R.string.ba_settings_action_pick_media_save_location),
                onClick = onPickMediaSaveLocation,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = appLucideFolderIcon(),
                textColor = MiuixTheme.colorScheme.primary,
                iconTint = MiuixTheme.colorScheme.primary,
                variant = GlassVariant.SheetAction,
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis,
            )
        }
    }
}
