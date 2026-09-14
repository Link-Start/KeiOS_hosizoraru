package os.kei.feature.github.data.remote

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.feature.github.engine.release.GitHubReleaseCandidateRanker
import os.kei.feature.github.engine.release.GitHubReleaseEvaluationEngine
import os.kei.feature.github.engine.release.GitHubReleaseEvaluationPolicy
import os.kei.feature.github.model.GitHubAtomFeed
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.GitHubAtomReleaseEntry
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubVersionCandidateSource

/**
 * `MatsuriDayo/NekoBoxForAndroid`, from the real `releases?per_page=30` response.
 *
 * The repository keeps a rolling `preview` tag. Its current release is named `pre-1.4.2-20260202-1`,
 * carries **no assets at all** — its body says so in as many words — and was published a week
 * *before* the stable `1.4.2` it was a preview of. A user on 1.4.2 was told, on every refresh and
 * forever, that an update was available whose download does not exist.
 *
 * Two separate defects produce that, and this holds both down:
 *
 *  - The name parses as 1.4.2 with a build stamp appended, so it compares as newer than `1.4.2`.
 *  - With no APK there is no version code to settle it, so the name comparison is the whole answer.
 */
class GitHubEmptyPreviewReleaseCorpusTest {
    private val strategy = GitHubApiTokenReleaseStrategy()

    private val entries by lazy {
        val json = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("nekobox-releases.json"),
        ) { "missing nekobox-releases.json fixture" }.use { it.readBytes().decodeToString() }
        strategy.parseReleaseEntries(json = json, owner = "MatsuriDayo", repo = "NekoBoxForAndroid")
    }

    private val latestStable by lazy {
        requireNotNull(GitHubReleaseCandidateRanker.latest(entries.filter { !it.isLikelyPreRelease }))
    }

    private val latestPre by lazy {
        requireNotNull(
            GitHubReleaseCandidateRanker.latest(
                entries.filter { entry ->
                    entry.isLikelyPreRelease &&
                        GitHubVersionUtils.hasMeaningfulPreReleaseVersionCandidates(
                            entry.versionCandidates,
                            GitHubVersionCandidateSource.Link.priority,
                        )
                },
            ),
        )
    }

    @Test
    fun `the fixture is the shape the report describes`() {
        assertEquals("1.4.2", latestStable.tag)
        assertEquals("preview", latestPre.tag)
        assertEquals("pre-1.4.2-20260202-1", latestPre.title)
        assertEquals(false, latestPre.hasDownloadableAsset, "the preview release has no assets")
        assertEquals(true, latestStable.hasDownloadableAsset, "the stable release has four")
        assertTrue(
            (latestPre.updatedAtMillis ?: 0L) < (latestStable.updatedAtMillis ?: 0L),
            "the preview predates the stable it was a preview of",
        )
    }

    /** The deeper of the two defects: a spent pre-release should not be surfaced at all. */
    @Test
    fun `a preview of a version that has since shipped is no longer relevant`() {
        assertFalse(
            GitHubVersionUtils.isRelevantPreRelease(
                preReleaseCandidates = latestPre.versionCandidates,
                stableCandidates = latestStable.versionCandidates,
                preReleaseUpdatedAtMillis = latestPre.updatedAtMillis,
                stableUpdatedAtMillis = latestStable.updatedAtMillis,
                preReleaseChannel = latestPre.channel,
                stableChannel = latestStable.channel,
            ),
        )
    }

    /** End to end, for a user sitting on the stable the preview preceded. */
    @Test
    fun `a user on the shipped stable is told they are up to date`() {
        val result = evaluateForLocal(preferPreRelease = false)

        assertFalse(result.hasUpdate, "there is no stable update; local is the latest stable")
        assertFalse(result.hasPreReleaseUpdate, "and no pre-release update either")
        assertEquals(GitHubTrackedReleaseStatus.UpToDate, result.status)
    }

    /**
     * The same user with pre-release tracking switched on. Opting into previews must not opt into a
     * preview that does not exist — this is the path the report came from.
     */
    @Test
    fun `opting into pre-releases does not resurrect the empty preview`() {
        val result = evaluateForLocal(preferPreRelease = true)

        assertFalse(result.hasPreReleaseUpdate)
        assertFalse(result.recommendsPreRelease)
    }

    private fun evaluateForLocal(preferPreRelease: Boolean) =
        GitHubReleaseEvaluationEngine.evaluate(
            localVersion = LOCAL_VERSION,
            localVersionCode = LOCAL_VERSION_CODE,
            snapshot = GitHubRepositoryReleaseSnapshot(
                strategyId = "test",
                feed = GitHubAtomFeed(entries = entries),
                latestStable = latestStable.toSignals(),
                hasStableRelease = true,
                latestPreRelease = latestPre.toSignals(),
            ),
            policy = GitHubReleaseEvaluationPolicy(preferPreRelease = preferPreRelease),
            // The day the stable `1.4.2` shipped, so the preview is four days stale and inside the
            // fortnight -- this test is about the empty asset list, not about the clock.
            nowMillis = 1770609300000L,
        )
}

/** What the reporter had installed. */
private const val LOCAL_VERSION = "1.4.2"
private const val LOCAL_VERSION_CODE = 230L

/** The strategy's own mapping is private; this mirrors it for the fields the engine reads. */
private fun GitHubAtomReleaseEntry.toSignals(): GitHubReleaseVersionSignals =
    GitHubReleaseVersionSignals(
        displayVersion = displayVersion,
        rawTag = tag,
        rawName = title,
        link = link,
        updatedAtMillis = updatedAtMillis,
        versionCandidates = versionCandidates,
        source = GitHubReleaseSignalSource.GitHubApi,
        channel = channel,
        hasDownloadableAsset = hasDownloadableAsset,
    )
