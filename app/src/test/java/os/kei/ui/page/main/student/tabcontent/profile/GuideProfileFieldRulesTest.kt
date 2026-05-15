package os.kei.ui.page.main.student.tabcontent.profile

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import os.kei.ui.page.main.student.BaGuideRow

class GuideProfileFieldRulesTest {
    @Test
    fun `momotalk rows use full width layout`() {
        assertTrue(shouldUseFullWidthProfileInfoRow("MomoTalk状态消息"))
        assertTrue(shouldUseFullWidthProfileInfoRow("MomoTalk解锁等级"))
        assertFalse(shouldStackProfileInfoRow("MomoTalk解锁等级", "2 / 3 / 5 / 6"))
        assertFalse(shouldStackProfileInfoRow("MomoTalk状态消息", "-"))
        assertTrue(
            shouldStackProfileInfoRow(
                key = "MomoTalk状态消息",
                value = "为了参加派对换上了礼服裙，所属于格黑娜学园的风纪委员长。"
            )
        )
    }

    @Test
    fun `gift preference keeps primary gift image and attitude emoji`() {
        val items = buildGiftPreferenceItems(
            listOf(
                BaGuideRow(
                    key = "礼物偏好礼物1",
                    value = "",
                    imageUrl = "https://cdn.example.com/gift-ticket.png",
                    imageUrls = listOf(
                        "https://cdn.example.com/gift-ticket.png",
                        "https://cdn.example.com/gift-emoji-love.gif"
                    )
                )
            )
        )

        assertEquals(1, items.size)
        assertEquals("https://cdn.example.com/gift-ticket.png", items.single().giftImageUrl)
        assertEquals("https://cdn.example.com/gift-emoji-love.gif", items.single().emojiImageUrl)
        assertEquals("礼物1", items.single().label)
    }

    @Test
    fun `gift preference falls back to non emoji candidate as primary image`() {
        val items = buildGiftPreferenceItems(
            listOf(
                BaGuideRow(
                    key = "礼物偏好礼物2",
                    value = "电影票",
                    imageUrls = listOf(
                        "https://cdn.example.com/gift-emoji-like.gif",
                        "https://cdn.example.com/movie-ticket.png"
                    )
                )
            )
        )

        assertEquals(1, items.size)
        assertEquals("https://cdn.example.com/movie-ticket.png", items.single().giftImageUrl)
        assertEquals("https://cdn.example.com/gift-emoji-like.gif", items.single().emojiImageUrl)
        assertEquals("电影票", items.single().label)
    }
}
