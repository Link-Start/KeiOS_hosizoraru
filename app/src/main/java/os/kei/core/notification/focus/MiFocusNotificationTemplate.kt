package os.kei.core.notification.focus

import android.app.Notification
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.annotation.DrawableRes
import com.xzakota.hyper.notification.focus.FocusNotification
import com.xzakota.hyper.notification.focus.template.FocusTemplateV3

/**
 * Xiaomi HyperOS Super Island/Focussed Notification JSON builder facade.
 *
 * Project usage:
 * 1. Build a [MiFocusNotificationSpec].
 * 2. Pick one summary-state big-island template with [MiFocusIslandBigTemplate].
 * 3. Pick one summary-state small-island template with [MiFocusIslandSmallTemplate].
 * 4. Add one or more expanded-state components with [MiFocusExpandedComponent].
 * 5. Pass the returned [Bundle] to `NotificationCompat.Builder.addExtras`.
 *
 * This facade keeps the project on the current client-side route:
 * `Notification.extras["miui.focus.param"]` + `com.xzakota.hyper.notification:focus-api`.
 * MIPUSH permission flow and Xiaomi Magic dispatch stay outside this file.
 *
 * Summary-state design notes:
 * - Big-island text should stay compact: 2-4 CJK chars, 1-3 digits, or a short percent.
 * - Put full titles and long details in expanded-state components such as [MiFocusExpandedComponent.Base].
 * - Use [MiFocusIslandBigTemplate.ImageTextLeft] plus
 *   [MiFocusIslandBigTemplate.ProgressText] for icon + progress text scenes.
 * - Use [MiFocusIslandBigTemplate.ImageTextLeft] plus
 *   [MiFocusIslandBigTemplate.SameWidthDigit] for countdown scenes.
 * - Use [MiFocusIslandSmallTemplate.CombinePic] when the small island must show progress.
 * - Use [MiFocusIslandBigTemplate.ImageTextRight] with `type = 3` for finished, failed,
 *   cancelled, read, or other terminal short states.
 *
 * Expanded-state design notes:
 * - [MiFocusExpandedComponent.Base] is the safest default for full title/body text.
 * - [MiFocusExpandedComponent.TextButtons] should contain at most 2 actions in this project:
 *   primary action highlighted, secondary action plain.
 * - [MiFocusExpandedComponent.MultiProgress] mirrors summary progress for expanded details.
 * - Keep icons square. Resource/vector icons and bitmap app icons are both supported.
 *
 * JSON composition:
 * - `FocusNotification.buildV3` serializes to `miui.focus.param`.
 * - Pictures are registered with `createPicture(key, Icon/Bitmap)` and referenced by string keys.
 * - Actions are registered with `createAction(key, Notification.Action)` and referenced by string keys.
 * - R8 must keep focus-api serializers, template fields, and this facade's names; see
 *   `app/src/main/keepRules/proguard-rules.keep`.
 */
internal object MiFocusNotificationTemplate {
    const val OUTER_GLOW_SRC = "outer_glow"
    const val PRIMARY_ACTION_BG = "#006EFF"
    const val PRIMARY_ACTION_TITLE = "#FFFFFF"

    fun build(context: Context, spec: MiFocusNotificationSpec): Bundle {
        return FocusNotification.buildV3 {
            val pictures = registerPictures(context, spec)
            applyBaseFlags(spec, pictures)

            spec.island?.let { islandSpec ->
                island {
                    islandPriority = islandSpec.priority
                    islandProperty = islandSpec.property
                    expandedTime = islandSpec.expandedTimeSeconds
                    islandTimeout = islandSpec.timeoutSeconds
                    dismissIsland = islandSpec.dismissIsland
                    islandOrder = islandSpec.reorderWhenHidden
                    maxSize = islandSpec.maxSize
                    needCloseAnimation = islandSpec.needCloseAnimation
                    business = islandSpec.business
                    highlightColor = islandSpec.highlightColor

                    bigIslandArea {
                        islandSpec.bigTemplates.forEach { template ->
                            applyBigIslandTemplate(template, pictures)
                        }
                    }
                    islandSpec.smallTemplate?.let { smallTemplate ->
                        smallIslandArea {
                            applySmallIslandTemplate(smallTemplate, pictures)
                        }
                    }
                    islandSpec.shareData?.let { data ->
                        shareData {
                            title = data.title
                            content = data.content
                            pic = pictures.keyOf(data.pic)
                            shareContent = data.shareContent
                            sharePic = pictures.keyOf(data.sharePic)
                        }
                    }
                }
            }

            spec.expanded.components.forEach { component ->
                applyExpandedComponent(context, component, pictures, spec.actionIconResId)
            }
        }
    }

