package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Hidden chrome stays composed — so showing it again does not compose and first-draw a glass control on the
 * frame a scroll starts — and is invisible to accessibility while hidden, where merely leaving it unplaced
 * kept it in the tree with a stale position.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class KeepComposedUnplacedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hiddenContentKeepsItsCompositionAndLeavesTheSemanticsTree() {
        var visible by mutableStateOf(true)
        var created = 0
        composeRule.setContent {
            Box(Modifier.keepComposedUnplaced(visible)) {
                // Runs only when the content enters the composition, not when it recomposes.
                remember { created++ }
                Box(Modifier.size(40.dp).semantics { contentDescription = "compact" })
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("compact").assertCountEquals(1)
        assertEquals(1, created)

        visible = false
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("compact").assertCountEquals(0)

        visible = true
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("compact").assertCountEquals(1)
        assertEquals(1, created, "showing it again must not compose the content anew")
    }
}
