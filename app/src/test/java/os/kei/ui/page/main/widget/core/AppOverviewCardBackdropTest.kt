package os.kei.ui.page.main.widget.core

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@Config(
    application = AppOverviewCardBackdropTestApp::class,
    sdk = [35],
)
class AppOverviewCardBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cardExportsStableChildBackdropAndProvidesContentColor() {
        lateinit var recompositionSignal: MutableIntState
        var parentBackdrop: Backdrop? = null
        val observedChildBackdrops = mutableListOf<Backdrop?>()
        val expectedContentColor = Color(0xFF2468AC)
        val observedContentColors = mutableListOf<Color>()

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                // Glass effects stay enabled here: since "export card material only when
                // active", cards re-export a child backdrop only while the liquid runtime
                // is on; the disabled path is covered by the solid-fallback test below.
                val signal = remember { mutableIntStateOf(0) }
                recompositionSignal = signal
                val parent = rememberLayerBackdrop()
                val revision = signal.intValue
                SideEffect { parentBackdrop = parent }

                androidx.compose.runtime.CompositionLocalProvider(LocalLiquidParentBackdrop provides parent) {
                    AppOverviewCard(
                        title = "Overview",
                        contentColor = expectedContentColor,
                        modifier =
                            Modifier
                                .size(180.dp)
                                .testTag("overview-card"),
                    ) {
                        val child = LocalLiquidParentBackdrop.current
                        val contentColor = LocalContentColor.current
                        SideEffect {
                            check(revision >= 0)
                            observedChildBackdrops += child
                            observedContentColors += contentColor
                        }
                        Box(modifier = Modifier.size(1.dp))
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag("overview-card")
            .assertWidthIsEqualTo(180.dp)
            .assertHeightIsEqualTo(180.dp)
        lateinit var settledChildBackdrop: Backdrop
        composeRule.runOnIdle {
            settledChildBackdrop = assertNotNull(observedChildBackdrops.last())
            assertNotSame(parentBackdrop, settledChildBackdrop)
            assertEquals(expectedContentColor, observedContentColors.last())
            recompositionSignal.intValue += 1
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertSame(settledChildBackdrop, observedChildBackdrops.last())
            assertEquals(expectedContentColor, observedContentColors.last())
        }
    }

    @Test
    fun standaloneCardKeepsParentBackdropAbsentForSolidFallback() {
        var observed = false
        var childBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                androidx.compose.runtime.CompositionLocalProvider(LocalLiquidControlsEnabled provides false) {
                    AppOverviewCard(
                        title = "Standalone",
                        modifier = Modifier.size(180.dp),
                    ) {
                        val child = LocalLiquidParentBackdrop.current
                        SideEffect {
                            observed = true
                            childBackdrop = child
                        }
                        Box(modifier = Modifier.size(1.dp))
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertTrue(observed)
            assertNull(childBackdrop)
        }
    }

    @Test
    fun sharedSurfaceKeepsClickAndLongClickButtonSemantics() {
        var clickCount = 0
        var longClickCount = 0

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppOverviewCard(
                    title = "Interactive overview",
                    onClick = { clickCount++ },
                    onLongClick = { longClickCount++ },
                ) {
                    Box(modifier = Modifier.size(1.dp))
                }
            }
        }

        composeRule
            .onNodeWithText("Interactive overview")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
            .performClick()
            .performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals(1, clickCount)
            assertEquals(1, longClickCount)
        }
    }

}

class AppOverviewCardBackdropTestApp : Application()
