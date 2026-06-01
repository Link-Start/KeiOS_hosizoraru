// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("FunctionName", "PropertyName")

package os.kei.ui.page.main.widget.shape

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import os.kei.ui.page.main.widget.shape.internal.BakedAppSquircleSdf
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

val LocalAppSquircleEnabled = staticCompositionLocalOf { true }

@Composable
@ReadOnlyComposable
fun isAppSquircleEnabled(): Boolean = LocalAppSquircleEnabled.current && isAppRuntimeShaderSupported()

@Composable
fun Modifier.appSquircleBackground(
    color: Color,
    cornerRadius: Dp,
    extension: Float = AppSquircleDefaults.Extension,
    control: Float = AppSquircleDefaults.Control,
): Modifier = appSquircleBackground(color, cornerRadius, cornerRadius, cornerRadius, cornerRadius, extension, control)

@Composable
fun Modifier.appSquircleBackground(
    color: Color,
    topStart: Dp,
    topEnd: Dp,
    bottomEnd: Dp,
    bottomStart: Dp,
    extension: Float = AppSquircleDefaults.Extension,
    control: Float = AppSquircleDefaults.Control,
): Modifier {
    val brush =
        rememberAppSquircleBrush(
            color = color,
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart,
            extension = extension,
            control = control,
        ) ?: return background(color = color, shape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart))
    return background(brush = brush, shape = RectangleShape)
}

@Composable
fun Modifier.appSquircleClip(
    cornerRadius: Dp,
    extension: Float = AppSquircleDefaults.Extension,
    control: Float = AppSquircleDefaults.Control,
): Modifier = appSquircleClip(cornerRadius, cornerRadius, cornerRadius, cornerRadius, extension, control)

@Composable
fun Modifier.appSquircleClip(
    topStart: Dp,
    topEnd: Dp,
    bottomEnd: Dp,
    bottomStart: Dp,
    extension: Float = AppSquircleDefaults.Extension,
    control: Float = AppSquircleDefaults.Control,
): Modifier {
    val brush =
        rememberAppSquircleBrush(
            color = Color.White,
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart,
            extension = extension,
            control = control,
        ) ?: return clip(RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart))
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(brush = brush, blendMode = BlendMode.DstIn)
        }
}

@Composable
fun Modifier.appSquircleSurface(
    color: Color,
    cornerRadius: Dp,
    extension: Float = AppSquircleDefaults.Extension,
    control: Float = AppSquircleDefaults.Control,
): Modifier = appSquircleSurface(color, cornerRadius, cornerRadius, cornerRadius, cornerRadius, extension, control)

@Composable
fun Modifier.appSquircleSurface(
    color: Color,
    topStart: Dp,
    topEnd: Dp,
    bottomEnd: Dp,
    bottomStart: Dp,
    extension: Float = AppSquircleDefaults.Extension,
    control: Float = AppSquircleDefaults.Control,
): Modifier {
    val brush =
        rememberAppSquircleBrush(
            color = Color.White,
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart,
            extension = extension,
            control = control,
        ) ?: return clip(RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)).background(color)
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawRect(color)
            drawContent()
            drawRect(brush = brush, blendMode = BlendMode.DstIn)
        }
}

fun Modifier.appSquircleBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
    extension: Float = AppSquircleDefaults.Extension,
    control: Float = AppSquircleDefaults.Control,
): Modifier =
    drawWithCache {
        val widthPx = width.toPx()
        val cornerRadiusPx = cornerRadius.toPx()
        val shaderRenderer =
            createAppSquircleDynamicBorderRenderer(
                size = size,
                strokeWidthPx = widthPx,
                cornerRadiusPx = cornerRadiusPx,
                extension = extension,
                control = control,
            )
        if (shaderRenderer != null) {
            onDrawBehind {
                if (color.alpha > 0f) {
                    shaderRenderer.color = color
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRect(
                            0f,
                            0f,
                            size.width,
                            size.height,
                            shaderRenderer.paint,
                        )
                    }
                }
            }
        } else {
            val halfStroke = widthPx / 2f
            val innerWidth = size.width - widthPx
            val innerHeight = size.height - widthPx
            val path = Path()
            val drawable = widthPx > 0f && innerWidth > 0f && innerHeight > 0f
            if (drawable) {
                path.addAppSquircleRect(
                    width = innerWidth,
                    height = innerHeight,
                    cornerRadius = (cornerRadiusPx - halfStroke).coerceAtLeast(0f),
                    extension = extension,
                    control = control,
                )
            }
            val stroke = Stroke(width = widthPx)
            onDrawBehind {
                if (drawable && color.alpha > 0f) {
                    translate(halfStroke, halfStroke) {
                        drawPath(path = path, color = color, style = stroke)
                    }
                }
            }
        }
    }

