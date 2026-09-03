package os.kei.ui.page.main.github.fdroid

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.feature.github.data.remote.fdroid.FdroidAntiFeatureSnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidPackageSnapshot
import os.kei.feature.github.model.FdroidAntiFeaturePolicy
import os.kei.feature.github.model.FdroidTrackedAppConfig
import os.kei.feature.github.model.FdroidVersionSelectionMode

/**
 * The page has two sources for one history and neither is sufficient alone.
 *
 * The refresh sidecar keeps full records for the eight newest builds — that is all
 * `buildFdroidMetadataSidecar` stores, because eight is what an update decision needs. The package API
 * answers with every build there has ever been and, on f-droid.org's own endpoint, a version name and a
 * code and nothing else. So the remote list decides which builds exist and the cache fills in what it
 * knows: complete at the bottom, detailed at the top.
 */
class FdroidVersionMergeTest {
    @Test
    fun `the remote list decides which builds exist`() {
        val cached = listOf(fdroidVersion(30L), fdroidVersion(20L))
        val remote = (1L..5L).map { code -> thinFdroidVersion(code * 10L) }

        val merged = mergeFdroidVersions(cached = cached, remote = remote)

        // Five builds, not the two the last refresh happened to keep.
        assertEquals(listOf(50L, 40L, 30L, 20L, 10L), merged.map { it.versionCode })
    }

    @Test
    fun `the cache fills in what the package API does not say`() {
        val merged =
            mergeFdroidVersions(
                cached = listOf(fdroidVersion(20L)),
                remote = listOf(thinFdroidVersion(20L), thinFdroidVersion(10L)),
            )

        val enriched = merged.first { it.versionCode == 20L }
        assertEquals("app_20.apk", enriched.apkName)
        assertEquals("hash20", enriched.apkSha256)
        assertEquals(20_480L, enriched.apkSizeBytes)
        assertEquals(24, enriched.minSdk)
        assertEquals("notes for 20", enriched.whatsNew)

        // And the build the cache knew nothing about stays as thin as it arrived, rather than borrowing
        // another build's hash — which would make the trust check verify the wrong file.
        val bare = merged.first { it.versionCode == 10L }
        assertEquals("", bare.apkSha256)
        assertEquals(0L, bare.apkSizeBytes)
        assertNull(bare.minSdk)
    }

    @Test
    fun `the remote value wins wherever it said something`() {
        val merged =
            mergeFdroidVersions(
                cached = listOf(fdroidVersion(20L, versionName = "stale", apkSha256 = "stalehash")),
                remote = listOf(fdroidVersion(20L, versionName = "1.20.0", apkSha256 = "freshhash")),
            )

        assertEquals("1.20.0", merged.single().versionName)
        assertEquals("freshhash", merged.single().apkSha256)
    }

    @Test
    fun `a cached build the remote list never mentions is kept rather than dropped`() {
        // Not hypothetical: the package API 404s on repositories that only publish an index, and there
        // the cached builds are the only truth there is.
        val merged =
            mergeFdroidVersions(
                cached = listOf(fdroidVersion(30L), fdroidVersion(20L)),
                remote = listOf(thinFdroidVersion(30L)),
            )

        assertEquals(listOf(30L, 20L), merged.map { it.versionCode })
    }

    @Test
    fun `a failed fetch leaves the cached history intact and in order`() {
        val merged =
            mergeFdroidVersions(
                cached = listOf(fdroidVersion(10L), fdroidVersion(30L), fdroidVersion(20L)),
                remote = emptyList(),
            )

        assertEquals(listOf(30L, 20L, 10L), merged.map { it.versionCode })
    }

    @Test
    fun `two split APKs under one version code stay two rows with their own hashes`() {
        val cached =
            listOf(
                fdroidVersion(20L, apkName = "app_20_arm64.apk", apkSha256 = "arm64hash"),
                fdroidVersion(20L, apkName = "app_20_armv7.apk", apkSha256 = "armv7hash"),
            )
        val remote =
            listOf(
                fdroidVersion(20L, apkName = "app_20_armv7.apk", apkSha256 = ""),
                fdroidVersion(20L, apkName = "app_20_arm64.apk", apkSha256 = ""),
            )

        val merged = mergeFdroidVersions(cached = cached, remote = remote)

        assertEquals(2, merged.size)
        // Matched by APK name, so neither split inherits the other's hash.
        assertEquals("arm64hash", merged.first { it.apkName == "app_20_arm64.apk" }.apkSha256)
        assertEquals("armv7hash", merged.first { it.apkName == "app_20_armv7.apk" }.apkSha256)
    }

