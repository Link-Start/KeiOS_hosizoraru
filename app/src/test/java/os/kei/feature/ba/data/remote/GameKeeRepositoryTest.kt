package os.kei.feature.ba.data.remote

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GameKeeRepositoryTest {
    @Test
    fun `content detail path normalizes invalid ids`() {
        assertEquals("/v1/content/detail/609145", GameKeeRepository.contentDetailApiPath(609145L))
        assertEquals("/v1/content/detail/0", GameKeeRepository.contentDetailApiPath(-1L))
    }

    @Test
    fun `ba api headers keep GameKee mobile contract`() {
        assertEquals(
            mapOf(
                "device-num" to "1",
                "game-alias" to "ba"
            ),
            GameKeeRepository.baApiHeaders()
        )
    }

    @Test
    fun `ba api repository builds calendar and pool requests with shared headers`() {
        assertEquals(
            mapOf(
                "device-num" to "1",
                "game-alias" to "ba"
            ),
            GameKeeRepository.baApiHeaders()
        )
    }

    @Test
    fun `network result failure keeps compact preview and throwable`() {
        val result = GameKeeNetworkResult.Failure(
            request = GameKeeNetworkRequest(
                pathOrUrl = "/v1/content/detail/1",
                refererPath = "/ba/tj/1.html"
            ),
            errorPreview = "IOException:http=403 body=<html>blocked...</html>",
            throwable = IllegalStateException("blocked")
        )

        val error = assertFailsWith<IllegalStateException> {
            result.getOrThrow()
        }
        assertEquals("blocked", error.message)
        assertTrue(result.errorPreview.length < 220)
    }

    @Test
    fun `content detail uses inline content json before cdn`() {
        val body = detailBody(
            contentJson = guideContentJson("角色名称", "星野"),
            content = guideContentJson("角色名称", "备用"),
            contentCdn = "//api-cdn.gamekee.com/wiki2.0/pro/829/content/1.json"
        )

        val detail = resolveBaContentDetailFromApiJson(
            contentId = 1L,
            refererPath = "/ba/tj/1.html",
            apiBody = body,
            fetchCdnJson = { error("cdn should stay unused") }
        )

        assertEquals(GameKeeBaContentSource.InlineContentJson, detail.contentSource)
        assertTrue(detail.resolvedContentJson.contains("星野"))
    }

    @Test
    fun `content detail resolves current cdn content wrapper`() {
        var request: GameKeeNetworkRequest? = null
        val body = detailBody(
            contentJson = "",
            content = "",
            contentCdn = "//api-cdn.gamekee.com/wiki2.0/pro/829/content/591006.json?v=1"
        )

        val detail = resolveBaContentDetailFromApiJson(
            contentId = 591006L,
            refererPath = "/ba/tj/591006.html",
            apiBody = body,
            fetchCdnJson = { incoming ->
                request = incoming
                GameKeeNetworkResult.Success(
                    value = """{"content":${
                        org.json.JSONObject.quote(
                            guideContentJson(
                                "BGM",
                                "//cdn.example/bgm.ogg"
                            )
                        )
                    },"editor_type":3}""",
                    request = incoming
                )
            }
        )

        assertEquals(GameKeeBaContentSource.ContentCdn, detail.contentSource)
        assertEquals(
            "https://api-cdn.gamekee.com/wiki2.0/pro/829/content/591006.json?v=1",
            request?.pathOrUrl
        )
        assertTrue(detail.resolvedContentJson.contains("//cdn.example/bgm.ogg"))
    }

    @Test
    fun `content detail keeps cdn failure as diagnosable empty result`() {
        val body = detailBody(
            contentJson = "",
            content = "",
            contentCdn = "//api-cdn.gamekee.com/wiki2.0/pro/829/content/591006.json?v=1"
        )

        val detail = resolveBaContentDetailFromApiJson(
            contentId = 591006L,
            refererPath = "/ba/tj/591006.html",
            apiBody = body,
            fetchCdnJson = { incoming ->
                GameKeeNetworkResult.Failure(
                    request = incoming,
                    errorPreview = "IOException:http=567 body=blocked"
                )
            }
        )

        assertEquals(GameKeeBaContentSource.Empty, detail.contentSource)
        assertTrue(detail.errorSummary.contains("http=567"))
    }

    private fun detailBody(
        contentJson: String,
        content: String,
        contentCdn: String
    ): String {
        return """
            {
              "code": 0,
              "data": {
                "id": 591006,
                "title": "测试学生",
                "summary": "",
                "content_json": ${org.json.JSONObject.quote(contentJson)},
                "content": ${org.json.JSONObject.quote(content)},
                "content_cdn": ${org.json.JSONObject.quote(contentCdn)},
                "game": { "name": "碧蓝档案" },
                "thumb": "",
                "image_list": null,
                "thumb_list": null,
                "video_list": null
              }
            }
        """.trimIndent()
    }

    private fun guideContentJson(key: String, value: String): String {
        return """
            {
              "baseData": [
                [
                  { "type": "text", "value": "$key", "isGlobal": true },
                  { "type": "text", "value": "$value" }
                ]
              ]
            }
        """.trimIndent()
    }
}
