package os.kei.feature.github.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test
import os.kei.feature.github.engine.release.GitHubReleaseEvaluationEngine
import os.kei.feature.github.engine.release.GitHubReleaseEvaluationPolicy
import os.kei.feature.github.fixture.MithkaReleaseCorpus
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubMithkaReleaseCorpusTest {
    @After
    fun tearDown() {
        GitHubAtomReleaseStrategy.clearCaches()
        GitHubApiTokenReleaseStrategy.clearSharedCaches()
    }

    @Test
    fun `atom identifies master flood as rolling prereleases`() = runBlocking {
        MockWebServer().use { server ->
            server.routeAtom(
                feed = MithkaReleaseCorpus.atomXml,
                latest = latestRedirect(
                    MithkaReleaseCorpus.stableTag,
                    owner = MithkaReleaseCorpus.owner,
                    repo = MithkaReleaseCorpus.repo,
                ),
            )

            val snapshot = server.loadAtomSnapshotTrace(
                owner = MithkaReleaseCorpus.owner,
                repo = MithkaReleaseCorpus.repo,
            ).result.getOrThrow()

            assertEquals(MithkaReleaseCorpus.stableTag, snapshot.latestStable.rawTag)
            assertEquals(
                MithkaReleaseCorpus.latestPreReleaseTag,
                snapshot.latestPreRelease?.rawTag,
            )
            assertEquals(1, snapshot.feed.entries.count { !it.isLikelyPreRelease })
            assertEquals(9, snapshot.feed.entries.count { it.isLikelyPreRelease })
            assertTrue(snapshot.feed.entries.filter { it.isLikelyPreRelease }.all {
                it.channel == GitHubReleaseChannel.DEV
            })
        }
    }

    @Test
    fun `api keeps stable and rolling prerelease identities separate`() = runBlocking {
        val snapshot = loadApiSnapshot()

        assertEquals(MithkaReleaseCorpus.stableTag, snapshot.latestStable.rawTag)
        assertEquals(
            MithkaReleaseCorpus.latestPreReleaseTag,
            snapshot.latestPreRelease?.rawTag,
        )
        assertEquals(GitHubReleaseChannel.STABLE, snapshot.latestStable.channel)
        assertEquals(GitHubReleaseChannel.DEV, snapshot.latestPreRelease?.channel)
        val preRelease = requireNotNull(snapshot.latestPreRelease)
        assertEquals(
            -1,
            GitHubVersionUtils.compareVersionToStructuredCandidates(
                localVersion = "0.3.0",
                candidates = preRelease.versionCandidates,
                remoteChannel = preRelease.channel,
            ),
            "the rolling build is newer than the stable by name",
        )
        assertEquals(
            -1,
            GitHubVersionUtils.compareVersionNameAndCodeToStructuredCandidates(
                localVersion = "0.3.0",
                localVersionCode = MithkaReleaseCorpus.stableVersionCode,
                candidates = preRelease.versionCandidates,
                remoteChannel = preRelease.channel,
            ),
            "and by name plus the stable's version code",
        )
    }

    @Test
    fun `rolling build evaluation follows prerelease policy and local build code`() = runBlocking {
        val snapshot = loadApiSnapshot()

        val stableOnly = evaluate(
            snapshot = snapshot,
            localVersionCode = MithkaReleaseCorpus.stableVersionCode,
        )
        val optionalPreRelease = evaluate(
            snapshot = snapshot,
            localVersionCode = MithkaReleaseCorpus.stableVersionCode,
            policy = GitHubReleaseEvaluationPolicy(checkAllTrackedPreReleases = true),
        )
        val preferredPreRelease = evaluate(
            snapshot = snapshot,
            localVersionCode = MithkaReleaseCorpus.stableVersionCode,
            policy = GitHubReleaseEvaluationPolicy(preferPreRelease = true),
        )
        val olderPreRelease = evaluate(
            snapshot = snapshot,
            localVersionCode = MithkaReleaseCorpus.olderPreReleaseVersionCode,
        )
        val currentPreRelease = evaluate(
            snapshot = snapshot,
            localVersion = "0.4.0",
            localVersionCode = MithkaReleaseCorpus.latestPreReleaseVersionCode,
        )

        assertEquals(GitHubTrackedReleaseStatus.UpToDate, stableOnly.status)
        assertFalse(stableOnly.hasPreReleaseUpdate)
        assertEquals(GitHubTrackedReleaseStatus.PreReleaseOptional, optionalPreRelease.status)
        assertTrue(optionalPreRelease.hasPreReleaseUpdate)
        assertEquals(
            GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable,
            preferredPreRelease.status,
        )
        assertEquals(GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable, olderPreRelease.status)
        assertTrue(olderPreRelease.isPreReleaseInstalled)
        assertEquals(GitHubTrackedReleaseStatus.PreReleaseTracked, currentPreRelease.status)
        assertFalse(currentPreRelease.hasUpdate)
    }

    private suspend fun loadApiSnapshot() = MockWebServer().use { server ->
        server.enqueue(MockResponse().setResponseCode(200).setBody(MithkaReleaseCorpus.apiJson))
        GitHubApiTokenReleaseStrategy(
            apiToken = "fixture-token",
            apiBaseUrl = server.url("/").toString(),
        ).loadSnapshot(
            owner = MithkaReleaseCorpus.owner,
            repo = MithkaReleaseCorpus.repo,
        ).getOrThrow()
    }

    private fun evaluate(
        snapshot: GitHubRepositoryReleaseSnapshot,
        localVersion: String = "0.3.0",
        localVersionCode: Long,
        policy: GitHubReleaseEvaluationPolicy = GitHubReleaseEvaluationPolicy(),
    ) = GitHubReleaseEvaluationEngine.evaluate(
        localVersion = localVersion,
        localVersionCode = localVersionCode,
        snapshot = snapshot,
        policy = policy,
        // Both corpora are frozen captures, so the staleness rule is judged at the moment they were
        // taken. Left to the wall clock, a July capture starts failing in September for no reason
        // but the calendar.
        nowMillis = MITHKA_CAPTURED_AT_MILLIS,
    )
}

/** 2026-07-11, the day iebb/mithka was captured; see MithkaReleaseCorpus. */
private const val MITHKA_CAPTURED_AT_MILLIS = 1_783_555_200_000L
