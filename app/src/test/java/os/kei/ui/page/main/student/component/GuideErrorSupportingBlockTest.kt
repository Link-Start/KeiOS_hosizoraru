package os.kei.ui.page.main.student.component

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = GuideErrorSupportingBlockTestApp::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class GuideErrorSupportingBlockTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longErrorRemainsCompleteAndSeparatedAtLargeFontInLightTheme() {
        verifyLongErrorLayout(ColorSchemeMode.Light)
    }

    private fun verifyLongErrorLayout(colorSchemeMode: ColorSchemeMode) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(colorSchemeMode)) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                    LocalLiquidParentBackdrop provides null,
                ) {
                    val explicitBackdrop = rememberLayerBackdrop()
                    Column(
                        modifier =
                            Modifier
                                .width(360.dp)
                                .testTag(ROOT_TAG),
                    ) {
                        Text(BEFORE_TEXT, modifier = Modifier.testTag(BEFORE_TAG))
                        GuideErrorSupportingBlock(
                            error = LONG_ERROR_MESSAGE,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(ERROR_BLOCK_TAG),
                            backdrop = explicitBackdrop,
                        )
                        Text(AFTER_TEXT, modifier = Modifier.testTag(AFTER_TAG))
                    }
                }
            }
        }

        val rootBounds = composeRule.onNodeWithTag(ROOT_TAG).bounds()
        val beforeBounds = composeRule.onNodeWithTag(BEFORE_TAG).bounds()
        val errorNode = composeRule.onNodeWithTag(ERROR_BLOCK_TAG)
        val errorBounds = errorNode.bounds()
        val errorTextBounds =
            composeRule
                .onNodeWithText(LONG_ERROR_MESSAGE, useUnmergedTree = true)
                .bounds()
        val afterBounds = composeRule.onNodeWithTag(AFTER_TAG).bounds()
        val tolerance = with(composeRule.density) { 1.dp.toPx() }

        errorNode.assertReadOnly()
        assertTrue(abs(errorBounds.width - rootBounds.width) <= tolerance)
        assertTrue(beforeBounds.bottom <= errorBounds.top + tolerance)
        assertTrue(errorBounds.bottom <= afterBounds.top + tolerance)
        assertTrue(errorTextBounds.top >= errorBounds.top - tolerance)
        assertTrue(errorTextBounds.bottom <= errorBounds.bottom + tolerance)
        assertTrue(errorTextBounds.height > beforeBounds.height * 2f)
    }
}

private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

private fun SemanticsNodeInteraction.assertReadOnly() {
    assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
    assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
    assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
    assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
    assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
}

class GuideErrorSupportingBlockTestApp : Application()

private const val ROOT_TAG = "guide-error-root"
private const val BEFORE_TAG = "guide-error-before"
private const val ERROR_BLOCK_TAG = "guide-error-block"
private const val AFTER_TAG = "guide-error-after"
private const val BEFORE_TEXT = "Student section"
private const val AFTER_TEXT = "Next student card"
private const val LONG_ERROR_MESSAGE =
    "The student guide response could not be parsed because several nested records use an unsupported format; " +
        "the complete diagnostic remains visible so the source can be corrected and refreshed."