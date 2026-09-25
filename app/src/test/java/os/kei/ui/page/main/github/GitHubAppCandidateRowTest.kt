package os.kei.ui.page.main.github

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.sheet.GitHubTrackAppPickerHeader
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class GitHubAppCandidateRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun candidateRowKeepsCompactHeightAndOneRadioAction() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                GitHubAppCandidateRow(
                    app = candidate,
                    selected = true,
                    showInstallSource = false,
                    onClick = { clickCount++ },
                    modifier = Modifier.testTag("candidate-row"),
                )
            }
        }

        composeRule
            .onNodeWithTag("candidate-row")
            .assertHeightIsEqualTo(72.dp)

        val radioButton =
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.RadioButton,
            )
        composeRule.onAllNodes(radioButton).assertCountEquals(1)
        composeRule.onAllNodes(radioButton, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNode(radioButton).performClick()
        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun appPickerHeaderKeepsResultCountRightAlignedAtLargeFont() {
        val title = "App 候选"
        val resultCount = "显示 138 / 157 个"
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = 1.5f),
                ) {
                    GitHubTrackAppPickerHeader(
                        title = title,
                        resultCount = resultCount,
                        modifier =
                            Modifier
                                .width(360.dp)
                                .testTag("app-picker-header"),
                    )
                }
            }
        }

        val headerBounds =
            composeRule
                .onNodeWithTag("app-picker-header")
                .assertWidthIsEqualTo(360.dp)
                .fetchSemanticsNode()
                .boundsInRoot
        val titleBounds =
            composeRule
                .onNodeWithText(title, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val resultCountBounds =
            composeRule
                .onNodeWithText(resultCount, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot

        assertTrue(titleBounds.right <= resultCountBounds.left)
        assertTrue(resultCountBounds.right <= headerBounds.right)
        with(composeRule.density) {
            assertTrue(abs(titleBounds.center.y - resultCountBounds.center.y).toDp() <= 1.dp)
        }
    }

    @Test
    fun longCandidateContentAtLargeFontKeepsTextAndInstallSourceSeparated() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = 1.5f)
                ) {
                    GitHubAppCandidateRow(
                        app = longCandidate,
                        selected = false,
                        showInstallSource = true,
                        onClick = {},
                        modifier =
                            Modifier
                                .width(360.dp)
                                .testTag("large-font-candidate-row"),
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag("large-font-candidate-row")
            .assertWidthIsEqualTo(360.dp)
            .assertHeightIsAtLeast(83.dp)

        val titleBounds =
            composeRule
                .onNodeWithText(longCandidate.label, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val packageBounds =
            composeRule
                .onNodeWithText(longCandidate.packageName, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val installSourceNode =
            composeRule.onNodeWithText(longCandidate.installSourceLabel, useUnmergedTree = true)
        val installSourceBounds = installSourceNode.fetchSemanticsNode().boundsInRoot

        installSourceNode
            .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
            .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
            .assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        val radioButton =
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.RadioButton,
            )
        composeRule.onAllNodes(radioButton).assertCountEquals(1)
        composeRule.onAllNodes(radioButton, useUnmergedTree = true).assertCountEquals(1)

        assertTrue(titleBounds.bottom <= packageBounds.top)
        assertTrue(titleBounds.right <= installSourceBounds.left)
        assertTrue(packageBounds.right <= installSourceBounds.left)
        with(composeRule.density) {
            assertTrue(titleBounds.height.toDp() <= 30.dp)
            assertTrue(packageBounds.height.toDp() <= 27.dp)
        }
    }

    @Test
    fun selectedAppCardAtLargeFontKeepsLongTextAndInstallSourceSeparated() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = 1.5f)
                ) {
                    Column(
                        modifier =
                            Modifier
                                .width(360.dp)
                                .testTag("large-font-selected-app-card"),
                    ) {
                        GitHubSelectedAppCard(
                            selectedApp = longCandidate,
                            showInstallSource = true,
                        )
                    }
                }
            }
        }

        val cardBounds =
            composeRule
                .onNodeWithTag("large-font-selected-app-card")
                .assertWidthIsEqualTo(360.dp)
                .fetchSemanticsNode()
                .boundsInRoot
        val titleBounds =
            composeRule
                .onNodeWithText(longCandidate.label, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val packageBounds =
            composeRule
                .onNodeWithText(longCandidate.packageName, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val installSourceNode =
            composeRule.onNodeWithText(longCandidate.installSourceLabel, useUnmergedTree = true)
        val installSourceBounds = installSourceNode.fetchSemanticsNode().boundsInRoot

        installSourceNode
            .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
            .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
            .assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))

        assertTrue(titleBounds.bottom <= packageBounds.top)
        assertTrue(titleBounds.right <= installSourceBounds.left)
        assertTrue(packageBounds.right <= installSourceBounds.left)
        assertTrue(installSourceBounds.right <= cardBounds.right)
    }

    private companion object {
        val candidate =
            InstalledAppItem(
                label = "KeiOS",
                packageName = "os.kei",
            )
        val longCandidate =
            InstalledAppItem(
                label = "A deliberately long localized application name for compact layout",
                packageName = "os.kei.a.deliberately.long.application.package.name",
                installSourceLabel = "A deliberately long application store source",
            )
    }
}
