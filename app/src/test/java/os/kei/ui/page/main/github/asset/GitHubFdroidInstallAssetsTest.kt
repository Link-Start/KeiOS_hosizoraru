package os.kei.ui.page.main.github.asset

import os.kei.feature.github.data.local.fdroid.FdroidMetadataSidecar
import os.kei.feature.github.data.local.fdroid.FdroidPackageMetadataSummary
import os.kei.feature.github.data.local.fdroid.FdroidRepoMetadataSummary
import os.kei.feature.github.data.local.fdroid.FdroidTrustSummary
import os.kei.feature.github.data.local.fdroid.FdroidVersionMetadataSummary
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.fdroid.FdroidIndexV2Parser
import os.kei.feature.github.data.remote.fdroid.FdroidPackageApiClient
import os.kei.feature.github.data.remote.fdroid.FdroidPackagePageParser
import os.kei.feature.github.data.remote.fdroid.FdroidVersionSnapshot
import os.kei.feature.github.model.FdroidIndexFormat
import os.kei.feature.github.model.FdroidTrustPolicy
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.fdroidRepositoryCheckSourceSignature
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The real repositories' addresses, file names and answers below were read live on 2026-09-23. Each
 * expected link answered 206 to a one-byte range request, and the same name read against the host's root
 * answered 404. The `example` hosts and build 102 are made up.
 */
class GitHubFdroidInstallAssetsTest {
    @Test
    fun `an index-v2 file name is read inside the repository, not at the host's root`() {
        // Widgets Anywhere as IzzyOnDroid's index names it. Read against the host, this was
        // https://apt.izzysoft.de/tk.zwander.lockscreenwidgets_217.apk, and the APK info sheet, download,
        // share and install all got a 404.
        assertEquals(
            "https://apt.izzysoft.de/fdroid/repo/tk.zwander.lockscreenwidgets_217.apk",
            resolveFdroidApkDownloadUrl(
                repoUrl = "https://apt.izzysoft.de/fdroid/repo",
                apkPath = "/tk.zwander.lockscreenwidgets_217.apk",
            ),
        )
        assertEquals(
            "https://f-droid.org/repo/org.fdroid.fdroid_2000041.apk",
            resolveFdroidApkDownloadUrl(
                repoUrl = "https://f-droid.org/repo",
                apkPath = "/org.fdroid.fdroid_2000041.apk",
            ),
        )
        assertEquals(
            "https://f-droid.org/archive/ac.mdiq.Podcini.A_122.apk",
            resolveFdroidApkDownloadUrl(
                repoUrl = "https://f-droid.org/archive",
                apkPath = "/ac.mdiq.Podcini.A_122.apk",
            ),
        )
        assertEquals(
            "https://guardianproject.info/fdroid/repo/Checkey-0.1.1.apk",
            resolveFdroidApkDownloadUrl(
                repoUrl = "https://guardianproject.info/fdroid/repo",
                apkPath = "/Checkey-0.1.1.apk",
            ),
        )
    }

    @Test
    fun `a bare file name is read inside the repository`() {
        // index-v1's apkName. The same shape arrives through the asset's apkName fallback.
        assertEquals(
            "https://apt.izzysoft.de/fdroid/repo/tk.zwander.lockscreenwidgets_217.apk",
            resolveFdroidApkDownloadUrl(
                repoUrl = "https://apt.izzysoft.de/fdroid/repo",
                apkPath = "tk.zwander.lockscreenwidgets_217.apk",
            ),
        )
    }

    @Test
    fun `an absolute link is used as it stands`() {
        // The package page links every build absolutely.
        assertEquals(
            "https://f-droid.org/repo/org.fdroid.fdroid_2000041.apk",
            resolveFdroidApkDownloadUrl(
                repoUrl = "https://f-droid.org/repo",
                apkPath = "https://f-droid.org/repo/org.fdroid.fdroid_2000041.apk",
            ),
        )
        assertEquals(
            "https://cdn.example/demo.apk",
            resolveFdroidApkDownloadUrl(
                repoUrl = "https://repo.example/fdroid/repo",
                apkPath = "https://cdn.example/demo.apk",
            ),
        )
        // HTML may leave the scheme out. The link still names its own host.
        assertEquals(
            "https://cdn.example/demo.apk",
            resolveFdroidApkDownloadUrl(
                repoUrl = "https://repo.example/fdroid/repo",
                apkPath = "//cdn.example/demo.apk",
            ),
        )
    }

    @Test
    fun `a trailing slash on the repository address changes nothing`() {
        assertEquals(
            "https://apt.izzysoft.de/fdroid/repo/tk.zwander.lockscreenwidgets_217.apk",
            resolveFdroidApkDownloadUrl(
                repoUrl = "https://apt.izzysoft.de/fdroid/repo/",
                apkPath = "/tk.zwander.lockscreenwidgets_217.apk",
            ),
        )
    }

