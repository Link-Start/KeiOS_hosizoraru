package os.kei.ui.page.main.student.tabcontent.profile

import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.profileRowsForDisplay
import os.kei.ui.page.main.student.shouldHideMovedHeaderRow
import os.kei.ui.page.main.student.tabcontent.isGrowthTitleVoiceRow
import os.kei.ui.page.main.student.tabcontent.isVoicePlaceholderRow
import java.util.LinkedHashMap

private const val NPC_PROFILE_STATE_CACHE_MAX_SIZE = 96

private val npcProfileStateCache =
    object : LinkedHashMap<String, GuideNpcSatelliteProfileState>(
        NPC_PROFILE_STATE_CACHE_MAX_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GuideNpcSatelliteProfileState>?): Boolean =
            size > NPC_PROFILE_STATE_CACHE_MAX_SIZE
    }

internal data class GuideNpcSatelliteProfileState(
    val aliasRows: List<BaGuideRow>,
    val identityRows: List<BaGuideRow>,
    val introRows: List<BaGuideRow>,
    val normalRows: List<BaGuideRow>,
    val relatedRoleItems: List<SameNameRoleItem>,
    val relatedRoleHint: String,
    val relatedRoleTitle: String,
    val sameNameRoleItems: List<SameNameRoleItem>,
    val sameNameRoleHint: String,
    val sameNameRoleTitle: String,
)

internal fun buildGuideNpcSatelliteProfileState(guide: BaStudentGuideInfo): GuideNpcSatelliteProfileState {
    val cacheKey = guide.npcProfileStateCacheKey()
    synchronized(npcProfileStateCache) {
        npcProfileStateCache[cacheKey]?.let { return it }
    }

    val baseRows =
        guide
            .profileRowsForDisplay()
            .filterNot(::isGrowthTitleVoiceRow)
            .filterNot(::isVoicePlaceholderRow)
            .filterNot(::isProfileSectionHeaderRow)
            .filterNot(::isGalleryRelatedProfileLinkRow)
            .filterNot { row ->
                val key = normalizeProfileFieldKey(row.key)
                shouldHideMovedHeaderRow(row) && !isNpcAffiliationKey(key)
            }

    val relatedRows = baseRows.filter { row ->
        val key = normalizeProfileFieldKey(row.key)
        key == relatedRoleHeaderKey || key == relatedRoleNameRowKey
    }
    val sameNameRows = baseRows.filter(::isSameNameRoleRow)
    val relatedRoleItems = buildRelatedRoleItems(relatedRows)
    val relatedRoleHint =
        relatedRows
            .firstNotNullOfOrNull { row ->
                extractRelatedRoleHint(row)
            }.orEmpty()
    val relatedRoleTitle = extractRelatedRoleSectionTitle(relatedRows)
    val sameNameRoleItems = buildSameNameRoleItems(sameNameRows)
    val sameNameRoleHint =
        sameNameRows
            .firstNotNullOfOrNull { row ->
                extractSameNameRoleHint(row)
            }.orEmpty()
    val sameNameRoleTitle = extractSameNameRoleSectionTitle(sameNameRows)

    val availableRows =
        baseRows
            .filterNot(::isRelatedRoleRow)
            .mapNotNull(::cleanNpcProfileRow)
            .filterNot(::isNpcMediaIndexRow)
            .distinctBy(::npcRowExactDedupeKey)

    val aliasRows =
        availableRows
            .filter { row -> isNpcAliasRow(row) }
            .filterNot { row -> isNpcTitleEchoRow(guide, row) }
            .map(::normalizeNpcAliasRow)
            .sortedWith(npcAliasRowComparator)
            .let(::mergeNpcRowsByKey)
    val identityRows =
        availableRows
            .filter { row -> isNpcIdentityRow(row) && !isNpcAliasRow(row) }
            .map(::normalizeNpcIdentityRow)
            .sortedWith(npcIdentityRowComparator)
            .let(::mergeNpcRowsByKey)
    val introRows =
        availableRows
            .filter { row -> isNpcIntroRow(row) }
            .map(::normalizeNpcIntroRow)
            .let(::mergeNpcRowsByKey)
    val normalRows =
        availableRows
            .filterNot { row -> isNpcAliasRow(row) || isNpcIdentityRow(row) || isNpcIntroRow(row) }
            .map(::normalizeNpcNormalRow)
            .let(::mergeNpcRowsByKey)

    val computed =
        GuideNpcSatelliteProfileState(
            aliasRows = aliasRows,
            identityRows = identityRows,
            introRows = introRows,
            normalRows = normalRows,
            relatedRoleItems = relatedRoleItems,
            relatedRoleHint = relatedRoleHint,
            relatedRoleTitle = relatedRoleTitle,
            sameNameRoleItems = sameNameRoleItems,
            sameNameRoleHint = sameNameRoleHint,
            sameNameRoleTitle = sameNameRoleTitle,
        )
    synchronized(npcProfileStateCache) {
        npcProfileStateCache[cacheKey] = computed
    }
    return computed
}

