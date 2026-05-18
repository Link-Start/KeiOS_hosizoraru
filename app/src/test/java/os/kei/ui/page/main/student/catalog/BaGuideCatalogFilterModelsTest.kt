package os.kei.ui.page.main.student.catalog

import org.junit.Test
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogSortMode
import os.kei.ui.page.main.student.catalog.state.sortedByMode
import kotlin.test.assertEquals

class BaGuideCatalogFilterModelsTest {
    @Test
    fun `parse filter index maps attributes by entry id`() {
        val index =
            parseBaGuideCatalogFilterIndex(
                """
                  {
                    "code": 0,
                    "data": {
                      "entry_filter": [
                        {
                          "id": 68,
                          "name": "星级",
                          "type": 0,
                          "children": [
                            { "id": 176, "name": "三星" },
                            { "id": 70, "name": "二星" }
                          ]
                        },
                        {
                          "id": 11543,
                          "name": "外服评分",
                          "type": 0,
                          "children": [
                            { "id": 11544, "name": "100(SS)" },
                            { "id": 11545, "name": "90+(S)" }
                          ]
                        },
                        {
                          "id": 12034,
                          "name": "学园",
                          "type": 0,
                          "children": [
                            { "id": 12035, "name": "联邦学生会" },
                            { "id": 12036, "name": "夏莱" }
                          ]
                        },
                        { "id": 3774, "name": "实装日期", "type": 1, "children": [] }
                      ],
                  "entry_filter_attr": {
                    "100051": [
                      { "input_id": 68, "value": ["176"] },
                      { "input_id": 11543, "value": ["11545"] },
                      { "input_id": 3774, "value": "1637078400000" }
                    ],
                    "100052": [
                      { "input_id": 68, "value": "70" }
                    ],
                    "107621": [
                      { "input_id": 12034, "value": ["12035"] }
                    ]
                  }
                }
                  }
                """.trimIndent(),
            )

        assertEquals(listOf(68, 11543, 3774, BA_GUIDE_FILTER_ID_NPC_SCHOOL), index.definitions.map { it.id })
        assertEquals(setOf(176), index.attributes(100051).optionIds(68))
        assertEquals(setOf(70), index.attributes(100052).optionIds(68))
        assertEquals(setOf(12035), index.attributes(107621).optionIds(BA_GUIDE_FILTER_ID_NPC_SCHOOL))
        assertEquals(1_637_078_400L, index.releaseDateSec(100051))
    }

    @Test
    fun `student filter and score sort use parsed option ranks`() {
        val starDefinition =
            BaGuideCatalogFilterDefinition(
                id = 68,
                name = "星级",
                type = 0,
                options =
                    listOf(
                        BaGuideCatalogFilterOption(176, "三星"),
                        BaGuideCatalogFilterOption(70, "二星"),
                    ),
            )
        val scoreDefinition =
            BaGuideCatalogFilterDefinition(
                id = BA_GUIDE_FILTER_ID_GLOBAL_SCORE,
                name = "外服评分",
                type = 0,
                options =
                    listOf(
                        BaGuideCatalogFilterOption(11544, "100(SS)"),
                        BaGuideCatalogFilterOption(11545, "90+(S)"),
                    ),
            )
        val highScore =
            catalogEntry(
                name = "高分",
                order = 2,
                attributes =
                    BaGuideCatalogEntryFilterAttributes(
                        optionIdsByFilterId =
                            mapOf(
                                68 to setOf(176),
                                BA_GUIDE_FILTER_ID_GLOBAL_SCORE to setOf(11544),
                            ),
                    ),
            )
        val lowScore =
            catalogEntry(
                name = "低分",
                order = 1,
                attributes =
                    BaGuideCatalogEntryFilterAttributes(
                        optionIdsByFilterId =
                            mapOf(
                                68 to setOf(70),
                                BA_GUIDE_FILTER_ID_GLOBAL_SCORE to setOf(11545),
                            ),
                    ),
            )

        val entries = listOf(lowScore, highScore)
        val filtered = entries.filterByCatalogFilters(mapOf(68 to setOf(176)))
        val sorted =
            entries.sortedByMode(
                mode = BaGuideCatalogSortMode.GlobalScoreDesc,
                favoriteCatalogEntries = emptyMap(),
                filterDefinitions = listOf(starDefinition, scoreDefinition),
            )

        assertEquals(listOf("高分"), filtered.map { it.name })
        assertEquals(listOf("高分", "低分"), sorted.map { it.name })
    }

    @Test
    fun `catalog filters are scoped to current tab definitions`() {
        val studentDefinition =
            BaGuideCatalogFilterDefinition(
                id = 68,
                name = "星级",
                type = 0,
                options = listOf(BaGuideCatalogFilterOption(176, "三星")),
            )
        val npcDefinition =
            BaGuideCatalogFilterDefinition(
                id = BA_GUIDE_FILTER_ID_NPC_SCHOOL,
                name = "学园",
                type = 0,
                options = listOf(BaGuideCatalogFilterOption(12035, "联邦学生会")),
            )
        val selected =
            mapOf(
                68 to setOf(176),
                BA_GUIDE_FILTER_ID_NPC_SCHOOL to setOf(12035),
            )
        val scopedStudentFilters =
            selectedCatalogFilterOptionsForDefinitions(
                selectedOptionIdsByFilterId = selected,
                definitions = listOf(studentDefinition),
            )
        val scopedNpcFilters =
            selectedCatalogFilterOptionsForDefinitions(
                selectedOptionIdsByFilterId = selected,
                definitions = listOf(npcDefinition),
            )
        val npcEntry =
            catalogEntry(
                name = "联邦学生会长",
                order = 3,
                tab = BaGuideCatalogTab.NpcSatellite,
                attributes =
                    BaGuideCatalogEntryFilterAttributes(
                        optionIdsByFilterId = mapOf(BA_GUIDE_FILTER_ID_NPC_SCHOOL to setOf(12035)),
                    ),
            )

        assertEquals(mapOf(68 to setOf(176)), scopedStudentFilters)
        assertEquals(mapOf(BA_GUIDE_FILTER_ID_NPC_SCHOOL to setOf(12035)), scopedNpcFilters)
        assertEquals(listOf("联邦学生会长"), listOf(npcEntry).filterByCatalogFilters(scopedNpcFilters).map { it.name })
    }

    private fun catalogEntry(
        name: String,
        order: Int,
        attributes: BaGuideCatalogEntryFilterAttributes,
        tab: BaGuideCatalogTab = BaGuideCatalogTab.Student,
    ): BaGuideCatalogEntry {
        val contentId = order.toLong() + 100L
        return BaGuideCatalogEntry(
            entryId = order + 10,
            pid = 49443,
            contentId = contentId,
            name = name,
            alias = "",
            aliasDisplay = "",
            iconUrl = "",
            type = 1,
            order = order,
            createdAtSec = 1L,
            detailUrl = "https://www.gamekee.com/ba/tj/$contentId.html",
            tab = tab,
            filterAttributes = attributes,
        )
    }
}
