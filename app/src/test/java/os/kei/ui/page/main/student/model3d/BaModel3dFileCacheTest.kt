package os.kei.ui.page.main.student.model3d

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import kotlin.test.*

class BaModel3dFileCacheTest {
    @get:Rule val temp = TemporaryFolder()
    private val bytes = "glTFverified test model bytes".toByteArray()
    private fun asset(server: MockWebServer): BaModel3dAsset {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${bytes.size}\u0000".toByteArray()); digest.update(bytes)
        return BaModel3dAsset("Default", "test.glb", bytes.size.toLong(), digest.digest().joinToString("") { "%02x".format(it) }, server.url("/test.glb").toString())
    }
    @Test fun `a verified cache hit requires no further network request`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
            val cache = BaModel3dFileCache(temp.newFolder(), baseClient = OkHttpClient())
            val model = asset(server)
            repeat(2) { BaModel3dCacheSession().use { s -> cache.open(model, s).use { assertContentEquals(bytes, it.readBytes()) } } }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun `a wrong digest is not committed and a later retry can recover`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(bytes.size)))
            server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
            val folder = temp.newFolder(); val cache = BaModel3dFileCache(folder, baseClient = OkHttpClient()); val model = asset(server)
            BaModel3dCacheSession().use { assertFails { cache.open(model, it) } }
            assertTrue(folder.listFiles().orEmpty().isEmpty())
            BaModel3dCacheSession().use { s -> cache.open(model, s).use { assertContentEquals(bytes, it.readBytes()) } }
        }
    }
    @Test fun `same sized corruption causes validation and re download`() {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes))) }
            val folder = temp.newFolder(); val cache = BaModel3dFileCache(folder, baseClient = OkHttpClient()); val model = asset(server)
            BaModel3dCacheSession().use { s -> cache.open(model, s).close() }
            folder.listFiles()!!.single().writeBytes(ByteArray(bytes.size))
            BaModel3dCacheSession().use { s -> cache.open(model, s).use { assertContentEquals(bytes, it.readBytes()) } }
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun `a closed session never starts another download`() {
        MockWebServer().use { server ->
            val session = BaModel3dCacheSession(); session.close()
            val cache = BaModel3dFileCache(temp.newFolder(), baseClient = OkHttpClient())
            assertFails { cache.open(asset(server), session) }
            assertEquals(0, server.requestCount)
        }
    }
}
