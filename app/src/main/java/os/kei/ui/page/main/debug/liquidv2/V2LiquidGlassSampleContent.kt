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
import os.kei.ui.page.main.os.appLucideMailIcon
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
        V2LightPageShell(
            page = page,
            modifier = modifier
        )
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
    val playLabel = stringResource(R.string.debug_v2_liquid_action_play)
    val downloadLabel = stringResource(R.string.debug_v2_liquid_action_download)
    val favoriteLabel = stringResource(R.string.debug_v2_liquid_action_favorite)
    val clearLabel = stringResource(R.string.debug_v2_liquid_segment_clear)
    val balancedLabel = stringResource(R.string.debug_v2_liquid_segment_balanced)
    val deepLabel = stringResource(R.string.debug_v2_liquid_segment_deep)
    val playIcon = appLucidePlayIcon()
    val downloadIcon = appLucideDownloadIcon()
    val favoriteIcon = appLucideHeartIcon()
    val actionItems =
        remember(playLabel, downloadLabel, favoriteLabel, playIcon, downloadIcon, favoriteIcon) {
            listOf(
                V2GlassActionItem(
                    label = playLabel,
                    icon = playIcon,
                    role = V2GlassRole.Success,
                    selected = true,
                    onClick = {}
                ),
                V2GlassActionItem(
                    label = downloadLabel,
                    icon = downloadIcon,
                    role = V2GlassRole.Accent,
                    loading = true,
                    onClick = {}
                ),
                V2GlassActionItem(
                    label = favoriteLabel,
                    icon = favoriteIcon,
                    role = V2GlassRole.Danger,
                    onClick = {}
                )
            )
        }
    val segmentItems = remember(clearLabel, balancedLabel, deepLabel) {
        listOf(clearLabel, balancedLabel, deepLabel)
    }

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
                        leadingIcon = appLucideConfirmIcon(),
                        trailingIcon = appLucideMoreIcon(),
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f),
                        tint = palette.accent.copy(alpha = 0.18f),
                        selected = true,
                        role = V2GlassRole.Accent,
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
                        icon = playIcon,
                        contentDescription = playLabel,
                        backdrop = backdrop,
                        tint = palette.success.copy(alpha = 0.18f),
                        onClick = {}
                    )
                    V2GlassIconButton(
                        icon = downloadIcon,
                        contentDescription = downloadLabel,
                        backdrop = backdrop,
                        tint = palette.accent.copy(alpha = 0.18f),
                        onClick = {}
                    )
                    V2GlassIconButton(
                        icon = favoriteIcon,
                        contentDescription = favoriteLabel,
                        backdrop = backdrop,
                        tint = Color(0xFFFF5C8A).copy(alpha = 0.18f),
                        onClick = {}
                    )
                    V2GlassButton(
                        text = stringResource(R.string.debug_v2_liquid_button_disabled),
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f),
                        role = V2GlassRole.Danger,
                        enabled = false,
                        onClick = {}
                    )
                }
                V2GlassActionGroup(
                    actions = actionItems,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth()
                )
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
                    items = segmentItems,
                    selectedIndex = segment,
                    onSelectedIndexChange = { segment = it },
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    selectionStyle = V2GlassSelectionStyle.Indicator
                )
                V2SliderLine(
                    label = stringResource(R.string.debug_v2_liquid_slider_volume),
                    value = volume,
                    onValueChange = { volume = it },
                    backdrop = backdrop,
                    steps = 4
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
    val compactLabel = stringResource(R.string.debug_v2_liquid_inputs_dropdown_compact)
    val normalLabel = stringResource(R.string.debug_v2_liquid_inputs_dropdown_normal)
    val expandedLabel = stringResource(R.string.debug_v2_liquid_inputs_dropdown_expanded)
    val fastLabel = stringResource(R.string.debug_v2_liquid_dropdown_badge_fast)
    val lockedLabel = stringResource(R.string.debug_v2_liquid_dropdown_badge_locked)
    val layersIcon = appLucideLayersIcon()
    val dropdownItems =
        remember(compactLabel, normalLabel, expandedLabel, fastLabel, lockedLabel, layersIcon) {
            listOf(
                V2GlassDropdownItem(
                    label = compactLabel,
                    trailingText = fastLabel
                ),
                V2GlassDropdownItem(
                    label = normalLabel,
                    leadingIcon = layersIcon
                ),
                V2GlassDropdownItem(
                    label = expandedLabel,
                    enabled = false,
                    trailingText = lockedLabel
                )
            )
        }

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
                        trailingIcon = appLucideMoreIcon(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    V2GlassDropdown(
                        label = stringResource(R.string.debug_v2_liquid_inputs_dropdown_label),
                        items = dropdownItems,
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
                            icon = layersIcon,
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
    val pullLabel = stringResource(R.string.debug_v2_liquid_command_pull)
    val pinLabel = stringResource(R.string.debug_v2_liquid_command_pin)
    val runLabel = stringResource(R.string.debug_v2_liquid_command_run)
    val homeLabel = stringResource(R.string.debug_v2_liquid_nav_home)
    val mediaLabel = stringResource(R.string.debug_v2_liquid_nav_media)
    val toolsLabel = stringResource(R.string.debug_v2_liquid_nav_tools)
    val statusLabel = stringResource(R.string.debug_v2_liquid_nav_status)
    val downloadIcon = appLucideDownloadIcon()
    val heartIcon = appLucideHeartIcon()
    val playIcon = appLucidePlayIcon()
    val homeIcon = appLucideHomeIcon()
    val musicIcon = appLucideMusicIcon()
    val configIcon = appLucideConfigIcon()
    val listIcon = appLucideListIcon()
    val commandItems = remember(pullLabel, pinLabel, runLabel, downloadIcon, heartIcon, playIcon) {
        listOf(
            V2GlassTabItem(
                label = pullLabel,
                icon = downloadIcon
            ),
            V2GlassTabItem(
                label = pinLabel,
                icon = heartIcon,
                badge = "2"
            ),
            V2GlassTabItem(
                label = runLabel,
                icon = playIcon
            )
        )
    }
    val navItems = remember(
        homeLabel,
        mediaLabel,
        toolsLabel,
        statusLabel,
        homeIcon,
        musicIcon,
        configIcon,
        listIcon
    ) {
        listOf(
            V2GlassTabItem(
                label = homeLabel,
                icon = homeIcon
            ),
            V2GlassTabItem(
                label = mediaLabel,
                icon = musicIcon
            ),
            V2GlassTabItem(
                label = toolsLabel,
                icon = configIcon
            ),
            V2GlassTabItem(
                label = statusLabel,
                icon = listIcon
            )
        )
    }

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
                    items = commandItems,
                    selectedIndex = commandIndex,
                    onSelectedIndexChange = { commandIndex = it },
                    backdrop = backdrop,
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
                    items = navItems,
                    selectedIndex = navIndex,
                    onSelectedIndexChange = { navIndex = it },
                    backdrop = backdrop,
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
                V2PerfPanel(
                    pageLabel = stringResource(R.string.debug_v2_liquid_page_scenarios),
                    backdrop = backdrop
                )
                V2GroupStressSample(
                    backdrop = backdrop,
                    title = stringResource(R.string.debug_v2_liquid_group_stress_title),
                    subtitle = stringResource(R.string.debug_v2_liquid_group_stress_subtitle)
                )
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
                V2GlassToolbar(
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    role = V2GlassRole.Accent,
                    leading = { scope ->
                        V2GlassIconButton(
                            icon = appLucideMailIcon(),
                            contentDescription = stringResource(R.string.debug_v2_liquid_scenario_ba_title),
                            backdrop = scope.childBackdrop,
                            size = V2GlassControlSize.Compact,
                            tint = palette.accent.copy(alpha = 0.16f),
                            onClick = {}
                        )
                    },
                    content = { scope ->
                        V2GlassButton(
                            text = stringResource(R.string.debug_v2_liquid_nav_controls),
                            backdrop = scope.childBackdrop,
                            size = V2GlassControlSize.Compact,
                            modifier = Modifier.weight(1f),
                            onClick = {}
                        )
                        V2GlassButton(
                            text = stringResource(R.string.debug_v2_liquid_nav_media),
                            backdrop = scope.childBackdrop,
                            size = V2GlassControlSize.Compact,
                            modifier = Modifier.weight(1f),
                            selected = true,
                            role = V2GlassRole.Accent,
                            onClick = {}
                        )
                    },
                    trailing = { scope ->
                        V2GlassIconButton(
                            icon = appLucideMoreIcon(),
                            contentDescription = stringResource(R.string.debug_v2_liquid_nav_tools),
                            backdrop = scope.childBackdrop,
                            size = V2GlassControlSize.Compact,
                            onClick = {}
                        )
                    }
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
