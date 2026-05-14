package os.kei.ui.page.main.ba.support

import org.junit.Test
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BaCalendarPoolRemoteBridgeTest {
    @Test
    fun `calendar api parser normalizes links images and running state`() {
        val entries = parseBaCalendarEntriesFromApiBody(
            body = """
                {
                  "code": 0,
                  "data": [
                    {
                      "id": 7,
                      "title": "活动",
                      "activity_kind_id": 31,
                      "activity_kind_name": "特殊",
                      "begin_at": 10,
                      "end_at": 20,
                      "link_url": "/ba/huodong/16",
                      "cover": { "url": "//cdn.example/activity.webp" }
                    }
                  ]
                }
            """.trimIndent(),
            nowMs = 15_000L
        )

        val entry = entries.single()
        assertEquals("活动", entry.title)
        assertEquals("https://www.gamekee.com/ba/huodong/16", entry.linkUrl)
        assertEquals("https://cdn.example/activity.webp", entry.imageUrl)
        assertTrue(entry.isRunning)
    }

    @Test
    fun `pool api parser keeps active unknown tag as fallback active tag`() {
        val entries = parseBaPoolEntriesFromAllApiBody(
            body = """
                {
                  "code": 0,
                  "data": [
                    {
                      "id": 9,
                      "name": "招募",
                      "tag_id": "999",
                      "start_at": 10,
                      "end_at": 20,
                      "link_url": "kachi/detail",
                      "image_url": "/upload/pool.png"
                    }
                  ]
                }
            """.trimIndent(),
            nowMs = 15_000L
        )

        val entry = entries.single()
        assertEquals(BA_POOL_FALLBACK_ACTIVE_TAG_ID, entry.tagId)
        assertEquals("https://www.gamekee.com/kachi/detail", entry.linkUrl)
        assertEquals("https://www.gamekee.com/upload/pool.png", entry.imageUrl)
    }

    @Test
    fun `pool api parser separates explicit student guide link from source link`() {
        val entries = parseBaPoolEntriesFromAllApiBody(
            body = """
                {
                  "code": 0,
                  "data": [
                    {
                      "id": 17,
                      "name": "妃咲",
                      "tag_id": "9",
                      "start_at": 10,
                      "end_at": 20,
                      "link_url": "https://www.gamekee.com/ba/tj/68993.html",
                      "icon": "//cdn.example/kisaki.webp"
                    }
                  ]
                }
            """.trimIndent(),
            nowMs = 15_000L
        )

        val entry = entries.single()
        assertEquals("https://www.gamekee.com/ba/tj/68993.html", entry.linkUrl)
        assertEquals("https://www.gamekee.com/ba/tj/68993.html", entry.studentGuideUrl)
    }

    @Test
    fun `pool api parser leaves cn pool source link out of student guide link`() {
        val entries = parseBaPoolEntriesFromAllApiBody(
            body = """
                {
                  "code": 0,
                  "data": [
                    {
                      "id": 2388,
                      "name": "优香(体操服)",
                      "tag_id": "6",
                      "start_at": 10,
                      "end_at": 20,
                      "link_url": "https://www.gamekee.com/ba/701261.html",
                      "icon": "//cdn.example/yuuka.webp"
                    }
                  ]
                }
            """.trimIndent(),
            nowMs = 15_000L
        )

        val entry = entries.single()
        assertEquals("https://www.gamekee.com/ba/701261.html", entry.linkUrl)
        assertEquals("", entry.studentGuideUrl)
    }

    @Test
    fun `pool student guide resolver maps cn pool source link by catalog name`() {
        val pool = BaPoolEntry(
            id = 2388,
            name = "优香(体操服)",
            tagId = 6,
            tagName = "",
            startAtMs = 10_000L,
            endAtMs = 20_000L,
            linkUrl = "https://www.gamekee.com/ba/701261.html",
            imageUrl = "",
            isRunning = true
        )
        val resolver = BaPoolStudentGuideUrlResolver.fromCatalogEntries(
            listOf(catalogEntry(contentId = 170295L, name = "优香(体操服)"))
        )

        val resolved = resolver.resolve(pool)

        assertEquals("https://www.gamekee.com/ba/tj/170295.html", resolved.studentGuideUrl)
        assertEquals("https://www.gamekee.com/ba/tj/170295.html", resolved.studentGuideOpenUrl)
    }

    @Test
    fun `pool cache round trip preserves student guide link`() {
        val raw = encodeBaPoolEntries(
            listOf(
                BaPoolEntry(
                    id = 2388,
                    name = "优香(体操服)",
                    tagId = 6,
                    tagName = "",
                    startAtMs = 10_000L,
                    endAtMs = 20_000L,
                    linkUrl = "https://www.gamekee.com/ba/701261.html",
                    imageUrl = "",
                    isRunning = true,
                    studentGuideUrl = "https://www.gamekee.com/ba/tj/170295.html"
                )
            )
        )

        val decoded = decodeBaPoolEntries(raw, nowMs = 15_000L).single()

        assertEquals("https://www.gamekee.com/ba/701261.html", decoded.linkUrl)
        assertEquals("https://www.gamekee.com/ba/tj/170295.html", decoded.studentGuideUrl)
    }

    @Test
    fun `remote result reports all source failure with compact error`() {
        val error = assertFailsWith<IllegalStateException> {
            fetchBaPoolRemoteResult(
                serverIndex = 2,
                nowMs = 1_000L,
                fetchJson = { _, _ -> error("blocked") }
            )
        }

        assertTrue(error.message.orEmpty().contains("pool all sources failed"))
        assertTrue(error.message.orEmpty().contains("blocked"))
    }

    private fun catalogEntry(
        contentId: Long,
        name: String,
    ): BaGuideCatalogEntry {
        return BaGuideCatalogEntry(
            entryId = contentId.toInt(),
            pid = 49443,
            contentId = contentId,
            name = name,
            alias = "",
            aliasDisplay = "",
            iconUrl = "",
            type = 0,
            order = 0,
            createdAtSec = 0L,
            detailUrl = "https://www.gamekee.com/ba/tj/$contentId.html",
            tab = BaGuideCatalogTab.Student
        )
    }
}
