package os.kei.ui.page.main.student.model3d

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
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
        assertEquals("ch0258_02", normalizeModelDevelopmentId("ＣＨ＿２５８＿２"))
        assertEquals("shiroko_ridingsuit", normalizeModelDevelopmentId("Shiroko_RidingSuit"))
        assertNull(normalizeModelDevelopmentId("10135"))
        assertNull(normalizeModelDevelopmentId("../../CH0335"))
    }
    @Test fun `legacy resource Original aliases match only explicit reviewed defaults`() {
        assertEquals("Hina.glb", index.resolve(guide(59934, BaGuideRow("DevName", "Hina_Original")))?.defaultFile)
        assertEquals("Arisu.glb", index.resolve(guide(72904, BaGuideRow("DevName", "Aris_Original")))?.defaultFile)
        assertEquals("Hifumi.glb", index.resolve(guide(66256, BaGuideRow("DevName", "Hihumi_Original")))?.defaultFile)
        assertEquals("Karin.glb", index.resolve(guide(67011, BaGuideRow("DevName", "Karin_Original")))?.defaultFile)
        assertNull(index.resolve(guide(83596, BaGuideRow("DevName", "Hihumi_Original"))))
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
    @Test fun `initial reviewed article defaults remain distinct across costume families`() {
        val expected = mapOf(690582L to "Kei.glb", 59934L to "Hina.glb", 83729L to "Hina (Swimsuit).glb", 611753L to "Hina (Dress).glb",
            67658L to "Yuuka.glb", 170295L to "Yuuka (Sportswear).glb", 643617L to "Yuuka (Pajama).glb",
            72904L to "Arisu.glb", 589642L to "Arisu (Maid).glb", 690586L to "Arisu (Battle).glb",
            46677L to "Shiroko.glb", 162557L to "Shiroko＊Terror.glb")
        expected.forEach { (id, file) -> assertEquals(file, index.resolve(guide(id))?.defaultFile) }
    }
    @Test fun `dual combat roles share the model but retain different game identities`() {
        val tank = assertNotNull(index.resolve(guide(621572, BaGuideRow("DevName", "CH0258_02"))))
        val attacker = assertNotNull(index.resolve(guide(597535, BaGuideRow("DevName", "CH0258_01"))))
        assertEquals("Hoshino (Battle).glb", tank.defaultFile)
        assertEquals(tank.defaultFile, attacker.defaultFile)
        assertEquals(10098, tank.characterId)
        assertEquals(10099, attacker.characterId)
        assertNull(index.resolve(guide(621572, BaGuideRow("DevName", "CH0258_01"))))
        assertNull(index.resolve(guide(999999, BaGuideRow("DevName", "CH0258"))))
    }
    @Test fun `adult and kid swimsuit forms cannot collapse to a shared given name`() {
        assertEquals("Shun (Swimsuit) (1).glb", index.resolve(guide(709616))?.defaultFile)
        assertEquals("Shun (Swimsuit) (2).glb", index.resolve(guide(709617))?.defaultFile)
        assertNull(index.resolve(guide(709617, BaGuideRow("DevName", "CH0355_01"))))
    }
    @Test fun `legacy costumes collaborations and translated names retain reviewed defaults`() {
        for ((id, file) in mapOf(150220L to "Aru (New Year).glb", 85351L to "Shiroko (Riding).glb",
            90749L to "Hatsune Miku.glb", 591005L to "Otogi.glb", 674784L to "Rena.glb",
            71734L to "Reijo.glb", 645973L to "Akane (School Uniform).glb", 667849L to "Kikyou (Swimsuit).glb")) {
            assertEquals(file, index.resolve(guide(id))?.defaultFile)
        }
        assertEquals("Hifumi (Swimsuit).glb", index.resolve(guide(83596, BaGuideRow("DevName", "Hihumi_Swimsuit")))?.defaultFile)
        assertNull(index.resolve(guide(83596, BaGuideRow("DevName", "Hihumi_default"))))
    }
    @Test fun `articles without an upstream model do not borrow the ordinary costume`() {
        for (id in listOf(714062L, 714033L, 714055L, 714037L, 714029L, 671482L, 718266L, 718265L, 721663L, 721664L, 620940L)) {
            assertNull(index.resolve(guide(id)))
        }
    }
    @Test fun `reviewed skill and halo resources are selectable without becoming student defaults`() {
        assertTrue(index.resolve(guide(667849))!!.models.any { it.file == "sm030001.glb" })
        assertTrue(index.resolve(guide(680454))!!.models.any { it.file == "sm032601.glb" })
        val momoi = assertNotNull(index.resolve(guide(68801)))
        assertEquals("Momoi.glb", momoi.defaultFile)
        assertTrue(momoi.models.any { it.file == "st0004.glb" })
        assertTrue(index.resolve(guide(68802))!!.models.any { it.file == "st0005.glb" })
        assertFalse(momoi.models.any { it.file.startsWith("ladies_biker") })
    }
    @Test fun `every reviewed identity resolves through article development and character IDs`() {
        val review = File("../scripts/ba/model_identity_bindings.json").takeIf { it.exists() }
            ?: File("scripts/ba/model_identity_bindings.json")
        val bindings = JSONObject(review.readText()).getJSONArray("bindings")
        assertEquals(267, bindings.length())
        for (n in 0 until bindings.length()) {
            val b = bindings.getJSONObject(n)
            val article = assertNotNull(index.resolve(guide(b.getLong("gameKeeContentId"))))
            assertEquals(b.getString("defaultFile"), article.defaultFile)
            assertEquals(article, index.resolve(guide(999999, BaGuideRow("DevName", b.getString("developmentId")))))
            assertEquals(article, index.resolve(guide(999999, BaGuideRow("CharacterId", b.getInt("characterId").toString()))))
            val aliases = b.optJSONArray("developmentAliases")
            if (aliases != null) for (i in 0 until aliases.length()) {
                assertEquals(article, index.resolve(guide(999999, BaGuideRow("DevName", aliases.getString(i)))))
            }
        }
    }
}
