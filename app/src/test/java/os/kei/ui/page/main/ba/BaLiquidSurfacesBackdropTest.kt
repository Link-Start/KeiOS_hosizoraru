package os.kei.ui.page.main.ba

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.glass.LocalAppEdgeStackCards
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdropOverridesFallback
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.glass.activeGlassBackdrop
import os.kei.ui.page.main.widget.glass.rememberAppEdgeStackState
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
)
class BaLiquidSurfacesBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inheritedPageBackdropExportsCardMaterialToDescendants() {
        var pageBackdrop: Backdrop? = null
        var descendantBackdrop: Backdrop? = null
        var descendantOverridesFallback = false

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                pageBackdrop = backdrop
                BaLiquidCard(backdrop = backdrop) {
                    val observedBackdrop = LocalLiquidParentBackdrop.current
                    val observedOverride = LocalLiquidParentBackdropOverridesFallback.current
                    SideEffect {
                        descendantBackdrop = observedBackdrop
                        descendantOverridesFallback = observedOverride
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertNotNull(pageBackdrop)
            assertNotNull(descendantBackdrop)
            assertNotSame(pageBackdrop, descendantBackdrop)
            assertTrue(descendantOverridesFallback)
        }
    }

    @Test
    fun nestedPanelConsumesAndReExportsParentCardMaterial() {
        var pageBackdrop: Backdrop? = null
        var cardBackdrop: Backdrop? = null
        var panelBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                pageBackdrop = backdrop
                BaLiquidCard(backdrop = backdrop) {
                    val observedCardBackdrop = LocalLiquidParentBackdrop.current
                    SideEffect { cardBackdrop = observedCardBackdrop }
                    BaLiquidPanel(backdrop = backdrop) {
                        val observedPanelBackdrop = LocalLiquidParentBackdrop.current
                        SideEffect { panelBackdrop = observedPanelBackdrop }
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertNotNull(pageBackdrop)
            assertNotNull(cardBackdrop)
            assertNotNull(panelBackdrop)
            assertNotSame(pageBackdrop, cardBackdrop)
            assertNotSame(cardBackdrop, panelBackdrop)
        }
    }

    @Test
    fun flatteningIsGatedOnHavingNoGesture() {
        // The press deformation lives inside the glass layer, so a pressable panel must keep that layer
        // even when it opted into flattening; only a gesture-free panel may drop to the flat fill.
        var cardBackdrop: Backdrop? = null
        var pressableBackdrop: Backdrop? = null
        var staticBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                BaLiquidCard(backdrop = backdrop) {
                    val observedCardBackdrop = LocalLiquidParentBackdrop.current
                    SideEffect { cardBackdrop = observedCardBackdrop }
                    BaLiquidPanel(backdrop = backdrop, flattenOverUniformParent = true, onLongClick = {}) {
                        val observed = LocalLiquidParentBackdrop.current
                        SideEffect { pressableBackdrop = observed }
                    }
                    BaLiquidPanel(backdrop = backdrop, flattenOverUniformParent = true) {
                        val observed = LocalLiquidParentBackdrop.current
                        SideEffect { staticBackdrop = observed }
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertNotNull(cardBackdrop)
            assertNotNull(pressableBackdrop)
            assertNotSame(cardBackdrop, pressableBackdrop, "a pressable panel must keep its own glass layer")
            assertSame(cardBackdrop, staticBackdrop, "a gesture-free flattened panel must not export a layer")
        }
    }

    @Test
    fun standaloneAndDisabledCardsKeepDescendantsOnTheirOwnFallbacks() {
        var standaloneBackdrop: Backdrop? = null
        var standaloneOverride = true
        var disabledBackdrop: Backdrop? = null
        var disabledOverride = true

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                BaLiquidCard(backdrop = null) {
                    val observedBackdrop = LocalLiquidParentBackdrop.current
                    val observedOverride = LocalLiquidParentBackdropOverridesFallback.current
                    SideEffect {
                        standaloneBackdrop = observedBackdrop
                        standaloneOverride = observedOverride
                    }
                }
                BaLiquidCard(
                    backdrop = rememberLayerBackdrop(),
                    effectsEnabled = false,
                ) {
                    val observedBackdrop = LocalLiquidParentBackdrop.current
                    val observedOverride = LocalLiquidParentBackdropOverridesFallback.current
                    SideEffect {
                        disabledBackdrop = observedBackdrop
                        disabledOverride = observedOverride
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertNull(standaloneBackdrop)
            assertFalse(standaloneOverride)
            assertNull(disabledBackdrop)
            assertFalse(disabledOverride)
        }
    }

    @Test
    fun runtimeDisabledCardDoesNotPublishAnEmptyMaterial() {
        var parentBackdrop: Backdrop? = null
        var descendantBackdrop: Backdrop? = null
        var descendantActiveBackdrop: Backdrop? = null
        var descendantOverridesFallback = true

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                parentBackdrop = backdrop
                CompositionLocalProvider(
                    LocalLiquidParentBackdrop provides backdrop,
                    LocalLiquidControlsEnabled provides false,
                ) {
                    BaLiquidCard(backdrop = null) {
                        val observedBackdrop = LocalLiquidParentBackdrop.current
                        val observedActiveBackdrop = activeGlassBackdrop(observedBackdrop)
                        val observedOverride = LocalLiquidParentBackdropOverridesFallback.current
                        SideEffect {
                            descendantBackdrop = observedBackdrop
                            descendantActiveBackdrop = observedActiveBackdrop
                            descendantOverridesFallback = observedOverride
                        }
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertNotNull(parentBackdrop)
            assertSame(parentBackdrop, descendantBackdrop)
            assertNull(descendantActiveBackdrop)
            assertFalse(descendantOverridesFallback)
        }
    }

    @Test
    fun aCardInTheEdgeStackKeepsItsGlassAndExportsItToDescendants() {
        // 482f0cfb3 gated the backdrop on `effectsEnabled && edgeStack == null`. The calendar/pool layout
        // always provides a stack, so every card on both pages fell to a flat fill with no glass -- and
        // descendants were left on their own fallbacks. A stacked card must behave like an unstacked one.
        var pageBackdrop: Backdrop? = null
        var descendantBackdrop: Backdrop? = null
        var descendantOverridesFallback = false
        var descendantSeesTheStack = true

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                pageBackdrop = backdrop
                val stack = rememberAppEdgeStackState(stackLine = 24.dp)
                CompositionLocalProvider(LocalAppEdgeStackCards provides stack) {
                    BaLiquidCard(backdrop = backdrop) {
                        val observedBackdrop = LocalLiquidParentBackdrop.current
                        val observedOverride = LocalLiquidParentBackdropOverridesFallback.current
                        val observedStack = LocalAppEdgeStackCards.current
                        SideEffect {
                            descendantBackdrop = observedBackdrop
                            descendantOverridesFallback = observedOverride
                            descendantSeesTheStack = observedStack != null
                        }
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertNotNull(pageBackdrop)
            assertNotNull(descendantBackdrop, "a stacked card must still export its glass")
            assertNotSame(pageBackdrop, descendantBackdrop)
            assertTrue(descendantOverridesFallback)
            // A panel inside the card is part of the card, not a second member of the pile.
            assertFalse(descendantSeesTheStack, "the card's content must not join the pile itself")
        }
    }
}
