package os.kei.ui.page.main.github.page

import org.junit.Test
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubProfileDepth
import os.kei.feature.github.model.GitHubRepositoryProfileCapability
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.githubProfileSourceSignature
import os.kei.feature.github.model.requiredCapabilities
import os.kei.ui.page.main.github.VersionCheckUi
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubDecisionAssistDetailRefreshPolicyTest {
    @Test
    fun `missing profile requests health detail refresh`() {
        assertTrue(
            shouldAutoRefreshRepositoryHealthDetail(
                itemState = VersionCheckUi(),
                lookupConfig = GitHubLookupConfig(),
                refreshing = false,
                nowMillis = FETCHED_AT,
            ),
        )
    }

    @Test
    fun `active refresh suppresses health detail refresh`() {
        assertFalse(
            shouldAutoRefreshRepositoryHealthDetail(
                itemState = VersionCheckUi(),
                lookupConfig = GitHubLookupConfig(),
                refreshing = true,
                nowMillis = FETCHED_AT,
            ),
        )
    }

    @Test
    fun `health card profile requests fuller detail profile`() {
        val config = GitHubLookupConfig()
        val profile =
            profile(
                config = config,
                purpose = GitHubRepositoryProfilePurpose.HealthCard,
            )

        assertTrue(
            shouldAutoRefreshRepositoryHealthDetail(
                itemState = VersionCheckUi(repositoryProfile = profile),
                lookupConfig = config,
                refreshing = false,
                nowMillis = FETCHED_AT + 1_000L,
            ),
        )
    }

    @Test
    fun `fresh detail profile reuses cached health detail`() {
        val config = GitHubLookupConfig(profileDepth = GitHubProfileDepth.Deep)
        val profile =
            profile(
                config = config,
                purpose = GitHubRepositoryProfilePurpose.DetailFull,
                profileDepth = GitHubProfileDepth.Deep,
            )

        assertFalse(
            shouldAutoRefreshRepositoryHealthDetail(
                itemState = VersionCheckUi(repositoryProfile = profile),
                lookupConfig = config,
                refreshing = false,
                nowMillis = FETCHED_AT + 1_000L,
            ),
        )
    }

    @Test
    fun `stale detail profile requests health detail refresh`() {
        val config = GitHubLookupConfig()
        val profile =
            profile(
                config = config,
                purpose = GitHubRepositoryProfilePurpose.DetailFull,
            )

        assertTrue(
            shouldAutoRefreshRepositoryHealthDetail(
                itemState = VersionCheckUi(repositoryProfile = profile),
                lookupConfig = config,
                refreshing = false,
                nowMillis = FETCHED_AT + STALE_PROFILE_AGE_MS,
            ),
        )
    }

    private fun profile(
        config: GitHubLookupConfig,
        purpose: GitHubRepositoryProfilePurpose,
        profileDepth: GitHubProfileDepth = config.profileDepth,
    ): GitHubRepositoryProfileSnapshot {
        val capabilities = purpose.requiredCapabilities(profileDepth)
        return GitHubRepositoryProfileSnapshot(
            owner = "demo",
            repo = "app",
            sourceConfigSignature = config.githubProfileSourceSignature(capabilities),
            fetchedAtMillis = FETCHED_AT,
            purpose = purpose,
            capabilities = capabilities + setOf(GitHubRepositoryProfileCapability.RepositoryCore),
        )
    }

    private companion object {
        private const val FETCHED_AT = 1_700_000_000_000L
        private const val STALE_PROFILE_AGE_MS = 1000L * 60L * 60L * 7L
    }
}
