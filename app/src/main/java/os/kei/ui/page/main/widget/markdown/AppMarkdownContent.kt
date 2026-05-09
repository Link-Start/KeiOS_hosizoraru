package os.kei.ui.page.main.widget.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.ui.page.main.widget.support.CopyModeSelectionContainer
import os.kei.ui.page.main.widget.support.copyModeAwareRow
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun AppMarkdownContent(
    markdown: String,
    titleColor: Color,
    subtitleColor: Color,
    accentColor: Color,
    codeContainerColor: Color,
    modifier: Modifier = Modifier,
    paragraphMarker: String? = null,
    emptyText: String? = null,
    preserveLineBreaks: Boolean = false,
    onOpenLink: ((String) -> Unit)? = null
) {
    val blocksState = produceState<List<AppMarkdownBlock>>(
        initialValue = emptyList(),
        markdown,
        preserveLineBreaks
    ) {
        value = emptyList()
        value = withContext(Dispatchers.Default) {
            parseAppMarkdownBlocks(markdown, preserveLineBreaks = preserveLineBreaks)
        }
    }
    AppMarkdownBlocksContent(
        blocks = blocksState.value,
        titleColor = titleColor,
        subtitleColor = subtitleColor,
        accentColor = accentColor,
        codeContainerColor = codeContainerColor,
        modifier = modifier,
        paragraphMarker = paragraphMarker,
        emptyText = emptyText,
        onOpenLink = onOpenLink
    )
}

@Composable
internal fun AppMarkdownBlocksContent(
    blocks: List<AppMarkdownBlock>,
    titleColor: Color,
    subtitleColor: Color,
    accentColor: Color,
    codeContainerColor: Color,
    modifier: Modifier = Modifier,
    paragraphMarker: String? = null,
    emptyText: String? = null,
    onOpenLink: ((String) -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (blocks.isEmpty()) {
            if (!emptyText.isNullOrBlank()) {
                Text(emptyText, color = subtitleColor, fontSize = 15.sp, lineHeight = 22.sp)
            }
            return@Column
        }
        var index = 0
        while (index < blocks.size) {
            val block = blocks[index]
            if (block is AppMarkdownBlock.TableRow) {
                val rows = mutableListOf<AppMarkdownBlock.TableRow>()
                while (index < blocks.size && blocks[index] is AppMarkdownBlock.TableRow) {
                    rows += blocks[index] as AppMarkdownBlock.TableRow
                    index += 1
                }
                AppMarkdownTable(
                    rows = rows,
                    titleColor = titleColor,
                    subtitleColor = subtitleColor,
                    accentColor = accentColor,
                    onOpenLink = onOpenLink
                )
                continue
            }
            AppMarkdownBlockView(
                block = block,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
                codeContainerColor = codeContainerColor,
                paragraphMarker = paragraphMarker,
                onOpenLink = onOpenLink
            )
            index += 1
        }
    }
}

