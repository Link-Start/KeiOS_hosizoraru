package os.kei.ui.page.main.github.section

import java.io.File
import kotlin.test.assertEquals
import org.junit.Test

class GitHubOverviewPillPlanTest {
    @Test
    fun overviewUsesFixedCompactMetricPills() {
        val plan = buildGitHubOverviewExpandedPillPlan()

        assertEquals(
            listOf(
                GitHubOverviewExpandedPillKind.Stable,
                GitHubOverviewExpandedPillKind.PreRelease,
                GitHubOverviewExpandedPillKind.CheckFailed,
            ),
            plan.map { it.kind },
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
