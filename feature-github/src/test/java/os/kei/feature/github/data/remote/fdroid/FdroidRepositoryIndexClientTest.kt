package os.kei.feature.github.data.remote.fdroid

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import os.kei.feature.github.data.local.fdroid.FdroidRepoCacheRecord
import os.kei.feature.github.data.local.fdroid.FdroidRepoCacheRequestKey
import os.kei.feature.github.data.local.fdroid.FdroidRepositoryIndexCacheStore
import os.kei.feature.github.model.FdroidIndexFormat
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FdroidRepositoryIndexClientTest {
    @Test
    fun `searchIndexV2 reads candidates through repository index stream`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(FdroidIndexV2Fixtures.index)
            )

            val snapshot = FdroidRepositoryIndexClient()
                .searchIndexV2(
                    repoBaseUrl = server.url("/fdroid/repo").toString(),
                    query = "PixEz",
                    packageName = "",
                    limit = 12
                )
                .getOrThrow()

            assertEquals("/fdroid/repo/index-v2.json", server.takeRequest().path)
            assertEquals(listOf("com.perol.pixez"), snapshot.packages.keys.toList())
            assertEquals("PixEz", snapshot.packageSnapshot("com.perol.pixez")?.appName)
            assertNull(snapshot.packageSnapshot("dev.imranr.obtainium"))
        }
    }

    @Test
    fun `fetchIndexV2Packages materializes only requested packages`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(FdroidIndexV2Fixtures.index)
            )

            val snapshot = FdroidRepositoryIndexClient()
                .fetchIndexV2Packages(
                    repoBaseUrl = server.url("/fdroid/repo").toString(),
                    packageNames = setOf("dev.imranr.obtainium")
                )
                .getOrThrow()

            assertEquals("/fdroid/repo/index-v2.json", server.takeRequest().path)
            assertEquals(listOf("dev.imranr.obtainium"), snapshot.packages.keys.toList())
            assertEquals(3, snapshot.packageCount)
        }
    }

    @Test
    fun `fetchIndexV2Packages uses fresh cached package snapshot without network`() = runBlocking {
        MockWebServer().use { server ->
            val repoUrl = server.url("/fdroid/repo").toString().trimEnd('/')
            val cacheStore = InMemoryFdroidIndexCacheStore()
            cacheStore.save(
                FdroidRepoCacheRequestKey.packages(
                    repoUrl = repoUrl,
                    packageNames = setOf("dev.imranr.obtainium")
                ),
                FdroidRepoCacheRecord(
                    repoUrl = repoUrl,
                    fetchedAtMillis = 2_000L,
                    etag = "cached-etag",
                    lastModified = "Tue, 30 Jun 2026 12:00:00 GMT",
                    snapshot = cachedRepositorySnapshot(repoUrl)
                )
            )

            val snapshot = FdroidRepositoryIndexClient(
                cacheStore = cacheStore,
                clock = { 2_500L }
            )
                .fetchIndexV2Packages(
                    repoBaseUrl = repoUrl,
                    packageNames = setOf("dev.imranr.obtainium")
                )
                .getOrThrow()

            assertEquals(0, server.requestCount)
            assertEquals(listOf("dev.imranr.obtainium"), snapshot.packages.keys.toList())
            assertEquals("2.0", snapshot.packageSnapshot("dev.imranr.obtainium")?.versions?.first()?.versionName)
        }
    }

    @Test
    fun `fetchIndexV2Packages revalidates cache on force refresh and reuses snapshot on 304`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(304))
            val repoUrl = server.url("/fdroid/repo").toString().trimEnd('/')
            val cacheStore = InMemoryFdroidIndexCacheStore()
            cacheStore.save(
                FdroidRepoCacheRequestKey.packages(
                    repoUrl = repoUrl,
                    packageNames = setOf("dev.imranr.obtainium")
                ),
                FdroidRepoCacheRecord(
                    repoUrl = repoUrl,
                    fetchedAtMillis = 1_000L,
                    etag = "\"index-etag\"",
                    lastModified = "Tue, 30 Jun 2026 12:00:00 GMT",
                    snapshot = cachedRepositorySnapshot(repoUrl)
                )
            )

            val snapshot = FdroidRepositoryIndexClient(
                cacheStore = cacheStore,
                clock = { 9_000L }
            )
                .fetchIndexV2Packages(
                    repoBaseUrl = repoUrl,
                    packageNames = setOf("dev.imranr.obtainium"),
                    forceRefresh = true
                )
                .getOrThrow()

            val request = server.takeRequest()
            assertEquals("/fdroid/repo/index-v2.json", request.path)
            assertEquals("\"index-etag\"", request.getHeader("If-None-Match"))
            assertEquals("Tue, 30 Jun 2026 12:00:00 GMT", request.getHeader("If-Modified-Since"))
            assertEquals("2.0", snapshot.packageSnapshot("dev.imranr.obtainium")?.versions?.first()?.versionName)
            assertEquals(9_000L, cacheStore.load(
                FdroidRepoCacheRequestKey.packages(
                    repoUrl = repoUrl,
                    packageNames = setOf("dev.imranr.obtainium")
                )
            )?.fetchedAtMillis)
        }
    }

    @Test
    fun `searchIndexV2 reuses fresh cached search snapshot`() = runBlocking {
        MockWebServer().use { server ->
            val repoUrl = server.url("/fdroid/repo").toString().trimEnd('/')
            val cacheStore = InMemoryFdroidIndexCacheStore()
            cacheStore.save(
                FdroidRepoCacheRequestKey.search(
                    repoUrl = repoUrl,
                    query = "PixEz",
                    packageName = "",
                    limit = 12
                ),
                FdroidRepoCacheRecord(
                    repoUrl = repoUrl,
                    fetchedAtMillis = 2_000L,
                    etag = "",
                    lastModified = "",
                    snapshot = FdroidRepositorySnapshot(
                        repoUrl = repoUrl,
                        format = FdroidIndexFormat.V2,
                        repoName = "IzzyOnDroid",
                        repoDescription = "",
                        timestampMillis = null,
                        mirrors = emptyList(),
                        packages = mapOf(
                            "com.perol.pixez" to cachedPackage(
                                repoUrl = repoUrl,
                                packageName = "com.perol.pixez",
                                appName = "PixEz",
                                versionName = "0.9.104 wsv",
                                versionCode = 10010040
                            )
                        ),
                        totalPackageCount = 3
                    )
                )
            )

            val snapshot = FdroidRepositoryIndexClient(
                cacheStore = cacheStore,
                clock = { 2_500L }
            )
                .searchIndexV2(
                    repoBaseUrl = repoUrl,
                    query = "PixEz",
                    packageName = "",
                    limit = 12
                )
                .getOrThrow()

            assertEquals(0, server.requestCount)
            assertEquals(listOf("com.perol.pixez"), snapshot.packages.keys.toList())
            assertEquals("PixEz", snapshot.packageSnapshot("com.perol.pixez")?.appName)
        }
    }

    @Test
    fun `searchIndexV2 returns failure for http errors`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))

            val result = FdroidRepositoryIndexClient()
                .searchIndexV2(
                    repoBaseUrl = server.url("/fdroid/repo").toString(),
                    query = "PixEz",
                    packageName = "",
                    limit = 12
                )

            assertTrue(result.isFailure)
        }
    }

    private class InMemoryFdroidIndexCacheStore : FdroidRepositoryIndexCacheStore {
        private val records = linkedMapOf<FdroidRepoCacheRequestKey, FdroidRepoCacheRecord>()

        override fun load(key: FdroidRepoCacheRequestKey): FdroidRepoCacheRecord? = records[key]

        override fun save(key: FdroidRepoCacheRequestKey, record: FdroidRepoCacheRecord) {
            records[key] = record
        }

        override fun clear(repoUrl: String?) {
            val normalized = repoUrl?.trim()?.trimEnd('/')
            records.keys
                .filter { key -> normalized == null || key.repoUrl == normalized }
                .toList()
                .forEach(records::remove)
        }
    }

    private fun cachedRepositorySnapshot(repoUrl: String): FdroidRepositorySnapshot {
        return FdroidRepositorySnapshot(
            repoUrl = repoUrl,
            format = FdroidIndexFormat.V2,
            repoName = "IzzyOnDroid",
            repoDescription = "",
            timestampMillis = null,
            mirrors = emptyList(),
            packages = mapOf(
                "dev.imranr.obtainium" to cachedPackage(
                    repoUrl = repoUrl,
                    packageName = "dev.imranr.obtainium",
                    appName = "Obtainium",
                    versionName = "2.0",
                    versionCode = 200
                )
            ),
            totalPackageCount = 3
        )
    }

    private fun cachedPackage(
        repoUrl: String,
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: Long
    ): FdroidPackageSnapshot {
        return FdroidPackageSnapshot(
            repoUrl = repoUrl,
            packageName = packageName,
            suggestedVersionCode = versionCode,
            appName = appName,
            versions = listOf(
                FdroidVersionSnapshot(
                    versionName = versionName,
                    versionCode = versionCode,
                    apkName = "${packageName}_$versionCode.apk",
                    apkPath = "/repo/${packageName}_$versionCode.apk",
                    apkSha256 = "sha256-$versionCode",
                    apkSizeBytes = versionCode,
                    addedAtMillis = null,
                    minSdk = 23,
                    targetSdk = 35,
                    nativeAbis = emptyList(),
                    signerSha256 = emptyList(),
                    releaseChannels = emptyList(),
                    whatsNew = "",
                    antiFeatures = emptyList()
                )
            )
        )
    }
}
