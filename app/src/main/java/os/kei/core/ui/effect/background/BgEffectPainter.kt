// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package os.kei.core.ui.effect.background

import androidx.compose.ui.graphics.Brush
import top.yukonga.miuix.kmp.blur.RuntimeShader
import top.yukonga.miuix.kmp.blur.asBrush
import kotlin.math.cos
import kotlin.math.sin

class BgEffectPainter {
    val shaderCode by lazy { OS3_BG_FRAG }
    val runtimeShader by lazy { RuntimeShader(shaderCode) }
    val brush: Brush get() = runtimeShader.asBrush()

    private val resolution = FloatArray(2)
    private val bound = floatArrayOf(0.0f, 0.4489f, 1.0f, 0.5511f)
    private val pointsAnimBuffer = FloatArray(8)

    private var animTime = Float.NaN
    private var isDarkCached: Boolean? = null
    private var cachedLogoHeight = Float.NaN
    private var cachedTotalHeight = Float.NaN
    private var cachedTotalWidth = Float.NaN
    private var cachedPointsAnimTime = Float.NaN
    private var cachedPointsSource: FloatArray? = null

    // 配置常量
    companion object {
        private const val U_TRANSLATE_Y = 0.0f
        private const val U_ALPHA_MULTI = 1.0f
        private const val U_NOISE_SCALE = 1.5f
        private const val U_POINT_OFFSET = 0.1f
        private const val U_POINT_RADIUS_MULTI = 1.0f

        // 预设配置
        private val PHONE_LIGHT_POINTS =
            floatArrayOf(
                0.67f,
                0.42f,
                1.0f,
                0.69f,
                0.75f,
                1.0f,
                0.14f,
                0.71f,
                0.95f,
                0.14f,
                0.27f,
                0.8f,
            )

        private val PHONE_LIGHT_COLORS =
            floatArrayOf(
                0.57f,
                0.76f,
                0.98f,
                1.0f,
                0.98f,
                0.85f,
                0.68f,
                1.0f,
                0.98f,
                0.75f,
                0.93f,
                1.0f,
                0.73f,
                0.7f,
                0.98f,
                1.0f,
            )

        private val PHONE_DARK_POINTS =
            floatArrayOf(
                0.63f,
                0.5f,
                0.88f,
                0.69f,
                0.75f,
                0.8f,
                0.17f,
                0.66f,
                0.81f,
                0.14f,
                0.24f,
                0.72f,
            )

        private val PHONE_DARK_COLORS =
            floatArrayOf(
                0.0f,
                0.31f,
                0.58f,
                1.0f,
                0.53f,
                0.29f,
                0.15f,
                1.0f,
                0.46f,
                0.06f,
                0.27f,
                1.0f,
                0.16f,
                0.12f,
                0.45f,
                1.0f,
            )

        private val PAD_LIGHT_POINTS =
            floatArrayOf(
                0.67f,
                0.37f,
                0.88f,
                0.54f,
                0.66f,
                1.0f,
                0.37f,
                0.71f,
                0.68f,
                0.28f,
                0.26f,
                0.62f,
            )

        private val PAD_LIGHT_COLORS =
            floatArrayOf(
                0.57f,
                0.76f,
                0.98f,
                1.0f,
                0.98f,
                0.85f,
                0.68f,
                1.0f,
                0.98f,
                0.75f,
                0.93f,
                0.95f,
                0.73f,
                0.7f,
                0.98f,
                0.9f,
            )

        private val PAD_DARK_POINTS =
            floatArrayOf(
                0.55f,
                0.42f,
                1.0f,
                0.56f,
                0.75f,
                1.0f,
                0.4f,
                0.59f,
                0.71f,
                0.43f,
                0.09f,
                0.75f,
            )

        private val PAD_DARK_COLORS =
            floatArrayOf(
                0.0f,
                0.31f,
                0.58f,
                1.0f,
                0.53f,
                0.29f,
                0.15f,
                1.0f,
                0.46f,
                0.06f,
                0.27f,
                1.0f,
                0.16f,
                0.12f,
                0.45f,
                1.0f,
            )
    }

    // 当前配置
    private var uPoints = PHONE_LIGHT_POINTS
    private var uColors = PHONE_LIGHT_COLORS
    private var uSaturateOffset = 0.2f
    private var uLightOffset = 0.1f

    init {
        initializeShader()
    }

    private fun initializeShader() {
        runtimeShader.apply {
            setFloatUniform("uTranslateY", U_TRANSLATE_Y)
            setFloatUniform("uNoiseScale", U_NOISE_SCALE)
            setFloatUniform("uPointRadiusMulti", U_POINT_RADIUS_MULTI)
            setFloatUniform("uSaturateOffset", uSaturateOffset)
            setFloatUniform("uBound", bound)
            setFloatUniform("uAlphaMulti", U_ALPHA_MULTI)
            setFloatUniform("uLightOffset", uLightOffset)

            setFloatUniform("uPoints", uPoints)
            updatePointsAnim(0f)
            setFloatUniform("uColors", uColors)
        }
    }

