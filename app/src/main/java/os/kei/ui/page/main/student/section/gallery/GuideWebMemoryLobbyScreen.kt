@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.section.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.ui.page.main.student.BaGuideWebMemoryLobby
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun GuideWebMemoryLobbyScreen(
    resource: BaGuideWebMemoryLobby,
    onDismiss: () -> Unit,
    onOpenSource: () -> Unit,
    onControlsVisibleChange: (Boolean) -> Unit,
) {
    val viewerUrl = resource.viewerUrl
    var playing by rememberSaveable(viewerUrl) { mutableStateOf(true) }
    var retry by remember(viewerUrl) { mutableIntStateOf(0) }
    var actionRequest by remember(viewerUrl) { mutableIntStateOf(0) }
    var stateRequest by remember(resource) { mutableIntStateOf(0) }
    var actions by remember(resource, retry) { mutableStateOf(emptyList<String>()) }
    var selectedAction by remember(resource, retry) { mutableStateOf("") }
    var controlsVisible by rememberSaveable(viewerUrl) { mutableStateOf(true) }
    LaunchedEffect(controlsVisible) { onControlsVisibleChange(controlsVisible) }
    BackHandler(enabled = !controlsVisible) { controlsVisible = true }

    GuideWebMemoryLobbyScene(
        controlsVisible = controlsVisible,
        onShowControls = { controlsVisible = true },
        header = { backdrop -> GuideWebMemoryLobbyHeader(backdrop, onDismiss) },
        controls = { backdrop ->
            GuideWebMemoryLobbyControls(
                backdrop = backdrop,
                playing = playing,
                actions = actions,
                selectedAction = selectedAction,
                onTogglePlayback = { playing = !playing },
                onSelectAction = { selectedAction = it; actionRequest += 1 },
                onRetry = { playing = true; retry += 1 },
                onOpenSource = onOpenSource,
                onHideControls = { controlsVisible = false },
                onActionsRequested = { stateRequest += 1 },
            )
        },
    ) { viewport ->
        GuideWebMemoryLobbyPlayer(
            resource = resource,
            playing = playing,
            retryToken = retry,
            stateRequest = stateRequest,
            actionRequest = actionRequest,
            selectedAction = selectedAction,
            onActionsAvailable = { available, current -> actions = available; selectedAction = current },
            modifier = viewport,
        )
    }
}

/** Chrome overlays the media; neither safe insets nor toolbar heights reduce its viewport. */
@Composable
internal fun GuideWebMemoryLobbyScene(
    header: @Composable (Backdrop) -> Unit,
    controls: @Composable (Backdrop) -> Unit,
    modifier: Modifier = Modifier,
    controlsVisible: Boolean = true,
    onShowControls: () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    val mediaBackdrop = rememberLayerBackdrop()
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(Color.Black)
            .testTag(GuideWebMemoryLobbySceneTag)
            .semantics { testTagsAsResourceId = true },
    ) {
        // The Wiki uses cover scaling. Preserve its cinematic framing in wide windows,
        // while using the full window height, including the area beneath floating chrome.
        val viewport = if (maxWidth > maxHeight * (16f / 9f)) {
            Modifier.fillMaxHeight().aspectRatio(16f / 9f)
        } else {
            Modifier.fillMaxSize()
        }
        Box(
            Modifier.fillMaxSize().layerBackdrop(mediaBackdrop),
            contentAlignment = Alignment.Center,
        ) {
            content(viewport.testTag(GuideWebMemoryLobbyViewportTag))
        }
        if (controlsVisible) {
            // A short transparent ramp protects white system icons over bright animation.
            // It overlays the image instead of reserving a status-bar strip.
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                Modifier.fillMaxWidth().height(statusBarHeight + 20.dp).background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent)),
                ),
            )
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(
                        horizontal = AppChromeTokens.pageHorizontalPadding,
                        vertical = AppChromeTokens.topBarChromeTopPadding,
                    ).testTag(GuideWebMemoryLobbyHeaderTag),
                ) { header(mediaBackdrop) }
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(
                        horizontal = AppChromeTokens.pageHorizontalPadding,
                        vertical = AppChromeTokens.pageBottomInsetExtra,
                    ).widthIn(max = 460.dp).fillMaxWidth().testTag(GuideWebMemoryLobbyControlsTag),
                ) { controls(mediaBackdrop) }
            }
        } else {
            // Keep the media subtree mounted and playing. Only this transparent tap target
            // replaces chrome, including an accessible way to reveal it again.
            val showControls = stringResource(R.string.guide_gallery_dynamic_lobby_show_controls)
            Box(
                Modifier.fillMaxSize().testTag(GuideWebMemoryLobbyRestoreTag)
                    .semantics { contentDescription = showControls }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = showControls,
                        onClick = onShowControls,
                    ),
            )
        }
    }
}

@Composable
internal fun GuideWebMemoryLobbyStatus(message: String) {
    AppSurfaceCard(
        modifier = Modifier.padding(AppChromeTokens.pageHorizontalPadding).widthIn(max = 320.dp),
        showIndication = false,
    ) {
        Text(
            message,
            modifier = Modifier.padding(CardLayoutRhythm.cardContentPadding),
            fontSize = AppTypographyTokens.Body.fontSize,
            lineHeight = AppTypographyTokens.Body.lineHeight,
        )
    }
}

internal const val GuideWebMemoryLobbySceneTag = "guide_web_lobby_scene"
internal const val GuideWebMemoryLobbyViewportTag = "guide_web_lobby_viewport"
internal const val GuideWebMemoryLobbyHeaderTag = "guide_web_lobby_header"
internal const val GuideWebMemoryLobbyControlsTag = "guide_web_lobby_controls"
internal const val GuideWebMemoryLobbyRestoreTag = "guide_web_lobby_restore_controls"