    private fun FocusTemplateV3.registerPictures(
        context: Context,
        spec: MiFocusNotificationSpec
    ): MiFocusPictureRegistry {
        val assets = buildList {
            add(
                MiFocusPictureAsset(
                    ref = MiFocusPictureRef.TickerLight,
                    source = MiFocusPictureSource.Resource(
                        resId = spec.tickerIconResId,
                        tintColor = Color.BLACK
                    )
                )
            )
            add(
                MiFocusPictureAsset(
                    ref = MiFocusPictureRef.TickerDark,
                    source = MiFocusPictureSource.Resource(
                        resId = spec.tickerIconResId,
                        tintColor = Color.WHITE
                    )
                )
            )
            add(
                MiFocusPictureAsset(
                    ref = MiFocusPictureRef.Display,
                    source = spec.displayPictureSource
                )
            )
            add(
                MiFocusPictureAsset(
                    ref = MiFocusPictureRef.Expanded,
                    source = spec.expandedPictureSource
                )
            )
            addAll(spec.extraPictures)
        }
        val keys = assets.associate { asset ->
            asset.ref to createPicture(asset.ref.key, asset.source.toParcelable(context))
        }
        return MiFocusPictureRegistry(keys)
    }

    private fun FocusTemplateV3.applyBaseFlags(
        spec: MiFocusNotificationSpec,
        pictures: MiFocusPictureRegistry
    ) {
        islandFirstFloat = spec.islandFirstFloat
        enableFloat = spec.allowFloat
        updatable = spec.updatable
        isShowNotification = spec.showNotification
        showSmallIcon = spec.showSmallIcon
        timeout = spec.timeoutMinutes
        aodTitle = spec.aodTitle
        aodPic = pictures.keyOf(spec.aodPic)
        reopen = spec.reopen
        filterWhenNoPermission = spec.filterWhenNoPermission
        hideDeco = spec.hideDeco
        sequence = spec.sequence
        business = spec.business
        notifyId = spec.notifyId
        orderId = spec.orderId
        ticker = spec.ticker ?: spec.compactTicker ?: spec.title
        tickerPic = pictures.keyOf(spec.tickerLightPic)
        tickerPicDark = pictures.keyOf(spec.tickerDarkPic)

        if (spec.outerGlow) {
            outEffectSrc = OUTER_GLOW_SRC
        }
        spec.outEffectColor?.let { outEffectColor = it }
        spec.outEffectSrc?.let { outEffectSrc = it }
    }

    private fun com.xzakota.hyper.notification.island.model.BigIslandArea.applyBigIslandTemplate(
        template: MiFocusIslandBigTemplate,
        pictures: MiFocusPictureRegistry
    ) {
        when (template) {
            is MiFocusIslandBigTemplate.Text -> {
                textInfo = template.text.toIslandTextInfo()
            }

            is MiFocusIslandBigTemplate.Picture -> {
                picInfo = template.pic.toIslandPicInfo(pictures)
            }

            is MiFocusIslandBigTemplate.ImageTextLeft -> {
                imageTextInfoLeft {
                    applyImageTextInfo(template, pictures)
                }
            }

            is MiFocusIslandBigTemplate.ImageTextRight -> {
                imageTextInfoRight {
                    applyImageTextInfo(template, pictures)
                }
            }

            is MiFocusIslandBigTemplate.ProgressText -> {
                progressTextInfo {
                    progressInfo = template.progress.toIslandProgressInfo()
                    textInfo = template.text.toIslandTextInfo()
                }
            }

            is MiFocusIslandBigTemplate.FixedWidthDigit -> {
                fixedWidthDigitInfo {
                    content = template.content
                    digit = template.digit
                    showHighlightColor = template.showHighlightColor
                    turnAnim = template.turnAnim
                }
            }

            is MiFocusIslandBigTemplate.SameWidthDigit -> {
                sameWidthDigitInfo {
                    content = template.content
                    digit = template.digit
                    showHighlightColor = template.showHighlightColor
                    turnAnim = template.turnAnim
                    template.timer?.let { timer ->
                        timerInfo {
                            timerType = timer.type.rawValue
                            timerWhen = timer.whenAtMs
                            timerTotal = timer.totalMs
                            timerSystemCurrent = timer.systemCurrentMs
                        }
                    }
                }
            }
        }
    }