fun Modifier.drawAppSquircleBorder(
    width: Dp,
    cornerRadius: Dp,
    extension: Float = AppSquircleDefaults.Extension,
    control: Float = AppSquircleDefaults.Control,
    color: () -> Color,
): Modifier =
    drawWithCache {
        val widthPx = width.toPx()
        val cornerRadiusPx = cornerRadius.toPx()
        val shaderRenderer =
            createAppSquircleDynamicBorderRenderer(
                size = size,
                strokeWidthPx = widthPx,
                cornerRadiusPx = cornerRadiusPx,
                extension = extension,
                control = control,
            )
        if (shaderRenderer != null) {
            onDrawBehind {
                val drawColor = color()
                if (drawColor.alpha > 0f) {
                    shaderRenderer.color = drawColor
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRect(
                            0f,
                            0f,
                            size.width,
                            size.height,
                            shaderRenderer.paint,
                        )
                    }
                }
            }
        } else {
            val halfStroke = widthPx / 2f
            val innerWidth = size.width - widthPx
            val innerHeight = size.height - widthPx
            val path = Path()
            val drawable = widthPx > 0f && innerWidth > 0f && innerHeight > 0f
            if (drawable) {
                path.addAppSquircleRect(
                    width = innerWidth,
                    height = innerHeight,
                    cornerRadius = (cornerRadiusPx - halfStroke).coerceAtLeast(0f),
                    extension = extension,
                    control = control,
                )
            }
            val stroke = Stroke(width = widthPx)
            onDrawBehind {
                val drawColor = color()
                if (drawable && drawColor.alpha > 0f) {
                    translate(halfStroke, halfStroke) {
                        drawPath(path = path, color = drawColor, style = stroke)
                    }
                }
            }
        }
    }

fun Modifier.drawAppSquircleBackground(
    cornerRadius: Dp,
    extension: Float = AppSquircleDefaults.Extension,
    control: Float = AppSquircleDefaults.Control,
    color: () -> Color,
): Modifier =
    drawWithCache {
        val shaderRenderer =
            createAppSquircleDynamicBackgroundRenderer(
                size = size,
                cornerRadiusPx = cornerRadius.toPx(),
                extension = extension,
                control = control,
            )
        if (shaderRenderer != null) {
            onDrawBehind {
                val drawColor = color()
                if (drawColor.alpha > 0f) {
                    shaderRenderer.color = drawColor
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRect(
                            0f,
                            0f,
                            size.width,
                            size.height,
                            shaderRenderer.paint,
                        )
                    }
                }
            }
        } else {
            val path = Path()
            path.addAppSquircleRect(
                width = size.width,
                height = size.height,
                cornerRadius = cornerRadius.toPx(),
                extension = extension,
                control = control,
            )
            onDrawBehind {
                val drawColor = color()
                if (drawColor.alpha > 0f) {
                    drawPath(path = path, color = drawColor)
                }
            }
        }
    }

object AppSquircleDefaults {
    const val Extension = 1.1f
    const val Control = 0.63f
    const val ExtensionMin = 1f
    const val ExtensionMax = 2f
    const val ControlMin = 0.3f
    const val ControlMax = 0.9f
}

fun Path.addAppSquircleRect(
    width: Float,
    height: Float,
    cornerRadius: Float,
    extension: Float = AppSquircleDefaults.Extension,
    control: Float = AppSquircleDefaults.Control,
) {
    if (width <= 0f || height <= 0f) return
    val extClamped = extension.coerceIn(AppSquircleDefaults.ExtensionMin, AppSquircleDefaults.ExtensionMax)
    val ctrlClamped = control.coerceIn(AppSquircleDefaults.ControlMin, AppSquircleDefaults.ControlMax)
    val halfMin = min(width, height) * 0.5f
    val tile = max(0f, cornerRadius * extClamped).coerceAtMost(halfMin)
    if (tile <= 0f) {
        addRect(Rect(0f, 0f, width, height))
        return
    }
    val handle = tile * (1f - ctrlClamped)
    moveTo(tile, 0f)
    lineTo(width - tile, 0f)
    cubicTo(width - handle, 0f, width, handle, width, tile)
    lineTo(width, height - tile)
    cubicTo(width, height - handle, width - handle, height, width - tile, height)
    lineTo(tile, height)
    cubicTo(handle, height, 0f, height - handle, 0f, height - tile)
    lineTo(0f, tile)
    cubicTo(0f, handle, handle, 0f, tile, 0f)
    close()
}

