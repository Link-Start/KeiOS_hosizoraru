package os.kei.ui.page.main.student.tabcontent.profile

import org.junit.Test
import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.BaStudentGuideInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuideNpcSatelliteProfileStateTest {
    @Test
    fun `npc profile merges repeated affiliation and removes media rows`() {
        val state =
            buildGuideNpcSatelliteProfileState(
                guide =
                    npcGuide(
                        title = "阿罗娜",
                        rows =
                            listOf(
                                BaGuideRow("角色名称", "阿罗娜"),
                                BaGuideRow("所属学园", "联邦学生会"),
                                BaGuideRow("社团", "什亭之匣"),
                                BaGuideRow("首次登场日期", "主线剧情"),
                                BaGuideRow("个人简介", "联邦搜查部的系统管理员。"),
                                BaGuideRow("影画相关链接", "https://example.com/gallery"),
                                BaGuideRow("图片", "https://example.com/image.png"),
                            ),
                    ),
            )

        assertTrue(state.aliasRows.none { it.key == "角色名称" })
        assertEquals(listOf("所属", "首次登场"), state.identityRows.map { it.key })
        assertEquals("联邦学生会 / 什亭之匣", state.identityRows.first().value)
        assertEquals(listOf("介绍"), state.introRows.map { it.key })
        assertTrue(state.normalRows.isEmpty())
    }

    @Test
    fun `npc profile keeps npc-specific identity order`() {
        val state =
            buildGuideNpcSatelliteProfileState(
                guide =
                    npcGuide(
                        title = "凛",
                        rows =
                            listOf(
                                BaGuideRow("声优", "大原沙耶香"),
                                BaGuideRow("所属", "联邦学生会"),
                                BaGuideRow("身高", "171cm"),
                                BaGuideRow("生日", "11月1日"),
                                BaGuideRow("黑话(别名)", "会长代理"),
                            ),
                    ),
            )

        assertEquals(listOf("黑话"), state.aliasRows.map { it.key })
        assertEquals(listOf("所属", "声优", "生日", "身高"), state.identityRows.map { it.key })
    }

    private fun npcGuide(
        title: String,
        rows: List<BaGuideRow>,
    ): BaStudentGuideInfo =
        BaStudentGuideInfo(
            sourceUrl = "https://www.gamekee.com/ba/${title.hashCode()}.html",
            title = title,
            subtitle = "GameKee",
            description = "",
            imageUrl = "",
            summary = "",
            stats = emptyList(),
            profileRows = rows,
            syncedAtMs = rows.hashCode().toLong(),
        )
}
