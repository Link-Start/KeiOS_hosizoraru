package os.kei.ui.page.main.github.picker

import org.junit.Test
import os.kei.feature.github.model.InstalledAppItem
import kotlin.test.assertEquals

class GitHubTrackAppPickerModelsTest {
    @Test
    fun `candidate filter applies scope, tracked, pinned and search rules`() {
        data class Case(
            val name: String,
            val query: String = "",
            val includeUserApps: Boolean,
            val includeSystemApps: Boolean,
            val includeTrackedApps: Boolean,
            val trackedPackageNames: Set<String> = setOf("com.demo.beta"),
            val pinnedPackageNames: Set<String> = emptySet(),
            val expected: List<String>,
        )
        listOf(
            Case(
                name = "add flow hides tracked apps by default",
                includeUserApps = true,
                includeSystemApps = false,
                includeTrackedApps = false,
                expected = listOf("com.demo.alpha"),
            ),
            Case(
                name = "tracked toggle includes tracked apps",
                includeUserApps = true,
                includeSystemApps = false,
                includeTrackedApps = true,
                expected = listOf("com.demo.alpha", "com.demo.beta"),
            ),
            Case(
                name = "editing keeps current tracked app visible with scope filters off",
                includeUserApps = false,
                includeSystemApps = false,
                includeTrackedApps = false,
                pinnedPackageNames = setOf("com.demo.beta"),
                expected = listOf("com.demo.beta"),
            ),
            Case(
                name = "search still applies to pinned app",
                query = "Alpha",
                includeUserApps = true,
                includeSystemApps = true,
                includeTrackedApps = false,
                pinnedPackageNames = setOf("com.demo.beta"),
                expected = listOf("com.demo.alpha"),
            ),
            Case(
                name = "package matching normalizes case and whitespace",
                includeUserApps = true,
                includeSystemApps = true,
                includeTrackedApps = false,
                trackedPackageNames = setOf(" COM.DEMO.BETA "),
                expected = listOf("com.demo.alpha", "com.demo.system"),
            ),
        ).forEach { case ->
            val result = filterAndSortGitHubTrackAppCandidates(
                apps = apps,
                query = case.query,
                includeUserApps = case.includeUserApps,
                includeSystemApps = case.includeSystemApps,
                includeTrackedApps = case.includeTrackedApps,
                trackedPackageNames = case.trackedPackageNames,
                pinnedPackageNames = case.pinnedPackageNames,
                sortMode = GitHubTrackAppPickerSortMode.Name,
                sortDirection = GitHubTrackAppPickerSortDirection.Ascending
            )

            assertEquals(case.expected, result.map { it.packageName }, case.name)
        }
    }

    @Test
    fun `initial scroll index keeps selected app near the viewport`() {
        val candidates = listOf(
            InstalledAppItem(label = "One", packageName = "com.demo.one"),
            InstalledAppItem(label = "Two", packageName = "com.demo.two"),
            InstalledAppItem(label = "Three", packageName = "com.demo.three"),
            InstalledAppItem(label = "Four", packageName = "com.demo.four")
        )

        assertEquals(
            1,
            gitHubTrackAppCandidateInitialScrollIndex(
                candidates = candidates,
                selectedPackageName = "com.demo.three"
            )
        )
    }

    @Test
    fun `initial scroll index falls back to top when selected app is missing`() {
        val candidates = listOf(
            InstalledAppItem(label = "One", packageName = "com.demo.one"),
            InstalledAppItem(label = "Two", packageName = "com.demo.two")
        )

        assertEquals(
            0,
            gitHubTrackAppCandidateInitialScrollIndex(
                candidates = candidates,
                selectedPackageName = "com.demo.missing"
            )
        )
    }

    @Test
    fun `viewport icon packages include visible rows and nearby prefetch rows`() {
        val candidates =
            (0 until 40).map { index ->
                InstalledAppItem(
                    label = "App $index",
                    packageName = "com.demo.$index",
                )
            }

        val packages =
            gitHubTrackAppPickerViewportIconPackages(
                apps = candidates,
                visibleItemIndices = listOf(18, 19, 20, 21),
            )

        assertEquals(
            (10..29).map { index -> "com.demo.$index" },
            packages,
        )
    }

    @Test
    fun `viewport icon packages clamp restored indices to candidate bounds`() {
        val candidates =
            (0 until 12).map { index ->
                InstalledAppItem(
                    label = "App $index",
                    packageName = "com.demo.$index",
                )
            }

        assertEquals(
            (3 until 12).map { index -> "com.demo.$index" },
            gitHubTrackAppPickerViewportIconPackages(
                apps = candidates,
                visibleItemIndices = listOf(11, 99),
            ),
        )
        assertEquals(
            emptyList(),
            gitHubTrackAppPickerViewportIconPackages(
                apps = candidates,
                visibleItemIndices = listOf(99),
            ),
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
