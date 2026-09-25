package os.kei.ui.page.main.student.catalog.component.bgm

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class BaGuideBgmChromeMiniPlayerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun expandedPlayerExposesFortyEightDpSeekAndTransportTargets() {
        var previousClicks = 0
        var playClicks = 0
        var nextClicks = 0
        setMiniPlayer(
            expanded = 1f,
            onPreviousClick = { previousClicks++ },
            onPlayPauseClick = { playClicks++ },
            onNextClick = { nextClicks++ },
        )
        val context = ApplicationProvider.getApplicationContext<Application>()

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_seekbar))
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_action_previous))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_action_play))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_action_next))
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(1, previousClicks)
        assertEquals(1, playClicks)
        assertEquals(1, nextClicks)
    }

    @Test
    fun expandedPlayerUsesAdjacentTransportSlotsAndPreservesTitleWidthAtLargeFont() {
        setMiniPlayer(expanded = 1f)
        val context = ApplicationProvider.getApplicationContext<Application>()
        val density = context.resources.displayMetrics.density
        val expectedItemGapPx = 10.dp.value * density
        val minimumTitleWidthPx = 170.dp.value * density
        val artworkBounds =
            composeRule
                .onNodeWithTag(BaGuideBgmMiniPlayerArtworkSlotTestTag)
                .fetchSemanticsNode()
                .boundsInRoot
        val titleBounds =
            composeRule
                .onNodeWithTag(BaGuideBgmMiniPlayerTitleSlotTestTag)
                .fetchSemanticsNode()
                .boundsInRoot
        val transportGroupBounds =
            composeRule
                .onNodeWithTag(BaGuideBgmMiniPlayerTransportGroupTestTag)
                .assertWidthIsEqualTo(BaGuideBgmMiniPlayerTransportControlGroupWidth)
                .fetchSemanticsNode()
                .boundsInRoot
        val previousBounds =
            composeRule
                .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_action_previous))
                .assertWidthIsEqualTo(BaGuideBgmMiniPlayerTransportControlSlotSize)
                .fetchSemanticsNode()
                .boundsInRoot
        val playBounds =
            composeRule
                .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_action_play))
                .assertWidthIsEqualTo(BaGuideBgmMiniPlayerTransportControlSlotSize)
                .fetchSemanticsNode()
                .boundsInRoot
        val nextBounds =
            composeRule
                .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_action_next))
                .assertWidthIsEqualTo(BaGuideBgmMiniPlayerTransportControlSlotSize)
                .fetchSemanticsNode()
                .boundsInRoot

        composeRule
            .onNodeWithTag(BaGuideBgmMiniPlayerTitleSlotTestTag)
            .assertWidthIsAtLeast(170.dp)

        assertTrue(
            abs(previousBounds.right - playBounds.left) <= 0.5f,
            "Previous slot right ${previousBounds.right} must meet play slot left ${playBounds.left}",
        )
        assertTrue(
            abs(playBounds.right - nextBounds.left) <= 0.5f,
            "Play slot right ${playBounds.right} must meet next slot left ${nextBounds.left}",
        )
        assertTrue(
            abs(titleBounds.left - artworkBounds.right - expectedItemGapPx) <= 0.5f,
            "Artwork-to-title gap must remain 10dp",
        )
        assertTrue(
            abs(transportGroupBounds.left - titleBounds.right - expectedItemGapPx) <= 0.5f,
            "Title-to-transport gap must remain 10dp",
        )
        assertTrue(
            titleBounds.width >= minimumTitleWidthPx,
            "Title slot width ${titleBounds.width} must retain at least 170dp at 1.5x font scale",
        )
    }

    @Test
    fun expandedPlayerKeepsDraggedProgressThumbBelowTitleInsideProductionSurface() {
        setMiniPlayer(expanded = 1f)
        val context = ApplicationProvider.getApplicationContext<Application>()
        val titleBounds = composeRule.onNodeWithText(TEST_TRACK_TITLE).fetchSemanticsNode().boundsInRoot
        val seekBounds =
            composeRule
                .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_seekbar))
                .fetchSemanticsNode()
                .boundsInRoot
        val surfaceBounds =
            composeRule
                .onNodeWithTag(MINI_PLAYER_SURFACE_TEST_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
        val density = context.resources.displayMetrics.density
        val restingThumbHalfHeightPx = 18.dp.value / 2f * density
        val maximumDraggedThumbHalfHeightPx = 18.dp.value * 1.30f * 1.10f / 2f * density
        val visualOffsetPx = BaGuideBgmMiniPlayerProgressVisualOffset.value * density
        val visualThumbCenterY = seekBounds.center.y + visualOffsetPx
        val minimumRestingOpticalGapPx = 5.dp.value * density
        val pressSafePaddingPx = 2.dp.value * density
        val productionInnerClipBottom = surfaceBounds.bottom - pressSafePaddingPx

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_seekbar))
            .assertHeightIsEqualTo(48.dp)

        assertTrue(
            visualThumbCenterY - restingThumbHalfHeightPx >=
                titleBounds.bottom + minimumRestingOpticalGapPx,
            "Resting progress thumb top ${visualThumbCenterY - restingThumbHalfHeightPx} must keep " +
                "a clear gap below title bottom ${titleBounds.bottom}",
        )
        assertTrue(
            visualThumbCenterY - maximumDraggedThumbHalfHeightPx >= titleBounds.bottom,
            "Maximum dragged progress thumb top ${visualThumbCenterY - maximumDraggedThumbHalfHeightPx} " +
                "must stay below title bottom ${titleBounds.bottom}",
        )
        assertTrue(
            visualThumbCenterY + maximumDraggedThumbHalfHeightPx <= productionInnerClipBottom,
            "Maximum dragged progress thumb bottom " +
                "${visualThumbCenterY + maximumDraggedThumbHalfHeightPx} must stay " +
                "inside production inner clip bottom $productionInnerClipBottom",
        )
        assertTrue(
            abs(productionInnerClipBottom - seekBounds.bottom) <= 0.5f,
            "Seek target bottom ${seekBounds.bottom} must honor the production surface press-safe " +
                "padding and inner clip bottom $productionInnerClipBottom",
        )
    }

    @Test
    fun transitioningPlayerRevealsProgressAfterTitleClears() {
        val expandedState = mutableFloatStateOf(HIDDEN_PROGRESS_SAMPLES.first())
        setMiniPlayer(expanded = { expandedState.floatValue })
        val context = ApplicationProvider.getApplicationContext<Application>()
        val density = context.resources.displayMetrics.density
        val maximumDraggedThumbHalfHeightPx = 18.dp.value * 1.30f * 1.10f / 2f * density
        val visualOffsetPx = BaGuideBgmMiniPlayerProgressVisualOffset.value * density
        val pressSafePaddingPx = 2.dp.value * density

        HIDDEN_PROGRESS_SAMPLES.forEach { expanded ->
            composeRule.runOnIdle { expandedState.floatValue = expanded }
            composeRule.waitForIdle()

            composeRule
                .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_seekbar))
                .assertDoesNotExist()
        }

        VISIBLE_PROGRESS_SAMPLES.forEach { expanded ->
            composeRule.runOnIdle { expandedState.floatValue = expanded }
            composeRule.waitForIdle()

            val titleBounds =
                composeRule
                    .onNodeWithText(TEST_TRACK_TITLE)
                    .fetchSemanticsNode()
                    .boundsInRoot
            val seekBounds =
                composeRule
                    .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_seekbar))
                    .fetchSemanticsNode()
                    .boundsInRoot
            val surfaceBounds =
                composeRule
                    .onNodeWithTag(MINI_PLAYER_SURFACE_TEST_TAG)
                    .fetchSemanticsNode()
                    .boundsInRoot
            val visualThumbCenterY = seekBounds.center.y + visualOffsetPx
            val maximumDraggedThumbTop = visualThumbCenterY - maximumDraggedThumbHalfHeightPx
            val maximumDraggedThumbBottom = visualThumbCenterY + maximumDraggedThumbHalfHeightPx
            val productionInnerClipBottom = surfaceBounds.bottom - pressSafePaddingPx

            assertTrue(
                maximumDraggedThumbTop + 0.5f >= titleBounds.bottom,
                "At expandedProgress=$expanded, maximum dragged thumb top $maximumDraggedThumbTop " +
                    "must stay below title bottom ${titleBounds.bottom}",
            )
            assertTrue(
                maximumDraggedThumbBottom <= productionInnerClipBottom + 0.5f,
                "At expandedProgress=$expanded, maximum dragged thumb bottom $maximumDraggedThumbBottom " +
                    "must stay inside production clip bottom $productionInnerClipBottom",
            )
        }
    }

    @Test
    fun compactPlayerRemovesSeekAndSideTransportSemantics() {
        setMiniPlayer(expanded = 0f)
        val context = ApplicationProvider.getApplicationContext<Application>()

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_seekbar))
            .assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_action_previous))
            .assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_action_next))
            .assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_action_play))
            .assertHeightIsAtLeast(48.dp)
    }

    private fun setMiniPlayer(
        expanded: Float,
        onPreviousClick: () -> Unit = {},
        onPlayPauseClick: () -> Unit = {},
        onNextClick: () -> Unit = {},
    ) = setMiniPlayer(
        expanded = { expanded },
        onPreviousClick = onPreviousClick,
        onPlayPauseClick = onPlayPauseClick,
        onNextClick = onNextClick,
    )

    private fun setMiniPlayer(
        expanded: () -> Float,
        onPreviousClick: () -> Unit = {},
        onPlayPauseClick: () -> Unit = {},
        onNextClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .testTag(MINI_PLAYER_SURFACE_TEST_TAG),
                    ) {
                        AppLiquidFloatingSurface(
                            modifier = Modifier.matchParentSize(),
                            consumeTouches = true,
                        ) {
                            BaGuideBgmChromeMiniPlayer(
                                accent = Color(0xFF3B82F6),
                                currentTrackTitle = TEST_TRACK_TITLE,
                                artworkImageUrl = "",
                                isPlaying = false,
                                playbackProgress = { 0.25f },
                                onPlaybackProgressChange = {},
                                onPlaybackProgressChangeFinished = {},
                                onPlaybackSliderInteractionChanged = {},
                                expandedProgress = expanded,
                                compactProgress = { 1f - expanded() },
                                onPlayPauseClick = onPlayPauseClick,
                                onPreviousClick = onPreviousClick,
                                onNextClick = onNextClick,
                                backdrop = null,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val TEST_TRACK_TITLE = "学生 · A long track title"

private const val MINI_PLAYER_SURFACE_TEST_TAG = "ba_bgm_mini_player_surface"

private val HIDDEN_PROGRESS_SAMPLES = listOf(0.01f, 0.25f, 0.5f)

private val VISIBLE_PROGRESS_SAMPLES = listOf(0.51f, 0.75f)