@Composable
private fun AppMarkdownBlockView(
    block: AppMarkdownBlock,
    titleColor: Color,
    subtitleColor: Color,
    accentColor: Color,
    codeContainerColor: Color,
    paragraphMarker: String?,
    onOpenLink: ((String) -> Unit)?
) {
    when (block) {
        is AppMarkdownBlock.Heading -> AppMarkdownHeading(
            level = block.level,
            text = block.text,
            titleColor = titleColor,
            accentColor = accentColor,
            onOpenLink = onOpenLink
        )

        is AppMarkdownBlock.Paragraph -> {
            if (paragraphMarker.isNullOrBlank()) {
                AppMarkdownTextLine(
                    text = block.text,
                    subtitleColor = subtitleColor,
                    accentColor = accentColor,
                    onOpenLink = onOpenLink
                )
            } else {
                AppMarkdownMarkedLine(
                    marker = paragraphMarker,
                    text = block.text,
                    subtitleColor = subtitleColor,
                    accentColor = accentColor,
                    onOpenLink = onOpenLink
                )
            }
        }

        is AppMarkdownBlock.Bullet -> AppMarkdownMarkedLine(
            marker = "•",
            text = block.text,
            subtitleColor = subtitleColor,
            accentColor = accentColor,
            onOpenLink = onOpenLink
        )

        is AppMarkdownBlock.Task -> AppMarkdownMarkedLine(
            marker = if (block.checked) "✓" else "□",
            text = block.text,
            subtitleColor = subtitleColor,
            accentColor = accentColor,
            onOpenLink = onOpenLink
        )

        is AppMarkdownBlock.Ordered -> AppMarkdownMarkedLine(
            marker = "${block.index}.",
            text = block.text,
            subtitleColor = subtitleColor,
            accentColor = accentColor,
            onOpenLink = onOpenLink
        )

        is AppMarkdownBlock.Quote -> AppMarkdownMarkedLine(
            marker = "›",
            text = block.text,
            subtitleColor = subtitleColor,
            accentColor = accentColor,
            onOpenLink = onOpenLink
        )

        is AppMarkdownBlock.TableRow -> AppMarkdownTable(
            rows = listOf(block),
            titleColor = titleColor,
            subtitleColor = subtitleColor,
            accentColor = accentColor,
            onOpenLink = onOpenLink
        )

        AppMarkdownBlock.HorizontalRule -> AppMarkdownHorizontalRule(
            color = subtitleColor.copy(alpha = 0.22f)
        )

        is AppMarkdownBlock.Code -> AppMarkdownCodeBlock(
            text = block.text,
            titleColor = titleColor,
            codeContainerColor = codeContainerColor
        )
    }
}

@Composable
private fun AppMarkdownHeading(
    level: Int,
    text: String,
    titleColor: Color,
    accentColor: Color,
    onOpenLink: ((String) -> Unit)?
) {
    val size = when (level) {
        1 -> 18.sp
        2 -> 17.sp
        3 -> 16.sp
        else -> 15.sp
    }
    CopyModeSelectionContainer {
        AppMarkdownInlineText(
            text = text,
            color = titleColor,
            fontSize = size,
            fontWeight = FontWeight.SemiBold,
            lineHeight = (size.value + 6f).sp,
            modifier = Modifier
                .fillMaxWidth()
                .markdownCopyAwareRow(copyPayload = text, linksEnabled = onOpenLink != null),
            baseStyle = SpanStyle(color = titleColor, fontWeight = FontWeight.SemiBold),
            accentStyle = SpanStyle(
                color = accentColor,
                background = accentColor.copy(alpha = 0.10f),
                fontWeight = FontWeight.Medium
            ),
            linkStyle = SpanStyle(
                color = accentColor,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Medium
            ),
            onOpenLink = onOpenLink
        )
    }
}

@Composable
private fun AppMarkdownMarkedLine(
    marker: String,
    text: String,
    subtitleColor: Color,
    accentColor: Color,
    onOpenLink: ((String) -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .markdownCopyAwareRow(copyPayload = text, linksEnabled = onOpenLink != null),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(marker, color = subtitleColor, fontSize = 15.sp, lineHeight = 22.sp)
        AppMarkdownTextLine(
            text = text,
            subtitleColor = subtitleColor,
            accentColor = accentColor,
            modifier = Modifier.weight(1f),
            onOpenLink = onOpenLink
        )
    }
}

@Composable
private fun AppMarkdownTextLine(
    text: String,
    subtitleColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onOpenLink: ((String) -> Unit)?
) {
    CopyModeSelectionContainer {
        AppMarkdownInlineText(
            text = text,
            color = subtitleColor,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = modifier
                .fillMaxWidth()
                .markdownCopyAwareRow(copyPayload = text, linksEnabled = onOpenLink != null),
            baseStyle = SpanStyle(color = subtitleColor),
            accentStyle = SpanStyle(
                color = accentColor,
                background = accentColor.copy(alpha = 0.10f),
                fontWeight = FontWeight.Medium
            ),
            linkStyle = SpanStyle(
                color = accentColor,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Medium
            ),
            onOpenLink = onOpenLink
        )
    }
}

