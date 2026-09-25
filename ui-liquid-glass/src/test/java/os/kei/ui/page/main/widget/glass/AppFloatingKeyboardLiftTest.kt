package os.kei.ui.page.main.widget.glass

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

class AppFloatingKeyboardLiftTest {
    @Test
    fun keyboardLiftFollowsTheImeLessTheRestingGapAndRestsWhenHidden() {
        data class Case(val label: String, val imeBottom: Dp, val restingBottomGap: Dp, val expected: Dp)
        listOf(
            Case("no resting gap: IME height plus the focused lift", 320.dp, 0.dp, 338.dp),
            Case("a resting gap is already part of the way up", 520.dp, 390.dp, 148.dp),
            Case("IME hidden behind the navigation bar", 24.dp, 0.dp, 0.dp),
        ).forEach { case ->
            assertEquals(
                case.expected,
                appFloatingKeyboardLiftTarget(
                    imeBottom = case.imeBottom,
                    navigationBottom = 24.dp,
                    focusedLift = 18.dp,
                    restingBottomGap = case.restingBottomGap,
                ),
                case.label,
            )
        }
    }

    @Test
    fun floatingDockBottomTargetTracksTheFullAndCompactBottomBar() {
        // Expanded: above the full bottom bar. Compact: aligned with the compact bar.
        assertEquals(112.dp, appFloatingDockBottomTarget(contentBottomPadding = 136.dp, bottomBarVisible = true))
        assertEquals(32.dp, appFloatingDockBottomTarget(contentBottomPadding = 136.dp, bottomBarVisible = false))
    }
}
