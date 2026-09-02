@file:Suppress("FunctionName")

package os.kei.ui.page.main.host.pager

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.model.bottomPageIconScale
import os.kei.ui.page.main.os.appLucideListIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppSidebarToggleSize
import os.kei.ui.page.main.widget.chrome.AppSidebarWidth
import os.kei.ui.page.main.widget.chrome.appTopBarEdgePadding
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The tab bar's converted form: a leading rail of the app's sections.
 *
 * ## Regular Liquid Glass, deliberately
 *
 * The Materials guidance splits Liquid Glass into a *regular* variant, which blurs and adjusts the luminosity
 * of what is behind it, and a *clear* variant, which is highly translucent and meant for controls floating over
 * photos and video. It then names the case this is: "use the regular variant when background content might
 * create legibility issues, or when components have a significant amount of text, such as alerts, **sidebars**,
 * or popovers". A rail of five labels is a significant amount of text, so it takes the blurred variant that the
 * app's `AppLiquidFloatingSurface` already provides — not the thin translucency the floating docks use.
 *
 * ## Why it floats instead of taking a column, and where that falls short
 *
 * The pager is inset by [AppSidebarWidth] rather than placed in a `Row` beside the rail, which costs one padding
 * value instead of restructuring the host.
 *
 * **It does not yet deliver the background extension effect**, and an earlier version of this comment claimed it
 * did. The guidance asks to "extend visually rich content beneath the sidebar… to reinforce the separation", but
 * insetting the pager insets its background along with its content, so a page that paints its own artwork —
 * Home's hero, or any page under a managed background image — stops that artwork at the rail's outer edge and
 * leaves the strip beside it showing the scaffold's plain `surface`. Measured on the Pad AVD: uniform on OS,
 * which paints nothing, and a visible seam on Home, which does.
 *
 * Closing it means separating the background layer from the content layer so only the latter is inset, which is
 * a change to the pager host rather than to this file.
 *
 * ## Why only half of the edge gesture is here
 *
 * The guidance says "in iPadOS, people expect to use the built-in edge swipe gesture" to hide and show a
 * sidebar. Half of that does not port. On Android the leading edge belongs to the **system back gesture**, so an
 * app claiming a leading-edge drag to *open* a sidebar would be fighting predictive back on the same pixels —
 * worse than not offering it. This app has been here before: issue #21 was miuix-nav's `navSwipeDismiss`
 * claiming horizontal drags parent-first and taking them away from sliders, switches and text fields.
 *
 * So the gesture is only the *closing* half, and it lives on the rail rather than on a screen edge: drag the
 * sidebar toward the leading edge and it converts back to a tab bar. There is nothing to conflict with — the
 * rail scrolls in neither direction, and `draggable` only engages past touch slop, so the rows keep their taps.
 * Opening stays the button's job, which the adaptable style requires to exist anyway.
 */
@Composable
internal fun BoxScope.MainPagerSidebar(
    tabs: List<BottomPage>,
    selectedIndex: Int,
    backdrop: Backdrop,
    topInset: Dp,
    bottomInset: Dp,
    onSelected: (Int) -> Unit,
    onConvertToTabBar: () -> Unit,
) {
    val margin = appTopBarEdgePadding()
    val density = LocalDensity.current
    // Half the rail: a deliberate shove, not a brush past it.
    val dismissThresholdPx = with(density) { (AppSidebarWidth / 2f).toPx() }
    var dragTotalPx by remember { mutableFloatStateOf(0f) }
    val dragState =
        rememberDraggableState { delta ->
            dragTotalPx += delta
        }
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
                        if (dragTotalPx <= -dismissThresholdPx) onConvertToTabBar()
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
                // The toggle stays available in this shape too: the adaptable style keeps a button in *both*
                // forms, so a sidebar is never a state the user cannot leave.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MainPagerSidebarToggle(
                        backdrop = backdrop,
                        expanded = true,
                        onClick = onConvertToTabBar,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "KeiOS",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                tabs.forEachIndexed { index, page ->
                    MainPagerSidebarRow(
                        page = page,
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
private fun MainPagerSidebarRow(
    page: BottomPage,
    selected: Boolean,
    backdrop: Backdrop,
    onClick: () -> Unit,
) {
    val accent = MiuixTheme.colorScheme.primary
    val label = page.label
    val row: @Composable BoxScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconModifier = Modifier.size(22.dp).bottomPageIconScale(page)
            if (page.iconRes != null) {
                Icon(
                    painter = painterResource(id = page.iconRes),
                    contentDescription = null,
                    tint = if (page.keepOriginalColors) Color.Unspecified else accent,
                    modifier = iconModifier,
                )
            } else {
                page.icon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = iconModifier,
                    )
                }
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
    if (selected) {
        // Only the selected row takes a glass fill. "Use Liquid Glass effects sparingly… limit these effects
        // to the most important functional elements" — five glass plates stacked inside a glass rail would be
        // the overuse that guidance warns about, and would flatten the very distinction it is there to make.
        AppLiquidFloatingSurface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag(page.sidebarRowTestTag()),
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
                    .testTag(page.sidebarRowTestTag()),
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

/** The button that converts between the two shapes. Present in both, per the adaptable style. */
@Composable
internal fun MainPagerSidebarToggle(
    backdrop: Backdrop,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Which toggle this is, for instrumentation.
     *
     * A route that offers its own rail — the student guide does — puts a second one of these on
     * screen over a pager that still holds the first. One tag for both would make a profile journey's
     * tap ambiguous, so the caller names it.
     */
    testTag: String = KeiOsTestTags.MainSidebarToggle,
) {
    val description = if (expanded) "Show tab bar" else "Show sidebar"
    AppLiquidFloatingSurface(
        modifier = modifier.size(AppSidebarToggleSize).testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        backdrop = backdrop,
        onClick = onClick,
        content = {
            Icon(
                imageVector = appLucideListIcon(),
                contentDescription = description,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        },
    )
}

private fun BottomPage.sidebarRowTestTag(): String =
    when (this) {
        BottomPage.Home -> KeiOsTestTags.MainSidebarRowHome
        BottomPage.Os -> KeiOsTestTags.MainSidebarRowOs
        BottomPage.Mcp -> KeiOsTestTags.MainSidebarRowMcp
        BottomPage.GitHub -> KeiOsTestTags.MainSidebarRowGitHub
        BottomPage.Ba -> KeiOsTestTags.MainSidebarRowBa
    }