@Composable
private fun rememberAppSquircleBrush(
    color: Color,
    topStart: Dp,
    topEnd: Dp,
    bottomEnd: Dp,
    bottomStart: Dp,
    extension: Float,
    control: Float,
): AppSquircleShaderBrush? {
    if (!isAppSquircleEnabled()) return null
    val density = LocalDensity.current
    val extClamped = extension.coerceIn(AppSquircleDefaults.ExtensionMin, AppSquircleDefaults.ExtensionMax)
    val ctrlClamped = control.coerceIn(AppSquircleDefaults.ControlMin, AppSquircleDefaults.ControlMax)
    val tiles =
        remember(topStart, topEnd, bottomEnd, bottomStart, extClamped, density) {
            with(density) {
                floatArrayOf(
                    topStart.toPx().coerceAtLeast(0f) * extClamped,
                    topEnd.toPx().coerceAtLeast(0f) * extClamped,
                    bottomEnd.toPx().coerceAtLeast(0f) * extClamped,
                    bottomStart.toPx().coerceAtLeast(0f) * extClamped,
                )
            }
        }
    val ctrlKey = (ctrlClamped * CONTROL_KEY_PRECISION).toInt()
    val sdfShader = remember(ctrlKey) { getOrCreateSdfShader(ctrlClamped, ctrlKey) }
    return remember(color, tiles, sdfShader) {
        AppSquircleShaderBrush(
            color = color,
            cornerTilesPx = tiles,
            sdfShader = sdfShader,
        )
    }
}

private class AppSquircleShaderBrush(
    private val color: Color,
    private val cornerTilesPx: FloatArray,
    private val sdfShader: Shader,
) : ShaderBrush() {
    private val runtimeShader = RuntimeShader(SQUIRCLE_SHADER)
    private val effectiveSizes = FloatArray(4)
    private val halfRanges = FloatArray(4)
    private val weights = FloatArray(4)

    override fun createShader(size: Size): Shader {
        val halfMin = minOf(size.width, size.height) * 0.5f
        val threshold = halfMin * BLEND_THRESHOLD_RATIO
        val range = halfMin - threshold
        for (index in 0..3) {
            val tile = cornerTilesPx[index]
            val effective = tile.coerceIn(0f, halfMin)
            effectiveSizes[index] = effective
            halfRanges[index] = SDF_HALF_RANGE * effective
            weights[index] = if (range <= 0f) 1f else ((tile - threshold) / range).coerceIn(0f, 1f)
        }
        runtimeShader.setColorUniform("color", color.toArgb())
        runtimeShader.setFloatUniform("size", size.width, size.height)
        runtimeShader.setFloatUniform("cornerSizes", effectiveSizes)
        runtimeShader.setFloatUniform("halfRangesPx", halfRanges)
        runtimeShader.setFloatUniform("blendWeights", weights)
        runtimeShader.setFloatUniform("bitmapSize", SDF_BITMAP_SIZE.toFloat())
        runtimeShader.setInputShader("cornerSdf", sdfShader)
        return runtimeShader
    }
}

private class AppSquircleDynamicBackgroundRenderer(
    private val runtimeShader: RuntimeShader,
    private val cornerTilesPx: FloatArray,
) {
    val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = runtimeShader }
    private val effectiveSizes = FloatArray(4)
    private val halfRanges = FloatArray(4)
    private val weights = FloatArray(4)
    private var lastColorArgb: Int = 0

    var color: Color = Color.Transparent
        set(value) {
            field = value
            val argb = value.toArgb()
            if (lastColorArgb != argb) {
                runtimeShader.setColorUniform("color", argb)
                lastColorArgb = argb
            }
        }

    fun updateSize(size: Size) {
        val halfMin = minOf(size.width, size.height) * 0.5f
        val threshold = halfMin * BLEND_THRESHOLD_RATIO
        val range = halfMin - threshold
        for (index in 0..3) {
            val tile = cornerTilesPx[index]
            val effective = tile.coerceIn(0f, halfMin)
            effectiveSizes[index] = effective
            halfRanges[index] = SDF_HALF_RANGE * effective
            weights[index] = if (range <= 0f) 1f else ((tile - threshold) / range).coerceIn(0f, 1f)
        }
        runtimeShader.setFloatUniform("size", size.width, size.height)
        runtimeShader.setFloatUniform("cornerSizes", effectiveSizes)
        runtimeShader.setFloatUniform("halfRangesPx", halfRanges)
        runtimeShader.setFloatUniform("blendWeights", weights)
        runtimeShader.setFloatUniform("bitmapSize", SDF_BITMAP_SIZE.toFloat())
    }
}

