@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.core.prefs.NonHomeBackgroundAlignment
import os.kei.core.prefs.NonHomeBackgroundContentScale
import os.kei.core.prefs.NonHomeBackgroundPageStyle
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.glass.rememberUniformColorBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

@Immutable
data class AppManagedBackgroundStyle(
    val opacityMultiplier: Float = 1f,
    val lightOverlayAlpha: Float = 0f,
    val darkOverlayAlpha: Float = 0f,
    val lightEdgeGradientAlpha: Float = 0f,
    val darkEdgeGradientAlpha: Float = 0f,
    val lightSideGradientAlpha: Float = 0f,
    val darkSideGradientAlpha: Float = 0f,
)

object AppManagedBackgroundStyles {
    val Standard =
        AppManagedBackgroundStyle(
            lightEdgeGradientAlpha = 0.04f,
            darkEdgeGradientAlpha = 0.05f,
            lightSideGradientAlpha = 0.018f,
            darkSideGradientAlpha = 0.024f,
        )
    val Soft =
        AppManagedBackgroundStyle(
            opacityMultiplier = 0.82f,
            lightOverlayAlpha = 0.04f,
            darkOverlayAlpha = 0.06f,
            lightEdgeGradientAlpha = 0.07f,
            darkEdgeGradientAlpha = 0.09f,
            lightSideGradientAlpha = 0.028f,
            darkSideGradientAlpha = 0.036f,
        )
    val Readable =
        AppManagedBackgroundStyle(
            opacityMultiplier = 0.75f,
            lightOverlayAlpha = 0.08f,
            darkOverlayAlpha = 0.12f,
            lightEdgeGradientAlpha = 0.12f,
            darkEdgeGradientAlpha = 0.16f,
            lightSideGradientAlpha = 0.048f,
            darkSideGradientAlpha = 0.064f,
        )
    val Focused =
        AppManagedBackgroundStyle(
            opacityMultiplier = 0.62f,
            lightOverlayAlpha = 0.14f,
            darkOverlayAlpha = 0.18f,
            lightEdgeGradientAlpha = 0.18f,
            darkEdgeGradientAlpha = 0.22f,
            lightSideGradientAlpha = 0.072f,
            darkSideGradientAlpha = 0.088f,
        )
    val FocusedTask =
        AppManagedBackgroundStyle(
            opacityMultiplier = 0.58f,
            lightOverlayAlpha = 0.18f,
            darkOverlayAlpha = 0.24f,
            lightEdgeGradientAlpha = 0.22f,
            darkEdgeGradientAlpha = 0.28f,
            lightSideGradientAlpha = 0.088f,
            darkSideGradientAlpha = 0.112f,
        )

    fun forPageStyle(pageStyle: NonHomeBackgroundPageStyle): AppManagedBackgroundStyle =
        when (pageStyle) {
            NonHomeBackgroundPageStyle.Standard -> Standard
            NonHomeBackgroundPageStyle.Readable -> Readable
            NonHomeBackgroundPageStyle.Soft -> Soft
            NonHomeBackgroundPageStyle.Focused -> Focused
        }

    fun resolve(
        pageStyle: NonHomeBackgroundPageStyle,
        sceneStyle: AppManagedBackgroundStyle,
    ): AppManagedBackgroundStyle {
        val preset = forPageStyle(pageStyle)
        return AppManagedBackgroundStyle(
            opacityMultiplier = minOf(preset.opacityMultiplier, sceneStyle.opacityMultiplier),
            lightOverlayAlpha = maxOf(preset.lightOverlayAlpha, sceneStyle.lightOverlayAlpha),
            darkOverlayAlpha = maxOf(preset.darkOverlayAlpha, sceneStyle.darkOverlayAlpha),
            lightEdgeGradientAlpha = maxOf(preset.lightEdgeGradientAlpha, sceneStyle.lightEdgeGradientAlpha),
            darkEdgeGradientAlpha = maxOf(preset.darkEdgeGradientAlpha, sceneStyle.darkEdgeGradientAlpha),
            lightSideGradientAlpha = maxOf(preset.lightSideGradientAlpha, sceneStyle.lightSideGradientAlpha),
            darkSideGradientAlpha = maxOf(preset.darkSideGradientAlpha, sceneStyle.darkSideGradientAlpha),
        )
    }
}

