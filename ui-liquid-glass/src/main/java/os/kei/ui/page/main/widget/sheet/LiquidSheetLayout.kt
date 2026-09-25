@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.sheet

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import os.kei.ui.page.main.widget.glass.claimFloatingChromeDrags

/** Forces an exact height. Used for the scrim's blur band. */
internal fun Modifier.liquidSheetHeightPx(heightPx: () -> Int): Modifier =
    layout { measurable, constraints ->
        val resolvedHeight = heightPx().coerceIn(0, constraints.maxHeight)
        val placeable = measurable.measure(
            constraints.copy(minHeight = resolvedHeight, maxHeight = resolvedHeight),
        )
        layout(placeable.width, resolvedHeight) {
            placeable.place(0, 0)
        }
    }

/** Forces an exact height only once one is requested, otherwise leaves the content's own. */
internal fun Modifier.liquidSheetOptionalHeightPx(heightPx: () -> Int): Modifier =
    layout { measurable, constraints ->
        val requestedHeight = heightPx()
        if (requestedHeight <= 0) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            val resolvedHeight = requestedHeight.coerceIn(0, constraints.maxHeight)
            val placeable = measurable.measure(
                constraints.copy(minHeight = resolvedHeight, maxHeight = resolvedHeight),
            )
            layout(placeable.width, resolvedHeight) { placeable.place(0, 0) }
        }
    }

/** Forces an exact width. Used for the grabber's press-widening. */
internal fun Modifier.liquidSheetWidthPx(widthPx: () -> Int): Modifier =
    layout { measurable, constraints ->
        val resolvedWidth = widthPx().coerceIn(0, constraints.maxWidth)
        val placeable = measurable.measure(
            constraints.copy(minWidth = resolvedWidth, maxWidth = resolvedWidth),
        )
        layout(resolvedWidth, placeable.height) { placeable.place(0, 0) }
    }

internal fun liquidSheetSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * The area outside the sheet: tap to dismiss, and nothing gets through.
 *
 * The claim goes **before** the tap gesture. Modifiers declared earlier are outer, the Main pass
 * travels inner-to-outer, and the claim only consumes position changes — never the down or up — so
 * taps still resolve while a drag that wanders across this area cannot reach the pager underneath and
 * switch pages behind a modal sheet.
 */
internal fun Modifier.liquidSheetOutsideDismiss(
    allowDismiss: Boolean,
    onDismissRequest: () -> Unit,
    onBlockedDismissRequest: (() -> Unit)?,
): Modifier =
    this
        .claimFloatingChromeDrags()
        .pointerInput(allowDismiss, onDismissRequest, onBlockedDismissRequest) {
            detectTapGestures(
                onTap = {
                    if (allowDismiss) {
                        onDismissRequest()
                    } else {
                        onBlockedDismissRequest?.invoke()
                    }
                },
            )
        }
