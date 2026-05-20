package os.kei.core.notification.focus

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import os.kei.R

internal const val MI_FOCUS_DEFAULT_BUSINESS = "keios"

internal data class MiFocusNotificationSpec(
    val title: String,
    val content: String,
    @param:DrawableRes val displayIconResId: Int,
    @param:DrawableRes val expandedIconResId: Int = displayIconResId,
    @param:DrawableRes val actionIconResId: Int = displayIconResId,
    @param:DrawableRes val tickerIconResId: Int = R.drawable.ic_notification_logo,
    val displayPictureSource: MiFocusPictureSource =
        MiFocusPictureSource.Resource(displayIconResId),
    val expandedPictureSource: MiFocusPictureSource =
        MiFocusPictureSource.Resource(expandedIconResId),
    val extraPictures: List<MiFocusPictureAsset> = emptyList(),
    val island: MiFocusIslandSpec? = MiFocusIslandSpec.summaryText(),
    val expanded: MiFocusExpandedSpec = MiFocusExpandedSpec.base(title, content),
    val allowFloat: Boolean = true,
    val islandFirstFloat: Boolean = true,
    val updatable: Boolean = true,
    val showNotification: Boolean? = null,
    val showSmallIcon: Boolean? = null,
    val timeoutMinutes: Int? = null,
    val outerGlow: Boolean = false,
    val outEffectSrc: String? = null,
    val outEffectColor: String? = null,
    val aodTitle: String = title,
    val aodPic: MiFocusPictureRef? = MiFocusPictureRef.TickerLight,
    val ticker: String? = null,
    val compactTicker: String? = null,
    val tickerLightPic: MiFocusPictureRef? = MiFocusPictureRef.TickerLight,
    val tickerDarkPic: MiFocusPictureRef? = MiFocusPictureRef.TickerDark,
    val reopen: String? = null,
    val filterWhenNoPermission: Boolean? = null,
    val hideDeco: Boolean? = null,
    val sequence: Long? = null,
    val business: String? = MI_FOCUS_DEFAULT_BUSINESS,
    val notifyId: String? = null,
    val orderId: String? = null
)

internal data class MiFocusIslandSpec(
    val bigTemplates: List<MiFocusIslandBigTemplate>,
    val smallTemplate: MiFocusIslandSmallTemplate? =
        MiFocusIslandSmallTemplate.Picture(),
    val property: Int = 1,
    val priority: Int? = null,
    val expandedTimeSeconds: Int? = null,
    val timeoutSeconds: Int? = null,
    val dismissIsland: Boolean? = null,
    val reorderWhenHidden: Boolean? = null,
    val maxSize: Boolean? = null,
    val needCloseAnimation: Boolean? = null,
    val business: String? = null,
    val highlightColor: String? = null,
    val shareData: MiFocusIslandShareData? = null
) {
    companion object {
        fun summaryText(
            title: String = "",
            content: String? = null,
            pic: MiFocusPictureRef = MiFocusPictureRef.Display
        ) = MiFocusIslandSpec(
            bigTemplates = listOf(
                MiFocusIslandBigTemplate.ImageTextLeft(pic = MiFocusIslandPic(pic = pic)),
                MiFocusIslandBigTemplate.ImageTextRight(
                    type = 3,
                    text = MiFocusIslandText(title = title, content = content)
                )
            ),
            smallTemplate = MiFocusIslandSmallTemplate.Picture(MiFocusIslandPic(pic = pic))
        )
    }
}

internal sealed interface MiFocusIslandBigTemplate {
    data class Text(val text: MiFocusIslandText) : MiFocusIslandBigTemplate

    data class Picture(
        val pic: MiFocusIslandPic = MiFocusIslandPic()
    ) : MiFocusIslandBigTemplate

    data class ImageTextLeft(
        override val type: Int = 1,
        override val text: MiFocusIslandText? = null,
        override val pic: MiFocusIslandPic? = MiFocusIslandPic(),
        override val progress: MiFocusIslandProgress? = null
    ) : MiFocusIslandImageTextTemplate, MiFocusIslandBigTemplate

    data class ImageTextRight(
        override val type: Int = 3,
        override val text: MiFocusIslandText? = null,
        override val pic: MiFocusIslandPic? = null,
        override val progress: MiFocusIslandProgress? = null
    ) : MiFocusIslandImageTextTemplate, MiFocusIslandBigTemplate

    data class ProgressText(
        val text: MiFocusIslandText,
        val progress: MiFocusIslandProgress
    ) : MiFocusIslandBigTemplate

    data class FixedWidthDigit(
        val digit: String,
        val content: String? = null,
        val showHighlightColor: Boolean? = null,
        val turnAnim: Boolean? = null
    ) : MiFocusIslandBigTemplate

