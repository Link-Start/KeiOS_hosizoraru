@file:Suppress("FunctionName", "PropertyName")

package os.kei.ui.page.main.widget.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import os.kei.ui.page.main.widget.glass.LiquidMenuPresentation
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import kotlin.math.roundToInt

enum class SnapshotPopupPlacement {
    Dropdown,
    ButtonEnd,
    ActionBarCenter,
}

internal data class SnapshotPopupLayoutInfo(
    val offset: IntOffset,
    val showBelow: Boolean,
    val showAbove: Boolean,
    val transformOrigin: TransformOrigin,
)

internal data class SnapshotPopupSafeInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** Identifies the anchored panel itself, as opposed to anything inside it. */
const val SnapshotMenuPanelTestTag = "snapshot_menu_panel"

/**
 * An anchored menu or dropdown panel.
 *
 * Renders in the activity window, not a `Popup`. Every call site already threaded a `Backdrop` through to
 * the panel inside, and every one of them was discarded: a Popup is a second window, a `LayerBackdrop`
 * resolves its consumer/producer offset through shared `LayoutCoordinates`, and
 * `LiquidBackdropWindowBoundary` therefore blanks the scene backdrop for exactly that reason. So
 * `activeGlassBackdrop` returned null and the panel drew a flat filled card — with the blur, lens,
 * vibrancy and shadow values configured for it never reaching a shader. Hosting in-window is what makes
 * the material real.
 *
 * The signature is unchanged so the call sites did not have to move. Behaviour differences worth knowing:
 *
 * - **Placement is identical.** The same miuix position provider and the same
 *   [calculateSnapshotPopupLayout] run, just against the activity window's bounds.
 * - **The panel can no longer overflow the window.** The Popup set `clippingEnabled = false`; menus were
 *   clamped inside the safe insets regardless, so nothing reachable became unreachable.
 * - **Back and outside-tap moved.** The Popup was focusable and owned both, including a deliberate
 *   host-window dispatcher lookup to work around miuix-nav's entry-scoped owner. In-window there is no
 *   second window to take focus, so a plain `BackHandler` sees the event and the workaround is gone.
 * - **Focus stays where it was.** Not taking focus is what lets a dropdown open beside a focused text
 *   field without dismissing the keyboard.
 */
@Composable
fun SnapshotWindowListPopup(
    show: Boolean,
    modifier: Modifier = Modifier,
    popupPositionProvider: PopupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.Start,
    anchorBounds: IntRect? = null,
    placement: SnapshotPopupPlacement = SnapshotPopupPlacement.Dropdown,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    maxHeight: Dp? = null,
    minWidth: Dp = 0.dp,
    maxWidth: Dp? = 280.dp,
    matchAnchorWidth: Boolean = false,
    anchorBoundsProvider: (() -> IntRect?)? = null,
    content: @Composable () -> Unit,
) {
    var mounted by remember { mutableStateOf(false) }
    SideEffect {
        if (show) mounted = true
    }
    val presentedAnchorBounds =
        if (anchorBoundsProvider != null && (show || mounted)) anchorBoundsProvider() else anchorBounds
    val density = LocalDensity.current
    val anchorWidthDp =
        remember(presentedAnchorBounds, density) {
            presentedAnchorBounds?.let { with(density) { it.width.toDp() } } ?: 0.dp
        }
    val resolvedMinWidth =
        if (matchAnchorWidth) maxOf(minWidth, anchorWidthDp) else minWidth
    val panelMinWidth = maxWidth?.let { resolvedMinWidth.coerceAtMost(it) } ?: resolvedMinWidth

    LiquidMenuPresentation(
        show = show,
        anchorBounds = presentedAnchorBounds,
        popupPositionProvider = popupPositionProvider,
        alignment = alignment,
        placement = placement,
        onDismissRequest = onDismissRequest,
        onDismissFinished = {
            mounted = false
            onDismissFinished?.invoke()
        },
    ) {
        Box(
            modifier =
                modifier
                    // The panel used to be measurable as "the second Compose root", because it lived in
                    // its own window. In-window there is only one root, so it identifies itself.
                    .testTag(SnapshotMenuPanelTestTag)
                    .defaultMinSize(minWidth = panelMinWidth)
                    .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
                    .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier),
        ) {
            content()
        }
    }
}