/**
 * How far the background image is allowed to pull the page away from its base colour.
 *
 * The image and the page's primary text both sit on the same surface, so the further the composite
 * travels from `colorScheme.background` the worse text contrast gets — and the image is user-supplied,
 * so the worst case has to be assumed: a white image in dark theme, a black one in light.
 *
 * Composited strength is `opacity * (1 - overlay)`. Solving WCAG contrast for primary text against that
 * worst case, targeting 4.8:1 for margin over the 4.5:1 AA line:
 *
 * | | dark (white image over `#242424`) | light (black image over White) |
 * |---|---|---|
 * | ceiling | **0.357** | 0.527 |
 *
 * Dark theme binds, because `#242424` is much closer to white than White is to black.
 *
 * Measured before this existed: at the then-40% maximum in dark theme primary text fell to **4.20:1**,
 * under AA, with nothing in the default configuration to stop it — `AppManagedBackgroundStyles.Standard`
 * has a zero flat overlay and the reading-overlay pref defaults to 0, so readability depended on the
 * user finding the "Readable" page style.
 *
 * This ceiling is also what the slider's own range is derived from, so the two cannot disagree — see
 * `UiPrefs.NON_HOME_BACKGROUND_OPACITY_DEFAULT`. The default sits *at* the ceiling, which is the
 * strongest wallpaper needing no dimming at all, and the maximum sits where Apple's 35% figure for a
 * dimming layer is reached. `AppManagedBackgroundReadabilityTest` checks both properties, so raising the
 * range without re-deriving it fails rather than quietly costing legibility.
 */
internal fun appManagedBackgroundReadableStrengthCeiling(darkBase: Boolean): Float = if (darkBase) 0.357f else 0.527f

/**
 * How opaque a content-layer card's fill is while a managed background is painting.
 *
 * A card cannot *sample* the page — measured on the BA page's steady-state scroll, giving every card the
 * scene to blur took the 1% low frame rate from 13 fps to **6** while the average went 36 → 33. But it
 * does not need to: `drawBackdrop` composites its sampled layer over what is already on screen, so
 * drawing the card's own fill below full opacity lets the page — wallpaper and all — through underneath,
 * at exactly the cost of the `drawRect` that was already there.
 *
 * The number is a floor derived from the weakest text on a card, `onBackgroundVariant`, against a
 * worst-case image (white in dark theme, black in light). Its contrast on a *plain* page is the app's own
 * baseline — 3.86:1 dark, 3.04:1 light — and these alphas keep **80%** of that:
 *
 * | | alpha | worst-case secondary | show-through |
 * |---|---|---|---|
 * | dark | 0.80 | 3.11:1 | 20% |
 * | light | 0.82 | 2.45:1 | 18% |
 *
 * Both round *up* from the solved minimum (0.794 and 0.813). Rounding down put light at 2.41:1 against a
 * 2.43:1 floor — the test caught it.
 *
 * Light needs the higher alpha because `onBackgroundVariant` has no headroom there at all: it is already
 * 3.04:1 on pure white, so any darkening costs it immediately. That asymmetry is why this is per-theme
 * rather than one constant.
 *
 * Worth being plain about the trade: this *does* cost secondary-text contrast, deliberately, because a
 * card that reads as opaque over a wallpaper is the thing being fixed. Primary text is unaffected —
 * [appManagedBackgroundReadableStrengthCeiling] already holds the page itself at AA for primary text, and
 * a translucent card can be no worse than the page it reveals.
 */
internal fun appManagedPageCardMaterialAlpha(darkBase: Boolean): Float = if (darkBase) 0.80f else 0.82f

