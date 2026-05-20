package os.kei.feature.github.install

import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

const val GITHUB_APK_STREAM_BUFFER_SIZE = 1024 * 1024

private const val ANDROID_MANIFEST_ENTRY = "AndroidManifest.xml"

fun selectGitHubInstallApkEntry(zipFile: ZipFile): ZipEntry? =
    zipFile
        .entries()
        .asSequence()
        .filter { entry -> !entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true) }
        .sortedWith(
            compareBy<ZipEntry> { entry -> nestedApkEntryScore(entry.name) }
                .thenBy { entry -> entry.name.length }
                .thenBy { entry -> entry.name.lowercase() },
        ).firstOrNull()

fun ZipFile.isGitHubDirectApkArchive(): Boolean = getEntry(ANDROID_MANIFEST_ENTRY) != null

fun String.toGitHubApkSessionName(): String {
    val normalized = trim().substringAfterLast('/').ifBlank { "base.apk" }
    return if (normalized.endsWith(".apk", ignoreCase = true)) normalized else "$normalized.apk"
}

fun createGitHubTempApkFile(
    context: Context,
    assetName: String,
): File = createGitHubTempInstallFile(context, assetName, ".apk")

fun createGitHubTempInstallFile(
    context: Context,
    assetName: String,
    suffix: String,
): File {
    val directory = File(context.cacheDir, "github-managed-install").apply { mkdirs() }
    val safeName =
        assetName
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
            .ifBlank { "download$suffix" }
    return File.createTempFile("kei-", "-$safeName", directory)
}

private fun nestedApkEntryScore(entryName: String): Int {
    val name = entryName.substringAfterLast('/').lowercase()
    return when {
        "universal" in name && "release" in name -> 0
        "universal" in name -> 1
        "release" in name -> 2
        "debug" in name -> 4
        else -> 3
    }
}
