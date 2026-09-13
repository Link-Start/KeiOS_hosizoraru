package os.kei.ui.page.main.github.importer

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.feature.github.domain.GitHubStarImportClassifier
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportApkVerification
import os.kei.feature.github.model.GitHubStarImportApkVerificationStatus
import os.kei.feature.github.model.GitHubStarImportQuality
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.GitHubStatusPalette
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = GitHubStarImportCandidateCardTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class GitHubStarImportCandidateCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedCandidateExposesOneCheckedActionAndClicksOnce() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                StarImportCandidateCard(
                    candidate = candidate,
                    selected = true,
                    trackedSelectable = false,
                    apkVerificationState = null,
                    onToggle = { clickCount++ },
                )
            }
        }

        composeRule.onAllNodes(CHECKBOX).assertCountEquals(1)
        composeRule.onAllNodes(CHECKBOX, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNode(CHECKBOX).assertIsOn().performClick()
        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun unselectedCandidateReportsUncheckedState() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                StarImportCandidateCard(
                    candidate = candidate,
                    selected = false,
                    trackedSelectable = false,
                    apkVerificationState = null,
                    onToggle = {},
                )
            }
        }

        composeRule.onNode(CHECKBOX).assertIsOff()
    }

    @Test
    fun trackedCandidateKeepsOneDisabledActionAndBlocksCallback() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                StarImportCandidateCard(
                    candidate = candidate.copy(alreadyTracked = true),
                    selected = false,
                    trackedSelectable = false,
                    apkVerificationState = null,
                    onToggle = { clickCount++ },
                )
            }
        }

        composeRule.onAllNodes(CHECKBOX).assertCountEquals(1)
        composeRule.onAllNodes(CHECKBOX, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNode(CHECKBOX).assertIsOff().assertIsNotEnabled().performClick()
        composeRule.runOnIdle { assertEquals(0, clickCount) }
    }

    @Test
    fun enabledAndDisabledCandidatesKeepTheSameOuterHeight() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Column {
                    StarImportCandidateCard(
                        candidate = candidate,
                        selected = false,
                        trackedSelectable = false,
                        apkVerificationState = null,
                        modifier = Modifier.testTag(ENABLED_CARD_TAG),
                        onToggle = {},
                    )
                    StarImportCandidateCard(
                        candidate = candidate.copy(alreadyTracked = true),
                        selected = false,
                        trackedSelectable = false,
                        apkVerificationState = null,
                        modifier = Modifier.testTag(DISABLED_CARD_TAG),
                        onToggle = {},
                    )
                }
            }
        }

        val enabledHeight = composeRule.onNodeWithTag(ENABLED_CARD_TAG).bounds().height
        val disabledHeight = composeRule.onNodeWithTag(DISABLED_CARD_TAG).bounds().height
        assertEquals(enabledHeight, disabledHeight)
    }

    @Test
    fun richCandidateAt360DpKeepsCompactContainedGeometry() {
        setRichCandidate(fontScale = 1f)

        assertRichCandidateGeometry(maxHeight = 140.dp)
    }

    @Test
    fun richCandidateAtLargeFontKeepsCompactContainedGeometry() {
        setRichCandidate(fontScale = 1.5f)

        assertRichCandidateGeometry(maxHeight = 186.dp)
    }

    @Test
    fun candidateColorsKeepLightAndDarkGlassRoles() {
        lateinit var lightCapture: StarImportCandidateThemeCapture
        lateinit var darkCapture: StarImportCandidateThemeCapture
        composeRule.setContent {
            Column {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    val capture = starImportCandidateThemeCapture(isDark = false)
                    SideEffect { lightCapture = capture }
                }
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Dark)) {
                    val capture = starImportCandidateThemeCapture(isDark = true)
                    SideEffect { darkCapture = capture }
                }
            }
        }

        composeRule.runOnIdle {
            assertStarImportCandidateColors(lightCapture, containerAlpha = 0.10f)
            assertStarImportCandidateColors(darkCapture, containerAlpha = 0.08f)
        }
    }

    private fun setRichCandidate(fontScale: Float) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale),
                ) {
                    StarImportCandidateCard(
                        candidate = richCandidate,
                        selected = true,
                        trackedSelectable = false,
                        apkVerificationState = richVerification,
                        modifier =
                            Modifier
                                .width(360.dp)
                                .testTag(RICH_CARD_TAG),
                        onToggle = {},
                    )
                }
            }
        }
    }

    private fun assertRichCandidateGeometry(maxHeight: Dp) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val quality = GitHubStarImportClassifier.classify(richCandidate)
        val labels =
            listOf(
                richCandidate.repository.fullName,
                richCandidate.repository.description,
                context.getString(R.string.github_star_import_candidate_selected),
                context.getString(quality.labelRes()),
                context.getString(
                    R.string.github_star_import_candidate_stars_pill,
                    richCandidate.repository.starCount.formatStarCount(),
                ),
                context.getString(R.string.github_star_import_candidate_fork_pill),
                richCandidate.repository.language,
                context.getString(
                    R.string.github_star_import_apk_pill_count,
                    richVerification.verification?.apkAssetCount,
                ),
                richCandidate.trackedApp.packageName,
            )
        val card = composeRule.onNodeWithTag(RICH_CARD_TAG).bounds()
        val contents = labels.map(composeRule::textBounds)
        val title = contents[0]
        val description = contents[1]
        val status = contents[2]

        assertBoundsContainedBy(card, *contents.toTypedArray())
        assertTrue(title.right <= status.left)
        assertTrue(description.right <= status.left)
        with(composeRule.density) {
            assertTrue(card.width.toDp() <= 360.dp)
            assertTrue(card.height.toDp() >= 48.dp)
            assertTrue(card.height.toDp() <= maxHeight)
            assertTrue(contents.last().width.toDp() <= 180.dp)
        }
    }

    private companion object {
        const val RICH_CARD_TAG = "github-star-import-rich-candidate"
        const val ENABLED_CARD_TAG = "github-star-import-enabled-candidate"
        const val DISABLED_CARD_TAG = "github-star-import-disabled-candidate"
        val CHECKBOX =
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.Checkbox,
            )
        val candidate = starImportCandidateFixture()
        val richCandidate =
            starImportCandidateFixture(
                owner = "a-deliberately-long-github-organization",
                repo = "a-deliberately-long-android-application",
                description = "A deliberately long Android APK release candidate description",
                language = "Kotlin Multiplatform With Long Metadata",
                starCount = 98_765,
                fork = true,
                packageName = "io.github.example.a.deliberately.long.android.application.package",
            )
        val richVerification =
            StarImportApkVerificationUiState(
                verification =
                    GitHubStarImportApkVerification(
                        owner = richCandidate.repository.owner,
                        repo = richCandidate.repository.repo,
                        status = GitHubStarImportApkVerificationStatus.HasApk,
                        apkAssetCount = 128,
                        packageName = richCandidate.trackedApp.packageName,
                    ),
            )
    }
}

