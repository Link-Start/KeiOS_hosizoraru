package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideBranchIcon
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideHeartIcon
import os.kei.ui.page.main.os.appLucideHomeIcon
import os.kei.ui.page.main.os.appLucideLayersIcon
import os.kei.ui.page.main.os.appLucideListIcon
import os.kei.ui.page.main.os.appLucideMediaIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideMusicIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucidePlayIcon
import os.kei.ui.page.main.os.appLucideSearchIcon

@Composable
internal fun V2LiquidGlassSampleContent(
    page: V2SamplePage,
    active: Boolean,
    rootBackdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    if (!active) {
        Box(modifier = modifier)
        return
    }

    when (page) {
        V2SamplePage.Surfaces -> V2SurfacesPage(rootBackdrop, modifier)
        V2SamplePage.Controls -> V2ControlsPage(rootBackdrop, modifier)
        V2SamplePage.Inputs -> V2InputsPage(rootBackdrop, modifier)
        V2SamplePage.Navigation -> V2NavigationPage(rootBackdrop, modifier)
        V2SamplePage.Scenarios -> V2ScenariosPage(rootBackdrop, modifier)
    }
}

@Composable
private fun V2SurfacesPage(
    backdrop: Backdrop,
    modifier: Modifier
) {
    V2SampleColumn(modifier) {
        item {
            V2SampleSection(
                title = stringResource(R.string.debug_v2_liquid_section_surfaces_title),
                subtitle = stringResource(R.string.debug_v2_liquid_section_surfaces_subtitle),
                backdrop = backdrop
            ) {
                V2SampleBadgeRow(backdrop)
                V2BackdropStage(backdrop)
            }
        }
        item {
            V2SampleSection(
                title = stringResource(R.string.debug_v2_liquid_surface_nested_title),
                subtitle = stringResource(R.string.debug_v2_liquid_surface_nested_body),
                backdrop = backdrop
            ) {
                V2NestedExportedBackdropSample(backdrop)
            }
        }
    }
}

@Composable
private fun V2ControlsPage(
    backdrop: Backdrop,
    modifier: Modifier
) {
    var checked by remember { mutableStateOf(true) }
    var volume by remember { mutableFloatStateOf(0.58f) }
    var intensity by remember { mutableFloatStateOf(0.72f) }
    var segment by remember { mutableIntStateOf(1) }
    val palette = rememberV2LiquidGlassPalette()

    V2SampleColumn(modifier) {
        item {
            V2SampleSection(
                title = stringResource(R.string.debug_v2_liquid_section_controls_title),
                subtitle = stringResource(R.string.debug_v2_liquid_section_controls_subtitle),
                backdrop = backdrop
            ) {
                V2SampleLabel(stringResource(R.string.debug_v2_liquid_controls_buttons))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    V2GlassButton(
                        text = stringResource(R.string.debug_v2_liquid_button_primary),
                        icon = appLucideConfirmIcon(),
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f),
                        tint = palette.accent.copy(alpha = 0.18f),
                        onClick = {}
                    )
                    V2GlassButton(
                        text = stringResource(R.string.debug_v2_liquid_button_secondary),
                        icon = appLucideLayersIcon(),
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f),
                        onClick = {}
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    V2GlassIconButton(
                        icon = appLucidePlayIcon(),
                        contentDescription = stringResource(R.string.debug_v2_liquid_action_play),
                        backdrop = backdrop,
                        tint = palette.success.copy(alpha = 0.18f),
                        onClick = {}
                    )
                    V2GlassIconButton(
                        icon = appLucideDownloadIcon(),
                        contentDescription = stringResource(R.string.debug_v2_liquid_action_download),
                        backdrop = backdrop,
                        tint = palette.accent.copy(alpha = 0.18f),
                        onClick = {}
                    )
                    V2GlassIconButton(
                        icon = appLucideHeartIcon(),
                        contentDescription = stringResource(R.string.debug_v2_liquid_action_favorite),
                        backdrop = backdrop,
                        tint = Color(0xFFFF5C8A).copy(alpha = 0.18f),
                        onClick = {}
                    )
                    V2GlassButton(
                        text = stringResource(R.string.debug_v2_liquid_button_disabled),
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f),
                        enabled = false,
                        onClick = {}
                    )
                }
            }
        }
        item {
            V2SampleSection(
                title = stringResource(R.string.debug_v2_liquid_switch_label),
                subtitle = stringResource(R.string.debug_v2_liquid_segment_density),
                backdrop = backdrop
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    V2GlassStatusCapsule(
                        label = stringResource(R.string.debug_v2_liquid_switch_label),
                        backdrop = backdrop,
                        tint = palette.accent.copy(alpha = 0.16f)
                    )
                    V2GlassSwitch(
                        checked = checked,
                        onCheckedChange = { checked = it },
                        backdrop = backdrop
                    )
                }
                V2GlassSegmentedControl(
                    items = listOf(
                        stringResource(R.string.debug_v2_liquid_segment_clear),
                        stringResource(R.string.debug_v2_liquid_segment_balanced),
                        stringResource(R.string.debug_v2_liquid_segment_deep)
                    ),
                    selectedIndex = segment,
                    onSelectedIndexChange = { segment = it },
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth()
                )
                V2SliderLine(
                    label = stringResource(R.string.debug_v2_liquid_slider_volume),
                    value = volume,
                    onValueChange = { volume = it },
                    backdrop = backdrop
                )
                V2SliderLine(
                    label = stringResource(R.string.debug_v2_liquid_slider_intensity),
                    value = intensity,
                    onValueChange = { intensity = it },
                    backdrop = backdrop
                )
            }
        }
    }
}