    fun updateResolution(
        width: Float,
        height: Float,
    ) {
        if (resolution[0] == width && resolution[1] == height) return
        resolution[0] = width
        resolution[1] = height
        runtimeShader.setFloatUniform("uResolution", resolution)
    }

    private fun setColors(fArr: FloatArray) {
        uColors = fArr.copyOf()
        runtimeShader.setFloatUniform("uColors", fArr)
    }

    private fun setPoints(fArr: FloatArray) {
        uPoints = fArr.copyOf()
        runtimeShader.setFloatUniform("uPoints", fArr)
    }

    private fun setBound(fArr: FloatArray) {
        fArr.copyInto(bound)
        this.runtimeShader.setFloatUniform("uBound", bound)
    }

    private fun setLightOffset(f: Float) {
        this.uLightOffset = f
        this.runtimeShader.setFloatUniform("uLightOffset", f)
    }

    private fun setSaturateOffset(f: Float) {
        this.uSaturateOffset = f
        this.runtimeShader.setFloatUniform("uSaturateOffset", f)
    }

    fun updateAnimTime(time: Float) {
        if (animTime == time) return
        animTime = time
        runtimeShader.setFloatUniform("uAnimTime", animTime)
    }

    fun updatePointsAnim(time: Float) {
        if (cachedPointsAnimTime == time && cachedPointsSource === uPoints) return
        val offset = U_POINT_OFFSET
        var index = 0
        while (index < 4) {
            val srcX = uPoints[index * 3]
            val srcY = uPoints[index * 3 + 1]
            val animX = srcX + sin(time + srcY) * offset
            val animY = srcY + cos(time + animX) * offset
            pointsAnimBuffer[index * 2] = animX
            pointsAnimBuffer[index * 2 + 1] = animY
            index++
        }
        runtimeShader.setFloatUniform("uPointsAnim", pointsAnimBuffer)
        cachedPointsAnimTime = time
        cachedPointsSource = uPoints
    }

    fun updateModeIfNeeded(isDarkMode: Boolean) {
        if (isDarkCached == isDarkMode) return
        updateMode(isDarkMode)
        isDarkCached = isDarkMode
    }

    private fun updateMode(isDarkMode: Boolean) {
        if (isDarkMode) setPhoneDark() else setPhoneLight()
    }

    private fun setPhoneLight() {
        setLightOffset(0.1f)
        setSaturateOffset(0.2f)
        setPoints(PHONE_LIGHT_POINTS)
        setColors(PHONE_LIGHT_COLORS)
        setBound(bound)
        cachedPointsSource = null
    }

    private fun setPhoneDark() {
        setLightOffset(-0.1f)
        setSaturateOffset(0.2f)
        setPoints(PHONE_DARK_POINTS)
        setColors(PHONE_DARK_COLORS)
        setBound(bound)
        cachedPointsSource = null
    }

    private fun setPadLight() {
        setLightOffset(0.1f)
        setSaturateOffset(0.0f)
        setPoints(PAD_LIGHT_POINTS)
        setColors(PAD_LIGHT_COLORS)
        setBound(bound)
        cachedPointsSource = null
    }

    private fun setPadDark() {
        setLightOffset(-0.1f)
        setSaturateOffset(0.2f)
        setPoints(PAD_DARK_POINTS)
        setColors(PAD_DARK_COLORS)
        setBound(bound)
        cachedPointsSource = null
    }

    fun updateBoundIfNeeded(
        logoHeight: Float,
        totalHeight: Float,
        totalWidth: Float,
    ) {
        if (cachedLogoHeight == logoHeight &&
            cachedTotalHeight == totalHeight &&
            cachedTotalWidth == totalWidth
        ) {
            return
        }
        calcAnimationBound(logoHeight, totalHeight, totalWidth)
        runtimeShader.setFloatUniform("uBound", bound)
        cachedLogoHeight = logoHeight
        cachedTotalHeight = totalHeight
        cachedTotalWidth = totalWidth
    }

    private fun calcAnimationBound(
        logoHeight: Float,
        totalHeight: Float,
        totalWidth: Float,
    ) {
        val heightRatio = logoHeight / totalHeight

        if (totalWidth <= totalHeight) {
            bound[0] = 0.0f
            bound[1] = 1.0f - heightRatio
            bound[2] = 1.0f
            bound[3] = heightRatio
        } else {
            val widthRatio = logoHeight / totalWidth
            val xOffset = (totalWidth - logoHeight) / 2.0f / totalWidth
            bound[0] = xOffset
            bound[1] = 1.0f - heightRatio
            bound[2] = widthRatio
            bound[3] = heightRatio
        }
    }
}
