package os.kei.ui.page.main.widget.status

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.assertTrue
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
    application = Application::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class StatusIconPillTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultAtomKeepsBaGeometryAndPassiveLabelSemantics() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(LocalLiquidParentBackdrop provides null) {
                    StatusIconPill(
                        label = DEFAULT_LABEL,
                        color = ACCENT,
                        icon = TestStatusIcon,
                        modifier = Modifier.testTag(DEFAULT_TAG),
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag(DEFAULT_TAG)
            .assertWidthIsEqualTo(28.dp)
            .assertHeightIsEqualTo(22.dp)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(DEFAULT_LABEL),
                ),
            ).assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
            .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
            .assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        composeRule.onAllNodesWithContentDescription(DEFAULT_LABEL).assertCountEquals(1)
    }

    @Test
    fun compactAtomLeavesTitleSpaceAt360DpAndLargeFont() {
        var density = 1f
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val baseDensity = LocalDensity.current
                density = baseDensity.density
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                    LocalLiquidParentBackdrop provides null,
                ) {
                    Row(
                        modifier = Modifier.width(328.dp).testTag(ROW_TAG),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "A long localized component laboratory status title",
                            modifier = Modifier.weight(1f).testTag(TITLE_TAG),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        StatusIconPill(
                            label = LARGE_FONT_LABEL,
                            color = ACCENT,
                            icon = TestStatusIcon,
                            modifier = Modifier.testTag(LARGE_FONT_TAG),
                            width = 32.dp,
                            height = 24.dp,
                            iconSize = 15.dp,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(LARGE_FONT_TAG).assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(24.dp)
        val rowBounds = composeRule.onNodeWithTag(ROW_TAG).fetchSemanticsNode().boundsInRoot
        val titleBounds = composeRule.onNodeWithTag(TITLE_TAG).fetchSemanticsNode().boundsInRoot
        val pillBounds = composeRule.onNodeWithTag(LARGE_FONT_TAG).fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            assertTrue(titleBounds.width > 72f * density)
            assertTrue(titleBounds.right <= pillBounds.left)
            assertTrue(pillBounds.right <= rowBounds.right)
        }
    }

    @Test
    fun standaloneLightAndInheritedDarkPathsBothRenderThePassiveAtom() {
        composeRule.setContent {
            Column {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    CompositionLocalProvider(LocalLiquidParentBackdrop provides null) {
                        StatusIconPill(
                            label = LIGHT_LABEL,
                            color = ACCENT,
                            icon = TestStatusIcon,
                            modifier = Modifier.testTag(LIGHT_TAG),
                        )
                    }
                }
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Dark)) {
                    val parentBackdrop = rememberLayerBackdrop()
                    CompositionLocalProvider(
                        LocalLiquidControlsEnabled provides true,
                        LocalLiquidParentBackdrop provides parentBackdrop,
                    ) {
                        StatusIconPill(
                            label = DARK_LABEL,
                            color = ACCENT,
                            icon = TestStatusIcon,
                            modifier = Modifier.testTag(DARK_TAG),
                        )
                    }
                }
            }
        }

        listOf(LIGHT_TAG, DARK_TAG).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertWidthIsEqualTo(28.dp).assertHeightIsEqualTo(22.dp)
        }
        composeRule.onNodeWithContentDescription(LIGHT_LABEL).assertExists()
        composeRule.onNodeWithContentDescription(DARK_LABEL).assertExists()
    }
}

private val TestStatusIcon =
    ImageVector
        .Builder(
            name = "TestStatusIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 5f)
                lineTo(19f, 5f)
                lineTo(19f, 19f)
                lineTo(5f, 19f)
                close()
            }
        }.build()

private val ACCENT = Color(0xFF60A5FA)

private const val DEFAULT_LABEL = "Cached"
private const val LARGE_FONT_LABEL = "Iteration queued"
private const val LIGHT_LABEL = "Light status"
private const val DARK_LABEL = "Dark status"
private const val DEFAULT_TAG = "status-icon-pill-default"
private const val LARGE_FONT_TAG = "status-icon-pill-large-font"
private const val LIGHT_TAG = "status-icon-pill-light"
private const val DARK_TAG = "status-icon-pill-dark"
private const val ROW_TAG = "status-icon-pill-row"
private const val TITLE_TAG = "status-icon-pill-title"