    @Test
    fun `a named build never inherits a differently-named build's hash, even under one version code`() {
        // The trust check verifies a download against exactly the hash and signer on the row. So a build
        // that names its APK is matched on that name and nothing else: if the cache holds only the other
        // split of the same version code, this row goes unenriched rather than claiming the wrong file's
        // fingerprint. An empty hash offers no verification; a wrong hash asserts a false one.
        val merged =
            mergeFdroidVersions(
                cached =
                    listOf(
                        fdroidVersion(
                            20L,
                            apkName = "app_20_armv7.apk",
                            apkSha256 = "armv7hash",
                            signerSha256 = listOf("armv7signer"),
                        ),
                    ),
                // Named, but with nothing else of its own -- the shape a repository index gives when it
                // lists a file it has no hash for.
                remote =
                    listOf(
                        fdroidVersion(
                            20L,
                            apkName = "app_20_arm64.apk",
                            apkSha256 = "",
                            signerSha256 = emptyList(),
                        ),
                    ),
            )

        // Both splits are on screen: the remote one plus the cached one it did not claim.
        assertEquals(2, merged.size)
        val arm64 = merged.first { it.apkName == "app_20_arm64.apk" }
        assertEquals("", arm64.apkSha256, "arm64 must not carry armv7's hash")
        assertTrue(arm64.signerSha256.isEmpty(), "inherited ${arm64.signerSha256}")
        // And the cached split keeps its own, so nothing was lost in the process either.
        assertEquals("armv7hash", merged.first { it.apkName == "app_20_armv7.apk" }.apkSha256)
    }

    @Test
    fun `a named build claims a nameless cached record instead of rendering beside it`() {
        // The regression this exists for: the page reads f-droid.org's package page, so its records name
        // their APK, while a sidecar written from `/api/v1/packages` names nothing -- that endpoint
        // publishes no file. Refusing the match left both, and every version rendered twice with the same
        // version code and two different row ids.
        val merged =
            mergeFdroidVersions(
                cached = listOf(thinFdroidVersion(20L).copy(addedAtMillis = 1_700_000_000_000L)),
                remote = listOf(fdroidVersion(20L, apkName = "app_20.apk", addedAtMillis = null)),
            )

        assertEquals(1, merged.size, "one build, one row: ${merged.map { it.apkName }}")
        assertEquals("app_20.apk", merged.single().apkName)
        // And the nameless record's own facts still carry across.
        assertEquals(1_700_000_000_000L, merged.single().addedAtMillis)
    }

    @Test
    fun `a nameless cached record is claimed once, not by every split of its version code`() {
        val merged =
            mergeFdroidVersions(
                cached = listOf(thinFdroidVersion(20L)),
                remote =
                    listOf(
                        fdroidVersion(20L, apkName = "app_20_arm64.apk"),
                        fdroidVersion(20L, apkName = "app_20_armv7.apk"),
                    ),
            )

        // Two real files stay two rows; the one cached record does not multiply into both.
        assertEquals(2, merged.size)
        assertEquals(merged.size, merged.map { version -> fdroidVersionRowId(version) }.toSet().size)
    }

    @Test
    fun `a thin build still takes the version code's cached record, which is the whole point`() {
        val merged =
            mergeFdroidVersions(
                cached = listOf(fdroidVersion(20L, apkName = "app_20_arm64.apk", apkSha256 = "arm64hash")),
                remote = listOf(thinFdroidVersion(20L)),
            )

        // No name to match on, so the code is all there is -- and a repository that publishes one APK per
        // version, which is the common case, would otherwise never enrich at all.
        assertEquals("arm64hash", merged.single().apkSha256)
    }

