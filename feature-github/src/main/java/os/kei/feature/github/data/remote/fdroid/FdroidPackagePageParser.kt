package os.kei.feature.github.data.remote.fdroid

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * A repository's package page, read as a version history.
 *
 * The reason this exists rather than the index: f-droid.org's `/api/v1/packages/<pkg>` answers with a
 * version name and a code and nothing else, and `index-v2.json` — the only endpoint that carries what a
 * build *is* — is 58 MB for that repository. The package page is **37 KB** and states, per version, the
 * name and code, which one is suggested, the date it landed, its ABIs, the Android version it needs, how
 * it was signed, its size, and a direct APK link. That is the whole set this app shows, at a
 * fifteen-hundredth of the bytes.
 *
 * Parsing a page is not this codebase's first choice and not its first instance: the GitHub release path
 * already reads a release out of its HTML when there is no API token, for the same reason — the cheap
 * structured endpoint does not carry enough.
 *
 * Structural markers only, never prose. Every field is taken from a class name or a tag F-Droid's own
 * templates emit (`package-version`, `package-version-download`, `package-nativecode`,
 * `suggested-badge`), so a wording change cannot silently corrupt a version code. The two exceptions are
 * the date and the Android requirement, which the template renders as English sentences — which is why
 * [fdroidPackagePageUrl] asks for the `/en/` page rather than whatever the device's locale would serve.
 * Both degrade to null rather than to a wrong number.
 */
object FdroidPackagePageParser {
    fun parseVersions(html: String): List<FdroidVersionSnapshot> =
        VERSION_BLOCK
            .split(html)
            .drop(1)
            .mapNotNull { block -> block.toVersionSnapshot() }

    /** The version the repository itself marks as suggested, or null when the page marks none. */
    fun parseSuggestedVersionCode(html: String): Long? =
        VERSION_BLOCK
            .split(html)
            .drop(1)
            .firstOrNull { block -> SUGGESTED_BADGE in block }
            ?.let { block -> VERSION_HEADING.find(block)?.groupValues?.get(2)?.toLongOrNull() }

    private fun String.toVersionSnapshot(): FdroidVersionSnapshot? {
        val heading = VERSION_HEADING.find(this) ?: return null
        val versionCode = heading.groupValues[2].toLongOrNull() ?: return null
        val downloadBlock = DOWNLOAD_BLOCK.find(this)?.value.orEmpty()
        val apkUrl = APK_HREF.find(downloadBlock)?.groupValues?.get(1).orEmpty()
        return FdroidVersionSnapshot(
            versionName = heading.groupValues[1].trim(),
            versionCode = versionCode,
            apkName = apkUrl.substringAfterLast('/'),
            // Absolute, which resolveFdroidApkDownloadUrl already accepts — the page links straight at
            // the repository rather than relative to it.
            apkPath = apkUrl,
            // The page publishes neither, and leaving them blank is deliberate: the trust check verifies
            // a download against these fields, and a build with no hash offers no verification while a
            // build with the wrong one asserts a false one. The refresh sidecar fills them in for the
            // build it selected, through the normal merge.
            apkSha256 = "",
            apkSizeBytes = SIZE.find(downloadBlock)?.let { match ->
                sizeToBytes(match.groupValues[1], match.groupValues[2])
            } ?: 0L,
            addedAtMillis = ADDED_ON.find(this)?.groupValues?.get(1)?.let(::parseAddedDate),
            minSdk = REQUIRES_ANDROID.find(this)?.groupValues?.get(1)?.let(::androidVersionToApiLevel),
            targetSdk = null,
            nativeAbis = NATIVE_CODE.findAll(this).map { match -> match.groupValues[1].trim() }.toList(),
            signerSha256 = emptyList(),
            // The page has no release-channel concept; the version name is what classification is left
            // with, and fdroidReleaseChannelOf already reads it.
            releaseChannels = emptyList(),
            whatsNew = "",
            antiFeatures = emptyList(),
        )
    }

    private fun sizeToBytes(
        amount: String,
        unit: String,
    ): Long {
        val value = amount.toDoubleOrNull() ?: return 0L
        val multiplier =
            when (unit.uppercase(Locale.ROOT)) {
                "KIB" -> 1024.0
                "MIB" -> 1024.0 * 1024.0
                "GIB" -> 1024.0 * 1024.0 * 1024.0
                else -> return 0L
            }
        return (value * multiplier).toLong()
    }

