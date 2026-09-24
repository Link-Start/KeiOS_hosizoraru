package os.kei.ui.page.main.host.pager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.ui.page.main.widget.chrome.LocalAppManagedSceneBackdrop
import os.kei.ui.page.main.widget.chrome.appManagedPageCardMaterialColor
import os.kei.ui.page.main.widget.chrome.appPageBackdropBaseColor
import os.kei.ui.page.main.widget.glass.rememberUniformColorBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A page's backdrops, split by direction: what glass **samples**, and what **records** into a layer.
 *
 * They used to be the same objects, which hid a trap: `MainPageContentBackdropScene` decided whether to
 * record by casting its argument to a layer, so any consumer value that happened to be one would
 * silently be re-recorded and blanked. Naming the producers apart makes the direction a compile-time
 * question instead of a runtime cast.
 */
@Immutable
data class MainPageBackdropSet(
    /** Sampled by the top bar and the floating chrome. */
    val topBar: Backdrop,
    /** Sampled by sheets. */
    val sheet: Backdrop,
    /** Sampled by content-layer cards. */
    val contentMaterial: Backdrop,
    /** The only values that belong in `Modifier.layerBackdrop`. */
    val topBarProducer: LayerBackdrop,
    val contentProducer: LayerBackdrop,
    val sheetProducer: LayerBackdrop,
)

/**
 * Hosts LayerBackdrop producers before the consumer slot. Canvas-backed materials draw directly
 * inside each consumer and pass through this host without allocating a page-sized producer.
 */
@Composable
internal fun MainPageContentBackdropScene(
    contentProducer: LayerBackdrop?,
    sheetProducer: LayerBackdrop? = null,
    modifier: Modifier = Modifier,
    producerActive: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        if (producerActive && contentProducer != null) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .layerBackdrop(contentProducer),
            )
        }
        if (
            producerActive &&
                sheetProducer != null &&
                sheetProducer !== contentProducer
        ) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .layerBackdrop(sheetProducer),
            )
        }
        content()
    }
}

@Composable
fun rememberMainPageBackdropSet(
    keyPrefix: String,
    refreshOnCompositionEnter: Boolean = false,
    distinctLayers: Boolean = true,
    useSolidSurfaceBackdrops: Boolean = false,
): MainPageBackdropSet {
    // Not `colorScheme.surface`: see [appPageBackdropBaseColor]. A non-Home main page's scaffold is
    // transparent, so its visible base is `background`, and recording `surface` had this page's glass
    // sampling pure black while the page itself rendered `#242424`.
    val baseColor = appPageBackdropBaseColor()
    // When a managed background is painting, *it* is the page's base. Its composite — base colour,
    // image, readability overlay — is drawn under each layer below, so painting a flat base here would
    // cover it and leave the chrome a black plate on a photograph.
    val scene = LocalAppManagedSceneBackdrop.current
    val instanceKeySuffix = if (refreshOnCompositionEnter) {
        var activationCount by rememberSaveable(keyPrefix) { mutableIntStateOf(0) }
        DisposableEffect(Unit) {
            activationCount++
            onDispose { }
        }
        "-$activationCount"
    } else {
        ""
    }

    @Composable
    fun rememberPageBackdrop(slot: String): LayerBackdrop = key("$keyPrefix-$slot$instanceKeySuffix") {
        rememberLayerBackdrop {
            if (scene == null) {
                drawRect(baseColor)
            }
            drawContent()
        }
    }

    // The top bar captures scrolling content from its dedicated list producer. Page cards sample
    // either their legacy surface-color layer or the equivalent direct canvas material.
    val topBarProducer = rememberPageBackdrop("topbar")
    val contentProducer =
        if (useSolidSurfaceBackdrops) {
            topBarProducer
        } else {
            rememberPageBackdrop("content")
        }
    val sheetProducer =
        if (distinctLayers) {
            rememberPageBackdrop("sheet")
        } else {
            contentProducer
        }
    // Deliberately *not* scene-composed, on both Apple's advice and a measurement.
    //
    // Apple's Materials guidance: "Don't use Liquid Glass in the content layer... Instead, use standard
    // materials for elements in the content layer" — a card is content, and a standard material is a
    // blur-and-fill, not a refracting lens. Composing the scene under it turns every one of a page's
    // ~20 cards from a `drawRect` into a real sample of a screen-sized layer, and on the BA page's
    // steady-state scroll that took the 1% low from 13 fps to **6** (average 36 → 33).
    //
    // It does not have to be opaque to show the page, though. `drawBackdrop` composites its sampled layer
    // over what is already on screen, so a fill below full opacity lets the wallpaper through underneath
    // for the price of the same single rect — see [appManagedPageCardMaterialAlpha] for where the number
    // comes from. Full opacity with no background, because then there is nothing behind to reveal.
    //
    // With no background the material is the *elevated* token at full strength, not the page's base.
    // Apple pairs a receding base with an advancing elevated surface, and a card is the advancing one;
    // sampling the page instead left the card's own 64% `surfaceContainer` fill nothing to lift off —
    // measured a 6-level step in dark and, once the page moved to `surface`, still only +5 in light,
    // where miuix has just 8 levels between the two tokens. This is also exactly what a route's cards
    // already sample, so a card stops depending on how deep the page is.
    val cardMaterialColor =
        appManagedPageCardMaterialColor(
            baseColor = baseColor,
            elevatedColor = MiuixTheme.colorScheme.surfaceContainer,
            backed = scene != null,
        )
    val contentMaterial =
        key("$keyPrefix-content-material$instanceKeySuffix") {
            rememberUniformColorBackdrop(cardMaterialColor)
        }
    return MainPageBackdropSet(
        topBar = rememberSceneComposedBackdrop(scene, topBarProducer),
        sheet = rememberSceneComposedBackdrop(scene, sheetProducer),
        contentMaterial = contentMaterial,
        topBarProducer = topBarProducer,
        contentProducer = contentProducer,
        sheetProducer = sheetProducer,
    )
}

/**
 * [layer] over [scene], or [layer] alone when nothing is behind it.
 *
 * Order is load-bearing: `Combined2Backdrops` draws its first backdrop first, so the scene has to come
 * first to sit *under* the page's own content. It also has to have been recorded earlier in the frame,
 * which holds because every scene producer is drawn before the content it backs.
 */
@Composable
private fun rememberSceneComposedBackdrop(
    scene: LayerBackdrop?,
    layer: Backdrop,
): Backdrop =
    if (scene != null) {
        rememberCombinedBackdrop(scene, layer)
    } else {
        layer
    }
