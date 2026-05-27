package os.kei.ui.page.main.mcp.skill.component

import org.junit.Test
import os.kei.ui.page.main.mcp.skill.model.SkillSection
import os.kei.ui.page.main.mcp.skill.model.SkillSectionItem
import kotlin.test.assertEquals

class McpSkillContentListTest {
    @Test
    fun `stable section row keys survive head trimming`() {
        val first =
            SkillSection(
                level = 2,
                title = "Overview",
                items = listOf(SkillSectionItem.Paragraph("one")),
            )
        val second =
            SkillSection(
                level = 2,
                title = "Workflow",
                items = listOf(SkillSectionItem.Bullet("two")),
            )
        val third =
            SkillSection(
                level = 3,
                title = "Details",
                items = listOf(SkillSectionItem.Code("three")),
            )

        val originalRows = listOf(first, second, third).toStableMcpSkillSectionRows()
        val trimmedRows = listOf(second, third).toStableMcpSkillSectionRows()

        assertEquals(originalRows[1].stableKey, trimmedRows[0].stableKey)
        assertEquals(originalRows[2].stableKey, trimmedRows[1].stableKey)
    }
}
