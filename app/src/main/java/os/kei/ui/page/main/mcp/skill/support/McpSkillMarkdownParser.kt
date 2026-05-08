package os.kei.ui.page.main.mcp.skill.support

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import os.kei.ui.page.main.mcp.skill.model.SkillSection
import os.kei.ui.page.main.mcp.skill.model.SkillSectionItem
import os.kei.ui.page.main.widget.markdown.AppMarkdownBlock
import os.kei.ui.page.main.widget.markdown.buildAppMarkdownInlineText
import os.kei.ui.page.main.widget.markdown.parseAppMarkdownBlocks

internal fun buildSkillSections(
    blocks: List<AppMarkdownBlock>,
    defaultRootTitle: String,
    defaultOverviewTitle: String,
    defaultContentTitle: String,
    emptyContentText: String
): List<SkillSection> {
    if (blocks.isEmpty()) {
        return listOf(
            SkillSection(
                level = 1,
                title = defaultRootTitle,
                items = listOf(SkillSectionItem.Paragraph(emptyContentText))
            )
        )
    }

    val sections = mutableListOf<SkillSection>()
    val currentItems = mutableListOf<SkillSectionItem>()
    var currentTitle = ""
    var currentLevel = 2

    fun ensureSectionStarted() {
        if (currentTitle.isBlank()) {
            currentTitle = defaultOverviewTitle
            currentLevel = 2
        }
    }

    fun flushSection() {
        if (currentTitle.isBlank() && currentItems.isEmpty()) return
        if (currentItems.isEmpty() && currentLevel == 1 && sections.isEmpty()) {
            currentTitle = ""
            return
        }
        if (currentItems.isEmpty() && sections.isNotEmpty()) {
            currentTitle = ""
            return
        }
        sections += SkillSection(
            level = currentLevel,
            title = currentTitle.ifBlank { defaultContentTitle },
            items = currentItems.toList()
        )
        currentItems.clear()
    }

    blocks.forEach { block ->
        when (block) {
            is AppMarkdownBlock.Heading -> {
                if (block.level <= 2) {
                    flushSection()
                    currentTitle = block.text.ifBlank { defaultContentTitle }
                    currentLevel = block.level
                } else {
                    ensureSectionStarted()
                    currentItems += SkillSectionItem.SubHeading(block.level, block.text)
                }
            }

            is AppMarkdownBlock.Paragraph -> {
                ensureSectionStarted()
                currentItems += SkillSectionItem.Paragraph(block.text)
            }

            is AppMarkdownBlock.Bullet -> {
                ensureSectionStarted()
                currentItems += SkillSectionItem.Bullet(block.text)
            }

            is AppMarkdownBlock.Ordered -> {
                ensureSectionStarted()
                currentItems += SkillSectionItem.Ordered(block.index, block.text)
            }

            is AppMarkdownBlock.Code -> {
                ensureSectionStarted()
                currentItems += SkillSectionItem.Code(block.text)
            }
        }
    }

    flushSection()
    if (sections.isEmpty()) {
        return listOf(
            SkillSection(
                level = 1,
                title = defaultRootTitle,
                items = listOf(SkillSectionItem.Paragraph(emptyContentText))
            )
        )
    }
    return sections
}

internal fun parseMarkdownBlocks(markdown: String): List<AppMarkdownBlock> =
    parseAppMarkdownBlocks(markdown)

internal fun buildInlineStyledText(
    text: String,
    baseStyle: SpanStyle,
    accentStyle: SpanStyle,
    linkStyle: SpanStyle
): AnnotatedString = buildAppMarkdownInlineText(text, baseStyle, accentStyle, linkStyle)
