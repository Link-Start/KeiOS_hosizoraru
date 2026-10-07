@file:Suppress("FunctionName")
package os.kei.ui.page.main.student.model3d

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.os.*
import os.kei.ui.page.main.student.section.gallery.GuideWebMemoryLobbyHeader
import os.kei.ui.page.main.student.section.gallery.GuideWebMemoryLobbyScene
import os.kei.ui.page.main.student.section.gallery.GuideWebMemoryLobbyStatus
import os.kei.ui.page.main.widget.chrome.*
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton

@Composable
internal fun GuideModel3dScreen(resource: BaModel3dResource, onDismiss: () -> Unit, onControlsVisible: (Boolean) -> Unit) {
    var file by rememberSaveable(resource.contentId) { mutableStateOf(resource.defaultFile) }
    val model = resource.models.firstOrNull { it.file == file } ?: resource.models.first()
    var playing by rememberSaveable(resource.contentId) { mutableStateOf(true) }
    var controlsVisible by rememberSaveable(resource.contentId) { mutableStateOf(true) }
    var actions by remember(resource.contentId) { mutableStateOf(emptyList<String>()) }
    var selected by remember(resource.contentId) { mutableStateOf("") }
    var durations by remember(resource.contentId) { mutableStateOf(emptyList<Float>()) }
    var ready by remember { mutableStateOf(false) }
    val position = remember { mutableFloatStateOf(0f) }
    val duration = remember { mutableFloatStateOf(0f) }
    var seekRequest by remember { mutableStateOf(0 to 0f) }
    var scrubbing by remember { mutableStateOf(false) }
    var toolsVisible by rememberSaveable { mutableStateOf(false) }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var loop by rememberSaveable { mutableStateOf(true) }
    var outline by rememberSaveable { mutableStateOf(true) }
    var outlineWidth by rememberSaveable { mutableFloatStateOf(0.25f) }
    var actionRequest by remember { mutableStateOf(0 to "") }
    var reset by remember { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible) { onControlsVisible(controlsVisible) }
    BackHandler(!controlsVisible) { controlsVisible = true }
    GuideWebMemoryLobbyScene(
        controlsVisible = controlsVisible,
        onShowControls = { controlsVisible = true },
        header = { backdrop ->
            Box(Modifier.fillMaxWidth()) {
                GuideWebMemoryLobbyHeader(backdrop, onDismiss)
                Box(Modifier.align(Alignment.TopEnd).widthIn(max = 180.dp)) {
                    ModelPicker(backdrop, model.label, resource.models.map { it.label }, resource.models.indexOf(model)) { i ->
                        resource.models.getOrNull(i)?.let { file = it.file }
                    }
                }
            }
        },
        controls = { backdrop ->
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (actions.isNotEmpty()) GuideModel3dTimeline(backdrop, position, duration, ready,
                onScrubbing = { scrubbing = it }, onSeek = { seekRequest = seekRequest.first + 1 to it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppChromeTokens.liquidToolbarGroupSpacing), verticalAlignment = Alignment.CenterVertically) {
                LiquidToolbar(backdrop = backdrop, actions = listOf(
                    LiquidToolbarAction(if (playing) appLucidePauseIcon() else appLucidePlayIcon(), stringResource(if (playing) R.string.guide_action_pause else R.string.guide_action_play), onClick = { playing = !playing }, enabled = actions.isNotEmpty()),
                    LiquidToolbarAction(appLucideUndoIcon(), stringResource(R.string.guide_gallery_dynamic_lobby_reset_view), onClick = { reset++ }),
                ))
                Box(Modifier.weight(1f)) {
                    val labels = remember(actions, durations) { actions.mapIndexed { i, action -> "$action · ${"%.2f".format(durations.getOrElse(i) { 0f })}s" } }
                    ModelPicker(backdrop, stringResource(R.string.guide_gallery_dynamic_lobby_actions), labels, actions.indexOf(selected)) { i ->
                        actions.getOrNull(i)?.let { actionRequest = actionRequest.first + 1 to it }
                    }
                }
                LiquidToolbar(backdrop = backdrop, actions = listOf(
                    LiquidToolbarAction(appLucideConfigIcon(), stringResource(R.string.guide_model_3d_tools), onClick = { toolsVisible = true }),
                    LiquidToolbarAction(appLucideFullscreenIcon(), stringResource(R.string.guide_gallery_dynamic_lobby_hide_controls), onClick = { controlsVisible = false }),
                ))
            }
          }
        },
    ) { viewport ->
        Box(viewport) {
            GuideModel3dPlayer(resource, model, playing, actionRequest, reset, retry,
                options = BaModel3dOptions(speed, loop, outline, outlineWidth, scrubbing), seekRequest = seekRequest,
                pollProgress = controlsVisible,
                onState = { state ->
                    actions = state.actions; durations = state.durations; selected = state.selected; ready = state.ready
                    position.floatValue = state.position; duration.floatValue = state.duration
                    if (state.ended) playing = false
                },
                onError = { failed = it }, modifier = Modifier.fillMaxSize())
            if (failed) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GuideWebMemoryLobbyStatus(stringResource(R.string.guide_model_3d_failed))
                    AppStandaloneLiquidTextButton(text = stringResource(R.string.guide_action_retry),
                        onClick = { retry++; failed = false }, variant = GlassVariant.SheetAction)
                }
            }
        }
    }
    GuideModel3dToolsSheet(toolsVisible, { toolsVisible = false }, actions.isNotEmpty(), speed, loop, outline, outlineWidth,
        onSpeed = { speed = it }, onLoop = { loop = it }, onOutline = { outline = it }, onOutlineWidth = { outlineWidth = it },
        onRetry = { retry++; failed = false; toolsVisible = false })
}

@Composable
private fun ModelPicker(backdrop: Backdrop, label: String, options: List<String>, index: Int, onSelect: (Int) -> Unit) {
    var expanded by remember(options) { mutableStateOf(false) }
    AppDropdownSelector(
        selectedText = label, options = options, selectedIndex = index, expanded = expanded,
        onExpandedChange = { expanded = it }, onSelectedIndexChange = onSelect,
        modifier = Modifier.fillMaxWidth(), backdrop = backdrop, variant = GlassVariant.Bar,
        minHeight = AppChromeTokens.liquidActionBarOuterHeight, anchorFillMaxWidth = true,
        anchorAlignment = Alignment.Center, enabled = options.isNotEmpty(), popupMaxHeight = 360.dp,
        anchorContent = { enabled, onClick -> LiquidToolbarTextButton(backdrop = backdrop, text = label, onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) },
    )
}
