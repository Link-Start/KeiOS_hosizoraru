package os.kei.ui.page.main.github.fdroid

import os.kei.feature.github.data.remote.fdroid.FdroidAntiFeatureSnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidVersionSnapshot

/**
 * An index entry with everything filled in, so a test says only what it is about.
 *
 * Defaults are the rich shape — the one the refresh sidecar stores. [thinFdroidVersion] is the other
 * shape, which is what f-droid.org's package API actually answers with.
 */
internal fun fdroidVersion(
    versionCode: Long,
    versionName: String = "1.$versionCode.0",
    apkName: String = "app_$versionCode.apk",
    apkPath: String = apkName,
    apkSha256: String = "hash$versionCode",
    apkSizeBytes: Long = 1_024L * versionCode,
    addedAtMillis: Long? = 1_700_000_000_000L + versionCode,
    minSdk: Int? = 24,
    targetSdk: Int? = 34,
    nativeAbis: List<String> = emptyList(),
    signerSha256: List<String> = listOf("signer$versionCode"),
    releaseChannels: List<String> = emptyList(),
    whatsNew: String = "notes for $versionCode",
    antiFeatures: List<FdroidAntiFeatureSnapshot> = emptyList(),
): FdroidVersionSnapshot =
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

/**
 * What `/api/v1/packages/<pkg>` returns: a name, a code, and nothing else.
 *
 * The whole reason [mergeFdroidVersions] exists, so the tests for it have to be able to say this shape.
 */
internal fun thinFdroidVersion(
    versionCode: Long,
    versionName: String = "1.$versionCode.0",
): FdroidVersionSnapshot =
    FdroidVersionSnapshot(
        versionName = versionName,
        versionCode = versionCode,
        apkName = "",
        apkPath = "",
        apkSha256 = "",
        apkSizeBytes = 0L,
        addedAtMillis = null,
        minSdk = null,
        targetSdk = null,
        nativeAbis = emptyList(),
        signerSha256 = emptyList(),
        releaseChannels = emptyList(),
        whatsNew = "",
        antiFeatures = emptyList(),
    )