internal fun cleanNpcProfileRow(row: BaGuideRow): BaGuideRow? {
    val cleanedValue = sanitizeProfileFieldValue(row.key, row.value)
    val hasImage = row.imageUrl.isNotBlank() || row.imageUrls.any { it.isNotBlank() }
    if (isProfileInstructionPlaceholder(row.value) && isProfileValuePlaceholder(cleanedValue)) return null
    if (isProfileValuePlaceholder(cleanedValue) && !hasImage) return null
    return row.copy(value = cleanedValue)
}

private fun isNpcAliasRow(row: BaGuideRow): Boolean {
    val key = normalizeProfileFieldKey(row.key)
    return listOf(
        "角色名称",
        "全名",
        "日语全名",
        "假名注音",
        "假名注明",
        "繁中译名",
        "简中译名",
        "其他译名",
        "黑话",
        "黑话(别名)",
        "别名",
    ).any { key == normalizeProfileFieldKey(it) }
}

private fun isNpcAffiliationKey(normalizedKey: String): Boolean =
    listOf(
        "所属",
        "所属学园",
        "所属学院",
        "学园",
        "学院",
        "所属社团",
        "社团",
    ).any { normalizedKey == normalizeProfileFieldKey(it) }

private fun isNpcTitleEchoRow(
    guide: BaStudentGuideInfo,
    row: BaGuideRow,
): Boolean {
    val key = normalizeProfileFieldKey(row.key)
    if (key != normalizeProfileFieldKey("角色名称")) return false
    val title = guide.title.trim()
    if (title.isBlank()) return false
    return row.value.trim() == title
}

private fun isNpcIdentityRow(row: BaGuideRow): Boolean {
    val key = normalizeProfileFieldKey(row.key)
    return listOf(
        "所属",
        "所属学园",
        "所属学院",
        "学园",
        "学院",
        "所属社团",
        "社团",
        "身份",
        "职务",
        "职位",
        "年级",
        "年龄",
        "生日",
        "身高",
        "声优",
        "画师",
        "原画师",
        "设计",
        "设计师",
        "实装日期",
        "首次登场日期",
        "首次登场",
        "登场",
        "角色考据",
    ).any { key == normalizeProfileFieldKey(it) }
}

private fun isNpcMediaIndexRow(row: BaGuideRow): Boolean {
    val key = normalizeProfileFieldKey(row.key)
    val value = row.value.trim()
    val mediaKeyTokens =
        listOf(
            "影画相关链接",
            "相关链接",
            "来源链接",
            "个人账号主页",
            "账号主页",
            "个人主页",
            "主页链接",
            "主页",
            "巧克力",
            "巧克力简介",
            "互动家具",
            "互动家具简介",
            "设定图",
            "设定集",
            "立绘",
            "差分",
            "表情差分",
        ).map(::normalizeProfileFieldKey)
    if (mediaKeyTokens.any { token -> token.isNotBlank() && key.contains(token) }) return true
    if (key == normalizeProfileFieldKey("图片")) return true
    return value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true)
}

private fun isNpcIntroRow(row: BaGuideRow): Boolean {
    val key = normalizeProfileFieldKey(row.key)
    return listOf(
        "个人简介",
        "简介",
        "介绍",
        "MomoTalk状态消息",
        "Momotalk状态消息",
    ).any { key == normalizeProfileFieldKey(it) }
}

private fun normalizeNpcIntroRow(row: BaGuideRow): BaGuideRow {
    val normalizedKey = normalizeProfileFieldKey(row.key)
    if (normalizedKey == normalizeProfileFieldKey("个人简介")) {
        return row.copy(key = "介绍")
    }
    return row
}

private fun normalizeNpcAliasRow(row: BaGuideRow): BaGuideRow {
    val normalizedKey = normalizeProfileFieldKey(row.key)
    val displayKey =
        when (normalizedKey) {
            normalizeProfileFieldKey("假名注明") -> "假名注音"

            normalizeProfileFieldKey("黑话(别名)"),
            normalizeProfileFieldKey("别名"),
            -> "黑话"

            else -> row.key
        }
    return row.copy(key = displayKey)
}

