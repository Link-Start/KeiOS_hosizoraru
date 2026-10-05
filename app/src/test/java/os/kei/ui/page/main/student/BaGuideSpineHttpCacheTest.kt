package os.kei.ui.page.main.student

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaGuideSpineHttpCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val baseClient = OkHttpClient()

    @Test fun `large CDN Age still permits bounded local reuse across cache reopen`() {
        MockWebServer().use { server ->
            server.enqueue(asset("texture", "max-age=0").setHeader("Age", "1111015"))
            val directory = temporary.newFolder()
            val request = Request.Builder().url(server.url("/texture.png")).build()
            BaGuideSpineHttpCache(directory, baseClient = baseClient).use { cache ->
                assertEquals("texture", cache.open(request, BA_GUIDE_SPINE_ASSET_REUSE_MS).use { it.body.string() })
                assertEquals(1, cache.stats().entries)
            }
            BaGuideSpineHttpCache(directory, baseClient = baseClient).use { cache ->
                assertEquals("texture", cache.open(request, BA_GUIDE_SPINE_ASSET_REUSE_MS).use { it.body.string() })
                assertEquals(1L, cache.stats().hits)
                assertEquals(0L, cache.stats().downloadedBytes)
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test fun `expiry and manual retry use conditional GET instead of discarding bytes`() {
        MockWebServer().use { server ->
            server.enqueue(asset("skeleton", "max-age=86400"))
            server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag", "\"revision1\""))
            server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag", "\"revision1\""))
            var now = System.currentTimeMillis()
            BaGuideSpineHttpCache(temporary.newFolder(), baseClient = baseClient, clock = { now }).use { cache ->
                val request = Request.Builder().url(server.url("/skeleton.skel")).build()
                assertEquals("skeleton", cache.open(request, 1_000).use { it.body.string() })
                now += 2_000
                assertEquals("skeleton", cache.open(request, 1_000).use { it.body.string() })
                assertEquals("skeleton", cache.open(request, 1_000, revalidate = true).use { it.body.string() })
                server.takeRequest()
                repeat(2) { assertEquals("\"revision1\"", server.takeRequest(2, TimeUnit.SECONDS)?.getHeader("If-None-Match")) }
                assertEquals(8L, cache.stats().downloadedBytes)
                assertEquals(0, cache.activeLockCount())
            }
        }
    }

    @Test fun `URL revision and selective removal do not reuse different asset content`() {
        MockWebServer().use { server ->
            for (body in listOf("first", "second", "fresh")) server.enqueue(asset(body))
            BaGuideSpineHttpCache(temporary.newFolder(), baseClient = baseClient).use { cache ->
                val first = Request.Builder().url(server.url("/a.png?v=1")).build()
                val second = Request.Builder().url(server.url("/a.png?v=2")).build()
                assertEquals("first", cache.open(first, 60_000).use { it.body.string() })
                assertEquals("second", cache.open(second, 60_000).use { it.body.string() })
                cache.removeResources(setOf(first.url.toString()))
                assertEquals("second", cache.open(second, 60_000).use { it.body.string() })
                assertEquals("fresh", cache.open(first, 60_000).use { it.body.string() })
                assertEquals(3, server.requestCount)
            }
        }
    }

    @Test fun `server changes at the same URL replace the old cached resource`() {
        MockWebServer().use { server ->
            server.enqueue(asset("before"))
            server.enqueue(asset("after").setHeader("ETag", "\"revision2\""))
            BaGuideSpineHttpCache(temporary.newFolder(), baseClient = baseClient).use { cache ->
                val request = Request.Builder().url(server.url("/a.skel")).build()
                assertEquals("before", cache.open(request, 60_000).use { it.body.string() })
                assertEquals("after", cache.open(request, 60_000, revalidate = true).use { it.body.string() })
                assertEquals("after", cache.open(request, 60_000).use { it.body.string() })
                server.takeRequest()
                assertEquals("\"revision1\"", server.takeRequest().getHeader("If-None-Match"))
                assertEquals(2, server.requestCount)
                assertEquals(11L, cache.stats().downloadedBytes)
            }
        }
    }

    @Test fun `no-store and error responses are not replayed as cached assets`() {
        MockWebServer().use { server ->
            server.enqueue(asset("private", "no-store"))
            server.enqueue(asset("private", "no-store"))
            server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))
            server.enqueue(asset("recovered"))
            BaGuideSpineHttpCache(temporary.newFolder(), baseClient = baseClient).use { cache ->
                val request = Request.Builder().url(server.url("/a.png")).build()
                repeat(2) { assertEquals("private", cache.open(request, 60_000).use { it.body.string() }) }
                assertEquals(0, cache.stats().entries)
                cache.open(request, 60_000).use { assertEquals(404, it.code); it.body.string() }
                assertEquals("recovered", cache.open(request, 60_000).use { it.body.string() })
                assertEquals(4, server.requestCount)
            }
        }
    }

    @Test fun `no-cache and must-revalidate directives still require server validation`() {
        for (directive in listOf("no-cache", "max-age=0, must-revalidate")) {
            MockWebServer().use { server ->
                server.enqueue(asset("data", directive))
                server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag", "\"revision1\""))
                BaGuideSpineHttpCache(temporary.newFolder(), baseClient = baseClient).use { cache ->
                    val request = Request.Builder().url(server.url("/a.atlas")).build()
                    repeat(2) { assertEquals("data", cache.open(request, 60_000).use { it.body.string() }) }
                    assertEquals(2, server.requestCount)
                    assertEquals(4L, cache.stats().downloadedBytes)
                }
            }
        }
    }

    @Test fun `incomplete download is discarded and retry can fetch a complete file`() {
        MockWebServer().use { server ->
            server.enqueue(asset("x".repeat(100_000)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            server.enqueue(asset("complete"))
            BaGuideSpineHttpCache(temporary.newFolder(), baseClient = baseClient).use { cache ->
                val request = Request.Builder().url(server.url("/a.skel")).build()
                assertFailsWith<IOException> { cache.open(request, 60_000).use { it.body.bytes() } }
                assertEquals(0, cache.activeLockCount())
                assertEquals(0, cache.stats().entries)
                assertEquals("complete", cache.open(request, 60_000).use { it.body.string() })
                assertEquals(2, server.requestCount)
            }
        }
    }

    @Test fun `concurrent requests for the same resource share one download`() {
        MockWebServer().use { server ->
            server.enqueue(asset("pixels"))
            BaGuideSpineHttpCache(temporary.newFolder(), baseClient = baseClient).use { cache ->
                val request = Request.Builder().url(server.url("/a.png")).build()
                val first = cache.open(request, 60_000)
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val started = CountDownLatch(1)
                    val second = executor.submit<String> {
                        started.countDown()
                        cache.open(request, 60_000).use { it.body.string() }
                    }
                    assertTrue(started.await(2, TimeUnit.SECONDS))
                    first.use { assertEquals("pixels", it.body.string()) }
                    assertEquals("pixels", second.get(3, TimeUnit.SECONDS))
                    assertEquals(1, server.requestCount)
                    assertEquals(0, cache.activeLockCount())
                } finally { first.close(); executor.shutdownNow() }
            }
        }
    }

    @Test fun `cancelled duplicate does not retain its lock while waiting for another body`() {
        MockWebServer().use { server ->
            server.enqueue(asset("pixels"))
            BaGuideSpineHttpCache(temporary.newFolder(), baseClient = baseClient).use { cache ->
                val request = Request.Builder().url(server.url("/a.png")).build()
                cache.open(request, 60_000).use { first ->
                    val executor = Executors.newSingleThreadExecutor()
                    try {
                        val cancelled = AtomicBoolean()
                        val started = CountDownLatch(1)
                        val waiting = executor.submit {
                            started.countDown()
                            assertFailsWith<IOException> { cache.open(request, 60_000, cancelled = cancelled::get) }
                        }
                        assertTrue(started.await(2, TimeUnit.SECONDS)); cancelled.set(true)
                        waiting.get(3, TimeUnit.SECONDS)
                        first.body.string()
                    } finally { executor.shutdownNow() }
                }
                assertEquals(0, cache.activeLockCount())
            }
        }
    }

    @Test fun `LRU stays within capacity and retains recently read resources`() {
        MockWebServer().use { server ->
            repeat(4) { server.enqueue(asset("x".repeat(2_000))) }
            BaGuideSpineHttpCache(temporary.newFolder(), maxBytes = 6_000, baseClient = baseClient).use { cache ->
                fun load(path: String) = cache.open(Request.Builder().url(server.url(path)).build(), 60_000).use { it.body.bytes() }
                load("/a"); load("/b"); load("/a"); load("/c")
                val stats = cache.stats()
                assertTrue(stats.bytes <= 6_000)
                assertEquals(2, stats.entries)
                load("/b")
                assertEquals(4, server.requestCount)
                cache.clear()
                assertEquals(0, cache.stats().entries)
                assertEquals(0L, cache.stats().bytes)
            }
        }
    }

    @Test fun `gzip statistics count downloaded body bytes and not cached or decoded bytes`() {
        val encoded = java.io.ByteArrayOutputStream().also { output ->
            java.util.zip.GZIPOutputStream(output).use { it.write("animation".repeat(1_000).toByteArray()) }
        }.toByteArray()
        MockWebServer().use { server ->
            server.enqueue(asset("").setHeader("Content-Encoding", "gzip").setBody(Buffer().write(encoded)))
            BaGuideSpineHttpCache(temporary.newFolder(), baseClient = baseClient).use { cache ->
                val request = Request.Builder().url(server.url("/a.skel")).build()
                repeat(2) { assertEquals("animation".repeat(1_000), cache.open(request, 60_000).use { it.body.string() }) }
                assertEquals(encoded.size.toLong(), cache.stats().downloadedBytes)
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test fun `policy intercepts declared resources and versioned renderer scripts only`() {
        val asset = "https://cdnimg-v2.gamekee.com/student/a.skel?v=1"
        val policy = BaGuideSpineResourcePolicy(BaGuideWebMemoryLobby(
            "https://www.gamekee.com/ba/718266?tab=3", "https://cdnimg-v2.gamekee.com/student/a.atlas", skeletonUrl = asset,
        ))
        assertNotNull(policy.reuseMs(asset))
        assertNotNull(policy.reuseMs("https://cdnstatic.gamekee.com/wiki/spa/apps/web/client/dist/static/pixi.js?t=10001"))
        for (url in listOf(asset.replace("v=1", "v=2"), asset.replace("gamekee.com", "gamekee.com.evil.test"),
            "https://www.gamekee.com/ba/718266?tab=3", "https://cdnimg-v2.gamekee.com/other.mp4", "https://cdnstatic.gamekee.com/other.js")) {
            assertNull(policy.reuseMs(url))
        }
    }

    private fun asset(body: String, control: String = "max-age=0") = MockResponse()
        .setHeader("Content-Type", "application/octet-stream")
        .setHeader("Cache-Control", control)
        .setHeader("ETag", "\"revision1\"")
        .setBody(body)
}