    @Test
    fun `a name that repeats the repository's path is not second-guessed`() {
        // No repository publishes this: in every index read, each name is a single file. F-Droid's own client
        // trims the slash and appends the name to the repository address, so a repository that did publish
        // it would be broken there as well. Treating it as host-absolute would be right only about a shape
        // that does not exist, and wrong about a real folder that happened to share the repository's name.
        assertEquals(
            "https://f-droid.org/repo/repo/org.fdroid.fdroid_102.apk",
            resolveFdroidApkDownloadUrl(
                repoUrl = "https://f-droid.org/repo",
                apkPath = "/repo/org.fdroid.fdroid_102.apk",
            ),
        )
    }

    @Test
    fun `IzzyOnDroid's index entry becomes the repository's link`() {
        val snapshot =
            FdroidIndexV2Parser
                .parsePackage(
                    repoUrl = "https://apt.izzysoft.de/fdroid/repo",
                    packageName = "tk.zwander.lockscreenwidgets",
                    rawJson = IZZY_WIDGETS_ANYWHERE_INDEX_V2_PACKAGE,
                ).getOrThrow()

        val asset = assertNotNull(snapshot.versions.single().assetIn(snapshot.repoUrl))

        assertEquals("https://apt.izzysoft.de/fdroid/repo/tk.zwander.lockscreenwidgets_217.apk", asset.downloadUrl)
        assertEquals("tk.zwander.lockscreenwidgets_217.apk", asset.name)
        assertEquals("sha256:efc9938e13818ecab59065c7d52e166cd7bc1c63da38501d7dd3826f1551590d", asset.digest)
    }

    @Test
    fun `the package page's link is kept as the page wrote it`() {
        val version = FdroidPackagePageParser.parseVersions(FDROID_PACKAGE_PAGE_VERSION).single()

        val asset = assertNotNull(version.assetIn("https://f-droid.org/repo"))

        assertEquals("https://f-droid.org/repo/org.fdroid.fdroid_2000041.apk", asset.downloadUrl)
    }

    @Test
    fun `the package API names no file, so its builds offer no asset`() {
        MockWebServer().use { server ->
            // IzzyOnDroid's answer, verbatim. f-droid.org's has the same shape.
            server.enqueue(MockResponse().setResponseCode(200).setBody(IZZY_WIDGETS_ANYWHERE_PACKAGE_API))
            val snapshot =
                runBlocking {
                    FdroidPackageApiClient().fetchPackage(
                        repoBaseUrl = server.url("/fdroid/repo").toString(),
                        packageName = "tk.zwander.lockscreenwidgets",
                    )
                }.getOrThrow()

            // A row with nowhere to download from would be a download button that does nothing.
            assertNull(snapshot.versions.single().assetIn(snapshot.repoUrl))
        }
    }

    @Test
    fun `fdroid sidecar maps selected apk to release asset bundle`() {
        val item = fdroidItem()
        val sidecar = sidecar(item)

        val data = item.fdroidAssetPanelData(sidecar)

        assertNotNull(data)
        assertEquals("1.2.3", data.targetRawTag)
        assertEquals(GITHUB_FDROID_ASSET_FETCH_SOURCE, data.bundle.fetchSource)
        assertEquals(item.fdroidRepositoryCheckSourceSignature(), data.bundle.sourceConfigSignature)
        assertEquals("F-Droid", data.bundle.releaseName)
        assertEquals("https://f-droid.org/packages/org.fdroid.fdroid/", data.bundle.htmlUrl)
        val asset = data.bundle.assets.single()
        assertEquals("org.fdroid.fdroid_102.apk", asset.name)
        assertEquals("https://f-droid.org/repo/org.fdroid.fdroid_102.apk", asset.downloadUrl)
        assertEquals("sha256:abc123", asset.digest)
        assertEquals(listOf("signer-1"), asset.signerSha256)
    }

    @Test
    fun `stale fdroid sidecar is ignored`() {
        val item = fdroidItem()
        val sidecar = sidecar(item).copy(sourceConfigSignature = "old")

        assertNull(item.fdroidAssetPanelData(sidecar))
    }

    private fun fdroidItem(): GitHubTrackedApp {
        return GitHubTrackedApp(
            repoUrl = "https://f-droid.org/packages/org.fdroid.fdroid/",
            owner = "f-droid.org",
            repo = "repo",
            packageName = "org.fdroid.fdroid",
            appLabel = "F-Droid",
            sourceMode = GitHubTrackedSourceMode.FdroidRepository,
        )
    }

