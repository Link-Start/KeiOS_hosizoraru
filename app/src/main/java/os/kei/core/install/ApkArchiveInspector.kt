package os.kei.core.install

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

data class LocalApkArchiveInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val signatureSha256: List<String>,
    val debuggable: Boolean,
    val testOnly: Boolean
)

class ApkArchiveInspector(
    private val context: Context
) {
    fun inspect(file: File): Result<LocalApkArchiveInfo> = runCatching {
        require(file.exists() && file.isFile) { "APK file does not exist: ${file.absolutePath}" }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        } else {
            @Suppress("DEPRECATION")
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNATURES.toLong())
        }
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("APK package info unavailable")
        info.applicationInfo?.sourceDir = file.absolutePath
        info.applicationInfo?.publicSourceDir = file.absolutePath
        info.toLocalApkArchiveInfo()
    }
}

private fun PackageInfo.toLocalApkArchiveInfo(): LocalApkArchiveInfo {
    val appInfo = applicationInfo
    return LocalApkArchiveInfo(
        packageName = packageName.orEmpty(),
        versionName = versionName.orEmpty(),
        versionCode = longVersionCode,
        minSdk = appInfo?.minSdkVersion ?: -1,
        targetSdk = appInfo?.targetSdkVersion ?: -1,
        signatureSha256 = signatureSha256List(),
        debuggable = appInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0,
        testOnly = appInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_TEST_ONLY) != 0
    )
}

private fun PackageInfo.signatureSha256List(): List<String> {
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val signingInfo = signingInfo ?: return emptyList()
        if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
    } else {
        @Suppress("DEPRECATION")
        signatures
    } ?: return emptyList()
    return signatures
        .map { signature -> signature.toByteArray().sha256Hex() }
        .distinct()
}

private fun ByteArray.sha256Hex(): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
