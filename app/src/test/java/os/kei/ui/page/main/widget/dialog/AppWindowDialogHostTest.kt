package os.kei.ui.page.main.widget.dialog

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = AppWindowDialogHostTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppWindowDialogHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fullscreenPresentationUsesWindowHeightAndDispatchesDismissFinishedOnce() {
        val show = mutableStateOf(true)
        var dismissFinishedCount = 0
        var contentObserved = false
        var observedParentBackdrop: Backdrop? = null
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val pageBackdrop = rememberLayerBackdrop()
                CompositionLocalProvider(LocalLiquidParentBackdrop provides pageBackdrop) {
                    AppWindowDialogHost(
                        show = show.value,
                        presentation = AppWindowDialogPresentation.Fullscreen,
                        onDismissFinished = { dismissFinishedCount++ },
                    ) {
                        val parentBackdrop = LocalLiquidParentBackdrop.current
                        SideEffect {
                            contentObserved = true
                            observedParentBackdrop = parentBackdrop
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .testTag("fullscreen-dialog-content"),
                        )
                    }
                }
            }
        }

        composeRule
            .onNodeWithTag("fullscreen-dialog-content")
            .assertExists()
            .assertHeightIsAtLeast(850.dp)
        composeRule.runOnIdle {
            assertTrue(contentObserved)
            assertNull(observedParentBackdrop)
        }

        composeRule.runOnIdle { show.value = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fullscreen-dialog-content").assertDoesNotExist()
        assertEquals(1, dismissFinishedCount)

        composeRule.runOnIdle { show.value = false }
        composeRule.waitForIdle()
        assertEquals(1, dismissFinishedCount)
    }

    @Test
    fun fullscreenContentBackHandlerUsesDialogDispatcherBeforeHostFallback() {
        var contentBackCount = 0
        var hostBackCount = 0
        lateinit var dialogBackDispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppWindowDialogHost(
                    show = true,
                    presentation = AppWindowDialogPresentation.Fullscreen,
                    onDismissRequest = { hostBackCount++ },
                ) {
                    val owner = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
                    SideEffect { dialogBackDispatcher = owner.onBackPressedDispatcher }
                    BackHandler { contentBackCount++ }
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        composeRule.runOnIdle { dialogBackDispatcher.onBackPressed() }

        assertEquals(1, contentBackCount)
        assertEquals(0, hostBackCount)
    }

    @Test
    fun fullscreenBackHandlerSurvivesHostProvidedNavigationEventOwner() {
        // miuix-nav's NavDisplay provides an entry-scoped NavigationEventDispatcherOwner around
        // every route; without the window scope re-resolve, dialog-content back handlers would
        // register there and the dialog window's own dispatcher would swallow back events.
        val hostOwner =
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher = NavigationEventDispatcher()
            }
        var contentBackCount = 0
        var hostBackCount = 0
        lateinit var dialogBackDispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides hostOwner) {
                    AppWindowDialogHost(
                        show = true,
                        presentation = AppWindowDialogPresentation.Fullscreen,
                        onDismissRequest = { hostBackCount++ },
                    ) {
                        val owner = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
                        SideEffect { dialogBackDispatcher = owner.onBackPressedDispatcher }
                        BackHandler { contentBackCount++ }
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }

        composeRule.runOnIdle { dialogBackDispatcher.onBackPressed() }

        composeRule.runOnIdle {
            assertEquals(1, contentBackCount)
            assertEquals(0, hostBackCount)
        }
    }

}

class AppWindowDialogHostTestApp : Application()
