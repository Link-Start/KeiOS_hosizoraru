package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideHeartIcon
import os.kei.ui.page.main.os.appLucideLayersIcon
import os.kei.ui.page.main.os.appLucideMailIcon
import os.kei.ui.page.main.os.appLucideMediaIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucidePlayIcon
import os.kei.ui.page.main.os.appLucideSearchIcon

@Composable
internal fun V2LiquidGlassSampleContent(
    page: V2SamplePage,
    active: Boolean,
    rootBackdrop: Backdrop,
    onScrollInProgressChange: (Boolean) -> Unit,
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
        V2SamplePage.Surfaces -> V2SurfacesPage(rootBackdrop, modifier, onScrollInProgressChange)
        V2SamplePage.Controls -> V2ControlsPage(rootBackdrop, modifier, onScrollInProgressChange)
        V2SamplePage.Inputs -> V2InputsPage(rootBackdrop, modifier, onScrollInProgressChange)
        V2SamplePage.Navigation -> V2NavigationPage(
            rootBackdrop,
            modifier,
            onScrollInProgressChange
        )

        V2SamplePage.Scenarios -> V2ScenariosPage(rootBackdrop, modifier, onScrollInProgressChange)
    }
}

@Composable
private fun V2SurfacesPage(
    backdrop: Backdrop,
    modifier: Modifier,
    onScrollInProgressChange: (Boolean) -> Unit
) {
    V2ActiveSampleColumn(modifier, onScrollInProgressChange) { tierFor ->
        item {
            V2LiquidReferenceStage(
                title = stringResource(R.string.debug_v2_liquid_reference_title),
                subtitle = stringResource(R.string.debug_v2_liquid_section_surfaces_subtitle),
                parentBackdrop = backdrop,
                stageKind = V2ReferenceStageKind.Media,
                renderTier = tierFor(0)
            ) { scope ->
                V2KyantHeroSample(
                    backdrop = scope.exportedBackdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                )
            }
        }
        item {
            V2LiquidReferenceStage(
                title = stringResource(R.string.debug_v2_liquid_surface_nested_title),
                subtitle = stringResource(R.string.debug_v2_liquid_surface_nested_body),
                parentBackdrop = backdrop,
                stageKind = V2ReferenceStageKind.Controls,
                stageHeight = 380.dp,
                renderTier = tierFor(1)
            ) { scope ->
                V2NestedExportedBackdropSample(
                    backdrop = scope.exportedBackdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun V2ControlsPage(
    backdrop: Backdrop,
    modifier: Modifier,
    onScrollInProgressChange: (Boolean) -> Unit
) {
    var checked by remember { mutableStateOf(true) }
    var segment by remember { mutableIntStateOf(1) }
    val palette = rememberV2LiquidGlassPalette()
    val segmentItems = listOf(
        stringResource(R.string.debug_v2_liquid_segment_clear),
        stringResource(R.string.debug_v2_liquid_segment_balanced),
        stringResource(R.string.debug_v2_liquid_segment_deep)
    )

    V2ActiveSampleColumn(modifier, onScrollInProgressChange) { tierFor ->
        item {
            V2LiquidReferenceStage(
                title = stringResource(R.string.debug_v2_liquid_section_controls_title),
                subtitle = stringResource(R.string.debug_v2_liquid_reference_subtitle),
                parentBackdrop = backdrop,
                stageKind = V2ReferenceStageKind.Controls,
                renderTier = tierFor(0)
            ) { scope ->
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    V2KyantHeroSample(
                        backdrop = scope.exportedBackdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        V2GlassStatusCapsule(
                            label = stringResource(R.string.debug_v2_liquid_switch_label),
                            backdrop = scope.exportedBackdrop,
                            tint = palette.success.copy(alpha = 0.14f)
                        )
                        V2LiquidToggle(
                            checked = checked,
                            onCheckedChange = { checked = it },
                            backdrop = scope.exportedBackdrop
                        )
                    }
                }
            }
        }
        item {
            V2SampleSection(
                title = stringResource(R.string.debug_v2_liquid_sandbox_title),
                subtitle = stringResource(R.string.debug_v2_liquid_sandbox_body),
                backdrop = backdrop
            ) {
                V2GlassSegmentedControl(
                    items = segmentItems,
                    selectedIndex = segment,
                    onSelectedIndexChange = { segment = it },
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth()
                )
                V2LiquidParameterPanel(backdrop)
                V2GlassActionGroup(
                    actions = rememberControlActions(backdrop),
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun V2InputsPage(
    backdrop: Backdrop,
    modifier: Modifier,
    onScrollInProgressChange: (Boolean) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var dropdownIndex by remember { mutableIntStateOf(1) }
    var showSheet by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val dropdownItems = rememberDropdownItems()

    Box(modifier = modifier) {
        V2ActiveSampleColumn(Modifier.fillMaxSize(), onScrollInProgressChange) { tierFor ->
            item {
                V2LiquidReferenceStage(
                    title = stringResource(R.string.debug_v2_liquid_section_inputs_title),
                    subtitle = stringResource(R.string.debug_v2_liquid_section_inputs_subtitle),
                    parentBackdrop = backdrop,
                    stageKind = V2ReferenceStageKind.HomeScreen,
                    renderTier = tierFor(0)
                ) { scope ->
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        V2GlassSearchField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = stringResource(R.string.debug_v2_liquid_inputs_search_placeholder),
                            backdrop = scope.exportedBackdrop,
                            leadingIcon = appLucideSearchIcon(),
                            trailingIcon = appLucideMoreIcon(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        V2GlassDropdown(
                            label = stringResource(R.string.debug_v2_liquid_inputs_dropdown_label),
                            items = dropdownItems,
                            selectedIndex = dropdownIndex,
                            onSelectedIndexChange = { dropdownIndex = it },
                            backdrop = scope.exportedBackdrop,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item {
                V2SampleSection(
                    title = stringResource(R.string.debug_v2_liquid_section_inputs_title),
                    subtitle = stringResource(R.string.debug_v2_liquid_inputs_sheet_body),
                    backdrop = backdrop
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        V2LiquidButton(
                            text = stringResource(R.string.debug_v2_liquid_inputs_sheet_button),
                            icon = appLucideLayersIcon(),
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f),
                            onClick = { showSheet = true }
                        )
                        V2LiquidButton(
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
    modifier: Modifier,
    onScrollInProgressChange: (Boolean) -> Unit
) {
    V2ActiveSampleColumn(modifier, onScrollInProgressChange) { tierFor ->
        item {
            V2LiquidReferenceStage(
                title = stringResource(R.string.debug_v2_liquid_section_navigation_title),
                subtitle = stringResource(R.string.debug_v2_liquid_section_navigation_subtitle),
                parentBackdrop = backdrop,
                stageKind = V2ReferenceStageKind.HomeScreen,
                renderTier = tierFor(0)
            ) { scope ->
                V2LiquidTabBarShowcase(
                    backdrop = scope.exportedBackdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
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
                V2LiquidActionStack(
                    backdrop = backdrop,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun V2ScenariosPage(
    backdrop: Backdrop,
    modifier: Modifier,
    onScrollInProgressChange: (Boolean) -> Unit
) {
    var corner by remember { mutableFloatStateOf(0.54f) }
    var blur by remember { mutableFloatStateOf(0.70f) }
    var lens by remember { mutableFloatStateOf(0.64f) }
    var tint by remember { mutableFloatStateOf(0.34f) }
    var depth by remember { mutableStateOf(true) }
    val palette = rememberV2LiquidGlassPalette()

    V2ActiveSampleColumn(modifier, onScrollInProgressChange) { tierFor ->
        item {
            V2LiquidReferenceStage(
                title = stringResource(R.string.debug_v2_liquid_phone_title),
                subtitle = stringResource(R.string.debug_v2_liquid_phone_subtitle),
                parentBackdrop = backdrop,
                stageKind = V2ReferenceStageKind.HomeScreen,
                stageHeight = 440.dp,
                renderTier = tierFor(0),
                backdropContent = {
                    V2ReferenceBackdropArt(
                        kind = V2ReferenceStageKind.HomeScreen,
                        modifier = Modifier.matchParentSize()
                    )
                    V2PhoneMockBackdropContent(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 18.dp)
                    )
                }
            ) { scope ->
                V2LiquidPhoneMock(
                    backdrop = scope.exportedBackdrop,
                    drawWallpaper = false,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 18.dp)
                )
            }
        }
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
                V2BaToolbarMock(backdrop)
            }
        }
        item {
            V2SampleSection(
                title = stringResource(R.string.debug_v2_liquid_sandbox_title),
                subtitle = stringResource(R.string.debug_v2_liquid_sandbox_body),
                backdrop = backdrop
            ) {
                V2SandboxPreview(corner, blur, lens, tint, depth, backdrop)
                V2SliderLine(
                    stringResource(R.string.debug_v2_liquid_sandbox_corner),
                    corner,
                    { corner = it },
                    backdrop
                )
                V2SliderLine(
                    stringResource(R.string.debug_v2_liquid_sandbox_blur),
                    blur,
                    { blur = it },
                    backdrop
                )
                V2SliderLine(
                    stringResource(R.string.debug_v2_liquid_sandbox_lens),
                    lens,
                    { lens = it },
                    backdrop
                )
                V2SliderLine(
                    stringResource(R.string.debug_v2_liquid_sandbox_tint),
                    tint,
                    { tint = it },
                    backdrop
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    V2SampleLabel(stringResource(R.string.debug_v2_liquid_sandbox_depth))
                    V2LiquidToggle(
                        checked = depth,
                        onCheckedChange = { depth = it },
                        backdrop = backdrop
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberControlActions(backdrop: Backdrop): List<V2GlassActionItem> {
    val playLabel = stringResource(R.string.debug_v2_liquid_action_play)
    val downloadLabel = stringResource(R.string.debug_v2_liquid_action_download)
    val favoriteLabel = stringResource(R.string.debug_v2_liquid_action_favorite)
    val playIcon = appLucidePlayIcon()
    val downloadIcon = appLucideDownloadIcon()
    val favoriteIcon = appLucideHeartIcon()
    return remember(
        backdrop,
        playLabel,
        downloadLabel,
        favoriteLabel,
        playIcon,
        downloadIcon,
        favoriteIcon
    ) {
        listOf(
            V2GlassActionItem(
                playLabel,
                playIcon,
                V2GlassRole.Success,
                selected = true,
                onClick = {}),
            V2GlassActionItem(
                downloadLabel,
                downloadIcon,
                V2GlassRole.Accent,
                loading = true,
                onClick = {}),
            V2GlassActionItem(favoriteLabel, favoriteIcon, V2GlassRole.Danger, onClick = {})
        )
    }
}

@Composable
private fun rememberDropdownItems(): List<V2GlassDropdownItem> {
    val compactLabel = stringResource(R.string.debug_v2_liquid_inputs_dropdown_compact)
    val normalLabel = stringResource(R.string.debug_v2_liquid_inputs_dropdown_normal)
    val expandedLabel = stringResource(R.string.debug_v2_liquid_inputs_dropdown_expanded)
    val fastLabel = stringResource(R.string.debug_v2_liquid_dropdown_badge_fast)
    val lockedLabel = stringResource(R.string.debug_v2_liquid_dropdown_badge_locked)
    val layersIcon = appLucideLayersIcon()
    return remember(compactLabel, normalLabel, expandedLabel, fastLabel, lockedLabel, layersIcon) {
        listOf(
            V2GlassDropdownItem(label = compactLabel, trailingText = fastLabel),
            V2GlassDropdownItem(label = normalLabel, leadingIcon = layersIcon),
            V2GlassDropdownItem(label = expandedLabel, enabled = false, trailingText = lockedLabel)
        )
    }
}

@Composable
private fun V2BaToolbarMock(backdrop: Backdrop) {
    val palette = rememberV2LiquidGlassPalette()
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
            V2LiquidButton(
                text = stringResource(R.string.debug_v2_liquid_nav_controls),
                backdrop = scope.childBackdrop,
                modifier = Modifier.weight(1f),
                onClick = {}
            )
            V2LiquidButton(
                text = stringResource(R.string.debug_v2_liquid_nav_media),
                backdrop = scope.childBackdrop,
                modifier = Modifier.weight(1f),
                tint = palette.accent.copy(alpha = 0.18f),
                selected = true,
                onClick = {}
            )
        },
        trailing = { scope ->
            V2GlassIconButton(
                icon = appLucideConfigIcon(),
                contentDescription = stringResource(R.string.debug_v2_liquid_nav_tools),
                backdrop = scope.childBackdrop,
                size = V2GlassControlSize.Compact,
                onClick = {}
            )
        }
    )
}
