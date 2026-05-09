package os.kei.ui.page.main.mcp.skill.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import os.kei.ui.page.main.mcp.skill.state.McpSkillPageContentState
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn

@Composable
internal fun McpSkillContentList(
    innerPadding: PaddingValues,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    contentState: McpSkillPageContentState,
    clawCardTitle: String,
    clawCardSummary: String,
    clawPrompt: String,
    copyClawPromptText: String,
    clawPromptCopiedToast: String,
    emptyItemText: String,
    titleColor: Color,
    subtitleColor: Color,
    accentColor: Color,
    codeColor: Color,
    modifier: Modifier = Modifier
) {
    AppPageLazyColumn(
        innerPadding = innerPadding,
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        bottomExtra = AppChromeTokens.pageBottomInsetExtra,
        sectionSpacing = AppChromeTokens.pageSectionGap
    ) {
        item {
            McpSkillClawGuideCard(
                title = clawCardTitle,
                summary = clawCardSummary,
                prompt = clawPrompt,
                copyContentDescription = copyClawPromptText,
                copiedToastText = clawPromptCopiedToast,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
                codeColor = codeColor
            )
        }
        itemsIndexed(
            items = contentState.sections,
            key = { index, section -> "${section.level}:${section.title}:$index" },
            contentType = { _, _ -> "mcp_skill_section" }
        ) { _, section ->
            SkillSectionCard(
                section = section,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accentColor = accentColor,
                codeColor = codeColor,
                emptyItemText = emptyItemText
            )
        }
    }
}
