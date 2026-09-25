package os.kei.feature.github.domain.fdroid

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.feature.github.model.GitHubReleaseChannel

/**
 * F-Droid has no `prerelease` flag, so the channel is inferred — and two surfaces have to infer it the
 * same way.
 *
 * This was private to `FdroidReleaseCheckSource` until the version-history page needed to mark and anchor
 * pre-release builds. A page that called a build stable while the checker treated it as a pre-release
 * would be showing a different history than the one being tracked, so the classifier is shared and these
 * tests pin the behaviour the checker already relied on.
 */
class FdroidVersionChannelsTest {
    @Test
    fun `channel follows the declared channel, then the version name`() {
        // (label, declared release channels, version name, expected channel)
        val rows = listOf(
            Row("a plain version is stable", emptyList(), "1.4.0", GitHubReleaseChannel.STABLE),
            Row("nothing at all is stable, not unknown", emptyList(), "", GitHubReleaseChannel.STABLE),
            Row("a declared channel is believed", listOf("beta"), "1.4.0", GitHubReleaseChannel.BETA),
            Row("declared channel, any case", listOf("Beta"), "", GitHubReleaseChannel.BETA),
            // The index declaring nothing is the common case, so the name is read.
            Row("alpha in the name", emptyList(), "2.0.0-alpha3", GitHubReleaseChannel.ALPHA),
            Row("beta in the name", emptyList(), "2.0.0-beta", GitHubReleaseChannel.BETA),
            Row("name, any case", emptyList(), "1.4.0-BETA", GitHubReleaseChannel.BETA),
            Row("preview in the name", emptyList(), "2.0.0-preview", GitHubReleaseChannel.PREVIEW),
            Row("dev in the name", emptyList(), "2.0.0-dev", GitHubReleaseChannel.DEV),
            Row("SNAPSHOT reads as dev", emptyList(), "2.0.0-SNAPSHOT", GitHubReleaseChannel.DEV),
            // rc is matched as a whole word, so a version that merely contains the letters is not one.
            Row("rc", emptyList(), "1.4.0-rc", GitHubReleaseChannel.RC),
            Row("rc.2", emptyList(), "1.4.0-rc.2", GitHubReleaseChannel.RC),
            Row("release candidate", emptyList(), "1.4.0 release candidate", GitHubReleaseChannel.RC),
            Row("arch contains the letters", emptyList(), "1.4.0-arch", GitHubReleaseChannel.STABLE),
            Row("source contains the letters", emptyList(), "1.4.0-source", GitHubReleaseChannel.STABLE),
        )

        rows.forEach { row ->
            assertEquals(row.expected, channelOf(row.releaseChannels, row.versionName), row.label)
        }
        // A build with no name and no declared channel: the checker counts it as stable, and the history
        // page must not float it into the pre-release anchor.
        assertFalse(channelOf().isPreRelease)
    }

    private data class Row(
        val label: String,
        val releaseChannels: List<String>,
        val versionName: String,
        val expected: GitHubReleaseChannel,
    )

    @Test
    fun `an rc with the number run onto it is missed, which is the word boundary's cost`() {
        // Pinned as a known gap rather than asserted as correct: `\brc\b` cannot match "rc2", because the
        // digit is a word character and closes no boundary. So the most common spelling of a release
        // candidate reads as stable.
        //
        // Left alone deliberately. This classifier is what the update check has always used to split
        // stable from pre-release, and widening it would change which builds existing tracks are notified
        // about — a tracking-behaviour change, not a page. The history page inherits the same blind spot,
        // which is the point: both surfaces agree.
        assertEquals(GitHubReleaseChannel.STABLE, channelOf(versionName = "1.4.0-rc2"))
        assertFalse(channelOf(versionName = "1.4.0-rc2").isPreRelease)
        // The index saying so is the way out, and repositories that ship candidates usually do.
        assertTrue(channelOf(releaseChannels = listOf("rc"), versionName = "1.4.0-rc2").isPreRelease)
    }

    @Test
    fun `the earlier branch wins when a name carries two markers`() {
        // Ordering is behaviour the checker already had: dev is tested before alpha, so this is dev.
        // Pinned rather than asserted as ideal — the point is that both surfaces agree on the answer.
        assertEquals(GitHubReleaseChannel.DEV, channelOf(versionName = "1.0.0-dev-alpha"))
    }

    @Test
    fun `every inferred pre-release channel actually reads as one`() {
        // "1.0-rc" rather than "1.0-rc1" -- see the word-boundary gap above.
        listOf("1.0-dev", "1.0-alpha", "1.0-beta", "1.0-rc", "1.0-preview").forEach { name ->
            assertTrue(channelOf(versionName = name).isPreRelease, name)
        }
    }
}

private fun channelOf(
    releaseChannels: List<String> = emptyList(),
    versionName: String = "",
): GitHubReleaseChannel = fdroidReleaseChannelOf(releaseChannels = releaseChannels, versionName = versionName)
