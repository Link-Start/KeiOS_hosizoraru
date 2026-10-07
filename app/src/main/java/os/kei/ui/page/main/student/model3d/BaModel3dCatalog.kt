package os.kei.ui.page.main.student.model3d

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.compose.runtime.Immutable
import org.json.JSONObject
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl
import os.kei.ui.page.main.student.gameKeeMemoryLobbyViewerUrl
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

@Immutable
internal data class BaModel3dAsset(val label: String, val file: String, val bytes: Long, val gitBlob: String, val url: String)

@Immutable
internal data class BaModel3dResource(
    val contentId: Long,
    val characterId: Int,
    val developmentId: String,
    val wikiPage: String,
    val defaultFile: String,
    val models: List<BaModel3dAsset>,
) {
    val sourceUrl: String get() = "https://bluearchive.wiki/wiki/" + encodeModelPath(wikiPage) + "/gallery"
}

internal fun encodeModelPath(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

/** Prefixes remain distinct. CharacterId, GameKee contentId and resource development IDs are different namespaces. */
internal fun normalizeModelDevelopmentId(raw: String): String? {
    val value = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC)
    Regex("(?i)^(CH|NP)[ _-]?(\\d{1,4})$").matchEntire(value)?.let {
        return it.groupValues[1].lowercase(Locale.ROOT) + it.groupValues[2].padStart(4, '0')
    }
    return value.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9]*(?:_(?:default|Original))?", RegexOption.IGNORE_CASE)) }
        ?.lowercase(Locale.ROOT)
}

internal class BaModel3dIndex private constructor(private val resources: List<BaModel3dResource>, private val aliases: Map<String, String>) {
    fun forContentId(id: Long): BaModel3dResource? = resources.singleOrNull { it.contentId == id }

    fun resolve(guide: BaStudentGuideInfo): BaModel3dResource? {
        if (gameKeeMemoryLobbyViewerUrl(guide.sourceUrl).isBlank()) return null
        val contentId = extractGuideContentIdFromUrl(guide.sourceUrl) ?: return null
        val byContent = forContentId(contentId)
        val rows = guide.profileRows.map { it.key to it.value } + guide.stats
        val devRows = rows.filter { (key, _) -> key.trim().lowercase(Locale.ROOT) in DEV_KEYS }
        if (devRows.any { normalizeModelDevelopmentId(it.second) == null }) return null
        val devValues = devRows.mapNotNull { normalizeModelDevelopmentId(it.second) }.map { aliases[it] ?: it }.distinct()
        if (devValues.size > 1) return null
        val byDev = devValues.singleOrNull()?.let { dev -> resources.singleOrNull { normalizeModelDevelopmentId(it.developmentId) == dev } }
        if (devValues.isNotEmpty() && byContent != null && byDev?.developmentId != byContent.developmentId) return null
        // An imported guide cannot silently reinterpret an explicitly conflicting game identity.
        val characterRows = rows.filter { (key, _) -> key.trim().lowercase(Locale.ROOT) in CHARACTER_KEYS }
        val characterIds = characterRows.mapNotNull { it.second.trim().toIntOrNull() }.distinct()
        if (characterIds.size > 1 || characterRows.size != characterRows.count { it.second.trim().toIntOrNull() != null }) return null
        val byCharacter = characterIds.singleOrNull()?.let { id -> resources.singleOrNull { it.characterId == id } }
        val selected = byContent ?: byDev ?: byCharacter
        if (devValues.isNotEmpty() && byDev != selected) return null
        if (characterIds.isNotEmpty() && selected?.characterId != characterIds.single()) return null
        return selected
    }

    companion object {
        private val DEV_KEYS = setOf("开发id", "开发编号", "资源id", "devname", "developmentid")
        private val CHARACTER_KEYS = setOf("角色id", "学生id", "characterid")

        fun parse(text: String): BaModel3dIndex {
            val root = JSONObject(text)
            require(root.getInt("schema") == 1)
            val revision = root.getString("revision").also { require(it.matches(Regex("[0-9a-f]{40}"))) }
            val groups = root.getJSONArray("groups")
            val assets = buildMap<String, List<BaModel3dAsset>> {
                for (i in 0 until groups.length()) {
                    val group = groups.getJSONObject(i)
                    val models = group.getJSONArray("models")
                    val name = group.getString("name")
                    require(!containsKey(name) && models.length() > 0)
                    put(name, List(models.length()) { n ->
                        val m = models.getJSONObject(n)
                        val file = m.getString("file")
                        require(file.endsWith(".glb") && '/' !in file && '\\' !in file && file.length < 160)
                        val bytes = m.getLong("bytes").also { require(it in 20..(32L * 1024 * 1024)) }
                        val blob = m.getString("gitBlob").also { require(it.matches(Regex("[0-9a-f]{40}"))) }
                        BaModel3dAsset(m.getString("name"), file, bytes, blob,
                            "https://cdn.jsdelivr.net/gh/lihaohong6/BlueArchiveModels@$revision/${encodeModelPath(file)}")
                    })
                }
            }
            val bindings = root.getJSONArray("bindings")
            val aliases = mutableMapOf<String, String>()
            val resources = List(bindings.length()) { n ->
                val b = bindings.getJSONObject(n)
                val dev = b.getString("developmentId")
                val normalized = requireNotNull(normalizeModelDevelopmentId(dev))
                val extra = b.optJSONArray("developmentAliases")
                if (extra != null) for (i in 0 until extra.length()) {
                    val alias = requireNotNull(normalizeModelDevelopmentId(extra.getString(i)))
                    require(aliases[alias] == null || aliases[alias] == normalized)
                    aliases[alias] = normalized
                }
                val models = assets.getValue(b.getString("group"))
                val default = b.getString("defaultFile").also { require(models.any { m -> m.file == it }) }
                val contentId = b.getLong("gameKeeContentId").also { require(it > 0) }
                val characterId = b.getInt("characterId").also { require(it > 0) }
                BaModel3dResource(contentId, characterId, dev, b.getString("wikiPage"), default, models)
            }
            require(resources.map { it.contentId }.distinct().size == resources.size)
            require(resources.map { it.characterId }.distinct().size == resources.size)
            require(resources.map { normalizeModelDevelopmentId(it.developmentId) }.distinct().size == resources.size)
            for (r in resources) { val dev = normalizeModelDevelopmentId(r.developmentId); require(aliases[dev] == null || aliases[dev] == dev) }
            return BaModel3dIndex(resources, aliases)
        }
    }
}

internal object BaModel3dCatalog {
    @Volatile private var index: BaModel3dIndex? = null
    @WorkerThread @Synchronized fun load(context: Context): BaModel3dIndex = index ?: context.assets.open("ba3d/catalog.json")
        .bufferedReader().use { BaModel3dIndex.parse(it.readText()) }.also { index = it }
}
