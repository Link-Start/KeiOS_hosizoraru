package os.kei.feature.github.engine.release

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.core.versioning.ReleaseSelectionRule
import os.kei.feature.github.data.remote.GitHubApiTokenReleaseStrategy
import os.kei.feature.github.model.GitHubReleaseRejection

/**
 * The record the pipeline never kept, read back off the two responses that produced reports.
 *
 * Both of those were diagnosed the same way: fetch the real API response, hand-trim it to the fields
 * the parser reads, then write a throwaway probe that prints the candidate list and the comparisons
 * so the discarded releases become visible again. That work was thrown away each time, because the
 * selection kept nothing.
 *
 * These assertions are that probe, made permanent. If a third repository shape arrives, its trace is
 * one call away instead of an afternoon.
 */
class GitHubReleaseSelectionTraceTest {
    private val strategy = GitHubApiTokenReleaseStrategy()

    /**
     * `stratumauth/app` shipped to `1.25.2` as Authenticator Pro, was rebranded, and restarted at
     * `v1.0.1`. The trace has to name both halves: the release it chose, and the old high number it
     * chose *against* — without which "why is it showing v1.6.2" has no answer in the record.
     */
    @Test
    fun `a restarted project records what it overrode and that it is worth confirming`() {
        val plan = GitHubReleaseSelector.plan(entriesFrom("stratumauth-releases.json", "stratumauth", "app"))

        assertEquals(ReleaseSelectionRule.VersioningReset, plan.stable.rule)
        assertEquals("v1.6.2", plan.stable.entry?.tag)
        assertEquals("1.25.2", plan.stable.runnerUpTag)
        assertTrue(
            plan.shouldConsultForgeLatest,
            "a suspected reset is exactly when the forge's own latest flag is worth a request",
        )

        val selection = plan.resolve()
        assertTrue(
            selection.rejected.any { it.tag == "1.25.2" && it.reason == GitHubReleaseRejection.Outranked },
            "the overridden tag is named in the record, not merely absent from it: ${selection.summary()}",
        )
    }

    /**
     * `MatsuriDayo/NekoBoxForAndroid` keeps a rolling `preview` tag. Nothing about it is wrong at
     * selection time — it is a real pre-release with a comparable version — so the trace should show
     * it chosen here and set aside later, by the rule that needs the reader's own build to run.
     */
    @Test
    fun `a rolling preview is chosen by the selector and set aside by the evaluator`() {
        val plan = GitHubReleaseSelector.plan(entriesFrom("nekobox-releases.json", "MatsuriDayo", "NekoBoxForAndroid"))
        val selection = plan.resolve()

        assertEquals("1.4.2", selection.stable?.rawTag)
        assertEquals("preview", selection.preRelease?.rawTag)
        assertFalse(
            plan.shouldConsultForgeLatest,
            "an ordinary history must not pay for the second request",
        )
        assertEquals(false, selection.preRelease?.hasDownloadableAsset)
    }

    /** The caveat on every other assertion here: one page is all anybody looked at. */
    @Test
    fun `a full page of releases is recorded as a window, not as the whole history`() {
        val json = fixture("nekobox-releases.json")
        val window = strategy.parseReleaseWindow(
            json = json,
            owner = "MatsuriDayo",
            repo = "NekoBoxForAndroid",
            limit = 5,
        )

        assertEquals(5, window.entries.size)
        assertTrue(window.windowWasFull, "five of a larger page is a slice and says so")
        assertFalse(
            strategy.parseReleaseWindow(json, "MatsuriDayo", "NekoBoxForAndroid", limit = 500)
                .windowWasFull,
            "a page that came back short is the whole history",
        )
    }

    /** One line a human can read, which is the point of keeping any of this. */
    @Test
    fun `the summary names the choice, the runner up and the rule`() {
        val summary = GitHubReleaseSelector
            .plan(entriesFrom("stratumauth-releases.json", "stratumauth", "app"))
            .resolve()
            .summary()

        assertTrue(summary.startsWith("stable=v1.6.2 by VersioningReset over 1.25.2"), summary)
        assertTrue(summary.contains("considered="), summary)
    }

    private fun entriesFrom(resource: String, owner: String, repo: String) =
        strategy.parseReleaseEntries(json = fixture(resource), owner = owner, repo = repo)

    private fun fixture(resource: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(resource)) {
            "missing $resource fixture"
        }.use { it.readBytes().decodeToString() }
}
