package os.kei.ui.page.main.ba

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
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
    application = BaLiquidSurfacesInteractionTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class BaLiquidSurfacesInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun passivePanelsAvoidPressPaddingAndButtonSemanticsAcrossMaterialPaths() {
        setPanels()

        listOf("active-passive", "fallback-passive").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertHeightIsEqualTo(48.dp)
            val panelTree = hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))
            composeRule
                .onAllNodes(panelTree and hasClickAction(), useUnmergedTree = true)
                .assertCountEquals(0)
            composeRule
                .onAllNodes(
                    panelTree and SemanticsMatcher.keyIsDefined(SemanticsProperties.Role),
                    useUnmergedTree = true,
                ).assertCountEquals(0)
        }
    }

    @Test
    fun interactivePanelsKeepPressPaddingAndButtonSemanticsAcrossMaterialPaths() {
        setPanels()

        listOf("active-interactive", "fallback-interactive").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertHeightIsEqualTo(56.dp)
            val panelTree = hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))
            composeRule
                .onNode(panelTree and hasClickAction(), useUnmergedTree = true)
                .assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
    }

    private fun setPanels() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                Column(modifier = Modifier.width(200.dp)) {
                    TestPanel(tag = "active-passive", backdrop = backdrop)
                    TestPanel(tag = "active-interactive", backdrop = backdrop, onClick = {})
                    TestPanel(tag = "fallback-passive", backdrop = null)
                    TestPanel(tag = "fallback-interactive", backdrop = null, onClick = {})
                }
            }
        }
    }
}

@Composable
private fun TestPanel(
    tag: String,
    backdrop: Backdrop?,
    onClick: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.testTag(tag)) {
        BaLiquidPanel(
            backdrop = backdrop,
            effectsEnabled = backdrop != null,
            contentPadding = PaddingValues.Zero,
            onClick = onClick,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
            )
        }
    }
}

private fun sourceFile(relativePath: String): String {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    val sourceFile =
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .map { directory -> File(directory, relativePath) }
            .firstOrNull(File::isFile)
    return requireNotNull(sourceFile) {
        "Unable to locate $relativePath from $workingDirectory"
    }.readText()
}

class BaLiquidSurfacesInteractionTestApp : Application()
