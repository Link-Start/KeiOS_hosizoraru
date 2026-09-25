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
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
    fun explicitModesIgnoreTheSystemAndSystemModesFollowIt() {
        data class Case(val mode: ColorSchemeMode?, val systemDark: Boolean, val expected: Boolean)
        listOf(
            Case(ColorSchemeMode.Light, systemDark = true, expected = false),
            Case(ColorSchemeMode.MonetLight, systemDark = true, expected = false),
            Case(ColorSchemeMode.Dark, systemDark = false, expected = true),
            Case(ColorSchemeMode.MonetDark, systemDark = false, expected = true),
            Case(ColorSchemeMode.System, systemDark = false, expected = false),
            Case(ColorSchemeMode.System, systemDark = true, expected = true),
            Case(ColorSchemeMode.MonetSystem, systemDark = false, expected = false),
            Case(ColorSchemeMode.MonetSystem, systemDark = true, expected = true),
            // No controller mode (direct colours) follows the system.
            Case(null, systemDark = false, expected = false),
            Case(null, systemDark = true, expected = true),
        ).forEach { case ->
            assertEquals(case.expected, resolveAppDarkTheme(case.mode, case.systemDark), "$case")
        }
    }

    /** The wiring: [isAppInDarkTheme] reads the controller's mode rather than only the system flag. */
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
}