    private fun com.xzakota.hyper.notification.island.model.SmallIslandArea.applySmallIslandTemplate(
        template: MiFocusIslandSmallTemplate,
        pictures: MiFocusPictureRegistry
    ) {
        when (template) {
            is MiFocusIslandSmallTemplate.Picture -> {
                picInfo = template.pic.toIslandPicInfo(pictures)
            }

            is MiFocusIslandSmallTemplate.CombinePic -> {
                combinePicInfo {
                    picInfo = template.pic.toIslandPicInfo(pictures)
                    progressInfo = template.progress.toIslandProgressInfo()
                    smallPicInfo = template.smallPic?.toIslandPicInfo(pictures)
                }
            }
        }
    }

    private fun com.xzakota.hyper.notification.island.model.ImageTextInfo.applyImageTextInfo(
        template: MiFocusIslandImageTextTemplate,
        pictures: MiFocusPictureRegistry
    ) {
        type = template.type
        textInfo = template.text?.toIslandTextInfo()
        picInfo = template.pic?.toIslandPicInfo(pictures)
        progressInfo = template.progress?.toIslandProgressInfo()
    }

    private fun FocusTemplateV3.applyExpandedComponent(
        context: Context,
        component: MiFocusExpandedComponent,
        pictures: MiFocusPictureRegistry,
        @DrawableRes defaultActionIconResId: Int
    ) {
        when (component) {
            is MiFocusExpandedComponent.Base -> {
                baseInfo {
                    type = component.type
                    title = component.text.title
                    subTitle = component.text.subTitle
                    extraTitle = component.text.extraTitle
                    specialTitle = component.text.specialTitle
                    content = component.text.content
                    subContent = component.text.subContent
                    showDivider = component.showDivider
                    showContentDivider = component.showContentDivider
                    picFunction = pictures.keyOf(component.picFunction)
                    setMarginTop = component.setMarginTop
                    setMarginBottom = component.setMarginBottom
                    applyExpandedTextColors(component.text)
                }
            }

            is MiFocusExpandedComponent.Chat -> {
                chatInfo {
                    applyExpandedTextColors(component.text)
                    title = component.text.title
                    subTitle = component.text.subTitle
                    extraTitle = component.text.extraTitle
                    specialTitle = component.text.specialTitle
                    content = component.text.content
                    subContent = component.text.subContent
                    picProfile = pictures.keyOf(component.picProfile)
                    picProfileDark = pictures.keyOf(component.picProfileDark)
                    appIconPkg = component.appIconPkg
                    component.timer?.let { timer ->
                        timerInfo { applyFocusTimer(timer) }
                    }
                }
            }

            is MiFocusExpandedComponent.Highlight -> {
                highlightInfo {
                    type = component.type
                    applyExpandedTextColors(component.text)
                    title = component.text.title
                    subTitle = component.text.subTitle
                    extraTitle = component.text.extraTitle
                    specialTitle = component.text.specialTitle
                    content = component.text.content
                    subContent = component.text.subContent
                    picFunction = pictures.keyOf(component.picFunction)
                    picFunctionDark = pictures.keyOf(component.picFunctionDark)
                    component.timer?.let { timer ->
                        timerInfo { applyFocusTimer(timer) }
                    }
                }
            }

            is MiFocusExpandedComponent.Hint -> {
                hintInfo {
                    type = component.type
                    titleLineCount = component.titleLineCount
                    colorContentBg = component.colorContentBg
                    picContent = pictures.keyOf(component.picContent)
                    applyExpandedTextColors(component.text)
                    title = component.text.title
                    subTitle = component.text.subTitle
                    extraTitle = component.text.extraTitle
                    specialTitle = component.text.specialTitle
                    content = component.text.content
                    subContent = component.text.subContent
                    component.timer?.let { timer ->
                        timerInfo { applyFocusTimer(timer) }
                    }
                    component.action?.let { focusAction ->
                        actionInfo {
                            this@applyExpandedComponent.applyAction(
                                target = this,
                                context = context,
                                focusAction = focusAction,
                                defaultActionIconResId = defaultActionIconResId
                            )
                        }
                    }
                }
            }

            is MiFocusExpandedComponent.Progress -> {
                progressInfo {
                    progress = component.progress.progressPercent.coerceIn(0, 100)
                    colorProgress = component.progress.colorReach
                    colorProgressEnd = component.progress.colorEnd
                    picForward = pictures.keyOf(component.picForward)
                    picMiddle = pictures.keyOf(component.picMiddle)
                    picMiddleUnselected = pictures.keyOf(component.picMiddleUnselected)
                    picEnd = pictures.keyOf(component.picEnd)
                    picEndUnselected = pictures.keyOf(component.picEndUnselected)
                }
            }

            is MiFocusExpandedComponent.Picture -> {
                picInfo {
                    type = component.type
                    pic = pictures.keyOf(component.pic)
                    picDark = pictures.keyOf(component.picDark)
                    component.action?.let { focusAction ->
                        actionInfo {
                            this@applyExpandedComponent.applyAction(
                                target = this,
                                context = context,
                                focusAction = focusAction,
                                defaultActionIconResId = defaultActionIconResId
                            )
                        }
                    }
                }
            }

            is MiFocusExpandedComponent.Background -> {
                bgInfo {
                    type = component.type
                    colorBg = component.color
                    picBg = pictures.keyOf(component.pic)
                }
            }

            is MiFocusExpandedComponent.Cover -> {
                coverInfo = com.xzakota.hyper.notification.focus.model.CoverInfo().apply {
                    applyExpandedTextColors(component.text)
                    title = component.text.title
                    subTitle = component.text.subTitle
                    extraTitle = component.text.extraTitle
                    specialTitle = component.text.specialTitle
                    content = component.text.content
                    subContent = component.text.subContent
                    picCover = pictures.keyOf(component.pic)
                }
            }

            is MiFocusExpandedComponent.HighlightV3 -> {
                highlightInfoV3 {
                    applyExpandedTextColors(component.text)
                    title = component.text.title
                    subTitle = component.text.subTitle
                    extraTitle = component.text.extraTitle
                    specialTitle = component.text.specialTitle
                    content = component.text.content
                    subContent = component.text.subContent
                    highLightText = component.label
                    highLightTextColor = component.labelColor
                    highLightTextColorDark = component.labelColorDark
                    highLightbgColor = component.labelBgColor
                    highLightbgColorDark = component.labelBgColorDark
                    primaryColor = component.primaryColor
                    primaryColorDark = component.primaryColorDark
                    primaryText = component.primaryText
                    secondaryColor = component.secondaryColor
                    secondaryColorDark = component.secondaryColorDark
                    secondaryText = component.secondaryText
                    showSecondaryLine = component.showSecondaryLine
                    component.action?.let { focusAction ->
                        actionInfo {
                            this@applyExpandedComponent.applyAction(
                                target = this,
                                context = context,
                                focusAction = focusAction,
                                defaultActionIconResId = defaultActionIconResId
                            )
                        }
                    }
                }
            }

            is MiFocusExpandedComponent.IconText -> {
                iconTextInfo {
                    type = component.type
                    applyExpandedTextColors(component.text)
                    title = component.text.title
                    subTitle = component.text.subTitle
                    extraTitle = component.text.extraTitle
                    specialTitle = component.text.specialTitle
                    content = component.text.content
                    subContent = component.text.subContent
                    component.icon?.let { icon ->
                        animIconInfo {
                            applyAnimIcon(icon, pictures)
                        }
                    }
                }
            }

            is MiFocusExpandedComponent.MultiProgress -> {
                multiProgressInfo {
                    progress = component.progressPercent.coerceIn(0, 100)
                    color = component.color
                    points = component.points?.coerceIn(0, 4)
                    title = component.text?.title
                    subTitle = component.text?.subTitle
                    content = component.text?.content
                    subContent = component.text?.subContent
                    component.text?.let { applyExpandedTextColors(it) }
                }
            }

            is MiFocusExpandedComponent.AnimText -> {
                animTextInfo {
                    applyExpandedTextColors(component.text)
                    title = component.text.title
                    subTitle = component.text.subTitle
                    extraTitle = component.text.extraTitle
                    specialTitle = component.text.specialTitle
                    content = component.text.content
                    subContent = component.text.subContent
                    component.icon?.let { icon ->
                        animIconInfo {
                            applyAnimIcon(icon, pictures)
                        }
                    }
                    component.timer?.let { timer ->
                        timerInfo { applyFocusTimer(timer) }
                    }
                }
            }

            is MiFocusExpandedComponent.TextButtons -> {
                textButton {
                    component.actions.take(2).forEach { focusAction ->
                        addActionInfo {
                            this@applyExpandedComponent.applyAction(
                                target = this,
                                context = context,
                                focusAction = focusAction,
                                defaultActionIconResId = defaultActionIconResId
                            )
                        }
                    }
                }
            }
        }
    }

