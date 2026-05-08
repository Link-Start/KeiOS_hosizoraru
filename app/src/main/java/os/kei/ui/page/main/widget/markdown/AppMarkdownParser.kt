package os.kei.ui.page.main.widget.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private val HEADING_REGEX = Regex("^(#{1,4})\\s+(.+)$")
private val ORDERED_LIST_PREFIX_REGEX = Regex("^\\d+\\.\\s+")
private val ORDERED_LIST_CONTENT_REGEX = Regex("^(\\d+)\\.\\s+(.*)$")
private val UNORDERED_LIST_REGEX = Regex("^[-*+]\\s+(.*)$")
private val INLINE_TOKEN_REGEX = Regex(
    "`([^`]+)`|\\*\\*([^*]+)\\*\\*|\\*([^*]+)\\*|\\[(.+?)]\\((https?://[^)\\s]+)\\)"
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

    lines.forEach { raw ->
        val line = raw.trimEnd()
        val trimmed = line.trim()

        if (inHtmlComment) {
            if (trimmed.contains("-->")) {
                inHtmlComment = false
            }
            return@forEach
        }
        if (trimmed.startsWith("<!--")) {
            if (!trimmed.contains("-->")) {
                inHtmlComment = true
            }
            return@forEach
        }

        if (trimmed.startsWith("```")) {
            flushParagraph()
            if (inCode) {
                flushCode()
                inCode = false
            } else {
                inCode = true
            }
            return@forEach
        }

        if (inCode) {
            codeBuffer += line
            return@forEach
        }

        if (trimmed.isBlank()) {
            flushParagraph()
            return@forEach
        }

        val headingMatch = HEADING_REGEX.find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            blocks += AppMarkdownBlock.Heading(
                level = headingMatch.groupValues[1].length,
                text = headingMatch.groupValues[2].trim()
            )
            return@forEach
        }

        val unorderedMatch = UNORDERED_LIST_REGEX.find(trimmed)
        if (unorderedMatch != null) {
            flushParagraph()
            blocks += AppMarkdownBlock.Bullet(unorderedMatch.groupValues[1].trim())
            return@forEach
        }

        if (ORDERED_LIST_PREFIX_REGEX.containsMatchIn(trimmed)) {
            flushParagraph()
            val match = ORDERED_LIST_CONTENT_REGEX.find(trimmed)
            val index = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val text = match?.groupValues?.getOrNull(2).orEmpty()
            blocks += AppMarkdownBlock.Ordered(index, text)
            return@forEach
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

internal fun buildAppMarkdownInlineText(
    text: String,
    baseStyle: SpanStyle,
    accentStyle: SpanStyle,
    linkStyle: SpanStyle
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
                    withStyle(linkStyle) { append(token.label) }
                    withStyle(baseStyle.copy(color = baseStyle.color.copy(alpha = 0.72f))) {
                        append(" (${token.url})")
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
        val em = match.groups[3]?.value
        val label = match.groups[4]?.value
        val url = match.groups[5]?.value
        when {
            !code.isNullOrBlank() -> tokens += AppMarkdownInlineToken.Code(code)
            !strong.isNullOrBlank() -> tokens += AppMarkdownInlineToken.Emphasis(strong)
            !em.isNullOrBlank() -> tokens += AppMarkdownInlineToken.Emphasis(em)
            !label.isNullOrBlank() && !url.isNullOrBlank() -> {
                tokens += AppMarkdownInlineToken.Link(label, url)
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
