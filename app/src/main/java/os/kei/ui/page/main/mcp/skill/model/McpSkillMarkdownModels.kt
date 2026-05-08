package os.kei.ui.page.main.mcp.skill.model

internal data class SkillSection(
    val level: Int,
    val title: String,
    val items: List<SkillSectionItem>
)

internal sealed interface SkillSectionItem {
    data class SubHeading(val level: Int, val text: String) : SkillSectionItem
    data class Paragraph(val text: String) : SkillSectionItem
    data class Bullet(val text: String) : SkillSectionItem
    data class Ordered(val index: Int, val text: String) : SkillSectionItem
    data class Code(val text: String) : SkillSectionItem
}
