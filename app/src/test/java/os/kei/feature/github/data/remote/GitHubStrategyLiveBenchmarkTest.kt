package os.kei.feature.github.data.remote

import kotlinx.coroutines.runBlocking
import os.kei.feature.github.domain.GitHubStrategyBenchmarkService
import os.kei.feature.github.model.GitHubRepoTarget
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opt-in live benchmark for GitHub release strategies.
 *
 * Property lookup order:
 * 1. JVM system properties
 * 2. Environment variables
 * 3. ~/.gradle/gradle.properties
 *
 * Recommended local-only keys:
 * - keios.github.liveBenchmark=true
 * - keios.github.api.token=ghp_xxx
 * - keios.github.liveTargets=owner/repo,owner/repo
 * - keios.github.forceGuest=true
 *
 * `gpr.key` is accepted as a token fallback so existing local Gradle setups can reuse it
 * without copying secrets into the repository.
 */
class GitHubStrategyLiveBenchmarkTest {
    @Test
    fun `live benchmark compares atom and api strategies`() = runBlocking {
        assumeTrue(
            "Set keios.github.liveBenchmark=true in ~/.gradle/gradle.properties, env vars, or JVM system properties to enable",
            isLiveBenchmarkEnabled()
        )

        val targets = liveBenchmarkTargets()
        val token = liveApiToken()
        val report = GitHubStrategyBenchmarkService.compareTargets(
            targets = targets,
            apiToken = token
        )

        println(buildReportText(report))

        assertEquals(2, report.results.size)
        assertTrue(report.targets.isNotEmpty())
        assertTrue(report.results.all { result -> result.warmSamples.all { sample -> sample.fromCache } })
    }

    private fun isLiveBenchmarkEnabled(): Boolean {
        return readSystemOrGlobalGradleProperty("keios.github.liveBenchmark")
            ?.let { value ->
                value.equals("true", ignoreCase = true) || value == "1"
            } == true
    }

    private fun liveApiToken(): String {
        if (forceGuestApi()) return ""
        return readSystemOrGlobalGradleProperty("keios.github.api.token")
            ?.trim()
            .orEmpty()
            .ifBlank { readSystemOrGlobalGradleProperty("gpr.key").orEmpty().trim() }
    }

    private fun forceGuestApi(): Boolean {
        return readSystemOrGlobalGradleProperty("keios.github.forceGuest")
            ?.equals("true", ignoreCase = true) == true
    }

    private fun liveBenchmarkTargets(): List<GitHubRepoTarget> {
        val raw = readSystemOrGlobalGradleProperty("keios.github.liveTargets").orEmpty().trim()
        val parsed = raw
            .split(',')
            .mapNotNull { item ->
                val repo = item.trim()
                if ('/' !in repo) return@mapNotNull null
                val owner = repo.substringBefore('/').trim()
                val name = repo.substringAfter('/').trim()
                if (owner.isBlank() || name.isBlank()) null else GitHubRepoTarget(owner, name)
            }
            .distinctBy { it.id }
        if (parsed.isNotEmpty()) return parsed
        return listOf(
            GitHubRepoTarget("topjohnwu", "Magisk"),
            GitHubRepoTarget("neovim", "neovim"),
            GitHubRepoTarget("shadowsocks", "shadowsocks-android")
        )
    }

    private fun buildReportText(
        report: os.kei.feature.github.model.GitHubStrategyBenchmarkReport
    ): String {
        return buildString {
            appendLine("GitHub Strategy Live Benchmark")
            appendLine("Targets: ${report.targets.joinToString { it.id }}")
            report.results.forEach { result ->
                appendLine(
                    "- ${result.summaryLabel}: cold=${result.coldAverageMs}ms warm=${result.warmAverageMs}ms " +
                        "cache=${result.cacheHitCount}/${result.warmSamples.size} " +
                        "success=${result.coldSuccessCount}/${result.totalTargets}"
                )
                result.coldSamples.forEach { sample ->
                    appendLine(
                        "  ${sample.target.id}: stable=${sample.stableTag.ifBlank { "-" }} pre=${sample.preReleaseTag.ifBlank { "-" }} success=${sample.success} cache=${sample.fromCache}"
                    )
                }
                if (result.failures.isNotEmpty()) {
                    appendLine("  failures=${result.failures.joinToString(" | ")}")
                }
            }
        }
    }

    private fun readSystemOrGlobalGradleProperty(key: String): String? {
        val sysValue = System.getProperty(key)?.trim()
        if (!sysValue.isNullOrBlank()) return sysValue
        val envKey = key
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace('.', '_')
            .uppercase()
        val envValue = System.getenv(envKey)?.trim()
        if (!envValue.isNullOrBlank()) return envValue

        val gradleProps = File(System.getProperty("user.home"), ".gradle/gradle.properties")
        if (!gradleProps.exists()) return null
        return runCatching {
            val props = Properties()
            gradleProps.inputStream().use(props::load)
            props.getProperty(key)?.trim()
        }.getOrNull()
    }
}
