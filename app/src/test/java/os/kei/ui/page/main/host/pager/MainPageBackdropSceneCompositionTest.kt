package os.kei.ui.page.main.host.pager

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.chrome.LocalAppManagedSceneBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * Chrome glass must sample the page's real composite, and content-layer cards must not.
 *
 * Measured on the BA page with an image at 16% before this held: the title capsule read `rgb(14,14,14)`
 * — exactly neutral, so carrying no trace of the image — one pixel from page pixels at `rgb(61,54,60)`,
 * because every producer recorded an opaque `drawRect(colorScheme.surface)` and the image was a sibling
 * outside the recorded subtree. After: `rgb(46,52,58)`, tinted like the page it floats over.
 *
 * The card exclusion is equally deliberate. Apple's Materials guidance keeps Liquid Glass out of the
 * content layer, and composing the scene under ~20 cards took the page's 1% low frame rate to 8 fps.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
)
class MainPageBackdropSceneCompositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun withoutAManagedBackgroundEveryChromeConsumerSamplesItsOwnLayerAlone() {
        val backdrops = backdropSet(scene = null)

        assertSame(
            backdrops.topBarProducer,
            backdrops.topBar,
            "with nothing behind the page there is nothing to compose, so the consumer must be the layer",
        )
        assertSame(backdrops.sheetProducer, backdrops.sheet)
    }

    @Test
    fun aManagedBackgroundPutsThePageCompositeUnderTheChromeLayers() {
        var scene: LayerBackdrop? = null
        val backdrops = backdropSet(sceneFactory = { rememberLayerBackdrop().also { scene = it } })

        assertNotSame(
            backdrops.topBarProducer,
            backdrops.topBar,
            "the top bar must sample the scene composed under its layer, not the bare layer",
        )
        assertNotSame(backdrops.sheetProducer, backdrops.sheet)
        // Composition, not replacement: the producers keep their identities so recording is unaffected.
        val recorded = requireNotNull(scene)
        assertNotSame<Any>(recorded, backdrops.topBar)
        assertNotSame<Any>(recorded, backdrops.sheet)
        assertTrue(
            backdrops.topBar.isCoordinatesDependent,
            "a composed scene is positioned per consumer, so it must stay coordinates-dependent",
        )
    }

    @Test
    fun cardsKeepTheirFlatFillEvenWhenABackgroundIsPainting() {
        val withScene = backdropSet(sceneFactory = { rememberLayerBackdrop() })

        assertFalse(
            withScene.contentMaterial.isCoordinatesDependent,
            "the content material must stay a canvas fill: a coordinates-dependent one means every card " +
                "is blurring a screen-sized layer, which measured 8 fps at the 1% low",
        )
    }

    private fun backdropSet(
        scene: LayerBackdrop? = null,
        sceneFactory: (@Composable () -> LayerBackdrop?)? = null,
    ): MainPageBackdropSet {
        var observed: MainPageBackdropSet? = null
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Dark)) {
                val resolved = sceneFactory?.invoke() ?: scene
                CompositionLocalProvider(LocalAppManagedSceneBackdrop provides resolved) {
                    observed = rememberMainPageBackdropSet(keyPrefix = "scene-composition")
                }
            }
        }
        composeRule.waitForIdle()
        return requireNotNull(observed)
    }
}
