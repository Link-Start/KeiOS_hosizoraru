package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = LiquidInfoBlockTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class LiquidInfoBlockTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exportsIndependentCardBackdropToContent() {
        var pageBackdrop: Backdrop? = null
        var contentBackdrop: Backdrop? = null
        var resolvedExplicitFallback: Backdrop? = null
        var overridesExplicitFallback = false

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                val explicitFallback = rememberLayerBackdrop()
                pageBackdrop = backdrop
                BackdropScene(backdrop, 280.dp) {
                    LiquidInfoBlock(
                        backdrop = backdrop,
                        title = "Status",
                        subtitle = "Ready",
                        accent = Color(0xFF2563EB),
                        modifier = Modifier.testTag("info-block"),
                    ) {
                        contentBackdrop = LocalLiquidParentBackdrop.current
                        resolvedExplicitFallback = preferredLiquidBackdrop(explicitFallback)
                        overridesExplicitFallback = LocalLiquidParentBackdropOverridesFallback.current
                        Box(modifier = Modifier.testTag("info-content"))
                    }
                }
            }
        }

        composeRule.onNodeWithTag("info-block").assertExists()
        composeRule.onNodeWithTag("info-content").assertExists()
        composeRule.runOnIdle {
            assertNotNull(pageBackdrop)
            assertNotNull(contentBackdrop)
            assertNotSame(pageBackdrop, contentBackdrop)
            assertSame(contentBackdrop, resolvedExplicitFallback)
            assertTrue(overridesExplicitFallback)
        }
    }

    @Test
    fun inheritsCompositionLocalBeforeExportingCardBackdrop() {
        var pageBackdrop: Backdrop? = null
        var contentBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                pageBackdrop = backdrop
                BackdropScene(backdrop, 280.dp) {
                    CompositionLocalProvider(LocalLiquidParentBackdrop provides backdrop) {
                        LiquidInfoBlock(
                            title = "Inherited",
                            subtitle = "Ready",
                            accent = Color(0xFF22C55E),
                        ) {
                            contentBackdrop = LocalLiquidParentBackdrop.current
                            Box(modifier = Modifier.testTag("inherited-content"))
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("inherited-content").assertExists()
        composeRule.runOnIdle {
            assertNotNull(pageBackdrop)
            assertNotNull(contentBackdrop)
            assertNotSame(pageBackdrop, contentBackdrop)
        }
    }

    @Test
    fun missingParentBackdropKeepsDescendantsOnSolidFallback() {
        var contentBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidInfoBlock(
                    title = "Standalone",
                    subtitle = "Fallback",
                    accent = Color(0xFF2563EB),
                ) {
                    contentBackdrop = LocalLiquidParentBackdrop.current
                    Box(modifier = Modifier.testTag("standalone-content"))
                }
            }
        }

        composeRule.onNodeWithTag("standalone-content").assertExists()
        composeRule.runOnIdle { assertNull(contentBackdrop) }
    }

    @Test
    fun disabledLiquidEffectsKeepDescendantsOnSolidFallback() {
        var contentBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                CompositionLocalProvider(LocalLiquidControlsEnabled provides false) {
                    LiquidInfoBlock(
                        backdrop = backdrop,
                        title = "Disabled",
                        subtitle = "Fallback",
                        accent = Color(0xFF2563EB),
                    ) {
                        contentBackdrop = LocalLiquidParentBackdrop.current
                        Box(modifier = Modifier.testTag("disabled-content"))
                    }
                }
            }
        }

        composeRule.onNodeWithTag("disabled-content").assertExists()
        composeRule.runOnIdle { assertNull(contentBackdrop) }
    }

    @Test
    fun callerModifierReachesRootWithoutMergingContent() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidInfoBlock(
                    title = "Status",
                    subtitle = "Ready",
                    accent = Color(0xFF2563EB),
                    modifier =
                        Modifier
                            .testTag("semantic-root")
                            .semantics { contentDescription = "Sync status" },
                ) {
                    Box(modifier = Modifier.testTag("semantic-child"))
                }
            }
        }

        composeRule
            .onNodeWithTag("semantic-root")
            .assertContentDescriptionEquals("Sync status")
        composeRule.onNodeWithTag("semantic-child").assertExists()
    }

    @Test
    fun blankSubtitleDoesNotReserveASecondTextRow() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Column {
                    LiquidInfoBlock(
                        title = "Compact",
                        subtitle = "",
                        accent = Color(0xFF2563EB),
                        modifier =
                            Modifier
                                .width(240.dp)
                                .testTag("blank-subtitle"),
                    )
                    LiquidInfoBlock(
                        title = "Regular",
                        subtitle = "A visible status description",
                        accent = Color(0xFF2563EB),
                        modifier =
                            Modifier
                                .width(240.dp)
                                .testTag("visible-subtitle"),
                    )
                }
            }
        }

        val blankHeight =
            composeRule
                .onNodeWithTag("blank-subtitle")
                .fetchSemanticsNode()
                .boundsInRoot
                .height
        val visibleHeight =
            composeRule
                .onNodeWithTag("visible-subtitle")
                .fetchSemanticsNode()
                .boundsInRoot
                .height

        assertTrue(blankHeight < visibleHeight)
    }

    @Test
    fun compactStatusKeepsTitleAndSubtitleInOneDenseRowAtLargeFont() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = 1.5f),
                ) {
                    LiquidInfoBlock(
                        title = "App list",
                        subtitle = "No matching results",
                        accent = Color(0xFF2563EB),
                        density = LiquidInfoBlockDensity.Compact,
                        modifier =
                            Modifier
                                .width(360.dp)
                                .testTag("compact-status"),
                    )
                }
            }
        }

        val blockBounds =
            composeRule
                .onNodeWithTag("compact-status")
                .fetchSemanticsNode()
                .boundsInRoot
        val titleBounds =
            composeRule
                .onNodeWithText("App list", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val subtitleBounds =
            composeRule
                .onNodeWithText("No matching results", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot

        with(composeRule.density) {
            assertTrue(titleBounds.right + 8.dp.toPx() <= subtitleBounds.left + 0.5f)
            assertTrue(abs(titleBounds.center.y - subtitleBounds.center.y) <= 1.dp.toPx())
            val blockHeight = blockBounds.height.toDp()
            val blockWidth = blockBounds.width.toDp()
            val titleHeight = titleBounds.height.toDp()
            val titleWidth = titleBounds.width.toDp()
            val subtitleHeight = subtitleBounds.height.toDp()
            val subtitleWidth = subtitleBounds.width.toDp()
            assertTrue(
                blockHeight <= 56.dp,
                "Compact block was ${blockWidth}x$blockHeight " +
                    "(title=${titleWidth}x$titleHeight, subtitle=${subtitleWidth}x$subtitleHeight)",
            )
            assertTrue(titleHeight <= 28.dp, "Compact title height was $titleHeight")
            assertTrue(subtitleHeight <= 28.dp, "Compact subtitle height was $subtitleHeight")
        }
    }
}

class LiquidInfoBlockTestApp : Application()
