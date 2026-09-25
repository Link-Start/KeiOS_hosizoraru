package os.kei.feature.github.data.remote

import org.junit.Test
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubVersionCandidateSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubVersionUtilsTest {
    @Test
    fun `stable release outranks same-base rc from ImageToolbox style versions`() {
        val compare = GitHubVersionUtils.compareVersionToCandidates(
            localVersion = "3.8.0-rc04",
            candidates = listOf("3.8.0")
        )
        assertEquals(-1, compare)
    }

    @Test
    fun `canary build is older than alpha build for Capsulyric style versions`() {
        val compare = GitHubVersionUtils.compareVersionToCandidates(
            localVersion = "Version.26.4.Canary_C378",
            candidates = listOf("Version.26.4.Alpha2_C384")
        )
        assertEquals(-1, compare)
    }

    @Test
    fun `newer alpha still outranks older stable for SaltPlayer style versions`() {
        val compare = GitHubVersionUtils.compareVersionToCandidates(
            localVersion = "11.2.0-alpha01",
            candidates = listOf("11.1.0")
        )
        assertEquals(1, compare)
    }

    @Test
    fun `wild tracked version tags classify into their release channel`() {
        // Null means "no pre-release marker"; the STABLE row accepts either null or STABLE.
        val rows = listOf(
            "pre-release keyword without dash" to ("v1.4.6-prerelease2" to GitHubReleaseChannel.PREVIEW),
            "BatteryRecorder prerelease tag" to ("v1.4.7-prerelease3" to GitHubReleaseChannel.PREVIEW),
            "WhatAnime nightly token" to ("1.9.0.n488.nightly" to GitHubReleaseChannel.DEV),
            "Hyper-pick-up-code Dev suffix" to ("v26.4.9.C01-Dev" to GitHubReleaseChannel.DEV),
            "date prefixed stable name" to ("260412_1.22" to GitHubReleaseChannel.STABLE),
        )

        rows.forEach { (label, row) ->
            val (input, expected) = row
            assertEquals(
                expected,
                GitHubVersionUtils.classifyVersionChannel(input) ?: GitHubReleaseChannel.STABLE,
                "$label: classifyVersionChannel(\"$input\")",
            )
        }
    }

    @Test
    fun `wild tracked versions remain comparable after normalization`() {
        assertTrue(
            "1.22" in GitHubVersionUtils.normalizeVersionCandidates("260412_1.22"),
            "date prefixed release name must expose its semantic version candidate",
        )
        val rows = listOf(
            Triple("C01 build before its Dev tag", "26.4.3.C01" to "v26.4.9.C01-Dev", -1),
            Triple("revision hash builds", "1.8.8.r471.cff36155" to "1.9.0.r488.d07a3e1b", -1),
            Triple("date prefixed release name", "1.22" to "260412_1.22", 0),
        )

        rows.forEach { (label, versions, expected) ->
            val (local, candidate) = versions
            assertEquals(
                expected,
                GitHubVersionUtils.compareVersionToCandidates(
                    localVersion = local,
                    candidates = listOf(candidate),
                ),
                "$label: compare(\"$local\", \"$candidate\")",
            )
        }
    }

    @Test
    fun `release candidate values use symmetric shared ordering`() {
        assertEquals(
            -1,
            GitHubVersionUtils.compareReleaseCandidateValues(
                left = "2.4.0-unstable2",
                right = "2.4.0-unstable10",
            ),
        )
        assertEquals(
            1,
            GitHubVersionUtils.compareReleaseCandidateValues(
                left = "2.4.0-unstable10",
                right = "2.4.0-unstable2",
            ),
        )
    }

    @Test
    fun `keyguard style semantic title candidate beats raw date tag`() {
        val candidates = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to "r20260410",
            GitHubVersionCandidateSource.Title to "Release v2.8.0-20260410"
        )
        val compare = GitHubVersionUtils.compareVersionToStructuredCandidates(
            localVersion = "2.7.0",
            candidates = candidates
        )
        assertEquals(-1, compare)
    }

    @Test
    fun `candidate normalization keeps tracked project formats`() {
        val candidates = GitHubVersionUtils.normalizeVersionCandidates("Version.26.4.Canary_C378")
        assertTrue(candidates.any { it.contains("26.4") })
        assertTrue(candidates.any { it.contains("canary") || it.contains("dev") })
    }

    @Test
    fun `structured candidate comparison prefers exact semantic match over content noise`() {
        val left = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to "v0.19.7"
        )
        val right = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to "v0.19.9",
            GitHubVersionCandidateSource.Content to "release notes for v0.19.7 and older hotfix"
        )
        val compare = GitHubVersionUtils.compareStructuredCandidateSets(left, right)
        assertEquals(-1, compare)
    }

    @Test
    fun `release notes version range cannot hide newer release tag`() {
        val candidates = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to "v1.2.0",
            GitHubVersionCandidateSource.Title to "KeiOS v1.2.0",
            GitHubVersionCandidateSource.Link to "https://github.com/hosizoraru/KeiOS/releases/tag/v1.2.0",
            GitHubVersionCandidateSource.Content to "这是从 v1.1.0 到 v1.2.0 的功能更新"
        )

        val compare = GitHubVersionUtils.compareVersionToStructuredCandidates("1.1.0", candidates)
        assertEquals(-1, compare)
    }

    @Test
    fun `structured comparison returns exact match when title carries current version`() {
        val candidates = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to "r20260410",
            GitHubVersionCandidateSource.Title to "Release v2.7.0-20260320"
        )
        val compare = GitHubVersionUtils.compareVersionToStructuredCandidates("2.7.0", candidates)
        assertNotNull(compare)
        assertEquals(0, compare)
    }

    @Test
    fun `meaningful prerelease candidates keep date based rc tags but reject branch names`() {
        val dateRcCandidates = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to "v20260410-rc1"
        )
        val branchCandidates = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to "dev-fix-access-denied-error-1"
        )

        assertTrue(GitHubVersionUtils.hasMeaningfulPreReleaseVersionCandidates(dateRcCandidates))
        assertEquals(false, GitHubVersionUtils.hasMeaningfulPreReleaseVersionCandidates(branchCandidates))
    }

    @Test
    fun `same release detection uses candidate overlap instead of loose semantic equality`() {
        val stableCandidates = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to "Version.1.3.Fix2_C359"
        )
        val preCandidates = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to "Version.26.4.Alpha2_C384"
        )
        val duplicateCandidates = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to "v0.0.8",
            GitHubVersionCandidateSource.Title to "0.0.8"
        )

        assertEquals(false, GitHubVersionUtils.referToSameReleaseVersion(preCandidates, stableCandidates))
        assertTrue(GitHubVersionUtils.referToSameReleaseVersion(duplicateCandidates, duplicateCandidates))
    }

    @Test
    fun `same release detection ignores content noise from changelog ranges`() {
        val stableCandidates = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to "3.8.0"
        )
        val preCandidates = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to "3.8.0-rc04",
            GitHubVersionCandidateSource.Content to "Full Changelog 3.8.0-rc03...3.8.0-rc04"
        )

        assertEquals(false, GitHubVersionUtils.referToSameReleaseVersion(preCandidates, stableCandidates))
    }

    @Test
    fun `remote candidate can match local versionName plus versionCode`() {
        val candidates = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to "v1.2.18.2102"
        )

        assertTrue(
            GitHubVersionUtils.remoteCandidateMatchesLocalVersionNameAndCode(
                localVersion = "1.2.18",
                localVersionCode = 2102L,
                remoteCandidates = candidates
            )
        )
    }

    @Test
    fun `remote candidate with different build code stays newer`() {
        val candidates = GitHubVersionUtils.buildVersionCandidates(
            GitHubVersionCandidateSource.Tag to "v1.2.18.2102"
        )

        assertEquals(
            false,
            GitHubVersionUtils.remoteCandidateMatchesLocalVersionNameAndCode(
                localVersion = "1.2.18",
                localVersionCode = 2101L,
                remoteCandidates = candidates
            )
        )
    }
}
