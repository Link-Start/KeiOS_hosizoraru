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

internal class GitHubApkInfoRepository(
    private val zipEntryReader: RemoteZipEntryReader = RemoteZipEntryReader()
) {
    fun inspect(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubApkManifestInfo> = runCatching {
        val manifestBytes = readAndroidManifestBytes(asset, lookupConfig).getOrThrow()
        val entryNames = listEntryNames(asset, lookupConfig).getOrDefault(emptyList())
        AndroidBinaryXmlPackageNameParser.parseManifestInfo(manifestBytes).getOrThrow().copy(
            assetName = asset.name,
            fetchSource = resolvePrimarySource(asset, lookupConfig),
            nativeAbis = entryNames.extractNativeAbis(),
            signatureInfo = readSignatureInfo(asset, lookupConfig, entryNames).getOrNull()
        )
    }

    private fun readAndroidManifestBytes(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<ByteArray> {
        return readWithApiFallback(asset, lookupConfig) { url, token ->
            zipEntryReader.readEntry(
                url = url,
                entryName = ANDROID_MANIFEST_ENTRY,
                apiToken = token
            )
        }
    }

    private fun listEntryNames(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<List<String>> {
        return readWithApiFallback(asset, lookupConfig) { url, token ->
            zipEntryReader.listEntryNames(url = url, apiToken = token)
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
        val certBytes = readWithApiFallback(asset, lookupConfig) { url, token ->
            zipEntryReader.readEntry(
                url = url,
                entryName = signatureEntry,
                apiToken = token
            )
        }.getOrThrow()
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

    private fun <T> readWithApiFallback(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig,
        read: (String, String) -> Result<T>
    ): Result<T> {
        val targets = resolveReadTargets(asset, lookupConfig)
        var firstFailure: Result<T>? = null
        targets.forEach { target ->
            val result = read(target.url, target.token)
            if (result.isSuccess) return result
            if (firstFailure == null) firstFailure = result
        }
        return firstFailure ?: Result.failure(IllegalStateException("APK info read target missing"))
    }

    private fun resolveReadTargets(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): List<ApkInfoReadTarget> {
        val token = lookupConfig.apiToken.trim()
        val apiTarget = if (
            lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken &&
            token.isNotBlank() &&
            asset.apiAssetUrl.isNotBlank()
        ) {
            GitHubReleaseAssetRepository.resolvePreferredDownloadUrl(
                asset = asset,
                useApiAssetUrl = true,
                apiToken = token
            ).getOrNull()?.let { ApkInfoReadTarget(url = it, token = token) }
        } else {
            null
        }
        return buildList {
            apiTarget?.let(::add)
            add(ApkInfoReadTarget(url = asset.downloadUrl, token = lookupConfig.apiToken))
        }.distinctBy { it.url }
    }

    private fun resolvePrimarySource(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): String {
        val canUseApiAsset =
            lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken &&
                    lookupConfig.apiToken.trim().isNotBlank() &&
                    asset.apiAssetUrl.isNotBlank()
        return if (canUseApiAsset) {
            GitHubReleaseAssetFetchSources.API
        } else {
            GitHubReleaseAssetFetchSources.HTML
        }
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

    companion object {
        private const val ANDROID_MANIFEST_ENTRY = "AndroidManifest.xml"
    }
}

private data class ApkInfoReadTarget(
    val url: String,
    val token: String
)

private fun ByteArray.sha256Hex(): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
