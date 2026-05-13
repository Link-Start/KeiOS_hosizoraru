package os.kei.ui.page.main.ba

import android.app.Activity
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.support.BaCalendarRefreshIntervalOption
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetFieldBlock
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.sheet.UnsavedSheetDismissConfirmDialog
import os.kei.ui.page.main.widget.sheet.rememberUnsavedSheetDismissHandler
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class BaSettingsSheetState(
    val cafeLevel: Int,
    val mediaAdaptiveRotationEnabled: Boolean,
    val mediaSaveCustomEnabled: Boolean,
    val mediaSaveFixedTreeUri: String,
    val idIndependentByServer: Boolean,
    val showEndedActivities: Boolean,
    val showEndedPools: Boolean,
    val showCalendarPoolImages: Boolean,
    val calendarRefreshIntervalHours: Int,
)

@Composable
internal fun BaSettingsSheet(
    show: Boolean,
    backdrop: Backdrop?,
    state: BaSettingsSheetState,
    onMediaAdaptiveRotationEnabledChange: (Boolean) -> Unit,
    onMediaSaveCustomEnabledChange: (Boolean) -> Unit,
    onMediaSaveFixedTreeUriChange: (String) -> Unit,
    onIdIndependentByServerChange: (Boolean) -> Unit,
    onShowEndedActivitiesChange: (Boolean) -> Unit,
    onShowEndedPoolsChange: (Boolean) -> Unit,
    onShowCalendarPoolImagesChange: (Boolean) -> Unit,
    onCalendarRefreshIntervalSelected: (Int) -> Unit,
    hasUnsavedChanges: Boolean,
    onDismissRequest: () -> Unit,
    onSaveRequest: () -> Unit,
) {
    val context = LocalContext.current
    val settingsAccent = Color(0xFF3B82F6)
    var refreshIntervalDropdownExpanded by remember { mutableStateOf(false) }
    var refreshIntervalDropdownAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    val pickMediaSaveFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val treeUri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            val persistableFlags = (result.data?.flags ?: 0) and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (persistableFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (persistableFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        onMediaSaveFixedTreeUriChange(treeUri.toString())
    }
    val dismissHandler = rememberUnsavedSheetDismissHandler(
        hasUnsavedChanges = hasUnsavedChanges,
        onDismissRequest = onDismissRequest
    )

    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(R.string.ba_settings_title),
        onDismissRequest = dismissHandler.requestDismiss,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                icon = MiuixIcons.Regular.Close,
                contentDescription = stringResource(R.string.common_close),
                variant = GlassVariant.Bar,
                onClick = dismissHandler.requestDismiss,
            )
        },
        endAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                icon = MiuixIcons.Regular.Ok,
                contentDescription = stringResource(R.string.common_save),
                variant = GlassVariant.Bar,
                onClick = onSaveRequest,
            )
        },
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetSectionTitle(stringResource(R.string.ba_settings_section_sync))
            SheetSectionCard {
                Text(
                    text = stringResource(R.string.ba_settings_card_sync_title),
                    color = settingsAccent,
                )
                SheetControlRow(
                    label = stringResource(R.string.ba_cd_refresh_interval),
                    summary = stringResource(R.string.ba_settings_summary_refresh_interval),
                ) {
                    BaSettingsRefreshIntervalDropdown(
                        backdrop = backdrop,
                        selectedHours = state.calendarRefreshIntervalHours,
                        expanded = refreshIntervalDropdownExpanded,
                        anchorBounds = refreshIntervalDropdownAnchorBounds,
                        onExpandedChange = { refreshIntervalDropdownExpanded = it },
                        onAnchorBoundsChange = { refreshIntervalDropdownAnchorBounds = it },
                        onSelected = onCalendarRefreshIntervalSelected,
                    )
                }
            }

            SheetSectionTitle(stringResource(R.string.ba_settings_section_media))
            SheetSectionCard {
                Text(
                    text = stringResource(R.string.ba_settings_card_media_title),
                    color = settingsAccent,
                )
                SheetControlRow(
                    label = stringResource(R.string.ba_settings_label_media_adaptive_rotation),
                    summary = stringResource(R.string.ba_settings_summary_media_adaptive_rotation),
                ) {
                    AppSwitch(
                        checked = state.mediaAdaptiveRotationEnabled,
                        onCheckedChange = onMediaAdaptiveRotationEnabledChange,
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.ba_settings_label_media_save_custom),
                    summary = stringResource(R.string.ba_settings_summary_media_save_custom),
                ) {
                    AppSwitch(
                        checked = state.mediaSaveCustomEnabled,
                        onCheckedChange = onMediaSaveCustomEnabledChange,
                    )
                }
                if (state.mediaSaveCustomEnabled) {
                    SheetFieldBlock(
                        title = stringResource(R.string.ba_settings_label_media_save_fixed_uri),
                        summary = if (state.mediaSaveFixedTreeUri.isBlank()) {
                            stringResource(R.string.ba_settings_summary_media_save_fixed_uri_empty)
                        } else {
                            stringResource(R.string.ba_settings_summary_media_save_fixed_uri_ready)
                        },
                        trailing = {
                            AppLiquidTextButton(
                                backdrop = backdrop,
                                variant = GlassVariant.SheetPrimaryAction,
                                textColor = Color(0xFF3B82F6),
                                text = stringResource(R.string.ba_settings_action_pick_media_save_location),
                                onClick = {
                                    val currentTreeUri = state.mediaSaveFixedTreeUri
                                        .takeIf { it.isNotBlank() }
                                        ?.let { raw -> runCatching { raw.toUri() }.getOrNull() }
                                    val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                        addFlags(
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                        )
                                        putExtra(
                                            DocumentsContract.EXTRA_INITIAL_URI,
                                            currentTreeUri
                                                ?: "content://com.android.externalstorage.documents/tree/primary%3ADownload".toUri()
                                        )
                                    }
                                    pickMediaSaveFolderLauncher.launch(pickerIntent)
                                }
                            )
                        }
                    ) {
                        AppLiquidSearchField(
                            modifier = Modifier.fillMaxWidth(),
                            value = state.mediaSaveFixedTreeUri,
                            onValueChange = { onMediaSaveFixedTreeUriChange(it.trim()) },
                            label = stringResource(R.string.ba_settings_hint_media_save_fixed_uri),
                            backdrop = backdrop,
                            variant = GlassVariant.SheetInput,
                            singleLine = true,
                            textAlign = TextAlign.Start,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            SheetSectionTitle(stringResource(R.string.ba_id_card_title))
            SheetSectionCard {
                SheetControlRow(
                    label = stringResource(R.string.ba_settings_label_id_independent_by_server),
                    summary = stringResource(R.string.ba_settings_summary_id_independent_by_server),
                ) {
                    AppSwitch(
                        checked = state.idIndependentByServer,
                        onCheckedChange = onIdIndependentByServerChange,
                    )
                }
            }
            SheetSectionTitle(stringResource(R.string.ba_settings_section_content))
            SheetSectionCard {
                SheetControlRow(label = stringResource(R.string.ba_settings_label_show_ended_activity)) {
                    AppSwitch(
                        checked = state.showEndedActivities,
                        onCheckedChange = onShowEndedActivitiesChange,
                    )
                }
                SheetControlRow(label = stringResource(R.string.ba_settings_label_show_ended_pool)) {
                    AppSwitch(
                        checked = state.showEndedPools,
                        onCheckedChange = onShowEndedPoolsChange,
                    )
                }
                SheetControlRow(label = stringResource(R.string.ba_settings_label_show_images)) {
                    AppSwitch(
                        checked = state.showCalendarPoolImages,
                        onCheckedChange = onShowCalendarPoolImagesChange,
                    )
                }
            }
        }
    }
    UnsavedSheetDismissConfirmDialog(
        show = dismissHandler.showConfirmDialog,
        onKeepEditing = dismissHandler.keepEditing,
        onDiscardChanges = dismissHandler.discardChanges
    )
}

@Composable
private fun BaSettingsRefreshIntervalDropdown(
    backdrop: Backdrop?,
    selectedHours: Int,
    expanded: Boolean,
    anchorBounds: IntRect?,
    onExpandedChange: (Boolean) -> Unit,
    onAnchorBoundsChange: (IntRect?) -> Unit,
    onSelected: (Int) -> Unit,
) {
    val options = BaCalendarRefreshIntervalOption.entries
    val selected = BaCalendarRefreshIntervalOption.fromHours(selectedHours)
    AppDropdownSelector(
        modifier = Modifier.width(128.dp),
        selectedText = stringResource(selected.labelRes),
        options = options.map { stringResource(it.labelRes) },
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        expanded = expanded,
        anchorBounds = anchorBounds,
        onExpandedChange = onExpandedChange,
        onSelectedIndexChange = { index ->
            options.getOrNull(index)?.let { option ->
                onSelected(option.hours)
            }
            onExpandedChange(false)
        },
        onAnchorBoundsChange = onAnchorBoundsChange,
        backdrop = backdrop,
        variant = GlassVariant.SheetAction,
        textColor = MiuixTheme.colorScheme.primary,
        horizontalPadding = 10.dp,
        anchorAlignment = Alignment.CenterEnd,
    )
}
