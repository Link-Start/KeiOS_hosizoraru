package os.kei.ui.page.main.student

import org.json.JSONObject
import org.junit.Test
import os.kei.ui.page.main.student.catalog.testCatalogEntry
import os.kei.ui.page.main.student.catalog.state.toMemoryLobbyResolvedItem
import os.kei.ui.page.main.student.fetch.extractWebMemoryLobbies
import os.kei.ui.page.main.student.fetch.parseGuideDetailFromContentJson
import os.kei.ui.page.main.student.page.support.collectGuideMediaCacheUrls
import os.kei.ui.page.main.student.tabcontent.render.resolveGuideGalleryTabState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BaGuideWebMemoryLobbyTest {
    @Test
    fun `new student fixtures expose dynamic lobby without inventing a video or texture illustrations`() {
        for ((id, textureCount) in listOf("718266" to 3, "714062" to 2)) {
            val gallery = fixture(id)
            val web = gallery.single { it.mediaType == "web" }
            val lobby = assertNotNull(web.webMemoryLobby)
            assertEquals("https://www.gamekee.com/ba/$id?tab=3", lobby.viewerUrl)
            assertEquals(textureCount, lobby.textureUrls.size)
            assertEquals("Idle_01", lobby.animation)
            assertTrue(assertNotNull(lobby.viewport).width > 2_000)
            assertEquals("5", web.memoryUnlockLevel)
            assertTrue(lobby.atlasUrl.endsWith(".atlas"))
            assertTrue(lobby.skeletonUrl.endsWith(".skel"))
            assertTrue(gallery.none { it.mediaType == "video" })
            assertTrue(gallery.none { it.title.startsWith("回忆大厅文件") })
            assertTrue(gallery.none { it.imageUrl in lobby.textureUrls })
            val state = resolveGuideGalleryTabState(info(id, gallery))
            assertEquals(1, state.memoryHallVideoGroup?.second?.size)
            assertTrue(state.hasRenderableContent)
            assertTrue(state.memoryHallVideoGroup?.second?.single()?.imageUrl?.endsWith(".png") == true)
            val catalogItem = assertNotNull(info(id, gallery).toMemoryLobbyResolvedItem(testCatalogEntry(), false))
            assertTrue(catalogItem.galleryItems.any { it.webMemoryLobby == lobby })
        }
    }

    @Test
    fun `migrated old student retains both Web and MP4 pathways`() {
        val gallery = fixture("59934")
        val state = resolveGuideGalleryTabState(info("59934", gallery))
        val items = assertNotNull(state.memoryHallVideoGroup).second
        assertEquals(setOf("web", "video"), items.map { it.mediaType }.toSet())
        assertTrue(items.single { it.mediaType == "video" }.mediaUrl.endsWith("256762.mp4"))
        assertEquals("8", state.memoryUnlockLevel)
    }

    @Test
    fun `gallery derivation follows resource changes even with the same timestamp and item count`() {
        val original = info("718266", fixture("718266"))
        val initial = assertNotNull(resolveGuideGalleryTabState(original).memoryHallVideoGroup).second.single()
        assertEquals("Idle_01", initial.webMemoryLobby?.animation)
        val updated = original.copy(galleryItems = original.galleryItems.map { item ->
            item.webMemoryLobby?.let { item.copy(webMemoryLobby = it.copy(animation = "Talk_01_M")) } ?: item
        })
        val changed = assertNotNull(resolveGuideGalleryTabState(updated).memoryHallVideoGroup).second.single()
        assertEquals("Talk_01_M", changed.webMemoryLobby?.animation)
    }

    @Test
    fun `gallery cache roundtrip retains full resource and accepts old video cache`() {
        val items = fixture("59934")
        assertEquals(items, decodeGalleryItemsFromArray(encodeGalleryItems(items)))
        val old = BaGuideGalleryItem("回忆大厅视频", "", "video", "https://example.com/old.mp4")
        assertEquals(listOf(old), decodeGalleryItemsFromArray(encodeGalleryItems(listOf(old))))
    }

    @Test
    fun `viewport retains Wiki camera coordinates and rejects invalid extents`() {
        val lobby = assertNotNull(fixture("714062").single { it.mediaType == "web" }.webMemoryLobby)
        assertEquals(BaGuideSpineViewport(-1456.303939432598, 233.08702541282145, 2617.0117734989517, 1397.7109961662948), lobby.viewport)
        assertEquals(lobby, decodeWebMemoryLobby(lobby.toJson()))
        val badViewport = JSONObject().put("x", 0).put("y", 0).put("width", -1).put("height", 100)
        assertEquals(null, decodeSpineViewport(badViewport))
        assertEquals(null, decodeSpineViewport(badViewport.put("width", 100).put("x", "invalid")))
        assertEquals(null, decodeSpineViewport(badViewport.put("x", 1e30)))
    }

    @Test
    fun `cached resource rejects executable URLs and forged GameKee hosts`() {
        val lobby = assertNotNull(fixture("718266").single { it.mediaType == "web" }.webMemoryLobby)
        for (url in listOf("javascript:alert(1)", "https://gamekee.com.evil.test/a.atlas", "https://cdnimg.gamekee.com:8443/a.atlas")) {
            assertEquals(null, decodeWebMemoryLobby(lobby.toJson().put("atlas", url)))
        }
    }

    @Test
    fun `previous Web parser cache refreshes for camera metadata`() {
        val original = info("718266", fixture("718266")).copy(galleryParserVersion = 1)
        assertTrue(BaStudentGuideStore.isCacheExpired(BaStudentGuideCacheSnapshot(original, true, true, 1L), 12, 2L))
    }

    @Test
    fun `offline media cache retains lobby poster and MP4 without downloading the Wiki as media`() {
        val gallery = fixture("59934")
        val urls = collectGuideMediaCacheUrls(info("59934", gallery))
        assertTrue(urls.any { it.endsWith("256762.mp4") })
        assertTrue(urls.any { it.endsWith("674225.png") })
        assertFalse(urls.any { it.contains("www.gamekee.com/ba/") })
    }

    @Test
    fun `old payload remains readable but expires for parser migration while current payload stays fresh`() {
        val original = info("718266", fixture("718266"))
        val payload = encodeGuideV2Payload(original).toMutableMap()
        val decoded = assertNotNull(decodeGuideV2InfoFromPayload(original.sourceUrl) { payload[it].orEmpty() })
        assertEquals(original.galleryItems, decoded.galleryItems)
        assertEquals(BA_GUIDE_GALLERY_PARSER_VERSION, decoded.galleryParserVersion)
        assertFalse(BaStudentGuideStore.isCacheExpired(
            BaStudentGuideCacheSnapshot(decoded, true, true, decoded.syncedAtMs), 12, 2L,
        ))
        payload[CACHE_SUFFIX_META] = JSONObject(payload.getValue(CACHE_SUFFIX_META))
            .apply { remove("galleryParserVersion") }.toString()
        val legacy = assertNotNull(decodeGuideV2InfoFromPayload(original.sourceUrl) { payload[it].orEmpty() })
        assertEquals(0, legacy.galleryParserVersion)
        assertEquals(original.galleryItems, legacy.galleryItems)
        assertTrue(BaStudentGuideStore.isCacheExpired(
            BaStudentGuideCacheSnapshot(legacy, true, true, legacy.syncedAtMs), 12, 2L,
        ))
    }

    @Test
    fun `incomplete resources and untrusted viewer URLs are not dynamic lobbies`() {
        val source = "https://www.gamekee.com/ba/718266"
        assertTrue(extractWebMemoryLobbies(source, JSONObject().put("atlas", "//cdnimg.gamekee.com/a.atlas")).isEmpty())
        val valid = JSONObject().put("atlas", "//cdnimg.gamekee.com/a.atlas")
            .put("json", "//cdnimg.gamekee.com/a.json")
            .put("image", "//cdnimg.gamekee.com/a.png,//cdnimg.gamekee.com/b.png")
        val result = extractWebMemoryLobbies(source, valid).single()
        assertEquals(2, result.textureUrls.size)
        assertTrue(result.skeletonUrl.isBlank())
        assertTrue(extractWebMemoryLobbies("https://gamekee.com.evil.test/ba/718266", valid).isEmpty())
        assertTrue(extractWebMemoryLobbies(source, valid.put("atlas", "javascript:alert(1)")).isEmpty())
        assertTrue(extractWebMemoryLobbies(source, valid.put("atlas", "N")).isEmpty())
        assertTrue(extractWebMemoryLobbies(source, valid.put("atlas", "null")).isEmpty())
        assertFalse(hasRenderableGalleryMedia(BaGuideGalleryItem("回忆大厅视频", "", "web", source)))
        assertEquals(source + "?tab=3", gameKeeMemoryLobbyViewerUrl("https://www.gamekee.com/ba/tj/718266.html"))
    }

    private fun fixture(id: String): List<BaGuideGalleryItem> = parseGuideDetailFromContentJson(
        javaClass.getResource("/gamekee/memory-lobby/$id.json")!!.readText(),
        "https://www.gamekee.com/ba/$id",
    ).galleryItems

    private fun info(id: String, gallery: List<BaGuideGalleryItem>) = BaStudentGuideInfo(
        sourceUrl = "https://www.gamekee.com/ba/$id",
        title = id, subtitle = "", description = "", imageUrl = "", summary = "",
        stats = emptyList(), galleryItems = gallery, syncedAtMs = 1L,
        galleryParserVersion = BA_GUIDE_GALLERY_PARSER_VERSION,
    )
}