@Composable
private fun starImportCandidateThemeCapture(isDark: Boolean): StarImportCandidateThemeCapture =
    StarImportCandidateThemeCapture(
        primary = MiuixTheme.colorScheme.primary,
        onBackground = MiuixTheme.colorScheme.onBackground,
        onBackgroundVariant = MiuixTheme.colorScheme.onBackgroundVariant,
        selected =
            starImportCandidateColors(
                selected = true,
                disabled = false,
                quality = GitHubStarImportQuality.NeedsReview,
                isDark = isDark,
            ),
        recommended =
            starImportCandidateColors(
                selected = false,
                disabled = false,
                quality = GitHubStarImportQuality.LikelyAndroid,
                isDark = isDark,
            ),
        ordinary =
            starImportCandidateColors(
                selected = false,
                disabled = false,
                quality = GitHubStarImportQuality.NeedsReview,
                isDark = isDark,
            ),
        tracked =
            starImportCandidateColors(
                selected = false,
                disabled = true,
                quality = GitHubStarImportQuality.LikelyAndroid,
                isDark = isDark,
            ),
    )

private data class StarImportCandidateThemeCapture(
    val primary: Color,
    val onBackground: Color,
    val onBackgroundVariant: Color,
    val selected: StarImportCandidateColors,
    val recommended: StarImportCandidateColors,
    val ordinary: StarImportCandidateColors,
    val tracked: StarImportCandidateColors,
)

private fun assertStarImportCandidateColors(
    capture: StarImportCandidateThemeCapture,
    containerAlpha: Float,
) {
    assertEquals(
        GitHubStatusPalette.Update.copy(alpha = containerAlpha),
        capture.selected.containerColor,
    )
    assertEquals(GitHubStatusPalette.Update.copy(alpha = 0.34f), capture.selected.borderColor)
    assertEquals(capture.onBackground, capture.selected.titleColor)

    assertEquals(
        GitHubStatusPalette.Active.copy(alpha = containerAlpha),
        capture.recommended.containerColor,
    )
    assertEquals(GitHubStatusPalette.Active.copy(alpha = 0.34f), capture.recommended.borderColor)
    assertEquals(capture.onBackground, capture.recommended.titleColor)

    assertEquals(capture.primary.copy(alpha = containerAlpha), capture.ordinary.containerColor)
    assertEquals(capture.primary.copy(alpha = 0.18f), capture.ordinary.borderColor)
    assertEquals(capture.onBackground, capture.ordinary.titleColor)

    assertEquals(
        capture.onBackgroundVariant.copy(alpha = containerAlpha),
        capture.tracked.containerColor,
    )
    assertEquals(capture.onBackgroundVariant.copy(alpha = 0.18f), capture.tracked.borderColor)
    assertEquals(capture.onBackground, capture.tracked.titleColor)
}

private fun starImportCandidateFixture(
    owner: String = "hosizoraru",
    repo: String = "KeiOS",
    description: String = "Android application with APK releases",
    language: String = "Kotlin",
    starCount: Int = 42,
    fork: Boolean = false,
    packageName: String = "os.kei",
): GitHubRepositoryImportCandidate {
    val repoUrl = "https://github.com/$owner/$repo"
    val repository =
        GitHubRepositoryCandidate(
            owner = owner,
            repo = repo,
            repoUrl = repoUrl,
            description = description,
            language = language,
            starCount = starCount,
            fork = fork,
            sourceType = GitHubRepositoryDiscoverySourceType.StarList,
            matchReason = GitHubRepositoryCandidateMatchReason.Starred,
        )
    return GitHubRepositoryImportCandidate(
        repository = repository,
        trackedApp =
            GitHubTrackedApp(
                repoUrl = repoUrl,
                owner = owner,
                repo = repo,
                packageName = packageName,
                appLabel = repo,
            ),
        alreadyTracked = false,
        score = 100,
    )
}

private fun ComposeTestRule.textBounds(text: String): Rect =
    onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

private fun assertBoundsContainedBy(
    container: Rect,
    vararg contents: Rect,
) {
    contents.forEach { content ->
        assertTrue(content.left >= container.left)
        assertTrue(content.top >= container.top)
        assertTrue(content.right <= container.right)
        assertTrue(content.bottom <= container.bottom)
    }
}

internal class GitHubStarImportCandidateCardTestApp : Application()
