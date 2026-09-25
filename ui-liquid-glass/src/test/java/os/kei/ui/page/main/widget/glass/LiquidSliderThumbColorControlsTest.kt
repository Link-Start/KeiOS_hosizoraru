package os.kei.ui.page.main.widget.glass

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The slider thumb's colour controls, which replaced a fixed `vibrancy()`.
 *
 * These exist because the interesting half cannot be caught on a device. The resting appearance is only
 * correct if it reproduces `vibrancy()` exactly — the Backdrop docs define that as
 * `colorControls(saturation = 1.5f)` — and nothing about a screenshot would reveal a drift from 1.5 to,
 * say, 1.4; the thumb would simply be slightly duller than every other glass surface in the app forever.
 *
 * The active half is not reachable by **synthetic** input on the API 37 AVD: every `adb shell input`
 * horizontal drag on a settings slider is claimed by the settings pager, so `pressProgress` never leaves
 * zero. It *is* reachable by a real mouse drag on the emulator window, and that is how the pressed
 * saturation was caught — see `grabbingTheThumbAddsLightWithoutAddingChroma`.
 */
class LiquidSliderThumbColorControlsTest {
    /**
     * The invariant a screenshot of a resting slider can never guard, and the one a real drag caught.
     *
     * `colorControls` is the **colour filter** in Backdrop's `color filter ⇒ blur ⇒ lens` order, so it
     * saturates the backdrop before the lens works on it. Raising chroma there feeds whatever the lens
     * does next, at exactly the moment the lens amount is largest — the one thing a colour control on this
     * surface must not do.
     *
     * Measured from two 120fps recordings of real mouse drags, masked to the capsule's own pixels
     * (excluding the blue track and the recording's pointer sprite): the rest-to-press amplification of
     * mean chroma is **×2.86 with a pressed saturation of 1.85, and ×1.66 with it equal to resting**.
     */
    @Test
    fun grabbingTheThumbAddsLightWithoutAddingChroma() {
        assertEquals(
            "Pressed saturation must equal resting: this lens has chromatic aberration on, and the " +
                "colour filter runs before it, so a chroma lift becomes a rainbow fringe",
            SliderThumbVibrancySaturation,
            SliderThumbPressedSaturation,
            0f,
        )
        assertTrue(
            "Brightness is the whole of the press emphasis now, so it must be positive and non-trivial",
            SliderThumbPressedBrightness >= 0.08f,
        )
    }

    /**
     * The two lens decisions the recordings forced, which are easy to "restore to the tutorial" by mistake.
     *
     * Both are about the same thing: this thumb's entire backdrop is a 4dp blue track on near-black, so the
     * lens only ever has a hard high-contrast edge to work with. Channel splitting on that edge reads as a
     * smear, and removing the last of the frost lets the edge tear into disconnected blobs.
     */
    @Test
    fun theThumbLensKeepsAberrationOffAndSomeFrostOn() {
        val source = sourceFile(LIQUID_SLIDER_SOURCE).readText()

        assertTrue(
            "The thumb lens must keep chromatic aberration off — see the note at the call site",
            "chromaticAberration = false," in source,
        )
        assertFalse(
            "chromaticAberration = true would bring the green-above/orange-below fringes back",
            "chromaticAberration = true," in source,
        )
        assertTrue(
            "The press must keep a residual frost rather than lerping the blur to 0f",
            SliderThumbPressedBlurFloorFraction > 0f,
        )
        assertTrue(
            "A residual frost above about a quarter stops reading as 'the glass clears up'",
            SliderThumbPressedBlurFloorFraction <= 0.25f,
        )
    }

    @Test
    fun theLiftStaysGentleEnoughToReadAsLight() {
        // A 20dp capsule of glass over arbitrary content, including photos. Past roughly this bound a
        // luminance lift stops looking like light on glass and starts washing the refraction out.
        assertTrue(
            "Pressed brightness $SliderThumbPressedBrightness is beyond a plausible lift",
            SliderThumbPressedBrightness <= 0.18f,
        )
    }

}

private fun sourceFile(relativePath: String): File {
    val candidates =
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
    return requireNotNull(
        candidates
            .map { File(it, relativePath) }
            .firstOrNull(File::isFile),
    ) {
        "Unable to locate $relativePath from ${System.getProperty("user.dir")}"
    }
}

private const val LIQUID_SLIDER_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/glass/LiquidSliderVariants.kt"
