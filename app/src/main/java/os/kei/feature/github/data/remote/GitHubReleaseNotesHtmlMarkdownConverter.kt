package os.kei.feature.github.data.remote

internal object GitHubReleaseNotesHtmlMarkdownConverter {
    private const val MAX_RELEASE_NOTES_BODY_CHARS = 24_000
    private val htmlBlockOptions = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val markdownBodyNestedRegex = Regex(
        """<div[^>]*class="[^"]*markdown-body[^"]*"[^>]*>(.*?)</div>\s*</div>""",
        htmlBlockOptions
    )
    private val markdownBodyRegex = Regex(
        """<div[^>]*class="[^"]*markdown-body[^"]*"[^>]*>(.*?)</div>""",
        htmlBlockOptions
    )
    private val preCodeRegex = Regex(
        """<pre[^>]*>\s*<code[^>]*>(.*?)</code>\s*</pre>""",
        htmlBlockOptions
    )
    private val tableRegex = Regex("""<table[^>]*>(.*?)</table>""", htmlBlockOptions)
    private val headingRegex = Regex("""<h([1-6])[^>]*>(.*?)</h\1>""", htmlBlockOptions)
    private val summaryRegex = Regex("""<summary[^>]*>(.*?)</summary>""", htmlBlockOptions)
    private val blockquoteRegex = Regex("""<blockquote[^>]*>(.*?)</blockquote>""", htmlBlockOptions)
    private val listItemRegex = Regex("""<li[^>]*>(.*?)</li>""", htmlBlockOptions)
    private val paragraphRegex = Regex("""<p[^>]*>(.*?)</p>""", htmlBlockOptions)
    private val horizontalRuleRegex = Regex("""<hr\s*/?>""", RegexOption.IGNORE_CASE)
    private val lineBreakRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val closingBlockRegex =
        Regex("""</(?:div|section|article|ul|ol|details)>""", RegexOption.IGNORE_CASE)
    private val htmlTagRegex = Regex("""<[^>]+>""")
    private val tableRowRegex = Regex("""<tr[^>]*>(.*?)</tr>""", htmlBlockOptions)
    private val tableCellRegex = Regex("""<t([hd])[^>]*>(.*?)</t\1>""", htmlBlockOptions)
    private val imageRegex = Regex("""<img\b([^>]*)>""", htmlBlockOptions)
    private val anchorRegex = Regex("""<a\b([^>]*)>(.*?)</a>""", htmlBlockOptions)
    private val codeRegex = Regex("""<(?:code|tt)[^>]*>(.*?)</(?:code|tt)>""", htmlBlockOptions)
    private val strongRegex = Regex("""<(?:strong|b)[^>]*>(.*?)</(?:strong|b)>""", htmlBlockOptions)
    private val emphasisRegex = Regex("""<(?:em|i)[^>]*>(.*?)</(?:em|i)>""", htmlBlockOptions)
    private val checkboxRegex = Regex("""<input\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val horizontalWhitespaceRegex = Regex("""[ \t\x0B\f\r]+""")
    private val whitespaceRegex = Regex("""\s+""")

    fun parse(html: String): String {
        val markdownBlock = markdownBodyNestedRegex.find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .ifBlank {
                markdownBodyRegex.find(html)
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()
            }
        return markdownBlock.htmlReleaseNotesToMarkdown().take(MAX_RELEASE_NOTES_BODY_CHARS)
    }

    private fun String.htmlReleaseNotesToMarkdown(): String {
        return this
            .replace(preCodeRegex) { match ->
                val code = match.groupValues.getOrNull(1).orEmpty()
                    .stripHtml()
                    .decodeHtmlEntities()
                    .trimEnd()
                "\n```\n$code\n```\n"
            }
            .replace(tableRegex) { match ->
                "\n${match.groupValues.getOrNull(1).orEmpty().htmlTableToMarkdown()}\n"
            }
            .replace(headingRegex) { match ->
                val level = match.groupValues.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 4) ?: 2
                val text = match.groupValues.getOrNull(2).orEmpty().htmlFragmentToMarkdown()
                "\n${"#".repeat(level)} $text\n\n"
            }
            .replace(summaryRegex) { match ->
                val text = match.groupValues.getOrNull(1).orEmpty().htmlFragmentToMarkdown()
                "\n### $text\n\n"
            }
            .replace(blockquoteRegex) { match ->
                val text = match.groupValues.getOrNull(1).orEmpty().htmlFragmentToMarkdown()
                "\n> $text\n"
            }
            .replace(listItemRegex) { match ->
                val text = match.groupValues.getOrNull(1).orEmpty()
                    .htmlTaskPrefixAndBody()
                    .let { (prefix, body) -> "$prefix${body.htmlFragmentToMarkdown()}" }
                "\n- $text\n"
            }
            .replace(paragraphRegex) { match ->
                val text = match.groupValues.getOrNull(1).orEmpty().htmlFragmentToMarkdown()
                "\n$text\n\n"
            }
            .replace(horizontalRuleRegex, "\n---\n")
            .replace(lineBreakRegex, "\n")
            .replace(closingBlockRegex, "\n")
            .replace(htmlTagRegex, " ")
            .decodeHtmlEntities()
            .normalizeReleaseNotesMarkdownLines()
    }

    private fun String.htmlTableToMarkdown(): String {
        val rows = tableRowRegex.findAll(this).mapNotNull { rowMatch ->
            val rowHtml = rowMatch.groupValues.getOrNull(1).orEmpty()
            val cells = tableCellRegex.findAll(rowHtml)
                .map { cellMatch ->
                    cellMatch.groupValues.getOrNull(2)
                        .orEmpty()
                        .htmlFragmentToMarkdown()
                        .asMarkdownTableCell()
                }
                .toList()
            cells.takeIf { it.isNotEmpty() }
                ?.let { rowHtml.contains("<th", ignoreCase = true) to it }
        }.toList()
        if (rows.isEmpty()) return ""
        return buildString {
            rows.forEachIndexed { index, row ->
                appendLine(
                    row.second.joinToString(
                        prefix = "| ",
                        separator = " | ",
                        postfix = " |"
                    )
                )
                if (index == 0 && row.first) {
                    appendLine(
                        row.second.joinToString(
                            prefix = "| ",
                            separator = " | ",
                            postfix = " |"
                        ) { "---" })
                }
            }
        }.trim()
    }

    private fun String.htmlFragmentToMarkdown(): String {
        return replace(imageRegex) { match ->
            val attrs = match.groupValues.getOrNull(1).orEmpty()
            val src = attrs.htmlAttribute("data-canonical-src")
                .ifBlank { attrs.htmlAttribute("src") }
                .normalizeGitHubUrl()
            val alt = attrs.htmlAttribute("alt").ifBlank { "image" }
            if (src.isBlank()) alt else "[$alt]($src)"
        }
            .replace(anchorRegex) { match ->
                val href = match.groupValues.getOrNull(1).orEmpty()
                    .htmlAttribute("href")
                    .normalizeGitHubUrl()
                val label = match.groupValues.getOrNull(2).orEmpty()
                    .htmlFragmentToMarkdown()
                    .trimMarkdownCodeFence()
                    .ifBlank { href }
                if (href.isBlank()) label else "[$label]($href)"
            }
            .replace(codeRegex) { match ->
                "`${
                    match.groupValues.getOrNull(1).orEmpty().stripHtml().decodeHtmlEntities().trim()
                }`"
            }
            .replace(strongRegex) { match ->
                "**${match.groupValues.getOrNull(1).orEmpty().htmlFragmentToMarkdown()}**"
            }
            .replace(emphasisRegex) { match ->
                "*${match.groupValues.getOrNull(1).orEmpty().htmlFragmentToMarkdown()}*"
            }
            .replace(lineBreakRegex, "\n")
            .replace(htmlTagRegex, " ")
            .decodeHtmlEntities()
            .replace(horizontalWhitespaceRegex, " ")
            .lines()
            .joinToString(" ") { it.trim() }
            .trim()
    }

    private fun String.htmlTaskPrefixAndBody(): Pair<String, String> {
        val checkbox = checkboxRegex.find(this)
            ?: return "" to this
        val attrs = checkbox.groupValues.getOrNull(1).orEmpty()
        val checked = attrs.contains("checked", ignoreCase = true)
        val body = replaceRange(checkbox.range, "").trim()
        return if (checked) "[x] " to body else "[ ] " to body
    }

    private fun String.trimMarkdownCodeFence(): String {
        val trimmed = trim()
        return if (trimmed.length >= 2 && trimmed.first() == '`' && trimmed.last() == '`') {
            trimmed.substring(1, trimmed.lastIndex).trim()
        } else {
            trimmed
        }
    }

    private fun String.htmlAttribute(name: String): String {
        val needle = name.trim().takeIf { it.isNotBlank() } ?: return ""
        var searchFrom = 0
        while (searchFrom < length) {
            val nameIndex = indexOf(needle, startIndex = searchFrom, ignoreCase = true)
            if (nameIndex < 0) return ""
            val before = getOrNull(nameIndex - 1)
            val afterNameIndex = nameIndex + needle.length
            if (before != null && (before.isLetterOrDigit() || before == '-' || before == '_')) {
                searchFrom = afterNameIndex
                continue
            }
            var cursor = afterNameIndex
            while (cursor < length && this[cursor].isWhitespace()) cursor += 1
            if (getOrNull(cursor) != '=') {
                searchFrom = afterNameIndex
                continue
            }
            cursor += 1
            while (cursor < length && this[cursor].isWhitespace()) cursor += 1
            val quote = getOrNull(cursor)
            if (quote != '"' && quote != '\'') {
                searchFrom = afterNameIndex
                continue
            }
            val valueStart = cursor + 1
            val valueEnd = indexOf(quote, startIndex = valueStart)
            if (valueEnd < 0) return ""
            return substring(valueStart, valueEnd).decodeHtmlEntities().trim()
        }
        return ""
    }

    private fun String.normalizeGitHubUrl(): String {
        val cleaned = decodeHtmlEntities().trim()
        return when {
            cleaned.startsWith("https://", ignoreCase = true) ||
                    cleaned.startsWith("http://", ignoreCase = true) -> cleaned

            cleaned.startsWith("/") -> "https://github.com$cleaned"
            else -> cleaned
        }
    }

    private fun String.asMarkdownTableCell(): String {
        return replace('\n', ' ')
            .replace("|", "\\|")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    private fun String.normalizeReleaseNotesMarkdownLines(): String {
        val lines = lines().map { it.trim() }
        val nextNonBlankLines = MutableList(lines.size) { "" }
        var nextNonBlank = ""
        for (index in lines.indices.reversed()) {
            nextNonBlankLines[index] = nextNonBlank
            if (lines[index].isNotBlank()) {
                nextNonBlank = lines[index]
            }
        }
        val normalized = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val nextLine = nextNonBlankLines[index]
            when {
                line.isBlank() &&
                        normalized.lastOrNull()?.startsWith("- ") == true &&
                        nextLine.startsWith("- ") -> Unit

                line.isBlank() && normalized.lastOrNull().isNullOrBlank() -> Unit
                line.isBlank() -> normalized += ""
                else -> normalized += line
            }
        }
        return normalized
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")
            .trim()
    }

    private fun String.stripHtml(): String {
        return replace(htmlTagRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    private fun String.decodeHtmlEntities(): String {
        return replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }
}