/**
 * What a content-layer card's material draws: the page revealed through it, or the elevated surface.
 *
 * Apple, Dark Mode: "the system uses two sets of background colors — called base and elevated... The base
 * colors are dimmer, making background interfaces appear to recede, and the elevated colors are brighter,
 * making foreground interfaces appear to advance." A card is the advancing one, so with nothing behind the
 * page it draws [elevatedColor] outright.
 *
 * Sampling [baseColor] there instead — the page's own base — is what made cards vanish: `AppFeatureCard`
 * fills with `surfaceContainer` at 64%, and 64% of a colour over itself changes nothing. Measured a
 * 6-level card/page step in dark and none at all in light.
 *
 * With a background the card has something worth revealing, so it draws the base at
 * [appManagedPageCardMaterialAlpha] and lets the wallpaper through.
 */
internal fun appManagedPageCardMaterialColor(
    baseColor: Color,
    elevatedColor: Color,
    backed: Boolean,
): Color =
    if (backed) {
        baseColor.copy(alpha = appManagedPageCardMaterialAlpha(baseColor.luminance() < 0.5f))
    } else {
        elevatedColor
    }

/**
 * The image alpha and the readability overlay floor to render with, from one place.
 *
 * Both the main pager and the route host draw this background, and they have already drifted apart once
 * — one of them composited over `surface` while the other used `background`, which is what made the same
 * opacity slider produce two different results. Deriving both numbers here keeps that from recurring.
 *
 * The overlay is a *floor*: the page style's own overlay and the user's reading-overlay preference are
 * still free to go higher.
 */
internal fun appManagedBackgroundRender(
    opacity: Float,
    style: AppManagedBackgroundStyle,
    darkBase: Boolean,
): AppManagedBackgroundRender {
    // `coerceIn` passes NaN straight through, so a non-finite preference would otherwise reach
    // `AsyncImage`'s alpha as NaN and take the readability test with it. Absent beats invalid.
    val requested = (opacity * style.opacityMultiplier).takeIf(Float::isFinite) ?: 0f
    val imageOpacity = requested.coerceIn(0f, 1f)
    val ceiling = appManagedBackgroundReadableStrengthCeiling(darkBase)
    // overlay = 1 - ceiling/opacity, i.e. exactly enough to pull the composite back to the ceiling.
    val overlay = if (imageOpacity > ceiling) 1f - ceiling / imageOpacity else 0f
    return AppManagedBackgroundRender(
        imageOpacity = imageOpacity,
        readabilityOverlay = overlay.coerceIn(0f, 1f),
    )
}

@Immutable
internal data class AppManagedBackgroundRender(
    val imageOpacity: Float,
    val readabilityOverlay: Float,
)

/**
 * The page's own composite — base colour, background image, readability overlay — as something glass
 * can sample. `null` when no managed background is painting.
 *
 * Without this every glass surface refracted a flat token instead of the page. Each producer records
 * `drawRect(colorScheme.surface)` before `drawContent()`, and the background image is a *sibling* drawn
 * behind the recorded subtree, so the image could never reach the layer and the opaque rect would have
 * covered it anyway. Measured on the BA page with an image at 16%: the title capsule read
 * `rgb(14,14,14)` — perfectly neutral, so carrying no trace of the image — one pixel away from page
 * pixels at `rgb(61,54,60)`. The chrome was a black hole punched into a photograph, which is the
 * opposite of what the material is for: Apple's Materials guidance has Liquid Glass "allow content to
 * scroll and peek through from beneath these elements".
 *
 * Consumers sample this *under* their own layer (see `MainPageBackdropSet`), so the wallpaper is blurred
 * and refracted like any other content rather than being pasted in flat behind the effect.
 */
val LocalAppManagedSceneBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * A page-level backdrop, split by direction: [producer] records, the object itself is sampled.
 *
 * Routes that roll their own producer need the same treatment `MainPageBackdropSet` gives the main pages,
 * and the two directions differ once a managed background exists — the recording must *not* paint a base,
 * because the page composite is drawn under it instead. Keeping one object that is a `Backdrop` for
 * consumers and hands out its layer for producers means a call site cannot accidentally record what it
 * meant to sample.
 */
