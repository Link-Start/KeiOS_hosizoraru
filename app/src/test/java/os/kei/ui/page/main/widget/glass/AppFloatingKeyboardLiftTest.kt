package os.kei.ui.page.main.widget.glass

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

class AppFloatingKeyboardLiftTest {
    @Test
    fun keyboardLiftUsesImeHeightWhenNoRestingGapIsProvided() {
        assertEquals(
            338.dp,
            appFloatingKeyboardLiftTarget(
                imeBottom = 320.dp,
                navigationBottom = 24.dp,
                focusedLift = 18.dp
            )
        )
    }

    @Test
    fun keyboardLiftAccountsForRestingBottomGap() {
        assertEquals(
            148.dp,
            appFloatingKeyboardLiftTarget(
                imeBottom = 520.dp,
                navigationBottom = 24.dp,
                focusedLift = 18.dp,
                restingBottomGap = 390.dp
            )
        )
    }

    @Test
    fun keyboardLiftStaysAtRestWhenImeIsHidden() {
        assertEquals(
            0.dp,
            appFloatingKeyboardLiftTarget(
                imeBottom = 24.dp,
                navigationBottom = 24.dp,
                focusedLift = 18.dp
            )
        )
    }
}
