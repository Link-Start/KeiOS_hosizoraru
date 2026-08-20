@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.widget.core.AppCardBodyColumn
import os.kei.ui.page.main.widget.core.AppCardHeader
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.core.rememberExpandableCardVisibilityState
import os.kei.ui.page.main.widget.core.shouldApplyEdgeStackToExpandableCard
import os.kei.ui.page.main.widget.motion.appExpandIn
import os.kei.ui.page.main.widget.motion.appExpandOut
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppLiquidAccordionCard(
    backdrop: Backdrop?,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    headerStartAction: (@Composable () -> Unit)? = null,
    titleAccessory: (@Composable RowScope.() -> Unit)? = null,
    headerActions: (@Composable () -> Unit)? = null,
    onHeaderLongClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    clipContent: Boolean = true,
    headerContentPadding: PaddingValues = CardLayoutRhythm.cardContentPadding,
    headerHorizontalSpacing: Dp = CardLayoutRhythm.controlRowGap,
    headerActionSpacing: Dp = CardLayoutRhythm.infoRowGap,
    contentPadding: PaddingValues = CardLayoutRhythm.cardContentPadding,
    verticalSpacing: Dp = CardLayoutRhythm.sectionGap,
    content: @Composable () -> Unit,
) {
    AppLiquidExpandableCardFrame(
        backdrop = backdrop,
        title = title,
        subtitle = subtitle,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
        headerStartAction = headerStartAction,
        titleAccessory = titleAccessory,
        headerActions = headerActions,
        onHeaderLongClick = onHeaderLongClick,
        containerColor = containerColor,
        clipContent = clipContent,
        headerContentPadding = headerContentPadding,
        headerHorizontalSpacing = headerHorizontalSpacing,
        headerActionSpacing = headerActionSpacing,
        contentPadding = contentPadding,
        verticalSpacing = verticalSpacing,
        content = content,
    )
}

@Composable
fun AppLiquidExpandableSection(
    backdrop: Backdrop?,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    headerStartAction: (@Composable () -> Unit)? = null,
    headerActions: (@Composable () -> Unit)? = null,
    onHeaderLongClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    clipContent: Boolean = true,
    content: @Composable () -> Unit,
) {
    AppLiquidExpandableCardFrame(
        backdrop = backdrop,
        title = title,
        subtitle = subtitle,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        headerStartAction = headerStartAction,
        headerActions = headerActions,
        onHeaderLongClick = onHeaderLongClick,
        containerColor = containerColor,
        clipContent = clipContent,
        headerContentPadding = CardLayoutRhythm.cardContentPadding,
        headerHorizontalSpacing = CardLayoutRhythm.controlRowGap,
        headerActionSpacing = CardLayoutRhythm.infoRowGap,
        contentPadding =
            PaddingValues(
                start = CardLayoutRhythm.cardHorizontalPadding,
                end = CardLayoutRhythm.cardHorizontalPadding,
                bottom = CardLayoutRhythm.cardVerticalPadding,
            ),
        verticalSpacing = CardLayoutRhythm.sectionGap,
        content = content,
    )
}

@Composable
private fun AppLiquidExpandableCardFrame(
    backdrop: Backdrop?,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    headerStartAction: (@Composable () -> Unit)?,
    titleAccessory: (@Composable RowScope.() -> Unit)? = null,
    headerActions: (@Composable () -> Unit)?,
    onHeaderLongClick: (() -> Unit)?,
    containerColor: Color?,
    clipContent: Boolean,
    headerContentPadding: PaddingValues,
    headerHorizontalSpacing: Dp,
    headerActionSpacing: Dp,
    contentPadding: PaddingValues,
    verticalSpacing: Dp,
    content: @Composable () -> Unit,
) {
    val sectionSurface = containerColor ?: MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
    val contentVisibilityState = rememberExpandableCardVisibilityState(expanded)

    AppSurfaceCard(
        modifier = modifier,
        backdrop = backdrop,
        containerColor = sectionSurface,
        borderColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.14f),
        showIndication = false,
        exportBackdropToContent = true,
        clipContent = clipContent,
        edgeStackEnabled =
            shouldApplyEdgeStackToExpandableCard(
                currentState = contentVisibilityState.currentState,
                targetState = contentVisibilityState.targetState,
            ),
    ) {
        AppCardHeader(
            title = title,
            subtitle = subtitle,
            startAction = headerStartAction,
            titleAccessory = titleAccessory,
            endActions =
                if (headerActions != null) {
                    { headerActions.invoke() }
                } else {
                    null
                },
            expandable = true,
            expanded = expanded,
            expandTint = MiuixTheme.colorScheme.primary,
            contentPadding = headerContentPadding,
            horizontalSpacing = headerHorizontalSpacing,
            endActionSpacing = headerActionSpacing,
            onClick = { onExpandedChange(!expanded) },
            onLongClick = onHeaderLongClick,
        )
        AnimatedVisibility(
            visibleState = contentVisibilityState,
            enter = appExpandIn(),
            exit = appExpandOut(),
        ) {
            AppCardBodyColumn(
                contentPadding = contentPadding,
                verticalSpacing = verticalSpacing,
                content = { content() },
            )
        }
    }
}
