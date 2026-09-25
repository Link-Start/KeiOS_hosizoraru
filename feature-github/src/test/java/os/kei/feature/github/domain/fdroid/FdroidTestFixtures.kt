package os.kei.feature.github.domain.fdroid

import os.kei.feature.github.data.remote.fdroid.FdroidVersionSnapshot

/** One index version row; the data class has no defaults, so the fields no test reads are filled here. */
internal fun fdroidVersionFixture(
    versionCode: Long,
    versionName: String = "1.$versionCode",
    apkName: String = "app_$versionCode.apk",
    apkPath: String = apkName,
    apkSha256: String = "sha256-$versionCode",
    minSdk: Int? = 23,
    signerSha256: List<String> = emptyList(),
): FdroidVersionSnapshot =
    FdroidVersionSnapshot(
        versionName = versionName,
        versionCode = versionCode,
        apkName = apkName,
        apkPath = apkPath,
        apkSha256 = apkSha256,
        apkSizeBytes = 0L,
        addedAtMillis = null,
        minSdk = minSdk,
        targetSdk = 35,
        nativeAbis = emptyList(),
        signerSha256 = signerSha256,
        releaseChannels = emptyList(),
        whatsNew = "",
        antiFeatures = emptyList(),
    )
