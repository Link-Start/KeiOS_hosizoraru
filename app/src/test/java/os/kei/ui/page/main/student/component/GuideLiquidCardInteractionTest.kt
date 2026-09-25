package os.kei.ui.page.main.student.component

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
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
class GuideLiquidCardInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onlyRealActionsExposeButtonSemanticsAndInteractiveCardsKeepTheirGeometry() {
        var actionClicks = 0

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Column {
                    GuideLiquidCard(
                        modifier = Modifier.width(120.dp).testTag("static-guide-card"),
                        shadow = false,
                    ) {
                        Box(Modifier.fillMaxWidth().height(40.dp))
                    }
                    GuideLiquidCard(
                        modifier = Modifier.width(120.dp).testTag("action-guide-card"),
                        shadow = false,
                        onClick = { actionClicks++ },
                    ) {
                        Box(Modifier.fillMaxWidth().height(40.dp))
                    }
                    GuideLiquidCard(
                        modifier = Modifier.width(120.dp).testTag("passive-guide-card"),
                        isInteractive = false,
                        shadow = false,
                    ) {
                        Box(Modifier.fillMaxWidth().height(40.dp))
                    }
                }
            }
        }

        composeRule.onNodeWithTag("static-guide-card").assertHeightIsEqualTo(48.dp)
        composeRule.onNodeWithTag("action-guide-card").assertHeightIsEqualTo(48.dp)
        composeRule.onNodeWithTag("passive-guide-card").assertHeightIsEqualTo(40.dp)

        val actionNodes =
            composeRule
                .onAllNodes(hasClickAction(), useUnmergedTree = true)
                .assertCountEquals(1)
        actionNodes[0]
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        composeRule.runOnIdle { assertEquals(1, actionClicks) }
    }
}
