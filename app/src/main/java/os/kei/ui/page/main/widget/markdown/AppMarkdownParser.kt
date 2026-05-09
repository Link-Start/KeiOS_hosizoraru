package os.kei.ui.page.main.widget.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

private val HEADING_REGEX = Regex("^(#{1,4})\\s+(.+)$")
private val HORIZONTAL_RULE_REGEX = Regex("""^[-*_]{3,}\s*$""")
private val BLOCKQUOTE_REGEX = Regex("""^>\s?(.*)$""")
private val TASK_LIST_REGEX = Regex("""^[-*+]\s+\[([ xX])]\s+(.*)$""")
private val ORDERED_LIST_PREFIX_REGEX = Regex("^\\d+\\.\\s+")
private val ORDERED_LIST_CONTENT_REGEX = Regex("^(\\d+)\\.\\s+(.*)$")
private val UNORDERED_LIST_REGEX = Regex("^[-*+]\\s+(.*)$")
private val TABLE_SEPARATOR_CELL_REGEX = Regex(""":?-{3,}:?""")
private val INLINE_TOKEN_REGEX = Regex(
    "`([^`]+)`|\\*\\*([^*]+)\\*\\*|__([^_]+)__|\\*([^*]+)\\*|_([^_]+)_|!\\[(.*?)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)|\\[(.+?)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)|<((?:https?://|mailto:)[^>\\s]+)>|((?:https?://|mailto:)[^\\s<]+)"
)

internal fun parseAppMarkdownBlocks(
    markdown: String,
    preserveLineBreaks: Boolean = false
): List<AppMarkdownBlock> {
    val lines = markdown.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<AppMarkdownBlock>()
    val paragraphBuffer = mutableListOf<String>()
    val codeBuffer = mutableListOf<String>()
    var inCode = false
    var inHtmlComment = false

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            val text = paragraphBuffer.joinToString(
                separator = if (preserveLineBreaks) "\n" else " "
            ).trim()
            if (text.isNotBlank()) blocks += AppMarkdownBlock.Paragraph(text)
            paragraphBuffer.clear()
        }
    }

    fun flushCode() {
        if (codeBuffer.isNotEmpty()) {
            blocks += AppMarkdownBlock.Code(codeBuffer.joinToString("\n").trimEnd())
            codeBuffer.clear()
        }
    }

    lines.forEachIndexed { index, raw ->
        val line = raw.trimEnd()
        val trimmed = line.trim()

        if (inHtmlComment) {
            if (trimmed.contains("-->")) {
                inHtmlComment = false
            }
            return@forEachIndexed
        }
        if (trimmed.startsWith("<!--")) {
            if (!trimmed.contains("-->")) {
                inHtmlComment = true
            }
            return@forEachIndexed
        }

        if (trimmed.startsWith("```")) {
            flushParagraph()
            if (inCode) {
                flushCode()
                inCode = false
            } else {
                inCode = true
            }
            return@forEachIndexed
        }

        if (inCode) {
            codeBuffer += line
            return@forEachIndexed
        }

        if (trimmed.isBlank()) {
            flushParagraph()
            return@forEachIndexed
        }

        if (HORIZONTAL_RULE_REGEX.matches(trimmed)) {
            flushParagraph()
            blocks += AppMarkdownBlock.HorizontalRule
            return@forEachIndexed
        }

        val headingMatch = HEADING_REGEX.find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            blocks += AppMarkdownBlock.Heading(
                level = headingMatch.groupValues[1].length,
                text = headingMatch.groupValues[2].trim()
            )
            return@forEachIndexed
        }

        val quoteMatch = BLOCKQUOTE_REGEX.find(trimmed)
        if (quoteMatch != null) {
            flushParagraph()
            blocks += AppMarkdownBlock.Quote(quoteMatch.groupValues[1].trim())
            return@forEachIndexed
        }

        val tableCells = trimmed.markdownTableCellsOrNull()
        if (tableCells != null) {
            flushParagraph()
            if (!trimmed.isMarkdownTableSeparator()) {
                blocks += AppMarkdownBlock.TableRow(
                    cells = tableCells,
                    header = lines.getOrNull(index + 1)?.trim()?.isMarkdownTableSeparator() == true
                )
            }
            return@forEachIndexed
        }

        val taskMatch = TASK_LIST_REGEX.find(trimmed)
        if (taskMatch != null) {
            flushParagraph()
            blocks += AppMarkdownBlock.Task(
                checked = taskMatch.groupValues[1].equals("x", ignoreCase = true),
                text = taskMatch.groupValues[2].trim()
            )
            return@forEachIndexed
        }

        val unorderedMatch = UNORDERED_LIST_REGEX.find(trimmed)
        if (unorderedMatch != null) {
            flushParagraph()
            blocks += AppMarkdownBlock.Bullet(unorderedMatch.groupValues[1].trim())
            return@forEachIndexed
        }

        if (ORDERED_LIST_PREFIX_REGEX.containsMatchIn(trimmed)) {
            flushParagraph()
            val match = ORDERED_LIST_CONTENT_REGEX.find(trimmed)
            val index = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val text = match?.groupValues?.getOrNull(2).orEmpty()
            blocks += AppMarkdownBlock.Ordered(index, text)
            return@forEachIndexed
        }

        paragraphBuffer += trimmed
        if (preserveLineBreaks) {
            flushParagraph()
        }
    }

    flushParagraph()
    flushCode()
    return blocks
}

private fun String.markdownTableCellsOrNull(): List<String>? {
    if (!contains('|')) return null
    val source = trim()
    if (!source.startsWith("|") && !source.endsWith("|")) return null
    val trimmed = source.trim('|')
    val cells = trimmed.splitMarkdownTableCells().map { it.trim() }
    return cells.takeIf { it.size >= 2 && it.any { cell -> cell.isNotBlank() } }
}

