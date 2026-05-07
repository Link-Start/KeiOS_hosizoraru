package os.kei.ui.page.main.ba.support

import org.junit.Test
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
}
