package os.kei.feature.github.data.remote

import os.kei.feature.github.data.apk.AndroidBinaryXmlPackageNameParser
import os.kei.feature.github.data.apk.RemoteZipEntryReader
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
    fun inspect(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkManifestInfo> = runCatching {
        val manifest = readAndroidManifestPayload(asset, lookupConfig).getOrThrow()
        val entryNames = listEntryNames(asset, lookupConfig).getOrDefault(emptyList())
        AndroidBinaryXmlPackageNameParser.parseManifestInfo(manifest.value).getOrThrow().copy(
            assetName = asset.name,
            fetchSource = manifest.source,
            nativeAbis = entryNames.extractNativeAbis(),
            signatureInfo = readSignatureInfo(asset, lookupConfig, entryNames).getOrNull()
        )
    }

    fun readPackageName(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<String> {
        return readAndroidManifestBytes(asset, lookupConfig).mapCatching { manifestBytes ->
            parsePackageName(manifestBytes).getOrThrow()
        }
    }

    fun readNestedApkPackageName(
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

    fun readSelectedNestedApkPackageName(
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

    fun readAndroidManifestBytes(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<ByteArray> {
        return readAndroidManifestPayload(asset, lookupConfig).map { it.value }
    }

    fun listEntryNames(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<List<String>> {
        return readWithFallback(asset, lookupConfig) { url, token ->
            zipEntryReader.listEntryNames(url = url, apiToken = token)
        }.map { it.value }
    }

    private fun readAndroidManifestPayload(
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

    private fun readSignatureInfo(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        entryNames: List<String>
    ): Result<GitHubApkSignatureInfo?> = runCatching {
        val signatureEntry = entryNames.firstOrNull { entry ->
            entry.startsWith("META-INF/", ignoreCase = true) &&
                    (entry.endsWith(".RSA", ignoreCase = true) ||
                            entry.endsWith(".DSA", ignoreCase = true) ||
                            entry.endsWith(".EC", ignoreCase = true))
        } ?: return@runCatching null
        val certBytes = readWithFallback(asset, lookupConfig) { url, token ->
            zipEntryReader.readEntry(
                url = url,
                entryName = signatureEntry,
                apiToken = token
            )
        }.getOrThrow().value
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

    private fun <T> readWithFallback(
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

    private fun resolveReadTargets(
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
    }
}

private fun ByteArray.sha256Hex(): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
