package os.kei.ui.page.main.github.actions

import org.junit.Test
import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubActionsArtifactMatch
import os.kei.feature.github.model.GitHubActionsArtifactNameTraits
import os.kei.feature.github.model.GitHubActionsLookupStrategyOption
import os.kei.feature.github.model.GitHubActionsRunArtifacts
import os.kei.feature.github.model.GitHubActionsRunMatch
import os.kei.feature.github.model.GitHubActionsRunTraits
import os.kei.feature.github.model.GitHubActionsWorkflow
import os.kei.feature.github.model.GitHubActionsWorkflowMatch
import os.kei.feature.github.model.GitHubActionsWorkflowRun
import os.kei.feature.github.model.GitHubActionsWorkflowTraits
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.ui.page.main.github.page.GitHubActionsArtifactFilter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubActionsSheetStateTest {
    @Test
    fun `actions sheet derivation selects recommended run and artifacts`() {
        val run = runMatch(id = 100, safe = true, artifacts = listOf(artifact(1), artifact(2), artifact(3)))

        val state =
            deriveGitHubActionsSheetState(
                GitHubActionsSheetInput(
                    visible = true,
                    workflows = listOf(workflowMatch(7)),
                    runs = listOf(run),
                    selectedWorkflowId = 7,
                    selectedRunId = 100,
                    artifactFilter = GitHubActionsArtifactFilter.Recommended,
                    lookupConfig =
                        GitHubLookupConfig(
                            actionsStrategy = GitHubActionsLookupStrategyOption.GitHubApiToken,
                            apiToken = "token",
                        ),
                ),
            )

        assertEquals(7, state.recommendedWorkflowId)
        assertEquals(100, state.recommendedRunId)
        assertEquals(run, state.selectedRun)
        assertTrue(state.canResolveArtifacts)
        assertEquals(1, state.recommendedArtifactCount)
        assertEquals(2, state.alternativesArtifactCount)
        assertEquals(listOf(1L), state.visibleArtifactMatches.map { it.match.artifact.id })
        assertTrue(state.visibleArtifactMatches.single().recommended)
    }

    @Test
    fun `actions sheet derivation filters alternative artifacts`() {
        val run = runMatch(id = 100, safe = true, artifacts = listOf(artifact(1), artifact(2)))

        val state =
            deriveGitHubActionsSheetState(
                GitHubActionsSheetInput(
                    visible = true,
                    runs = listOf(run),
                    selectedRunId = 100,
                    artifactFilter = GitHubActionsArtifactFilter.Alternatives,
                ),
            )

        assertEquals(listOf(2L), state.visibleArtifactMatches.map { it.match.artifact.id })
        assertFalse(state.visibleArtifactMatches.single().recommended)
    }

    @Test
    fun `actions sheet derivation marks selected run artifact refresh`() {
        val run = runMatch(id = 100, completed = true, successful = true, artifacts = emptyList())

        val state =
            deriveGitHubActionsSheetState(
                GitHubActionsSheetInput(
                    visible = true,
                    runs = listOf(run),
                    selectedRunId = 100,
                    refreshingRunIds = mapOf(100L to true),
                ),
            )

        assertTrue(state.refreshing)
        assertTrue(state.selectedRunArtifactsLoading)
    }

    private fun workflowMatch(id: Long): GitHubActionsWorkflowMatch =
        GitHubActionsWorkflowMatch(
            workflow = GitHubActionsWorkflow(id = id, name = "Build"),
            traits =
                GitHubActionsWorkflowTraits(
                    normalizedName = "build",
                    normalizedPath = ".github/workflows/build.yml",
                    fileName = "build.yml",
                ),
            score = 10,
        )

    private fun runMatch(
        id: Long,
        completed: Boolean = true,
        successful: Boolean = true,
        safe: Boolean = false,
        artifacts: List<GitHubActionsArtifact>,
    ): GitHubActionsRunMatch =
        GitHubActionsRunMatch(
            runArtifacts =
                GitHubActionsRunArtifacts(
                    run = GitHubActionsWorkflowRun(id = id, runNumber = id),
                    artifacts = artifacts,
                ),
            traits =
                GitHubActionsRunTraits(
                    normalizedBranch = "main",
                    normalizedEvent = "workflow_dispatch",
                    normalizedStatus = if (completed) "completed" else "in_progress",
                    normalizedConclusion = if (successful) "success" else "",
                    completed = completed,
                    successful = successful,
                    safeForRecommendation = safe,
                ),
            artifactMatches =
                artifacts.map { artifact ->
                    GitHubActionsArtifactMatch(
                        artifact = artifact,
                        traits = GitHubActionsArtifactNameTraits(normalizedName = artifact.name),
                        score = 10,
                    )
                },
            score = 10,
        )

    private fun artifact(id: Long): GitHubActionsArtifact =
        GitHubActionsArtifact(
            id = id,
            name = "app-$id.apk",
        )
}