    /**
     * `Aug 10, 2026`, as the `/en/` page writes it. Null rather than a guess on anything else.
     *
     * Resolved at local midnight, not UTC midnight. The page states a calendar date and nothing finer, so
     * anchoring it to UTC and rendering it in the reader's zone invents an hour — "26-08-10 08:00" for a
     * field that never carried a time. Local midnight is both the reader's own calendar date and the only
     * instant that displays as one.
     */
    private fun parseAddedDate(text: String): Long? =
        runCatching {
            SimpleDateFormat("MMM d, yyyy", Locale.US)
                .apply { timeZone = TimeZone.getDefault() }
                .parse(text.trim())
                ?.time
        }.getOrNull()?.takeIf { millis -> millis > 0L }

    /**
     * "This version requires Android 6.0 or newer" as an API level.
     *
     * The page states a marketing version and the compatibility filter needs an API level. The mapping is
     * public and finite, and an unknown entry yields null — which the selector and the page both read as
     * "the index did not say", the same as F-Droid's own behaviour, rather than as incompatible.
     */
    private fun androidVersionToApiLevel(marketingVersion: String): Int? =
        ANDROID_API_LEVELS[marketingVersion.trim()]
            // A point release the table does not list ("6.0.1") still pins its major line.
            ?: ANDROID_API_LEVELS[marketingVersion.trim().substringBeforeLast('.')]

    private val VERSION_BLOCK = Regex("""<li[^>]*class="package-version"""")
    private val VERSION_HEADING = Regex("""<b>\s*Version\s+(.+?)\s*</b>\s*\((\d+)\)""")
    private val DOWNLOAD_BLOCK =
        Regex("""<p class="package-version-download">.*?</p>""", RegexOption.DOT_MATCHES_ALL)
    private val APK_HREF = Regex("""href="([^"]+\.apk)"""")
    private val SIZE = Regex("""([\d.]+)\s*(KiB|MiB|GiB)""")
    private val ADDED_ON = Regex("""Added on\s+([A-Z][a-z]{2}\s+\d{1,2},\s+\d{4})""")
    private val REQUIRES_ANDROID = Regex("""requires Android\s+([\d.]+)""")
    private val NATIVE_CODE = Regex("""<code class="package-nativecode">([^<]+)</code>""")
    private const val SUGGESTED_BADGE = "suggested-badge"

    private val ANDROID_API_LEVELS =
        mapOf(
            "1.0" to 1, "1.1" to 2, "1.5" to 3, "1.6" to 4,
            "2.0" to 5, "2.0.1" to 6, "2.1" to 7, "2.2" to 8, "2.3" to 9, "2.3.3" to 10,
            "3.0" to 11, "3.1" to 12, "3.2" to 13,
            "4.0" to 14, "4.0.3" to 15, "4.1" to 16, "4.2" to 17, "4.3" to 18, "4.4" to 19,
            "4.4W" to 20,
            "5.0" to 21, "5.1" to 22,
            "6.0" to 23,
            "7.0" to 24, "7.1" to 25,
            "8.0" to 26, "8.1" to 27,
            "9" to 28, "9.0" to 28,
            "10" to 29, "10.0" to 29,
            "11" to 30, "11.0" to 30,
            "12" to 31, "12.0" to 31, "12L" to 32,
            "13" to 33, "13.0" to 33,
            "14" to 34, "14.0" to 34,
            "15" to 35, "15.0" to 35,
            "16" to 36, "16.0" to 36,
            "17" to 37, "17.0" to 37,
        )
}

/**
 * The page to read for a package, in English so its two prose fields parse deterministically.
 *
 * Null for a repository with no known page layout. Only f-droid.org and its archive are claimed here:
 * this parser is written against F-Droid's own templates, and pointing it at an unrelated repository's
 * site would produce plausible-looking nonsense rather than an error.
 */
fun fdroidPackagePageUrl(
    normalizedRepoUrl: String,
    packageName: String,
): String? {
    val packageId = packageName.trim()
    if (packageId.isBlank()) return null
    val host = runCatching { java.net.URI(normalizedRepoUrl.trim()).host }.getOrNull()
        .orEmpty()
        .lowercase(Locale.ROOT)
        .removePrefix("www.")
    return when (host) {
        "f-droid.org" -> "https://f-droid.org/en/packages/$packageId/"
        else -> null
    }
}
