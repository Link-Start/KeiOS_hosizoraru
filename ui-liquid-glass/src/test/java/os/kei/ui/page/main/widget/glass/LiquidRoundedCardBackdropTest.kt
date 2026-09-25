package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class LiquidRoundedCardBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun roundedCardExportsIndependentMaterialAndPrioritizesItForContent() {
        var pageBackdrop: Backdrop? = null
        var contentBackdrop: Backdrop? = null
        var resolvedExplicitFallback: Backdrop? = null
        var overridesExplicitFallback = false

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                val explicitFallback = rememberLayerBackdrop()
                pageBackdrop = backdrop
                BackdropScene(backdrop, 280.dp) {
                    LiquidRoundedCard(
                        backdrop = backdrop,
                        exportBackdropToContent = true,
                        modifier = Modifier.testTag("rounded-card"),
                    ) {
                        contentBackdrop = LocalLiquidParentBackdrop.current
                        resolvedExplicitFallback = preferredLiquidBackdrop(explicitFallback)
                        overridesExplicitFallback =
                            LocalLiquidParentBackdropOverridesFallback.current
                        Box(modifier = Modifier.testTag("rounded-card-content"))
                    }
                }
            }
        }

        composeRule.onNodeWithTag("rounded-card").assertExists()
        composeRule.onNodeWithTag("rounded-card-content").assertExists()
        composeRule.runOnIdle {
            assertNotNull(pageBackdrop)
            assertNotNull(contentBackdrop)
            assertNotSame(pageBackdrop, contentBackdrop)
            assertSame(contentBackdrop, resolvedExplicitFallback)
            assertTrue(overridesExplicitFallback)
        }
    }

    @Test
    fun parentMaterialIsNotReplacedByAnEmptyExportWhenRuntimeEffectsAreDisabled() {
        var parentBackdrop: Backdrop? = null
        var explicitFallback: Backdrop? = null
        var contentBackdrop: Backdrop? = null
        var resolvedExplicitFallback: Backdrop? = null
        var overridesExplicitFallback = true

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val parent = rememberLayerBackdrop()
                val fallback = rememberLayerBackdrop()
                parentBackdrop = parent
                explicitFallback = fallback
                CompositionLocalProvider(
                    LocalLiquidControlsEnabled provides false,
                    LocalLiquidParentBackdrop provides parent,
                ) {
                    BackdropScene(parent, 280.dp) {
                        LiquidRoundedCard(
                            backdrop = parent,
                            exportBackdropToContent = true,
                            modifier = Modifier.testTag("rounded-card-runtime-disabled"),
                        ) {
                            contentBackdrop = LocalLiquidParentBackdrop.current
                            resolvedExplicitFallback = preferredLiquidBackdrop(fallback)
                            overridesExplicitFallback =
                                LocalLiquidParentBackdropOverridesFallback.current
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("rounded-card-runtime-disabled").assertExists()
        composeRule.runOnIdle {
            assertNotNull(parentBackdrop)
            assertNotNull(explicitFallback)
            assertSame(parentBackdrop, contentBackdrop)
            assertSame(explicitFallback, resolvedExplicitFallback)
            assertFalse(overridesExplicitFallback)
        }
    }

    @Test
    fun windowBoundaryRejectsExplicitParentWithoutPublishingAnEmptyExport() {
        var pageBackdrop: Backdrop? = null
        var contentBackdrop: Backdrop? = null
        var resolvedExplicitBackdrop: Backdrop? = null
        var overridesExplicitFallback = true

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val page = rememberLayerBackdrop()
                pageBackdrop = page
                CompositionLocalProvider(LocalLiquidControlsEnabled provides true) {
                    LiquidBackdropWindowBoundary {
                        LiquidRoundedCard(
                            backdrop = page,
                            exportBackdropToContent = true,
                            modifier = Modifier.testTag("rounded-card-window-fallback"),
                        ) {
                            contentBackdrop = LocalLiquidParentBackdrop.current
                            resolvedExplicitBackdrop = preferredLiquidBackdrop(page)
                            overridesExplicitFallback =
                                LocalLiquidParentBackdropOverridesFallback.current
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("rounded-card-window-fallback").assertExists()
        composeRule.runOnIdle {
            assertNotNull(pageBackdrop)
            assertNull(contentBackdrop)
            assertNull(resolvedExplicitBackdrop)
            assertFalse(overridesExplicitFallback)
        }
    }

    @Test
    fun windowLocalParentStillExportsIndependentCardMaterial() {
        var windowParentBackdrop: Backdrop? = null
        var contentBackdrop: Backdrop? = null
        var resolvedExplicitFallback: Backdrop? = null
        var overridesExplicitFallback = false

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val pageBackdrop = rememberLayerBackdrop()
                CompositionLocalProvider(LocalLiquidControlsEnabled provides true) {
                    LiquidBackdropWindowBoundary {
                        val windowParent = rememberLayerBackdrop()
                        windowParentBackdrop = windowParent
                        CompositionLocalProvider(LocalLiquidParentBackdrop provides windowParent) {
                            BackdropScene(windowParent, 280.dp) {
                                LiquidRoundedCard(
                                    backdrop = pageBackdrop,
                                    exportBackdropToContent = true,
                                    modifier = Modifier.testTag("rounded-card-window-local"),
                                ) {
                                    contentBackdrop = LocalLiquidParentBackdrop.current
                                    resolvedExplicitFallback = preferredLiquidBackdrop(pageBackdrop)
                                    overridesExplicitFallback =
                                        LocalLiquidParentBackdropOverridesFallback.current
                                }
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("rounded-card-window-local").assertExists()
        composeRule.runOnIdle {
            assertNotNull(windowParentBackdrop)
            assertNotNull(contentBackdrop)
            assertNotSame(windowParentBackdrop, contentBackdrop)
            assertSame(contentBackdrop, resolvedExplicitFallback)
            assertTrue(overridesExplicitFallback)
        }
    }
}