    private fun com.xzakota.hyper.notification.focus.model.TextAndColorInfo.applyExpandedTextColors(
        text: MiFocusExpandedText
    ) {
        colorTitle = text.colorTitle
        colorTitleDark = text.colorTitleDark
        colorSubTitle = text.colorSubTitle
        colorSubTitleDark = text.colorSubTitleDark
        colorExtraTitle = text.colorExtraTitle
        colorExtraTitleDark = text.colorExtraTitleDark
        colorSpecialTitle = text.colorSpecialTitle
        colorSpecialTitleDark = text.colorSpecialTitleDark
        colorSpecialBg = text.colorSpecialBg
        colorContent = text.colorContent
        colorContentDark = text.colorContentDark
        colorSubContent = text.colorSubContent
        colorSubContentDark = text.colorSubContentDark
    }

    private fun FocusTemplateV3.applyAction(
        target: com.xzakota.hyper.notification.focus.model.ActionInfo,
        context: Context,
        focusAction: MiFocusNotificationAction,
        @DrawableRes defaultActionIconResId: Int
    ) {
        target.type = focusAction.type.rawValue
        val nativeAction = Notification.Action.Builder(
            Icon.createWithResource(context, focusAction.iconResId ?: defaultActionIconResId),
            focusAction.title,
            focusAction.pendingIntent
        ).build()
        target.action = createAction(focusAction.key, nativeAction)
        target.actionTitle = focusAction.title
        target.clickWithCollapse = focusAction.collapsePanel
        if (focusAction.isHighlighted) {
            target.actionBgColor = focusAction.backgroundColor
            target.actionBgColorDark =
                focusAction.backgroundColorDark ?: focusAction.backgroundColor
            target.actionTitleColor = focusAction.titleColor
            target.actionTitleColorDark = focusAction.titleColorDark ?: focusAction.titleColor
        }
    }

