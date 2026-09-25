package os.kei.ui.page.main.github.sheet

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
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
import os.kei.feature.github.model.FdroidAppSearchCandidate
import os.kei.feature.github.model.FdroidAppSearchSource
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidate
import os.kei.feature.github.model.GitHubRepositoryCandidateMatchReason
import os.kei.feature.github.model.GitHubRepositoryDiscoverySourceType
import os.kei.feature.github.model.GitHubTrackedApp
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = GitHubCandidateChoiceRowTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class GitHubCandidateChoiceRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun repositoryCandidateExposesOneSelectedRadioAction() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                RepositoryScanCandidateRow(
                    candidate = repositoryCandidate,
                    recommended = true,
                    selected = true,
                    onClick = { clickCount++ },
                    modifier = Modifier.testTag(REPOSITORY_ROW_TAG),
                )
            }
        }

        assertSingleSelectedRadioAction { clickCount }
        val height =
            composeRule
                .onNodeWithTag(REPOSITORY_ROW_TAG)
                .heightDp(composeRule.density)
        assertTrue(height >= 48.dp)
        assertTrue(height <= LEGACY_REPOSITORY_SELECTED_HEIGHT)
    }

    @Test
    fun fdroidCandidateExposesOneSelectedRadioAction() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                FdroidCandidateRow(
                    candidate = fdroidCandidate,
                    selected = true,
                    onClick = { clickCount++ },
                    modifier = Modifier.testTag(FDROID_ROW_TAG),
                )
            }
        }

        assertSingleSelectedRadioAction { clickCount }
        val height =
            composeRule
                .onNodeWithTag(FDROID_ROW_TAG)
                .heightDp(composeRule.density)
        assertTrue(height >= 48.dp)
        assertTrue(height <= LEGACY_FDROID_SELECTED_HEIGHT)
    }

    @Test
    fun longRepositoryCandidateAtLargeFontKeepsTextAndTrailingPillsSeparated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recommendedLabel =
            context.getString(R.string.github_track_sheet_repo_candidate_recommended)
        val starLabel =
            context.getString(R.string.github_track_sheet_repo_candidate_stars_format, "98.7k")
        val forkLabel = context.getString(R.string.github_track_sheet_repo_candidate_fork)
        val meta = "release-2026.07.17 · keios-a-deliberately-long-universal-release.apk"

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = 1.5f),
                ) {
                    RepositoryScanCandidateRow(
                        candidate = longRepositoryCandidate,
                        recommended = true,
                        selected = false,
                        onClick = {},
                        modifier =
                            Modifier
                                .width(360.dp)
                                .testTag(LARGE_REPOSITORY_ROW_TAG),
                    )
                }
            }
        }

        val title = composeRule.textBounds(longRepositoryCandidate.repository.fullName)
        val summary = composeRule.textBounds(longRepositoryCandidate.repository.description)
        val details = composeRule.textBounds(meta)
        val recommended = composeRule.textBounds(recommendedLabel)
        val stars = composeRule.textBounds(starLabel)
        val fork = composeRule.textBounds(forkLabel)
        val row = composeRule.onNodeWithTag(LARGE_REPOSITORY_ROW_TAG).bounds()

        composeRule.onNode(RADIO_BUTTON).assertIsNotSelected()
        assertTrue(title.bottom <= summary.top)
        assertTrue(summary.bottom <= details.top)
        assertTrue(title.right <= recommended.left)
        assertTrue(summary.right <= recommended.left)
        assertTrue(details.right <= recommended.left)
        assertTrue(recommended.bottom <= stars.top)
        assertTrue(stars.bottom <= fork.top)
        assertBoundsContainedBy(row, title, summary, details, recommended, stars, fork)
        val height =
            composeRule
                .onNodeWithTag(LARGE_REPOSITORY_ROW_TAG)
                .heightDp(composeRule.density)
        assertTrue(height <= LEGACY_REPOSITORY_LARGE_RECOMMENDED_HEIGHT)
    }

    @Test
    fun longFdroidCandidateAtLargeFontKeepsTextAndTrailingPillsSeparated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val selectedLabel = context.getString(R.string.github_track_sheet_fdroid_candidate_selected)
        val antiFeaturesLabel =
            context.getString(
                R.string.github_track_sheet_fdroid_candidate_antifeatures_format,
                longFdroidCandidate.antiFeatures.size,
            )
        val sourceLabel =
            context.getString(R.string.github_track_sheet_fdroid_candidate_source_index)
        val versionText =
            context.getString(
                R.string.github_track_sheet_fdroid_candidate_versions_format,
                longFdroidCandidate.latestVersionName,
                longFdroidCandidate.latestVersionCode,
                longFdroidCandidate.versionCount,
            )
        val meta =
            listOf(
                longFdroidCandidate.repoDisplayName,
                longFdroidCandidate.packageName,
                versionText,
            ).joinToString(" · ")

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = 1.5f),
                ) {
                    FdroidCandidateRow(
                        candidate = longFdroidCandidate,
                        selected = true,
                        onClick = {},
                        modifier =
                            Modifier
                                .width(360.dp)
                                .testTag(LARGE_FDROID_ROW_TAG),
                    )
                }
            }
        }

        val title = composeRule.textBounds(longFdroidCandidate.displayName)
        val summary = composeRule.textBounds(longFdroidCandidate.summary)
        val details = composeRule.textBounds(meta)
        val selected = composeRule.textBounds(selectedLabel)
        val repository = composeRule.textBounds(longFdroidCandidate.repoDisplayName)
        val antiFeatures = composeRule.textBounds(antiFeaturesLabel)
        val source = composeRule.textBounds(sourceLabel)
        val row = composeRule.onNodeWithTag(LARGE_FDROID_ROW_TAG).bounds()

        assertTrue(title.bottom <= summary.top)
        assertTrue(summary.bottom <= details.top)
        assertTrue(title.right <= selected.left)
        assertTrue(selected.right <= repository.left)
        assertTrue(summary.right <= repository.left)
        assertTrue(details.right <= repository.left)
        assertTrue(repository.bottom <= antiFeatures.top)
        assertTrue(antiFeatures.bottom <= source.top)
        assertBoundsContainedBy(
            row,
            title,
            summary,
            details,
            selected,
            repository,
            antiFeatures,
            source,
        )
        with(composeRule.density) {
            assertTrue(repository.width.toDp() <= 120.dp)
            assertTrue(selected.height.toDp() <= 27.dp)
        }
        val height =
            composeRule
                .onNodeWithTag(LARGE_FDROID_ROW_TAG)
                .heightDp(composeRule.density)
        assertTrue(height <= LEGACY_FDROID_LARGE_SELECTED_HEIGHT)
    }

    @Test
    fun repositoryCandidateListExposesSelectableGroupSemantics() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                RepositoryScanCandidateList(
                    candidates = listOf(repositoryCandidate),
                    selectedRepoUrl = repositoryCandidate.trackedApp.repoUrl,
                    onCandidateClick = {},
                )
            }
        }

        composeRule.onAllNodes(SELECTABLE_GROUP).assertCountEquals(1)
    }

    @Test
    fun fdroidCandidateListExposesSelectableGroupSemantics() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                FdroidCandidateList(
                    candidates = listOf(fdroidCandidate),
                    selectedCandidate = fdroidCandidate,
                    onCandidateSelected = {},
                )
            }
        }

        composeRule.onAllNodes(SELECTABLE_GROUP).assertCountEquals(1)
    }

    private fun assertSingleSelectedRadioAction(clickCount: () -> Int) {
        composeRule.onAllNodes(RADIO_BUTTON).assertCountEquals(1)
        composeRule.onAllNodes(RADIO_BUTTON, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNode(RADIO_BUTTON).assertIsSelected().performClick()
        composeRule.runOnIdle { assertEquals(1, clickCount()) }
    }

    private companion object {
        const val REPOSITORY_ROW_TAG = "repository-candidate-row"
        const val FDROID_ROW_TAG = "fdroid-candidate-row"
        const val LARGE_REPOSITORY_ROW_TAG = "large-repository-candidate-row"
        const val LARGE_FDROID_ROW_TAG = "large-fdroid-candidate-row"
        val RADIO_BUTTON =
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val SELECTABLE_GROUP =
            SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)

        // v1.11.8 used the same legacy row geometry as 7c51efef6. These bounds come from
        // its text/pill line heights plus the 6dp outer and 3dp control-row paddings.
        val LEGACY_REPOSITORY_SELECTED_HEIGHT = 80.dp
        val LEGACY_REPOSITORY_LARGE_RECOMMENDED_HEIGHT = 131.dp
        val LEGACY_FDROID_SELECTED_HEIGHT = 104.dp
        val LEGACY_FDROID_LARGE_SELECTED_HEIGHT = 170.dp

        val repositoryCandidate = repositoryCandidate()
        val longRepositoryCandidate =
            repositoryCandidate(
                owner = "a-deliberately-long-github-organization-name",
                repo = "a-deliberately-long-android-repository-name",
                description =
                    "A deliberately long repository description for compact large-font layout",
                starCount = 98_765,
                fork = true,
                releaseTag = "release-2026.07.17",
                assetName = "keios-a-deliberately-long-universal-release.apk",
            )
        val fdroidCandidate = fdroidCandidate()
        val longFdroidCandidate =
            fdroidCandidate(
                repoDisplayName = "A deliberately long F-Droid repository display name",
                packageName = "os.kei.a.deliberately.long.fdroid.application.package.name",
                appName = "A deliberately long F-Droid application name for compact layout",
                summary = "A deliberately long summary for compact large-font candidate layout",
                antiFeatures = listOf("Tracking", "NonFreeNet"),
                latestVersionName = "2026.07.17-preview-release",
                latestVersionCode = 2_026_071_700L,
                versionCount = 128,
            )
    }
}
private fun repositoryCandidate(
    owner: String = "hosizoraru",
    repo: String = "KeiOS",
    description: String = "KeiOS Android application",
    starCount: Int = 42,
    fork: Boolean = false,
    releaseTag: String = "v1.11.8",
    assetName: String = "KeiOS.apk",
): GitHubPackageRepositoryScanCandidate {
    val repoUrl = "https://github.com/$owner/$repo"
    return GitHubPackageRepositoryScanCandidate(
        repository =
            GitHubRepositoryCandidate(
                owner = owner,
                repo = repo,
                repoUrl = repoUrl,
                description = description,
                starCount = starCount,
                fork = fork,
                sourceType = GitHubRepositoryDiscoverySourceType.RepositorySearch,
                matchReason = GitHubRepositoryCandidateMatchReason.RepositoryName,
            ),
        trackedApp =
            GitHubTrackedApp(
                repoUrl = repoUrl,
                owner = owner,
                repo = repo,
                packageName = "os.kei",
                appLabel = "KeiOS",
            ),
        score = 100,
        releaseTag = releaseTag,
        releaseUrl = "$repoUrl/releases/tag/$releaseTag",
        assetName = assetName,
    )
}

