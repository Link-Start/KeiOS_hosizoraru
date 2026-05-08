package os.kei.feature.github.data.local

import org.junit.Test
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubProfileDepth
import os.kei.feature.github.model.GitHubProfileField
import os.kei.feature.github.model.GitHubRepositoryActivityProfile
import os.kei.feature.github.model.GitHubRepositoryForkSyncProfile
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryProfileAvailabilityStatus
import os.kei.feature.github.model.GitHubRepositoryProfileCapability
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryProfileSourceState
import os.kei.feature.github.model.GitHubRepositorySecurityProfile
import os.kei.feature.github.model.GitHubRepositoryTrafficProfile
import os.kei.feature.github.model.GitHubRepositoryUpstreamProfile
import os.kei.feature.github.model.githubProfileSourceSignature
import os.kei.feature.github.model.requiredCapabilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubRepositoryProfileCacheJsonTest {
    @Test
    fun `profile cache round trip preserves lifecycle and source availability`() {
        val snapshot = GitHubRepositoryProfileSnapshot(
            owner = "demo",
            repo = "app",
            sourceConfigSignature = "check-v2|fixture",
            fetchedAtMillis = FETCHED_AT,
            purpose = GitHubRepositoryProfilePurpose.DetailFull,
            capabilities = setOf(
                GitHubRepositoryProfileCapability.RepositoryCore,
                GitHubRepositoryProfileCapability.ReleaseSignals,
                GitHubRepositoryProfileCapability.Traffic,
                GitHubRepositoryProfileCapability.ForkSync,
                GitHubRepositoryProfileCapability.Security
            ),
            lifecycle = GitHubRepositoryLifecycleProfile(
                archived = field(true),
                fork = field(true),
                upstream = GitHubRepositoryUpstreamProfile(
                    fullName = field("upstream/app"),
                    archived = field(false),
                    pushedAtMillis = field(1_699_000_000_000L)
                )
            ),
            activity = GitHubRepositoryActivityProfile(
                pushedAtMillis = field(1_700_000_000_000L)
            ),
            traffic = GitHubRepositoryTrafficProfile(
                viewCount = field(42),
                cloneCount = field(7)
            ),
            forkSync = GitHubRepositoryForkSyncProfile(
                aheadBy = field(2),
                behindBy = field(3),
                status = field("behind")
            ),
            security = GitHubRepositorySecurityProfile(
                dependabotAlertsAvailable = field(true),
                openDependabotAlertsCount = field(1),
                codeScanningAvailable = field(true),
                openCodeScanningAlertsCount = field(0)
            ),
            sourceAvailability = listOf(
                GitHubRepositoryProfileSourceState(
                    source = GitHubRepositoryProfileSource.GitHubApiRepository,
                    status = GitHubRepositoryProfileAvailabilityStatus.Loaded,
                    fetchedAtMillis = FETCHED_AT,
                    elapsedMs = 34L,
                    required = true
                )
            )
        )

        val restored = parseGitHubRepositoryProfileSnapshot(snapshot.toCacheJson())
            ?: error("profile cache should restore")

        assertEquals("demo", restored.owner)
        assertEquals("check-v2|fixture", restored.sourceConfigSignature)
        assertTrue(restored.lifecycle.archived?.value == true)
        assertEquals("upstream/app", restored.lifecycle.upstream?.fullName?.value)
        assertFalse(restored.lifecycle.upstream?.archived?.value == true)
        assertEquals(42, restored.traffic.viewCount?.value)
        assertEquals(3, restored.forkSync.behindBy?.value)
        assertEquals(1, restored.security.openDependabotAlertsCount?.value)
        assertEquals(GitHubRepositoryProfilePurpose.DetailFull, restored.purpose)
        assertTrue(GitHubRepositoryProfileCapability.Security in restored.capabilities)
        assertEquals(
            GitHubRepositoryProfileSource.GitHubApiRepository,
            restored.sourceAvailability.single().source
        )
        assertEquals(34L, restored.sourceAvailability.single().elapsedMs)
        assertTrue(restored.sourceAvailability.single().required)
        assertTrue(restored.sourceAvailability.single().fromCache)
    }

    @Test
    fun `profile freshness follows source signature and ttl`() {
        val snapshot = GitHubRepositoryProfileSnapshot(
            owner = "demo",
            repo = "app",
            sourceConfigSignature = "check-v2|fixture",
            fetchedAtMillis = FETCHED_AT
        )

        assertTrue(
            snapshot.isFreshFor(
                activeSourceConfigSignature = "check-v2|fixture",
                nowMillis = FETCHED_AT + 1_000L,
                ttlMillis = 10_000L
            )
        )
        assertFalse(
            snapshot.isFreshFor(
                activeSourceConfigSignature = "check-v2|other",
                nowMillis = FETCHED_AT + 1_000L,
                ttlMillis = 10_000L
            )
        )
        assertFalse(
            snapshot.isFreshFor(
                activeSourceConfigSignature = "check-v2|fixture",
                nowMillis = FETCHED_AT + 20_000L,
                ttlMillis = 10_000L
            )
        )
    }

    @Test
    fun `profile freshness allows fuller capability cache to satisfy smaller purposes`() {
        val config = GitHubLookupConfig(profileDepth = GitHubProfileDepth.Deep)
        val detailCapabilities = GitHubRepositoryProfilePurpose.DetailFull.requiredCapabilities(
            GitHubProfileDepth.Deep
        )
        val healthCapabilities = GitHubRepositoryProfilePurpose.HealthCard.requiredCapabilities(
            GitHubProfileDepth.Deep
        )
        val snapshot = GitHubRepositoryProfileSnapshot(
            owner = "demo",
            repo = "app",
            sourceConfigSignature = config.githubProfileSourceSignature(detailCapabilities),
            fetchedAtMillis = FETCHED_AT,
            capabilities = detailCapabilities
        )

        assertTrue(
            snapshot.isFreshFor(
                activeSourceConfigSignature = config.githubProfileSourceSignature(
                    healthCapabilities
                ),
                nowMillis = FETCHED_AT + 1_000L,
                ttlMillis = 10_000L,
                requiredCapabilities = healthCapabilities
            )
        )
        assertFalse(
            snapshot.copy(capabilities = healthCapabilities).isFreshFor(
                activeSourceConfigSignature = config.githubProfileSourceSignature(
                    detailCapabilities
                ),
                nowMillis = FETCHED_AT + 1_000L,
                ttlMillis = 10_000L,
                requiredCapabilities = detailCapabilities
            )
        )
    }

    @Test
    fun `old profile cache without capabilities infers available layers`() {
        val json = snapshotJsonWithoutCapabilities()

        val restored = parseGitHubRepositoryProfileSnapshot(json)
            ?: error("profile cache should restore")

        assertTrue(GitHubRepositoryProfileCapability.RepositoryCore in restored.capabilities)
        assertTrue(GitHubRepositoryProfileCapability.Traffic in restored.capabilities)
        assertTrue(GitHubRepositoryProfileCapability.Security in restored.capabilities)
        assertTrue(restored.sourceAvailability.single().fromCache)
    }

    private fun <T> field(value: T): GitHubProfileField<T> {
        return GitHubProfileField(
            value = value,
            source = GitHubRepositoryProfileSource.GitHubApiRepository,
            fetchedAtMillis = FETCHED_AT,
            confidence = GitHubRepositoryProfileConfidence.High
        )
    }

    private fun snapshotJsonWithoutCapabilities(): org.json.JSONObject {
        return org.json.JSONObject()
            .put("owner", "demo")
            .put("repo", "app")
            .put("sourceConfigSignature", "check-v2|old")
            .put("fetchedAtMillis", FETCHED_AT)
            .put(
                "traffic",
                org.json.JSONObject().put(
                    "viewCount",
                    field(3).toJsonField()
                )
            )
            .put(
                "security",
                org.json.JSONObject().put(
                    "dependabotAlertsAvailable",
                    field(true).toJsonField()
                )
            )
            .put(
                "sourceAvailability",
                org.json.JSONArray().put(
                    org.json.JSONObject()
                        .put("source", GitHubRepositoryProfileSource.TrafficViewsApi.name)
                        .put("status", GitHubRepositoryProfileAvailabilityStatus.Loaded.name)
                        .put("fetchedAtMillis", FETCHED_AT)
                )
            )
    }

    private fun GitHubProfileField<*>.toJsonField(): org.json.JSONObject {
        return org.json.JSONObject()
            .put("value", value)
            .put("source", source.name)
            .put("fetchedAtMillis", fetchedAtMillis)
            .put("confidence", confidence.name)
    }

    private companion object {
        const val FETCHED_AT = 1_700_000_000_000L
    }
}
