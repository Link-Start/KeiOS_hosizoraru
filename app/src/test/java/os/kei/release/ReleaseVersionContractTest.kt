package os.kei.release

import org.junit.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseVersionContractTest {
    @Test
    fun releaseTargetStaysAlignedAcrossBuildCiAndDocs() {
        val projectRoot = locateProjectRoot()
        val buildScript = projectRoot.resolve("app/build.gradle.kts").readText()
        val releaseTargetMatch =
            requireNotNull(
                Regex(
                    """releaseTargetVersion\s*=\s*AppSemVer\(major\s*=\s*(\d+),\s*minor\s*=\s*(\d+),\s*patch\s*=\s*(\d+)\)""",
                ).find(buildScript),
            ) { "app/build.gradle.kts must declare releaseTargetVersion" }
        val releaseVersion = releaseTargetMatch.groupValues.drop(1).joinToString(".")

        val ciAction = projectRoot.resolve(".github/actions/setup-android-gradle-build/action.yml").readText()
        val ciVersion =
            requireNotNull(Regex("""release_target_version="(\d+\.\d+\.\d+)""").find(ciAction)) {
                "setup-android-gradle-build must declare release_target_version"
            }.groupValues[1]
        assertEquals(releaseVersion, ciVersion, "Gradle and CI release targets must match")

        listOf("README.md", "readme/CN.md").forEach { path ->
            assertContains(
                projectRoot.resolve(path).readText(),
                "v$releaseVersion",
                message = "$path must identify the current source release",
            )
        }

        val releaseNotes = projectRoot.resolve("readme/RELEASE_V$releaseVersion.md")
        assertTrue(releaseNotes.isFile, "${releaseNotes.path} must exist for the current release target")
        assertContains(
            releaseNotes.readText(),
            "# KeiOS v$releaseVersion Release Notes",
            message = "Release notes must identify the current release target",
        )
    }
}

private fun locateProjectRoot(): File {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    return requireNotNull(
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .firstOrNull { directory -> File(directory, "settings.gradle.kts").isFile },
    ) {
        "Unable to locate the KeiOS project root from $workingDirectory"
    }
}
