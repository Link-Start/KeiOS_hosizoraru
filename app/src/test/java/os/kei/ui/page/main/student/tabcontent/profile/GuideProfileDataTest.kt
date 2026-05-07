package os.kei.ui.page.main.student.tabcontent.profile

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuideProfileDataTest {
    @Test
    fun `gamekee guide link title uses content detail first`() {
        var detailRequest: Pair<Long, String>? = null
        var htmlCalled = false

        val title = fetchProfileLinkTitle(
            url = "https://www.gamekee.com/ba/tj/591006.html",
            fetchDetailTitle = { contentId, refererPath ->
                detailRequest = contentId to refererPath
                "堇（打工）"
            },
            fetchHtml = {
                htmlCalled = true
                ""
            }
        )

        assertEquals("堇（打工）", title)
        assertEquals(591006L to "/ba/tj/591006.html", detailRequest)
        assertTrue(!htmlCalled)
    }

    @Test
    fun `external link title uses html fallback`() {
        val title = fetchProfileLinkTitle(
            url = "https://example.com/page",
            fetchDetailTitle = { _, _ -> error("detail unused") },
            fetchHtml = {
                """<html><head><meta property="og:title" content="外部资料"></head></html>"""
            }
        )

        assertEquals("外部资料", title)
    }

    @Test
    fun `spa shell without title returns empty title`() {
        val title = fetchProfileLinkTitle(
            url = "https://example.com/app",
            fetchDetailTitle = { _, _ -> error("detail unused") },
            fetchHtml = { """<html><body><div id="app"></div></body></html>""" }
        )

        assertEquals("", title)
    }
}
