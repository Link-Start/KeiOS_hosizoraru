@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.page.component

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.host.pager.MainPagerSidebarToggle
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppSidebarWidth
import os.kei.ui.page.main.widget.chrome.appTopBarEdgePadding
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The guide's section bar in its converted, leading-rail form.
 *
 * Six sections is where a bottom bar starts to strain: it is one past the five the label rule allows on a
 * phone, so the bar goes iconic, and six icons is exactly the row a reader has to decode rather than read.
 * A rail has room for the labels down the side and costs the content only [AppSidebarWidth], which on a
 * panel is width the page was leaving as gutter anyway.
 *
 * Deliberately the same vocabulary as `MainPagerSidebar` — regular Liquid Glass for a rail of text, a glass
 * fill on the selected row only, drag-left or the toggle to convert back — because this is the same control
 * in the same shape, one level down. The toggle writes this page's own stored answer rather than the
 * pager's, because six sections and five are not the same question -- see
 * `BaStudentGuideUiPreferencesStore`.
 */
/** The guide's convert button, distinct from the pager's so a journey's tap is unambiguous. */
internal const val GuideSidebarToggleTestTag = KeiOsTestTags.BaStudentGuideSidebarToggle

/** Gap between the back button and the convert-to-rail button in the top row. */
internal val GuideSidebarToggleGap: Dp = 8.dp

@Composable
internal fun BoxScope.BaStudentGuideSidebar(
    title: String,
    tabs: List<GuideBottomTab>,
    selectedIndex: Int,
    backdrop: Backdrop,
    topInset: Dp,
    bottomInset: Dp,
    onSelected: (Int) -> Unit,
    onConvertToBottomBar: () -> Unit,
) {
    val margin = appTopBarEdgePadding()
    val density = LocalDensity.current
    // Half the rail: a deliberate shove, not a brush past it. Same threshold as the pager's.
    val dismissThresholdPx = with(density) { (AppSidebarWidth / 2f).toPx() }
    var dragTotalPx by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta -> dragTotalPx += delta }
    AppLiquidFloatingSurface(
        modifier =
            Modifier
                .align(Alignment.CenterStart)
                .width(AppSidebarWidth - margin)
                .fillMaxSize()
                .padding(
                    start = margin,
                    top = topInset + AppChromeTokens.topBarChromeTopPadding,
                    bottom = bottomInset + 12.dp,
                ).draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { dragTotalPx = 0f },
                    onDragStopped = {
                        if (dragTotalPx <= -dismissThresholdPx) onConvertToBottomBar()
                        dragTotalPx = 0f
                    },
                ),
        shape = RoundedCornerShape(28.dp),
        backdrop = backdrop,
        content = {
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // The toggle is present in this shape too, so a rail is never a state with no way out.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MainPagerSidebarToggle(
                        backdrop = backdrop,
                        expanded = true,
                        onClick = onConvertToBottomBar,
                        testTag = GuideSidebarToggleTestTag,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                tabs.forEachIndexed { index, tab ->
                    BaStudentGuideSidebarRow(
                        tab = tab,
                        selected = index == selectedIndex,
                        backdrop = backdrop,
                        onClick = { onSelected(index) },
                    )
                }
            }
        },
    )
}

@Composable
private fun BaStudentGuideSidebarRow(
    tab: GuideBottomTab,
    selected: Boolean,
    backdrop: Backdrop,
    onClick: () -> Unit,
) {
    val accent = MiuixTheme.colorScheme.primary
    val label = stringResource(tab.labelRes)
    val row: @Composable BoxScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconModifier = Modifier.size(22.dp)
            if (tab.localLogoRes != null) {
                Icon(
                    painter = painterResource(id = tab.localLogoRes),
                    contentDescription = null,
                    // The same three that take the theme tint in the bottom bar: the rest are artwork
                    // and keep their own colours.
                    tint = if (tab.tintsLocalLogo()) accent else Color.Unspecified,
                    modifier = iconModifier,
                )
            } else {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = iconModifier,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) accent else MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    // A glass fill on the selected row only, for the reason the pager's rail gives: six glass plates
    // inside a glass rail is the overuse the guidance warns about, and flattens the distinction.
    if (selected) {
        AppLiquidFloatingSurface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag(guideSidebarRowTestTag(tab)),
            shape = RoundedCornerShape(16.dp),
            backdrop = backdrop,
            onClick = onClick,
            content = row,
        )
    } else {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag(guideSidebarRowTestTag(tab)),
            contentAlignment = Alignment.Center,
        ) {
            AppLiquidFloatingSurface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                backdrop = null,
                onClick = onClick,
                content = row,
            )
        }
    }
}