private fun normalizeNpcIdentityRow(row: BaGuideRow): BaGuideRow {
    val normalizedKey = normalizeProfileFieldKey(row.key)
    val displayKey =
        when {
            isNpcAffiliationKey(normalizedKey) -> "所属"

            normalizedKey == normalizeProfileFieldKey("职位") -> "职务"

            normalizedKey == normalizeProfileFieldKey("首次登场日期") ||
                normalizedKey == normalizeProfileFieldKey("登场") -> "首次登场"

            normalizedKey == normalizeProfileFieldKey("原画师") -> "画师"

            normalizedKey == normalizeProfileFieldKey("设计师") -> "设计"

            else -> row.key
        }
    return row.copy(key = displayKey)
}

private fun normalizeNpcNormalRow(row: BaGuideRow): BaGuideRow {
    val normalizedKey = normalizeProfileFieldKey(row.key)
    val displayKey =
        when (normalizedKey) {
            normalizeProfileFieldKey("Momotalk状态消息") -> "MomoTalk状态消息"
            else -> row.key
        }
    return row.copy(key = displayKey)
}

private fun mergeNpcRowsByKey(rows: List<BaGuideRow>): List<BaGuideRow> {
    val merged = LinkedHashMap<String, BaGuideRow>()
    rows.forEach { row ->
        val key = normalizeProfileFieldKey(row.key)
        val value = row.value.trim()
        val existing = merged[key]
        if (existing == null) {
            merged[key] = row.copy(value = value)
            return@forEach
        }
        val mergedValue = mergeNpcRowValues(existing.value, value)
        val mergedImageUrls = (existing.imageUrls + row.imageUrls).filter { it.isNotBlank() }.distinct()
        merged[key] =
            existing.copy(
                value = mergedValue,
                imageUrl = existing.imageUrl.ifBlank { row.imageUrl },
                imageUrls = mergedImageUrls,
            )
    }
    return merged.values.toList()
}

private fun mergeNpcRowValues(
    first: String,
    second: String,
): String {
    val values =
        (splitNpcDisplayValues(first) + splitNpcDisplayValues(second))
            .filter { it.isNotBlank() }
            .distinctBy { it.replace(" ", "").replace("　", "") }
    return values.joinToString(" / ")
}

private fun splitNpcDisplayValues(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return raw
        .replace("／", "/")
        .replace("｜", "/")
        .split("/")
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun npcRowExactDedupeKey(row: BaGuideRow): String =
    "${normalizeProfileFieldKey(row.key)}|${row.value.trim()}|${row.imageUrl.trim()}"

private val npcAliasRowOrder =
    listOf(
        "全名",
        "日语全名",
        "假名注音",
        "假名注明",
        "其他译名",
        "繁中译名",
        "简中译名",
        "黑话",
        "黑话(别名)",
        "别名",
        "角色名称",
    ).mapIndexed { index, key -> normalizeProfileFieldKey(key) to index }.toMap()

private val npcIdentityRowOrder =
    listOf(
        "所属",
        "所属学园",
        "所属学院",
        "学园",
        "学院",
        "所属社团",
        "社团",
        "身份",
        "职务",
        "职位",
        "首次登场",
        "首次登场日期",
        "声优",
        "生日",
        "年龄",
        "身高",
        "年级",
        "画师",
        "原画师",
        "设计",
        "设计师",
        "角色考据",
        "实装日期",
        "登场",
    ).mapIndexed { index, key -> normalizeProfileFieldKey(key) to index }.toMap()

private val npcAliasRowComparator =
    compareBy<BaGuideRow> { row -> npcAliasRowOrder[normalizeProfileFieldKey(row.key)] ?: Int.MAX_VALUE }
        .thenBy { row -> normalizeProfileFieldKey(row.key) }

private val npcIdentityRowComparator =
    compareBy<BaGuideRow> { row -> npcIdentityRowOrder[normalizeProfileFieldKey(row.key)] ?: Int.MAX_VALUE }
        .thenBy { row -> normalizeProfileFieldKey(row.key) }

private fun BaStudentGuideInfo.npcProfileStateCacheKey(): String =
    buildString {
        append(sourceUrl.trim().ifBlank { title.trim() })
        append('|')
        append(syncedAtMs)
        append('|')
        append(profileRows.size)
        append('|')
        append(galleryItems.size)
        append('|')
        append(stats.size)
    }
