package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class LiquidFiniteSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun linearProgressKeepsLatestFiniteValueInSemantics() {
        val progress = mutableFloatStateOf(3.5f)
        composeRule.setContent {
            CompositionLocalProvider(LocalLiquidControlsEnabled provides false) {
                LiquidLinearProgressBar(
                    progress = { progress.floatValue },
                    modifier = Modifier.testTag("linear-progress"),
                    valueRange = 2f..4f,
                )
            }
        }

        composeRule
            .onNodeWithTag("linear-progress")
            .assertRangeInfoEquals(ProgressBarRangeInfo(3.5f, 2f..4f))

        composeRule.runOnIdle { progress.floatValue = Float.NaN }
        composeRule
            .onNodeWithTag("linear-progress")
            .assertRangeInfoEquals(ProgressBarRangeInfo(3.5f, 2f..4f))

        composeRule.runOnIdle { progress.floatValue = Float.POSITIVE_INFINITY }
        composeRule
            .onNodeWithTag("linear-progress")
            .assertRangeInfoEquals(ProgressBarRangeInfo(3.5f, 2f..4f))
    }

    @Test
    fun circularProgressNormalizesReversedRangeAndKeepsLatestFiniteValue() {
        val progress = mutableFloatStateOf(0.25f)
        composeRule.setContent {
            LiquidCircularProgressBar(
                { progress.floatValue },
                Modifier.testTag("circular-progress"),
                valueRange = 1f..0f,
            )
        }

        composeRule
            .onNodeWithTag("circular-progress")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.25f, 0f..1f))

        composeRule.runOnIdle { progress.floatValue = Float.NEGATIVE_INFINITY }
        composeRule
            .onNodeWithTag("circular-progress")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.25f, 0f..1f))
    }

    @Test
    fun sliderSemanticsNeverExposeNonFiniteCurrentValue() {
        val value = mutableFloatStateOf(Float.NaN)
        composeRule.setContent {
            CompositionLocalProvider(LocalLiquidControlsEnabled provides false) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.size(width = 300.dp, height = 64.dp)) {
                    LiquidVolumeSlider(
                        value = { value.floatValue },
                        onValueChange = {},
                        valueRange = 1f..0f,
                        visibilityThreshold = 0.001f,
                        backdrop = backdrop,
                        modifier = Modifier.testTag("liquid-slider"),
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag("liquid-slider")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))

        composeRule.runOnIdle { value.floatValue = 0.70f }
        composeRule
            .onNodeWithTag("liquid-slider")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.70f, 0f..1f))

        composeRule.runOnIdle { value.floatValue = Float.POSITIVE_INFINITY }
        composeRule
            .onNodeWithTag("liquid-slider")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.70f, 0f..1f))
    }
}