private fun String.splitMarkdownTableCells(): List<String> {
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < length) {
        val char = this[index]
        when {
            char == '\\' && getOrNull(index + 1) == '|' -> {
                current.append('|')
                index += 2
            }

            char == '|' -> {
                cells += current.toString()
                current.clear()
                index += 1
            }

            else -> {
                current.append(char)
                index += 1
            }
        }
    }
    cells += current.toString()
    return cells
}

private fun String.isMarkdownTableSeparator(): Boolean {
    val cells = markdownTableCellsOrNull() ?: return false
    return cells.all { cell ->
        cell.matches(TABLE_SEPARATOR_CELL_REGEX)
    }
}

internal fun buildAppMarkdownInlineText(
    text: String,
    baseStyle: SpanStyle,
    accentStyle: SpanStyle,
    linkStyle: SpanStyle,
    onOpenLink: ((String) -> Unit)? = null
): AnnotatedString {
    val tokens = parseAppMarkdownInlineTokens(text)
    return buildAnnotatedString {
        tokens.forEach { token ->
            when (token) {
                is AppMarkdownInlineToken.Plain -> withStyle(baseStyle) { append(token.text) }
                is AppMarkdownInlineToken.Emphasis -> withStyle(
                    baseStyle.copy(fontWeight = FontWeight.SemiBold)
                ) {
                    append(token.text)
                }

                is AppMarkdownInlineToken.Code -> withStyle(accentStyle) { append(" ${token.text} ") }
                is AppMarkdownInlineToken.Link -> {
                    val label = token.label.ifBlank { token.url }
                    if (onOpenLink == null) {
                        withStyle(linkStyle) {
                            append(label)
                        }
                    } else {
                        withLink(
                            LinkAnnotation.Url(
                                url = token.url,
                                styles = TextLinkStyles(style = linkStyle),
                                linkInteractionListener = { link ->
                                    val url = (link as? LinkAnnotation.Url)?.url ?: token.url
                                    onOpenLink(url)
                                }
                            )
                        ) {
                            append(label)
                        }
                    }
                }
            }
        }
    }
}

internal fun parseAppMarkdownInlineTokens(text: String): List<AppMarkdownInlineToken> {
    val tokens = mutableListOf<AppMarkdownInlineToken>()
    var cursor = 0
    INLINE_TOKEN_REGEX.findAll(text).forEach { match ->
        val range = match.range
        if (range.first > cursor) {
            tokens += AppMarkdownInlineToken.Plain(text.substring(cursor, range.first))
        }
        val code = match.groups[1]?.value
        val strong = match.groups[2]?.value
        val strongUnderscore = match.groups[3]?.value
        val em = match.groups[4]?.value
        val emUnderscore = match.groups[5]?.value
        val imageLabel = match.groups[6]?.value
        val imageUrl = match.groups[7]?.value
        val label = match.groups[8]?.value
        val markdownUrl = match.groups[9]?.value
        val autoLinkUrl = match.groups[10]?.value
        val bareUrl = match.groups[11]?.value
        when {
            !code.isNullOrBlank() -> tokens += AppMarkdownInlineToken.Code(code)
            !strong.isNullOrBlank() -> tokens += AppMarkdownInlineToken.Emphasis(strong)
            !strongUnderscore.isNullOrBlank() -> tokens += AppMarkdownInlineToken.Emphasis(
                strongUnderscore
            )
            !em.isNullOrBlank() -> tokens += AppMarkdownInlineToken.Emphasis(em)
            !emUnderscore.isNullOrBlank() -> tokens += AppMarkdownInlineToken.Emphasis(emUnderscore)
            imageLabel != null && !imageUrl.isNullOrBlank() -> {
                tokens += AppMarkdownInlineToken.Link(
                    imageLabel.trimMarkdownLinkLabelCode().ifBlank { "image" }, imageUrl
                )
            }

            !label.isNullOrBlank() && !markdownUrl.isNullOrBlank() -> {
                tokens += AppMarkdownInlineToken.Link(
                    label.trimMarkdownLinkLabelCode(),
                    markdownUrl
                )
            }

            !autoLinkUrl.isNullOrBlank() -> {
                tokens += AppMarkdownInlineToken.Link(autoLinkUrl, autoLinkUrl)
            }

            !bareUrl.isNullOrBlank() -> {
                val (url, trailing) = bareUrl.trimBareUrlTrailingPunctuation()
                tokens += AppMarkdownInlineToken.Link(url, url)
                if (trailing.isNotEmpty()) tokens += AppMarkdownInlineToken.Plain(trailing)
            }

            else -> tokens += AppMarkdownInlineToken.Plain(match.value)
        }
        cursor = range.last + 1
    }
    if (cursor < text.length) {
        tokens += AppMarkdownInlineToken.Plain(text.substring(cursor))
    }
    return if (tokens.isEmpty()) listOf(AppMarkdownInlineToken.Plain(text)) else tokens
}

private fun String.trimBareUrlTrailingPunctuation(): Pair<String, String> {
    val trailing = takeLastWhile { it in ".,;:!?" }
    if (trailing.isEmpty()) return this to ""
    return dropLast(trailing.length) to trailing
}

private fun String.trimMarkdownLinkLabelCode(): String {
    val trimmed = trim()
    return if (trimmed.length >= 2 && trimmed.first() == '`' && trimmed.last() == '`') {
        trimmed.substring(1, trimmed.lastIndex).trim()
    } else {
        trimmed
    }
}
