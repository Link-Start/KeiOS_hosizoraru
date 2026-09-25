package os.kei.ui.page.main.widget.chrome

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import os.kei.core.prefs.UiPrefs
import kotlin.math.pow

/**
 * The non-Home background must not make the page's own text unreadable.
 *
 * The image is user-supplied, so the worst case has to be assumed rather than sampled: a white image in
 * dark theme, a black one in light. Composited strength is `opacity * (1 - overlay)`, and
 * [appManagedBackgroundRender] holds that below a ceiling solved from WCAG contrast.
 *
 * Measured before the ceiling existed: at the then-40% maximum in dark theme primary text fell to **4.20:1**,
 * under the 4.5:1 AA line, and nothing in the default configuration prevented it —
 * `AppManagedBackgroundStyles.Standard` has a zero flat overlay and the reading-overlay preference
 * defaults to 0, so readability depended on the user discovering the "Readable" page style.
 */
class AppManagedBackgroundReadabilityTest {
    @Test
    fun theDefaultIsTheStrongestWallpaperThatNeedsNoDimmingAndTheCeilingBindsInDarkFirst() {
        // Apple's Materials guidance dims only "if the underlying content is bright", and says a dark
        // dimming layer of 35% opacity when it does. So the default is placed exactly at the ceiling:
        // the most wallpaper that still needs none, so forcing an overlay there would dim it for nothing.
        listOf(true, false).forEach { darkBase ->
            val render = appManagedBackgroundRender(DEFAULT_OPACITY, AppManagedBackgroundStyles.Standard, darkBase)
            assertEquals("the default must not be dimmed (darkBase=$darkBase)", 0f, render.readabilityOverlay, 0f)
            assertEquals("darkBase=$darkBase", DEFAULT_OPACITY, render.imageOpacity, 1e-4f)
        }
        // Dark theme binds, because #242424 sits far closer to white than White does to black.
        val darkCeiling = appManagedBackgroundReadableStrengthCeiling(darkBase = true)
        val lightCeiling = appManagedBackgroundReadableStrengthCeiling(darkBase = false)
        assertTrue("dark should bind: $darkCeiling vs $lightCeiling", darkCeiling < lightCeiling)
        assertTrue(
            "a step above the default should already need dimming, or the default is too timid",
            appManagedBackgroundRender(DEFAULT_OPACITY + 0.01f, AppManagedBackgroundStyles.Standard, darkBase = true)
                .readabilityOverlay > 0f,
        )
        assertTrue(
            "the maximum is the case that used to fail and must now be protected",
            appManagedBackgroundRender(MAX_OPACITY, AppManagedBackgroundStyles.Standard, darkBase = true)
                .readabilityOverlay > 0f,
        )
    }

    @Test
    fun primaryTextStaysAtWcagAaAcrossTheWholeOpacityRange() {
        // Every reachable slider position, both themes, against the worst image for that theme.
        var step = MIN_OPACITY
        while (step <= MAX_OPACITY + 1e-4f) {
            listOf(
                Triple(true, DARK_BASE, WHITE),
                Triple(false, LIGHT_BASE, BLACK),
            ).forEach { (darkBase, base, worstImage) ->
                val render = appManagedBackgroundRender(step, AppManagedBackgroundStyles.Standard, darkBase)
                val text = if (darkBase) WHITE else BLACK
                val ratio = contrast(text, composite(worstImage, base, render))
                assertTrue(
                    "opacity=$step darkBase=$darkBase gave $ratio:1, under WCAG AA",
                    ratio >= WCAG_AA_BODY,
                )
            }
            step += 0.01f
        }
    }

    @Test
    fun noReachableOpacityNeedsMoreDimmingThanAppleAsksFor() {
        // The maximum is derived as ceiling / (1 - 0.35), so the top of the slider lands exactly on
        // Apple's 35% figure. Widening the range without re-deriving it should fail here.
        var step = MIN_OPACITY
        while (step <= MAX_OPACITY + 1e-4f) {
            listOf(true, false).forEach { darkBase ->
                val overlay =
                    appManagedBackgroundRender(step, AppManagedBackgroundStyles.Standard, darkBase).readabilityOverlay
                assertTrue(
                    "opacity=$step darkBase=$darkBase needs $overlay dimming, past Apple's $APPLE_DIMMING_LAYER",
                    overlay <= APPLE_DIMMING_LAYER + 1e-3f,
                )
            }
            step += 0.01f
        }
    }

