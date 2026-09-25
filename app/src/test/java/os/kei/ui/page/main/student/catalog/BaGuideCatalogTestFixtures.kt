package os.kei.ui.page.main.student.catalog

import os.kei.ui.page.main.student.GuideBgmFavoriteItem

/** Shared builders for the student-guide tests. Name only the fields a test depends on. */
internal fun testCatalogEntry(
    contentId: Long = 1L,
    name: String = "Student $contentId",
    tab: BaGuideCatalogTab = BaGuideCatalogTab.Student,
    iconUrl: String = "",
    order: Int = contentId.toInt(),
    createdAtSec: Long = 0L,
    detailUrl: String = "https://www.gamekee.com/ba/$contentId",
    filterAttributes: BaGuideCatalogEntryFilterAttributes = BaGuideCatalogEntryFilterAttributes.EMPTY,
    entryId: Int = contentId.toInt(),
): BaGuideCatalogEntry =
    BaGuideCatalogEntry(
        entryId = entryId,
        pid = 0,
        contentId = contentId,
        name = name,
        alias = "",
        aliasDisplay = "",
        iconUrl = iconUrl,
        type = 0,
        order = order,
        createdAtSec = createdAtSec,
        detailUrl = detailUrl,
        tab = tab,
        filterAttributes = filterAttributes,
    )

internal fun testBgmFavorite(
    audioUrl: String,
    sourceUrl: String = "",
    title: String = "",
    studentTitle: String = "",
    studentImageUrl: String = "",
    imageUrl: String = "",
    favoritedAtMs: Long = 1L,
): GuideBgmFavoriteItem =
    GuideBgmFavoriteItem(
        audioUrl = audioUrl,
        title = title,
        studentTitle = studentTitle,
        studentImageUrl = studentImageUrl,
        imageUrl = imageUrl,
        sourceUrl = sourceUrl,
        note = "",
        favoritedAtMs = favoritedAtMs,
    )
