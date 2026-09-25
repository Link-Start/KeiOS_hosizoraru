package os.kei.feature.github.domain

import kotlin.test.assertEquals
import org.junit.Test
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubCheckCacheEntry
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedIgnoreMode

class GitHubBackgroundRefreshTargetSelectorTest {
    @Test
    fun `release targets prefer earliest due items within explicit batch limit`() {
        val items = (1..8).map(::tracked)
        val ignored = tracked(9).copy(ignoreMode = GitHubTrackedIgnoreMode.Temporary)
        val snapshot =
            GitHubTrackSnapshot(
                items = items + ignored,
                checkCache =
                    items.associate { item ->
                        item.id to GitHubCheckCacheEntry(
                            checkedAtMillis = item.repo.removePrefix("repo-").toLong(),
                        )
                    } + (ignored.id to GitHubCheckCacheEntry(checkedAtMillis = 1L)),
                refreshIntervalHours = 1,
            )

        val selected =
            selectGitHubBackgroundReleaseTargets(
                snapshot = snapshot,
                nowMs = 10_000_000L,
                maxTargets = 4,
            )

        assertEquals((1..4).map { "repo-$it" }, selected.map { it.repo })
    }

    @Test
    fun `release targets coalesce nearby due items by default`() {
        val items = (1..12).map(::tracked)
        val snapshot =
            GitHubTrackSnapshot(
                items = items,
                checkCache =
                    items.associate { item ->
                        val index = item.repo.removePrefix("repo-").toLong()
                        item.id to GitHubCheckCacheEntry(
                            checkedAtMillis =
                                checkedAtForReleaseDueIn(
                                    dueInMs = index * 2L * 60L * 1000L,
                                ),
                        )
                    },
                refreshIntervalHours = 1,
            )

        val selected =
            selectGitHubBackgroundReleaseTargets(
                snapshot = snapshot,
                nowMs = NOW_MS,
            )

        assertEquals((1..10).map { "repo-$it" }, selected.map { it.repo })
    }

    @Test
    fun `release targets include never checked items first`() {
        val old = tracked(1)
        val unchecked = tracked(2)
        val snapshot =
            GitHubTrackSnapshot(
                items = listOf(old, unchecked),
                checkCache = mapOf(old.id to GitHubCheckCacheEntry(checkedAtMillis = 1L)),
                lastRefreshMs = 0L,
                refreshIntervalHours = 1,
            )

        val selected =
            selectGitHubBackgroundReleaseTargets(
                snapshot = snapshot,
                nowMs = 10_000_000L,
                maxTargets = 2,
            )

        assertEquals(listOf("repo-2", "repo-1"), selected.map { it.repo })
    }

    @Test
    fun `actions targets use action snapshots and explicit batch limit`() {
        val items = (1..8).map { index ->
            tracked(index).copy(checkActionsUpdates = true)
        }
        val previous =
            items.associate { item ->
                item.id to actionsSnapshot(
                    item = item,
                    checkedAtMillis = item.repo.removePrefix("repo-").toLong(),
                )
            }

        // Never checked, so it would sort first, but its Actions check is off.
        val actionsOff = tracked(0)

        val selected =
            selectGitHubBackgroundActionsTargets(
                items = listOf(actionsOff) + items,
                previousById = previous,
                refreshIntervalHours = 1,
                nowMs = 10_000_000L,
                maxTargets = 4,
            )

        assertEquals((1..4).map { "repo-$it" }, selected.map { it.repo })
    }

    private fun tracked(index: Int): GitHubTrackedApp =
        GitHubTrackedApp(
            repoUrl = "https://github.com/demo/repo-$index",
            owner = "demo",
            repo = "repo-$index",
            packageName = "demo.repo$index",
            appLabel = "Repo $index",
        )

    private fun actionsSnapshot(
        item: GitHubTrackedApp,
        checkedAtMillis: Long,
    ): GitHubActionsRecommendedRunSnapshot =
        GitHubActionsRecommendedRunSnapshot(
            trackId = item.id,
            owner = item.owner,
            repo = item.repo,
            appLabel = item.appLabel,
            workflowId = 1L,
            workflowName = "Build",
            workflowPath = ".github/workflows/build.yml",
            runId = 1L,
            runNumber = 1L,
            runAttempt = 1,
            runDisplayName = "Build",
            headBranch = "main",
            headSha = "sha",
            event = "push",
            status = "completed",
            conclusion = "success",
            htmlUrl = "https://github.com/${item.owner}/${item.repo}/actions/runs/1",
            artifactCount = 1,
            androidArtifactCount = 1,
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
            checkedAtMillis = checkedAtMillis,
        )

    private fun checkedAtForReleaseDueIn(dueInMs: Long): Long =
        NOW_MS + dueInMs - RELEASE_INTERVAL_MS

    private companion object {
        private const val NOW_MS = 10_000_000L
        private const val RELEASE_INTERVAL_MS = 60L * 60L * 1000L
    }
}