@Composable
private fun AppMarkdownTable(
    rows: List<AppMarkdownBlock.TableRow>,
    titleColor: Color,
    subtitleColor: Color,
    accentColor: Color,
    onOpenLink: ((String) -> Unit)?
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = subtitleColor.copy(alpha = 0.06f),
                shape = shape
            )
            .border(
                width = 0.6.dp,
                color = subtitleColor.copy(alpha = 0.12f),
                shape = shape
            )
    ) {
        rows.forEachIndexed { rowIndex, row ->
            val cells = row.cells.filter { it.isNotBlank() }
            val textColor = if (row.header) titleColor else subtitleColor
            val rowModifier = Modifier
                .fillMaxWidth()
                .background(if (row.header) accentColor.copy(alpha = 0.12f) else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 8.dp)
            if (cells.size <= 3) {
                Row(
                    modifier = rowModifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    cells.forEach { cell ->
                        AppMarkdownTableCell(
                            cell = cell,
                            textColor = textColor,
                            accentColor = accentColor,
                            header = row.header,
                            modifier = Modifier.weight(1f),
                            onOpenLink = onOpenLink
                        )
                    }
                }
            } else {
                Column(
                    modifier = rowModifier,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    cells.forEach { cell ->
                        AppMarkdownTableCell(
                            cell = cell,
                            textColor = textColor,
                            accentColor = accentColor,
                            header = row.header,
                            modifier = Modifier.fillMaxWidth(),
                            onOpenLink = onOpenLink
                        )
                    }
                }
            }
            if (rowIndex < rows.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(subtitleColor.copy(alpha = 0.10f))
                )
            }
        }
    }
}

@Composable
private fun AppMarkdownTableCell(
    cell: String,
    textColor: Color,
    accentColor: Color,
    header: Boolean,
    modifier: Modifier,
    onOpenLink: ((String) -> Unit)?
) {
    AppMarkdownInlineText(
        text = cell,
        color = textColor,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier,
        baseStyle = SpanStyle(
            color = textColor,
            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal
        ),
        accentStyle = SpanStyle(
            color = accentColor,
            background = accentColor.copy(alpha = 0.10f),
            fontWeight = FontWeight.Medium
        ),
        linkStyle = SpanStyle(
            color = accentColor,
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Medium
        ),
        onOpenLink = onOpenLink
    )
}

@Composable
private fun AppMarkdownHorizontalRule(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(1.dp)
            .background(color)
    )
}

@Composable
private fun AppMarkdownInlineText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier,
    baseStyle: SpanStyle,
    accentStyle: SpanStyle,
    linkStyle: SpanStyle,
    fontWeight: FontWeight? = null,
    onOpenLink: ((String) -> Unit)?
) {
    val annotated = buildAppMarkdownInlineText(
        text = text,
        baseStyle = baseStyle,
        accentStyle = accentStyle,
        linkStyle = linkStyle,
        onOpenLink = onOpenLink
    )
    if (onOpenLink == null) {
        Text(
            text = annotated,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
            modifier = modifier
        )
    } else {
        val firstLink = remember(annotated) { annotated.firstMarkdownUrlOrNull() }
        BasicText(
            text = annotated,
            modifier = if (firstLink == null) {
                modifier
            } else {
                modifier.clickable { onOpenLink(firstLink) }
            },
            style = TextStyle(
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                lineHeight = lineHeight
            )
        )
    }
}

private fun androidx.compose.ui.text.AnnotatedString.firstMarkdownUrlOrNull(): String? {
    return getLinkAnnotations(0, length)
        .firstOrNull()
        ?.item
        ?.markdownUrlOrNull()
}

private fun LinkAnnotation.markdownUrlOrNull(): String? {
    return when (this) {
        is LinkAnnotation.Url -> url
        is LinkAnnotation.Clickable -> tag
        else -> null
    }
}

private fun Modifier.markdownCopyAwareRow(
    copyPayload: String,
    linksEnabled: Boolean
): Modifier {
    return if (linksEnabled) this else copyModeAwareRow(copyPayload = copyPayload)
}

@Composable
private fun AppMarkdownCodeBlock(
    text: String,
    titleColor: Color,
    codeContainerColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = codeContainerColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .copyModeAwareRow(copyPayload = text)
    ) {
        CopyModeSelectionContainer {
            Text(
                text = text,
                color = titleColor,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