@Composable
fun SnapshotWindowBottomSheet(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    backgroundColor: Color? = null,
    enableWindowDim: Boolean = true,
    cornerRadius: Dp = BottomSheetDefaults.cornerRadius,
    sheetMaxWidth: Dp = BottomSheetDefaults.maxWidth,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    outsideMargin: DpSize = BottomSheetDefaults.outsideMargin,
    insideMargin: DpSize = DpSize(BottomSheetDefaults.insideMargin.width, 14.dp),
    defaultWindowInsetsPadding: Boolean = true,
    dragHandleColor: Color? = null,
    allowDismiss: Boolean = true,
    onBlockedDismissRequest: (() -> Unit)? = null,
    enableNestedScroll: Boolean = true,
    initialDetent: LiquidSheetInitialDetent = LiquidSheetInitialDetent.ThreeQuarter,
    surfaceTone: LiquidSheetSurfaceTone = LiquidSheetSurfaceTone.Default,
    preferExportedBackdrop: Boolean = false,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var wasShown by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) {
            wasShown = true
        } else if (wasShown) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    val currentOnBlockedDismissRequest by rememberUpdatedState(onBlockedDismissRequest)
    val blockedDismissRequestGate = remember(show) { BlockedDismissRequestGate() }
    val requestBlockedDismiss: () -> Unit = {
        blockedDismissRequestGate.dispatch {
            currentOnBlockedDismissRequest?.invoke()
        }
    }

    LiquidGlassBottomSheet(
        show = show,
        modifier = modifier,
        title = title,
        startAction = startAction,
        endAction = endAction,
        backgroundColor = backgroundColor,
        enableWindowDim = enableWindowDim,
        cornerRadius = cornerRadius,
        sheetMaxWidth = sheetMaxWidth,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        outsideMargin = outsideMargin,
        insideMargin = insideMargin,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        dragHandleColor = dragHandleColor,
        allowDismiss = allowDismiss,
        onBlockedDismissRequest =
            requestBlockedDismiss.takeIf {
                onBlockedDismissRequest != null
            },
        enableNestedScroll = enableNestedScroll,
        initialDetent = initialDetent,
        surfaceTone = surfaceTone,
        preferExportedBackdrop = preferExportedBackdrop,
        content = content,
    )
}

private const val BlockedSheetDismissDeduplicationWindowNanos = 200_000_000L

internal class BlockedDismissRequestGate(
    private val deduplicationWindowNanos: Long = BlockedSheetDismissDeduplicationWindowNanos,
) {
    private var lastDispatchNanos: Long? = null

    fun dispatch(
        nowNanos: Long = System.nanoTime(),
        onDispatch: () -> Unit,
    ): Boolean {
        val previous = lastDispatchNanos
        if (previous != null && nowNanos - previous < deduplicationWindowNanos) return false
        lastDispatchNanos = nowNanos
        onDispatch()
        return true
    }
}

private fun PaddingValues.toIntRect(
    density: androidx.compose.ui.unit.Density,
    layoutDirection: LayoutDirection,
): IntRect =
    with(density) {
        IntRect(
            left = calculateLeftPadding(layoutDirection).roundToPx(),
            top = calculateTopPadding().roundToPx(),
            right = calculateRightPadding(layoutDirection).roundToPx(),
            bottom = calculateBottomPadding().roundToPx(),
        )
    }

private fun PopupPositionProvider.Align.normalizeForDropdown(layoutDirection: LayoutDirection): PopupPositionProvider.Align =
    when (this) {
        PopupPositionProvider.Align.End,
        PopupPositionProvider.Align.TopEnd,
        PopupPositionProvider.Align.BottomEnd,
        -> {
            if (layoutDirection == LayoutDirection.Ltr) {
                PopupPositionProvider.Align.End
            } else {
                PopupPositionProvider.Align.Start
            }
        }

        PopupPositionProvider.Align.Start,
        PopupPositionProvider.Align.TopStart,
        PopupPositionProvider.Align.BottomStart,
        -> {
            if (layoutDirection == LayoutDirection.Ltr) {
                PopupPositionProvider.Align.Start
            } else {
                PopupPositionProvider.Align.End
            }
        }
    }

