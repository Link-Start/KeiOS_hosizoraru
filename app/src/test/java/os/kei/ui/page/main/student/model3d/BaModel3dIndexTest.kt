package os.kei.ui.page.main.student.model3d

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.BaStudentGuideInfo
import java.io.File
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class BaModel3dIndexTest {
    private val catalog = File("src/main/assets/ba3d/catalog.json").takeIf { it.exists() }
        ?: File("app/src/main/assets/ba3d/catalog.json")
    private val index = BaModel3dIndex.parse(catalog.readText())
    private fun guide(id: Long, vararg rows: BaGuideRow) = BaStudentGuideInfo(
        "https://www.gamekee.com/ba/$id?tab=3", "Name may be localized", "", "", "", "", emptyList(),
        profileRows = rows.toList(), syncedAtMs = 0,
    )

    @Test fun `verified ordinary costume and special students select their exact default models`() {
        for ((id, file) in listOf(690582L to "Kei.glb", 59934L to "Hina.glb", 170295L to "Yuuka (Sportswear).glb", 162557L to "Shiroko＊Terror.glb", 72904L to "Arisu.glb")) {
            val resource = assertNotNull(index.resolve(guide(id)))
            assertEquals(file, resource.defaultFile)
            assertTrue(resource.models.any { it.file == file })
        }
    }
    @Test fun `development ID format changes preserve prefix and padded identity`() {
        assertEquals("ch0335", normalizeModelDevelopmentId("ＣＨ＿０３３５"))
        assertEquals("ch0335", normalizeModelDevelopmentId("ch-335"))
        assertEquals("np0269", normalizeModelDevelopmentId("NP0269"))
        assertNull(normalizeModelDevelopmentId("10135"))
        assertNull(normalizeModelDevelopmentId("../../CH0335"))
    }
    @Test fun `legacy resource Original aliases match only explicit reviewed defaults`() {
        assertEquals("Hina.glb", index.resolve(guide(59934, BaGuideRow("DevName", "Hina_Original")))?.defaultFile)
        assertEquals("Arisu.glb", index.resolve(guide(72904, BaGuideRow("DevName", "Aris_Original")))?.defaultFile)
    }
    @Test fun `conflicting identities never silently select another student or costume`() {
        assertNull(index.resolve(guide(170295, BaGuideRow("开发ID", "CH0335"))))
        assertNull(index.resolve(guide(690582, BaGuideRow("CharacterId", "10053"))))
        assertNull(index.resolve(guide(690582, BaGuideRow("开发ID", "invalid/value"))))
        assertNull(index.resolve(guide(690582, BaGuideRow("开发ID", "CH0335"), BaGuideRow("DevName", "CH0263"))))
    }
    @Test fun `names alone and foreign URLs do not create a model association`() {
        assertNull(index.resolve(guide(999999).copy(title = "Kei")))
        assertNull(index.resolve(guide(690582).copy(sourceUrl = "https://example.com/ba/690582")))
    }
    @Test fun `pinned paths retain non ASCII punctuation and reject traversal in registry`() {
        val asset = index.forContentId(162557)!!.models.first()
        assertTrue(asset.url.contains("Shiroko%EF%BC%8ATerror.glb"))
        assertFails { BaModel3dIndex.parse(catalog.readText().replace("Kei.glb", "../Kei.glb")) }
    }
    @Test fun `an explicit game identity can resolve a renamed article without fuzzy names`() {
        assertEquals("Yuuka (Sportswear).glb", index.resolve(guide(999999, BaGuideRow("DevName", "CH-184")))?.defaultFile)
        assertEquals("Yuuka (Sportswear).glb", index.resolve(guide(999999, BaGuideRow("CharacterId", "10053")))?.defaultFile)
        assertNull(index.resolve(guide(999999, BaGuideRow("DevName", "NP0184"), BaGuideRow("CharacterId", "10053"))))
    }
    @Test fun `all twelve reviewed article defaults remain distinct across costume families`() {
        val expected = mapOf(690582L to "Kei.glb", 59934L to "Hina.glb", 83729L to "Hina (Swimsuit).glb", 611753L to "Hina (Dress).glb",
            67658L to "Yuuka.glb", 170295L to "Yuuka (Sportswear).glb", 643617L to "Yuuka (Pajama).glb",
            72904L to "Arisu.glb", 589642L to "Arisu (Maid).glb", 690586L to "Arisu (Battle).glb",
            46677L to "Shiroko.glb", 162557L to "Shiroko＊Terror.glb")
        expected.forEach { (id, file) -> assertEquals(file, index.resolve(guide(id))?.defaultFile) }
    }
}
