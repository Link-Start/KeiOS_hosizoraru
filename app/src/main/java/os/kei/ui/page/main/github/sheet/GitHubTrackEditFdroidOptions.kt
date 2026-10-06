@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.sheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.feature.github.model.FdroidAntiFeaturePolicy
import os.kei.feature.github.model.FdroidTrustPolicy
import os.kei.feature.github.model.FdroidVersionSelectionMode
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetInputTitle
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionHeader

@Composable
internal fun GitHubTrackEditFdroidOptions(
    backdrop: Backdrop,
    versionSelectionMode: FdroidVersionSelectionMode,
    versionNameRegex: String,
    apkNameRegex: String,
    trustPolicy: FdroidTrustPolicy,
    antiFeaturePolicy: FdroidAntiFeaturePolicy,
    versionSelectionDropdownExpanded: Boolean,
    trustPolicyDropdownExpanded: Boolean,
    antiFeaturePolicyDropdownExpanded: Boolean,
    onVersionSelectionModeChange: (FdroidVersionSelectionMode) -> Unit,
    onVersionNameRegexChange: (String) -> Unit,
    onApkNameRegexChange: (String) -> Unit,
    onTrustPolicyChange: (FdroidTrustPolicy) -> Unit,
    onAntiFeaturePolicyChange: (FdroidAntiFeaturePolicy) -> Unit,
    onVersionSelectionDropdownExpandedChange: (Boolean) -> Unit,
    onTrustPolicyDropdownExpandedChange: (Boolean) -> Unit,
    onAntiFeaturePolicyDropdownExpandedChange: (Boolean) -> Unit,
) {
    val versionSelectionModes = FdroidVersionSelectionMode.entries
    val versionSelectionOptions =
        versionSelectionModes.map { mode -> fdroidVersionSelectionModeLabel(mode) }
    val versionSelectionIndex =
        versionSelectionModes.indexOf(versionSelectionMode).coerceAtLeast(0)
    val trustPolicies = FdroidTrustPolicy.entries
    val trustPolicyOptions = trustPolicies.map { policy -> fdroidTrustPolicyLabel(policy) }
    val trustPolicyIndex = trustPolicies.indexOf(trustPolicy).coerceAtLeast(0)
    val antiFeaturePolicies =
        listOf(
            FdroidAntiFeaturePolicy.ShowAndWarn,
            FdroidAntiFeaturePolicy.HideTracking,
            FdroidAntiFeaturePolicy.HideSecurityRisk,
        )
    val antiFeaturePolicyOptions =
        antiFeaturePolicies.map { policy -> fdroidAntiFeaturePolicyLabel(policy) }
    val antiFeaturePolicyIndex =
        antiFeaturePolicies.indexOf(antiFeaturePolicy).coerceAtLeast(0)

    SheetSectionHeader(stringResource(R.string.github_track_sheet_section_fdroid_options))
    SheetSectionCard {
        SheetDescriptionText(
            text = stringResource(R.string.github_track_sheet_summary_fdroid_probe),
        )
        SheetControlRow(
            label = stringResource(R.string.github_track_sheet_label_fdroid_version_selection),
            summary = fdroidVersionSelectionModeSummary(versionSelectionMode),
        ) {
            AppDropdownSelector(
                selectedText =
                    versionSelectionOptions.getOrElse(versionSelectionIndex) {
                        stringResource(
                            R.string.github_track_sheet_fdroid_version_selection_suggested,
                        )
                    },
                options = versionSelectionOptions,
                selectedIndex = versionSelectionIndex,
                expanded = versionSelectionDropdownExpanded,
                onExpandedChange = onVersionSelectionDropdownExpandedChange,
                onSelectedIndexChange = { index ->
                    versionSelectionModes.getOrNull(index)?.let(onVersionSelectionModeChange)
                },
                backdrop = backdrop,
                dropdownItemTextMaxLines = 2,
            )
        }
        if (versionSelectionMode == FdroidVersionSelectionMode.VersionNameRegex) {
            SheetInputTitle(stringResource(R.string.github_track_sheet_input_fdroid_version_regex_title))
            AppLiquidSearchField(
                value = versionNameRegex,
                onValueChange = onVersionNameRegexChange,
                label = stringResource(R.string.github_track_sheet_input_fdroid_version_regex),
                backdrop = backdrop,
                variant = GlassVariant.SheetInput,
                singleLine = true,
            )
            SheetDescriptionText(
                text = stringResource(R.string.github_track_sheet_summary_fdroid_version_regex),
            )
        }
        SheetInputTitle(stringResource(R.string.github_track_sheet_input_fdroid_apk_regex_title))
        AppLiquidSearchField(
            value = apkNameRegex,
            onValueChange = onApkNameRegexChange,
            label = stringResource(R.string.github_track_sheet_input_fdroid_apk_regex),
            backdrop = backdrop,
            variant = GlassVariant.SheetInput,
            singleLine = true,
        )
        SheetDescriptionText(
            text = stringResource(R.string.github_track_sheet_summary_fdroid_apk_regex),
        )
        SheetControlRow(
            label = stringResource(R.string.github_track_sheet_label_fdroid_trust_policy),
            summary = fdroidTrustPolicySummary(trustPolicy),
        ) {
            AppDropdownSelector(
                selectedText =
                    trustPolicyOptions.getOrElse(trustPolicyIndex) {
                        stringResource(R.string.github_track_sheet_fdroid_trust_track_warn)
                    },
                options = trustPolicyOptions,
                selectedIndex = trustPolicyIndex,
                expanded = trustPolicyDropdownExpanded,
                onExpandedChange = onTrustPolicyDropdownExpandedChange,
                onSelectedIndexChange = { index ->
                    trustPolicies.getOrNull(index)?.let(onTrustPolicyChange)
                },
                backdrop = backdrop,
                dropdownItemTextMaxLines = 2,
            )
        }
        SheetControlRow(
            label = stringResource(R.string.github_track_sheet_label_fdroid_antifeature_policy),
            summary = fdroidAntiFeaturePolicySummary(antiFeaturePolicy),
        ) {
            AppDropdownSelector(
                selectedText =
                    antiFeaturePolicyOptions.getOrElse(antiFeaturePolicyIndex) {
                        stringResource(R.string.github_track_sheet_fdroid_antifeature_show_warn)
                    },
                options = antiFeaturePolicyOptions,
                selectedIndex = antiFeaturePolicyIndex,
                expanded = antiFeaturePolicyDropdownExpanded,
                onExpandedChange = onAntiFeaturePolicyDropdownExpandedChange,
                onSelectedIndexChange = { index ->
                    antiFeaturePolicies.getOrNull(index)?.let(onAntiFeaturePolicyChange)
                },
                backdrop = backdrop,
                dropdownItemTextMaxLines = 2,
                anchorTextOverflow = TextOverflow.Ellipsis,
            )
        }
    }
}