@Stable
class AppPageBackdrop internal constructor(
    val producer: LayerBackdrop,
    private val sampled: Backdrop,
) : Backdrop by sampled

/**
 * [AppPageBackdrop] for a page that owns its own producer.
 *
 * @param key identity for the underlying layer, so callers keep whatever lifecycle scoping they had —
 *   guide tabs rekey theirs per activation to avoid RenderThread crashes on some HyperOS builds.
 */
@Composable
fun rememberAppPageBackdrop(
    key: String,
    baseColor: Color = appPageBackdropBaseColor(),
): AppPageBackdrop {
    val scene = LocalAppManagedSceneBackdrop.current
    val producer =
        key(key) {
            rememberLayerBackdrop {
                if (scene == null) {
                    drawRect(baseColor)
                }
                drawContent()
            }
        }
    val sampled: Backdrop =
        if (scene != null) {
            rememberCombinedBackdrop(scene, producer)
        } else {
            producer
        }
    return remember(producer, sampled) { AppPageBackdrop(producer, sampled) }
}

@Composable
fun AppManagedBackgroundHost(
    enabled: Boolean,
    imageUri: String,
    opacity: Float,
    saturation: Float = 1f,
    contentScale: NonHomeBackgroundContentScale,
    scrim: Float,
    modifier: Modifier = Modifier,
    alignment: NonHomeBackgroundAlignment = NonHomeBackgroundAlignment.Center,
    pageStyle: NonHomeBackgroundPageStyle = NonHomeBackgroundPageStyle.Standard,
    style: AppManagedBackgroundStyle = AppManagedBackgroundStyles.Standard,
    exportBackdropToContent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val trimmedUri = imageUri.trim()
    val active = enabled && trimmedUri.isNotBlank()
    val baseColor = MiuixTheme.colorScheme.background
    val darkBase = baseColor.luminance() < 0.5f
    val resolvedStyle =
        remember(pageStyle, style) {
            AppManagedBackgroundStyles.resolve(
                pageStyle = pageStyle,
                sceneStyle = style,
            )
        }
    // Recorded whenever a background is painting, not only when a route asks for it: the page's glass
    // needs to sample this composite even on routes that never exported a backdrop of their own.
    // With no background painting, that recording is `drawRect(baseColor)` over an empty box: one colour
    // across the whole screen. Glass sampling one colour computes one colour, so the page is handed that
    // colour as a [UniformColorBackdrop] instead, which lets every card on it draw without an offscreen
    // layer and saves recording the screen-sized layer itself. See docs/planning/liquid-flat-field.md.
    val flatScene = !active && exportBackdropToContent
    val sceneBackdrop =
        if (active || (exportBackdropToContent && !flatScene)) {
            rememberLayerBackdrop {
                drawRect(baseColor)
                drawContent()
            }
        } else {
            null
        }
    val flatSceneBackdrop = if (flatScene) rememberUniformColorBackdrop(baseColor) else null

    Box(
        modifier =
            modifier
                .fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .then(appManagedBackgroundBaseModifier(baseColor, sceneBackdrop)),
        ) {
            if (active) {
                val render = appManagedBackgroundRender(opacity, resolvedStyle, darkBase)
                AppManagedBackgroundImage(
                    enabled = true,
                    imageUri = trimmedUri,
                    opacity = render.imageOpacity,
                    saturation = saturation,
                    contentScale = contentScale,
                    alignment = alignment,
                    modifier = Modifier.fillMaxSize(),
                )
                AppManagedBackgroundOverlay(
                    baseColor = baseColor,
                    darkBase = darkBase,
                    style = resolvedStyle,
                    scrim = scrim,
                    readabilityOverlay = render.readabilityOverlay,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        CompositionLocalProvider(
            LocalAppScaffoldContainerColor provides if (active) Color.Transparent else null,
            LocalAppManagedSceneBackdrop provides sceneBackdrop.takeIf { active },
        ) {
            // Still gated on the caller opting in: handing every route's presentations a parent
            // backdrop they never asked for is a wider change than letting their glass sample the page.
            val exportedScene = flatSceneBackdrop ?: sceneBackdrop
            if (exportBackdropToContent && exportedScene != null) {
                CompositionLocalProvider(
                    LocalLiquidParentBackdrop provides exportedScene,
                    content = content,
                )
            } else {
                content()
            }
        }
    }
}

/**
 * The page's opaque base, plus the backdrop recording when one is exported.
 *
 * **[baseColor] is painted unconditionally, and that is the fix for "二级菜单的透明度在白色背景下生效".**
 *
 * `Modifier.layerBackdrop` draws only `drawContent()` to the screen and records the
 * `rememberLayerBackdrop { ... }` block into an offscreen layer *separately*
 * (`LayerBackdropModifier.kt`: `drawContent(); recordLayer(...) { backdrop.onDraw(...) }`). So the
 * `drawRect(baseColor)` inside that recording block reaches the sampled layer and **never the screen**.
 * While this branch was `if (sceneBackdrop != null) layerBackdrop(...) else background(baseColor)`,
 * every route that exported a backdrop had no opaque base at all and was transparent down to the main
 * pager behind it — the page underneath and the custom image composited together into what looks like
 * two stacked backgrounds, with the pager's near-white `colorScheme.surface` showing through in light
 * theme. Measured before the fix: the Settings route showed the BA page's own rows and bottom tab bar.
 *
 * Only the `else` branch ever painted a base, which is why the routes that do not export one (About,
 * WebDavSync) already looked right, and the issue read as half-fixed.
 *
 * The recording still needs its own `drawRect(baseColor)`: it runs at the `layerBackdrop` node, so it
 * cannot see a background drawn by an outer modifier.
 */
internal fun appManagedBackgroundBaseModifier(
    baseColor: Color,
    sceneBackdrop: LayerBackdrop?,
): Modifier =
    Modifier
        .background(baseColor)
        .then(if (sceneBackdrop != null) Modifier.layerBackdrop(sceneBackdrop) else Modifier)

@Composable
fun AppManagedBackgroundOverlay(
    baseColor: Color,
    style: AppManagedBackgroundStyle,
    scrim: Float,
    modifier: Modifier = Modifier,
    darkBase: Boolean = baseColor.luminance() < 0.5f,
    /**
     * Minimum overlay needed to keep primary text at WCAG AA against a worst-case image. A floor, not a
     * replacement — the page style and the user's reading-overlay preference may ask for more. See
     * [appManagedBackgroundReadableStrengthCeiling].
     */
    readabilityOverlay: Float = 0f,
) {
    val overlayAlpha =
        maxOf(
            (
                if (darkBase) {
                    style.darkOverlayAlpha
                } else {
                    style.lightOverlayAlpha
                } + scrim
            ).coerceIn(0f, 1f),
            readabilityOverlay.coerceIn(0f, 1f),
        )
    val edgeGradientAlpha =
        (if (darkBase) {
            style.darkEdgeGradientAlpha
        } else {
            style.lightEdgeGradientAlpha
        } + scrim * 0.42f).coerceIn(0f, 1f)
    val sideGradientAlpha =
        (if (darkBase) {
            style.darkSideGradientAlpha
        } else {
            style.lightSideGradientAlpha
        } + scrim * 0.18f).coerceIn(0f, 1f)

    if (overlayAlpha <= 0f && edgeGradientAlpha <= 0f && sideGradientAlpha <= 0f) return

    Box(
        modifier =
            modifier
                .background(baseColor.copy(alpha = overlayAlpha))
                .drawWithCache {
                    val transparent = baseColor.copy(alpha = 0f)
                    val edgeColor = baseColor.copy(alpha = edgeGradientAlpha)
                    val sideColor = baseColor.copy(alpha = sideGradientAlpha)
                    val verticalMask =
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0f to edgeColor,
                                    0.30f to transparent,
                                    0.68f to transparent,
                                    1f to edgeColor,
                                ),
                        )
                    val horizontalMask =
                        Brush.horizontalGradient(
                            colorStops =
                                arrayOf(
                                    0f to sideColor,
                                    0.18f to transparent,
                                    0.82f to transparent,
                                    1f to sideColor,
                                ),
                        )
                    onDrawBehind {
                        drawRect(verticalMask)
                        drawRect(horizontalMask)
                    }
                },
    )
}