@Composable
private fun V2InputsPage(
    backdrop: Backdrop,
    modifier: Modifier
) {
    var search by remember { mutableStateOf("") }
    var dropdownIndex by remember { mutableIntStateOf(1) }
    var showSheet by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        V2SampleColumn(Modifier.fillMaxSize()) {
            item {
                V2SampleSection(
                    title = stringResource(R.string.debug_v2_liquid_section_inputs_title),
                    subtitle = stringResource(R.string.debug_v2_liquid_section_inputs_subtitle),
                    backdrop = backdrop
                ) {
                    V2GlassSearchField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = stringResource(R.string.debug_v2_liquid_inputs_search_placeholder),
                        backdrop = backdrop,
                        leadingIcon = appLucideSearchIcon(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    V2GlassDropdown(
                        label = stringResource(R.string.debug_v2_liquid_inputs_dropdown_label),
                        items = listOf(
                            stringResource(R.string.debug_v2_liquid_inputs_dropdown_compact),
                            stringResource(R.string.debug_v2_liquid_inputs_dropdown_normal),
                            stringResource(R.string.debug_v2_liquid_inputs_dropdown_expanded)
                        ),
                        selectedIndex = dropdownIndex,
                        onSelectedIndexChange = { dropdownIndex = it },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        V2GlassButton(
                            text = stringResource(R.string.debug_v2_liquid_inputs_sheet_button),
                            icon = appLucideLayersIcon(),
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f),
                            onClick = { showSheet = true }
                        )
                        V2GlassButton(
                            text = stringResource(R.string.debug_v2_liquid_inputs_dialog_button),
                            icon = appLucideMoreIcon(),
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f),
                            onClick = { showDialog = true }
                        )
                    }
                }
            }
        }

        V2InputSheet(
            visible = showSheet,
            backdrop = backdrop,
            onDismiss = { showSheet = false }
        )
        V2GlassDialog(
            visible = showDialog,
            backdrop = backdrop,
            title = stringResource(R.string.debug_v2_liquid_inputs_dialog_title),
            message = stringResource(R.string.debug_v2_liquid_inputs_dialog_body),
            confirmLabel = stringResource(R.string.common_close),
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun V2NavigationPage(
    backdrop: Backdrop,
    modifier: Modifier
) {
    var navIndex by remember { mutableIntStateOf(0) }
    var commandIndex by remember { mutableIntStateOf(1) }
    val palette = rememberV2LiquidGlassPalette()

    V2SampleColumn(modifier) {
        item {
            V2SampleSection(
                title = stringResource(R.string.debug_v2_liquid_section_navigation_title),
                subtitle = stringResource(R.string.debug_v2_liquid_section_navigation_subtitle),
                backdrop = backdrop
            ) {
                V2SampleLabel(stringResource(R.string.debug_v2_liquid_navigation_actionbar))
                V2GlassActionBar(
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    V2GlassIconButton(
                        icon = appLucideCloseIcon(),
                        contentDescription = stringResource(R.string.common_close),
                        backdrop = backdrop,
                        tint = palette.danger.copy(alpha = 0.12f),
                        onClick = {}
                    )
                    V2GlassIconButton(
                        icon = appLucideSearchIcon(),
                        contentDescription = stringResource(R.string.debug_v2_liquid_nav_tools),
                        backdrop = backdrop,
                        onClick = {}
                    )
                    V2GlassIconButton(
                        icon = appLucideConfirmIcon(),
                        contentDescription = stringResource(R.string.debug_v2_liquid_nav_status),
                        backdrop = backdrop,
                        tint = palette.success.copy(alpha = 0.16f),
                        onClick = {}
                    )
                }
                V2SampleLabel(stringResource(R.string.debug_v2_liquid_navigation_command_dock))
                V2GlassBottomTabs(
                    items = listOf(
                        stringResource(R.string.debug_v2_liquid_command_pull),
                        stringResource(R.string.debug_v2_liquid_command_pin),
                        stringResource(R.string.debug_v2_liquid_command_run)
                    ),
                    selectedIndex = commandIndex,
                    onSelectedIndexChange = { commandIndex = it },
                    backdrop = backdrop,
                    icons = listOf(
                        appLucideDownloadIcon(),
                        appLucideHeartIcon(),
                        appLucidePlayIcon()
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    activeTint = palette.accent.copy(alpha = 0.24f)
                )
            }
        }
        item {
            V2SampleSection(
                title = stringResource(R.string.debug_v2_liquid_navigation_mini_player),
                subtitle = stringResource(R.string.debug_v2_liquid_mini_subtitle),
                backdrop = backdrop
            ) {
                V2MiniPlayerDock(backdrop)
                V2GlassBottomTabs(
                    items = listOf(
                        stringResource(R.string.debug_v2_liquid_nav_home),
                        stringResource(R.string.debug_v2_liquid_nav_media),
                        stringResource(R.string.debug_v2_liquid_nav_tools),
                        stringResource(R.string.debug_v2_liquid_nav_status)
                    ),
                    selectedIndex = navIndex,
                    onSelectedIndexChange = { navIndex = it },
                    backdrop = backdrop,
                    icons = listOf(
                        appLucideHomeIcon(),
                        appLucideMusicIcon(),
                        appLucideConfigIcon(),
                        appLucideListIcon()
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp)
                )
            }
        }
    }
}

@Composable
private fun V2ScenariosPage(
    backdrop: Backdrop,
    modifier: Modifier
) {
    var corner by remember { mutableFloatStateOf(0.54f) }
    var blur by remember { mutableFloatStateOf(0.70f) }
    var lens by remember { mutableFloatStateOf(0.64f) }
    var tint by remember { mutableFloatStateOf(0.34f) }
    var depth by remember { mutableStateOf(true) }
    val palette = rememberV2LiquidGlassPalette()

    V2SampleColumn(modifier) {
        item {
            V2SampleSection(
                title = stringResource(R.string.debug_v2_liquid_section_scenarios_title),
                subtitle = stringResource(R.string.debug_v2_liquid_section_scenarios_subtitle),
                backdrop = backdrop
            ) {
                V2ScenarioActionRow(
                    icon = appLucideBranchIcon(),
                    title = stringResource(R.string.debug_v2_liquid_scenario_github_title),
                    body = stringResource(R.string.debug_v2_liquid_scenario_github_body),
                    meta = stringResource(R.string.debug_v2_liquid_scenario_github_meta),
                    action = stringResource(R.string.debug_v2_liquid_action_download),
                    tint = palette.accent.copy(alpha = 0.18f),
                    backdrop = backdrop
                )
                V2ScenarioActionRow(
                    icon = appLucidePackageIcon(),
                    title = stringResource(R.string.debug_v2_liquid_scenario_os_title),
                    body = stringResource(R.string.debug_v2_liquid_scenario_os_body),
                    meta = stringResource(R.string.debug_v2_liquid_scenario_os_meta),
                    action = stringResource(R.string.debug_v2_liquid_command_run),
                    tint = palette.success.copy(alpha = 0.18f),
                    backdrop = backdrop
                )
                V2ScenarioActionRow(
                    icon = appLucideMediaIcon(),
                    title = stringResource(R.string.debug_v2_liquid_scenario_ba_title),
                    body = stringResource(R.string.debug_v2_liquid_scenario_ba_body),
                    meta = stringResource(R.string.debug_v2_liquid_scenario_ba_meta),
                    action = stringResource(R.string.debug_v2_liquid_action_play),
                    tint = Color(0xFFFF5C8A).copy(alpha = 0.16f),
                    backdrop = backdrop
                )
            }
        }
        item {
            V2SampleSection(
                title = stringResource(R.string.debug_v2_liquid_sandbox_title),
                subtitle = stringResource(R.string.debug_v2_liquid_sandbox_body),
                backdrop = backdrop
            ) {
                V2SandboxPreview(
                    corner = corner,
                    blur = blur,
                    lens = lens,
                    tint = tint,
                    depth = depth,
                    backdrop = backdrop
                )
                V2SliderLine(
                    label = stringResource(R.string.debug_v2_liquid_sandbox_corner),
                    value = corner,
                    onValueChange = { corner = it },
                    backdrop = backdrop
                )
                V2SliderLine(
                    label = stringResource(R.string.debug_v2_liquid_sandbox_blur),
                    value = blur,
                    onValueChange = { blur = it },
                    backdrop = backdrop
                )
                V2SliderLine(
                    label = stringResource(R.string.debug_v2_liquid_sandbox_lens),
                    value = lens,
                    onValueChange = { lens = it },
                    backdrop = backdrop
                )
                V2SliderLine(
                    label = stringResource(R.string.debug_v2_liquid_sandbox_tint),
                    value = tint,
                    onValueChange = { tint = it },
                    backdrop = backdrop
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    V2SampleLabel(stringResource(R.string.debug_v2_liquid_sandbox_depth))
                    V2GlassSwitch(
                        checked = depth,
                        onCheckedChange = { depth = it },
                        backdrop = backdrop
                    )
                }
            }
        }
    }
}
