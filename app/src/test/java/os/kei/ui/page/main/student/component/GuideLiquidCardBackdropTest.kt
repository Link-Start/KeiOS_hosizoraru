package os.kei.ui.page.main.student.component

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdropOverridesFallback
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
)
class GuideLiquidCardBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pageSceneBackdropExportsCardMaterialToDescendants() {
        var sceneBackdrop: Backdrop? = null
        var descendantBackdrop: Backdrop? = null
        var descendantOverride = false

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                sceneBackdrop = backdrop
                CompositionLocalProvider(LocalLiquidParentBackdrop provides backdrop) {
                    GuideLiquidCard {
                        val observedBackdrop = LocalLiquidParentBackdrop.current
                        val observedOverride = LocalLiquidParentBackdropOverridesFallback.current
                        SideEffect {
                            descendantBackdrop = observedBackdrop
                            descendantOverride = observedOverride
                        }
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertNotNull(sceneBackdrop)
            assertNotNull(descendantBackdrop)
            assertNotSame(sceneBackdrop, descendantBackdrop)
            assertTrue(descendantOverride)
        }
    }

    @Test
    fun standaloneAndDisabledCardsDoNotPublishEmptyBackdropLayers() {
        var standaloneBackdrop: Backdrop? = null
        var standaloneOverride = true
        var disabledBackdrop: Backdrop? = null
        var disabledOverride = true

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                GuideLiquidCard {
                    val observedBackdrop = LocalLiquidParentBackdrop.current
                    val observedOverride = LocalLiquidParentBackdropOverridesFallback.current
                    SideEffect {
                        standaloneBackdrop = observedBackdrop
                        standaloneOverride = observedOverride
                    }
                }
                CompositionLocalProvider(
                    LocalLiquidParentBackdrop provides rememberLayerBackdrop(),
                    LocalLiquidControlsEnabled provides false,
                ) {
                    GuideLiquidCard {
                        val observedBackdrop = LocalLiquidParentBackdrop.current
                        val observedOverride = LocalLiquidParentBackdropOverridesFallback.current
                        SideEffect {
                            disabledBackdrop = observedBackdrop
                            disabledOverride = observedOverride
                        }
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertNull(standaloneBackdrop)
            assertFalse(standaloneOverride)
            assertNotNull(disabledBackdrop)
            assertFalse(disabledOverride)
        }
    }
}
