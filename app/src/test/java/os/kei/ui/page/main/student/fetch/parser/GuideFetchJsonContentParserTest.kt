package os.kei.ui.page.main.student.fetch.parser

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import os.kei.ui.page.main.student.fetch.parseGuideDetailFromContentJson
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuideFetchJsonContentParserTest {
    @Test
    fun `terrain rows keep first value when GameKee payload contains replacement candidates`() {
        val detail = parseGuideDetailFromObjectContentJson(
            raw = objectContentJson(
                row(
                    "屋外",
                    textCell("B"),
                    imageCell("//cdn.example/outdoor-b.png"),
                    textCell(""),
                    textCell("C"),
                    imageCell("//cdn.example/outdoor-c.png"),
                    textCell("D"),
                    imageCell("//cdn.example/outdoor-d.png")
                ),
                row(
                    "屋内",
                    textCell("D"),
                    imageCell("//cdn.example/indoor-d.png"),
                    textCell(""),
                    textCell("A"),
                    imageCell("//cdn.example/indoor-a.png"),
                    textCell("B"),
                    imageCell("//cdn.example/indoor-b.png")
                )
            ),
            sourceUrl = "https://www.gamekee.com/ba/tj/591006.html"
        )

        val profileValues = detail.profileRows.associate { it.key to it.value }
        assertEquals("B", profileValues["屋外"])
        assertEquals("D", profileValues["屋内"])
    }

    @Test
    fun `momotalk unlock level clears status text instead of using memory lobby level`() {
        val status = "奉行不回头看过去的原则。"
        val detail = parseGuideDetailFromObjectContentJson(
            raw = objectContentJson(
                row("回忆大厅解锁等级", textCell("5")),
                row("MomoTalk解锁等级", textCell(status)),
                row("MomoTalk状态消息", textCell(status))
            ),
            sourceUrl = "https://www.gamekee.com/ba/tj/591006.html"
        )

        val profileValues = detail.profileRows.associate { it.key to it.value }
        assertEquals("", profileValues["MomoTalk解锁等级"])
        assertEquals(status, profileValues["MomoTalk状态消息"])
    }

    @Test
    fun `momotalk unlock level keeps numeric level lists`() {
        val detail = parseGuideDetailFromObjectContentJson(
            raw = objectContentJson(
                row("MomoTalk解锁等级", textCell("Lv.2、Lv.3、5级")),
                row("MomoTalk状态消息", textCell("今天也会认真执行任务。"))
            ),
            sourceUrl = "https://www.gamekee.com/ba/tj/sample.html"
        )

        val profileValues = detail.profileRows.associate { it.key to it.value }
        assertEquals("2 / 3 / 5", profileValues["MomoTalk解锁等级"])
    }

    @Test
    fun `cdn content wrapper unwraps into normal guide detail`() {
        val content = objectContentJson(
            row("角色名称", textCell("胡桃")),
            row("立绘", imageCell("//cdn.example/hutao.png")),
            row("技能名称", textCell("防护盾，启动"))
        )
        val detail = parseGuideDetailFromContentJson(
            raw = JSONObject()
                .put("content", content)
                .put("editor_type", 3)
                .toString(),
            sourceUrl = "https://www.gamekee.com/ba/tj/591006.html"
        )

        assertEquals("胡桃", detail.profileRows.associate { it.key to it.value }["角色名称"])
        assertEquals("防护盾，启动", detail.skillRows.associate { it.key to it.value }["技能名称"])
        assertEquals("https://cdn.example/hutao.png", detail.galleryItems.first().mediaUrl)
    }

    @Test
    fun `array npc gallery keeps old tab info media beyond one hundred items`() {
        val detail = parseGuideDetailFromArrayContentJson(
            raw = arrayContentJson(
                tabInfoNode(
                    tab("立绘", 1, "standing"),
                    tab("差分", 110, "expression"),
                    tab("设定集", 2, "setting")
                )
            ),
            sourceUrl = "https://www.gamekee.com/ba/161188.html"
        )

        val mediaUrls = detail.galleryItems.map { it.mediaUrl }
        assertEquals(113, mediaUrls.size)
        assertTrue("https://cdn.example/expression-110.png" in mediaUrls)
        assertTrue("https://cdn.example/setting-2.png" in mediaUrls)
        assertTrue(detail.galleryItems.any { it.title.startsWith("角色表情") })
        assertTrue(detail.galleryItems.any { it.title.startsWith("设定集") })
    }

    @Test
    fun `object npc gallery accepts old custom categories and inherited blank rows`() {
        val detail = parseGuideDetailFromObjectContentJson(
            raw = objectContentJson(
                row("人物介绍", imageCell("//cdn.example/intro.png")),
                row("差分（私服）", imageCell("//cdn.example/private-1.png")),
                row("", imageCell("//cdn.example/private-2.png")),
                row("动画设定图", imageCell("//cdn.example/animation-setting.png")),
                row("技能图标", imageCell("//cdn.example/skill-icon.png"))
            ),
            sourceUrl = "https://www.gamekee.com/ba/161175.html"
        )

        val mediaUrls = detail.galleryItems.map { it.mediaUrl }
        assertTrue("https://cdn.example/intro.png" in mediaUrls)
        assertTrue("https://cdn.example/private-1.png" in mediaUrls)
        assertTrue("https://cdn.example/private-2.png" in mediaUrls)
        assertTrue("https://cdn.example/animation-setting.png" in mediaUrls)
        assertFalse("https://cdn.example/skill-icon.png" in mediaUrls)
        assertTrue(
            detail.galleryItems.any {
                it.mediaUrl == "https://cdn.example/private-2.png" &&
                    it.title.startsWith("差分（私服）")
            }
        )
    }

    @Test
    fun `array relation info preserves related and same name groups`() {
        val detail = parseGuideDetailFromArrayContentJson(
            raw = arrayContentJson(
                relationInfoNode(
                    relationGroup(
                        title = "相关人物",
                        relationItem(
                            name = "桃香",
                            href = "/ba/tj/161100.html",
                            avatar = "//cdn.example/momoka.png",
                        ),
                    ),
                    relationGroup(
                        title = "同名角色",
                        relationItem(
                            name = "凛(临战)",
                            href = "/ba/tj/161101.html",
                            avatar = "//cdn.example/rin-alt.png",
                        ),
                    ),
                ),
            ),
            sourceUrl = "https://www.gamekee.com/ba/161188.html",
        )

        val rowsByKey = detail.profileRows.groupBy { it.key }
        assertEquals("相关人物", rowsByKey.getValue("相关角色").single().value)
        assertEquals("同名角色", rowsByKey.getValue("相关同名角色").single().value)
        assertEquals(
            "桃香 / https://www.gamekee.com/ba/tj/161100.html",
            rowsByKey.getValue("相关角色名称").single().value,
        )
        assertEquals(
            "凛(临战) / https://www.gamekee.com/ba/tj/161101.html",
            rowsByKey.getValue("同名角色名称").single().value,
        )
    }

    private fun objectContentJson(vararg rows: JSONArray): String {
        return JSONObject()
            .put(
                "baseData",
                JSONArray().apply {
                    rows.forEach(::put)
                }
            )
            .toString()
    }

    private fun arrayContentJson(vararg nodes: JSONObject): String {
        return JSONArray()
            .apply {
                nodes.forEach(::put)
            }
            .toString()
    }

    private fun tabInfoNode(vararg tabs: JSONObject): JSONObject {
        return JSONObject()
            .put("type", "illustrated-book")
            .put(
                "data",
                JSONArray().put(
                    JSONObject()
                        .put("type", "tab-info")
                        .put(
                            "data",
                            JSONObject()
                                .put("title", "")
                                .put(
                                    "tabList",
                                    JSONArray().apply {
                                        tabs.forEach(::put)
                                    }
                                )
                        )
                )
            )
    }

    private fun tab(title: String, imageCount: Int, imagePrefix: String): JSONObject {
        return JSONObject()
            .put("title", title)
            .put(
                "content",
                JSONArray().apply {
                    repeat(imageCount) { index ->
                        put(JSONObject().put("url", "//cdn.example/$imagePrefix-${index + 1}.png"))
                    }
                }
            )
    }

    private fun relationInfoNode(vararg groups: JSONObject): JSONObject {
        return JSONObject()
            .put("type", "illustrated-book")
            .put(
                "data",
                JSONArray().put(
                    JSONObject()
                        .put("type", "relation-info")
                        .put(
                            "data",
                            JSONObject()
                                .put(
                                    "list",
                                    JSONArray().apply {
                                        groups.forEach(::put)
                                    },
                                ),
                        ),
                ),
            )
    }

    private fun relationGroup(
        title: String,
        vararg items: JSONObject,
    ): JSONObject {
        return JSONObject()
            .put("title", title)
            .put(
                "content",
                JSONArray().apply {
                    items.forEach(::put)
                },
            )
    }

    private fun relationItem(
        name: String,
        href: String,
        avatar: String,
    ): JSONObject {
        return JSONObject()
            .put("name", name)
            .put("jumpHref", href)
            .put("avatar", avatar)
    }

    private fun row(key: String, vararg cells: JSONObject): JSONArray {
        return JSONArray()
            .put(textCell(key, isGlobal = true))
            .apply {
                cells.forEach(::put)
            }
    }

    private fun textCell(value: String, isGlobal: Boolean = false): JSONObject {
        return JSONObject()
            .put("type", "text")
            .put("value", value)
            .apply {
                if (isGlobal) put("isGlobal", true)
            }
    }

    private fun imageCell(value: String): JSONObject {
        return JSONObject()
            .put("type", "image")
            .put("value", value)
    }

}
