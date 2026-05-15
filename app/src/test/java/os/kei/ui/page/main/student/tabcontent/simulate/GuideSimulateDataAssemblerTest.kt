package os.kei.ui.page.main.student.tabcontent.simulate

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.ui.page.main.student.BaGuideRow

class GuideSimulateDataAssemblerTest {
    @Test
    fun `weapon rows hide zero blank instruction values`() {
        val data = buildGuideSimulateData(
            listOf(
                BaGuideRow(key = "专武", value = "*Lv60"),
                BaGuideRow(key = "攻击力", value = "831"),
                BaGuideRow(key = "生命值", value = "2684"),
                BaGuideRow(key = "治愈力", value = "*若0,格留空")
            )
        )

        assertEquals(2, data.weaponRows.size)
        assertTrue(data.weaponRows.any { it.key == "攻击力" && it.value == "831" })
        assertTrue(data.weaponRows.any { it.key == "生命值" && it.value == "2684" })
        assertFalse(data.weaponRows.any { it.value.contains("留空") })
    }

    @Test
    fun `weapon rows keep real zero values`() {
        val data = buildGuideSimulateData(
            listOf(
                BaGuideRow(key = "专武", value = "Lv60"),
                BaGuideRow(key = "攻击力", value = "0")
            )
        )

        assertEquals(listOf(BaGuideRow(key = "攻击力", value = "0")), data.weaponRows)
    }
}
