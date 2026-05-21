@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.section

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.os.appLucideTimeIcon
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.settings.support.SettingsToggleItem

@Composable
internal fun SettingsAnimationSection(
    state: SettingsAnimationSectionState,
    actions: SettingsAnimationSectionActions,
    enabledCardColor: Color,
    disabledCardColor: Color,
) {
    val presentation = deriveAnimationPresentation(state)
    SettingsGroupCard(
        header = stringResource(R.string.settings_group_animation_header),
        title = stringResource(R.string.settings_group_animation_title),
        sectionIcon = appLucideTimeIcon(),
        containerColor = settingsSectionContainerColor(presentation, enabledCardColor, disabledCardColor),
    ) {
        SettingsToggleItem(
            title = stringResource(R.string.settings_transition_animations_title),
            summary =
                if (state.transitionAnimationsEnabled) {
                    stringResource(R.string.settings_transition_animations_summary_enabled)
                } else {
                    stringResource(R.string.settings_transition_animations_summary_disabled)
                },
            checked = state.transitionAnimationsEnabled,
            onCheckedChange = actions.onTransitionAnimationsChanged,
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_transition_animations_scope),
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_predictive_back_animations_title),
            summary =
                if (state.predictiveBackAnimationsEnabled) {
                    stringResource(R.string.settings_predictive_back_animations_summary_enabled)
                } else {
                    stringResource(R.string.settings_predictive_back_animations_summary_disabled)
                },
            checked = state.predictiveBackAnimationsEnabled,
            onCheckedChange = actions.onPredictiveBackAnimationsChanged,
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_predictive_back_animations_scope),
        )
    }
}