@Composable
fun AppManagedBackgroundImage(
    enabled: Boolean,
    imageUri: String,
    opacity: Float,
    modifier: Modifier = Modifier,
    contentScale: NonHomeBackgroundContentScale = NonHomeBackgroundContentScale.Crop,
    alignment: NonHomeBackgroundAlignment = NonHomeBackgroundAlignment.Center,
    saturation: Float = 1f,
    motionScale: Float = 1f,
    motionTranslationXProvider: (() -> Float)? = null,
    motionTranslationYProvider: (() -> Float)? = null,
) {
    if (!enabled || imageUri.isBlank()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowSize = appWindowSizeDp()
    val (targetWidthPx, targetHeightPx) =
        remember(windowSize, density) {
            with(density) {
                val width =
                    windowSize.width
                        .roundToPx()
                        .coerceAtLeast(1)
                val height =
                    windowSize.height
                        .roundToPx()
                        .coerceAtLeast(1)
                width to height
            }
        }
    val request =
        remember(imageUri, targetWidthPx, targetHeightPx, contentScale) {
            ImageRequest
                .Builder(context)
                .data(imageUri)
                .size(targetWidthPx, targetHeightPx)
                .scale(contentScale.toCoilScale())
                .precision(Precision.INEXACT)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }
    val safeMotionScale = motionScale.coerceAtLeast(1f)
    val safeSaturation = saturation.coerceIn(0f, 2f)
    val colorFilter =
        remember(safeSaturation) {
            if (abs(safeSaturation - 1f) < 0.01f) {
                null
            } else {
                ColorFilter.colorMatrix(
                    ColorMatrix().apply {
                        setToSaturation(safeSaturation)
                    },
                )
            }
        }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = contentScale.toComposeContentScale(),
        alignment = alignment.toComposeAlignment(),
        alpha = opacity.coerceIn(0f, 1f),
        colorFilter = colorFilter,
        modifier =
            modifier.then(
                if (
                    safeMotionScale != 1f ||
                    motionTranslationXProvider != null ||
                    motionTranslationYProvider != null
                ) {
                    Modifier.graphicsLayer {
                        scaleX = safeMotionScale
                        scaleY = safeMotionScale
                        translationX = motionTranslationXProvider?.invoke() ?: 0f
                        translationY = motionTranslationYProvider?.invoke() ?: 0f
                    }
                } else {
                    Modifier
                },
            ),
    )
}

private fun NonHomeBackgroundContentScale.toComposeContentScale(): ContentScale =
    when (this) {
        NonHomeBackgroundContentScale.Crop -> ContentScale.Crop
        NonHomeBackgroundContentScale.Fit -> ContentScale.Fit
        NonHomeBackgroundContentScale.FillBounds -> ContentScale.FillBounds
    }

private fun NonHomeBackgroundContentScale.toCoilScale(): Scale =
    when (this) {
        NonHomeBackgroundContentScale.Crop,
        NonHomeBackgroundContentScale.FillBounds -> Scale.FILL
        NonHomeBackgroundContentScale.Fit -> Scale.FIT
    }

private fun NonHomeBackgroundAlignment.toComposeAlignment(): Alignment =
    when (this) {
        NonHomeBackgroundAlignment.Top -> Alignment.TopCenter
        NonHomeBackgroundAlignment.Center -> Alignment.Center
        NonHomeBackgroundAlignment.Bottom -> Alignment.BottomCenter
        NonHomeBackgroundAlignment.Start -> Alignment.CenterStart
        NonHomeBackgroundAlignment.End -> Alignment.CenterEnd
    }
