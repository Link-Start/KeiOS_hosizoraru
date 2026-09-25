package os.kei.ui.page.main.home

import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class HomePageCompactLandscapeLayoutTest {
    @Test
    fun compactLandscapeUsesTheReducedHeroBudget() {
        assertTrue(
            homePageUsesCompactLandscapeLayout(
                availableWidth = 952.dp,
                availableHeight = 426.dp,
            ),
        )
        assertFalse(homePageHeroShowsSupportingDetails(compactHeightPresentation = true))
        assertEquals(
            96.dp,
            homePageLogoTopPadding(
                scaffoldTopPadding = 96.dp,
                contentTopPadding = 48.dp,
                compactHeightPresentation = true,
            ),
        )
        assertEquals(
            18.dp,
            homePageHeroSpacerHeight(
                heroContentHeight = 98.dp,
                logoTopPadding = 96.dp,
                listTopPadding = 200.dp,
                topPadding = 12.dp,
                trailingClearance = 12.dp,
                homeHeaderSinkOffset = 0.dp,
            ),
        )
    }

    /**
     * The geometries a large screen actually produces, now that the app cannot hold itself portrait there.
     *
     * `targetSdk >= 36` makes Android ignore an orientation request on `sw >= 600dp`, and `targetSdk >= 37`
     * removes the opt-out, so a tablet in landscape is reachable for the first time. Measured on the API 37
     * AVD; the widths and heights below are the real dp values.
     */
    @Test
    fun aLargeScreenInLandscapeUsesTheCompactHero() {
        // 2856x1280 at density 320. This is the case that regressed: 640dp is too tall for the old 480dp
        // landscape cutoff and far too short for the tall hero, so the pills sat under the floating dock.
        assertTrue(
            homePageUsesCompactLandscapeLayout(
                availableWidth = 1428.dp,
                availableHeight = 640.dp,
            ),
        )
        // Same panel in portrait: 1280x2856 at density 320. Tall enough, and dropping the old `width > height`
        // term must not change it.
        assertFalse(
            homePageUsesCompactLandscapeLayout(
                availableWidth = 640.dp,
                availableHeight = 1428.dp,
            ),
        )
    }

    @Test
    fun portraitKeepsTheFullHeroBudget() {
        assertFalse(
            homePageUsesCompactLandscapeLayout(
                availableWidth = 411.dp,
                availableHeight = 891.dp,
            ),
        )
        // 1280x2856 at density 480 — the AVD in portrait, and the shape a phone actually ships in.
        assertFalse(
            homePageUsesCompactLandscapeLayout(
                availableWidth = 426.dp,
                availableHeight = 952.dp,
            ),
        )
        assertTrue(homePageHeroShowsSupportingDetails(compactHeightPresentation = false))
        assertEquals(
            168.dp,
            homePageLogoTopPadding(
                scaffoldTopPadding = 96.dp,
                contentTopPadding = 48.dp,
                compactHeightPresentation = false,
            ),
        )
        assertEquals(
            0.dp,
            homePageHeroSpacerHeight(
                heroContentHeight = 40.dp,
                logoTopPadding = 0.dp,
                listTopPadding = 100.dp,
                topPadding = 12.dp,
                trailingClearance = 12.dp,
                homeHeaderSinkOffset = 0.dp,
            ),
        )
    }
}
