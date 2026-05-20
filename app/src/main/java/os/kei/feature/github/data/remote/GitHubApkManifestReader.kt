package os.kei.feature.github.data.remote

import os.kei.feature.github.data.apk.AndroidBinaryXmlPackageNameParser
import os.kei.feature.github.data.apk.RemoteZipEntryReader
import os.kei.feature.github.data.apk.RemoteZipSelectedEntries
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubApkSignatureInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal class GitHubApkManifestReader(
    private val zipEntryReader: RemoteZipEntryReader = RemoteZipEntryReader()
) {
    suspend fun inspect(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkManifestInfo> = runCatching {
        val inspectPayload = readInspectPayload(asset, lookupConfig).getOrThrow()
        inspectPayload.toManifestInfo(asset.name).getOrThrow()
    }

    suspend fun readPackageName(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<String> {
        return readAndroidManifestBytes(asset, lookupConfig).mapCatching { manifestBytes ->
            parsePackageName(manifestBytes).getOrThrow()
        }
    }

    suspend fun readNestedApkPackageName(
        asset: GitHubReleaseAssetFile,
        nestedApkEntryName: String,
        lookupConfig: GitHubLookupConfig
    ): Result<String> {
        return readWithFallback(asset, lookupConfig) { url, token ->
            zipEntryReader.readNestedStoredZipEntry(
                url = url,
                outerEntryName = nestedApkEntryName,
                innerEntryName = ANDROID_MANIFEST_ENTRY,
                apiToken = token
            )
        }.mapCatching { payload ->
            parsePackageName(payload.value).getOrThrow()
        }
    }

    suspend fun readSelectedNestedApkPackageName(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        selectNestedApkEntryNames: (List<String>) -> List<String>
    ): Result<String> {
        return readWithFallback(asset, lookupConfig) { url, token ->
            zipEntryReader.readSelectedNestedStoredZipEntry(
                url = url,
                innerEntryName = ANDROID_MANIFEST_ENTRY,
                apiToken = token,
                selectOuterEntryNames = selectNestedApkEntryNames
            )
        }.mapCatching { payload ->
            parsePackageName(payload.value).getOrThrow()
        }
    }

    fun parsePackageName(manifestBytes: ByteArray): Result<String> {
        return AndroidBinaryXmlPackageNameParser.parsePackageName(manifestBytes)
    }

    suspend fun readAndroidManifestBytes(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<ByteArray> {
        return readAndroidManifestPayload(asset, lookupConfig).map { it.value }
    }

    suspend fun listEntryNames(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<List<String>> {
        return readWithFallback(asset, lookupConfig) { url, token ->
            zipEntryReader.listEntryNames(url = url, apiToken = token)
        }.map { it.value }
    }

    private suspend fun readAndroidManifestPayload(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<ManifestReadPayload<ByteArray>> {
        return readWithFallback(asset, lookupConfig) { url, token ->
            zipEntryReader.readEntry(
                url = url,
                entryName = ANDROID_MANIFEST_ENTRY,
                apiToken = token
            )
        }
    }

    private suspend fun readInspectPayload(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<ManifestReadPayload<RemoteZipSelectedEntries>> {
        if (asset.isGitHubActionsApkArtifactArchive()) {
            readNestedInspectPayload(asset, lookupConfig).getOrNull()?.let { nestedPayload ->
                return Result.success(nestedPayload)
            }
        }
        return readWithFallback(asset, lookupConfig) { url, token ->
            zipEntryReader.readSelectedEntries(
                url = url,
                apiToken = token,
                selectEntryNames = ::selectInspectEntryNames
            )
        }
    }

    private suspend fun readNestedInspectPayload(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<ManifestReadPayload<RemoteZipSelectedEntries>> {
        return readWithFallback(asset, lookupConfig) { url, token ->
            zipEntryReader.readSelectedNestedStoredZipEntries(
                url = url,
                apiToken = token,
                selectOuterEntryNames = ::selectNestedApkEntryNames,
                selectInnerEntryNames = ::selectInspectEntryNames
            )
        }
    }

    private fun selectInspectEntryNames(entryNames: List<String>): List<String> {
        return buildList {
            add(ANDROID_MANIFEST_ENTRY)
            entryNames.firstSignatureEntry()?.let(::add)
        }
    }

    private fun parseSignatureInfo(
        signatureEntry: String,
        certBytes: ByteArray
    ): Result<GitHubApkSignatureInfo?> = runCatching {
        val certificates = CertificateFactory.getInstance("X.509")
            .generateCertificates(ByteArrayInputStream(certBytes))
        val certificate = certificates.firstOrNull() as? X509Certificate
            ?: return@runCatching null
        GitHubApkSignatureInfo(
            entryName = signatureEntry,
            subject = certificate.subjectX500Principal.name,
            issuer = certificate.issuerX500Principal.name,
            serialNumber = certificate.serialNumber.toString(16),
            algorithm = certificate.sigAlgName,
            notBeforeMillis = certificate.notBefore.time,
            notAfterMillis = certificate.notAfter.time,
            sha256 = certBytes.sha256Hex()
        )
    }

    private fun ManifestReadPayload<RemoteZipSelectedEntries>.toManifestInfo(
        assetName: String
    ): Result<GitHubApkManifestInfo> = runCatching {
        val entries = value
        val manifest = entries.entries[ANDROID_MANIFEST_ENTRY]
            ?: error("$ANDROID_MANIFEST_ENTRY was not found in APK")
        val signatureEntry = entries.entryNames.firstSignatureEntry()
        val signatureInfo = signatureEntry?.let { entryName ->
            entries.entries[entryName]?.let { certBytes ->
                parseSignatureInfo(entryName, certBytes).getOrNull()
            }
        }
        AndroidBinaryXmlPackageNameParser.parseManifestInfo(manifest).getOrThrow()
            .copy(
                assetName = assetName,
                fetchSource = source,
                nativeAbis = entries.entryNames.extractNativeAbis(),
                signatureInfo = signatureInfo
            )
    }

    private suspend fun <T> readWithFallback(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        read: (String, String) -> Result<T>
    ): Result<ManifestReadPayload<T>> {
        val targets = resolveReadTargets(asset, lookupConfig)
        var firstFailure: Result<T>? = null
        targets.forEach { target ->
            val result = read(target.url, target.token)
            if (result.isSuccess) {
                return result.map { value ->
                    ManifestReadPayload(value = value, source = target.source)
                }
            }
            if (firstFailure == null) firstFailure = result
        }
        return Result.failure(
            firstFailure?.exceptionOrNull()
                ?: IllegalStateException("APK manifest read target missing")
        )
    }

    private suspend fun resolveReadTargets(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): List<ApkManifestReadTarget> {
        val token = lookupConfig.apiToken.trim()
        val canUseApiAsset =
            lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken &&
                    token.isNotBlank() &&
                    asset.apiAssetUrl.isNotBlank()
        val apiTarget = if (canUseApiAsset) {
            GitHubReleaseAssetRepository.resolvePreferredDownloadUrl(
                asset = asset,
                useApiAssetUrl = true,
                apiToken = token
            ).getOrNull()?.let { url ->
                ApkManifestReadTarget(
                    url = url,
                    token = token,
                    source = GitHubReleaseAssetFetchSources.API
                )
            }
        } else {
            null
        }
        val htmlTarget = ApkManifestReadTarget(
            url = asset.downloadUrl,
            token = lookupConfig.apiToken,
            source = GitHubReleaseAssetFetchSources.HTML
        )
        return buildList {
            if (lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken) {
                apiTarget?.let(::add)
                add(htmlTarget)
            } else {
                add(htmlTarget)
                apiTarget?.let(::add)
            }
        }.distinctBy { it.url }
    }

    private fun List<String>.extractNativeAbis(): List<String> {
        return asSequence()
            .mapNotNull { entry ->
                val parts = entry.split('/')
                if (parts.size >= 3 && parts[0] == "lib" && parts.last().endsWith(".so")) {
                    parts[1]
                } else {
                    null
                }
            }
            .distinct()
            .sorted()
            .toList()
    }

    private fun List<String>.firstSignatureEntry(): String? {
        return firstOrNull { entry ->
            entry.startsWith("META-INF/", ignoreCase = true) &&
                    (entry.endsWith(".RSA", ignoreCase = true) ||
                            entry.endsWith(".DSA", ignoreCase = true) ||
                            entry.endsWith(".EC", ignoreCase = true))
        }
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

    private fun selectNestedApkEntryNames(entryNames: List<String>): List<String> {
        return entryNames
            .asSequence()
            .filter { it.endsWith(".apk", ignoreCase = true) }
            .sortedWith(
                compareBy<String> { nestedApkEntryScore(it) }
                    .thenBy { it.length }
                    .thenBy { it.lowercase() }
            )
            .take(MAX_NESTED_APK_SCAN_CANDIDATES)
            .toList()
    }

    private data class ManifestReadPayload<T>(
        val value: T,
        val source: String
    )

    private data class ApkManifestReadTarget(
        val url: String,
        val token: String,
        val source: String
    )

    companion object {
        private const val ANDROID_MANIFEST_ENTRY = "AndroidManifest.xml"
        private const val MAX_NESTED_APK_SCAN_CANDIDATES = 4
    }
}

private fun ByteArray.sha256Hex(): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
