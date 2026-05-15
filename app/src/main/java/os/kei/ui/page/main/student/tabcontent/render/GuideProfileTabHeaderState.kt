package os.kei.ui.page.main.student.tabcontent.render

import os.kei.ui.page.main.student.BaGuideGalleryItem
import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.hasRenderableGalleryMedia
import os.kei.ui.page.main.student.isChocolateGalleryItem
import os.kei.ui.page.main.student.isInteractiveFurnitureGalleryItem
import os.kei.ui.page.main.student.profileRowsForDisplay
import os.kei.ui.page.main.student.shouldHideMovedHeaderRow
import os.kei.ui.page.main.student.tabcontent.isGrowthTitleVoiceRow
import os.kei.ui.page.main.student.tabcontent.isVoicePlaceholderRow
import os.kei.ui.page.main.student.tabcontent.profile.GiftPreferenceItem
import os.kei.ui.page.main.student.tabcontent.profile.SameNameRoleItem
import os.kei.ui.page.main.student.tabcontent.profile.buildGiftPreferenceItems
import os.kei.ui.page.main.student.tabcontent.profile.buildProfileCardRows
import os.kei.ui.page.main.student.tabcontent.profile.buildSameNameRoleItems
import os.kei.ui.page.main.student.tabcontent.profile.extractSameNameRoleHint
import os.kei.ui.page.main.student.tabcontent.profile.isGiftPreferenceProfileRow
import os.kei.ui.page.main.student.tabcontent.profile.isGalleryRelatedProfileLinkRow
import os.kei.ui.page.main.student.tabcontent.profile.isProfileSectionHeaderRow
import os.kei.ui.page.main.student.tabcontent.profile.isSameNameRoleRow
import os.kei.ui.page.main.student.tabcontent.profile.isSkillMigratedProfileRow
import os.kei.ui.page.main.student.tabcontent.profile.isStructuredProfileCardRow
import os.kei.ui.page.main.student.tabcontent.profile.normalizeProfileFieldKey
import os.kei.ui.page.main.student.tabcontent.profile.profileHobbyFieldSpecs
import os.kei.ui.page.main.student.tabcontent.profile.profileNicknameFieldSpecs
import os.kei.ui.page.main.student.tabcontent.profile.profileStudentInfoFieldSpecs
import os.kei.ui.page.main.student.tabcontent.profile.sortGalleryItemsByTitleNumbers
import os.kei.ui.page.main.student.tabcontent.profile.sortProfileRowsByKeyNumbers
import java.util.LinkedHashMap

private const val GUIDE_PROFILE_TAB_STATE_CACHE_MAX_SIZE = 96

private val guideProfileTabStateCache = object : LinkedHashMap<String, GuideProfileTabHeaderState>(
    GUIDE_PROFILE_TAB_STATE_CACHE_MAX_SIZE,
    0.75f,
    true
) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GuideProfileTabHeaderState>?): Boolean {
        return size > GUIDE_PROFILE_TAB_STATE_CACHE_MAX_SIZE
    }
}

internal data class GuideProfileTabHeaderState(
    val nicknameRows: List<BaGuideRow>,
    val studentInfoRows: List<BaGuideRow>,
    val hobbyRows: List<BaGuideRow>,
    val giftPreferenceItems: List<GiftPreferenceItem>,
    val chocolateInfoRows: List<BaGuideRow>,
    val furnitureInfoRows: List<BaGuideRow>,
    val normalProfileRows: List<BaGuideRow>,
    val sameNameRoleItems: List<SameNameRoleItem>,
    val sameNameRoleHint: String,
    val chocolateGalleryItems: List<BaGuideGalleryItem>,
    val furnitureGalleryItems: List<BaGuideGalleryItem>
)

