package os.kei.ci

import org.junit.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubWorkflowContractTest {
    @Test
    fun `debug apk workflow includes unit tests as parallel job`() {
        val workflow = workflowText("ci-debug-apk.yml")

        // Unit tests run as a parallel job inside the debug APK workflow
        assertContains(workflow, "./gradlew :app:testDebugUnitTest --stacktrace")
        assertContains(workflow, "./gradlew :core-log:compileDebugKotlin :core-io:compileDebugKotlin --stacktrace")
        assertContains(workflow, "cache-read-only: \"true\"")
        assertWorkflowTriggersAppAndBuildChanges(workflow)
    }

    @Test
    fun `apk workflows keep expected assemble tasks and artifact signer verification`() {
        val debugWorkflow = workflowText("ci-debug-apk.yml")
        val benchmarkWorkflow = workflowText("ci-benchmark-apk.yml")

        assertContains(debugWorkflow, "./gradlew :app:assembleDebug --stacktrace")
        assertContains(debugWorkflow, "EXPECTED_APK_SIGNER_SHA256")
        assertContains(debugWorkflow, "apksigner\" verify --print-certs")
        assertWorkflowTriggersAppAndBuildChanges(debugWorkflow)

        assertContains(benchmarkWorkflow, "\":app:assembleBenchmark\"")
        assertContains(benchmarkWorkflow, "lintVitalBenchmark")
        assertContains(benchmarkWorkflow, "EXPECTED_APK_SIGNER_SHA256")
        assertContains(benchmarkWorkflow, "apksigner\" verify --print-certs")
    }

    @Test
    fun `tracked workflow set stays explicit`() {
        val workflows =
            workflowsDir()
                .listFiles { file -> file.isFile && file.extension == "yml" }
                .orEmpty()
                .map { it.name }
                .sorted()

        assertEquals(
            listOf(
                "ci-benchmark-apk.yml",
                "ci-debug-apk.yml",
            ),
            workflows,
        )
    }

    private fun assertWorkflowTriggersAppAndBuildChanges(workflow: String) {
        listOf(
            "app/**",
            "build.gradle.kts",
            "gradle.properties",
            "gradle/**",
            "gradlew",
            "settings.gradle.kts",
        ).forEach { path ->
            assertContains(workflow, "- \"$path\"")
        }
        assertContains(workflow, "- \".github/actions/setup-android-gradle-build/**\"")
    }

    private fun workflowText(name: String): String {
        val file = File(workflowsDir(), name)
        assertTrue(file.isFile, "Missing workflow: $name")
        return file.readText()
    }

    private fun workflowsDir(): File {
        val start = File(checkNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .map { File(it, ".github/workflows") }
            .firstOrNull { it.isDirectory }
            ?: error("Cannot locate .github/workflows from ${start.path}")
    }
}
