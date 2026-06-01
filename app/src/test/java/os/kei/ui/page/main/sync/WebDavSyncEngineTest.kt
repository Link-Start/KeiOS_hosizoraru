package os.kei.ui.page.main.sync

import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.webdav.client.WebDavDownloadResult
import os.kei.feature.webdav.client.WebDavTestConnectionResult
import os.kei.feature.webdav.client.WebDavUploadResult
import os.kei.feature.webdav.model.WebDavConfig
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebDavSyncEngineTest {
    @Test
    fun `sync returns up to date when local and remote content hashes match`() = runBlocking {
        val client = FakeWebDavSyncClientBridge(
            downloadResults = mutableListOf(
                WebDavDownloadResult.Success("""{"items":[1]}""", "etag-1"),
            ),
        )
        val metadata = FakeWebDavSyncMetadataStore()
        val engine = WebDavSyncEngine(
            clientFactory = { client },
            metadataStore = metadata,
            nowMillis = { 1234L },
        )
        val port = FakeWebDavSyncDataPort(localJson = """{"items":[1]}""")

        val outcome = engine.sync(fakeConfig(), WebDavSyncItem.GitHubTracked, port.port)

        assertEquals(WebDavItemStatus.UpToDate, outcome.status)
        assertEquals(0, port.mergeCalls.size)
        assertEquals(0, client.uploadCalls.size)
        assertEquals("etag-1", metadata.etags[WebDavSyncItem.GitHubTracked])
        assertEquals(1234L, metadata.lastSyncTimes[WebDavSyncItem.GitHubTracked])
    }

    @Test
    fun `sync conflict retry re merges latest remote payload before second upload`() = runBlocking {
        val client = FakeWebDavSyncClientBridge(
            downloadResults = mutableListOf(
                WebDavDownloadResult.Success("remote-v1", "etag-1"),
                WebDavDownloadResult.Success("remote-v2", "etag-2"),
            ),
            uploadResults = mutableListOf(
                WebDavUploadResult.Conflict,
                WebDavUploadResult.Success("etag-3"),
            ),
        )
        val metadata = FakeWebDavSyncMetadataStore()
        val port = FakeWebDavSyncDataPort(localJson = "local")
        val engine = WebDavSyncEngine(
            clientFactory = { client },
            metadataStore = metadata,
            nowMillis = { 777L },
        )

        val outcome = engine.sync(fakeConfig(), WebDavSyncItem.OsShellCards, port.port)

        assertEquals(WebDavItemStatus.Merged, outcome.status)
        assertEquals(listOf("remote-v1", "remote-v2"), port.mergeCalls)
        assertEquals(2, client.uploadCalls.size)
        assertEquals("merge(remote-v1)", client.uploadCalls[0].content)
        assertEquals("merge(remote-v1)+merge(remote-v2)", client.uploadCalls[1].content)
        assertEquals("etag-2", client.uploadCalls[1].etag)
        assertEquals("etag-3", metadata.etags[WebDavSyncItem.OsShellCards])
        assertEquals(777L, metadata.lastSyncTimes[WebDavSyncItem.OsShellCards])
    }

    @Test
    fun `probe remote found stores parsed remote summary`() = runBlocking {
        val client = FakeWebDavSyncClientBridge(
            downloadResults = mutableListOf(
                WebDavDownloadResult.Success("""{"items":[1,2,3]}""", "etag-9"),
            ),
        )
        val metadata = FakeWebDavSyncMetadataStore()
        val engine = WebDavSyncEngine(
            clientFactory = { client },
            metadataStore = metadata,
            nowMillis = { 999L },
        )
        val port = FakeWebDavSyncDataPort(
            localJson = "unused",
            remoteItemCount = 3,
        )

        val outcome = engine.probeRemote(fakeConfig(), WebDavSyncItem.BaCatalogFavorites, port.port)

        assertTrue(outcome is WebDavRemoteProbeOutcome.Found)
        assertEquals(3, outcome.itemCount)
        assertEquals(999L, metadata.remoteFound[WebDavSyncItem.BaCatalogFavorites]?.probedAtMs)
        assertEquals("etag-9", metadata.remoteFound[WebDavSyncItem.BaCatalogFavorites]?.etag)
        assertEquals(3, metadata.remoteFound[WebDavSyncItem.BaCatalogFavorites]?.itemCount)
    }
}

private fun fakeConfig() = WebDavConfig(
    serverUrl = "https://dav.example.com/dav/",
    username = "demo",
    appPassword = "secret",
    remoteDir = "KeiOS/",
)

private class FakeWebDavSyncDataPort(
    private var localJson: String,
    private val remoteItemCount: Int = 0,
) {
    val mergeCalls = mutableListOf<String>()
    val port = WebDavSyncDataPort(
        exportJson = { localJson },
        merge = { remote ->
            mergeCalls += remote
            localJson = if (localJson == "local") {
                "merge($remote)"
            } else {
                "$localJson+merge($remote)"
            }
        },
        localCount = { 0 },
        countRemoteItems = { remoteItemCount },
    )
}

private class FakeWebDavSyncClientBridge(
    val downloadResults: MutableList<WebDavDownloadResult> = mutableListOf(),
    val uploadResults: MutableList<WebDavUploadResult> = mutableListOf(),
) : WebDavSyncClientBridge {
    data class UploadCall(val fileName: String, val content: String, val etag: String?)

    val uploadCalls = mutableListOf<UploadCall>()

    override suspend fun testConnection(): WebDavTestConnectionResult =
        WebDavTestConnectionResult.Success(dirCreated = false)

    override suspend fun upload(
        fileName: String,
        content: String,
        etag: String?,
    ): WebDavUploadResult {
        uploadCalls += UploadCall(fileName, content, etag)
        return uploadResults.removeFirstOrNull() ?: WebDavUploadResult.Success("etag-default")
    }

    override suspend fun download(fileName: String): WebDavDownloadResult {
        return downloadResults.removeFirst()
    }
}

private class FakeWebDavSyncMetadataStore : WebDavSyncMetadataStore {
    data class RemoteSummaryRecord(
        val itemCount: Int,
        val byteSize: Long,
        val etag: String?,
        val probedAtMs: Long,
    )

    val etags = mutableMapOf<WebDavSyncItem, String?>()
    val hashes = mutableMapOf<WebDavSyncItem, String>()
    val lastSyncTimes = mutableMapOf<WebDavSyncItem, Long>()
    val remoteFound = mutableMapOf<WebDavSyncItem, RemoteSummaryRecord>()
    val remoteEmpty = mutableMapOf<WebDavSyncItem, Long>()

    override fun setItemEtag(item: WebDavSyncItem, etag: String?) {
        etags[item] = etag
    }

    override fun setItemContentHash(item: WebDavSyncItem, hash: String) {
        hashes[item] = hash
    }

    override fun setLastSyncTime(item: WebDavSyncItem, timeMs: Long) {
        lastSyncTimes[item] = timeMs
    }

    override fun saveRemoteSummaryFound(
        item: WebDavSyncItem,
        itemCount: Int,
        byteSize: Long,
        etag: String?,
        probedAtMs: Long,
    ) {
        remoteFound[item] = RemoteSummaryRecord(itemCount, byteSize, etag, probedAtMs)
    }

    override fun saveRemoteSummaryEmpty(item: WebDavSyncItem, probedAtMs: Long) {
        remoteEmpty[item] = probedAtMs
    }
}
