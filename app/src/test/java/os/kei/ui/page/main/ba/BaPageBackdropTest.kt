package os.kei.ui.page.main.ba

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import os.kei.ui.page.main.ba.support.BaCraftFunction
import os.kei.ui.page.main.host.pager.MainPageBackdropSet
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
)
class BaPageBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun baPageUsesStableCanvasContentAndIndependentVisibleSheetAfterEntry() {
        lateinit var recompositionSignal: MutableIntState
        var observedBackdrops: MainPageBackdropSet? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val signal = remember { mutableIntStateOf(0) }
                recompositionSignal = signal
                val backdrops =
                    rememberBaPageBackdropSet(
                        pageBackdropEffectsEnabled = true,
                        sheetBackdropVisible = true,
                    )
                val revision = signal.intValue

                SideEffect {
                    observedBackdrops = backdrops
                    check(revision >= 0)
                }
                Box(modifier = Modifier.size(1.dp))
            }
        }

        composeRule.waitForIdle()
        lateinit var settledBackdrops: MainPageBackdropSet
        composeRule.runOnIdle {
            settledBackdrops = requireNotNull(observedBackdrops)
            assertSame(settledBackdrops.topBarProducer, settledBackdrops.contentProducer)
            assertNotSame(settledBackdrops.contentProducer, settledBackdrops.contentMaterial)
            assertNotSame(settledBackdrops.topBarProducer, settledBackdrops.sheetProducer)
            recompositionSignal.intValue += 1
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val recomposedBackdrops = requireNotNull(observedBackdrops)
            assertSame(settledBackdrops.topBarProducer, recomposedBackdrops.topBarProducer)
            assertSame(settledBackdrops.contentProducer, recomposedBackdrops.contentProducer)
            assertSame(settledBackdrops.contentMaterial, recomposedBackdrops.contentMaterial)
            assertSame(settledBackdrops.sheetProducer, recomposedBackdrops.sheetProducer)
        }
    }

    @Test
    fun baPageReusesTopBarBackdropWhileEverySheetIsHidden() {
        var observedBackdrops: MainPageBackdropSet? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrops =
                    rememberBaPageBackdropSet(
                        pageBackdropEffectsEnabled = true,
                        sheetBackdropVisible = false,
                    )
                SideEffect { observedBackdrops = backdrops }
                Box(modifier = Modifier.size(1.dp))
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val backdrops = requireNotNull(observedBackdrops)
            assertSame(backdrops.topBarProducer, backdrops.contentProducer)
            assertSame(backdrops.contentProducer, backdrops.sheetProducer)
            assertNotSame(backdrops.contentProducer, backdrops.contentMaterial)
        }
    }

    @Test
    fun sheetBackdropVisibilityIncludesEveryBaPageSheet() {
        assertFalse(BaOfficeChromeUiState().hasVisiblePageSheet)
        listOf(
            BaOfficeChromeUiState(showSettingsSheet = true),
            BaOfficeChromeUiState(showAccountManagementSheet = true),
            BaOfficeChromeUiState(showNotificationSettingsSheet = true),
            BaOfficeChromeUiState(showApLimitToolsSheet = true),
            BaOfficeChromeUiState(showCafeApToolsSheet = true),
            BaOfficeChromeUiState(dailyDoneSheet = BaDailyDoneSheetUiState(show = true)),
            BaOfficeChromeUiState(cafeCooldownEditTarget = BaCafeCooldownEditTarget.Headpat),
            BaOfficeChromeUiState(
                craftSlotEditTarget = BaCraftSlotEditTarget(function = BaCraftFunction.Generate, index = 0),
            ),
        ).forEach { state ->
            assertTrue(state.hasVisiblePageSheet)
        }
    }

}
