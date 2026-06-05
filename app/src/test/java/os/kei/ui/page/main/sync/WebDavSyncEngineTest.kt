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
    fun `sync creates empty remote with create only write`() = runBlocking {
        val client = FakeWebDavSyncClientBridge(
            downloadResults = mutableListOf(WebDavDownloadResult.Empty),
            uploadIfAbsentResults = mutableListOf(WebDavUploadResult.Success("etag-new")),
        )
        val metadata = FakeWebDavSyncMetadataStore()
        val port = FakeWebDavSyncDataPort(localJson = """{"items":[1]}""")
        val engine = WebDavSyncEngine(
            clientFactory = { client },
            metadataStore = metadata,
            nowMillis = { 5678L },
        )

        val outcome = engine.sync(fakeConfig(), WebDavSyncItem.GitHubTracked, port.port)

        assertEquals(WebDavItemStatus.Uploaded, outcome.status)
        assertEquals(1, client.uploadIfAbsentCalls.size)
        assertTrue(client.uploadCalls.isEmpty())
        assertEquals("etag-new", metadata.etags[WebDavSyncItem.GitHubTracked])
        assertEquals(5678L, metadata.lastSyncTimes[WebDavSyncItem.GitHubTracked])
    }

    @Test
    fun `sync empty remote conflict re downloads and merges latest remote`() = runBlocking {
        val client = FakeWebDavSyncClientBridge(
            downloadResults = mutableListOf(
                WebDavDownloadResult.Empty,
                WebDavDownloadResult.Success("remote-after-empty-preview", "etag-after"),
            ),
            uploadIfAbsentResults = mutableListOf(WebDavUploadResult.Conflict),
            uploadResults = mutableListOf(WebDavUploadResult.Success("etag-merged")),
        )
        val metadata = FakeWebDavSyncMetadataStore()
        val port = FakeWebDavSyncDataPort(localJson = "local")
        val engine = WebDavSyncEngine(
            clientFactory = { client },
            metadataStore = metadata,
            nowMillis = { 6789L },
        )

        val outcome = engine.sync(fakeConfig(), WebDavSyncItem.OsActivityCards, port.port)

        assertEquals(WebDavItemStatus.Merged, outcome.status)
        assertEquals(1, client.uploadIfAbsentCalls.size)
        assertEquals(listOf("remote-after-empty-preview"), port.mergeCalls)
        assertEquals("merge(remote-after-empty-preview)", client.uploadCalls.single().content)
        assertEquals("etag-after", client.uploadCalls.single().etag)
        assertEquals("etag-merged", metadata.etags[WebDavSyncItem.OsActivityCards])
        assertEquals(6789L, metadata.lastSyncTimes[WebDavSyncItem.OsActivityCards])
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

    @Test
    fun `prepare upload plan refreshes remote and marks shrink risk`() = runBlocking {
        val client = FakeWebDavSyncClientBridge(
            downloadResults = mutableListOf(
                WebDavDownloadResult.Success("""{"items":[1,2,3]}""", "etag-remote"),
            ),
        )
        val metadata = FakeWebDavSyncMetadataStore()
        val engine = WebDavSyncEngine(
            clientFactory = { client },
            metadataStore = metadata,
            nowMillis = { 2468L },
        )
        val port = FakeWebDavSyncDataPort(
            localJson = """{"items":[1]}""",
            localCount = 1,
            remoteItemCount = 3,
        )

        val planItem =
            engine.prepareChange(
                config = fakeConfig(),
                kind = WebDavBatchKind.Upload,
                item = WebDavSyncItem.GitHubTracked,
                port = port.port,
            )

        assertEquals(WebDavSyncPlanEffect.UploadOverwrite, planItem.effect)
        assertTrue(planItem.remoteState is WebDavSyncPlanRemoteState.Found)
        assertTrue(planItem.shrinksRemote)
        assertEquals("etag-remote", planItem.remoteEtag)
        assertEquals(2468L, metadata.remoteFound[WebDavSyncItem.GitHubTracked]?.probedAtMs)
        assertEquals(3, metadata.remoteFound[WebDavSyncItem.GitHubTracked]?.itemCount)
    }

    @Test
    fun `planned upload uses refreshed remote etag for conditional write`() = runBlocking {
        val client = FakeWebDavSyncClientBridge(
            uploadResults = mutableListOf(WebDavUploadResult.Success("etag-after")),
        )
        val metadata = FakeWebDavSyncMetadataStore()
        val engine = WebDavSyncEngine(
            clientFactory = { client },
            metadataStore = metadata,
            nowMillis = { 9999L },
        )
        val port = FakeWebDavSyncDataPort(localJson = """{"items":[1]}""")

        val outcome =
            engine.upload(
                config = fakeConfig(),
                item = WebDavSyncItem.GitHubTracked,
                port = port.port,
                expectedRemoteEtag = "etag-before",
            )

        assertEquals(WebDavItemStatus.Uploaded, outcome.status)
        assertEquals("etag-before", client.uploadCalls.single().etag)
        assertEquals("etag-after", metadata.etags[WebDavSyncItem.GitHubTracked])
    }

    @Test
    fun `planned upload returns conflict when remote changed after preview`() = runBlocking {
        val client = FakeWebDavSyncClientBridge(
            uploadResults = mutableListOf(WebDavUploadResult.Conflict),
        )
        val metadata = FakeWebDavSyncMetadataStore()
        val engine = WebDavSyncEngine(
            clientFactory = { client },
            metadataStore = metadata,
            nowMillis = { 9999L },
        )
        val port = FakeWebDavSyncDataPort(localJson = """{"items":[1]}""")

        val outcome =
            engine.upload(
                config = fakeConfig(),
                item = WebDavSyncItem.GitHubTracked,
                port = port.port,
                expectedRemoteEtag = "etag-before",
            )

        assertEquals(WebDavItemStatus.ConflictUnresolved, outcome.status)
        assertEquals("etag-before", client.uploadCalls.single().etag)
        assertTrue(metadata.etags.isEmpty())
    }

    @Test
    fun `planned upload uses create only write when preview saw empty remote`() = runBlocking {
        val client = FakeWebDavSyncClientBridge(
            uploadIfAbsentResults = mutableListOf(WebDavUploadResult.Conflict),
        )
        val metadata = FakeWebDavSyncMetadataStore()
        val engine = WebDavSyncEngine(
            clientFactory = { client },
            metadataStore = metadata,
            nowMillis = { 9999L },
        )
        val port = FakeWebDavSyncDataPort(localJson = """{"items":[1]}""")

        val outcome =
            engine.upload(
                config = fakeConfig(),
                item = WebDavSyncItem.GitHubTracked,
                port = port.port,
                remoteKnownEmpty = true,
            )

        assertEquals(WebDavItemStatus.ConflictUnresolved, outcome.status)
        assertEquals(1, client.uploadIfAbsentCalls.size)
        assertTrue(client.uploadCalls.isEmpty())
        assertTrue(metadata.etags.isEmpty())
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
    private val localCount: Int = 0,
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
        localCount = { localCount },
        countRemoteItems = { remoteItemCount },
    )
}

private class FakeWebDavSyncClientBridge(
    val downloadResults: MutableList<WebDavDownloadResult> = mutableListOf(),
    val uploadResults: MutableList<WebDavUploadResult> = mutableListOf(),
    val uploadIfAbsentResults: MutableList<WebDavUploadResult> = mutableListOf(),
) : WebDavSyncClientBridge {
    data class UploadCall(val fileName: String, val content: String, val etag: String?)
    data class UploadIfAbsentCall(val fileName: String, val content: String)

    val uploadCalls = mutableListOf<UploadCall>()
    val uploadIfAbsentCalls = mutableListOf<UploadIfAbsentCall>()

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

    override suspend fun uploadIfAbsent(fileName: String, content: String): WebDavUploadResult {
        uploadIfAbsentCalls += UploadIfAbsentCall(fileName, content)
        return uploadIfAbsentResults.removeFirstOrNull() ?: WebDavUploadResult.Success("etag-default")
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
