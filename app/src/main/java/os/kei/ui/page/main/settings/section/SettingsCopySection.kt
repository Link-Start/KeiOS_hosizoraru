@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.section

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.os.osLucideCopyIcon
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.settings.support.SettingsToggleItem

@Composable
internal fun SettingsCopySection(
    state: SettingsCopySectionState,
    actions: SettingsCopySectionActions,
    enabledCardColor: Color,
    disabledCardColor: Color,
) {
    val presentation = deriveCopyPresentation(state)
    SettingsGroupCard(
        header = stringResource(R.string.settings_group_copy_header),
        title = stringResource(R.string.settings_group_copy_title),
        sectionIcon = osLucideCopyIcon(),
        containerColor = settingsSectionContainerColor(presentation, enabledCardColor, disabledCardColor),
    ) {
        SettingsToggleItem(
            title = stringResource(R.string.settings_copy_capability_title),
            summary =
                if (state.textCopyCapabilityExpanded) {
                    stringResource(R.string.settings_copy_capability_summary_enabled)
                } else {
                    stringResource(R.string.settings_copy_capability_summary_disabled)
                },
            checked = state.textCopyCapabilityExpanded,
            onCheckedChange = actions.onTextCopyCapabilityExpandedChanged,
            infoKey = stringResource(R.string.common_note),
            infoValue =
                if (state.textCopyCapabilityExpanded) {
                    stringResource(R.string.settings_copy_capability_note_enabled)
                } else {
                    stringResource(R.string.settings_copy_capability_note_disabled)
                },
        )
    }
}
