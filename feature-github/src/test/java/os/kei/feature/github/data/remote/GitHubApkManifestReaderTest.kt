package os.kei.feature.github.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.apk.RemoteZipEntryReader
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.rangeDispatcher
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.zipWithEntries
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.zipWithStoredEntry
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubApkManifestReaderTest {
    @Test
    fun `hma release zip exposes nested manager manifest`() = runBlocking {
        val managerApk = apkWithManifestAndNativeLib(
            manifestBytes = BinaryManifestFixture.build(
                packageName = "org.frknkrc44.hma_oss",
                versionName = "oss-164",
                versionCode = 7_304_647L,
            ),
        )
        val releaseZip = zipWithStoredEntry(entryName = "manager.apk", bytes = managerApk)
        MockWebServer().use { server ->
            server.dispatcher = rangeDispatcher(releaseZip)
            val reader = GitHubApkManifestReader(
                zipEntryReader = RemoteZipEntryReader(client = OkHttpClient()),
            )

            val info = reader.inspect(
                asset = GitHubReleaseAssetFile(
                    name = "HMA-OSS-ZYGISK-oss-164-release.zip",
                    downloadUrl = server.url("/release.zip").toString(),
                    sizeBytes = releaseZip.size.toLong(),
                    downloadCount = 1,
                ),
                lookupConfig = GitHubLookupConfig(),
            ).getOrThrow()

            assertEquals("org.frknkrc44.hma_oss", info.packageName)
            assertEquals("oss-164", info.versionName)
            assertEquals("7304647", info.versionCode)
            assertTrue(server.requestCount <= 6)
        }
    }

    @Test
    fun `inspect reuses one zip directory for manifest and entry metadata`() = runBlocking {
        val apkBytes = apkWithManifestAndNativeLib(
            manifestBytes = BinaryManifestFixture.build(
                packageName = "os.kei.inspect",
                versionName = "2.4.0",
                versionCode = 20400L
            )
        )
        MockWebServer().use { server ->
            server.dispatcher = rangeDispatcher(apkBytes)
            val reader = GitHubApkManifestReader(
                zipEntryReader = RemoteZipEntryReader(client = OkHttpClient())
            )

            val info = reader.inspect(
                asset = GitHubReleaseAssetFile(
                    name = "inspect.apk",
                    downloadUrl = server.url("/download/inspect.apk").toString(),
                    sizeBytes = apkBytes.size.toLong(),
                    downloadCount = 1
                ),
                lookupConfig = GitHubLookupConfig()
            ).getOrThrow()

            assertEquals("os.kei.inspect", info.packageName)
            assertEquals("2.4.0", info.versionName)
            assertEquals("20400", info.versionCode)
            assertEquals(listOf("arm64-v8a"), info.nativeAbis)
            assertTrue(server.requestCount <= 3)
        }
    }

    @Test
    fun `repository reuses in-flight manifest inspect for identical asset`() = runBlocking {
        val apkBytes = apkWithManifestAndNativeLib(
            manifestBytes = BinaryManifestFixture.build(
                packageName = "os.kei.inspect.dedupe",
                versionName = "3.1.0",
                versionCode = 30100L
            )
        )
        MockWebServer().use { server ->
            val baseDispatcher = rangeDispatcher(apkBytes)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    Thread.sleep(50L)
                    return baseDispatcher.dispatch(request)
                }
            }
            val repository = GitHubApkInfoRepository(
                manifestReader = GitHubApkManifestReader(
                    zipEntryReader = RemoteZipEntryReader(client = OkHttpClient())
                )
            )
            val asset = GitHubReleaseAssetFile(
                name = "inspect-dedupe.apk",
                downloadUrl = server.url("/download/inspect-dedupe.apk").toString(),
                sizeBytes = apkBytes.size.toLong(),
                downloadCount = 1
            )
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            val futures = List(2) {
                executor.submit<GitHubApkManifestInfo> {
                    assertTrue(start.await(2, TimeUnit.SECONDS))
                    runBlocking {
                        repository.inspect(asset = asset, lookupConfig = GitHubLookupConfig())
                            .getOrThrow()
                    }
                }
            }
            start.countDown()

            val results = futures.map { future -> future.get(5, TimeUnit.SECONDS) }
            executor.shutdownNow()

            assertEquals(
                listOf("os.kei.inspect.dedupe", "os.kei.inspect.dedupe"),
                results.map { it.packageName })
            assertEquals(listOf("3.1.0", "3.1.0"), results.map { it.versionName })
            assertTrue(server.requestCount <= 4)
        }
    }

    @Test
    fun `repository force refresh bypasses completed manifest cache for fixed direct URL`() = runBlocking {
        val firstApkBytes = apkWithManifestAndNativeLib(
            manifestBytes = BinaryManifestFixture.build(
                packageName = "org.telegram.messenger",
                versionName = "12.0.0",
                versionCode = 120000L
            )
        )
        val secondApkBytes = apkWithManifestAndNativeLib(
            manifestBytes = BinaryManifestFixture.build(
                packageName = "org.telegram.messenger",
                versionName = "12.1.0",
                versionCode = 121000L
            )
        )
        MockWebServer().use { server ->
            val probeCount = AtomicInteger(0)
            var activeApkBytes = firstApkBytes
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.getHeader("Range").orEmpty() == "bytes=0-0") {
                        activeApkBytes = if (probeCount.incrementAndGet() == 1) {
                            firstApkBytes
                        } else {
                            secondApkBytes
                        }
                    }
                    return rangeDispatcher(activeApkBytes).dispatch(request)
                }
            }
            val repository = GitHubApkInfoRepository(
                manifestReader = GitHubApkManifestReader(
                    zipEntryReader = RemoteZipEntryReader(client = OkHttpClient())
                )
            )
            val asset = GitHubReleaseAssetFile(
                name = "Telegram.apk",
                downloadUrl = server.url("/dl/android/apk").toString(),
                sizeBytes = 0L,
                downloadCount = 0
            )

            val first = repository.inspect(asset = asset, lookupConfig = GitHubLookupConfig())
                .getOrThrow()
            val cached = repository.inspect(asset = asset, lookupConfig = GitHubLookupConfig())
                .getOrThrow()
            val refreshed = repository.inspect(
                asset = asset,
                lookupConfig = GitHubLookupConfig(),
                forceRefresh = true
            ).getOrThrow()

            assertEquals("12.0.0", first.versionName)
            assertEquals("12.0.0", cached.versionName)
            assertEquals("12.1.0", refreshed.versionName)
            assertEquals(2, probeCount.get())
        }
    }

    @Test
    fun `repository reuses persistent manifest cache across repository instances`() = runBlocking {
        val apkBytes = apkWithManifestAndNativeLib(
            manifestBytes = BinaryManifestFixture.build(
                packageName = "os.kei.inspect.persisted",
                versionName = "5.0.0",
                versionCode = 50000L
            )
        )
        MockWebServer().use { server ->
            server.dispatcher = rangeDispatcher(apkBytes)
            val manifestCache = FakeManifestInfoCache()
            val asset = GitHubReleaseAssetFile(
                name = "inspect-persisted.apk",
                downloadUrl = server.url("/download/inspect-persisted.apk").toString(),
                sizeBytes = apkBytes.size.toLong(),
                downloadCount = 1
            )
            val firstRepository = GitHubApkInfoRepository(
                manifestReader = GitHubApkManifestReader(
                    zipEntryReader = RemoteZipEntryReader(client = OkHttpClient())
                ),
                manifestCache = manifestCache
            )

            val first = firstRepository.inspect(asset = asset, lookupConfig = GitHubLookupConfig())
                .getOrThrow()
            GitHubApkInfoRepository.clearMemoryCachesForTest()
            val requestsAfterFirstRead = server.requestCount
            val secondRepository = GitHubApkInfoRepository(
                manifestReader = GitHubApkManifestReader(
                    zipEntryReader = RemoteZipEntryReader(client = OkHttpClient())
                ),
                manifestCache = manifestCache
            )
            val second = secondRepository.inspect(asset = asset, lookupConfig = GitHubLookupConfig())
                .getOrThrow()

            assertEquals("os.kei.inspect.persisted", first.packageName)
            assertEquals("5.0.0", second.versionName)
            assertEquals(requestsAfterFirstRead, server.requestCount)
        }
    }

    private fun apkWithManifestAndNativeLib(manifestBytes: ByteArray): ByteArray {
        return zipWithEntries(
            "AndroidManifest.xml" to manifestBytes,
            "lib/arm64-v8a/libfixture.so" to byteArrayOf(1, 2, 3, 4)
        )
    }

    private class FakeManifestInfoCache : GitHubApkManifestInfoCache {
        private val values = linkedMapOf<String, GitHubApkManifestInfo>()

        override fun load(
            cacheKey: String,
            refreshIntervalHours: Int
        ): GitHubApkManifestInfo? = values[cacheKey]

        override fun save(
            cacheKey: String,
            info: GitHubApkManifestInfo
        ) {
            values[cacheKey] = info
        }

        override fun remove(cacheKey: String) {
            values.remove(cacheKey)
        }
    }
}
