package os.kei.ui.page.main.student

internal fun BaStudentGuideInfo.isNpcSatelliteLikeGuide(
    catalogMatched: Boolean,
): Boolean {
    if (catalogMatched) return true
    val hasImplementedCombatData =
        skillRows.isNotEmpty() ||
            growthRows.isNotEmpty() ||
            simulateRows.isNotEmpty() ||
            stats.any { (key, value) ->
                val source = "$key $value"
                listOf("攻击类型", "防御类型", "战术作用", "武器类型", "市街", "屋外", "室内").any { token ->
                    source.contains(token, ignoreCase = true)
                }
            }
    val hasNpcProfileShape =
        profileRowsForDisplay().any { row ->
            row.key.trim() == "所属" ||
                row.key.contains("其他译名") ||
                row.key.contains("黑话") ||
                row.key.contains("首次登场")
        } ||
            (profileRows.isNotEmpty() && galleryItems.isNotEmpty() && stats.isEmpty())
    return hasNpcProfileShape && !hasImplementedCombatData
}
