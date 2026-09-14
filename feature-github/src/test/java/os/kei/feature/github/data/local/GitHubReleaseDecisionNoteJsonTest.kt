package os.kei.feature.github.data.local

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import os.kei.feature.github.model.GitHubReleaseDecisionBasis
import os.kei.feature.github.model.GitHubReleaseDecisionNote
import os.kei.feature.github.model.GitHubReleaseRejection

/**
 * The explanation has to survive a cold start, or it only exists for the minutes after a refresh.
 *
 * It is also written beside every cached check, so the cost of a repository with nothing to explain
 * has to be nothing at all.
 */
class GitHubReleaseDecisionNoteJsonTest {
    @Test
    fun `a note with nothing to say costs nothing to store`() {
        assertNull(releaseDecisionNoteToJson(GitHubReleaseDecisionNote()))
        assertEquals(
            GitHubReleaseDecisionNote(),
            releaseDecisionNoteFromJson(null),
            "and an entry written before this existed reads back as one",
        )
    }

    @Test
    fun `an explained choice round-trips`() {
        val note = GitHubReleaseDecisionNote(
            stableBasis = GitHubReleaseDecisionBasis.ForgeLatest,
            stableRunnerUpTag = "1.25.2",
            preReleaseRejection = GitHubReleaseRejection.AbandonedLine,
        )

        assertEquals(note, releaseDecisionNoteFromJson(releaseDecisionNoteToJson(note)))
    }

    /**
     * A card written by a newer build, read by an older one. Dropping the field it cannot name is
     * right; guessing at it would put a wrong sentence in front of somebody.
     */
    @Test
    fun `a value this build has no name for is dropped, not guessed at`() {
        val fromTheFuture = buildJsonObject {
            put("basis", "SomethingElse")
            put("over", "1.25.2")
            put("preReject", "SomeNewRule")
        }

        assertEquals(
            GitHubReleaseDecisionNote(
                stableBasis = GitHubReleaseDecisionBasis.Ranked,
                stableRunnerUpTag = "1.25.2",
                preReleaseRejection = null,
            ),
            releaseDecisionNoteFromJson(fromTheFuture),
        )
    }
}
