@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.page

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.picker.GitHubTrackAppPickerSortDirection
import os.kei.ui.page.main.github.picker.GitHubTrackAppPickerSortMode
import os.kei.ui.page.main.github.picker.filterAndSortGitHubTrackAppCandidates
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * Tracking an app must take it out of the add-track picker straight away.
 *
 * `GitHubPageState.trackedItems` is a `SnapshotStateList` that is mutated in place. The picker's
 * exclusion set used to be `remember(state.trackedItems) { ... }`, keyed on that list object, which
 * never changes, so the set stayed whatever it was on first composition and the picker kept offering
 * apps that were already tracked until the page was recreated.
 */
@RunWith(AndroidJUnit4::class)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class GitHubTrackAppPickerSynchronizationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun trackingAnAppRemovesItFromThePickerWithoutRecreatingThePage() {
        val state = GitHubPageState()
        state.trackedItems.add(trackedApp(ALREADY_TRACKED))

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val trackedPackageNames = rememberGitHubTrackedPackageNames(state.trackedItems)
                val candidates =
                    filterAndSortGitHubTrackAppCandidates(
                        apps = installedApps,
                        query = "",
                        includeUserApps = true,
                        includeSystemApps = true,
                        includeTrackedApps = false,
                        trackedPackageNames = trackedPackageNames,
                        pinnedPackageNames = emptySet(),
                        sortMode = GitHubTrackAppPickerSortMode.Name,
                        sortDirection = GitHubTrackAppPickerSortDirection.Ascending,
                    )
                Column {
                    candidates.forEach { app -> Text(text = app.label) }
                }
            }
        }

        composeRule.onAllNodes(hasText(ALREADY_TRACKED.label)).assertCountEquals(0)
        composeRule.onAllNodes(hasText(NEWLY_TRACKED.label)).assertCountEquals(1)
        composeRule.onAllNodes(hasText(UNTRACKED.label)).assertCountEquals(1)

        // The same state object and the same list: the page tracks the app the way the add flow does.
        composeRule.runOnIdle { state.trackedItems.add(trackedApp(NEWLY_TRACKED)) }

        composeRule.onAllNodes(hasText(NEWLY_TRACKED.label)).assertCountEquals(0)
        composeRule.onAllNodes(hasText(UNTRACKED.label)).assertCountEquals(1)

        // And untracking brings it back, for the same reason.
        composeRule.runOnIdle { state.trackedItems.removeAll { it.packageName == NEWLY_TRACKED.packageName } }

        composeRule.onAllNodes(hasText(NEWLY_TRACKED.label)).assertCountEquals(1)
    }
}

private val ALREADY_TRACKED = InstalledAppItem(label = "Backdrop", packageName = "io.github.kyant0.backdrop")
private val NEWLY_TRACKED = InstalledAppItem(label = "Miuix", packageName = "top.yukonga.miuix")
private val UNTRACKED = InstalledAppItem(label = "Zygisk", packageName = "com.example.zygisk")
private val installedApps = listOf(ALREADY_TRACKED, NEWLY_TRACKED, UNTRACKED)

private fun trackedApp(app: InstalledAppItem): GitHubTrackedApp =
    GitHubTrackedApp(
        repoUrl = "https://github.com/example/${app.label.lowercase()}",
        owner = "example",
        repo = app.label.lowercase(),
        packageName = app.packageName,
        appLabel = app.label,
    )
