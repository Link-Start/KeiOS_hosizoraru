package os.kei.ui.page.main.student

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.student.component.GuidePassiveMetadataPillMaxWidth
import os.kei.ui.page.main.student.component.GuidePassiveMetadataPillMinHeight
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class StudentGuidePassiveMetadataPillTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactPillKeepsReadOnlySemanticsAndNaturalLargeFontHeight() {
        var density = 1f

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val baseDensity = LocalDensity.current
                density = baseDensity.density
                Column {
                    CompositionLocalProvider(
                        LocalDensity provides Density(baseDensity.density, fontScale = 1f),
                    ) {
                        TestPassiveMetadataPill(
                            label = ONE_X_LABEL,
                            modifier = Modifier.testTag(ONE_X_TAG),
                        )
                    }
                    CompositionLocalProvider(
                        LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                    ) {
                        TestPassiveMetadataPill(
                            label = LARGE_FONT_LABEL,
                            modifier = Modifier.testTag(LARGE_FONT_TAG),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(ONE_X_TAG).assertHeightIsEqualTo(26.dp)
        composeRule
            .onNodeWithText(ONE_X_LABEL)
            .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
            .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
            .assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))

        val oneXPillBounds =
            composeRule.onNodeWithTag(ONE_X_TAG).fetchSemanticsNode().boundsInRoot
        val largeFontPillBounds =
            composeRule.onNodeWithTag(LARGE_FONT_TAG).fetchSemanticsNode().boundsInRoot
        val largeFontTextBounds =
            composeRule.onNodeWithText(LARGE_FONT_LABEL).fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            assertTrue(largeFontPillBounds.height >= oneXPillBounds.height)
            assertTrue(largeFontPillBounds.height < 48f * density)
            assertTrue(largeFontTextBounds.left >= largeFontPillBounds.left)
            assertTrue(largeFontTextBounds.right <= largeFontPillBounds.right)
            assertTrue(largeFontTextBounds.top - largeFontPillBounds.top >= 3f * density)
            assertTrue(largeFontPillBounds.bottom - largeFontTextBounds.bottom >= 3f * density)
        }
    }

    @Test
    fun dynamicPillLeavesTitleSpaceBesideThreeActionsAtLargeFontOn360Dp() {
        var density = 1f

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val baseDensity = LocalDensity.current
                density = baseDensity.density
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                ) {
                    Row(
                        modifier = Modifier.width(328.dp).testTag(CROWDED_ROW_TAG),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "A long gallery title that must keep visible space",
                            modifier = Modifier.weight(1f).testTag(TITLE_TAG),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TestPassiveMetadataPill(
                            label = "Tactical support role with a long localized name",
                            modifier =
                                Modifier
                                    .widthIn(max = GuidePassiveMetadataPillMaxWidth)
                                    .testTag(DYNAMIC_PILL_TAG),
                        )
                        repeat(3) { index ->
                            Box(
                                modifier =
                                    Modifier
                                        .size(48.dp)
                                        .testTag("$ACTION_TAG_PREFIX$index"),
                            )
                        }
                    }
                }
            }
        }

        val titleBounds = composeRule.onNodeWithTag(TITLE_TAG).fetchSemanticsNode().boundsInRoot
        val pillBounds = composeRule.onNodeWithTag(DYNAMIC_PILL_TAG).fetchSemanticsNode().boundsInRoot
        val actionBounds =
            List(3) { index ->
                composeRule
                    .onNodeWithTag("$ACTION_TAG_PREFIX$index")
                    .fetchSemanticsNode()
                    .boundsInRoot
            }

        composeRule.runOnIdle {
            assertTrue(titleBounds.width > 0f)
            assertTrue(titleBounds.right <= pillBounds.left)
            assertTrue(pillBounds.right <= actionBounds.first().left)
            actionBounds.zipWithNext().forEach { (left, right) ->
                assertTrue(left.right <= right.left)
            }
            assertTrue(pillBounds.width <= GuidePassiveMetadataPillMaxWidth.value * density + 1f)
        }
    }

}

@androidx.compose.runtime.Composable
private fun TestPassiveMetadataPill(
    label: String,
    modifier: Modifier = Modifier,
) {
    StatusPill(
        label = label,
        color = Color(0xFF3B82F6),
        modifier = modifier.heightIn(min = GuidePassiveMetadataPillMinHeight),
        size = AppStatusPillSize.Compact,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val ONE_X_LABEL = "Read-only metadata"
private const val LARGE_FONT_LABEL = "Large-font metadata"
private const val ONE_X_TAG = "passive-metadata-one-x"
private const val LARGE_FONT_TAG = "passive-metadata-large-font"
private const val CROWDED_ROW_TAG = "passive-metadata-crowded-row"
private const val TITLE_TAG = "passive-metadata-title"
private const val DYNAMIC_PILL_TAG = "passive-metadata-dynamic-pill"
private const val ACTION_TAG_PREFIX = "passive-metadata-action-"