    data class SameWidthDigit(
        val digit: String? = null,
        val content: String? = null,
        val timer: MiFocusTimer? = null,
        val showHighlightColor: Boolean? = null,
        val turnAnim: Boolean? = null
    ) : MiFocusIslandBigTemplate
}

internal sealed interface MiFocusIslandImageTextTemplate {
    val type: Int
    val text: MiFocusIslandText?
    val pic: MiFocusIslandPic?
    val progress: MiFocusIslandProgress?
}

internal sealed interface MiFocusIslandSmallTemplate {
    data class Picture(
        val pic: MiFocusIslandPic = MiFocusIslandPic()
    ) : MiFocusIslandSmallTemplate

    data class CombinePic(
        val pic: MiFocusIslandPic = MiFocusIslandPic(),
        val progress: MiFocusIslandProgress,
        val smallPic: MiFocusIslandPic? = null
    ) : MiFocusIslandSmallTemplate
}

internal data class MiFocusIslandText(
    val title: String? = null,
    val frontTitle: String? = null,
    val content: String? = null,
    val showHighlightColor: Boolean? = null,
    val narrowFont: Boolean? = null,
    val isTitleDigit: Boolean? = null,
    val turnAnim: Boolean? = null
)

internal data class MiFocusIslandPic(
    val type: Int = 1,
    val pic: MiFocusPictureRef = MiFocusPictureRef.Display,
    val contentDescription: String? = null,
    val number: Int? = null,
    val effectSrc: String? = null,
    val effectColor: String? = null,
    val autoplay: Boolean? = null,
    val loop: Boolean? = null
)

internal data class MiFocusIslandProgress(
    val progressPercent: Int,
    val colorReach: String? = null,
    val colorUnReach: String? = null,
    val isClockwiseFromTop: Boolean = true
)

internal data class MiFocusIslandShareData(
    val title: String? = null,
    val content: String? = null,
    val pic: MiFocusPictureRef? = null,
    val shareContent: String? = null,
    val sharePic: MiFocusPictureRef? = null
)

internal data class MiFocusExpandedSpec(
    val components: List<MiFocusExpandedComponent>
) {
    companion object {
        fun base(title: String, content: String) = MiFocusExpandedSpec(
            components = listOf(
                MiFocusExpandedComponent.Base(
                    text = MiFocusExpandedText(title = title, content = content.ifBlank { " " })
                )
            )
        )
    }
}

internal sealed interface MiFocusExpandedComponent {
    data class Base(
        val text: MiFocusExpandedText,
        val type: Int = 2,
        val showDivider: Boolean? = null,
        val showContentDivider: Boolean? = null,
        val picFunction: MiFocusPictureRef? = null,
        val setMarginTop: Boolean? = null,
        val setMarginBottom: Boolean? = null
    ) : MiFocusExpandedComponent

    data class Chat(
        val text: MiFocusExpandedText,
        val picProfile: MiFocusPictureRef? = null,
        val picProfileDark: MiFocusPictureRef? = null,
        val appIconPkg: String? = null,
        val timer: MiFocusTimer? = null
    ) : MiFocusExpandedComponent

    data class Highlight(
        val text: MiFocusExpandedText,
        val type: Int? = null,
        val picFunction: MiFocusPictureRef? = null,
        val picFunctionDark: MiFocusPictureRef? = null,
        val timer: MiFocusTimer? = null
    ) : MiFocusExpandedComponent

    data class Hint(
        val text: MiFocusExpandedText,
        val type: Int? = null,
        val titleLineCount: Int? = null,
        val colorContentBg: String? = null,
        val picContent: MiFocusPictureRef? = null,
        val timer: MiFocusTimer? = null,
        val action: MiFocusNotificationAction? = null
    ) : MiFocusExpandedComponent

    data class Progress(
        val progress: MiFocusExpandedProgress,
        val picForward: MiFocusPictureRef? = null,
        val picMiddle: MiFocusPictureRef? = null,
        val picMiddleUnselected: MiFocusPictureRef? = null,
        val picEnd: MiFocusPictureRef? = null,
        val picEndUnselected: MiFocusPictureRef? = null
    ) : MiFocusExpandedComponent

    data class Picture(
        val pic: MiFocusPictureRef = MiFocusPictureRef.Display,
        val picDark: MiFocusPictureRef? = null,
        val type: Int = 0,
        val action: MiFocusNotificationAction? = null
    ) : MiFocusExpandedComponent

    data class Background(
        val type: Int = 1,
        val color: String? = null,
        val pic: MiFocusPictureRef? = null
    ) : MiFocusExpandedComponent

    data class Cover(
        val text: MiFocusExpandedText,
        val pic: MiFocusPictureRef
    ) : MiFocusExpandedComponent

