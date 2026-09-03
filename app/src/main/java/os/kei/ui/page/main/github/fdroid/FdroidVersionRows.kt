package os.kei.ui.page.main.github.fdroid

import os.kei.feature.github.data.local.fdroid.FdroidVersionMetadataSummary
import os.kei.feature.github.data.remote.fdroid.FdroidPackageSnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidVersionSnapshot
import os.kei.feature.github.domain.fdroid.FdroidCandidateSelector
import os.kei.feature.github.domain.fdroid.fdroidReleaseChannelOf
import os.kei.feature.github.model.FdroidTrackedAppConfig
import os.kei.feature.github.model.GitHubReleaseChannel

/**
 * One build in an F-Droid package's history, as the page shows it.
 *
 * Flattened out of [FdroidVersionSnapshot] rather than wrapping it, because the two facts a row needs
 * most — which build this track would actually install, and whether this device can run it — are not
 * properties of the index entry at all. They come from the track's own selection config and from the
 * running SDK level, and deriving them per row while drawing would mean re-running the candidate
 * selector inside a lazy item.
 */
internal data class FdroidVersionRow(
    val version: FdroidVersionSnapshot,
    val channel: GitHubReleaseChannel,
    /** The build this track would install right now — see [FdroidCandidateSelector]. */
    val recommended: Boolean,
    /** True when this build's code, or failing that its name, is what is installed. */
    val installed: Boolean,
    /** False when this device's SDK is below the build's `minSdk`, so F-Droid would not offer it. */
    val compatible: Boolean,
    /** Empty when the source that listed this build did not say where its APK is. */
    val downloadUrl: String,
) {
    /** The lazy list's key for this row — see [fdroidVersionRowId]. */
    val id: String = fdroidVersionRowId(version)

    /** What the card's title says: the name if the index gave one, else the code. */
    val displayName: String
        get() = version.versionName.trim().ifBlank { version.versionCode.toString() }
}

/** The two builds the page floats into the reading lane, resolved the way the update check resolves them. */
internal data class FdroidRecommendedVersions(
    val stable: FdroidVersionSnapshot?,
    val preRelease: FdroidVersionSnapshot?,
)

/**
 * Which builds the track would install, stable and pre-release.
 *
 * Runs the real [FdroidCandidateSelector] over the same two channel-split snapshots
 * `FdroidReleaseCheckSource` splits, so the build this page marks recommended is the build a refresh
 * would actually offer — including the track's anti-feature policy, its version and APK name filters,
 * and its selection mode. Reimplementing "the newest one" here would quietly disagree with the checker
 * on exactly the tracks that configured something.
 */
internal fun fdroidRecommendedVersionsFor(
    snapshot: FdroidPackageSnapshot,
    config: FdroidTrackedAppConfig,
    deviceSdk: Int,
): FdroidRecommendedVersions {
    val stable =
        FdroidCandidateSelector.select(
            snapshot = snapshot.copy(versions = snapshot.versions.filter { !it.channel().isPreRelease }),
            config = config,
            deviceSdk = deviceSdk,
        )
    val preRelease =
        FdroidCandidateSelector.select(
            snapshot = snapshot.copy(versions = snapshot.versions.filter { it.channel().isPreRelease }),
            config = config,
            deviceSdk = deviceSdk,
        )
    return FdroidRecommendedVersions(stable = stable, preRelease = preRelease)
}

/**
 * The complete history, as rich as the two sources between them can make it.
 *
 * The page has two sources and neither is sufficient alone. The refresh sidecar holds full records —
 * size, hash, signer, ABIs, release notes — but only for the eight newest builds, because that is what
 * an update decision needs and all `buildFdroidMetadataSidecar` keeps. The package API answers with
 * every build there has ever been, but on f-droid.org's own `/api/v1/packages` that is a version name
 * and a code and nothing else.
 *
 * So [remote] decides *which* builds exist and [cached] fills in what it knows about them. The result is
 * a history that is complete at the bottom and detailed at the top, which is the shape of the question:
 * the recent builds are the ones anyone installs, and the old ones only have to be listed.
 *
 * A cached build the remote list does not mention is kept rather than dropped. That is not a
 * hypothetical — the package API 404s on repositories that only publish an index, and losing the eight
 * builds already on disk would turn a partial answer into an empty page.
 */