    private fun sidecar(item: GitHubTrackedApp): FdroidMetadataSidecar {
        return FdroidMetadataSidecar(
            trackId = item.id,
            sourceConfigSignature = item.fdroidRepositoryCheckSourceSignature(),
            fetchedAtMillis = 1_777_000_000_000L,
            repo =
                FdroidRepoMetadataSummary(
                    repoUrl = "https://f-droid.org/repo",
                    repoName = "F-Droid",
                    repoDescription = "",
                    format = FdroidIndexFormat.V2,
                    timestampMillis = null,
                    packageCount = 0,
                    mirrors = emptyList(),
                ),
            packageInfo =
                FdroidPackageMetadataSummary(
                    packageName = "org.fdroid.fdroid",
                    appName = "F-Droid",
                    summary = "",
                    description = "",
                    license = "GPL-3.0",
                    sourceCodeUrl = "",
                    webSiteUrl = "",
                    issueTrackerUrl = "",
                    changelogUrl = "",
                    categories = emptyList(),
                ),
            selectedVersion =
                FdroidVersionMetadataSummary(
                    versionName = "1.2.3",
                    versionCode = 102L,
                    apkName = "org.fdroid.fdroid_102.apk",
                    // index-v2's shape: a leading slash, relative to the repository.
                    apkPath = "/org.fdroid.fdroid_102.apk",
                    apkSha256 = "abc123",
                    apkSizeBytes = 12_345L,
                    addedAtMillis = 1_776_000_000_000L,
                    minSdk = 23,
                    targetSdk = 35,
                    nativeAbis = emptyList(),
                    signerSha256 = listOf("signer-1"),
                    releaseChannels = emptyList(),
                    whatsNew = "Release notes",
                    antiFeatures = emptyList(),
                ),
            candidateVersions = emptyList(),
            trust =
                FdroidTrustSummary(
                    trustPolicy = FdroidTrustPolicy.TrackOnlyWarn,
                    repoFingerprint = "",
                    apkSha256 = "abc123",
                    signerSha256 = listOf("signer-1"),
                    hashAvailable = true,
                    signerAvailable = true,
                ),
            antiFeatures = emptyList(),
        )
    }
}

/** The asset both F-Droid surfaces would build for this version. */
private fun FdroidVersionSnapshot.assetIn(repoUrl: String): GitHubReleaseAssetFile? =
    fdroidVersionAssetFile(
        repoUrl = repoUrl,
        apkName = apkName,
        apkPath = apkPath,
        apkSha256 = apkSha256,
        apkSizeBytes = apkSizeBytes,
        addedAtMillis = addedAtMillis,
        signerSha256 = signerSha256,
    )

/** IzzyOnDroid's index-v2 entry for Widgets Anywhere, cut down to one build and the fields read from it. */
private val IZZY_WIDGETS_ANYWHERE_INDEX_V2_PACKAGE =
    """
    {
      "metadata": { "name": { "en-US": "Widgets Anywhere" } },
      "versions": {
        "efc9938e13818ecab59065c7d52e166cd7bc1c63da38501d7dd3826f1551590d": {
          "added": 1789754295000,
          "file": {
            "name": "/tk.zwander.lockscreenwidgets_217.apk",
            "sha256": "efc9938e13818ecab59065c7d52e166cd7bc1c63da38501d7dd3826f1551590d",
            "size": 23178383
          },
          "manifest": {
            "versionName": "4.7.0",
            "versionCode": 217,
            "usesSdk": { "minSdkVersion": 24, "targetSdkVersion": 37 },
            "signer": { "sha256": ["2ed2e38cd7f5f93c2a40594360cf3fb08cecfacf1f7994e59e1ce835272ccd85"] }
          }
        }
      }
    }
    """.trimIndent()

/** One build from `https://f-droid.org/en/packages/org.fdroid.fdroid/`, with its ABI and permission lists cut. */
private val FDROID_PACKAGE_PAGE_VERSION =
    """
    <li class="package-version" id="latest">
        <div class="package-version-header">
            <a name="2.0-rc1"></a>
            <a name="2000041"></a>
            <b>Version 2.0-rc1</b> (2000041)
            <span class="beta-badge">beta</span>
            Added on Aug 23, 2026
        </div>
        <p class="package-version-requirement">
            This version requires Android 7.0 or newer.
        </p>
        <p class="package-version-source">It is built and signed by F-Droid, and guaranteed to correspond to <a href="https://f-droid.org/repo/org.fdroid.fdroid_2000041_src.tar.gz">this source tarball</a>.
        </p>
        <p class="package-version-download">
            <b>
                <a href="https://f-droid.org/repo/org.fdroid.fdroid_2000041.apk">
                    Download APK
                </a>
            </b>
            12 MiB
            <a href="https://f-droid.org/repo/org.fdroid.fdroid_2000041.apk.asc">PGP Signature</a>
            &#124;
            <a href="https://f-droid.org/repo/org.fdroid.fdroid_2000041.log.gz">Build Log</a>
        </p>
    </li>
    """.trimIndent()

/** `https://apt.izzysoft.de/fdroid/api/v1/packages/tk.zwander.lockscreenwidgets`, verbatim. */
private const val IZZY_WIDGETS_ANYWHERE_PACKAGE_API =
    """{"packageName":"tk.zwander.lockscreenwidgets","suggestedVersionCode":"217","packages":[{"versionCode":"217","versionName":"4.7.0"}]}"""
