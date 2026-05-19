package os.kei.mcp.framework.notification.builder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

internal object NotificationLargeIconFactory {
    private const val TARGET_SIZE_DP = 48
    private const val CONTENT_PADDING_RATIO = 0.08f

    private val bitmapCache = ConcurrentHashMap<Int, Bitmap>()

    fun create(
        context: Context,
        @DrawableRes resId: Int?
    ): Bitmap? {
        if (resId == null) return null
        bitmapCache[resId]?.let { return it }
        val drawable = context.getDrawable(resId) ?: return null
        val bitmap = drawable.renderCenteredBitmap(context)
        bitmapCache[resId] = bitmap
        return bitmap
    }

    private fun Drawable.renderCenteredBitmap(context: Context): Bitmap {
        val targetSizePx = max(
            1,
            (context.resources.displayMetrics.density * TARGET_SIZE_DP.toFloat()).roundToInt()
        )
        if (this is BitmapDrawable && bitmap != null && bitmap.width == targetSizePx && bitmap.height == targetSizePx) {
            return bitmap
        }

        val bitmap = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val padding = (targetSizePx * CONTENT_PADDING_RATIO).roundToInt()
        val availableWidth = (targetSizePx - (padding * 2)).coerceAtLeast(1)
        val availableHeight = (targetSizePx - (padding * 2)).coerceAtLeast(1)
        val sourceWidth = intrinsicWidth.takeIf { it > 0 } ?: targetSizePx
        val sourceHeight = intrinsicHeight.takeIf { it > 0 } ?: targetSizePx
        val scale = minOf(
            availableWidth.toFloat() / sourceWidth.toFloat(),
            availableHeight.toFloat() / sourceHeight.toFloat()
        )
        val drawWidth = max(1, (sourceWidth * scale).roundToInt())
        val drawHeight = max(1, (sourceHeight * scale).roundToInt())
        val left = ((targetSizePx - drawWidth) / 2f).roundToInt()
        val top = ((targetSizePx - drawHeight) / 2f).roundToInt()
        val targetRect = Rect(left, top, left + drawWidth, top + drawHeight)
        val previousBounds = Rect(bounds)
        val previousFilterBitmap = paint?.isFilterBitmap ?: true
        setBounds(targetRect)
        if (paint != null) {
            paint?.isFilterBitmap = true
        }
        draw(canvas)
        setBounds(previousBounds)
        if (paint != null) {
            paint?.isFilterBitmap = previousFilterBitmap
        }
        return bitmap
    }

    private val Drawable.paint: Paint?
        get() = (this as? BitmapDrawable)?.paint
}
