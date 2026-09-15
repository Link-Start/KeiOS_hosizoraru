package os.kei.ci

import org.junit.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubWorkflowContractTest {
    @Test
    fun `debug apk workflow includes unit tests as parallel job`() {
        val workflow = workflowText("ci-debug-apk.yml")
        val setupAction = actionText("setup-android-gradle-build/action.yml")

        // Unit tests run as a parallel job inside the debug APK workflow
        assertContains(workflow, "./gradlew :app:testDebugUnitTest --stacktrace")
        assertContains(workflow, "./gradlew :core-log:compileDebugKotlin :core-io:compileDebugKotlin --stacktrace")
        assertContains(workflow, "cache-read-only: \"true\"")
        assertSetupActionUsesCurrentActions(setupAction)
    }

    @Test
    fun `apk workflows keep expected assemble tasks and artifact signer verification`() {
        val debugWorkflow = workflowText("ci-debug-apk.yml")
        val benchmarkWorkflow = workflowText("ci-benchmark-apk.yml")

        assertWorkflowUsesCurrentActions(debugWorkflow)
        assertContains(debugWorkflow, "./gradlew :app:assembleDebug --stacktrace")
        assertContains(debugWorkflow, "EXPECTED_APK_SIGNER_SHA256")
        assertContains(debugWorkflow, "apksigner\" verify --print-certs")

        assertWorkflowUsesCurrentActions(benchmarkWorkflow)
        assertContains(benchmarkWorkflow, "\":app:assembleBenchmark\"")
        assertContains(benchmarkWorkflow, "lintVitalBenchmark")
        assertContains(benchmarkWorkflow, "EXPECTED_APK_SIGNER_SHA256")
        assertContains(benchmarkWorkflow, "apksigner\" verify --print-certs")
    }

    /**
     * The predecessor of this test named `app`, `core-io` and eighteen other module paths in an
     * *allowlist*, which is the wrong shape and had already failed: by the time anyone looked,
     * `core-download`, `core-notification`, `core-versioning`, `feature-keepalive` and
     * `feature-github-engine` existed and were in neither the workflows nor this test, so a commit
     * touching only one of them ran no CI at all and said nothing about it. A list written by hand
     * cannot catch the module added after it was written.
     *
     * So this asks the question the other way round, against the module list Gradle itself uses: no
     * module, and none of the files that decide how every module builds, may be excluded from a run.
     */
    @Test
    fun `no module is excluded from the apk workflows`() {
        val buildInputs =
            gradleModules().map { "$it/build.gradle.kts" } +
                listOf(
                    "build.gradle.kts",
                    "settings.gradle.kts",
                    "gradle.properties",
                    "gradle/libs.versions.toml",
                    "gradlew",
                    ".github/actions/setup-android-gradle-build/action.yml",
                )

        listOf("ci-debug-apk.yml", "ci-benchmark-apk.yml").forEach { name ->
            val workflow = workflowText(name)

            assertFalse(
                PATHS_ALLOWLIST.containsMatchIn(workflow),
                "$name filters triggers with an allowlist. Use paths-ignore: an allowlist stops " +
                    "testing every module added after it was written, and does it silently.",
            )

            val ignored = ignoredPathPatterns(workflow)
            assertTrue(ignored.isNotEmpty(), "$name has no paths-ignore entries to check")
            buildInputs.forEach { path ->
                val matched = ignored.firstOrNull { (_, regex) -> regex.matches(path) }
                assertTrue(
                    matched == null,
                    "$name would skip a change to $path, ignored by \"${matched?.first}\"",
                )
            }
        }
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

    private fun assertWorkflowUsesCurrentActions(workflow: String) {
        val used = actionsUsedBy(workflow)
        assertContains(used, "actions/checkout@v7")
        assertContains(used, "actions/upload-artifact@v7")
        assertContains(workflow, "persist-credentials: false")
    }

    private fun assertSetupActionUsesCurrentActions(action: String) {
        val used = actionsUsedBy(action)
        assertContains(used, "actions/setup-java@v5")
        assertContains(used, "gradle/actions/setup-gradle@v6")
        // Wrapper validation belongs to setup-gradle, not to a second action of its own.
        assertContains(action, "validate-wrappers: true")
        assertFalse(
            used.any { it.startsWith("gradle/actions/wrapper-validation") },
            "wrapper validation is setup-gradle's `validate-wrappers`; a separate action repeats it",
        )
        assertFalse(
            used.any { it.startsWith("android-actions/setup-android") },
            "the runner image ships build-tools and the platform this build names, and that " +
                "action's default package list is what took CI down when Google deleted `tools`",
        )
    }

    /**
     * The actions a workflow or composite action actually runs, read from its `uses:` lines --
     * so that the prose explaining why something was removed can name it without tripping a check.
     */
    private fun actionsUsedBy(text: String): List<String> =
        USES_LINE.findAll(text).map { it.groupValues[1] }.toList()

    private fun gradleModules(): List<String> =
        GRADLE_INCLUDE
            .findAll(File(repoRoot(), "settings.gradle.kts").readText())
            .map { it.groupValues[1] }
            .toList()
            .also { assertTrue(it.size > 10, "Parsed only ${it.size} modules from settings.gradle.kts") }

    /** Every entry of every `paths-ignore` block, paired with the glob compiled to a regex. */
    private fun ignoredPathPatterns(workflow: String): List<Pair<String, Regex>> {
        val globs = mutableListOf<String>()
        var insideBlock = false
        workflow.lineSequence().forEach { line ->
            val entry = PATHS_IGNORE_ENTRY.matchEntire(line)
            when {
                PATHS_IGNORE_HEADER.matchEntire(line) != null -> insideBlock = true
                !insideBlock -> Unit
                entry != null -> globs += entry.groupValues[1]
                line.isBlank() || line.trimStart().startsWith("#") -> Unit
                else -> insideBlock = false
            }
        }
        return globs.distinct().map { it to globToRegex(it) }
    }

    private fun globToRegex(glob: String): Regex {
        val pattern = StringBuilder()
        var index = 0
        while (index < glob.length) {
            when {
                glob.startsWith("**/", index) -> { pattern.append("(?:.*/)?"); index += 3 }
                glob.startsWith("**", index) -> { pattern.append(".*"); index += 2 }
                glob[index] == '*' -> { pattern.append("[^/]*"); index += 1 }
                else -> { pattern.append(Regex.escape(glob[index].toString())); index += 1 }
            }
        }
        return Regex(pattern.toString())
    }

    private fun workflowText(name: String): String {
        val file = File(workflowsDir(), name)
        assertTrue(file.isFile, "Missing workflow: $name")
        return file.readText()
    }

    private fun actionText(name: String): String {
        val file = File(actionsDir(), name)
        assertTrue(file.isFile, "Missing action: $name")
        return file.readText()
    }

    private fun workflowsDir(): File = File(repoRoot(), ".github/workflows")

    private fun actionsDir(): File = File(repoRoot(), ".github/actions")

    private fun repoRoot(): File {
        val start = File(checkNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, ".github/workflows").isDirectory && File(it, "settings.gradle.kts").isFile }
            ?: error("Cannot locate the repository root from ${start.path}")
    }

    private companion object {
        val GRADLE_INCLUDE = Regex("""^\s*include\("::?([A-Za-z0-9._-]+)"\)""", RegexOption.MULTILINE)
        val PATHS_IGNORE_HEADER = Regex("""\s*paths-ignore:\s*""")
        val PATHS_IGNORE_ENTRY = Regex("""\s+- "([^"]+)"\s*""")
        val PATHS_ALLOWLIST = Regex("""^\s+paths:\s*$""", RegexOption.MULTILINE)
        val USES_LINE = Regex("""^\s*(?:- )?uses:\s*(\S+)\s*$""", RegexOption.MULTILINE)
    }
}
