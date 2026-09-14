package os.kei.feature.github.engine.release

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.core.versioning.VersioningEngine
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.data.remote.GitHubApiTokenReleaseStrategy
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.fixture.MithkaReleaseCorpus
import os.kei.feature.github.model.GitHubAtomFeed
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.GitHubVersionCandidateSource
import java.io.File
import java.util.Locale
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubReleaseAlgorithmBenchmarkTest {
    @Test
    fun `fixed release corpus measures accuracy and throughput`() = runBlocking {
        val parser = GitHubApiTokenReleaseStrategy(apiToken = "fixture-token")
        val actualEntries = parser.parseReleaseEntries(
            json = MithkaReleaseCorpus.apiJson,
            owner = MithkaReleaseCorpus.owner,
            repo = MithkaReleaseCorpus.repo,
        )
        val stableEntry = requireNotNull(
            GitHubReleaseCandidateRanker.latest(actualEntries.filter { !it.isLikelyPreRelease }),
        )
        val preReleaseEntry = requireNotNull(
            GitHubReleaseCandidateRanker.latest(actualEntries.filter { it.isLikelyPreRelease }),
        )
        val snapshot = GitHubRepositoryReleaseSnapshot(
            strategyId = "benchmark",
            feed = GitHubAtomFeed(
                title = "iebb/mithka releases",
                feedUrl = "https://github.com/iebb/mithka/releases",
                entries = actualEntries,
            ),
            latestStable = stableEntry.toSignal(),
            latestPreRelease = preReleaseEntry.toSignal(),
        )

        assertEquals(MithkaReleaseCorpus.stableTag, stableEntry.tag)
        assertEquals(MithkaReleaseCorpus.latestPreReleaseTag, preReleaseEntry.tag)
        assertEquals(
            -1,
            GitHubVersionUtils.compareVersionToStructuredCandidates(
                localVersion = "0.3.0",
                candidates = preReleaseEntry.versionCandidates,
                remoteChannel = preReleaseEntry.channel,
            ),
        )
        assertEquals(
            -1,
            GitHubVersionUtils.compareVersionNameAndCodeToStructuredCandidates(
                localVersion = "0.3.0",
                localVersionCode = MithkaReleaseCorpus.stableVersionCode,
                candidates = preReleaseEntry.versionCandidates,
                remoteChannel = preReleaseEntry.channel,
            ),
        )
        assertEquals(
            GitHubTrackedReleaseStatus.PreReleaseOptional,
            evaluate(snapshot, MithkaReleaseCorpus.stableVersionCode).status,
        )
        assertEquals(
            GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable,
            evaluate(snapshot, MithkaReleaseCorpus.olderPreReleaseVersionCode).status,
        )
        assertEquals(
            GitHubTrackedReleaseStatus.PreReleaseTracked,
            evaluate(
                snapshot = snapshot,
                localVersionCode = MithkaReleaseCorpus.latestPreReleaseVersionCode,
                localVersion = "0.4.0",
            ).status,
        )

        val observedScalePreReleases = rollingEntries(
            count = OBSERVED_PRERELEASE_COUNT,
            baseVersion = "0.3.0",
            firstVersionCode = 26_070_000L,
        )
        val floodScalePreReleases = rollingEntries(
            count = FLOOD_BENCHMARK_SIZE,
            baseVersion = "9.0.0",
            firstVersionCode = 27_000_000L,
        )
        assertEquals(
            observedScalePreReleases.last().tag,
            GitHubReleaseCandidateRanker.latest(observedScalePreReleases)?.tag,
        )
        assertEquals(
            floodScalePreReleases.last().tag,
            GitHubReleaseCandidateRanker.latest(floodScalePreReleases)?.tag,
        )

        val evaluationCodes = List(BATCH_PROJECT_COUNT) { index ->
            when (index % 3) {
                0 -> MithkaReleaseCorpus.stableVersionCode
                1 -> MithkaReleaseCorpus.olderPreReleaseVersionCode
                else -> MithkaReleaseCorpus.latestPreReleaseVersionCode
            }
        }
        val hotNormalizationInputs = List(HOT_CACHE_INPUT_COUNT) { index ->
            "v4.2.$index-master.${26_000_000L + index}.${stableHash(index)}"
        }

        val metrics = listOf(
            measureMetric(
                name = "parse-real-api-corpus",
                operationsPerSample = ACTUAL_PARSE_REPEATS * actualEntries.size,
            ) {
                repeat(ACTUAL_PARSE_REPEATS) {
                    val parsed = parser.parseReleaseEntries(
                        json = MithkaReleaseCorpus.apiJson,
                        owner = MithkaReleaseCorpus.owner,
                        repo = MithkaReleaseCorpus.repo,
                    )
                    benchmarkSink = benchmarkSink xor parsed.size
                }
            },
            measureMetric(
                name = "normalize-hot-cache",
                operationsPerSample = hotNormalizationInputs.size,
            ) {
                hotNormalizationInputs.forEach { value ->
                    benchmarkSink = benchmarkSink xor
                        GitHubVersionUtils.normalizeVersionCandidates(value).size
                }
            },
            measureMetric(
                name = "normalize-unique-candidates",
                operationsPerSample = UNIQUE_NORMALIZATION_COUNT,
                sampleCount = 5,
            ) { sample ->
                repeat(UNIQUE_NORMALIZATION_COUNT) { index ->
                    val unique = "v8.$sample.$index-master.${28_000_000L + index}.${stableHash(index)}"
                    benchmarkSink = benchmarkSink xor
                        VersioningEngine.normalizeCandidates(unique).size
                }
            },
            measureMetric(
                name = "rank-observed-105-prereleases",
                operationsPerSample = RANK_OBSERVED_REPEATS * observedScalePreReleases.size,
            ) {
                repeat(RANK_OBSERVED_REPEATS) {
                    benchmarkSink = benchmarkSink xor
                        GitHubReleaseCandidateRanker.latest(observedScalePreReleases)
                            .hashCode()
                }
            },
            measureMetric(
                name = "rank-flood-1000-prereleases",
                operationsPerSample = RANK_FLOOD_REPEATS * floodScalePreReleases.size,
                sampleCount = 5,
            ) {
                repeat(RANK_FLOOD_REPEATS) {
                    benchmarkSink = benchmarkSink xor
                        GitHubReleaseCandidateRanker.latest(floodScalePreReleases)
                            .hashCode()
                }
            },
            measureMetric(
                name = "evaluate-100-projects-sequential",
                operationsPerSample = evaluationCodes.size,
            ) {
                evaluationCodes.forEach { versionCode ->
                    benchmarkSink = benchmarkSink xor evaluate(snapshot, versionCode).status.ordinal
                }
            },
            measureSuspendingMetric(
                name = "evaluate-100-projects-parallel-4",
                operationsPerSample = evaluationCodes.size,
            ) {
                val results = GitHubExecution.mapOrderedBounded(
                    items = evaluationCodes,
                    maxConcurrency = 4,
                    dispatcher = Dispatchers.Default,
                ) { versionCode ->
                    evaluate(snapshot, versionCode).status.ordinal
                }
                benchmarkSink = benchmarkSink xor results.sum()
            },
            measureMetric(
                name = "compare-version-name-only-hot",
                operationsPerSample = VERSION_CODE_COMPARE_REPEATS,
            ) {
                repeat(VERSION_CODE_COMPARE_REPEATS) {
                    benchmarkSink = benchmarkSink xor requireNotNull(
                        GitHubVersionUtils.compareVersionToStructuredCandidates(
                            localVersion = "0.3.0",
                            candidates = preReleaseEntry.versionCandidates,
                            remoteChannel = preReleaseEntry.channel,
                        ),
                    )
                }
            },
            measureMetric(
                name = "compare-version-name-and-code-hot",
                operationsPerSample = VERSION_CODE_COMPARE_REPEATS,
            ) {
                repeat(VERSION_CODE_COMPARE_REPEATS) {
                    benchmarkSink = benchmarkSink xor requireNotNull(
                        GitHubVersionUtils.compareVersionNameAndCodeToStructuredCandidates(
                            localVersion = "0.3.0",
                            localVersionCode = MithkaReleaseCorpus.olderPreReleaseVersionCode,
                            candidates = preReleaseEntry.versionCandidates,
                            remoteChannel = preReleaseEntry.channel,
                        ),
                    )
                }
            },
        )

        val report = writeReport(metrics)
        println(report.readText())
        assertTrue(metrics.all { it.averageNsPerOperation > 0.0 })
    }

    private fun evaluate(
        snapshot: GitHubRepositoryReleaseSnapshot,
        localVersionCode: Long,
        localVersion: String = "0.3.0",
    ) = GitHubReleaseEvaluationEngine.evaluate(
        localVersion = localVersion,
        localVersionCode = localVersionCode,
        snapshot = snapshot,
        policy = GitHubReleaseEvaluationPolicy(checkAllTrackedPreReleases = true),
        // A frozen capture is judged at the moment it was taken, or the staleness rule turns this
        // accuracy benchmark into a clock that fails on its own after a fortnight.
        nowMillis = BENCHMARK_CAPTURED_AT_MILLIS,
    )

    private fun rollingEntries(
        count: Int,
        baseVersion: String,
        firstVersionCode: Long,
    ): List<GitHubAtomReleaseEntry> {
        return List(count) { index ->
            val versionCode = firstVersionCode + index
            val tag = "v$baseVersion-master.$versionCode.${stableHash(index)}"
            GitHubAtomReleaseEntry(
                entryId = tag,
                tag = tag,
                title = "Build $baseVersion master ${stableHash(index)}",
                link = "https://github.com/fixture/app/releases/tag/$tag",
                updatedAtMillis = (count - index).toLong(),
                versionCandidates = GitHubVersionUtils.buildVersionCandidates(
                    GitHubVersionCandidateSource.Tag to tag,
                ),
                channel = GitHubReleaseChannel.DEV,
                isLikelyPreRelease = true,
            )
        }
    }

    private fun stableHash(index: Int): String {
        return (index.toLong() * 2_654_435_761L)
            .toString(16)
            .takeLast(7)
            .padStart(7, '0')
    }

    private fun GitHubAtomReleaseEntry.toSignal(): GitHubReleaseVersionSignals {
        return GitHubReleaseVersionSignals(
            displayVersion = displayVersion,
            rawTag = tag,
            rawName = title,
            link = link,
            updatedAtMillis = updatedAtMillis,
            versionCandidates = versionCandidates,
            source = GitHubReleaseSignalSource.GitHubApi,
            channel = channel,
        )
    }

    private fun measureMetric(
        name: String,
        operationsPerSample: Int,
        warmupCount: Int = 3,
        sampleCount: Int = 8,
        block: (sample: Int) -> Unit,
    ): BenchmarkMetric {
        repeat(warmupCount) { warmup -> block(-warmup - 1) }
        val durations = List(sampleCount) { sample ->
            val startedAt = System.nanoTime()
            block(sample)
            System.nanoTime() - startedAt
        }
        return BenchmarkMetric(name, operationsPerSample, durations)
    }

    private suspend fun measureSuspendingMetric(
        name: String,
        operationsPerSample: Int,
        warmupCount: Int = 3,
        sampleCount: Int = 8,
        block: suspend () -> Unit,
    ): BenchmarkMetric {
        repeat(warmupCount) { block() }
        val durations = buildList {
            repeat(sampleCount) {
                val startedAt = System.nanoTime()
                block()
                add(System.nanoTime() - startedAt)
            }
        }
        return BenchmarkMetric(name, operationsPerSample, durations)
    }

    private fun writeReport(metrics: List<BenchmarkMetric>): File {
        val workingDir = File(System.getProperty("user.dir").orEmpty())
        val moduleDir = if (workingDir.name == "feature-github-engine") {
            workingDir
        } else {
            File(workingDir, "feature-github-engine")
        }
        val reportDir = File(moduleDir, "build/reports/github-release-algorithm")
        reportDir.mkdirs()
        return File(reportDir, "release-algorithm-benchmark.md").apply {
            writeText(buildReportMarkdown(metrics))
        }
    }

    private fun buildReportMarkdown(metrics: List<BenchmarkMetric>): String {
        return buildString {
            appendLine("# GitHub Release Algorithm Benchmark")
            appendLine()
            appendLine("Corpus: iebb/mithka captured 2026-07-11; 119 releases observed, including 105 prereleases.")
            appendLine("Runtime: local JVM Debug unit-test variant with JIT warmup; use values for relative algorithm trends.")
            appendLine()
            appendLine("| workload | operations/sample | avg ms | p50 ms | p95 ms | ns/op | ops/s |")
            appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: |")
            metrics.forEach { metric ->
                append("| ")
                append(metric.name)
                append(" | ")
                append(metric.operationsPerSample)
                append(" | ")
                append(metric.averageMs.formatMetric())
                append(" | ")
                append(metric.medianMs.formatMetric())
                append(" | ")
                append(metric.p95Ms.formatMetric())
                append(" | ")
                append(metric.averageNsPerOperation.formatMetric())
                append(" | ")
                append(metric.operationsPerSecond.formatMetric())
                appendLine(" |")
            }
        }
    }

    private fun Double.formatMetric(): String {
        return String.format(Locale.US, "%.3f", this)
    }

    private data class BenchmarkMetric(
        val name: String,
        val operationsPerSample: Int,
        val durationsNs: List<Long>,
    ) {
        private val sortedDurations: List<Long> = durationsNs.sorted()

        val averageMs: Double
            get() = durationsNs.average() / NANOS_PER_MILLISECOND

        val medianMs: Double
            get() = sortedDurations[sortedDurations.size / 2] / NANOS_PER_MILLISECOND

        val p95Ms: Double
            get() {
                val index = (ceil(sortedDurations.size * 0.95).toInt() - 1)
                    .coerceIn(sortedDurations.indices)
                return sortedDurations[index] / NANOS_PER_MILLISECOND
            }

        val averageNsPerOperation: Double
            get() = durationsNs.average() / operationsPerSample.coerceAtLeast(1)

        val operationsPerSecond: Double
            get() = NANOS_PER_SECOND / averageNsPerOperation
    }

    private companion object {
        const val OBSERVED_PRERELEASE_COUNT = 105
        const val FLOOD_BENCHMARK_SIZE = 1_000
        const val BATCH_PROJECT_COUNT = 100
        const val HOT_CACHE_INPUT_COUNT = 100
        const val UNIQUE_NORMALIZATION_COUNT = 1_000
        const val ACTUAL_PARSE_REPEATS = 50
        const val RANK_OBSERVED_REPEATS = 50
        const val RANK_FLOOD_REPEATS = 5
        const val VERSION_CODE_COMPARE_REPEATS = 5_000
        const val NANOS_PER_MILLISECOND = 1_000_000.0
        const val NANOS_PER_SECOND = 1_000_000_000.0

        @Volatile
        var benchmarkSink: Int = 0
    }
}

/** 2026-07-11, the day iebb/mithka was captured; see MithkaReleaseCorpus. */
private const val BENCHMARK_CAPTURED_AT_MILLIS = 1_783_555_200_000L