private class AppSquircleDynamicBorderRenderer(
    private val runtimeShader: RuntimeShader,
    private val cornerTilesPx: FloatArray,
    private val strokeWidthPx: Float,
) {
    val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = runtimeShader }
    private val effectiveSizes = FloatArray(4)
    private val halfRanges = FloatArray(4)
    private val weights = FloatArray(4)
    private var lastColorArgb: Int = 0

    var color: Color = Color.Transparent
        set(value) {
            field = value
            val argb = value.toArgb()
            if (lastColorArgb != argb) {
                runtimeShader.setColorUniform("color", argb)
                lastColorArgb = argb
            }
        }

    fun updateSize(size: Size) {
        val halfMin = minOf(size.width, size.height) * 0.5f
        val threshold = halfMin * BLEND_THRESHOLD_RATIO
        val range = halfMin - threshold
        for (index in 0..3) {
            val tile = cornerTilesPx[index]
            val effective = tile.coerceIn(0f, halfMin)
            effectiveSizes[index] = effective
            halfRanges[index] = SDF_HALF_RANGE * effective
            weights[index] = if (range <= 0f) 1f else ((tile - threshold) / range).coerceIn(0f, 1f)
        }
        runtimeShader.setFloatUniform("size", size.width, size.height)
        runtimeShader.setFloatUniform("cornerSizes", effectiveSizes)
        runtimeShader.setFloatUniform("halfRangesPx", halfRanges)
        runtimeShader.setFloatUniform("blendWeights", weights)
        runtimeShader.setFloatUniform("strokeWidth", strokeWidthPx)
        runtimeShader.setFloatUniform("bitmapSize", SDF_BITMAP_SIZE.toFloat())
    }
}

private fun createAppSquircleDynamicBackgroundRenderer(
    size: Size,
    cornerRadiusPx: Float,
    extension: Float,
    control: Float,
): AppSquircleDynamicBackgroundRenderer? {
    if (!isAppRuntimeShaderSupported()) return null
    if (size.width <= 0f || size.height <= 0f) return null
    val extClamped = extension.coerceIn(AppSquircleDefaults.ExtensionMin, AppSquircleDefaults.ExtensionMax)
    val ctrlClamped = control.coerceIn(AppSquircleDefaults.ControlMin, AppSquircleDefaults.ControlMax)
    val tile = cornerRadiusPx.coerceAtLeast(0f) * extClamped
    val ctrlKey = (ctrlClamped * CONTROL_KEY_PRECISION).toInt()
    val runtimeShader = RuntimeShader(SQUIRCLE_SHADER)
    runtimeShader.setInputShader("cornerSdf", getOrCreateSdfShader(ctrlClamped, ctrlKey))
    return AppSquircleDynamicBackgroundRenderer(
        runtimeShader = runtimeShader,
        cornerTilesPx = floatArrayOf(tile, tile, tile, tile),
    ).also { renderer ->
        renderer.updateSize(size)
    }
}

private fun createAppSquircleDynamicBorderRenderer(
    size: Size,
    strokeWidthPx: Float,
    cornerRadiusPx: Float,
    extension: Float,
    control: Float,
): AppSquircleDynamicBorderRenderer? {
    if (!isAppRuntimeShaderSupported()) return null
    if (size.width <= 0f || size.height <= 0f || strokeWidthPx <= 0f) return null
    val extClamped = extension.coerceIn(AppSquircleDefaults.ExtensionMin, AppSquircleDefaults.ExtensionMax)
    val ctrlClamped = control.coerceIn(AppSquircleDefaults.ControlMin, AppSquircleDefaults.ControlMax)
    val tile = cornerRadiusPx.coerceAtLeast(0f) * extClamped
    val ctrlKey = (ctrlClamped * CONTROL_KEY_PRECISION).toInt()
    val runtimeShader = RuntimeShader(SQUIRCLE_BORDER_SHADER)
    runtimeShader.setInputShader("cornerSdf", getOrCreateSdfShader(ctrlClamped, ctrlKey))
    return AppSquircleDynamicBorderRenderer(
        runtimeShader = runtimeShader,
        cornerTilesPx = floatArrayOf(tile, tile, tile, tile),
        strokeWidthPx = strokeWidthPx,
    ).also { renderer ->
        renderer.updateSize(size)
    }
}

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
private fun isAppRuntimeShaderSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

