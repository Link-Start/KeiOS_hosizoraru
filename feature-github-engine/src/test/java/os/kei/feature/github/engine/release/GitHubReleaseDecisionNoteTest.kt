package os.kei.feature.github.engine.release

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.feature.github.data.remote.GitHubApiTokenReleaseStrategy
import os.kei.feature.github.model.GitHubReleaseDecisionBasis
import os.kei.feature.github.model.GitHubReleaseDecisionNote
import os.kei.feature.github.model.GitHubReleaseRejection

/**
 * What the card is allowed to say about a choice, taken from the selection that made it.
 *
 * The full record exists for diagnosis; this is the sentence a reader gets, and the rule it follows
 * is that an answer which explains itself says nothing. A note on every card would bury the two
 * that matter under the hundreds that do not.
 */
class GitHubReleaseDecisionNoteTest {
    private val strategy = GitHubApiTokenReleaseStrategy()

    @Test
    fun `an ordinary history has nothing to explain`() {
        val selection = GitHubReleaseSelector
            .plan(entriesFrom("nekobox-releases.json", "MatsuriDayo", "NekoBoxForAndroid"))
            .resolve()

        val note = GitHubReleaseDecisionNote.from(selection)

        assertEquals(GitHubReleaseDecisionBasis.Ranked, note.stableBasis)
        assertFalse(note.explainsStableChoice)
        assertTrue(note.isEmpty, "nothing to say means nothing cached and nothing drawn")
    }

    /**
     * The forge's flag is told rather than the reset that made the app go and ask for it: one is a
     * maintainer's statement about their own project, the other is this app's reading of a list.
     */
    @Test
    fun `a confirmed restart is explained as the forge's own answer`() {
        val plan = GitHubReleaseSelector
            .plan(entriesFrom("stratumauth-releases.json", "stratumauth", "app"))
        val selection = plan.resolve(
            authoritativeStable = requireNotNull(plan.stable.entry).let { entry ->
                GitHubReleaseSelector.plan(listOf(entry)).resolve().stable
            },
        )

        val note = GitHubReleaseDecisionNote.from(selection)

        assertEquals(GitHubReleaseDecisionBasis.ForgeLatest, note.stableBasis)
        assertEquals("1.25.2", note.stableRunnerUpTag, "the card names what it chose over")
        assertTrue(note.explainsStableChoice)
    }

    /** Without the forge's confirmation the reading still stands, and is described as a reading. */
    @Test
    fun `an unconfirmed restart is explained as a ranking by date`() {
        val note = GitHubReleaseDecisionNote.from(
            GitHubReleaseSelector
                .plan(entriesFrom("stratumauth-releases.json", "stratumauth", "app"))
                .resolve(),
        )

        assertEquals(GitHubReleaseDecisionBasis.VersioningReset, note.stableBasis)
        assertEquals("1.25.2", note.stableRunnerUpTag)
    }

    /**
     * Atom mode asks `releases/latest` on every refresh of every repository — it is how that mode
     * learns which entry is stable at all. So the flag agreeing with an unremarkable ranking is
     * that mode's ordinary Tuesday, and putting "the repository marks this as its latest release"
     * on every Atom card would bury the two cards that need a sentence under the hundreds that do
     * not. The confirmation is still in the record; it is simply not news.
     */
    @Test
    fun `a forge flag that merely agrees with an ordinary ranking is not news`() {
        val plan = GitHubReleaseSelector
            .plan(entriesFrom("nekobox-releases.json", "MatsuriDayo", "NekoBoxForAndroid"))
        val selection = plan.resolve(
            authoritativeStable = requireNotNull(plan.stable.entry).let { entry ->
                GitHubReleaseSelector.plan(listOf(entry)).resolve().stable
            },
        )

        assertTrue(selection.stableCameFromForgeLatest, "the flag was still what built the snapshot")
        assertTrue(selection.stableForgeLatestConfirmedRanking)

        val note = GitHubReleaseDecisionNote.from(selection)

        assertEquals(GitHubReleaseDecisionBasis.Ranked, note.stableBasis)
        assertTrue(note.isEmpty, "nothing to say means nothing cached and nothing drawn")
    }

    /**
     * The evaluator's verdict wins over the selector's, because it ran last and because it is the
     * one that takes away a row the reader could otherwise see.
     */
    @Test
    fun `the evaluator's rejection is the one reported`() {
        val selection = GitHubReleaseSelector
            .plan(entriesFrom("nekobox-releases.json", "MatsuriDayo", "NekoBoxForAndroid"))
            .resolve()

        assertEquals(
            GitHubReleaseRejection.AbandonedLine,
            GitHubReleaseDecisionNote
                .from(selection, GitHubReleaseRejection.AbandonedLine)
                .preReleaseRejection,
        )
    }

    /** A source that keeps no record still reports what the evaluator decided on its own. */
    @Test
    fun `a source with no selection still carries the evaluator's verdict`() {
        val note = GitHubReleaseDecisionNote.from(null, GitHubReleaseRejection.AbandonedLine)

        assertEquals(GitHubReleaseDecisionBasis.Ranked, note.stableBasis)
        assertEquals(GitHubReleaseRejection.AbandonedLine, note.preReleaseRejection)
        assertFalse(note.isEmpty)
        assertNull(GitHubReleaseDecisionNote.from(null).preReleaseRejection)
    }

    private fun entriesFrom(resource: String, owner: String, repo: String) =
        strategy.parseReleaseEntries(
            json = requireNotNull(javaClass.classLoader?.getResourceAsStream(resource)) {
                "missing $resource fixture"
            }.use { it.readBytes().decodeToString() },
            owner = owner,
            repo = repo,
        )
}
