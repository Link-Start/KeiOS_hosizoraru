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
    val sameNameRoleItems: List<SameNameRoleItem>,
    val sameNameRoleHint: String,
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
                shouldHideMovedHeaderRow(row) && key != normalizeProfileFieldKey("所属")
            }

    val sameNameRows = baseRows.filter(::isSameNameRoleRow)
    val sameNameRoleItems = buildSameNameRoleItems(sameNameRows)
    val sameNameRoleHint =
        sameNameRows
            .firstNotNullOfOrNull { row ->
                extractSameNameRoleHint(row)
            }.orEmpty()

    val availableRows =
        baseRows
            .filterNot(::isSameNameRoleRow)
            .mapNotNull(::cleanNpcProfileRow)
            .distinctBy { row -> "${normalizeProfileFieldKey(row.key)}|${row.value.trim()}|${row.imageUrl.trim()}" }

    val aliasRows = availableRows.filter { row -> isNpcAliasRow(row) }
    val identityRows = availableRows.filter { row -> isNpcIdentityRow(row) && !isNpcAliasRow(row) }
    val introRows = availableRows.filter { row -> isNpcIntroRow(row) }
    val groupedRows = (aliasRows + identityRows + introRows).toSet()
    val normalRows = availableRows.filterNot { row -> row in groupedRows }

    val computed =
        GuideNpcSatelliteProfileState(
            aliasRows = aliasRows,
            identityRows = identityRows,
            introRows = introRows,
            normalRows = normalRows,
            sameNameRoleItems = sameNameRoleItems,
            sameNameRoleHint = sameNameRoleHint,
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

private fun isNpcIdentityRow(row: BaGuideRow): Boolean {
    val key = normalizeProfileFieldKey(row.key)
    return listOf(
        "所属",
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
