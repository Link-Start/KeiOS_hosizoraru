@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountNotificationMode
import os.kei.ui.page.main.ba.support.BaAccountProfileInput
import os.kei.ui.page.main.ba.support.BaGlobalReminderSettings
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetInputTitle
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaAccountManagementSheet(
    show: Boolean,
    backdrop: Backdrop?,
    state: BaOfficeAccountUiState,
    onAllAccountsFollowGlobalNotificationSettingsChange: (Boolean) -> Unit,
    onAccountEnabledChange: (BaAccountId, Boolean) -> Unit,
    onSelectAccount: (BaAccountId) -> Unit,
    onAddAccount: (BaAccountProfileInput) -> Unit,
    onUpdateAccount: (BaAccountId, BaAccountProfileInput) -> Unit,
    onDeleteAccount: (BaAccountId) -> Unit,
    onMoveAccount: (BaAccountId, Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val settingsAccent = Color(0xFF3B82F6)
    val serverOptions =
        listOf(
            stringResource(R.string.ba_server_cn),
            stringResource(R.string.ba_server_global),
            stringResource(R.string.ba_server_jp),
        )
    val defaultAccountName = stringResource(R.string.ba_account_management_new_account_default_name)
    var editorDraft by remember { mutableStateOf<BaAccountEditorDraft?>(null) }
    var pendingDeleteAccountId by remember { mutableStateOf<BaAccountId?>(null) }

    LaunchedEffect(show) {
        if (!show) {
            editorDraft = null
            pendingDeleteAccountId = null
        }
    }

    fun startAddAccount() {
        val defaultServerIndex =
            state.accounts
                .firstOrNull { it.id == state.activeAccountId }
                ?.serverIndex
                ?: state.accounts.firstOrNull()?.serverIndex
                ?: 2
        editorDraft =
            BaAccountEditorDraft(
                editingAccountId = null,
                displayName = defaultAccountName,
                nickname = defaultAccountName,
                friendCode = "ARISUKEI",
                serverIndex = defaultServerIndex,
                notificationMode = BaAccountNotificationMode.FollowGlobal,
                remindersEnabled = true,
                customReminderSettings = state.globalReminderSettings,
            )
        pendingDeleteAccountId = null
    }

    fun saveDraft(draft: BaAccountEditorDraft) {
        val accountId = draft.editingAccountId
        if (accountId == null) {
            onAddAccount(draft.toInput())
        } else {
            onUpdateAccount(
                accountId,
                draft.toInput(),
            )
        }
        editorDraft = null
    }

    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(R.string.ba_account_management_title),
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                icon = MiuixIcons.Regular.Close,
                contentDescription = stringResource(R.string.common_close),
                variant = GlassVariant.Bar,
                onClick = onDismissRequest,
            )
        },
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetSectionTitle(stringResource(R.string.ba_account_management_section_global))
            SheetSectionCard {
                SheetControlRow(
                    label = stringResource(R.string.ba_account_management_follow_global),
                    summary = stringResource(R.string.ba_account_management_follow_global_summary),
                ) {
                    AppSwitch(
                        checked = state.allAccountsFollowGlobalNotificationSettings,
                        onCheckedChange = onAllAccountsFollowGlobalNotificationSettingsChange,
                    )
                }
            }

            SheetSectionTitle(stringResource(R.string.ba_account_management_section_accounts))
            SheetSectionCard {
                SheetControlRow(
                    label = stringResource(R.string.ba_account_management_add_title),
                    summary = stringResource(R.string.ba_account_management_add_summary),
                ) {
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        text = stringResource(R.string.ba_account_management_add_action),
                        textColor = settingsAccent,
                        containerColor = settingsAccent,
                        variant = GlassVariant.SheetAction,
                        onClick = ::startAddAccount,
                    )
                }
            }

            editorDraft?.let { draft ->
                BaAccountEditorCard(
                    backdrop = backdrop,
                    draft = draft,
                    serverOptions = serverOptions,
                    allAccountsFollowGlobalNotificationSettings = state.allAccountsFollowGlobalNotificationSettings,
                    settingsAccent = settingsAccent,
                    onDraftChange = { editorDraft = it },
                    onCancel = { editorDraft = null },
                    onSave = ::saveDraft,
                )
            }

            val pendingDeleteAccount = state.accounts.firstOrNull { it.id == pendingDeleteAccountId }
            if (pendingDeleteAccount != null) {
                BaAccountDeleteConfirmCard(
                    backdrop = backdrop,
                    account = pendingDeleteAccount,
                    onCancel = { pendingDeleteAccountId = null },
                    onConfirm = {
                        onDeleteAccount(pendingDeleteAccount.id)
                        if (editorDraft?.editingAccountId == pendingDeleteAccount.id) {
                            editorDraft = null
                        }
                        pendingDeleteAccountId = null
                    },
                )
            }

            if (state.accounts.isEmpty()) {
                SheetSectionCard {
                    Text(
                        text = stringResource(R.string.ba_account_management_empty),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            } else {
                state.accounts.forEachIndexed { index, account ->
                    BaAccountManagementAccountRow(
                        backdrop = backdrop,
                        account = account,
                        active = account.id == state.activeAccountId,
                        settingsAccent = settingsAccent,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.accounts.lastIndex,
                        canDelete = state.accounts.size > 1,
                        onAccountEnabledChange = onAccountEnabledChange,
                        onSelectAccount = onSelectAccount,
                        onEditAccount = {
                            editorDraft = account.toEditorDraft()
                            pendingDeleteAccountId = null
                        },
                        onMoveAccount = { offset -> onMoveAccount(account.id, offset) },
                        onDeleteRequest = {
                            pendingDeleteAccountId = account.id
                            editorDraft = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BaAccountEditorCard(
    backdrop: Backdrop?,
    draft: BaAccountEditorDraft,
    serverOptions: List<String>,
    allAccountsFollowGlobalNotificationSettings: Boolean,
    settingsAccent: Color,
    onDraftChange: (BaAccountEditorDraft) -> Unit,
    onCancel: () -> Unit,
    onSave: (BaAccountEditorDraft) -> Unit,
) {
    var serverDropdownExpanded by remember(draft.editingAccountId) { mutableStateOf(false) }
    var serverDropdownAnchorBounds by remember(draft.editingAccountId) { mutableStateOf<IntRect?>(null) }
    var reminderModeDropdownExpanded by remember(draft.editingAccountId) { mutableStateOf(false) }
    var reminderModeDropdownAnchorBounds by remember(draft.editingAccountId) { mutableStateOf<IntRect?>(null) }
    val canSave = draft.displayName.isNotBlank() || draft.nickname.isNotBlank()
    val reminderModeOptions =
        listOf(
            stringResource(R.string.ba_account_management_notification_mode_follow_global),
            stringResource(R.string.ba_account_management_notification_mode_custom),
        )

    SheetSectionCard {
        SheetInputTitle(
            if (draft.editingAccountId == null) {
                stringResource(R.string.ba_account_management_editor_add_title)
            } else {
                stringResource(R.string.ba_account_management_editor_edit_title)
            },
        )
        SheetDescriptionText(stringResource(R.string.ba_account_management_editor_summary))
        AppLiquidSearchField(
            value = draft.displayName,
            onValueChange = { onDraftChange(draft.copy(displayName = it)) },
            label = stringResource(R.string.ba_account_management_editor_display_name),
            backdrop = backdrop,
            variant = GlassVariant.SheetInput,
            singleLine = true,
        )
        AppLiquidSearchField(
            value = draft.nickname,
            onValueChange = { onDraftChange(draft.copy(nickname = it)) },
            label = stringResource(R.string.ba_account_management_editor_nickname),
            backdrop = backdrop,
            variant = GlassVariant.SheetInput,
            singleLine = true,
        )
        AppLiquidSearchField(
            value = draft.friendCode,
            onValueChange = { onDraftChange(draft.copy(friendCode = it)) },
            label = stringResource(R.string.ba_account_management_editor_friend_code),
            backdrop = backdrop,
            variant = GlassVariant.SheetInput,
            singleLine = true,
        )
        SheetControlRow(
            label = stringResource(R.string.ba_account_management_editor_server),
        ) {
            AppDropdownSelector(
                modifier = Modifier.width(128.dp),
                selectedText = serverOptions[draft.serverIndex.coerceIn(0, 2)],
                options = serverOptions,
                selectedIndex = draft.serverIndex.coerceIn(0, 2),
                expanded = serverDropdownExpanded,
                anchorBounds = serverDropdownAnchorBounds,
                onExpandedChange = { serverDropdownExpanded = it },
                onSelectedIndexChange = { index ->
                    onDraftChange(draft.copy(serverIndex = index.coerceIn(0, 2)))
                    serverDropdownExpanded = false
                },
                onAnchorBoundsChange = { serverDropdownAnchorBounds = it },
                backdrop = backdrop,
                variant = GlassVariant.SheetAction,
                textColor = settingsAccent,
                anchorAlignment = Alignment.CenterEnd,
            )
        }
        SheetInputTitle(stringResource(R.string.ba_account_management_reminder_section))
        SheetControlRow(
            label = stringResource(R.string.ba_account_management_reminders_enabled),
            summary = stringResource(R.string.ba_account_management_reminders_enabled_summary),
        ) {
            AppSwitch(
                checked = draft.remindersEnabled,
                onCheckedChange = { enabled -> onDraftChange(draft.copy(remindersEnabled = enabled)) },
            )
        }
        if (allAccountsFollowGlobalNotificationSettings) {
            SheetDescriptionText(stringResource(R.string.ba_account_management_global_follow_locked_summary))
        } else {
            SheetControlRow(
                label = stringResource(R.string.ba_account_management_notification_mode),
            ) {
                AppDropdownSelector(
                    modifier = Modifier.width(156.dp),
                    selectedText =
                        reminderModeOptions[
                            when (draft.notificationMode) {
                                BaAccountNotificationMode.FollowGlobal -> 0
                                BaAccountNotificationMode.Custom -> 1
                            },
                        ],
                    options = reminderModeOptions,
                    selectedIndex =
                        when (draft.notificationMode) {
                            BaAccountNotificationMode.FollowGlobal -> 0
                            BaAccountNotificationMode.Custom -> 1
                        },
                    expanded = reminderModeDropdownExpanded,
                    anchorBounds = reminderModeDropdownAnchorBounds,
                    onExpandedChange = { reminderModeDropdownExpanded = it },
                    onSelectedIndexChange = { index ->
                        onDraftChange(
                            draft.copy(
                                notificationMode =
                                    if (index == 1) {
                                        BaAccountNotificationMode.Custom
                                    } else {
                                        BaAccountNotificationMode.FollowGlobal
                                    },
                            ),
                        )
                        reminderModeDropdownExpanded = false
                    },
                    onAnchorBoundsChange = { reminderModeDropdownAnchorBounds = it },
                    backdrop = backdrop,
                    variant = GlassVariant.SheetAction,
                    textColor = settingsAccent,
                    anchorAlignment = Alignment.CenterEnd,
                )
            }
            if (draft.notificationMode == BaAccountNotificationMode.Custom && draft.remindersEnabled) {
                SheetDescriptionText(stringResource(R.string.ba_account_management_custom_reminder_summary))
                BaAccountCustomReminderEditor(
                    backdrop = backdrop,
                    settings = draft.customReminderSettings,
                    settingsAccent = settingsAccent,
                    onSettingsChange = { settings ->
                        onDraftChange(draft.copy(customReminderSettings = settings))
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppLiquidTextButton(
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                text = stringResource(R.string.ba_account_management_editor_cancel),
                textColor = MiuixTheme.colorScheme.onBackgroundVariant,
                containerColor = MiuixTheme.colorScheme.onBackgroundVariant,
                variant = GlassVariant.SheetAction,
                onClick = onCancel,
            )
            AppLiquidTextButton(
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                text = stringResource(R.string.ba_account_management_editor_save),
                textColor = settingsAccent,
                containerColor = settingsAccent,
                enabled = canSave,
                variant = GlassVariant.SheetAction,
                onClick = { onSave(draft) },
            )
        }
    }
}

@Composable
private fun BaAccountCustomReminderEditor(
    backdrop: Backdrop?,
    settings: BaGlobalReminderSettings,
    settingsAccent: Color,
    onSettingsChange: (BaGlobalReminderSettings) -> Unit,
) {
    SheetControlRow(label = stringResource(R.string.ba_settings_label_ap_notify)) {
        AppSwitch(
            checked = settings.apNotifyEnabled,
            onCheckedChange = { enabled -> onSettingsChange(settings.copy(apNotifyEnabled = enabled)) },
        )
    }
    if (settings.apNotifyEnabled) {
        BaAccountReminderThresholdRow(
            backdrop = backdrop,
            label = stringResource(R.string.ba_settings_label_ap_threshold),
            threshold = settings.apNotifyThreshold,
            settingsAccent = settingsAccent,
            onThresholdChange = { threshold ->
                onSettingsChange(settings.copy(apNotifyThreshold = threshold))
            },
        )
    }
    SheetControlRow(
        label = stringResource(R.string.ba_settings_label_cafe_ap_notify),
        summary = stringResource(R.string.ba_settings_summary_cafe_ap_notify),
    ) {
        AppSwitch(
            checked = settings.cafeApNotifyEnabled,
            onCheckedChange = { enabled -> onSettingsChange(settings.copy(cafeApNotifyEnabled = enabled)) },
        )
    }
    if (settings.cafeApNotifyEnabled) {
        BaAccountReminderThresholdRow(
            backdrop = backdrop,
            label = stringResource(R.string.ba_settings_label_cafe_ap_threshold),
            threshold = settings.cafeApNotifyThreshold,
            settingsAccent = settingsAccent,
            onThresholdChange = { threshold ->
                onSettingsChange(settings.copy(cafeApNotifyThreshold = threshold))
            },
        )
    }
    SheetControlRow(
        label = stringResource(R.string.ba_settings_label_arena_refresh_notify),
        summary = stringResource(R.string.ba_settings_summary_arena_refresh_notify),
    ) {
        AppSwitch(
            checked = settings.arenaRefreshNotifyEnabled,
            onCheckedChange = { enabled ->
                onSettingsChange(settings.copy(arenaRefreshNotifyEnabled = enabled))
            },
        )
    }
    SheetControlRow(
        label = stringResource(R.string.ba_settings_label_cafe_visit_notify),
        summary = stringResource(R.string.ba_settings_summary_cafe_visit_notify),
    ) {
        AppSwitch(
            checked = settings.cafeVisitNotifyEnabled,
            onCheckedChange = { enabled ->
                onSettingsChange(settings.copy(cafeVisitNotifyEnabled = enabled))
            },
        )
    }
}

@Composable
private fun BaAccountReminderThresholdRow(
    backdrop: Backdrop?,
    label: String,
    threshold: Int,
    settingsAccent: Color,
    onThresholdChange: (Int) -> Unit,
) {
    SheetControlRow(label = label) {
        AppLiquidSearchField(
            modifier = Modifier.width(70.dp),
            value = threshold.coerceIn(0, BA_AP_MAX).toString(),
            onValueChange = { input ->
                normalizeAccountReminderThresholdInput(input)?.let(onThresholdChange)
            },
            label = stringResource(R.string.ba_account_management_threshold_hint),
            backdrop = backdrop,
            variant = GlassVariant.SheetInput,
            singleLine = true,
            textColor = settingsAccent,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun BaAccountDeleteConfirmCard(
    backdrop: Backdrop?,
    account: BaOfficeAccountCardUiState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val dangerColor = MiuixTheme.colorScheme.error
    SheetSectionCard(borderColor = dangerColor.copy(alpha = 0.32f)) {
        SheetInputTitle(stringResource(R.string.ba_account_management_delete_title))
        SheetDescriptionText(
            stringResource(
                R.string.ba_account_management_delete_summary,
                account.displayName,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppLiquidTextButton(
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                text = stringResource(R.string.ba_account_management_editor_cancel),
                textColor = MiuixTheme.colorScheme.onBackgroundVariant,
                containerColor = MiuixTheme.colorScheme.onBackgroundVariant,
                variant = GlassVariant.SheetAction,
                onClick = onCancel,
            )
            AppLiquidTextButton(
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                text = stringResource(R.string.ba_account_management_delete_confirm),
                textColor = dangerColor,
                containerColor = dangerColor,
                variant = GlassVariant.SheetDangerAction,
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun BaAccountManagementAccountRow(
    backdrop: Backdrop?,
    account: BaOfficeAccountCardUiState,
    active: Boolean,
    settingsAccent: Color,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onAccountEnabledChange: (BaAccountId, Boolean) -> Unit,
    onSelectAccount: (BaAccountId) -> Unit,
    onEditAccount: () -> Unit,
    onMoveAccount: (Int) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    SheetSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = account.displayName,
                        color = MiuixTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (active) {
                        Text(
                            text = stringResource(R.string.ba_account_management_active_badge),
                            color = settingsAccent,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text =
                        stringResource(
                            R.string.ba_account_management_account_summary,
                            accountServerLabel(account.serverIndex),
                            account.nickname,
                            account.friendCode,
                        ),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier.widthIn(min = 84.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppSwitch(
                    checked = account.enabled,
                    onCheckedChange = { enabled -> onAccountEnabledChange(account.id, enabled) },
                )
                AppLiquidTextButton(
                    backdrop = backdrop,
                    text =
                        if (active) {
                            stringResource(R.string.ba_account_management_active_action)
                        } else {
                            stringResource(R.string.ba_account_management_use_action)
                        },
                    textColor = settingsAccent,
                    containerColor = settingsAccent,
                    enabled = !active,
                    variant = GlassVariant.SheetAction,
                    onClick = { onSelectAccount(account.id) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BaAccountActionButton(
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                text = stringResource(R.string.ba_account_management_edit_action),
                onClick = onEditAccount,
            )
            BaAccountActionButton(
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                text = stringResource(R.string.ba_account_management_move_up),
                enabled = canMoveUp,
                onClick = { onMoveAccount(-1) },
            )
            BaAccountActionButton(
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                text = stringResource(R.string.ba_account_management_move_down),
                enabled = canMoveDown,
                onClick = { onMoveAccount(1) },
            )
            BaAccountActionButton(
                modifier = Modifier.weight(1f),
                backdrop = backdrop,
                text = stringResource(R.string.ba_account_management_delete_action),
                textColor = MiuixTheme.colorScheme.error,
                variant = GlassVariant.SheetDangerAction,
                enabled = canDelete,
                onClick = onDeleteRequest,
            )
        }
    }
}

@Composable
private fun BaAccountActionButton(
    backdrop: Backdrop?,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MiuixTheme.colorScheme.primary,
    variant: GlassVariant = GlassVariant.SheetAction,
    enabled: Boolean = true,
) {
    AppLiquidTextButton(
        modifier = modifier,
        backdrop = backdrop,
        text = text,
        textColor = textColor,
        containerColor = textColor,
        enabled = enabled,
        variant = variant,
        minHeight = 30.dp,
        horizontalPadding = 8.dp,
        verticalPadding = 4.dp,
        textMaxLines = 1,
        textOverflow = TextOverflow.Ellipsis,
        textSoftWrap = false,
        textSize = 12.sp,
        onClick = onClick,
    )
}

@Composable
private fun accountServerLabel(serverIndex: Int): String =
    when (serverIndex.coerceIn(0, 2)) {
        0 -> stringResource(R.string.ba_server_cn)
        1 -> stringResource(R.string.ba_server_global)
        else -> stringResource(R.string.ba_server_jp)
    }

private data class BaAccountEditorDraft(
    val editingAccountId: BaAccountId?,
    val displayName: String,
    val nickname: String,
    val friendCode: String,
    val serverIndex: Int,
    val notificationMode: BaAccountNotificationMode,
    val remindersEnabled: Boolean,
    val customReminderSettings: BaGlobalReminderSettings,
)

private fun BaAccountEditorDraft.toInput(): BaAccountProfileInput =
    BaAccountProfileInput(
        serverIndex = serverIndex,
        displayName = displayName,
        nickname = nickname,
        friendCode = friendCode,
        notificationMode = notificationMode,
        remindersEnabled = remindersEnabled,
        customReminderSettings = customReminderSettings,
    )

private fun BaOfficeAccountCardUiState.toEditorDraft(): BaAccountEditorDraft =
    BaAccountEditorDraft(
        editingAccountId = id,
        displayName = displayName,
        nickname = nickname,
        friendCode = friendCode,
        serverIndex = serverIndex,
        notificationMode = notificationMode,
        remindersEnabled = remindersEnabled,
        customReminderSettings = customReminderSettings,
    )

private fun normalizeAccountReminderThresholdInput(input: String): Int? {
    val digits = input.filter { it.isDigit() }.take(3)
    if (digits.isBlank()) return null
    return digits.toIntOrNull()?.coerceIn(0, BA_AP_MAX)
}