    @Test
    fun `a cached record is claimed once, so two thin splits cannot both take it`() {
        val merged =
            mergeFdroidVersions(
                cached = listOf(fdroidVersion(20L, apkName = "app_20.apk", apkSha256 = "onlyhash")),
                remote =
                    listOf(
                        thinFdroidVersion(20L),
                        // A second entry under the same code with nothing to match on.
                        thinFdroidVersion(20L, versionName = "1.20.0-b"),
                    ),
            )

        assertEquals(1, merged.count { it.apkSha256 == "onlyhash" })
    }

    @Test
    fun `the selected build and the candidate list arriving together make one row, not two`() {
        // Exactly the shape the sidecar hands over: `selectedVersion` is normally also in
        // `candidateVersions`, and a duplicate key throws inside the lazy list rather than drawing twice.
        val selected = fdroidVersion(30L)
        val history = fdroidVersionHistoryOf(listOf(selected, fdroidVersion(30L), fdroidVersion(20L)))

        assertEquals(listOf(30L, 20L), history.map { it.versionCode })
        assertEquals(history.size, history.map { version -> fdroidVersionRowId(version) }.toSet().size)
    }

    @Test
    fun `every row in a merged history has a distinct key`() {
        val merged =
            mergeFdroidVersions(
                cached = listOf(fdroidVersion(20L), fdroidVersion(20L, apkName = "app_20_alt.apk")),
                remote = (1L..8L).map { code -> thinFdroidVersion(code * 10L) },
            )

        assertEquals(merged.size, merged.map { version -> fdroidVersionRowId(version) }.toSet().size)
    }
}

class FdroidVersionRowFactsTest {
    @Test
    fun `a version code identifies an installed build exactly, where a name only guesses`() {
        val version = fdroidVersion(20L, versionName = "1.20.0")

        assertTrue(version.matchesInstalled(installedVersionCode = 20L, installedVersion = "anything"))
        assertFalse(version.matchesInstalled(installedVersionCode = 19L, installedVersion = "1.20.0"))
    }

    @Test
    fun `the name is consulted only when there is no installed code to compare`() {
        // A track added before the check cache recorded a code. The `v` prefix is stripped because
        // repositories are inconsistent about it and the release list already normalises the same way.
        val version = fdroidVersion(20L, versionName = "1.20.0")

        assertTrue(version.matchesInstalled(installedVersionCode = -1L, installedVersion = "v1.20.0"))
        assertFalse(version.matchesInstalled(installedVersionCode = -1L, installedVersion = "1.19.0"))
        assertFalse(version.matchesInstalled(installedVersionCode = 0L, installedVersion = ""))
    }

    @Test
    fun `a build whose index gave no minSdk is treated as installable`() {
        // Which is what F-Droid itself does, and it is the common case on the package API.
        assertTrue(thinFdroidVersion(10L).isCompatibleWith(deviceSdk = 21))
        assertTrue(fdroidVersion(10L, minSdk = 21).isCompatibleWith(deviceSdk = 21))
        assertFalse(fdroidVersion(10L, minSdk = 35).isCompatibleWith(deviceSdk = 34))
    }

    @Test
    fun `the filter matches a version name, a code or an APK name`() {
        val version = fdroidVersion(210L, versionName = "2.10.0", apkName = "pixez_210.apk")

        assertTrue(version.matchesQuery(""))
        assertTrue(version.matchesQuery("2.10"))
        assertTrue(version.matchesQuery("210"))
        assertTrue(version.matchesQuery("PIXEZ"))
        assertFalse(version.matchesQuery("3.0"))
    }

    @Test
    fun `the channel is read from the release channel and from the version name`() {
        // F-Droid has no prerelease flag. index-v2 may carry `releaseChannels` and mostly does not, so a
        // build named 2.0.0-beta has to be recognised whatever the index said.
        assertTrue(fdroidVersion(10L, versionName = "2.0.0-beta").channel().isPreRelease)
        assertTrue(fdroidVersion(10L, versionName = "1.4.0", releaseChannels = listOf("beta")).channel().isPreRelease)
        assertFalse(fdroidVersion(10L, versionName = "1.4.0").channel().isPreRelease)
    }
}

