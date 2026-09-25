package os.kei.ui.page.main.widget

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppThemeAppearanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun explicitModesResolveIndependentlyFromSystemAppearance() {
        assertFalse(resolveAppDarkTheme(ColorSchemeMode.Light, systemInDarkTheme = true))
        assertFalse(resolveAppDarkTheme(ColorSchemeMode.MonetLight, systemInDarkTheme = true))
        assertTrue(resolveAppDarkTheme(ColorSchemeMode.Dark, systemInDarkTheme = false))
        assertTrue(resolveAppDarkTheme(ColorSchemeMode.MonetDark, systemInDarkTheme = false))
    }

    @Test
    fun systemModesAndDirectColorsFollowSystemAppearance() {
        assertFalse(resolveAppDarkTheme(ColorSchemeMode.System, systemInDarkTheme = false))
        assertTrue(resolveAppDarkTheme(ColorSchemeMode.System, systemInDarkTheme = true))
        assertFalse(resolveAppDarkTheme(ColorSchemeMode.MonetSystem, systemInDarkTheme = false))
        assertTrue(resolveAppDarkTheme(ColorSchemeMode.MonetSystem, systemInDarkTheme = true))
        assertFalse(resolveAppDarkTheme(colorSchemeMode = null, systemInDarkTheme = false))
        assertTrue(resolveAppDarkTheme(colorSchemeMode = null, systemInDarkTheme = true))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun forcedLightThemeOverridesDarkSystemAppearance() {
        var observedDarkTheme = true

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val isDark = isAppInDarkTheme()
                SideEffect { observedDarkTheme = isDark }
            }
        }

        composeRule.runOnIdle { assertFalse(observedDarkTheme) }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun forcedDarkThemeOverridesLightSystemAppearance() {
        var observedDarkTheme = false

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Dark)) {
                val isDark = isAppInDarkTheme()
                SideEffect { observedDarkTheme = isDark }
            }
        }

        composeRule.runOnIdle { assertTrue(observedDarkTheme) }
    }
}