internal fun mergeFdroidVersions(
    cached: List<FdroidVersionSnapshot>,
    remote: List<FdroidVersionSnapshot>,
): List<FdroidVersionSnapshot> {
    if (remote.isEmpty()) return fdroidVersionHistoryOf(cached)
    val unclaimedCached = cached.toMutableList()
    val merged =
        remote.map { version ->
            val matchIndex = unclaimedCached.indexOfBestMatch(version)
            if (matchIndex < 0) {
                version
            } else {
                // Removed as it is claimed, so two split APKs under one version code cannot both inherit
                // the same cached record and end up with the same hash.
                version.enrichedWith(unclaimedCached.removeAt(matchIndex))
            }
        }
    return fdroidVersionHistoryOf(merged + unclaimedCached)
}

/**
 * A list of builds in the order a history reads, with one row per APK.
 *
 * Newest first, and stable within a version code so per-ABI splits do not reshuffle between loads.
 * Reduced to distinct rows because the lazy list keys off [fdroidVersionRowId] and a duplicate key
 * throws rather than drawing a repeated card — and duplicates do arrive: the refresh sidecar stores its
 * selected build both on its own and inside the candidate list.
 */
internal fun fdroidVersionHistoryOf(versions: List<FdroidVersionSnapshot>): List<FdroidVersionSnapshot> =
    versions
        .sortedWith(
            compareByDescending<FdroidVersionSnapshot> { it.versionCode }
                .thenBy { it.apkName }
                .thenBy { it.apkPath },
        ).distinctBy { version -> fdroidVersionRowId(version) }

/**
 * One build's identity, used both as the lazy list's key and as the dedupe key.
 *
 * A version code alone is not an identity: a split build publishes several APKs under one code, and a
 * lazy list whose keys collide throws. The hash is in the key too, because some repositories reuse an
 * APK name across rebuilds of one version code.
 *
 * One function rather than two so the two uses cannot drift — a dedupe that keyed differently from the
 * list would leave the collision it was there to prevent.
 */
internal fun fdroidVersionRowId(version: FdroidVersionSnapshot): String =
    listOf(
        version.versionCode.toString(),
        version.apkName.trim().ifBlank { version.apkPath.trim() },
        version.apkSha256.trim(),
    ).joinToString("|")

/** Cached records speak the sidecar's summary type; everything downstream speaks the index type. */
internal fun FdroidVersionMetadataSummary.toVersionSnapshot(): FdroidVersionSnapshot =
    FdroidVersionSnapshot(
        versionName = versionName,
        versionCode = versionCode,
        apkName = apkName,
        apkPath = apkPath,
        apkSha256 = apkSha256,
        apkSizeBytes = apkSizeBytes,
        addedAtMillis = addedAtMillis,
        minSdk = minSdk,
        targetSdk = targetSdk,
        nativeAbis = nativeAbis,
        signerSha256 = signerSha256,
        releaseChannels = releaseChannels,
        whatsNew = whatsNew,
        antiFeatures = antiFeatures,
    )

internal fun FdroidVersionSnapshot.channel(): GitHubReleaseChannel =
    fdroidReleaseChannelOf(releaseChannels = releaseChannels, versionName = versionName)

/**
 * Whether this build matches what is installed.
 *
 * Version code first, because F-Droid publishes one and it is exact. The name is only consulted when
 * there is no installed code to compare — a track added before the check cache recorded one.
 */
