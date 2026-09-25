package os.kei.ui.page.main.widget.core

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.test.assertTrue
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
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class AppDualActionRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rowKeepsEqualActionsAndEightDpGapAtLargeFont() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
            ) {
                AppDualActionRow(
                    modifier = Modifier.width(328.dp).testTag(ROW_TAG),
                    spacing = 8.dp,
                    first = { modifier ->
                        Box(modifier.height(48.dp).testTag(FIRST_TAG))
                    },
                    second = { modifier ->
                        Box(modifier.height(48.dp).testTag(SECOND_TAG))
                    },
                )
            }
        }

        composeRule.onNodeWithTag(ROW_TAG).assertWidthIsEqualTo(328.dp).assertHeightIsEqualTo(48.dp)
        composeRule.onNodeWithTag(FIRST_TAG).assertHeightIsEqualTo(48.dp)
        composeRule.onNodeWithTag(SECOND_TAG).assertHeightIsEqualTo(48.dp)

        val rowBounds = composeRule.onNodeWithTag(ROW_TAG).fetchSemanticsNode().boundsInRoot
        val firstBounds = composeRule.onNodeWithTag(FIRST_TAG).fetchSemanticsNode().boundsInRoot
        val secondBounds = composeRule.onNodeWithTag(SECOND_TAG).fetchSemanticsNode().boundsInRoot
        val tolerance = with(composeRule.density) { 0.5.dp.toPx() }
        val expectedGap = with(composeRule.density) { 8.dp.toPx() }

        assertTrue(abs(firstBounds.width - secondBounds.width) <= tolerance)
        assertTrue(abs((secondBounds.left - firstBounds.right) - expectedGap) <= tolerance)
        assertTrue(firstBounds.left >= rowBounds.left - tolerance)
        assertTrue(secondBounds.right <= rowBounds.right + tolerance)
    }
}

private const val ROW_TAG = "dual-action-row"
private const val FIRST_TAG = "dual-action-first"
private const val SECOND_TAG = "dual-action-second"