internal fun buildGuideProfileTabHeaderState(
    guide: BaStudentGuideInfo
): GuideProfileTabHeaderState {
    val cacheKey = guide.profileTabStateCacheKey()
    synchronized(guideProfileTabStateCache) {
        guideProfileTabStateCache[cacheKey]?.let { return it }
    }
    val profileRowsBase = guide.profileRowsForDisplay()
        .filterNot(::shouldHideMovedHeaderRow)
        .filterNot(::isGrowthTitleVoiceRow)
        .filterNot(::isVoicePlaceholderRow)
        .filterNot(::isProfileSectionHeaderRow)
        .filterNot(::isGalleryRelatedProfileLinkRow)
    val sameNameRoleRows = profileRowsBase.filter(::isSameNameRoleRow)
    val sameNameRoleItems = buildSameNameRoleItems(sameNameRoleRows)
    val sameNameRoleHint = sameNameRoleRows.firstNotNullOfOrNull { row ->
        extractSameNameRoleHint(row)
    }.orEmpty()
    val hasTopDataHeader = profileRowsBase.any { row ->
        normalizeProfileFieldKey(row.key) == normalizeProfileFieldKey("顶级数据")
    }
    val hasInitialDataHeader = profileRowsBase.any { row ->
        normalizeProfileFieldKey(row.key) == normalizeProfileFieldKey("初始数据")
    }
    val allProfileRows = profileRowsBase.filterNot { row ->
        isSkillMigratedProfileRow(
            row = row,
            hasTopDataHeader = hasTopDataHeader,
            hasInitialDataHeader = hasInitialDataHeader
        ) || isSameNameRoleRow(row)
    }
    val nicknameRows = buildProfileCardRows(
        rows = allProfileRows,
        specs = profileNicknameFieldSpecs
    )
    val studentInfoRows = buildProfileCardRows(
        rows = allProfileRows,
        specs = profileStudentInfoFieldSpecs
    )
    val hobbyRows = buildProfileCardRows(
        rows = allProfileRows,
        specs = profileHobbyFieldSpecs
    )
    val giftPreferenceRows = allProfileRows
        .filter(::isGiftPreferenceProfileRow)
        .let(::sortProfileRowsByKeyNumbers)
    val giftPreferenceItems = buildGiftPreferenceItems(giftPreferenceRows)
    val chocolateInfoRows = allProfileRows.filter { row ->
        val key = row.key.trim()
        key.contains("巧克力", ignoreCase = true)
    }.let(::sortProfileRowsByKeyNumbers)
    val furnitureInfoRows = allProfileRows.filter { row ->
        val key = row.key.trim()
        key.contains("互动家具", ignoreCase = true)
    }.let(::sortProfileRowsByKeyNumbers)
    val normalProfileRows = allProfileRows.filterNot { row ->
        val key = row.key.trim()
        key.contains("巧克力", ignoreCase = true) ||
            key.contains("互动家具", ignoreCase = true) ||
            isGiftPreferenceProfileRow(row) ||
            isStructuredProfileCardRow(row)
    }
    val chocolateGalleryItems = guide.galleryItems
        .filter(::isChocolateGalleryItem)
        .filter(::hasRenderableGalleryMedia)
        .distinctBy {
            val media = it.mediaUrl.ifBlank { it.imageUrl }
            "${it.mediaType}|$media"
        }
        .let(::sortGalleryItemsByTitleNumbers)
    val furnitureGalleryItems = guide.galleryItems
        .filter(::isInteractiveFurnitureGalleryItem)
        .filter(::hasRenderableGalleryMedia)
        .distinctBy {
            val media = it.mediaUrl.ifBlank { it.imageUrl }
            "${it.mediaType}|$media"
        }
        .let(::sortGalleryItemsByTitleNumbers)
    val computed = GuideProfileTabHeaderState(
        nicknameRows = nicknameRows,
        studentInfoRows = studentInfoRows,
        hobbyRows = hobbyRows,
        giftPreferenceItems = giftPreferenceItems,
        chocolateInfoRows = chocolateInfoRows,
        furnitureInfoRows = furnitureInfoRows,
        normalProfileRows = normalProfileRows,
        sameNameRoleItems = sameNameRoleItems,
        sameNameRoleHint = sameNameRoleHint,
        chocolateGalleryItems = chocolateGalleryItems,
        furnitureGalleryItems = furnitureGalleryItems
    )
    synchronized(guideProfileTabStateCache) {
        guideProfileTabStateCache[cacheKey] = computed
    }
    return computed
}

private fun BaStudentGuideInfo.profileTabStateCacheKey(): String {
    return buildString {
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
}
