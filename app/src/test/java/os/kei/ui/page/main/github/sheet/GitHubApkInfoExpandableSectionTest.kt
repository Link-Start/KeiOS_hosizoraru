package os.kei.ui.page.main.github.sheet

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.feature.github.model.GitHubApkManifestNode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = GitHubApkInfoExpandableSectionTestApp::class,
    sdk = [35],
    qualifiers = "en-rUS-w360dp-h800dp-xxhdpi",
)
class GitHubApkInfoExpandableSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun manifestGroupExposesOneHeaderActionAndKeepsDetailsPassive() {
        var clickCount = 0
        setLargeFontContent {
            ManifestNodeGroupCard(
                title = MANIFEST_TITLE,
                nodes = listOf(manifestNode),
                expanded = true,
                onToggle = { clickCount++ },
            )
        }

        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule
            .onAllNodes(hasText(MANIFEST_DETAIL) and hasClickAction(), useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onNodeWithText(MANIFEST_TITLE).performClick()
        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun meaningHeaderIsTheOnlyToggleAndExpandsOncePerClick() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val title = context.getString(R.string.github_apk_info_section_meaning)
        val firstEntry = context.getString(R.string.github_apk_info_meaning_version)
        setLargeFontContent { ApkInfoMeaningSection() }

        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithText(firstEntry).assertDoesNotExist()

        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithText(firstEntry).assertExists()
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)

        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithText(firstEntry).assertDoesNotExist()
    }

    @Test
    fun meaningHeaderAtLargeFontStaysInsideACompact360DpStage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val title = context.getString(R.string.github_apk_info_section_meaning)
        val summary = context.getString(R.string.github_apk_info_meaning_summary)
        setLargeFontContent { ApkInfoMeaningSection() }

        val stage = composeRule.onNodeWithTag(STAGE_TAG).fetchSemanticsNode().boundsInRoot
        val titleBounds =
            composeRule
                .onNodeWithText(title, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val summaryBounds =
            composeRule
                .onNodeWithText(summary, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot

        assertTrue(titleBounds.bottom <= summaryBounds.top)
        listOf(titleBounds, summaryBounds).forEach { bounds ->
            assertTrue(bounds.left >= stage.left)
            assertTrue(bounds.right <= stage.right)
            assertTrue(bounds.top >= stage.top)
            assertTrue(bounds.bottom <= stage.bottom)
        }
        with(composeRule.density) {
            val stageHeight = stage.height.toDp()
            assertTrue(stageHeight <= 116.dp, "Expected a compact header, actual height was $stageHeight")
        }
    }

    private fun setLargeFontContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
            ) {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    Column(
                        modifier =
                            Modifier
                                .width(360.dp)
                                .testTag(STAGE_TAG),
                    ) {
                        content()
                    }
                }
            }
        }
    }

    private companion object {
        const val STAGE_TAG = "github-apk-expandable-stage"
        const val MANIFEST_TITLE = "Exported components"
        const val MANIFEST_DETAIL = "<activity> A deliberately long exported student activity · exported=true"
        val manifestNode =
            GitHubApkManifestNode(
                tagName = "activity",
                displayName = "A deliberately long exported student activity",
                attributes = mapOf("exported" to "true"),
            )
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

internal class GitHubApkInfoExpandableSectionTestApp : Application()
