package os.kei.ui.page.main.widget.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    preserveLineBreaks: Boolean = false
) {
    val blocks = remember(markdown, preserveLineBreaks) {
        parseAppMarkdownBlocks(markdown, preserveLineBreaks = preserveLineBreaks)
    }
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
        blocks.forEach { block ->
            AppMarkdownBlockView(
                block = block,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
                codeContainerColor = codeContainerColor,
                paragraphMarker = paragraphMarker
            )
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
    paragraphMarker: String?
) {
    when (block) {
        is AppMarkdownBlock.Heading -> AppMarkdownHeading(
            level = block.level,
            text = block.text,
            titleColor = titleColor,
            accentColor = accentColor
        )

        is AppMarkdownBlock.Paragraph -> {
            if (paragraphMarker.isNullOrBlank()) {
                AppMarkdownTextLine(
                    text = block.text,
                    subtitleColor = subtitleColor,
                    accentColor = accentColor
                )
            } else {
                AppMarkdownMarkedLine(
                    marker = paragraphMarker,
                    text = block.text,
                    subtitleColor = subtitleColor,
                    accentColor = accentColor
                )
            }
        }

        is AppMarkdownBlock.Bullet -> AppMarkdownMarkedLine(
            marker = "•",
            text = block.text,
            subtitleColor = subtitleColor,
            accentColor = accentColor
        )

        is AppMarkdownBlock.Ordered -> AppMarkdownMarkedLine(
            marker = "${block.index}.",
            text = block.text,
            subtitleColor = subtitleColor,
            accentColor = accentColor
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
    accentColor: Color
) {
    val size = when (level) {
        1 -> 18.sp
        2 -> 17.sp
        3 -> 16.sp
        else -> 15.sp
    }
    CopyModeSelectionContainer {
        Text(
            text = buildAppMarkdownInlineText(
                text = text,
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
                )
            ),
            color = titleColor,
            fontSize = size,
            fontWeight = FontWeight.SemiBold,
            lineHeight = (size.value + 6f).sp,
            modifier = Modifier
                .fillMaxWidth()
                .copyModeAwareRow(copyPayload = text)
        )
    }
}

@Composable
private fun AppMarkdownMarkedLine(
    marker: String,
    text: String,
    subtitleColor: Color,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .copyModeAwareRow(copyPayload = text),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(marker, color = subtitleColor, fontSize = 15.sp, lineHeight = 22.sp)
        AppMarkdownTextLine(
            text = text,
            subtitleColor = subtitleColor,
            accentColor = accentColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AppMarkdownTextLine(
    text: String,
    subtitleColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    CopyModeSelectionContainer {
        Text(
            text = buildAppMarkdownInlineText(
                text = text,
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
                )
            ),
            color = subtitleColor,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = modifier
                .fillMaxWidth()
                .copyModeAwareRow(copyPayload = text)
        )
    }
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