internal fun FdroidVersionSnapshot.matchesInstalled(
    installedVersionCode: Long,
    installedVersion: String,
): Boolean {
    if (installedVersionCode > 0L) return versionCode == installedVersionCode
    val installed = installedVersion.normalizedVersionName()
    if (installed.isBlank()) return false
    return versionName.normalizedVersionName() == installed
}

/** `minSdk` absent means the index did not say, and F-Droid treats that as installable. */
internal fun FdroidVersionSnapshot.isCompatibleWith(deviceSdk: Int): Boolean = (minSdk ?: 1) <= deviceSdk

/** Matches this build against a typed query, over the two things a reader would type. */
internal fun FdroidVersionSnapshot.matchesQuery(query: String): Boolean {
    val needle = query.trim()
    if (needle.isBlank()) return true
    return versionName.contains(needle, ignoreCase = true) ||
        versionCode.toString().contains(needle) ||
        apkName.contains(needle, ignoreCase = true)
}

private fun String.normalizedVersionName(): String = trim().removePrefix("v").removePrefix("V").trim()

/**
 * The cached record that describes this remote build, or -1.
 *
 * Three cases, and the middle one is the whole subtlety:
 *
 * 1. Same version code, same APK name — the same file. Claim it.
 * 2. Same version code, and the cached record names no file at all. Also claim it: a record with no file
 *    identity cannot be "a different file", and it is what a sidecar written from `/api/v1/packages`
 *    looks like, since that endpoint publishes no file. Leaving it unclaimed is what made every version
 *    render twice — once from the page, once from the nameless cache — with the same version code and two
 *    different row ids.
 * 3. Same version code, *different* APK name. Do not claim it. This is the safety case: handing
 *    `app_20_arm64.apk` the hash and signer of `app_20_armv7.apk` because the cache happened to hold only
 *    the other split would make the APK trust check verify a download against the wrong file's
 *    fingerprint. Better an unenriched row that offers no hash than one that asserts a false one. (A
 *    nameless record cannot carry a hash either, so case 2 can never smuggle one in.)
 */
private fun List<FdroidVersionSnapshot>.indexOfBestMatch(version: FdroidVersionSnapshot): Int {
    val apkName = version.apkName.trim()
    if (apkName.isBlank()) {
        return indexOfFirst { candidate -> candidate.versionCode == version.versionCode }
    }
    val sameFile = indexOfFirst { candidate ->
        candidate.versionCode == version.versionCode &&
            candidate.apkName.trim().equals(apkName, ignoreCase = true)
    }
    if (sameFile >= 0) return sameFile
    return indexOfFirst { candidate ->
        candidate.versionCode == version.versionCode && candidate.apkName.isBlank()
    }
}

/** Field by field, the remote value when it said something and the cached one when it did not. */
private fun FdroidVersionSnapshot.enrichedWith(cached: FdroidVersionSnapshot): FdroidVersionSnapshot =
    copy(
        versionName = versionName.ifBlank { cached.versionName },
        apkName = apkName.ifBlank { cached.apkName },
        apkPath = apkPath.ifBlank { cached.apkPath },
        apkSha256 = apkSha256.ifBlank { cached.apkSha256 },
        apkSizeBytes = if (apkSizeBytes > 0L) apkSizeBytes else cached.apkSizeBytes,
        addedAtMillis = addedAtMillis ?: cached.addedAtMillis,
        minSdk = minSdk ?: cached.minSdk,
        targetSdk = targetSdk ?: cached.targetSdk,
        nativeAbis = nativeAbis.ifEmpty { cached.nativeAbis },
        signerSha256 = signerSha256.ifEmpty { cached.signerSha256 },
        releaseChannels = releaseChannels.ifEmpty { cached.releaseChannels },
        whatsNew = whatsNew.ifBlank { cached.whatsNew },
        antiFeatures = antiFeatures.ifEmpty { cached.antiFeatures },
    )

