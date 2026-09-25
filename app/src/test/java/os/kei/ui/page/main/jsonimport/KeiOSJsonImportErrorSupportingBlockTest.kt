package os.kei.ui.page.main.jsonimport

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = KeiOSJsonImportErrorSupportingBlockTestApp::class,
    sdk = [35],
    qualifiers = "en-rUS-w360dp-h800dp-xxhdpi",
)
class KeiOSJsonImportErrorSupportingBlockTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longErrorRemainsCompleteAndSeparatedAtLargeFontInLightTheme() {
        verifyLongErrorLayout(ColorSchemeMode.Light)
    }

    @Test
    fun blankErrorAddsNoSupportingLayout() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                JsonImportErrorSupportingBlock(
                    errorMessage = "",
                    modifier = Modifier.testTag(ERROR_BLOCK_TAG),
                )
            }
        }

        composeRule.onNodeWithTag(ERROR_BLOCK_TAG).assertDoesNotExist()
    }

    private fun verifyLongErrorLayout(colorSchemeMode: ColorSchemeMode) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(colorSchemeMode)) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                ) {
                    val parentBackdrop = rememberLayerBackdrop()
                    CompositionLocalProvider(
                        LocalLiquidControlsEnabled provides true,
                        LocalLiquidParentBackdrop provides parentBackdrop,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .width(360.dp)
                                    .testTag(ROOT_TAG),
                        ) {
                            Text(
                                text = BEFORE_TEXT,
                                modifier = Modifier.testTag(BEFORE_TAG),
                            )
                            JsonImportErrorSupportingBlock(
                                errorMessage = LONG_ERROR_MESSAGE,
                                modifier = Modifier.testTag(ERROR_BLOCK_TAG),
                            )
                            Text(
                                text = AFTER_TEXT,
                                modifier = Modifier.testTag(AFTER_TAG),
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText(LONG_ERROR_MESSAGE).assertExists()
        val rootBounds = composeRule.onNodeWithTag(ROOT_TAG).fetchSemanticsNode().boundsInRoot
        val beforeBounds = composeRule.onNodeWithTag(BEFORE_TAG).fetchSemanticsNode().boundsInRoot
        val errorBlockBounds = composeRule.onNodeWithTag(ERROR_BLOCK_TAG).fetchSemanticsNode().boundsInRoot
        val errorTextBounds =
            composeRule
                .onNodeWithText(LONG_ERROR_MESSAGE, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val afterBounds = composeRule.onNodeWithTag(AFTER_TAG).fetchSemanticsNode().boundsInRoot
        val tolerance = with(composeRule.density) { 1.dp.toPx() }

        assertTrue(errorBlockBounds.left >= rootBounds.left - tolerance)
        assertTrue(errorBlockBounds.right <= rootBounds.right + tolerance)
        assertTrue(errorBlockBounds.top >= rootBounds.top - tolerance)
        assertTrue(errorBlockBounds.bottom <= rootBounds.bottom + tolerance)
        assertTrue(
            abs(errorBlockBounds.width - rootBounds.width) <= tolerance,
            "fillWidth must keep the supporting block aligned to the 360dp parent",
        )
        assertTrue(beforeBounds.bottom <= errorBlockBounds.top + tolerance)
        assertTrue(errorBlockBounds.bottom <= afterBounds.top + tolerance)
        assertTrue(errorTextBounds.top >= errorBlockBounds.top - tolerance)
        assertTrue(errorTextBounds.bottom <= errorBlockBounds.bottom + tolerance)
        assertTrue(
            errorTextBounds.height > beforeBounds.height * 2f,
            "The long error must remain naturally multiline at 1.5x font scale",
        )
    }
}

class KeiOSJsonImportErrorSupportingBlockTestApp : Application()

private const val ROOT_TAG = "json-import-error-layout-root"
private const val BEFORE_TAG = "json-import-error-before"
private const val ERROR_BLOCK_TAG = "json-import-error-supporting-block"
private const val AFTER_TAG = "json-import-error-after"
private const val BEFORE_TEXT = "Import status"
private const val AFTER_TEXT = "Next action"
private const val LONG_ERROR_MESSAGE =
    "The selected JSON file could not be parsed because several nested records contain unsupported fields; " +
        "review the complete diagnostic details, correct the source data, and try the import again."