/**
 * The badge has to name the build a refresh would actually offer.
 *
 * So it runs the real `FdroidCandidateSelector` over the same two channel-split snapshots
 * `FdroidReleaseCheckSource` splits, rather than reimplementing "the newest one" — which would quietly
 * disagree with the checker on exactly the tracks that configured something.
 */
class FdroidRecommendedVersionsTest {
    @Test
    fun `the recommended build respects the track's anti-feature policy`() {
        val snapshot =
            packageSnapshot(
                fdroidVersion(30L, antiFeatures = listOf(FdroidAntiFeatureSnapshot(id = "Tracking"))),
                fdroidVersion(20L),
            )

        val strict =
            fdroidRecommendedVersionsFor(
                snapshot = snapshot,
                config = FdroidTrackedAppConfig(antiFeaturePolicy = FdroidAntiFeaturePolicy.HideTracking),
                deviceSdk = 34,
            )
        val permissive =
            fdroidRecommendedVersionsFor(
                snapshot = snapshot,
                config = FdroidTrackedAppConfig(antiFeaturePolicy = FdroidAntiFeaturePolicy.ShowAndWarn),
                deviceSdk = 34,
            )

        // The newest build is not the recommended one for a track that rejects tracking, and the page has
        // to say so or it is describing a different track than the one being refreshed.
        assertEquals(20L, strict.stable?.versionCode)
        assertEquals(30L, permissive.stable?.versionCode)
    }

    @Test
    fun `a build this device cannot run is not the recommended one`() {
        val recommended =
            fdroidRecommendedVersionsFor(
                snapshot = packageSnapshot(fdroidVersion(30L, minSdk = 36), fdroidVersion(20L, minSdk = 24)),
                config = FdroidTrackedAppConfig(),
                deviceSdk = 34,
            )

        assertEquals(20L, recommended.stable?.versionCode)
    }

    @Test
    fun `stable and pre-release are resolved separately`() {
        val recommended =
            fdroidRecommendedVersionsFor(
                snapshot =
                    packageSnapshot(
                        fdroidVersion(40L, versionName = "2.0.0-beta1"),
                        fdroidVersion(30L, versionName = "1.9.0"),
                    ),
                config = FdroidTrackedAppConfig(),
                deviceSdk = 34,
            )

        assertEquals(30L, recommended.stable?.versionCode)
        assertEquals(40L, recommended.preRelease?.versionCode)
    }

    @Test
    fun `the repository's own suggestion is honoured when the track follows it`() {
        val snapshot =
            packageSnapshot(fdroidVersion(30L), fdroidVersion(20L), suggestedVersionCode = 20L)

        val following =
            fdroidRecommendedVersionsFor(
                snapshot = snapshot,
                config = FdroidTrackedAppConfig(
                    selectionMode = FdroidVersionSelectionMode.SuggestedVersionCode,
                ),
                deviceSdk = 34,
            )
        val ignoring =
            fdroidRecommendedVersionsFor(
                snapshot = snapshot,
                config = FdroidTrackedAppConfig(
                    selectionMode = FdroidVersionSelectionMode.HighestVersionCode,
                ),
                deviceSdk = 34,
            )

        assertEquals(20L, following.stable?.versionCode)
        assertEquals(30L, ignoring.stable?.versionCode)
    }

    @Test
    fun `a history the track rejects entirely recommends nothing`() {
        val recommended =
            fdroidRecommendedVersionsFor(
                snapshot = packageSnapshot(fdroidVersion(30L, minSdk = 40)),
                config = FdroidTrackedAppConfig(),
                deviceSdk = 34,
            )

        assertNull(recommended.stable)
        assertNull(recommended.preRelease)
    }
}

private fun packageSnapshot(
    vararg versions: os.kei.feature.github.data.remote.fdroid.FdroidVersionSnapshot,
    suggestedVersionCode: Long? = null,
): FdroidPackageSnapshot =
    FdroidPackageSnapshot(
        repoUrl = "https://example.org/repo",
        packageName = "com.example.app",
        suggestedVersionCode = suggestedVersionCode,
        versions = versions.toList(),
    )
