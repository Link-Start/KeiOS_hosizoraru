package os.kei.ui.page.main.student.section.gallery

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.sheet.SceneBackdropHost
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Protect the full media viewport while real project chrome and menus overlay it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class GuideWebMemoryLobbySceneTest {
    @get:Rule
    val composeRule = createComposeRule()
    private var playerMounts = 0
    private var playerDisposals = 0

    @Test
    fun draggingInImmersionMovesTheCameraWithoutRestoringChromeOrRemountingMedia() {
        val camera = GuideWebMemoryLobbyCamera()
        setScene(camera = camera)
        composeRule.onNodeWithContentDescription(text(R.string.guide_gallery_dynamic_lobby_hide_controls)).performClick()
        composeRule.onNodeWithTag(GuideWebMemoryLobbyRestoreTag).performTouchInput {
            swipe(center, center + Offset(100f, 60f), 200)
        }
        composeRule.onNodeWithTag(GuideWebMemoryLobbyHeaderTag).assertDoesNotExist()
        assertTrue(camera.transform.value.panX > 0f)
        assertTrue(camera.transform.value.panY > 0f)
        assertEquals(1, playerMounts)
        assertEquals(0, playerDisposals)
        composeRule.onNodeWithTag(GuideWebMemoryLobbyRestoreTag).performClick()
        composeRule.onNodeWithTag(GuideWebMemoryLobbyHeaderTag).assertIsDisplayed()
    }

    @Test
    fun cameraMenuZoomAndResetKeepTheViewportAndMediaMounted() {
        val camera = GuideWebMemoryLobbyCamera()
        setScene(camera = camera)
        val viewport = bounds(GuideWebMemoryLobbyViewportTag)
        composeRule.onNodeWithContentDescription(text(R.string.guide_gallery_dynamic_lobby_adjust_view)).performClick()
        composeRule.onNodeWithText(text(R.string.guide_gallery_dynamic_lobby_zoom_in)).performClick()
        assertTrue(camera.transform.value.scale > 1f)
        composeRule.onNodeWithContentDescription(text(R.string.guide_gallery_dynamic_lobby_adjust_view)).performClick()
        composeRule.onNodeWithText(text(R.string.guide_gallery_dynamic_lobby_reset_view)).performClick()
        assertEquals(GuideLobbyCameraTransform(), camera.transform.value)
        assertBoundsEqual(viewport, bounds(GuideWebMemoryLobbyViewportTag))
        assertEquals(1, playerMounts)
        assertEquals(0, playerDisposals)
    }

    @Test
    fun immersionHidesChromeAndTapRestoresItWithoutRemountingThePlayer() {
        setScene()
        val viewport = bounds(GuideWebMemoryLobbyViewportTag)
        composeRule.onNodeWithContentDescription(text(R.string.guide_gallery_dynamic_lobby_hide_controls)).performClick()
        composeRule.onNodeWithTag(GuideWebMemoryLobbyHeaderTag).assertDoesNotExist()
        composeRule.onNodeWithTag(GuideWebMemoryLobbyControlsTag).assertDoesNotExist()
        assertBoundsEqual(viewport, bounds(GuideWebMemoryLobbyViewportTag))
        assertEquals(1, playerMounts)
        assertEquals(0, playerDisposals)

        composeRule.onNodeWithTag(GuideWebMemoryLobbyRestoreTag).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(GuideWebMemoryLobbyHeaderTag).assertIsDisplayed()
        composeRule.onNodeWithTag(GuideWebMemoryLobbyControlsTag).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.guide_action_pause)).assertIsDisplayed()
        assertBoundsEqual(viewport, bounds(GuideWebMemoryLobbyViewportTag))
        assertEquals(1, playerMounts)
        assertEquals(0, playerDisposals)
    }

    @Test
    fun portraitMenuAndToolbarDoNotReserveSpaceAboveOrBelowTheAnimation() {
        var selected = ""
        var dismissed = false
        setScene(onSelectAction = { selected = it }, onDismiss = { dismissed = true })
        val originalViewport = bounds(GuideWebMemoryLobbyViewportTag)
        assertBoundsEqual(bounds(GuideWebMemoryLobbySceneTag), originalViewport)
        assertFloatingChromeInsideWindow()

        composeRule.onNodeWithText(text(R.string.guide_gallery_dynamic_lobby_actions)).performClick()
        composeRule.onNodeWithText("Idle_01").assertIsDisplayed().performClick()
        assertEquals("Idle_01", selected)
        assertBoundsEqual(originalViewport, bounds(GuideWebMemoryLobbyViewportTag))
        composeRule.onNodeWithContentDescription(text(R.string.common_close)).performClick()
        assertTrue(dismissed)
    }

    @Test
    fun largeFontKeepsActionsInsideThePhoneWithoutShrinkingTheMedia() {
        setScene(fontScale = 1.5f)
        assertBoundsEqual(bounds(GuideWebMemoryLobbySceneTag), bounds(GuideWebMemoryLobbyViewportTag))
        assertFloatingChromeInsideWindow()
        listOf(R.string.guide_action_pause, R.string.guide_action_retry, R.string.guide_gallery_dynamic_lobby_source)
            .forEach { composeRule.onNodeWithContentDescription(text(it)).assertIsDisplayed() }
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text(R.string.guide_gallery_dynamic_lobby))
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertTrue(layouts.all { !it.hasVisualOverflow || it.isLineEllipsized(0) })
    }

    @Test
    @Config(qualifiers = "w960dp-h420dp-land-xxhdpi")
    fun wideWindowKeepsCinematicFramingUsingTheEntireHeight() {
        setScene()
        val scene = bounds(GuideWebMemoryLobbySceneTag)
        val viewport = bounds(GuideWebMemoryLobbyViewportTag)
        assertTrue(abs(scene.top - viewport.top) <= 1f)
        assertTrue(abs(scene.bottom - viewport.bottom) <= 1f)
        assertTrue(abs(viewport.width / viewport.height - 16f / 9f) <= 0.01f)
        assertTrue(abs(scene.center.x - viewport.center.x) <= 1f)
        val title = composeRule.onNodeWithText(text(R.string.guide_gallery_dynamic_lobby))
            .fetchSemanticsNode().boundsInRoot
        assertTrue(title.right < scene.center.x, "Wide-window title must leave the central character unobscured")
        assertFloatingChromeInsideWindow()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletControlsStayCompactAndCenteredOverTheFullViewport() {
        setScene()
        val scene = bounds(GuideWebMemoryLobbySceneTag)
        assertBoundsEqual(scene, bounds(GuideWebMemoryLobbyViewportTag))
        val controls = bounds(GuideWebMemoryLobbyControlsTag)
        assertTrue(with(composeRule.density) { controls.width.toDp() } <= 460.dp)
        assertTrue(abs(scene.center.x - controls.center.x) <= 1f)
        assertFloatingChromeInsideWindow()
    }

    private fun setScene(
        fontScale: Float = 1f,
        onSelectAction: (String) -> Unit = {},
        onDismiss: () -> Unit = {},
        camera: GuideWebMemoryLobbyCamera = GuideWebMemoryLobbyCamera(),
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            var visible by remember { mutableStateOf(true) }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalLiquidControlsEnabled provides false,
                LocalTransitionAnimationsEnabled provides false,
            ) {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    SceneBackdropHost(backgroundColor = Color.Black) {
                        GuideWebMemoryLobbyScene(
                            controlsVisible = visible,
                            onShowControls = { visible = true },
                            camera = camera,
                            header = { GuideWebMemoryLobbyHeader(it, onDismiss, camera) },
                            controls = {
                                GuideWebMemoryLobbyControls(
                                    backdrop = it,
                                    playing = true,
                                    actions = listOf("Eye_Close_01", "Idle_01"),
                                    selectedAction = "",
                                    onTogglePlayback = {},
                                    onSelectAction = onSelectAction,
                                    onRetry = {},
                                    onOpenSource = {},
                                    onHideControls = { visible = false },
                                )
                            },
                        ) { viewport ->
                            DisposableEffect(Unit) {
                                playerMounts += 1
                                onDispose { playerDisposals += 1 }
                            }
                            Box(viewport.background(Color.Blue))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertFloatingChromeInsideWindow() {
        val scene = bounds(GuideWebMemoryLobbySceneTag)
        listOf(GuideWebMemoryLobbyHeaderTag, GuideWebMemoryLobbyControlsTag).forEach { tag ->
            val chrome = bounds(tag)
            assertTrue(chrome.left >= scene.left && chrome.right <= scene.right)
            assertTrue(chrome.top >= scene.top && chrome.bottom <= scene.bottom)
            assertTrue(with(composeRule.density) { chrome.height.toDp() } <= 64.dp)
        }
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun assertBoundsEqual(expected: Rect, actual: Rect) {
        assertTrue(abs(expected.left - actual.left) <= 1f)
        assertTrue(abs(expected.top - actual.top) <= 1f)
        assertTrue(abs(expected.right - actual.right) <= 1f)
        assertTrue(abs(expected.bottom - actual.bottom) <= 1f)
    }

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)
}