    data class HighlightV3(
        val text: MiFocusExpandedText,
        val label: String? = null,
        val labelColor: String? = null,
        val labelColorDark: String? = null,
        val labelBgColor: String? = null,
        val labelBgColorDark: String? = null,
        val primaryText: String? = null,
        val primaryColor: String? = null,
        val primaryColorDark: String? = null,
        val secondaryText: String? = null,
        val secondaryColor: String? = null,
        val secondaryColorDark: String? = null,
        val showSecondaryLine: Boolean? = null,
        val action: MiFocusNotificationAction? = null
    ) : MiFocusExpandedComponent

    data class IconText(
        val text: MiFocusExpandedText,
        val type: Int? = null,
        val icon: MiFocusAnimIcon? = null
    ) : MiFocusExpandedComponent

    data class MultiProgress(
        val progressPercent: Int,
        val color: String? = null,
        val points: Int? = null,
        val text: MiFocusExpandedText? = null
    ) : MiFocusExpandedComponent

    data class AnimText(
        val text: MiFocusExpandedText,
        val icon: MiFocusAnimIcon? = null,
        val timer: MiFocusTimer? = null
    ) : MiFocusExpandedComponent

    data class TextButtons(
        val actions: List<MiFocusNotificationAction>
    ) : MiFocusExpandedComponent
}

internal data class MiFocusExpandedText(
    val title: String? = null,
    val subTitle: String? = null,
    val extraTitle: String? = null,
    val specialTitle: String? = null,
    val content: String? = null,
    val subContent: String? = null,
    val colorTitle: String? = null,
    val colorTitleDark: String? = null,
    val colorSubTitle: String? = null,
    val colorSubTitleDark: String? = null,
    val colorExtraTitle: String? = null,
    val colorExtraTitleDark: String? = null,
    val colorSpecialTitle: String? = null,
    val colorSpecialTitleDark: String? = null,
    val colorSpecialBg: String? = null,
    val colorContent: String? = null,
    val colorContentDark: String? = null,
    val colorSubContent: String? = null,
    val colorSubContentDark: String? = null
)

internal data class MiFocusExpandedProgress(
    val progressPercent: Int,
    val colorReach: String? = null,
    val colorEnd: String? = null
)

internal data class MiFocusAnimIcon(
    val src: MiFocusPictureRef? = MiFocusPictureRef.Expanded,
    val srcDark: MiFocusPictureRef? = null,
    val type: Int = 0,
    val number: Int? = null,
    val effectSrc: String? = null,
    val effectColor: String? = null,
    val autoplay: Boolean? = null,
    val loop: Boolean? = null,
    val text: MiFocusExpandedText = MiFocusExpandedText()
)

internal data class MiFocusNotificationAction(
    val key: String,
    val title: String,
    val pendingIntent: PendingIntent,
    @param:DrawableRes val iconResId: Int? = null,
    val type: MiFocusActionType = MiFocusActionType.Text,
    val isHighlighted: Boolean = false,
    val collapsePanel: Boolean? = null,
    val backgroundColor: String = MiFocusNotificationTemplate.PRIMARY_ACTION_BG,
    val backgroundColorDark: String? = null,
    val titleColor: String = MiFocusNotificationTemplate.PRIMARY_ACTION_TITLE,
    val titleColorDark: String? = null
)

internal enum class MiFocusActionType(val rawValue: Int) {
    Circle(0),
    Progress(1),
    Text(2)
}

internal data class MiFocusTimer(
    val type: MiFocusTimerType,
    val whenAtMs: Long,
    val totalMs: Long,
    val systemCurrentMs: Long
) {
    companion object {
        fun countdown(deadlineAtMs: Long, nowMs: Long = System.currentTimeMillis()) = MiFocusTimer(
            type = MiFocusTimerType.CountdownStart,
            whenAtMs = deadlineAtMs,
            totalMs = (deadlineAtMs - nowMs).coerceAtLeast(0L),
            systemCurrentMs = nowMs
        )
    }
}

internal enum class MiFocusTimerType(val rawValue: Int) {
    CountdownPause(-2),
    CountdownStart(-1),
    None(0),
    CountUpStart(1),
    CountUpPause(2)
}

internal data class MiFocusPictureRef(val key: String) {
    companion object {
        val TickerLight = MiFocusPictureRef("mi_focus_ticker_light")
        val TickerDark = MiFocusPictureRef("mi_focus_ticker_dark")
        val Display = MiFocusPictureRef("mi_focus_display")
        val Expanded = MiFocusPictureRef("mi_focus_expanded")
    }
}

internal data class MiFocusPictureAsset(
    val ref: MiFocusPictureRef,
    val source: MiFocusPictureSource
)

internal sealed interface MiFocusPictureSource {
    data class Resource(
        @param:DrawableRes val resId: Int,
        val tintColor: Int? = null
    ) : MiFocusPictureSource

    data class IconValue(
        val icon: Icon
    ) : MiFocusPictureSource

    data class BitmapValue(
        val bitmap: Bitmap
    ) : MiFocusPictureSource
}
