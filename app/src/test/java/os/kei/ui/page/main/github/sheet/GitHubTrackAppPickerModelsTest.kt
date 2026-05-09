package os.kei.ui.page.main.github.sheet

import org.junit.Test
import os.kei.feature.github.model.InstalledAppItem
import kotlin.test.assertEquals

class GitHubTrackAppPickerModelsTest {
    @Test
    fun `add flow hides tracked apps by default`() {
        val result = filterAndSortGitHubTrackAppCandidates(
            apps = apps,
            query = "",
            includeUserApps = true,
            includeSystemApps = false,
            includeTrackedApps = false,
            trackedPackageNames = setOf("com.demo.beta"),
            pinnedPackageNames = emptySet(),
            sortMode = GitHubTrackAppPickerSortMode.Name,
            sortDirection = GitHubTrackAppPickerSortDirection.Ascending
        )

        assertEquals(listOf("com.demo.alpha"), result.map { it.packageName })
    }

    @Test
    fun `tracked toggle includes tracked apps`() {
        val result = filterAndSortGitHubTrackAppCandidates(
            apps = apps,
            query = "",
            includeUserApps = true,
            includeSystemApps = false,
            includeTrackedApps = true,
            trackedPackageNames = setOf("com.demo.beta"),
            pinnedPackageNames = emptySet(),
            sortMode = GitHubTrackAppPickerSortMode.Name,
            sortDirection = GitHubTrackAppPickerSortDirection.Ascending
        )

        assertEquals(
            listOf("com.demo.alpha", "com.demo.beta"),
            result.map { it.packageName }
        )
    }

    @Test
    fun `editing keeps current tracked app visible with scope filters off`() {
        val result = filterAndSortGitHubTrackAppCandidates(
            apps = apps,
            query = "",
            includeUserApps = false,
            includeSystemApps = false,
            includeTrackedApps = false,
            trackedPackageNames = setOf("com.demo.beta"),
            pinnedPackageNames = setOf("com.demo.beta"),
            sortMode = GitHubTrackAppPickerSortMode.Name,
            sortDirection = GitHubTrackAppPickerSortDirection.Ascending
        )

        assertEquals(listOf("com.demo.beta"), result.map { it.packageName })
    }

    @Test
    fun `search still applies to pinned app`() {
        val result = filterAndSortGitHubTrackAppCandidates(
            apps = apps,
            query = "Alpha",
            includeUserApps = true,
            includeSystemApps = true,
            includeTrackedApps = false,
            trackedPackageNames = setOf("com.demo.beta"),
            pinnedPackageNames = setOf("com.demo.beta"),
            sortMode = GitHubTrackAppPickerSortMode.Name,
            sortDirection = GitHubTrackAppPickerSortDirection.Ascending
        )

        assertEquals(listOf("com.demo.alpha"), result.map { it.packageName })
    }

    @Test
    fun `package matching normalizes case and whitespace`() {
        val result = filterAndSortGitHubTrackAppCandidates(
            apps = apps,
            query = "",
            includeUserApps = true,
            includeSystemApps = true,
            includeTrackedApps = false,
            trackedPackageNames = setOf(" COM.DEMO.BETA "),
            pinnedPackageNames = emptySet(),
            sortMode = GitHubTrackAppPickerSortMode.Name,
            sortDirection = GitHubTrackAppPickerSortDirection.Ascending
        )

        assertEquals(
            listOf("com.demo.alpha", "com.demo.system"),
            result.map { it.packageName }
        )
    }

    private companion object {
        private val apps = listOf(
            InstalledAppItem(
                label = "Alpha",
                packageName = "com.demo.alpha",
                isSystemApp = false
            ),
            InstalledAppItem(
                label = "Beta",
                packageName = "com.demo.beta",
                isSystemApp = false
            ),
            InstalledAppItem(
                label = "System",
                packageName = "com.demo.system",
                isSystemApp = true
            )
        )
    }
}