private val sdfCacheLock = Any()
private val sdfShaderCache = mutableMapOf<Int, Shader>()

private fun getOrCreateSdfShader(
    control: Float,
    key: Int,
): Shader =
    synchronized(sdfCacheLock) {
        sdfShaderCache.getOrPut(key) {
            ImageShader(
                makeAlphaImageBitmap(
                    size = SDF_BITMAP_SIZE,
                    alphaBytes =
                        if (key == BAKED_CONTROL_KEY) {
                            BakedAppSquircleSdf.bytes
                        } else {
                            generateSdfBytes(SDF_BITMAP_SIZE, control)
                        },
                ),
                TileMode.Clamp,
                TileMode.Clamp,
            )
        }
    }

private fun makeAlphaImageBitmap(
    size: Int,
    alphaBytes: ByteArray,
): androidx.compose.ui.graphics.ImageBitmap {
    val pixels = IntArray(size * size)
    for (index in alphaBytes.indices) {
        val alpha = alphaBytes[index].toInt() and 0xFF
        pixels[index] = (alpha shl 24) or (alpha shl 16) or (alpha shl 8) or alpha
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap.asImageBitmap()
}

private fun generateSdfBytes(
    size: Int,
    control: Float,
): ByteArray {
    val handle = 1f - control
    val bx = FloatArray(BEZIER_SAMPLES + 1)
    val by = FloatArray(BEZIER_SAMPLES + 1)
    for (index in 0..BEZIER_SAMPLES) {
        val t = index.toFloat() / BEZIER_SAMPLES
        val omt = 1f - t
        bx[index] = 3f * omt * t * t * handle + t * t * t
        by[index] = omt * omt * omt + 3f * omt * omt * t * handle
    }

    val bytes = ByteArray(size * size)
    val invSize = 1f / size
    val invRange2 = 1f / (2f * SDF_HALF_RANGE)

    for (py in 0 until size) {
        val y = (py + 0.5f) * invSize
        for (px in 0 until size) {
            val x = (px + 0.5f) * invSize
            var minSqDist = Float.MAX_VALUE
            var closestIdx = 0
            for (index in 0 until BEZIER_SAMPLES) {
                val ax = bx[index]
                val ay = by[index]
                val sx = bx[index + 1] - ax
                val sy = by[index + 1] - ay
                val len2 = sx * sx + sy * sy
                if (len2 < 1e-12f) continue
                val u = (((x - ax) * sx + (y - ay) * sy) / len2).coerceIn(0f, 1f)
                val qx = ax + u * sx
                val qy = ay + u * sy
                val ddx = x - qx
                val ddy = y - qy
                val sqDist = ddx * ddx + ddy * ddy
                if (sqDist < minSqDist) {
                    minSqDist = sqDist
                    closestIdx = index
                }
            }
            val dist = sqrt(minSqDist)
            val tx = bx[closestIdx + 1] - bx[closestIdx]
            val ty = by[closestIdx + 1] - by[closestIdx]
            val pdx = x - bx[closestIdx]
            val pdy = y - by[closestIdx]
            val cross = tx * pdy - ty * pdx
            val signedDist = if (cross > 0f) -dist else dist
            val alpha = (0.5f - signedDist * invRange2).coerceIn(0f, 1f)
            bytes[py * size + px] = (alpha * 255f + 0.5f).toInt().toByte()
        }
    }
    return bytes
}

private const val SDF_BITMAP_SIZE = 256
private const val SDF_HALF_RANGE = 0.125f
private const val BEZIER_SAMPLES = 64
private const val BLEND_THRESHOLD_RATIO = 0.7853982f
private const val CONTROL_KEY_PRECISION = 100f
private const val BAKED_CONTROL_KEY = 63

private const val SQUIRCLE_SHADER = """
uniform shader cornerSdf;
uniform float2 size;
uniform float4 cornerSizes;
uniform float4 halfRangesPx;
uniform float4 blendWeights;
uniform float bitmapSize;
layout(color) uniform half4 color;

half sampleSdfBilinear(float2 uv) {
    float2 px = uv * bitmapSize - 0.5;
    float2 i = floor(px);
    float2 f = px - i;
    half a00 = cornerSdf.eval(i + float2(0.5, 0.5)).a;
    half a10 = cornerSdf.eval(i + float2(1.5, 0.5)).a;
    half a01 = cornerSdf.eval(i + float2(0.5, 1.5)).a;
    half a11 = cornerSdf.eval(i + float2(1.5, 1.5)).a;
    half a0 = mix(a00, a10, half(f.x));
    half a1 = mix(a01, a11, half(f.x));
    return mix(a0, a1, half(f.y));
}

half4 main(float2 coord) {
    float dxL = coord.x;
    float dxR = size.x - coord.x;
    float dyT = coord.y;
    float dyB = size.y - coord.y;
    bool left = dxL < dxR;
    bool top = dyT < dyB;
    float dx = left ? dxL : dxR;
    float dy = top ? dyT : dyB;
    float cornerSize = top
        ? (left ? cornerSizes.x : cornerSizes.y)
        : (left ? cornerSizes.w : cornerSizes.z);
    float halfRangePx = top
        ? (left ? halfRangesPx.x : halfRangesPx.y)
        : (left ? halfRangesPx.w : halfRangesPx.z);
    float blendWeight = top
        ? (left ? blendWeights.x : blendWeights.y)
        : (left ? blendWeights.w : blendWeights.z);
    if (dx >= cornerSize || dy >= cornerSize) {
        return color;
    }
    float2 uv = float2(dx, dy) / cornerSize;
    half sdfSample = sampleSdfBilinear(uv);
    float squircleSdf = (1.0 - 2.0 * float(sdfSample)) * halfRangePx;
    float qx = cornerSize - dx;
    float qy = cornerSize - dy;
    float circleSdf = sqrt(qx * qx + qy * qy) - cornerSize;
    float dist = mix(squircleSdf, circleSdf, blendWeight);
    return color * half(smoothstep(0.5, -0.5, dist));
}
"""

private const val SQUIRCLE_BORDER_SHADER = """
uniform shader cornerSdf;
uniform float2 size;
uniform float4 cornerSizes;
uniform float4 halfRangesPx;
uniform float4 blendWeights;
uniform float strokeWidth;
uniform float bitmapSize;
layout(color) uniform half4 color;

half sampleSdfBilinear(float2 uv) {
    float2 px = uv * bitmapSize - 0.5;
    float2 i = floor(px);
    float2 f = px - i;
    half a00 = cornerSdf.eval(i + float2(0.5, 0.5)).a;
    half a10 = cornerSdf.eval(i + float2(1.5, 0.5)).a;
    half a01 = cornerSdf.eval(i + float2(0.5, 1.5)).a;
    half a11 = cornerSdf.eval(i + float2(1.5, 1.5)).a;
    half a0 = mix(a00, a10, half(f.x));
    half a1 = mix(a01, a11, half(f.x));
    return mix(a0, a1, half(f.y));
}

half4 main(float2 coord) {
    float dxL = coord.x;
    float dxR = size.x - coord.x;
    float dyT = coord.y;
    float dyB = size.y - coord.y;
    bool left = dxL < dxR;
    bool top = dyT < dyB;
    float dx = left ? dxL : dxR;
    float dy = top ? dyT : dyB;
    float edgeDistance = min(min(dxL, dxR), min(dyT, dyB));
    float cornerSize = top
        ? (left ? cornerSizes.x : cornerSizes.y)
        : (left ? cornerSizes.w : cornerSizes.z);
    float halfRangePx = top
        ? (left ? halfRangesPx.x : halfRangesPx.y)
        : (left ? halfRangesPx.w : halfRangesPx.z);
    float blendWeight = top
        ? (left ? blendWeights.x : blendWeights.y)
        : (left ? blendWeights.w : blendWeights.z);
    float dist = -edgeDistance;
    if (dx < cornerSize && dy < cornerSize) {
        float2 uv = float2(dx, dy) / cornerSize;
        half sdfSample = sampleSdfBilinear(uv);
        float squircleSdf = (1.0 - 2.0 * float(sdfSample)) * halfRangePx;
        float qx = cornerSize - dx;
        float qy = cornerSize - dy;
        float circleSdf = sqrt(qx * qx + qy * qy) - cornerSize;
        dist = mix(squircleSdf, circleSdf, blendWeight);
    }
    half outerAlpha = half(smoothstep(0.5, -0.5, dist));
    half innerAlpha = half(smoothstep(0.5, -0.5, dist + strokeWidth));
    return color * outerAlpha * (half(1.0) - innerAlpha);
}
"""
