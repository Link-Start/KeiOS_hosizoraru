package os.kei.feature.github.data.remote.fdroid

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The parser, against a page f-droid.org actually served.
 *
 * The fixture is the real `<ul class="package-versions-list">` for `com.github.catfriend1.syncthingfork`
 * captured 2026-09-03, permission lists trimmed. Written from a live capture rather than by hand because
 * the whole point of reading the page is that it carries what the cheap API does not, and a hand-written
 * fixture would only ever prove the parser agrees with its author.
 *
 * What this is for: f-droid.org's `/api/v1/packages/<pkg>` returns a version name and a code and nothing
 * else, and `index-v2.json` — which does carry the rest — is 58 MB for that repository. This page is
 * 37 KB and has all of it.
 */
class FdroidPackagePageParserTest {
    @Test
    fun `every version on the page is read, newest first as the page lists them`() {
        val versions = FdroidPackagePageParser.parseVersions(pageFixture())

        assertEquals(listOf(2010300L, 2010200L, 2010100L), versions.map { it.versionCode })
        assertEquals(listOf("2.1.3.0", "2.1.2.0", "2.1.1.0"), versions.map { it.versionName })
    }

    @Test
    fun `a build carries what the package API does not publish at all`() {
        val latest = FdroidPackagePageParser.parseVersions(pageFixture()).first()

        // The four fields whose absence made the history page an empty list of numbers.
        assertEquals("com.github.catfriend1.syncthingfork_2010300.apk", latest.apkName)
        assertEquals("https://f-droid.org/repo/com.github.catfriend1.syncthingfork_2010300.apk", latest.apkPath)
        assertEquals(65L * 1024L * 1024L, latest.apkSizeBytes)
        assertEquals(listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"), latest.nativeAbis)
    }

    @Test
    fun `the Android requirement becomes an API level, because that is what the filter compares`() {
        // "This version requires Android 6.0 or newer" -- the page states a marketing version and the
        // compatibility filter needs the level.
        assertEquals(23, FdroidPackagePageParser.parseVersions(pageFixture()).first().minSdk)
    }

    @Test
    fun `the added date is read from the English page`() {
        val latest = FdroidPackagePageParser.parseVersions(pageFixture()).first()

        val millis = requireNotNull(latest.addedAtMillis)
        // "Added on Aug 10, 2026", resolved to local midnight -- so it is the reader's own calendar date
        // and renders without a phantom time. Read back in the same zone rather than as a magic instant.
        val zoned = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
        assertEquals(java.time.LocalDate.of(2026, 8, 10), zoned.toLocalDate())
        assertEquals(java.time.LocalTime.MIDNIGHT, zoned.toLocalTime())
    }

    @Test
    fun `the suggested badge names the repository's own recommendation`() {
        // One badge on the page, and it must resolve to that block's code rather than to the first block.
        assertEquals(2010300L, FdroidPackagePageParser.parseSuggestedVersionCode(pageFixture()))
    }

    @Test
    fun `the hash and signer are left empty rather than invented`() {
        // The page publishes neither. The trust check verifies a download against exactly these fields,
        // so a blank offers no verification while a guess would assert a false one. The refresh sidecar
        // fills them in for the build it selected, through the normal merge.
        FdroidPackagePageParser.parseVersions(pageFixture()).forEach { version ->
            assertEquals("", version.apkSha256)
            assertTrue(version.signerSha256.isEmpty())
        }
    }

    @Test
    fun `markup with no version blocks yields nothing rather than a half-built version`() {
        assertTrue(FdroidPackagePageParser.parseVersions("<html><body>moved</body></html>").isEmpty())
        assertNull(FdroidPackagePageParser.parseSuggestedVersionCode("<html></html>"))
    }

    @Test
    fun `a block without a parseable version code is dropped, not defaulted to zero`() {
        // A version code of 0 would sort to the bottom of the history and claim to be a real build.
        val html =
            """
            <li class="package-version"><div class="package-version-header">
            <b>Version 9.9</b> (not-a-number)</div></li>
            """.trimIndent()

        assertTrue(FdroidPackagePageParser.parseVersions(html).isEmpty())
    }

    @Test
    fun `only the page's own structural markers are read`() {
        // Prose that mentions a version must not become one. Everything except the date and the Android
        // requirement comes from a class name or tag F-Droid's templates emit, so a wording change cannot
        // corrupt a version code.
        val html = "<p>Version 1.2.3 (999) was withdrawn. suggested-badge</p>"

        assertTrue(FdroidPackagePageParser.parseVersions(html).isEmpty())
    }

    @Test
    fun `the page url is claimed only for the repository this parser was written against`() {
        assertEquals(
            "https://f-droid.org/en/packages/com.example.app/",
            fdroidPackagePageUrl("https://f-droid.org/repo", "com.example.app"),
        )
        // Pointing this parser at an unrelated repository's site would produce plausible nonsense rather
        // than an error, so it declines instead and the caller falls back to the package API.
        assertNull(fdroidPackagePageUrl("https://apt.izzysoft.de/fdroid/repo", "com.example.app"))
        assertNull(fdroidPackagePageUrl("https://f-droid.org/repo", "   "))
    }

    @Test
    fun `sizes are read in the binary units the page writes`() {
        val html =
            """
            <li class="package-version"><div class="package-version-header">
            <b>Version 1.0</b> (10)</div>
            <p class="package-version-download"><b><a href="https://f-droid.org/repo/a_10.apk">Download APK</a></b>
            4.5 MiB</p></li>
            <li class="package-version"><div class="package-version-header">
            <b>Version 0.9</b> (9)</div>
            <p class="package-version-download"><b><a href="https://f-droid.org/repo/a_9.apk">Download APK</a></b>
            820 KiB</p></li>
            """.trimIndent()

        val sizes = FdroidPackagePageParser.parseVersions(html).map { it.apkSizeBytes }
        assertEquals(listOf((4.5 * 1024 * 1024).toLong(), 820L * 1024L), sizes)
    }
}

private fun pageFixture(): String =
    requireNotNull(
        FdroidPackagePageParserTest::class.java
            .classLoader
            ?.getResourceAsStream("fdroid/fdroid-package-page.html"),
    ) { "missing fdroid/fdroid-package-page.html test resource" }
        .bufferedReader()
        .use { reader -> reader.readText() }