private fun fdroidCandidate(
    repoDisplayName: String = "F-Droid",
    packageName: String = "os.kei",
    appName: String = "KeiOS",
    summary: String = "KeiOS Android application",
    antiFeatures: List<String> = emptyList(),
    latestVersionName: String = "1.11.8",
    latestVersionCode: Long = 11108L,
    versionCount: Int = 12,
): FdroidAppSearchCandidate =
    FdroidAppSearchCandidate(
        repoUrl = "https://f-droid.org/repo",
        repoDisplayName = repoDisplayName,
        packageName = packageName,
        appName = appName,
        summary = summary,
        latestVersionName = latestVersionName,
        latestVersionCode = latestVersionCode,
        versionCount = versionCount,
        antiFeatures = antiFeatures,
        source = FdroidAppSearchSource.RepositoryIndex,
    )

private fun androidx.compose.ui.test.junit4.ComposeTestRule.textBounds(text: String) =
    onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

private fun androidx.compose.ui.test.SemanticsNodeInteraction.heightDp(density: Density): Dp {
    val height = fetchSemanticsNode().boundsInRoot.height
    return with(density) { height.toDp() }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.bounds(): Rect =
    fetchSemanticsNode().boundsInRoot

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

class GitHubCandidateChoiceRowTestApp : Application()