internal fun calculateSnapshotPopupWindowBounds(
    windowSize: IntSize,
    safeInsets: SnapshotPopupSafeInsets,
): IntRect {
    val width = windowSize.width.coerceAtLeast(0)
    val height = windowSize.height.coerceAtLeast(0)
    val left = safeInsets.left.coerceIn(0, width)
    val top = safeInsets.top.coerceIn(0, height)
    val right = (width - safeInsets.right.coerceAtLeast(0)).coerceIn(left, width)
    val bottom = (height - safeInsets.bottom.coerceAtLeast(0)).coerceIn(top, height)
    return IntRect(left = left, top = top, right = right, bottom = bottom)
}

internal fun calculateSnapshotPopupLayout(
    anchorBounds: IntRect,
    windowBounds: IntRect,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
    popupMargin: IntRect,
    alignment: PopupPositionProvider.Align,
    placement: SnapshotPopupPlacement,
    providerOffset: IntOffset,
): SnapshotPopupLayoutInfo {
    val popupWidth = popupContentSize.width.coerceAtLeast(0)
    val popupHeight = popupContentSize.height.coerceAtLeast(0)
    val normalizedAlignment = alignment.normalizeForDropdown(layoutDirection)
    val rawX =
        when (placement) {
            SnapshotPopupPlacement.Dropdown -> {
                providerOffset.x
            }

            SnapshotPopupPlacement.ButtonEnd -> {
                anchorBounds.right - popupWidth - popupMargin.right
            }

            SnapshotPopupPlacement.ActionBarCenter -> {
                anchorBounds.left + (anchorBounds.width - popupWidth) / 2
            }
        }
    val minX = windowBounds.left + popupMargin.left
    val maxX =
        (windowBounds.right - popupWidth - popupMargin.right)
            .coerceAtLeast(minX)
    val minY = windowBounds.top + popupMargin.top
    val maxY =
        (windowBounds.bottom - popupHeight - popupMargin.bottom)
            .coerceAtLeast(minY)
    val offset =
        IntOffset(
            x = rawX.coerceIn(minX, maxX),
            y = providerOffset.y.coerceIn(minY, maxY),
        )

    val popupCenterY = offset.y + popupHeight / 2f
    val anchorCenterY = anchorBounds.top + anchorBounds.height / 2f
    val showBelow = popupCenterY > anchorCenterY
    val showAbove = popupCenterY < anchorCenterY
    val pivotX =
        when (placement) {
            SnapshotPopupPlacement.ActionBarCenter -> {
                0.5f
            }

            SnapshotPopupPlacement.Dropdown,
            SnapshotPopupPlacement.ButtonEnd,
            -> {
                val attachToEnd =
                    placement == SnapshotPopupPlacement.ButtonEnd ||
                        normalizedAlignment == PopupPositionProvider.Align.End
                val attachmentX =
                    if (attachToEnd) {
                        anchorBounds.right - popupMargin.right
                    } else {
                        anchorBounds.left + popupMargin.left
                    }
                if (popupWidth > 0) {
                    ((attachmentX - offset.x) / popupWidth.toFloat()).coerceIn(0f, 1f)
                } else {
                    if (attachToEnd) 1f else 0f
                }
            }
        }
    val pivotY =
        when {
            showBelow -> 0f
            showAbove -> 1f
            else -> 0.5f
        }
    return SnapshotPopupLayoutInfo(
        offset = offset,
        showBelow = showBelow,
        showAbove = showAbove,
        transformOrigin = TransformOrigin(pivotX, pivotY),
    )
}

fun Modifier.capturePopupAnchor(onBoundsChange: (IntRect) -> Unit): Modifier {
    return this.onGloballyPositioned { coordinates ->
        if (!coordinates.isAttached) return@onGloballyPositioned
        val position = coordinates.positionInWindow()
        if (!position.x.isFinite() || !position.y.isFinite()) return@onGloballyPositioned
        val right = position.x + coordinates.size.width
        val bottom = position.y + coordinates.size.height
        if (!right.isFinite() || !bottom.isFinite()) return@onGloballyPositioned
        onBoundsChange(
            IntRect(
                left = position.x.roundToInt(),
                top = position.y.roundToInt(),
                right = right.roundToInt(),
                bottom = bottom.roundToInt(),
            ),
        )
    }
}