    @Test
    fun aPageStyleThatAlreadyDimsTheImageNeedsLessProtection() {
        // `Focused` multiplies opacity down to 62%, so the composite is already inside the ceiling and the
        // extra overlay must not stack on top of the preset's own dimming.
        val focused = appManagedBackgroundRender(MAX_OPACITY, AppManagedBackgroundStyles.Focused, darkBase = true)
        val standard = appManagedBackgroundRender(MAX_OPACITY, AppManagedBackgroundStyles.Standard, darkBase = true)
        assertTrue(
            "Focused already dims, so it should need no more than Standard does",
            focused.readabilityOverlay <= standard.readabilityOverlay,
        )
    }

    @Test
    fun aTranslucentCardStillKeepsMostOfItsSecondaryTextContrast() {
        // A content-layer card cannot sample the page (6 fps at the 1% low when it did), so translucency
        // comes from its own fill alpha instead. That trades secondary-text contrast, and the alpha is the
        // floor that keeps 80% of what the same text has on a plain page.
        listOf(
            Triple(true, DARK_BASE, WHITE),
            Triple(false, LIGHT_BASE, BLACK),
        ).forEach { (darkBase, base, worstImage) ->
            val variant = if (darkBase) DARK_VARIANT else LIGHT_VARIANT
            val plain = contrast(variant, base)
            val card =
                cardComposite(
                    base = base,
                    worstImage = worstImage,
                    darkBase = darkBase,
                    alpha = appManagedPageCardMaterialAlpha(darkBase),
                )
            val ratio = contrast(variant, card)
            assertTrue(
                "darkBase=$darkBase card gave $ratio:1, under 80% of the plain-page $plain:1",
                ratio >= plain * 0.80f,
            )
        }
    }

    @Test
    fun theCardIsTranslucentEnoughToBeWorthDoingInBothThemes() {
        listOf(true, false).forEach { darkBase ->
            val alpha = appManagedPageCardMaterialAlpha(darkBase)
            assertTrue("darkBase=$darkBase is opaque at $alpha", alpha < 1f)
            assertTrue("darkBase=$darkBase shows too little through at $alpha", alpha <= 0.85f)
        }
        // Light needs the thicker fill: `onBackgroundVariant` is already 3.04:1 on pure white, so it has
        // no headroom there and any darkening costs it immediately.
        assertTrue(
            "light should need at least as thick a fill as dark",
            appManagedPageCardMaterialAlpha(darkBase = false) >= appManagedPageCardMaterialAlpha(darkBase = true),
        )
    }

    @Test
    fun primaryTextSurvivesEvenAFullyTransparentCard() {
        // The page ceiling already holds primary text at AA, and a translucent card can be no worse than
        // the page it reveals — so card translucency can never be what breaks primary text.
        listOf(
            Triple(true, DARK_BASE, WHITE),
            Triple(false, LIGHT_BASE, BLACK),
        ).forEach { (darkBase, base, worstImage) ->
            val text = if (darkBase) WHITE else BLACK
            val ratio =
                contrast(text, cardComposite(base = base, worstImage = worstImage, darkBase = darkBase, alpha = 0f))
            assertTrue("darkBase=$darkBase gave $ratio:1 with no card fill at all", ratio >= WCAG_AA_BODY)
        }
    }

    @Test
    fun anUnbackedCardDrawsTheElevatedSurfaceRatherThanThePageItSitsOn() {
        // Apple pairs a receding base with an advancing elevated surface. Drawing the base here is what
        // made cards disappear: `AppFeatureCard` fills with `surfaceContainer` at 64%, and 64% of a colour
        // over itself changes nothing.
        listOf(
            Triple(DARK_PAGE_BASE, DARK_ELEVATED, true),
            Triple(LIGHT_PAGE_BASE, LIGHT_ELEVATED, false),
        ).forEach { (base, elevated, darkBase) ->
            val material = appManagedPageCardMaterialColor(base, elevated, backed = false)

            assertEquals("darkBase=$darkBase must draw the elevated token", elevated, material)
            assertEquals("an unbacked card has nothing to reveal, so it stays opaque", 1f, material.alpha, 0f)
            assertTrue(
                "the two tokens must actually differ, or there is no step to draw (darkBase=$darkBase)",
                base != elevated,
            )
        }
    }