    private fun com.xzakota.hyper.notification.focus.model.AnimIconInfo.applyAnimIcon(
        icon: MiFocusAnimIcon,
        pictures: MiFocusPictureRegistry
    ) {
        type = icon.type
        number = icon.number
        src = pictures.keyOf(icon.src)
        srcDark = pictures.keyOf(icon.srcDark)
        effectSrc = icon.effectSrc
        effectColor = icon.effectColor
        autoplay = icon.autoplay
        loop = icon.loop
        title = icon.text.title
        subTitle = icon.text.subTitle
        content = icon.text.content
        subContent = icon.text.subContent
        applyExpandedTextColors(icon.text)
    }

    private fun com.xzakota.hyper.notification.common.model.TimerInfo.applyFocusTimer(
        timer: MiFocusTimer
    ) {
        timerType = timer.type.rawValue
        timerWhen = timer.whenAtMs
        timerTotal = timer.totalMs
        timerSystemCurrent = timer.systemCurrentMs
    }
}

private class MiFocusPictureRegistry(
    private val keys: Map<MiFocusPictureRef, String>
) {
    fun keyOf(ref: MiFocusPictureRef?): String? {
        return ref?.let { keys[it] }
    }
}

private fun MiFocusPictureSource.toParcelable(context: Context): android.os.Parcelable {
    return when (this) {
        is MiFocusPictureSource.Resource -> {
            Icon.createWithResource(context, resId).also { icon ->
                tintColor?.let(icon::setTint)
            }
        }

        is MiFocusPictureSource.IconValue -> icon
        is MiFocusPictureSource.BitmapValue -> Icon.createWithBitmap(bitmap)
    }
}

private fun MiFocusIslandText.toIslandTextInfo() =
    com.xzakota.hyper.notification.island.model.TextInfo().also { info ->
        info.title = title
        info.frontTitle = frontTitle
        info.content = content
        info.showHighlightColor = showHighlightColor
        info.narrowFont = narrowFont
        info.isTitleDigit = isTitleDigit
        info.turnAnim = turnAnim
    }

private fun MiFocusIslandPic.toIslandPicInfo(
    pictures: MiFocusPictureRegistry
) = com.xzakota.hyper.notification.island.model.PicInfo().also { info ->
    info.type = type
    info.pic = pictures.keyOf(pic)
    info.contentDescription = contentDescription
    info.number = number
    info.effectSrc = effectSrc
    info.effectColor = effectColor
    info.autoplay = autoplay
    info.loop = loop
}

private fun MiFocusIslandProgress.toIslandProgressInfo() =
    com.xzakota.hyper.notification.island.model.ProgressInfo().also { info ->
        info.progress = progressPercent.coerceIn(0, 100)
        info.isCCW = isClockwiseFromTop
        info.colorReach = colorReach
        info.colorUnReach = colorUnReach
    }
