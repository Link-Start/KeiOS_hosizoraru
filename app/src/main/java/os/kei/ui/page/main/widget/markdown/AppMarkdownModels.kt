package os.kei.ui.page.main.widget.markdown

internal sealed interface AppMarkdownBlock {
    data class Heading(val level: Int, val text: String) : AppMarkdownBlock
    data class Paragraph(val text: String) : AppMarkdownBlock
    data class Bullet(val text: String) : AppMarkdownBlock
    data class Task(val checked: Boolean, val text: String) : AppMarkdownBlock
    data class Ordered(val index: Int, val text: String) : AppMarkdownBlock
    data class Quote(val text: String) : AppMarkdownBlock
    data class TableRow(val cells: List<String>, val header: Boolean) : AppMarkdownBlock
    data object HorizontalRule : AppMarkdownBlock
    data class Code(val text: String) : AppMarkdownBlock
}

internal sealed interface AppMarkdownInlineToken {
    data class Plain(val text: String) : AppMarkdownInlineToken
    data class Emphasis(val text: String) : AppMarkdownInlineToken
    data class Code(val text: String) : AppMarkdownInlineToken
    data class Link(val label: String, val url: String) : AppMarkdownInlineToken
}
