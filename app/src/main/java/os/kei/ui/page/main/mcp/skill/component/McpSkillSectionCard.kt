@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp.skill.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import os.kei.ui.page.main.mcp.skill.model.SkillSection
import os.kei.ui.page.main.mcp.skill.model.SkillSectionItem
import os.kei.ui.page.main.mcp.skill.support.buildInlineStyledText
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.support.CopyModeSelectionContainer
import os.kei.ui.page.main.widget.support.copyModeAwareRow
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun SkillSectionCard(
    section: SkillSection,
    titleColor: Color,
    subtitleColor: Color,
    accentColor: Color,
    codeColor: Color,
    emptyItemText: String,
) {
    val titleSize =
        when (section.level) {
            1 -> 20.sp
            2 -> 18.sp
            else -> 17.sp
        }

    AppSurfaceCard(
        contentColor = titleColor,
        showIndication = false,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text =
                    buildInlineStyledText(
                        text = section.title,
                        baseStyle = SpanStyle(color = titleColor, fontWeight = FontWeight.SemiBold),
                        accentStyle =
                            SpanStyle(
                                color = accentColor,
                                background = accentColor.copy(alpha = 0.12f),
                                fontWeight = FontWeight.Medium,
                            ),
                        linkStyle =
                            SpanStyle(
                                color = accentColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium,
                            ),
                    ),
                color = titleColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = titleSize,
                lineHeight = (titleSize.value + 6f).sp,
            )

            if (section.items.isEmpty()) {
                Text(emptyItemText, color = subtitleColor)
            } else {
                section.items.forEachIndexed { index, item ->
                    SkillSectionItemView(
                        item = item,
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        accentColor = accentColor,
                        codeColor = codeColor,
                    )
                    if (index < section.items.lastIndex) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillSectionItemView(
    item: SkillSectionItem,
    titleColor: Color,
    subtitleColor: Color,
    accentColor: Color,
    codeColor: Color,
) {
    when (item) {
        is SkillSectionItem.SubHeading -> {
            val size = if (item.level <= 3) 16.sp else 15.sp
            CopyModeSelectionContainer(
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .copyModeAwareRow(copyPayload = item.text),
            ) {
                Text(
                    text =
                        buildInlineStyledText(
                            text = item.text,
                            baseStyle = SpanStyle(color = titleColor, fontWeight = FontWeight.Medium),
                            accentStyle =
                                SpanStyle(
                                    color = accentColor,
                                    background = accentColor.copy(alpha = 0.10f),
                                    fontWeight = FontWeight.Medium,
                                ),
                            linkStyle =
                                SpanStyle(
                                    color = accentColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium,
                                ),
                        ),
                    color = titleColor,
                    fontSize = size,
                    fontWeight = FontWeight.Medium,
                    lineHeight = (size.value + 6f).sp,
                )
            }
        }

        is SkillSectionItem.Paragraph -> {
            SkillParagraphLine(
                marker = "·",
                text = item.text,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
            )
        }

        is SkillSectionItem.Bullet -> {
            SkillParagraphLine(
                marker = "•",
                text = item.text,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
            )
        }

        is SkillSectionItem.Ordered -> {
            SkillParagraphLine(
                marker = "${item.index}.",
                text = item.text,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
            )
        }

        is SkillSectionItem.Code -> {
            AppSurfaceCard(
                containerColor = codeColor,
                contentColor = titleColor,
                showIndication = false,
            ) {
                CopyModeSelectionContainer(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .copyModeAwareRow(copyPayload = item.text),
                ) {
                    Text(
                        text = item.text,
                        modifier = Modifier.fillMaxWidth(),
                        color = titleColor,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillParagraphLine(
    marker: String,
    text: String,
    subtitleColor: Color,
    accentColor: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .copyModeAwareRow(copyPayload = text),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(marker, color = subtitleColor, fontSize = 15.sp)
        CopyModeSelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    buildInlineStyledText(
                        text = text,
                        baseStyle = SpanStyle(color = subtitleColor),
                        accentStyle =
                            SpanStyle(
                                color = accentColor,
                                background = accentColor.copy(alpha = 0.10f),
                                fontWeight = FontWeight.Medium,
                            ),
                        linkStyle =
                            SpanStyle(
                                color = accentColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium,
                            ),
                    ),
                color = subtitleColor,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