    @Test
    fun aBackedCardRevealsThePageInstead() {
        listOf(
            Triple(DARK_BASE, DARK_ELEVATED, true),
            Triple(LIGHT_BASE, LIGHT_ELEVATED, false),
        ).forEach { (base, elevated, darkBase) ->
            val material = appManagedPageCardMaterialColor(base, elevated, backed = true)

            assertEquals(
                "darkBase=$darkBase should reveal the page through the base colour",
                appManagedPageCardMaterialAlpha(darkBase),
                material.alpha,
                1f / 255f,
            )
        }
    }

    @Test
    fun malformedOpacityCannotProduceAnInvalidOverlay() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 5f).forEach { opacity ->
            val render = appManagedBackgroundRender(opacity, AppManagedBackgroundStyles.Standard, darkBase = true)
            assertTrue(
                "opacity=$opacity produced overlay ${render.readabilityOverlay}",
                render.readabilityOverlay in 0f..1f,
            )
            assertTrue(
                "opacity=$opacity produced image alpha ${render.imageOpacity}",
                render.imageOpacity in 0f..1f,
            )
        }
    }

    /*
     * Secondary text is a known limitation, recorded rather than silently "fixed".
     *
     * `onBackgroundVariant` is only 3.04:1 on plain White and 3.86:1 on plain `#242424` — already at or
     * under large-text AA before any image. No overlay rescues it: even 14% only reaches ~2.2:1 / ~2.5:1.
     * Fixing it means not putting secondary text on the raw page background, which is a change across
     * many pages rather than a tuning of this one.
     */

    // ---- WCAG plumbing, kept local so the assertions are self-contained -----------------------

    private fun composite(
        image: Color,
        base: Color,
        render: AppManagedBackgroundRender,
    ): Color {
        val strength = render.imageOpacity * (1f - render.readabilityOverlay)
        fun mix(
            i: Float,
            b: Float,
        ) = i * strength + b * (1f - strength)
        return Color(mix(image.red, base.red), mix(image.green, base.green), mix(image.blue, base.blue))
    }

    /** The page at its readable ceiling over [worstImage], with a card fill of [alpha] on top. */
    private fun cardComposite(
        base: Color,
        worstImage: Color,
        darkBase: Boolean,
        alpha: Float,
    ): Color {
        val strength = appManagedBackgroundReadableStrengthCeiling(darkBase)
        fun mix(
            over: Float,
            under: Float,
            a: Float,
        ) = over * a + under * (1f - a)
        fun channel(image: Float, plain: Float): Float {
            val page = mix(image, plain, strength)
            return mix(plain, page, alpha)
        }
        return Color(
            channel(worstImage.red, base.red),
            channel(worstImage.green, base.green),
            channel(worstImage.blue, base.blue),
        )
    }

    private fun contrast(
        foreground: Color,
        background: Color,
    ): Float {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }

    private fun relativeLuminance(color: Color): Float {
        fun channel(value: Float): Float =
            if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
    }

    private companion object {
        // Read from the store rather than restated, so the range these assertions sweep is the range the
        // slider actually offers. The properties below are what pin the numbers down.
        const val DEFAULT_OPACITY = UiPrefs.NON_HOME_BACKGROUND_OPACITY_DEFAULT
        const val MIN_OPACITY = UiPrefs.NON_HOME_BACKGROUND_OPACITY_MIN
        const val MAX_OPACITY = UiPrefs.NON_HOME_BACKGROUND_OPACITY_MAX
        const val WCAG_AA_BODY = 4.5f

        /** Apple, Materials: "consider adding a dark dimming layer of 35% opacity". */
        const val APPLE_DIMMING_LAYER = 0.35f

        val WHITE = Color.White
        val BLACK = Color.Black

        /** miuix `colorScheme.background` / `onBackgroundVariant`, light then dark. */
        val LIGHT_BASE = Color.White
        val DARK_BASE = Color(0xFF242424)
        val LIGHT_VARIANT = Color(0xFF8C93B0)
        val DARK_VARIANT = Color(0xFF787E96)

        /**
         * Apple's base/elevated pair for a page with *no* background, which is a different pairing from
         * [LIGHT_BASE] / [DARK_BASE] above — those are `colorScheme.background`, the colour the wallpaper
         * composites over. Unbacked, the page is `surface` and the card is `surfaceContainer`.
         */
        val LIGHT_PAGE_BASE = Color(0xFFF7F7F7)
        val DARK_PAGE_BASE = Color.Black
        val LIGHT_ELEVATED = Color.White
        val DARK_ELEVATED = Color(0xFF242424)
    }
}
